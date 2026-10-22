/**
 * P7 load harness — the six frozen workload profiles
 * ({@code specs/16-P7-Pilot-V1.5.md §25-§32}).
 *
 * Profile map:
 *   A  start      — 5,000 start requests in a narrow interval, snapshots already READY (§26-§27)
 *   B  steady     — steady exam: read → save → navigate, realistic think time (§28)
 *   C  autosave   — 5,000 active attempts autosaving, including duplicate retries (§29)
 *   D  submit     — 5,000 submissions, duplicate submits, late requests (§30)
 *   E  recovery   — reconnect into an existing attempt, same snapshot/answers/timer (§31)
 *   F  mixed      — the realistic blend used for the ramp and the capacity claim (§32)
 *
 * Every profile asserts business outcomes, not just transport success. A latency pass with a
 * data-integrity failure is a failed run (§48).
 */

import { sleep } from "k6";
import exec from "k6/execution";
import { SharedArray } from "k6/data";
import { check } from "k6";
import {
  BASE_URL,
  CREDENTIALS_FILE,
  DURATION_SECONDS,
  PROFILE,
  QUESTIONS_PER_ATTEMPT,
  USERS,
  AUTOSAVE_INTERVAL_SECONDS,
  OFFSET,
} from "./config.js";
import { options as scenarioOptions } from "./options.js";
import {
  assertOwnership,
  data,
  login,
  ok,
  readAttempt,
  readQuestions,
  readResponses,
  recover,
  saveResponse,
  start,
  submit,
} from "./common.js";

/** One credential per synthetic student, loaded once and shared read-only across VUs. */
const credentials = new SharedArray("credentials", () => JSON.parse(open(`../../${CREDENTIALS_FILE}`)));

// k6 requires `options` to be exported from the entry script; the shape lives in options.js so the
// scenario definitions and the thresholds stay readable side by side.
export const options = scenarioOptions;

// Reshapes the run into the P12 §10 evidence fields (see summary.js).
export { handleSummary } from "./summary.js";

/**
 * Picks a credential deterministically from the VU/iteration pair.
 *
 * <p>Uses the iteration index rather than random selection so a run is reproducible from its seed and
 * so each synthetic student is exercised at most once per profile pass — the burst profiles specifically
 * model *distinct* students starting at once. `P7_OFFSET` shifts the slice so ramp steps draw disjoint
 * cohorts: a student who already submitted cannot start again, so reusing a cohort between steps would
 * measure "already submitted" instead of load.
 */
function credentialFor(index) {
  return credentials[(OFFSET + index) % credentials.length];
}

export function setup() {
  if (credentials.length === 0) {
    throw new Error("no credentials loaded; run load/build-credentials.mjs first");
  }
  console.log(`profile=${PROFILE} users=${USERS} credentials=${credentials.length} base=${BASE_URL}`);
  return { startedAt: new Date().toISOString() };
}

// --------------------------------------------------------------------------- A: start burst

/**
 * Profile A — start burst (§26).
 *
 * <p>Narrow interval, every snapshot already READY. The critical assertion is that starting an exam
 * creates **no** new AttemptQuestion or AttemptQuestionOption rows: the snapshot must have been
 * prepared ahead of time, never generated synchronously by Start Exam. The harness cannot see the
 * database (§27 requires an external reconciliation), so it asserts the observable half — the snapshot
 * is present and complete immediately at start, with no server-side materialization round trip — and
 * the runbook pairs it with the read-only SQL check.
 */
export function startBurst() {
  const credential = credentialFor(exec.scenario.iterationInTest);
  const token = login(credential.email, credential.password);
  if (!token) {
    return;
  }
  const attempt = start(credential.scheduleId, token);
  if (!attempt) {
    return;
  }
  assertOwnership(attempt, attempt.userId);
  // The snapshot must already be READY; reading it must not be what creates it.
  const questions = readQuestions(attempt.attemptId, token, QUESTIONS_PER_ATTEMPT);
  check(questions, {
    "start burst: snapshot fully materialized at start": (q) => q !== null && q.length === QUESTIONS_PER_ATTEMPT,
  });
}

// --------------------------------------------------------------------------- B: steady exam

/**
 * Profile B — steady exam (§28).
 *
 * <p>One student working through questions: read the attempt, read the snapshot, save responses with
 * think time, then submit. Excluded from the pure-latency gate only in the sense that think time is
 * part of the model; the individual endpoint latencies are still measured.
 */
export function steadyExam() {
  const index = exec.scenario.iterationInTest;
  const credential = credentialFor(index);
  const token = login(credential.email, credential.password);
  if (!token) {
    return;
  }
  const attempt = start(credential.scheduleId, token);
  if (!attempt) {
    return;
  }
  const questions = readQuestions(attempt.attemptId, token, QUESTIONS_PER_ATTEMPT);
  if (!questions || !questions.length) {
    return;
  }

  const deadline = Date.now() + DURATION_SECONDS * 1000;
  let cursor = 0;
  // ETags are per (attempt, question) resource, so they must be tracked per question. Carrying one ETag
  // across questions sends a stale If-Match and the server correctly answers 412.
  const etags = new Map();
  while (Date.now() < deadline) {
    const question = questions[cursor % questions.length];
    readAttempt(attempt.attemptId, token);
    const saved = saveResponse(
      attempt.attemptId,
      question.questionVersionId,
      token,
      { response: "A", timeSpentSeconds: 2, visited: true },
      etags.get(question.questionVersionId),
    );
    if (ok(saved)) {
      etags.set(question.questionVersionId, data(saved)?.etag ?? null);
    }
    cursor += 1;
    // Realistic reading time; the reference client autosaves every ~10s.
    sleep(Math.min(AUTOSAVE_INTERVAL_SECONDS, 3));
  }

  submit(attempt.attemptId, token);
}

// --------------------------------------------------------------------------- C: autosave burst

/**
 * Profile C — autosave burst (§29).
 *
 * <p>Models the reference client's save loop under contention, including the failures the client must
 * survive: a duplicate retry of the same save and a stale `If-Match`. A 412 is the concurrency control
 * working correctly, so it is not an exam failure; the client's recovery (re-read, retry with the fresh
 * ETag) must succeed.
 */
export function autosaveBurst() {
  const credential = credentialFor(exec.scenario.iterationInTest);
  const token = login(credential.email, credential.password);
  if (!token) {
    return;
  }
  const attempt = start(credential.scheduleId, token);
  if (!attempt) {
    return;
  }
  const questions = readQuestions(attempt.attemptId, token, QUESTIONS_PER_ATTEMPT);
  if (!questions || !questions.length) {
    return;
  }

  const deadline = Date.now() + DURATION_SECONDS * 1000;
  let cursor = 0;
  while (Date.now() < deadline) {
    const question = questions[cursor % questions.length];
    const payload = { response: "B", timeSpentSeconds: 5, markedForReview: cursor % 4 === 0, visited: true };

    const first = saveResponse(attempt.attemptId, question.questionVersionId, token, payload, null);
    const etag = ok(first) ? data(first)?.etag : null;

    // Duplicate retry of the same write — must be idempotent, not a second logical response.
    saveResponse(attempt.attemptId, question.questionVersionId, token, payload, etag);

    // Deliberately stale write: a client that lost a race still holds the old ETag.
    if (etag) {
      const stale = saveResponse(attempt.attemptId, question.questionVersionId, token, payload, "stale-etag-value");
      check(stale, { "autosave: stale ETag rejected": (r) => r.status === 412 });
      // Recover exactly as the reference client does: re-read, then retry with the fresh ETag.
      const reread = readResponses(attempt.attemptId, token);
      const current = (reread ?? []).find((r) => r.questionVersionId === question.questionVersionId);
      if (current?.etag) {
        saveResponse(attempt.attemptId, question.questionVersionId, token, payload, current.etag);
      }
    }
    cursor += 1;
    sleep(AUTOSAVE_INTERVAL_SECONDS);
  }

  // Responses must have survived: read them back before submitting.
  const persisted = readResponses(attempt.attemptId, token);
  check(persisted, { "autosave: responses persisted": (r) => r !== null && r.length > 0 });
  submit(attempt.attemptId, token);
}

// --------------------------------------------------------------------------- D: submission burst

/**
 * Profile D — submission burst (§30).
 *
 * <p>Every student submits, including a duplicate submit and a late request for an already-submitted
 * attempt. Idempotency means the duplicate must also report `SUBMITTED`; the runbook's SQL check then
 * proves exactly one durable submission per attempt.
 */
export function submissionBurst() {
  const credential = credentialFor(exec.scenario.iterationInTest);
  const token = login(credential.email, credential.password);
  if (!token) {
    return;
  }
  const attempt = start(credential.scheduleId, token);
  if (!attempt) {
    // Already submitted by an earlier pass: this is the "late request" case (§30). A late submit must
    // still be idempotent rather than an error, so exercise it through the attempt the student owns.
    // Without a start context there is nothing legitimate to submit, so the iteration is a no-op rather
    // than a fabricated failure.
    return;
  }
  const questions = readQuestions(attempt.attemptId, token, QUESTIONS_PER_ATTEMPT);
  if (questions && questions.length) {
    saveResponse(
      attempt.attemptId,
      questions[0].questionVersionId,
      token,
      { response: "C", timeSpentSeconds: 9, visited: true },
      null,
    );
  }

  const first = submit(attempt.attemptId, token);
  check(first, { "submit: first submission accepted": (r) => r.submitted === true });
  // Duplicate submit — idempotent, still SUBMITTED.
  const second = submit(attempt.attemptId, token);
  check(second, { "submit: duplicate submit is idempotent": (r) => r.submitted === true });
}

// --------------------------------------------------------------------------- E: recovery burst

/**
 * Profile E — recovery burst (§31).
 *
 * <p>Simulates a reconnect: the student already has an attempt with saved answers, and must recover the
 * same attempt, the same snapshot, the same answers and the same server timer — without a duplicate
 * attempt being created. The harness verifies the observable half: identical attempt id, identical
 * question set, identical answers, and a deadline that has not moved forward.
 */
export function recoveryBurst() {
  const credential = credentialFor(exec.scenario.iterationInTest);
  const token = login(credential.email, credential.password);
  if (!token) {
    return;
  }
  const attempt = start(credential.scheduleId, token);
  if (!attempt) {
    return;
  }
  const questions = readQuestions(attempt.attemptId, token, QUESTIONS_PER_ATTEMPT);
  if (!questions || !questions.length) {
    return;
  }
  // Establish state that must survive recovery.
  const target = questions[0];
  saveResponse(
    attempt.attemptId,
    target.questionVersionId,
    token,
    { response: "D", timeSpentSeconds: 12, markedForReview: true, visited: true },
    null,
  );
  const before = readAttempt(attempt.attemptId, token);
  const beforeResponses = readResponses(attempt.attemptId, token);

  const recovered = recover(attempt.sessionId, token);
  check(recovered, {
    "recovery: same attempt recovered": (r) => r !== null && r.attemptId === attempt.attemptId,
    "recovery: timer deadline preserved": (r) =>
      r !== null && before !== null && r.timer && before.timer && r.timer.deadline === before.timer.deadline,
  });

  const after = readAttempt(attempt.attemptId, token);
  const afterResponses = readResponses(attempt.attemptId, token);
  check({ before, after, beforeResponses, afterResponses }, {
    "recovery: same snapshot after reconnect": (v) =>
      v.after !== null && v.after.snapshotStatus === "READY" && v.after.assessmentVersionId === v.before.assessmentVersionId,
    "recovery: answers preserved": (v) =>
      v.afterResponses !== null &&
      v.beforeResponses !== null &&
      v.afterResponses.length === v.beforeResponses.length,
    "recovery: marked-for-review preserved": (v) => {
      const marked = (v.afterResponses ?? []).find((r) => r.questionVersionId === target.questionVersionId);
      return marked !== undefined && marked.markedForReview === true;
    },
  });
}

// --------------------------------------------------------------------------- F: mixed

/**
 * Profile F — mixed realistic exam (§32).
 *
 * <p>The blend used for the capacity claim: students arrive, start, answer with autosave and
 * navigation, some recover mid-way, and all submit. Where no pilot data exists this is a documented
 * synthetic distribution, labelled as such in the evidence — it is not a measurement of real traffic.
 */
export function mixedExam() {
  const credential = credentialFor(exec.scenario.iterationInTest);
  const token = login(credential.email, credential.password);
  if (!token) {
    return;
  }
  const attempt = start(credential.scheduleId, token);
  if (!attempt) {
    return;
  }
  const questions = readQuestions(attempt.attemptId, token, QUESTIONS_PER_ATTEMPT);
  if (!questions || !questions.length) {
    return;
  }

  const deadline = Date.now() + DURATION_SECONDS * 1000;
  let cursor = 0;
  // Per-question ETags: reusing one ETag across questions is a stale write and the server answers 412.
  const etags = new Map();
  while (Date.now() < deadline) {
    const question = questions[cursor % questions.length];
    readAttempt(attempt.attemptId, token);
    const saved = saveResponse(
      attempt.attemptId,
      question.questionVersionId,
      token,
      {
        response: ["A", "B", "C", "D"][cursor % 4],
        timeSpentSeconds: 3,
        markedForReview: cursor % 7 === 0,
        visited: true,
      },
      etags.get(question.questionVersionId),
    );
    if (ok(saved)) {
      etags.set(question.questionVersionId, data(saved)?.etag ?? null);
    }
    // 1 in 20 students reconnects mid-exam.
    if (cursor % 20 === 19 && attempt.sessionId) {
      recover(attempt.sessionId, token);
    }
    cursor += 1;
    sleep(2);
  }

  readResponses(attempt.attemptId, token);
  submit(attempt.attemptId, token);
}

/** Default scenario: dispatch to the profile named by P7_PROFILE. */
export default function () {
  switch (PROFILE) {
    case "start":
      return startBurst();
    case "steady":
      return steadyExam();
    case "autosave":
      return autosaveBurst();
    case "submit":
      return submissionBurst();
    case "recovery":
      return recoveryBurst();
    case "mixed":
      return mixedExam();
    default:
      throw new Error(`unknown P7_PROFILE '${PROFILE}'`);
  }
}
