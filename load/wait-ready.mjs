#!/usr/bin/env node
/**
 * Blocks until every prepared attempt has reached `snapshot_status = READY`
 * ({@code specs/16-P7-Pilot-V1.5.md §23, §26-§27}).
 *
 * <p>This is a required step, not a convenience. Start Exam must consume an already-prepared snapshot; if
 * the harness ran while snapshots were still PENDING, the start burst would measure snapshot *creation*
 * and the zero-insert assertion would be meaningless. Preparation is asynchronous (a background job with a
 * batch size and an interval), so waiting is the only correct way to sequence it.
 *
 * <p>Read-only: it selects counts, nothing else.
 *
 * Usage: node load/wait-ready.mjs [--timeout-seconds=5400] [--interval-seconds=15]
 */

import { execFileSync } from "node:child_process";

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, ...v] = a.replace(/^--/, "").split("=");
    return [k, v.join("=") || "true"];
  }),
);

const container = args.container ?? process.env.P7_PSQL_CONTAINER ?? "exam-platform-postgres-1";
const user = args.user ?? process.env.P7_PSQL_USER ?? "exam_app";
const db = args.db ?? process.env.P7_PSQL_DB ?? "exam_platform";
const timeoutSeconds = Number(args["timeout-seconds"] ?? process.env.P7_READY_TIMEOUT ?? 5400);
const intervalSeconds = Number(args["interval-seconds"] ?? process.env.P7_READY_INTERVAL ?? 15);

/** Runs one read-only scalar query and returns its numeric result. */
function scalar(sql) {
  const out = execFileSync(
    "docker",
    ["exec", container, "psql", "-U", user, "-d", db, "-t", "-A", "-c", sql],
    { encoding: "utf8" },
  );
  return Number(out.trim());
}

const started = Date.now();
let lastPrepared = -1;
let stalledRounds = 0;

for (;;) {
  const total = scalar("SELECT count(*) FROM student_attempt;");
  const ready = scalar("SELECT count(*) FROM student_attempt WHERE snapshot_status = 'READY';");
  const failed = scalar("SELECT count(*) FROM student_attempt WHERE snapshot_status = 'FAILED';");
  const pending = total - ready - failed;

  const elapsed = Math.round((Date.now() - started) / 1000);
  console.log(`[ready] elapsed=${elapsed}s ready=${ready}/${total} pending=${pending} failed=${failed}`);

  if (total === 0) {
    console.error("[ready] no attempts exist; run load/seed.mjs then POST prepare-attempts");
    process.exit(1);
  }
  if (failed > 0) {
    // A failed snapshot is recoverable: the preparation service exposes an explicit retry policy (§23),
    // and the state is reset to PENDING by the operator (or by a stack restart reclaiming stale work).
    // Report it, but keep waiting rather than aborting, because the remaining backlog is unaffected.
    console.warn(
      `[ready] WARNING ${failed} attempt(s) FAILED preparation; the run cannot proceed until these are ` +
        "reset to PENDING and re-prepared (see runbook §3.3). Still waiting for the rest.",
    );
  }
  if (ready + failed === total) {
    if (failed > 0) {
      console.error(
        `[ready] ${failed} of ${total} attempts are FAILED and cannot be used for a start burst. ` +
          "Reset them to PENDING and re-run preparation (runbook §3.3), or seed more students.",
      );
      process.exit(1);
    }
    const aq = scalar("SELECT count(*) FROM attempt_question;");
    const aqo = scalar("SELECT count(*) FROM attempt_question_option;");
    console.log(`[ready] all ${total} snapshots READY in ${elapsed}s`);
    console.log(`[ready] baseline: attempt_question=${aq} attempt_question_option=${aqo}`);
    console.log("[ready] record these two numbers; the start burst must not increase them (§27)");
    process.exit(0);
  }

  // A backlog that stops shrinking means the worker is not making progress; fail loudly rather than
  // waiting out the whole timeout.
  if (ready === lastPrepared) {
    stalledRounds += 1;
    if (stalledRounds >= 12) {
      console.error(`[ready] no progress for ${stalledRounds * intervalSeconds}s; is the delivery worker enabled?`);
      process.exit(1);
    }
  } else {
    stalledRounds = 0;
    lastPrepared = ready;
  }

  if (elapsed > timeoutSeconds) {
    console.error(`[ready] timed out after ${timeoutSeconds}s with ${pending} still pending`);
    process.exit(1);
  }

  await new Promise((resolve) => setTimeout(resolve, intervalSeconds * 1000));
}
