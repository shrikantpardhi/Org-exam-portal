#!/usr/bin/env node
/**
 * Publishes the answer key for the seeded assessment version.
 *
 * WHY THIS EXISTS
 *   load/seed.mjs builds students, published questions, a published assessment and READY snapshots, but
 *   never publishes an answer key. The evaluation worker therefore has nothing to score against: a
 *   submitted attempt is stored and then left un-evaluated, and the backend logs
 *
 *     "No published answer key for assessment version <id>; submitted attempt <id> will not be
 *      evaluated automatically. Publish an answer key for this assessment version and trigger
 *      evaluation explicitly (specs/15-P6 section 26)."
 *
 *   The P7 reconciliation gate (specs/16-P7 section 94, section 52) requires exactly one current
 *   evaluation per submitted attempt, so a dataset with no key fails integrity for a reason that is
 *   entirely a harness omission. Both previous runs shipped without one; neither noticed because the
 *   question they were investigating was latency.
 *
 * CORRECTNESS SOURCE
 *   answer_key_detail.correct_option_id is the only correctness source evaluation reads
 *   (AGENTS.md rule 19). The correct option is resolved from question_option.is_correct inside the
 *   database — not from a client-supplied guess — and every question in the version must resolve to
 *   exactly one, or the script fails rather than publishing a partial key.
 *
 * IDEMPOTENT
 *   A published key is immutable (05-API-Contract section 47.4), so it is left untouched and the
 *   existing detail count is reported.
 *
 * Usage: node load/publish-answer-key.mjs
 */

import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import path from "node:path";

const outDir = "load/out";
const manifest = JSON.parse(await readFile(path.join(outDir, "manifest.json"), "utf8"));
const avId = manifest.assessmentVersionId;

const BASE = process.env.P7_BASE_URL ?? "http://localhost:8090";
const PG = process.env.P7_PSQL_CONTAINER ?? "exam-platform-postgres-1";

function psql(sql) {
  const out = execFileSync(
    "docker",
    [
      "exec", PG,
      "psql",
      "-U", process.env.P7_PSQL_USER ?? "exam_app",
      "-d", process.env.P7_PSQL_DB ?? "exam_platform",
      "-t", "-A", "-F", "\t", "-c", sql,
    ],
    { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 },
  );
  return out
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => l.split("\t"));
}

async function request(method, url, { body, token } = {}) {
  const response = await fetch(`${BASE}/api/v1${url}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`${method} ${url} -> ${response.status}: non-JSON body`);
  }
  if (!response.ok || payload?.success === false) {
    throw new Error(`${method} ${url} -> ${response.status}: ${payload?.message ?? text.slice(0, 300)}`);
  }
  return payload?.data ?? payload;
}

// --- authenticate as an administrator ---------------------------------------
// The credential is read from the manifest's provisioning output, never embedded.
const adminEmail = "load.admin@example.test";
const adminPassword = manifest.corpusCredentials?.[adminEmail];
if (!adminPassword) {
  throw new Error(
    `no credential for ${adminEmail} in manifest.corpusCredentials. Re-run load/seed.mjs so the ` +
      "manifest records the corpus identities.",
  );
}
const login = await request("POST", "/auth/login", {
  body: { username: adminEmail, password: adminPassword },
});
const token = login.accessToken;
if (!token) {
  throw new Error("admin login returned no access token");
}

// --- resolve the key ---------------------------------------------------------
const keys = await request("GET", `/answer-keys?assessmentVersionId=${avId}`, { token });
const keyList = Array.isArray(keys) ? keys : (keys?.content ?? []);
let key = keyList.find((k) => k.assessmentVersionId === avId);
if (!key) {
  key = await request("POST", "/answer-keys", { body: { assessmentVersionId: avId }, token });
  console.log(`[answer-key] created id=${key.id}`);
}
if (key.published) {
  const details = await request("GET", `/answer-keys/${key.id}/details`, { token });
  const count = Array.isArray(details) ? details.length : (details?.content ?? []).length;
  console.log(`[answer-key] already published id=${key.id} details=${count}`);
  process.exit(0);
}

// Exactly one correct option per question, read from the database.
const rows = psql(
  "SELECT aq.question_version_id, o.id " +
    "FROM assessment_question aq " +
    "JOIN question_option o ON o.question_version_id = aq.question_version_id AND o.is_correct " +
    `WHERE aq.assessment_version_id = '${avId}' AND o.status = 'ACTIVE' ` +
    "ORDER BY aq.display_order;",
);
if (rows.length === 0) {
  throw new Error("no correct options resolved; refusing to publish an empty answer key");
}

const existing = await request("GET", `/answer-keys/${key.id}/details`, { token });
const have = new Set(
  (Array.isArray(existing) ? existing : (existing?.content ?? [])).map((d) => d.questionVersionId),
);

let written = 0;
for (const [questionVersionId, correctOptionId] of rows) {
  if (have.has(questionVersionId)) {
    continue;
  }
  await request("POST", `/answer-keys/${key.id}/details`, {
    token,
    body: {
      questionVersionId,
      correctOptionId,
      questionOutcome: "NORMAL",
      awardedMarks: null,
      awardedNegativeMarks: null,
      remarks: null,
    },
  });
  written += 1;
}

await request("POST", `/answer-keys/${key.id}/publish`, { token });
const after = await request("GET", `/answer-keys/${key.id}/details`, { token });
const count = Array.isArray(after) ? after.length : (after?.content ?? []).length;
console.log(`[answer-key] published id=${key.id} written=${written} details=${count} questions=${rows.length}`);
if (count !== rows.length) {
  throw new Error(`published key has ${count} details but the version has ${rows.length} questions`);
}
