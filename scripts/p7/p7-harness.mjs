#!/usr/bin/env node
/**
 * P7 scale-validation load harness (specs/16-P7-Pilot-V1.5.md §24-§35, §44-§48, §141).
 *
 * Dependency-free Node (>=18). It drives the FROZEN HTTP contract only and never writes to the
 * database: database reconciliation is a separate read-only step (scripts/p7/reconcile.sql).
 *
 * It measures the runtime exam path: authenticate -> read attempt/questions -> start -> autosave
 * with ETag -> recover -> submit. It emits a machine-readable evidence file per run containing the
 * release, environment, seed, workload profile, latency percentiles, error classification, and
 * HTTP-level assertions.
 *
 * IMPORTANT
 *  - This harness has NOT been executed against a production-like environment (none exists on the
 *    development host). See docs/testing/p7/README.md.
 *  - Student credentials must be provisioned out of band: the frozen P1 onboarding assigns a
 *    non-usable credential and reset delivery is not built (GAP-002), so a load cohort cannot
 *    self-authenticate. Pass them in P7_CREDENTIALS_FILE.
 *
 * Usage:
 *   node scripts/p7/p7-harness.mjs --profile=start --users=100
 *   node scripts/p7/p7-harness.mjs --profile=ramp
 *   node scripts/p7/p7-harness.mjs --profile=autosave --users=500
 *
 * Env/args: P7_BASE_URL, P7_SEED, P7_PROFILE, P7_USERS, P7_RAMP, P7_QUESTIONS, P7_CONCURRENCY,
 *           P7_DURATION_SECONDS, P7_OUT, P7_CREDENTIALS_FILE, P7_ADMIN_EMAIL, P7_ADMIN_PASSWORD,
 *           P7_RELEASE, P7_ENVIRONMENT, P7_LABEL.
 */

import { mkdir, writeFile, readFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import { pathToFileURL } from "node:url";
import path from "node:path";

// --------------------------------------------------------------------------- config

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, ...v] = a.replace(/^--/, "").split("=");
    return [k, v.join("=") || "true"];
  }),
);

const cfg = {
  baseUrl: args["base-url"] ?? process.env.P7_BASE_URL ?? "http://localhost:8080",
  seed: Number(args["seed"] ?? process.env.P7_SEED ?? 20261015),
  profile: args.profile ?? process.env.P7_PROFILE ?? "start",
  users: Number(args.users ?? process.env.P7_USERS ?? 100),
  ramp: (args.ramp ?? process.env.P7_RAMP ?? "100,500,1000,2000,3000,4000,5000")
    .split(",").map((n) => Number(n.trim())).filter((n) => n > 0),
  questions: Number(args.questions ?? process.env.P7_QUESTIONS ?? 100),
  concurrency: Number(args.concurrency ?? process.env.P7_CONCURRENCY ?? 200),
  durationSeconds: Number(args["duration-seconds"] ?? process.env.P7_DURATION_SECONDS ?? 120),
  outDir: args.out ?? process.env.P7_OUT ?? path.join(process.cwd(), "p7-evidence"),
  credentialsFile: args["credentials-file"] ?? process.env.P7_CREDENTIALS_FILE ?? null,
  scheduleId: args["schedule-id"] ?? process.env.P7_SCHEDULE_ID ?? null,
  adminEmail: args["admin-email"] ?? process.env.P7_ADMIN_EMAIL ?? null,
  adminPassword: args["admin-password"] ?? process.env.P7_ADMIN_PASSWORD ?? null,
  release: args.release ?? process.env.P7_RELEASE ?? "unknown",
  environment: args.environment ?? process.env.P7_ENVIRONMENT ?? "unknown",
  label: args.label ?? process.env.P7_LABEL ?? "p7-run",
};

// --------------------------------------------------------------------------- deterministic PRNG

function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// --------------------------------------------------------------------------- metrics

class Metrics {
  constructor() {
    this.ops = new Map();
    this.errors = [];
    this.assertions = [];
  }
  record(op, ms, ok, errorClass = null) {
    if (!this.ops.has(op)) this.ops.set(op, { count: 0, ok: 0, errors: 0, samples: [], errorClasses: {} });
    const m = this.ops.get(op);
    m.count++;
    if (ok) m.ok++;
    else {
      m.errors++;
      if (errorClass) m.errorClasses[errorClass] = (m.errorClasses[errorClass] ?? 0) + 1;
    }
    m.samples.push(ms);
  }
  error(op, errorClass, detail) {
    this.errors.push({ op, errorClass, detail: String(detail).slice(0, 300), at: new Date().toISOString() });
  }
  assert(name, passed, detail = "") {
    this.assertions.push({ name, passed, detail });
  }
  summary() {
    const out = {};
    for (const [op, m] of this.ops) {
      const s = [...m.samples].sort((a, b) => a - b);
      out[op] = {
        count: m.count,
        ok: m.ok,
        errors: m.errors,
        successRate: m.count ? +(m.ok / m.count).toFixed(4) : 0,
        p50: pct(s, 50),
        p95: pct(s, 95),
        p99: pct(s, 99),
        max: s.length ? +s[s.length - 1].toFixed(1) : 0,
        errorClasses: m.errorClasses,
      };
    }
    return out;
  }
}

function pct(sorted, p) {
  if (!sorted.length) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return +sorted[Math.max(0, idx)].toFixed(1);
}

// --------------------------------------------------------------------------- HTTP

async function call(method, urlPath, { token, body, headers = {}, expect = [] } = {}) {
  const requestId = randomUUID();
  const started = performance.now();
  try {
    const response = await fetch(`${cfg.baseUrl}${urlPath}`, {
      method,
      headers: {
        "Content-Type": "application/json",
        "X-Request-Id": requestId,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30_000),
    });
    const ms = performance.now() - started;
    let json = null;
    try {
      json = await response.json();
    } catch {
      /* empty body */
    }
    const expected = expect.length === 0 || expect.includes(response.status);
    return { status: response.status, ms, json, requestId, expected, etag: response.headers.get("etag") };
  } catch (failure) {
    const ms = performance.now() - started;
    const errorClass = failure?.name === "TimeoutError" ? "timeout" : "network";
    return { status: 0, ms, json: null, requestId, expected: false, errorClass };
  }
}

function classify(result) {
  if (result.errorClass) return result.errorClass;
  if (result.status >= 500) return "http_5xx";
  if (result.status === 412) return "precondition";
  if (result.status >= 400) return "http_4xx";
  return null;
}

async function track(metrics, op, promise) {
  const result = await promise;
  metrics.record(op, result.ms, result.expected, result.expected ? null : classify(result));
  if (!result.expected) metrics.error(op, classify(result), `status=${result.status}`);
  return result;
}

// --------------------------------------------------------------------------- auth + sessions

async function login(email, password) {
  const result = await call("POST", "/api/v1/auth/login", { body: { email, password }, expect: [200] });
  if (!result.expected) throw new Error(`login failed for a load user: status=${result.status}`);
  return result.json.data.accessToken;
}

async function loadCredentials() {
  if (!cfg.credentialsFile) return [];
  return JSON.parse(await readFile(cfg.credentialsFile, "utf8"));
}

async function studentSession(metrics, credential) {
  const token = await login(credential.email, credential.password);
  const attempts = await track(metrics, "attempt.list", call("GET", "/api/v1/attempts", { token, expect: [200] }));
  const list = attempts.json?.data ?? [];
  const active = list.find((a) => a.attemptStatus === "NOT_STARTED" || a.attemptStatus === "IN_PROGRESS");
  return {
    token,
    attemptId: active?.attemptId ?? list[0]?.attemptId ?? null,
    status: active?.attemptStatus ?? null,
    scheduleId: credential.scheduleId ?? cfg.scheduleId,
    email: credential.email,
  };
}

// --------------------------------------------------------------------------- pooled burst

async function burst(metrics, count, concurrency, task) {
  const started = performance.now();
  let next = 0;
  const workers = Array.from({ length: Math.min(concurrency, count) }, async () => {
    while (next < count) {
      const index = next++;
      try {
        await task(index);
      } catch (failure) {
        metrics.error("burst", "task", failure?.message ?? failure);
      }
    }
  });
  await Promise.all(workers);
  return (performance.now() - started) / 1000;
}

// --------------------------------------------------------------------------- profiles

async function startOne(metrics, session) {
  if (!session.attemptId) return;
  const result = await track(metrics, "start",
    call("POST", `/api/v1/assessment-schedules/${session.scheduleId ?? ""}/start`, { token: session.token, expect: [200, 409] }));
  if (result.status === 200) {
    const attemptId = result.json?.data?.attemptId;
    session.attemptId = attemptId ?? session.attemptId;
    session.sessionId = result.json?.data?.sessionId;
  }
}

async function profileStart(metrics, sessions) {
  const seconds = await burst(metrics, sessions.length, cfg.concurrency, (i) => startOne(metrics, sessions[i]));
  metrics.assert("start.burst.window", true, `${sessions.length} starts released in ${seconds.toFixed(2)}s`);
}

async function profileAutosave(metrics, sessions) {
  const rng = mulberry32(cfg.seed);
  await burst(metrics, sessions.length, cfg.concurrency, async (i) => {
    const session = sessions[i];
    if (!session.attemptId) return;
    const questions = await track(metrics, "question.list",
      call("GET", `/api/v1/attempts/${session.attemptId}/questions`, { token: session.token, expect: [200] }));
    const list = questions.json?.data ?? [];
    const pick = list.slice(0, Math.max(1, Math.min(3, list.length)));
    for (const question of pick) {
      const body = { response: "A", timeSpentSeconds: 5, markedForReview: false, visited: true };
      const saved = await track(metrics, "response.save",
        call("PUT", `/api/v1/attempts/${session.attemptId}/responses/${question.questionVersionId}`,
          { token: session.token, body, expect: [200, 412] }));
      if (!saved.etag) continue;
      // 10% of saves exercise the stale-write precondition and then retry with the fresh ETag.
      if (rng() < 0.10) {
        await track(metrics, "response.save.stale",
          call("PUT", `/api/v1/attempts/${session.attemptId}/responses/${question.questionVersionId}`,
            { token: session.token, body, headers: { "If-Match": '"resp-stale-0"' }, expect: [412] }));
      }
      await track(metrics, "response.save.retry",
        call("PUT", `/api/v1/attempts/${session.attemptId}/responses/${question.questionVersionId}`,
          { token: session.token, body, headers: { "If-Match": saved.etag }, expect: [200, 412] }));
    }
  });
}

async function profileRecovery(metrics, sessions) {
  await burst(metrics, sessions.length, cfg.concurrency, async (i) => {
    const session = sessions[i];
    if (!session.attemptId) return;
    const attempt = await track(metrics, "attempt.read",
      call("GET", `/api/v1/attempts/${session.attemptId}`, { token: session.token, expect: [200] }));
    metrics.assert("recovery.sameAttempt",
      attempt.json?.data?.attemptId === session.attemptId,
      `attemptId=${session.attemptId}`);
    await track(metrics, "response.recovery",
      call("GET", `/api/v1/attempts/${session.attemptId}/responses`, { token: session.token, expect: [200] }));
  });
}

async function submitOne(metrics, session) {
  if (!session.attemptId) return;
  const first = await track(metrics, "submit",
    call("POST", `/api/v1/attempts/${session.attemptId}/submit`, { token: session.token, body: { confirmed: true }, expect: [200] }));
  if (first.status !== 200) return;
  const submittedOn = first.json?.data?.submittedOn;
  // Duplicate submit must be idempotent (same submittedOn, durably SUBMITTED).
  const duplicate = await track(metrics, "submit.duplicate",
    call("POST", `/api/v1/attempts/${session.attemptId}/submit`, { token: session.token, body: { confirmed: true }, expect: [200] }));
  metrics.assert("submit.idempotent",
    duplicate.json?.data?.submittedOn === submittedOn && first.json?.data?.status === "SUBMITTED",
    `attemptId=${session.attemptId} submittedOn=${submittedOn}`);
}

async function profileSubmit(metrics, sessions) {
  await burst(metrics, sessions.length, cfg.concurrency, (i) => submitOne(metrics, sessions[i]));
}

async function profileMixed(metrics, sessions) {
  const rng = mulberry32(cfg.seed);
  await burst(metrics, sessions.length, cfg.concurrency, async (i) => {
    const session = sessions[i];
    if (!session.attemptId) return;
    await track(metrics, "attempt.read",
      call("GET", `/api/v1/attempts/${session.attemptId}`, { token: session.token, expect: [200] }));
    const questions = await track(metrics, "question.list",
      call("GET", `/api/v1/attempts/${session.attemptId}/questions`, { token: session.token, expect: [200] }));
    const list = questions.json?.data ?? [];
    for (const question of list.slice(0, Math.min(5, list.length))) {
      await track(metrics, "response.save", call("PUT",
        `/api/v1/attempts/${session.attemptId}/responses/${question.questionVersionId}`,
        { token: session.token, body: { response: "A", visited: true }, expect: [200] }));
    }
    if (rng() < 0.05) await profileRecovery(metrics, [session]);
    if (rng() < 0.5) await submitOne(metrics, session);
  });
}

async function profileSoak(metrics, sessions) {
  const deadline = Date.now() + cfg.durationSeconds * 1000;
  let cycles = 0;
  while (Date.now() < deadline) {
    await profileMixed(metrics, sessions);
    cycles++;
  }
  metrics.assert("soak.cycles", cycles > 0, `${cycles} cycles in ${cfg.durationSeconds}s`);
}

async function profileRamp(metrics, sessions) {
  if (sessions.length === 0) {
    metrics.assert("ramp.noSessions", false, "no authenticated load sessions; nothing to ramp");
    return;
  }
  for (const step of cfg.ramp) {
    if (sessions.length < step) {
      console.log(`  ramp step ${step} users ... SKIPPED (only ${sessions.length} sessions available)`);
      metrics.assert(`ramp.${step}.available`, false, `needed ${step}, have ${sessions.length}`);
      continue;
    }
    process.stdout.write(`  ramp step ${step} users ... `);
    const slice = sessions.slice(0, step);
    const seconds = await burst(metrics, slice.length, cfg.concurrency, (i) => startOne(metrics, slice[i]));
    const stats = metrics.summary()["start"] ?? { successRate: 1, p95: 0 };
    console.log(`${seconds.toFixed(2)}s (start success ${(stats.successRate * 100).toFixed(2)}%, p95 ${stats.p95}ms)`);
    if (stats.successRate < 0.99) {
      metrics.assert(`ramp.${step}.successRate`, false, `start success ${stats.successRate}`);
      console.log("  start success below 99% — stopping the ramp rather than escalating (§33).");
      break;
    }
  }
}

// --------------------------------------------------------------------------- main

async function main() {
  const credentials = await loadCredentials();
  if (credentials.length < cfg.users) {
    console.warn(`WARN: ${credentials.length} credentials supplied but ${cfg.users} users requested.`);
  }
  const metrics = new Metrics();

  const sessions = [];
  const sessionCount = Math.min(cfg.users, credentials.length);
  for (let i = 0; i < sessionCount; i++) {
    try {
      sessions.push(await studentSession(metrics, credentials[i]));
    } catch (failure) {
      metrics.error("auth", "login", failure?.message ?? failure);
    }
  }
  console.log(`Authenticated ${sessions.length}/${sessionCount} load users (seed=${cfg.seed}).`);

  const runners = {
    ramp: profileRamp,
    start: profileStart,
    autosave: profileAutosave,
    recovery: profileRecovery,
    submit: profileSubmit,
    mixed: profileMixed,
    soak: profileSoak,
    spike: profileStart,
  };
  const runner = runners[cfg.profile];
  if (!runner) throw new Error(`Unknown profile '${cfg.profile}'. Known: ${Object.keys(runners).join(", ")}`);

  const startedAt = new Date().toISOString();
  await runner(metrics, sessions);
  const finishedAt = new Date().toISOString();

  const evidence = {
    harness: "scripts/p7/p7-harness.mjs",
    phase: "P7",
    label: cfg.label,
    release: cfg.release,
    environment: cfg.environment,
    baseUrl: cfg.baseUrl,
    seed: cfg.seed,
    profile: cfg.profile,
    requestedUsers: cfg.users,
    authenticatedUsers: sessions.length,
    concurrency: cfg.concurrency,
    questionsPerAttempt: cfg.questions,
    startedAt,
    finishedAt,
    latencyNote:
      "Latency is measured client-side at the harness, single origin. p50/p95/p99 retained per §44; " +
      "Nginx/application/database correlation requires the staged environment.",
    metrics: metrics.summary(),
    assertions: metrics.assertions,
    errors: metrics.errors.slice(0, 500),
    errorSummary: { total: metrics.errors.length },
    reconciliation: {
      required: true,
      note: "Database integrity gates must be verified separately with scripts/p7/reconcile.sql (§49-§54).",
    },
  };

  await mkdir(cfg.outDir, { recursive: true });
  const file = path.join(cfg.outDir, `${cfg.label}-${cfg.profile}-${Date.now()}.json`);
  await writeFile(file, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");

  console.log("\nP7 harness run complete");
  console.table(Object.fromEntries(Object.entries(evidence.metrics).map(([op, m]) => [op, {
    count: m.count, success: `${(m.successRate * 100).toFixed(2)}%`, p50: m.p50, p95: m.p95, p99: m.p99,
  }])));
  const failed = metrics.assertions.filter((a) => !a.passed);
  console.log(failed.length ? `ASSERTION FAILURES: ${JSON.stringify(failed)}` : "All harness assertions passed.");
  console.log(`Evidence: ${file}`);
  if (failed.length) process.exitCode = 2;
}

// Only run when invoked directly, so importing the module has no side effects.
const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(process.argv[1]).href;

if (invokedDirectly) {
  main().catch((failure) => {
    console.error("P7 harness failed:", failure);
    process.exitCode = 1;
  });
}

export { main, Metrics, pct, mulberry32 };
