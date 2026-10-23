#!/usr/bin/env node
/**
 * Builds the k6 credential manifest ({@code specs/16-P7-Pilot-V1.5.md §24}).
 *
 * <p>k6 cannot read a file per VU cheaply, and the harness must not embed secrets, so credentials live in one
 * JSON array that the scenario loads once.
 *
 * <p><b>Why the pairs are read from the database.</b> Each entry is one `(student, schedule)` pair with a
 * *fresh* attempt — `attempt_status = NOT_STARTED` and `snapshot_status = READY`. An earlier version derived
 * the cohort from a count of already-consumed attempts and sliced the manifest by that offset, which is wrong
 * whenever several runs each started at their own offset: the consumed set is not a contiguous prefix, so the
 * slice overlapped students who had already started and 55% of iterations failed with "no prepared attempt".
 * Enumerating the genuinely available pairs removes that whole class of error.
 *
 * <p>A student who has already submitted on a schedule cannot start again, so the ramp's 9,100 starts need
 * more pairs than one schedule of 5,000 students provides. Every prepared schedule contributes another
 * independent cohort.
 *
 * Usage: node load/build-credentials.mjs [--students=5000] [--schedules=id1,id2]
 */

import { execFileSync } from "node:child_process";
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, ...v] = a.replace(/^--/, "").split("=");
    return [k, v.join("=") || "true"];
  }),
);

const outDir = args.out ?? "load/out";
const manifest = JSON.parse(await readFile(path.join(outDir, "manifest.json"), "utf8"));
const limit = args.students ? Number(args.students) : 1_000_000;

function psql(sql) {
  const out = execFileSync(
    "docker",
    [
      "exec",
      process.env.P7_PSQL_CONTAINER ?? "exam-platform-postgres-1",
      "psql",
      "-U",
      process.env.P7_PSQL_USER ?? "exam_app",
      "-d",
      process.env.P7_PSQL_DB ?? "exam_platform",
      "-t",
      "-A",
      "-F",
      "\t",
      "-c",
      sql,
    ],
    { encoding: "utf8", maxBuffer: 128 * 1024 * 1024 },
  );
  return out
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.split("\t"));
}

/**
 * Every (schedule, student) pair whose attempt is prepared and not yet started.
 *
 * <p>Ordered by schedule then email so the workload is deterministic and a re-run against the same database
 * state produces the same cohort.
 */
const scheduleFilter = args.schedules
  ? `AND a.assessment_schedule_id IN (${String(args.schedules)
      .split(",")
      .map((s) => `'${s.trim()}'`)
      .join(",")})`
  : "";

const rows = psql(
  "SELECT a.assessment_schedule_id, u.email " +
    "FROM student_attempt a JOIN \"user\" u ON u.id = a.user_id " +
    "WHERE a.attempt_status = 'NOT_STARTED' " +
    "AND a.snapshot_status = 'READY' " +
    `AND a.assessment_version_id = '${manifest.assessmentVersionId}' ` +
    scheduleFilter +
    " ORDER BY a.assessment_schedule_id, u.email;",
);

// Provisioning issues a distinct random credential per account, so the password is looked up per email
// rather than read from a single shared manifest field. `studentPassword` remains as a fallback for older
// manifests, where every student really did share one credential.
const perStudent = manifest.studentCredentials ?? {};

const credentials = [];
const missing = [];
/**
 * The ramp population is the synthetic students and nothing else.
 *
 * <p>The cohort CLI also provisions the corpus author and reviewer, and because it assigns every member
 * {@code STUDENT} they receive prepared attempts like any student — so the query above returns them too.
 * They are not part of the exam population, and they have no entry in {@code studentCredentials}, so the
 * {@code studentPassword} fallback gave them another account's credential. At offset 0 they were the first
 * two entries, which made exactly two logins fail with BAD_CREDENTIALS before any student ran.
 *
 * <p>Filtering on the authoritative student list fixes both the wrong password and the population count:
 * the gate is about 5,000 students, not 5,000 students plus staff.
 */
const knownStudents = new Set(Object.keys(perStudent));
for (const [scheduleId, email] of rows) {
  if (knownStudents.size > 0 && !knownStudents.has(email)) {
    continue;
  }
  const password = perStudent[email] ?? manifest.studentPassword;
  if (!password) {
    missing.push(email);
    continue;
  }
  credentials.push({ email, password, scheduleId });
}
if (missing.length > 0) {
  // Failing loudly matters here: a credential manifest with holes makes the ramp silently skip VUs, and a
  // short ramp looks like a passing ramp.
  console.error(`missing credentials for ${missing.length} student(s), e.g. ${missing[0]}`);
  console.error("re-run load/seed.mjs with --apply-sql, then rebuild this manifest");
  process.exitCode = 1;
}

const selected = credentials.slice(0, limit);
const target = path.join(outDir, "credentials.json");
await writeFile(target, JSON.stringify(selected, null, 2), "utf8");

const needed = 100 + 500 + 1000 + 2500 + 5000;
const schedules = new Set(selected.map((c) => c.scheduleId));
console.log(`wrote ${selected.length} credentials to ${target}`);
console.log(`schedules=${schedules.size} availablePairs=${credentials.length} rampNeeds=${needed}`);
console.log(`assessmentVersionId=${manifest.assessmentVersionId}`);
if (selected.length < needed) {
  console.warn(
    `WARNING: only ${selected.length} fresh pairs available but the ramp needs ${needed}; ` +
      "prepare more schedules or lower the steps",
  );
}
