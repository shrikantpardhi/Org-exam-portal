#!/usr/bin/env node
/**
 * P7 ramp runner ({@code specs/16-P7-Pilot-V1.5.md §33}, {@code specs/21-P12-Hardening-V1.5.md §57}).
 *
 * <p>Runs the frozen ramp 100 → 500 → 1,000 → 2,500 → 5,000, and between steps:
 *
 * <ol>
 *   <li>records the `attempt_question` / `attempt_question_option` baseline,</li>
 *   <li>runs the k6 profile,</li>
 *   <li>re-reads those counts and asserts the delta is exactly zero (§27),</li>
 *   <li>stops the ramp when a step breaches a gate or a severe failure appears (§33: "stop and
 *       diagnose rather than blindly escalating").</li>
 * </ol>
 *
 * <p>No manual data mutation happens between steps: the only writes are the exam traffic itself.
 *
 * Usage: node load/ramp.mjs --profile=mixed --steps=100,500,1000,2500,5000
 */

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, ...v] = a.replace(/^--/, "").split("=");
    return [k, v.join("=") || "true"];
  }),
);

const cfg = {
  profile: args.profile ?? process.env.P7_PROFILE ?? "mixed",
  steps: (args.steps ?? process.env.P7_RAMP ?? "100,500,1000,2500,5000")
    .split(",")
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n) && n > 0),
  baseUrl: args["base-url"] ?? process.env.P7_BASE_URL ?? "http://localhost:8080",
  questions: Number(args.questions ?? process.env.P7_QUESTIONS ?? 100),
  vus: Number(args.vus ?? process.env.P7_VUS ?? 0),
  duration: Number(args.duration ?? process.env.P7_DURATION_SECONDS ?? 60),
  outDir: args.out ?? process.env.P7_OUT ?? "load/out",
  label: args.label ?? process.env.P7_LABEL ?? "ramp",
  k6: process.env.P7_K6 ?? "k6",
  container: process.env.P7_PSQL_CONTAINER ?? "exam-platform-postgres-1",
  psqlUser: process.env.P7_PSQL_USER ?? "exam_app",
  psqlDb: process.env.P7_PSQL_DB ?? "exam_platform",
  // Stop conditions (§33).
  stopOnGateFailure: (args["stop-on-gate-failure"] ?? "true") === "true",
  /**
   * First credential index to use. `null` means "derive it": students whose attempts are no longer
   * NOT_STARTED have been consumed by an earlier run and must not be replayed, or the run would measure
   * "already submitted" rather than load.
   */
  startOffset: args["start-offset"] ? Number(args["start-offset"]) : null,
  stopOnFailureRate: Number(args["stop-on-failure-rate"] ?? 0.01),
};

/** Read-only scalar query against the database, used only for integrity reconciliation. */
function scalar(sql) {
  const out = execFileSync(
    "docker",
    ["exec", cfg.container, "psql", "-U", cfg.psqlUser, "-d", cfg.psqlDb, "-t", "-A", "-c", sql],
    { encoding: "utf8" },
  );
  return Number(out.trim());
}

/**
 * Snapshot baseline.
 *
 * <p>These are the two counts §27 forbids Start Exam from changing. If a start burst increases either,
 * snapshots are being generated synchronously and the run fails regardless of latency.
 */
function snapshotCounts() {
  return {
    attemptQuestion: scalar("SELECT count(*) FROM attempt_question;"),
    attemptQuestionOption: scalar("SELECT count(*) FROM attempt_question_option;"),
    readyAttempts: scalar("SELECT count(*) FROM student_attempt WHERE snapshot_status = 'READY';"),
    totalAttempts: scalar("SELECT count(*) FROM student_attempt;"),
  };
}

/** Runs k6 once for one ramp step, drawing a disjoint cohort via `P7_OFFSET`. */
async function runStep(users, stepIndex, offset) {
  const summaryPath = path.join(cfg.outDir, `p7-${cfg.profile}-${users}.summary.json`);
  const env = {
    ...process.env,
    P7_BASE_URL: cfg.baseUrl,
    P7_PROFILE: cfg.profile,
    P7_USERS: String(users),
    P7_QUESTIONS: String(cfg.questions),
    P7_VUS: String(cfg.vus > 0 ? Math.min(cfg.vus, users) : users),
    P7_DURATION_SECONDS: String(cfg.duration),
    P7_LABEL: `${cfg.label}-${users}`,
    // Disjoint cohort per step: a student who already submitted cannot start again.
    P7_OFFSET: String(offset),
    P7_RELEASE: process.env.P7_RELEASE ?? "",
    P7_ENVIRONMENT: process.env.P7_ENVIRONMENT ?? "local-docker-single-host",
  };

  const startedAt = new Date();
  let exitCode = 0;
  try {
    const output = execFileSync(cfg.k6, ["run", "load/k6/scenarios.js"], {
      env,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
      maxBuffer: 64 * 1024 * 1024,
    });
    process.stdout.write(`\n--- k6 users=${users} ---\n${output.split("\n").slice(-25).join("\n")}\n`);
  } catch (error) {
    exitCode = error.status ?? 1;
    process.stdout.write(`\n--- k6 users=${users} (exit ${exitCode}) ---\n${error.stdout ?? ""}\n${error.stderr ?? ""}\n`);
  }
  const finishedAt = new Date();

  let summary = null;
  try {
    summary = JSON.parse(await readFile(summaryPath, "utf8"));
  } catch (_error) {
    summary = null;
  }
  return { users, stepIndex, startedAt, finishedAt, exitCode, summary, summaryPath };
}

/**
 * Number of fresh (student, schedule) pairs the credential manifest holds.
 *
 * <p>The manifest is built from attempts that are `NOT_STARTED` and `READY`, so its length *is* the number of
 * cohorts the ramp can still consume. Slicing it by index is then exact — a count of already-consumed attempts
 * would not be, because the consumed set is not a contiguous prefix.
 */
function availableCohorts() {
  const manifestPath = process.env.P7_CREDENTIALS_FILE ?? path.join(cfg.outDir, "credentials.json");
  // A missing or unreadable manifest must fail loudly: returning 0 here previously made the ramp report
  // "0 fresh cohorts" instead of the real error, which looks like a dataset problem rather than a bug.
  const parsed = JSON.parse(readFileSync(manifestPath, "utf8"));
  if (!Array.isArray(parsed)) {
    throw new Error(`${manifestPath} is not a JSON array of credentials`);
  }
  return parsed.length;
}

async function main() {
  await mkdir(cfg.outDir, { recursive: true });
  const baseline = snapshotCounts();
  const cohorts = availableCohorts();
  console.log(`[ramp] profile=${cfg.profile} steps=${cfg.steps.join(",")}`);
  console.log(
    `[ramp] baseline attempt_question=${baseline.attemptQuestion} attempt_question_option=${baseline.attemptQuestionOption} ready=${baseline.readyAttempts}/${baseline.totalAttempts}`,
  );
  console.log(`[ramp] fresh cohorts available in the credential manifest: ${cohorts}`);

  const results = [];
  let stopped = null;

  /**
   * The ramp consumes `sum(steps)` cohorts, and they must all be genuinely fresh.
   *
   * <p>The credential manifest lists only `NOT_STARTED` + `READY` attempts, so comparing against its length
   * is an exact check. Without it the later steps would silently replay students who already started, and the
   * run would report "no prepared attempt" errors as if they were load failures.
   */
  const requiredCohorts = cfg.steps.reduce((sum, n) => sum + n, 0);
  if (requiredCohorts > cohorts) {
    stopped = {
      at: cfg.steps[0],
      reason:
        `ramp needs ${requiredCohorts} fresh attempts (${cfg.steps.join("+")}) but the credential manifest ` +
        `holds only ${cohorts}; prepare more schedules (P7_SCHEDULES) and rebuild credentials`,
    };
    console.log(`[ramp] STOP: ${stopped.reason}`);
  }

  let offset = cfg.startOffset ?? 0;
  for (let i = 0; i < cfg.steps.length && stopped === null; i += 1) {
    const users = cfg.steps[i];
    if (users > baseline.totalAttempts) {
      stopped = {
        at: users,
        reason: `requested ${users} concurrent students but only ${baseline.totalAttempts} attempts exist`,
      };
      console.log(`[ramp] STOP: ${stopped.reason}`);
      break;
    }
    if (baseline.readyAttempts < baseline.totalAttempts) {
      // Scope the readiness requirement to the cohorts this run will actually use. Attempts belonging to a
      // schedule that is still preparing are irrelevant to this ramp, and requiring *every* attempt in the
      // database to be READY would block a run whose own cohort is fully prepared.
      const shortfall = requiredCohorts - cohorts;
      if (shortfall > 0) {
        stopped = {
          at: users,
          reason:
            `only ${cohorts} fresh READY cohorts are available but the ramp needs ${requiredCohorts}; ` +
            "prepare more attempts before measuring",
        };
        console.log(`[ramp] STOP: ${stopped.reason}`);
        break;
      }
      console.log(
        `[ramp] note: ${baseline.totalAttempts - baseline.readyAttempts} attempts are still preparing in ` +
          `unrelated schedules; the ${requiredCohorts} cohorts used by this ramp are all READY`,
      );
    }

    const before = snapshotCounts();
    const result = await runStep(users, i, offset);
    offset += users;
    const after = snapshotCounts();

    const delta = {
      attemptQuestion: after.attemptQuestion - before.attemptQuestion,
      attemptQuestionOption: after.attemptQuestionOption - before.attemptQuestionOption,
    };
    const zeroInsert = delta.attemptQuestion === 0 && delta.attemptQuestionOption === 0;

    const measured = result.summary?.measured ?? {};
    const p95 = measured.applicableLatency?.p95 ?? null;
    const failureRate = measured.examFailureRate ?? null;
    const submissionRate = measured.submissionSuccessRate ?? null;

    const record = {
      users,
      cohortOffset: offset - users,
      startedAt: result.startedAt.toISOString(),
      finishedAt: result.finishedAt.toISOString(),
      durationSeconds: Number(((result.finishedAt - result.startedAt) / 1000).toFixed(1)),
      k6ExitCode: result.exitCode,
      iterations: measured.iterations ?? null,
      httpRequests: measured.httpRequests ?? null,
      p50: measured.applicableLatency?.p50 ?? null,
      p95,
      p99: measured.applicableLatency?.p99 ?? null,
      submissionSuccessRate: submissionRate,
      examFailureRate: failureRate,
      httpFailedRate: measured.httpFailedRate ?? null,
      // --- integrity gate (§27) ---
      snapshotDelta: delta,
      zeroSnapshotInsert: zeroInsert,
      allThresholdsPassed: result.summary?.allThresholdsPassed ?? null,
      gatesAssessed: result.summary?.gatesAssessed ?? [],
      thresholds: result.summary?.thresholds ?? {},
      perEndpoint: result.summary?.perEndpoint ?? {},
      summaryPath: result.summaryPath,
      evidencePresent: result.summary !== null,
    };
    results.push(record);

    console.log(
      `[ramp] users=${users} p95=${p95 === null ? "n/a" : `${p95.toFixed(1)}ms`} ` +
        `p99=${record.p99 === null ? "n/a" : `${record.p99.toFixed(1)}ms`} ` +
        `submitSuccess=${submissionRate === null ? "n/a" : `${(submissionRate * 100).toFixed(0)}%`} ` +
        `examFail=${failureRate === null ? "n/a" : `${(failureRate * 100).toFixed(2)}%`} ` +
        `snapshotDelta=+${delta.attemptQuestion}/+${delta.attemptQuestionOption} ` +
        `gates=${record.allThresholdsPassed === null ? "none-assessed" : record.allThresholdsPassed}`,
    );

    // --- stop conditions (§33) ---
    if (!zeroInsert) {
      stopped = {
        at: users,
        reason: `snapshot insert detected (attempt_question +${delta.attemptQuestion}, attempt_question_option +${delta.attemptQuestionOption}) — Start Exam is materializing snapshots`,
      };
      console.log(`[ramp] STOP: ${stopped.reason}`);
      break;
    }
    if (cfg.stopOnGateFailure && record.allThresholdsPassed === false) {
      stopped = { at: users, reason: `threshold breach at ${users} users; not escalating (§33)` };
      console.log(`[ramp] STOP: ${stopped.reason}`);
      break;
    }
    if (failureRate !== null && failureRate > cfg.stopOnFailureRate) {
      stopped = {
        at: users,
        reason: `exam failure ${(failureRate * 100).toFixed(2)}% exceeds the ${(cfg.stopOnFailureRate * 100).toFixed(0)}% budget; not escalating (§33)`,
      };
      console.log(`[ramp] STOP: ${stopped.reason}`);
      break;
    }
    if (!result.summary) {
      stopped = { at: users, reason: "k6 produced no summary; the run is not evidence and cannot be escalated" };
      console.log(`[ramp] STOP: ${stopped.reason}`);
      break;
    }
  }

  const evidence = {
    generatedAt: new Date().toISOString(),
    profile: cfg.profile,
    label: cfg.label,
    baseUrl: cfg.baseUrl,
    questionsPerAttempt: cfg.questions,
    requestedSteps: cfg.steps,
    baseline,
    results,
    stopped,
    completedAllSteps: stopped === null && results.length === cfg.steps.length,
  };
  const target = path.join(cfg.outDir, `p7-ramp-${cfg.profile}-${Date.now()}.json`);
  await writeFile(target, JSON.stringify(evidence, null, 2), "utf8");
  console.log(`[ramp] evidence written to ${target}`);
  if (stopped) {
    console.log(`[ramp] ramp stopped at ${stopped.at} users: ${stopped.reason}`);
  }
}

main().catch((error) => {
  console.error(`[ramp] FAILED: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
});
