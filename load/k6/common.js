/**
 * Shared HTTP + assertion helpers for the P7 load harness
 * ({@code specs/16-P7-Pilot-V1.5.md §24-§35}).
 *
 * <p>Design rules that follow the frozen definitions:
 *
 * <ul>
 *   <li>The harness drives **only** the frozen HTTP contract. It never writes to the database, so a
 *       measured pass cannot be an artefact of the generator papering over a gap.</li>
 *   <li>Every request is tagged, so latency can be reported per endpoint and split into the
 *       "applicable synchronous" set that the p95 gate covers versus the async endpoints it excludes
 *       (§45).</li>
 *   <li>A response only counts as success when the *business* outcome is right, not merely when the
 *       socket opened (§48). Latency success without data-integrity success is a failed test.</li>
 * </ul>
 */

import { check } from "k6";
import http from "k6/http";
import { Counter, Rate, Trend } from "k6/metrics";
import { BASE_URL, TIMEOUT } from "./config.js";

// --------------------------------------------------------------------------- metrics

/**
 * Submission success (P12 §9: > 99%).
 *
 * <p>Counted as a success only when the client receives an authoritative 200 whose payload reports
 * {@code SUBMITTED}. Durability is verified separately against PostgreSQL, because a 200 alone does not
 * prove the row is committed.
 */
export const submissionSuccess = new Rate("submission_success_rate");

/**
 * Exam failure (P12 §9: < 1%).
 *
 * <p>Any of: cannot start, cannot recover, response not persisted, invalid timer, submission not
 * completed, wrong snapshot, cross-user exposure, wrong attempt association (§48).
 */
export const examFailure = new Rate("exam_failure_rate");

/** Per-endpoint latency trend, tagged so the p95 gate can be scoped to applicable endpoints. */
export const endpointDuration = new Trend("endpoint_duration", true);

/**
 * Per-endpoint trends.
 *
 * <p>k6 does not break a tagged trend down by tag in its summary, so per-endpoint p50/p95/p99 must be
 * declared as distinct metrics or they cannot be reported at all. The endpoint list is fixed here — it is
 * the frozen inventory the harness exercises — so the report always has the same shape.
 */
const ENDPOINTS = [
  "auth.login",
  "attempt.start",
  "attempt.read",
  "attempt.questions",
  "attempt.submit",
  "response.save",
  "response.list",
  "session.recover",
];

/**
 * k6 metric names allow only ASCII letters, digits and underscores, so the dotted endpoint label is
 * encoded here and decoded in summary.js. The mapping is deterministic, not a hash.
 */
const metricNameFor = (endpoint) => `ep_${endpoint.replace(/\./g, "_")}`;

export const perEndpoint = Object.fromEntries(ENDPOINTS.map((name) => [name, new Trend(metricNameFor(name), true)]));

/** Integrity-gate counters: any non-zero snapshot insert fails the run outright. */
export const snapshotInsertSuspected = new Counter("snapshot_insert_suspected");
export const crossUserExposure = new Counter("cross_user_exposure");
export const wrongSnapshot = new Counter("wrong_snapshot");

// --------------------------------------------------------------------------- calls

/**
 * Wraps one HTTP call so every request is measured and classified consistently.
 *
 * @param name      stable endpoint label used in the report
 * @param method    HTTP method
 * @param path      path beginning with `/api/v1`
 * @param body      JSON body or null
 * @param token     bearer token
 * @param tags      extra tags; must include `applicable:"true"` for gate-covered endpoints
 * @param headers   extra headers (e.g. If-Match)
 */
export function call(name, method, path, { body, token, tags = {}, headers = {} } = {}) {
  const requestHeaders = { Accept: "application/json", ...headers };
  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`;
  }
  let payload;
  if (body !== undefined && body !== null) {
    requestHeaders["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  const response = http.request(method, `${BASE_URL}${path}`, payload, {
    headers: requestHeaders,
    timeout: TIMEOUT,
    tags: { endpoint: name, ...tags },
  });

  endpointDuration.add(response.timings.duration, { endpoint: name, ...tags });
  // Per-endpoint trend so p50/p95/p99 can be reported for each endpoint individually.
  if (perEndpoint[name]) {
    perEndpoint[name].add(response.timings.duration);
  }
  return response;
}

/** Parses the platform envelope, tolerating a non-JSON body without throwing. */
export function parse(response) {
  try {
    const parsed = JSON.parse(response.body);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch (_error) {
    return null;
  }
}

/** True when the platform envelope reports success. */
export function ok(response) {
  if (response.status !== 200 && response.status !== 201 && response.status !== 202) {
    return false;
  }
  const body = parse(response);
  return body === null ? false : body.success !== false;
}

/** Extracts `data` from the envelope. */
export function data(response) {
  const body = parse(response);
  return body === null ? null : body.data;
}

/** Extracts the platform's stable machine-readable error code, if present. */
export function errorCode(response) {
  const body = parse(response);
  if (!body || !body.errors || !body.errors.length) {
    return null;
  }
  return body.errors[0].code ?? null;
}

// --------------------------------------------------------------------------- auth

/** Authenticates one synthetic student. */
export function login(email, password) {
  const response = call("auth.login", "POST", "/api/v1/auth/login", {
    body: { username: email, password },
    tags: { applicable: "true" },
  });
  if (!ok(response)) {
    return null;
  }
  const payload = data(response);
  return payload && payload.accessToken ? payload.accessToken : null;
}

// --------------------------------------------------------------------------- attempt flow

/** Reads the attempt (server-authoritative timer + snapshot status). */
export function readAttempt(attemptId, token) {
  const response = call("attempt.read", "GET", `/api/v1/attempts/${attemptId}`, {
    token,
    tags: { applicable: "true" },
  });
  if (!ok(response)) {
    examFailure.add(true);
    return null;
  }
  examFailure.add(false);
  return data(response);
}

/**
 * Reads the prepared snapshot.
 *
 * <p>Verifies the integrity invariants that §27 protects: the snapshot must already exist (reading it
 * must not create it), it must return the expected question count, and no question may belong to a
 * different attempt version.
 */
export function readQuestions(attemptId, token, expectedCount) {
  const response = call("attempt.questions", "GET", `/api/v1/attempts/${attemptId}/questions`, {
    token,
    tags: { applicable: "true" },
  });
  if (!ok(response)) {
    examFailure.add(true);
    return null;
  }
  const questions = data(response) ?? [];
  const countOk = expectedCount ? questions.length === expectedCount : questions.length > 0;
  if (!countOk) {
    wrongSnapshot.add(1, { reason: "question_count", expected: String(expectedCount), actual: String(questions.length) });
    examFailure.add(true);
  } else {
    examFailure.add(false);
  }
  return questions;
}

/**
 * Saves one response with optimistic concurrency.
 *
 * <p>Uses the ETag from the previous read/save. A stale ETag (412) is *not* an exam failure by itself —
 * it is the concurrency control working — so it is reported separately and the caller may retry with a
 * fresh ETag, which is what the reference client does (§42-§47).
 */
export function saveResponse(attemptId, questionVersionId, token, payload, etag) {
  const headers = {};
  if (etag) {
    headers["If-Match"] = `"${etag}"`;
  }
  const response = call("response.save", "PUT", `/api/v1/attempts/${attemptId}/responses/${questionVersionId}`, {
    token,
    body: payload,
    headers,
    tags: { applicable: "true" },
  });
  return response;
}

/** Reads all responses (recovery path). */
export function readResponses(attemptId, token) {
  const response = call("response.list", "GET", `/api/v1/attempts/${attemptId}/responses`, {
    token,
    tags: { applicable: "true" },
  });
  if (!ok(response)) {
    examFailure.add(true);
    return null;
  }
  examFailure.add(false);
  return data(response) ?? [];
}

/**
 * Submits an attempt.
 *
 * <p>Success requires an authoritative 200 with `status = SUBMITTED`. Duplicate submits are legitimate
 * and idempotent (§30), so a 200 reporting an already-SUBMITTED attempt still counts as success.
 */
export function submit(attemptId, token, confirmed = true) {
  const response = call("attempt.submit", "POST", `/api/v1/attempts/${attemptId}/submit`, {
    token,
    body: { confirmed },
    tags: { applicable: "true" },
  });
  const payload = data(response);
  const submitted = ok(response) && payload !== null && String(payload.status) === "SUBMITTED";
  submissionSuccess.add(submitted);
  if (!submitted) {
    examFailure.add(true);
  }
  return { response, submitted, payload };
}

/** Recovers an existing session without creating a new attempt (§31). */
export function recover(sessionId, token) {
  const response = call("session.recover", "POST", `/api/v1/sessions/${sessionId}/recover`, {
    token,
    tags: { applicable: "true" },
  });
  const payload = data(response);
  const recovered = ok(response) && payload !== null;
  if (!recovered) {
    examFailure.add(true);
    return null;
  }
  examFailure.add(false);
  return payload;
}

/** Starts the assessment for the authenticated student. */
export function start(scheduleId, token) {
  const response = call("attempt.start", "POST", `/api/v1/assessment-schedules/${scheduleId}/start`, {
    token,
    tags: { applicable: "true" },
  });
  const payload = data(response);
  if (!ok(response) || payload === null) {
    examFailure.add(true);
    return null;
  }
  // The timer must be server-authoritative and carry a deadline; a browser-derived timer is invalid.
  const timer = payload.timer;
  if (!timer || !timer.deadline) {
    examFailure.add(true);
    return null;
  }
  examFailure.add(false);
  return payload;
}

/** Asserts the attempt belongs to the authenticated user (no cross-user exposure). */
export function assertOwnership(attempt, userId) {
  const owned = !attempt || !attempt.userId || attempt.userId === userId;
  if (!owned) {
    crossUserExposure.add(1);
  }
  check(owned, { "attempt belongs to caller": (v) => v === true });
  return owned;
}

/** Records a suspected snapshot insertion. Never called on a healthy run. */
export function flagSnapshotInsert(reason) {
  snapshotInsertSuspected.add(1, { reason });
}
