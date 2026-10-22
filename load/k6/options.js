/**
 * k6 options for the six P7 profiles ({@code specs/16-P7-Pilot-V1.5.md §25-§32}).
 *
 * <p>Scenario shape is chosen per profile rather than reusing one:
 *
 * <ul>
 *   <li>Burst profiles (A, D, E) use `shared-iterations` so exactly N distinct students act once. That
 *       is what "5,000 starts in a narrow interval" means; a `constant-vus` loop would silently multiply
 *       the workload and make the number non-comparable to the gate.</li>
 *   <li>Sustained profiles (B, C, F) use `constant-vus` for a fixed duration, because they model
 *       students *working* rather than arriving.</li>
 * </ul>
 *
 * Thresholds are declared here so `k6 run` fails the process on a gate breach instead of leaving the
 * operator to read a table. They are the frozen numbers, not harness-specific ones.
 */

import { DURATION_SECONDS, PROFILE, USERS } from "./config.js";

/**
 * Concurrency for this run.
 *
 * <p>For a concurrency ramp a step of N users means N *concurrent* users, so the default is `USERS`: one
 * VU per simulated student. `P7_VUS` exists only to cap the burst profiles, where the number of iterations
 * is what matters and the VU count merely paces the arrival.
 */
const configuredVus = Number(__ENV.P7_VUS ?? 0);
const VUS = configuredVus > 0 ? Math.min(configuredVus, USERS) : USERS;

/**
 * Every profile models **one student sitting one exam**, so every profile uses `shared-iterations` with
 * exactly `P7_USERS` iterations.
 *
 * <p>This is not a stylistic choice. A `constant-vus` loop would have each VU start a *fresh* exam every
 * time it finished one, so a 60-second run with 500 VUs produced 6,591 logins instead of 500 — the run then
 * measures repeated arrival, not the workload it claims to measure, and the concurrency number on the report
 * is wrong. `P7_DURATION_SECONDS` is the length of the exam *work* inside one iteration, not the length of
 * the scenario.
 */
export function scenarioFor(profile, users, vus, durationSeconds) {
  void profile;
  void durationSeconds;
  return {
    executor: "shared-iterations",
    vus: Math.min(vus, users),
    iterations: users,
    maxDuration: __ENV.P7_MAX_DURATION ?? "45m",
  };
}

/**
 * Thresholds, scoped to the profile that can actually produce the metric.
 *
 * <p>k6 thresholds are global to the run, so a threshold for a metric a profile never emits would fail
 * the run on an empty sample (e.g. zero submissions in the start burst). Each threshold is therefore
 * attached only to the profiles that generate it — the *values* are still the frozen gate numbers.
 */
function thresholdsFor(profile) {
  const common = {
    // p95 < 100 ms on the applicable synchronous endpoints only (P7 §45, P12 §9).
    "http_req_duration{applicable:true}": ["p(95)<100"],
    // Transport-level hygiene: a 5xx is never acceptable as "load".
    http_req_failed: ["rate<0.01"],
    // Exam failure < 1% (P12 §9).
    exam_failure_rate: ["rate<0.01"],
    // Integrity gate: these must be exactly zero.
    snapshot_insert_suspected: ["count==0"],
    cross_user_exposure: ["count==0"],
  };
  // Only the profiles that submit can be held to the submission-success gate.
  if (["submit", "steady", "autosave", "mixed"].includes(profile)) {
    common.submission_success_rate = ["rate>0.99"];
  }
  return common;
}

export const options = {
  scenarios: {
    [PROFILE]: scenarioFor(PROFILE, USERS, VUS, DURATION_SECONDS),
  },
  thresholds: thresholdsFor(PROFILE),
  // Keep memory flat: the burst is short, the summaries are what matter.
  discardResponseBodies: false,
  summaryTrendStats: ["avg", "min", "med", "p(50)", "p(90)", "p(95)", "p(99)", "max", "count"],
  userAgent: "exam-platform-p7-load-harness/1.0",
};
