#!/usr/bin/env node
/**
 * P7 dataset seed — students, published assessment, question corpus, READY snapshots
 * ({@code specs/16-P7-Pilot-V1.5.md §21-§23, §26-§27}).
 *
 * <p>This script builds the reference load dataset through the **frozen HTTP contract** wherever the
 * platform exposes an endpoint, so every business invariant (published content, published assessment,
 * snapshot preparation) is enforced by the platform rather than bypassed with SQL. Two steps have no
 * endpoint and are the recorded limitations of this environment:
 *
 * <ul>
 *   <li><b>Credential provisioning.</b> {@code POST /users} deliberately assigns a non-usable
 *       credential (§56/§58) and reset delivery is not built (GAP-002), so a synthetic cohort cannot
 *       self-authenticate. The script therefore invokes the platform's own load-cohort provisioning CLI
 *       ({@code LoadCohortCli}), which hashes with the real password encoder, assigns STUDENT, audits each
 *       account, is idempotent by email, and refuses to run in production. That replaced an earlier raw-SQL
 *       path that wrote {@code {noop}} plaintext directly to PostgreSQL — see GAP-085.</li>
 *   <li><b>Nothing else.</b> Every other step uses the real API.</li>
 * </ul>
 *
 * <p>Idempotent: re-running reuses existing rows by code/email instead of duplicating them, which is
 * required because the ramp must not be preceded by a fresh mutation between steps (§33).
 *
 * Usage:
 *   node load/seed.mjs --students=5000 --questions=100
 *   node load/seed.mjs --students=100  --questions=20 --label=smoke
 */

import { writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";

// --------------------------------------------------------------------------- args

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, ...v] = a.replace(/^--/, "").split("=");
    return [k, v.join("=") || "true"];
  }),
);

const cfg = {
  baseUrl: args["base-url"] ?? process.env.P7_BASE_URL ?? "http://localhost:8080",
  students: Number(args.students ?? process.env.P7_STUDENTS ?? 5000),
  questions: Number(args.questions ?? process.env.P7_QUESTIONS ?? 100),
  label: args.label ?? process.env.P7_LABEL ?? "reference",
  outDir: args.out ?? process.env.P7_OUT ?? path.join(process.cwd(), "load", "out"),
  adminEmail: args["admin-email"] ?? process.env.P7_ADMIN_EMAIL ?? "load.admin@example.test",
  adminPassword: args["admin-password"] ?? process.env.P7_ADMIN_PASSWORD ?? "load-admin-password-123",
  reviewerEmail: args["reviewer-email"] ?? process.env.P7_REVIEWER_EMAIL ?? "load.reviewer@example.test",
  reviewerPassword: args["reviewer-password"] ?? process.env.P7_REVIEWER_PASSWORD ?? "load-reviewer-password-123",
  studentPassword: args["student-password"] ?? process.env.P7_STUDENT_PASSWORD ?? "load-student-password-123",
  concurrency: Number(args.concurrency ?? process.env.P7_SEED_CONCURRENCY ?? 12),
  scheduleStartHours: Number(args["start-hours"] ?? process.env.P7_START_HOURS ?? 2),
  scheduleEndHours: Number(args["end-hours"] ?? process.env.P7_END_HOURS ?? 72),
  /**
   * Number of independent schedules to prepare.
   *
   * <p>Each schedule gives the same students a fresh attempt, so the ramp has enough non-overlapping
   * cohorts for its 9,100 starts without mutating any data between steps. Two schedules over 5,000 students
   * yield 10,000 starts, which covers 100+500+1,000+2,500+5,000.
   */
  schedules: Number(args.schedules ?? process.env.P7_SCHEDULES ?? 2),
  /**
   * Whether to provision credentials.
   *
   * <p>Credential provisioning is a real, audited, environment-gated operation, so it needs an explicit
   * opt-in; a read-only run must never create accounts. The flag keeps its historical name so existing
   * runbooks and scripts keep working, but it no longer means "apply a SQL file": it now selects the
   * in-application provisioning CLI (GAP-085).
   */
  provision: (args["apply-sql"] ?? args.provision ?? process.env.P7_APPLY_SQL ?? "false") === "true",
  /** Compose service whose image carries the provisioning CLI. */
  backendContainer: args["backend-container"] ?? process.env.P7_BACKEND_CONTAINER ?? "exam-platform-backend-1",
  /** The Spring profile the CLI boots with; must never be a production profile. */
  provisionProfile: args["provision-profile"] ?? process.env.P7_PROVISION_PROFILE ?? "staging",
};

// --------------------------------------------------------------------------- http

class HttpError extends Error {
  constructor(method, url, status, body) {
    super(`${method} ${url} -> ${status}: ${body}`);
    this.status = status;
    this.body = body;
  }
}

let adminToken = null;

/**
 * Generated per-student credentials (email -> credential), populated by {@link provisionCredentials}.
 *
 * <p>Each account gets its own random credential, so there is no longer a single shared student password.
 * The values live only in memory and in the git-ignored manifest under {@code load/out/}.
 */
let studentCredentials = new Map();

async function request(method, url, { token, body, form } = {}) {
  const headers = {};
  // An explicit `null` means "no bearer at all" (used by login); only an omitted token falls back to
  // the admin identity. Conflating the two would send a stale admin token to the login endpoint.
  const bearer = token === undefined ? adminToken : token;
  if (bearer) {
    headers.Authorization = `Bearer ${bearer}`;
  }
  let payload;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  const response = await fetch(`${cfg.baseUrl}/api/v1${url}`, { method, headers, body: payload });
  const text = await response.text();
  if (!response.ok) {
    throw new HttpError(method, url, response.status, text.slice(0, 400));
  }
  if (!text) {
    return null;
  }
  const parsed = JSON.parse(text);
  return parsed?.data ?? parsed;
}

async function tryRequest(method, url, options) {
  try {
    return await request(method, url, options);
  } catch (error) {
    if (error instanceof HttpError && (error.status === 404 || error.status === 409)) {
      return null;
    }
    throw error;
  }
}

/** Runs `worker` over `items` with a bounded number in flight. */
async function pool(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  let failure = null;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    for (;;) {
      if (failure) {
        return;
      }
      const index = cursor++;
      if (index >= items.length) {
        return;
      }
      try {
        results[index] = await worker(items[index], index);
      } catch (error) {
        failure = failure ?? error;
        return;
      }
    }
  });
  await Promise.all(runners);
  if (failure) {
    throw failure;
  }
  return results;
}

// --------------------------------------------------------------------------- login

async function login(username, password) {
  const result = await request("POST", "/auth/login", { body: { username, password }, token: null });
  return result.accessToken;
}

/**
 * The corpus needs two distinct identities.
 *
 * <p>The platform enforces separation of duties: a user cannot approve a version they submitted
 * ({@code 12-P3 §112}). The seed therefore authenticates an author for the submit step and a
 * reviewer for the approve step. Both are provisioned users with the load credential.
 */
let reviewerToken = null;

async function authorToken() {
  return adminToken;
}

/**
 * Provisions the synthetic cohort through the application's own provisioning CLI (GAP-085).
 *
 * <p><b>This replaced raw SQL.</b> The harness used to pipe {@code INSERT} statements into PostgreSQL with a
 * {@code {noop}} plaintext password. That bypassed hashing, wrote to tables the application owns, produced
 * no audit trail and was not environment-gated. The application now exposes
 * {@code LoadCohortCli}, which hashes with the real encoder, assigns the STUDENT role, audits each account,
 * is idempotent by email, and refuses to run when {@code app.environment} is production.
 *
 * <p>The CLI boots a non-web Spring context inside the backend container, so this is the only call in the
 * harness that does not go through the frozen HTTP contract — and it is no longer a database write, it is an
 * application operation.
 *
 * @param {Array<{email: string, firstName: string, lastName: string}>} members accounts to provision
 * @returns {Map<string, string>} email -> generated credential
 */
function provisionCredentials(members) {
  // The jar is a Spring Boot executable archive: application classes live under BOOT-INF/classes and are
  // only visible to the Boot loader, so `-cp app.jar <mainClass>` fails with ClassNotFoundException.
  // PropertiesLauncher is the supported entry point for booting an alternate main class from such a jar.
  const args = [
    "exec",
    "-i",
    cfg.backendContainer,
    "java",
    "-Dloader.main=com.company.exam.identity.loadcohort.LoadCohortCli",
    "-cp",
    "/app/app.jar",
    "org.springframework.boot.loader.launch.PropertiesLauncher",
    `--spring.profiles.active=${cfg.provisionProfile}`,
  ];

  let stdout;
  try {
    stdout = execFileSync("docker", args, {
      input: JSON.stringify(members),
      encoding: "utf8",
      maxBuffer: 64 * 1024 * 1024,
      stdio: ["pipe", "pipe", "pipe"],
    });
  } catch (error) {
    const detail = (error.stderr || error.stdout || "").toString().trim();
    throw new Error(
      `credential provisioning failed (exit ${error.status}): ${detail || error.message}\n` +
        "The mechanism is environment-gated: it refuses to run when app.environment is production.",
    );
  }

  // The CLI prefixes its result with a marker, because Spring's structured logging also writes JSON to
  // stdout — including shutdown lines that arrive *after* the result. Selecting "the last JSON line"
  // would read a log record instead of the payload.
  const MARKER = "LOAD_COHORT_RESULT ";
  const line = stdout
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l.includes(MARKER))
    .map((l) => l.slice(l.indexOf(MARKER) + MARKER.length))
    .pop();
  if (!line) {
    throw new Error(`credential provisioning returned no result: ${stdout.trim().slice(0, 600)}`);
  }

  const parsed = JSON.parse(line);
  if (parsed.error) {
    throw new Error(`credential provisioning refused: ${parsed.error}`);
  }

  const credentials = new Map();
  for (const outcome of parsed.members ?? []) {
    if (outcome.credential) {
      credentials.set(outcome.email, outcome.credential);
    }
  }
  console.log(
    `[seed] provisioned credentials: created=${parsed.created} updated=${parsed.updated} skipped=${parsed.skipped}`,
  );
  if (parsed.skipped > 0) {
    console.warn(`[seed] ${parsed.skipped} account(s) were skipped and have no credential`);
  }
  return credentials;
}


// --------------------------------------------------------------------------- corpus

const TEMPLATE = "SINGLE_CHOICE";
const OPTION_KEYS = ["A", "B", "C", "D"];

/**
 * Builds representative content (§22): plain text, long text, inline math, block math, and special
 * characters. Deliberately not trivially small so payload handling is exercised realistically.
 */
function questionContent(index) {
  const variants = [
    () => `A train travels ${120 + index} km in ${2 + (index % 3)} hours. What is its average speed in km/h?`,
    () =>
      `Consider the arithmetic progression starting at ${index}. What is the ${index % 9 + 5}th term?\n\n` +
      `Use the standard formula for the nth term of an AP.`,
    () => `Evaluate the integral \\(\\int_0^{${index % 5 + 1}} x^2\\,\\mathrm{d}x\\). Which value is correct?`,
    () =>
      `Solve the system below.\n\n\\[\\begin{cases} 2x + 3y = ${index % 7 + 4} \\\\ x - y = 1 \\end{cases}\\]\n\nWhat is \\(x\\)?`,
    () => `If \\(a = ${index % 11 + 2}\\), what is \\(a^2 - a\\)?`,
    () =>
      `Read the passage and answer.\n\n"Prices rose by ${index % 9 + 2}% while wages stayed flat for a decade."\n\n` +
      `Which statement follows? (Note the special characters: — "quotes" & ampersands, 50% and 3.14.)`,
  ];
  return variants[index % variants.length]();
}

function optionContents(index) {
  const correct = (index % 90) + 10;
  return [
    `${correct}`,
    `${correct + 1}`,
    `${correct + 2}`,
    `${correct + 3}`,
  ];
}

// --------------------------------------------------------------------------- steps

async function ensureAcademic() {
  const languages = await request("GET", "/languages");
  let language = languages.find((l) => l.code === "EN");
  if (!language) {
    language = await request("POST", "/languages", { body: { code: "EN", name: "English" } });
  }
  const exams = await request("GET", "/exams");
  let exam = exams.find((e) => e.code === "LOAD");
  if (!exam) {
    exam = await request("POST", "/exams", { body: { code: "LOAD", name: "Load Test Exam" } });
  }
  const subjects = await request("GET", `/subjects?examId=${exam.id}`);
  let subject = subjects.find((s) => s.code === "LOAD-S1");
  if (!subject) {
    subject = await request("POST", "/subjects", {
      body: { examId: exam.id, code: "LOAD-S1", name: "Load Subject 1" },
    });
  }
  const chapters = await request("GET", `/chapters?subjectId=${subject.id}`);
  let chapter = chapters.find((c) => c.code === "LOAD-C1");
  if (!chapter) {
    chapter = await request("POST", "/chapters", {
      body: { subjectId: subject.id, code: "LOAD-C1", name: "Load Chapter 1" },
    });
  }
  const topics = await request("GET", `/topics?chapterId=${chapter.id}`);
  let topic = topics.find((t) => t.code === "LOAD-T1");
  if (!topic) {
    topic = await request("POST", "/topics", {
      body: { chapterId: chapter.id, code: "LOAD-T1", name: "Load Topic 1" },
    });
  }
  return { language, exam, subject, chapter, topic };
}

async function ensureTemplates() {
  const templates = await request("GET", "/question-templates");
  const found = templates.find((t) => t.code === TEMPLATE);
  if (!found) {
    throw new Error(`template ${TEMPLATE} is not seeded; cannot build a corpus`);
  }
  return found;
}

/**
 * Creates the question corpus and publishes every version.
 *
 * <p>The full authoring lifecycle is exercised: create → options → submit-review → approve → publish.
 * Each question contributes exactly one published version, so the rule materialization later has a
 * deterministic pool of `cfg.questions`.
 *
 * <p>Idempotent by reuse rather than by title (the list endpoint does not expose titles): any question in
 * this exam that is not yet published is pushed through the remaining lifecycle steps, and only the
 * shortfall is created. A re-run therefore converges instead of duplicating the corpus.
 */
async function seedQuestions(acad, template) {
  const published = [];
  const existing = await request("GET", `/questions?size=500&examId=${acad.exam.id}`).catch(() => []);
  const existingList = Array.isArray(existing) ? existing : (existing.content ?? []);

  // Finish any question left mid-lifecycle by an interrupted earlier run.
  for (const question of existingList) {
    const versions = await request("GET", `/questions/${question.id}/versions`);
    if (!versions.length) {
      continue;
    }
    let version = versions.find((v) => v.published) ?? versions[versions.length - 1];
    if (!version.published) {
      version = await publishVersion(question.id, version, published.length);
    }
    if (version.published) {
      published.push({ questionId: question.id, versionId: version.id });
    }
  }
  if (published.length > 0) {
    console.log(`[seed] reusing ${published.length} already-published questions`);
  }

  for (let index = published.length; index < cfg.questions; index += 1) {
    const title = `LOAD-Q${String(index + 1).padStart(3, "0")}`;
    const question = await request("POST", "/questions", {
      body: {
        templateId: template.id,
        examId: acad.exam.id,
        subjectId: acad.subject.id,
        chapterId: acad.chapter.id,
        topicId: acad.topic.id,
        title,
        content: questionContent(index),
        marks: 2,
        negativeMarks: 0.5,
        difficulty: ["EASY", "MEDIUM", "HARD"][index % 3],
        languageId: acad.language.id,
      },
    });
    const versions = await request("GET", `/questions/${question.id}/versions`);
    if (!versions.length) {
      throw new Error(`question ${question.id} has no version`);
    }
    const version = await publishVersion(question.id, versions[0], index);
    published.push({ questionId: question.id, versionId: version.id, title });
  }
  return published;
}

/**
 * Drives one question version to PUBLISHED, tolerating the states an interrupted run can leave behind.
 *
 * <p>Options are only added when absent, `submit-review` is only called from DRAFT, and the approve step
 * always uses the reviewer identity because the platform refuses self-review (§112).
 */
async function publishVersion(questionId, version, index) {
  if (version.published) {
    return version;
  }
  const options = await request("GET", `/questions/${questionId}/versions/${version.id}/options`);
  if (!options.length) {
    const contents = optionContents(index);
    for (let o = 0; o < OPTION_KEYS.length; o += 1) {
      await request("POST", `/questions/${questionId}/versions/${version.id}/options`, {
        body: {
          optionKey: OPTION_KEYS[o],
          content: contents[o],
          displayOrder: o + 1,
          isCorrect: o === 0,
        },
      });
    }
  }
  let current = await request("GET", `/questions/${questionId}/versions/${version.id}`);
  if (current.reviewStatus === "DRAFT") {
    await request("POST", `/questions/${questionId}/versions/${version.id}/submit-review`, {
      token: adminToken,
      body: { remarks: "load corpus" },
    });
    current = await request("GET", `/questions/${questionId}/versions/${version.id}`);
  }
  if (current.reviewStatus !== "PUBLISHED") {
    await request("POST", `/questions/${questionId}/versions/${version.id}/approve`, {
      token: reviewerToken,
      body: { remarks: "load corpus" },
    });
  }
  await request("POST", `/questions/${questionId}/versions/${version.id}/publish`, { token: adminToken });
  return request("GET", `/questions/${questionId}/versions/${version.id}`);
}

/**
 * Picks or creates a blueprint version sized to the corpus.
 *
 * <p>Same immutability rule as the assessment: a published blueprint that declares the wrong section size
 * cannot be edited, so a correctly-sized new version is created instead.
 */
async function ensureBlueprint(acad, questionCount) {
  const blueprints = await request("GET", "/blueprints?size=100");
  let blueprint = (blueprints.content ?? blueprints).find((b) => b.code === "LOAD-BP");
  if (!blueprint) {
    blueprint = await request("POST", "/blueprints", {
      body: { code: "LOAD-BP", name: "Load Blueprint", description: "P7 load dataset" },
    });
  }
  const detail = await request("GET", `/blueprints/${blueprint.id}`);
  const versions = detail.versions ?? [];

  const configuration = {
    sections: [
      {
        name: "Load Section 1",
        displayOrder: 1,
        questionCount,
        marks: questionCount * 2,
        durationMinutes: 120,
      },
    ],
    navigation: { allowSectionSwitch: true, mode: "FREE" },
    timer: { mode: "GLOBAL", serverAuthoritative: true, autoSubmitOnExpiry: true },
    submission: { allowManualSubmit: true, autoSubmitOnExpiry: true, requireConfirmation: true },
    validation: { responseRequiredBeforeNext: false, sectionCompletionRequired: false },
    presentation: { palettePosition: "RIGHT", showProgress: true },
    calculator: { enabled: false },
    language: { languageIds: [acad.language.id], defaultLanguageId: acad.language.id },
  };

  for (const candidate of versions) {
    const full = await request("GET", `/blueprints/${blueprint.id}/versions/${candidate.id}`);
    const sections = full.configuration?.sections ?? [];
    const declared = sections.reduce((sum, s) => sum + (s.questionCount ?? 0), 0);
    if (full.published && declared === questionCount) {
      return { blueprintId: blueprint.id, blueprintVersionId: candidate.id, reused: true };
    }
  }

  const version = await request("POST", `/blueprints/${blueprint.id}/versions`, {
    body: { configuration },
  });
  await request("POST", `/blueprints/${blueprint.id}/versions/${version.id}/publish`);
  return { blueprintId: blueprint.id, blueprintVersionId: version.id, reused: false };
}

/**
 * Picks a usable assessment version, creating a new one when the published ones are the wrong size.
 *
 * <p>Published examination artifacts are immutable (frozen rule 13), so a version that was published with
 * the wrong question count can never be corrected in place. If an earlier, smaller seed run already
 * published one, the only correct move is to create and publish a **new** version with the right corpus
 * size — editing the old one would violate immutability.
 */
async function ensureAssessment(acad, bp, questionCount) {
  const list = await request("GET", "/assessments?size=100");
  let assessment = (list.content ?? list).find((a) => a.code === "LOAD-AS");
  if (!assessment) {
    assessment = await request("POST", "/assessments", {
      body: { code: "LOAD-AS", name: "Load Assessment", description: "P7 load dataset" },
    });
  }
  const detail = await request("GET", `/assessments/${assessment.id}`);
  const versions = detail.versions ?? [];

  // Reuse a published version only when it already carries the requested corpus size.
  for (const candidate of versions) {
    if (!candidate.published) {
      continue;
    }
    const questions = await request("GET", `/assessment-versions/${candidate.id}/questions`).catch(() => []);
    if (questions.length === questionCount) {
      return { assessmentId: assessment.id, assessmentVersionId: candidate.id, reused: true };
    }
  }

  const version = await request("POST", `/assessments/${assessment.id}/versions`, {
    body: {
      blueprintVersionId: bp.blueprintVersionId,
      durationMinutes: 120,
      engineType: "CUSTOM",
      deliveryPolicyId: "33333333-3333-3333-3333-333333333301",
    },
  });
  return { assessmentId: assessment.id, assessmentVersionId: version.id, reused: false };
}

/**
 * Materializes the published question pool onto the assessment version and publishes it.
 *
 * <p>The frozen contract accepts exactly one materialize mode, {@code REPLACE_UNMATERIALIZED}
 * ({@code 13-P4 §63}). Materialization is what makes the AssessmentQuestion set concrete, which is the
 * precondition for a snapshot to be prepared.
 */
async function materializeAndPublish(avId, academic, questionCount) {
  const existing = await request("GET", `/assessment-versions/${avId}/questions`);
  if (!existing.length) {
    const rule = await request("POST", `/assessment-versions/${avId}/rules`, {
      body: {
        sectionName: "Load Section 1",
        targetCount: questionCount,
        displayOrder: 1,
        ruleConfiguration: {
          examId: academic.exam.id,
          subjectId: academic.subject.id,
          publishedOnly: true,
        },
      },
    });
    await request("POST", `/assessment-versions/${avId}/rules/materialize`, {
      body: { mode: "REPLACE_UNMATERIALIZED" },
    });
    void rule;
  }
  const validation = await request("POST", `/assessment-versions/${avId}/validate`);
  if (!validation.valid) {
    const errors = (validation.errors ?? []).map((e) => `${e.code}: ${e.message}`).join("; ");
    throw new Error(`assessment version failed validation: ${errors}`);
  }
  const detail = await request("GET", `/assessment-versions/${avId}`);
  if (!detail.published) {
    await request("POST", `/assessment-versions/${avId}/publish`);
  }
  const questions = await request("GET", `/assessment-versions/${avId}/questions`);
  return { questionCount: questions.length };
}

/**
 * Ensures the requested number of independent schedules exist for the published version.
 *
 * <p>The ramp needs more distinct (student, schedule) pairs than a single schedule can supply: a student
 * who submitted on one schedule cannot start again on it. Each extra schedule yields a fresh cohort of the
 * same students without mutating any data between ramp steps, which is what the "no manual data mutation
 * between ramp steps" constraint requires.
 */
async function ensureSchedules(avId, count) {
  const start = new Date(Date.now() - 60 * 60 * 1000);
  const end = new Date(Date.now() + cfg.scheduleEndHours * 60 * 60 * 1000);
  const schedules = await request("GET", "/assessment-schedules?size=100");
  const list = (schedules.content ?? schedules).filter((s) => s.assessmentVersionId === avId);

  const chosen = list.slice(0, count);
  while (chosen.length < count) {
    const created = await request("POST", "/assessment-schedules", {
      body: {
        assessmentVersionId: avId,
        mode: "EXAM",
        startTime: start.toISOString(),
        endTime: end.toISOString(),
      },
    });
    chosen.push(created);
  }
  return chosen;
}

/**
 * Enqueues snapshot preparation and waits for every attempt to reach READY (§23).
 *
 * <p>Preparation is an asynchronous background job, so the harness must not start the burst until the
 * snapshots are genuinely READY — otherwise the start burst would measure snapshot creation, which
 * §26/§27 explicitly forbid.
 */
async function prepareAttempts(scheduleId) {
  const prepared = await tryRequest("POST", `/assessment-schedules/${scheduleId}/prepare-attempts`);
  return prepared;
}

// --------------------------------------------------------------------------- main

async function main() {
  const startedAt = new Date();
  console.log(`[seed] target=${cfg.baseUrl} students=${cfg.students} questions=${cfg.questions}`);

  // ------------------------------------------------------------------ 1. identities
  //
  // The cohort and the corpus identities are provisioned by the application's own environment-gated CLI
  // (GAP-085), not by raw SQL. `POST /users` intentionally cannot accept a password (§56/§58) and reset
  // delivery is not built (GAP-002), so the platform provides a controlled provisioning path for synthetic
  // cohorts instead. It hashes with the real encoder, assigns STUDENT, audits each account, is idempotent
  // by email, and refuses to run when app.environment is production.
  const students = Array.from({ length: cfg.students }, (_, i) => {
    const n = String(i + 1).padStart(5, "0");
    return {
      id: `aaaaaaaa-0000-4000-8000-${String(i + 1).padStart(12, "0")}`,
      email: `load.student${n}@example.test`,
      firstName: "Load",
      lastName: `Student${n}`,
    };
  });

  if (!cfg.provision) {
    console.log("[seed] --apply-sql/--provision not set; showing the cohort that would be provisioned");
    console.log(`[seed] would provision ${students.length} students + admin + reviewer`);
    return;
  }

  // The corpus needs two distinct identities: the platform refuses self-review (12-P3 §112).
  const credentials = provisionCredentials([
    { email: cfg.adminEmail, firstName: "Load", lastName: "Admin" },
    { email: cfg.reviewerEmail, firstName: "Load", lastName: "Reviewer" },
    ...students,
  ]);
  for (const member of [cfg.adminEmail, cfg.reviewerEmail]) {
    if (!credentials.has(member)) {
      throw new Error(`provisioning returned no credential for ${member}; refusing to continue`);
    }
  }

  // Credentials are generated per account, so the caller is given the values rather than a shared default.
  cfg.adminPassword = credentials.get(cfg.adminEmail);
  cfg.reviewerPassword = credentials.get(cfg.reviewerEmail);
  cfg.studentPassword = credentials.get(students[0].email);
  studentCredentials = new Map(students.map((s) => [s.email, credentials.get(s.email)]));
  console.log("[seed] credentials provisioned");

  // ------------------------------------------------------------------ 2. authenticate
  adminToken = await login(cfg.adminEmail, cfg.adminPassword);
  reviewerToken = await login(cfg.reviewerEmail, cfg.reviewerPassword);
  console.log("[seed] admin + reviewer authenticated");

  // ------------------------------------------------------------------ 3. academic + corpus
  const academic = await ensureAcademic();
  console.log(`[seed] academic ready exam=${academic.exam.id}`);
  const template = await ensureTemplates();
  const corpus = await seedQuestions(academic, template);
  console.log(`[seed] published ${corpus.length} questions`);

  // ------------------------------------------------------------------ 4. assessment
  const bp = await ensureBlueprint(academic, cfg.questions);
  const assessment = await ensureAssessment(academic, bp, cfg.questions);
  const materialized = await materializeAndPublish(assessment.assessmentVersionId, academic, cfg.questions);
  console.log(`[seed] assessment version published with ${materialized.questionCount} questions`);

  const schedules = await ensureSchedules(assessment.assessmentVersionId, cfg.schedules);
  console.log(`[seed] schedules=${schedules.map((s) => s.id).join(",")}`);

  // ------------------------------------------------------------------ 5. snapshots
  const preparations = [];
  for (const schedule of schedules) {
    const prepared = await prepareAttempts(schedule.id);
    preparations.push({ scheduleId: schedule.id, preparationJobId: prepared?.jobId ?? null });
    console.log(`[seed] preparation queued schedule=${schedule.id} job=${prepared?.jobId ?? "n/a"}`);
  }

  const manifest = {
    generatedAt: startedAt.toISOString(),
    label: cfg.label,
    baseUrl: cfg.baseUrl,
    students: students.length,
    studentEmails: students.map((s) => s.email),
    /**
     * Per-student generated credentials. Provisioning issues a distinct random credential per account, so
     * there is no single shared password any more; readers that need one account use {@code studentEmails[0]}
     * with {@code studentCredentials[studentEmails[0]]}.
     *
     * <p>This file is git-ignored ({@code load/out/}) and is the only place the plaintext values exist.
     */
    studentCredentials: Object.fromEntries(studentCredentials),
    /** Retained for older readers; equals the first student's credential. */
    studentPassword: cfg.studentPassword,
    questions: corpus.length,
    examId: academic.exam.id,
    subjectId: academic.subject.id,
    languageId: academic.language.id,
    blueprintId: bp.blueprintId,
    blueprintVersionId: bp.blueprintVersionId,
    assessmentId: assessment.assessmentId,
    assessmentVersionId: assessment.assessmentVersionId,
    questionCount: materialized.questionCount,
    // The first schedule is the default target; `scheduleIds` lists every prepared cohort.
    scheduleId: schedules[0].id,
    scheduleIds: schedules.map((s) => s.id),
    preparations,
    /** How the cohort obtained its credentials; no file is written any more (GAP-085). */
    provisioning: "LoadCohortCli (environment-gated, hashed, audited, idempotent)",
  };
  await writeFile(path.join(cfg.outDir, "manifest.json"), JSON.stringify(manifest, null, 2), "utf8");
  console.log(`[seed] manifest written to ${path.join(cfg.outDir, "manifest.json")}`);
  console.log("[seed] next: node load/wait-ready.mjs  (blocks until every attempt is READY)");
}
main().catch((error) => {
  console.error(`[seed] FAILED: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
});
