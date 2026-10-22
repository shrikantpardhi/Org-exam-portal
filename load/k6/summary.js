/**
 * k6 handleSummary: writes the machine-readable evidence file
 * ({@code specs/16-P7-Pilot-V1.5.md §34, specs/21-P12-Hardening-V1.5.md §10}).
 *
 * <p>P12 §10 requires evidence to identify the software version, environment, workload, data volume,
 * duration, result, limitations, operator and date. k6's own JSON export does not carry most of that, so
 * the summary is reshaped here into exactly those fields and written next to the raw k6 output. Nothing
 * is inferred: p50/p95/p99 come straight from the run, and a metric that was never emitted is recorded
 * as absent rather than as zero.
 */

import { BASE_URL, DURATION_SECONDS, PROFILE, QUESTIONS_PER_ATTEMPT, USERS } from "./config.js";

/** Reads one metric's percentile values, returning null when the metric never fired. */
function percentiles(metrics, name) {
  const metric = metrics[name];
  if (!metric || !metric.values) {
    return null;
  }
  const v = metric.values;
  return {
    count: v.count ?? null,
    p50: v["p(50)"] ?? v.med ?? null,
    p95: v["p(95)"] ?? null,
    p99: v["p(99)"] ?? null,
    avg: v.avg ?? null,
    min: v.min ?? null,
    max: v.max ?? null,
  };
}

/** Reads a metric's scalar value or rate, returning null when absent. */
function scalar(metrics, name, key = "rate") {
  const metric = metrics[name];
  if (!metric || !metric.values) {
    return null;
  }
  return metric.values[key] ?? null;
}

/**
 * Per-endpoint latency, read from the `ep_*` trends declared in common.js.
 *
 * <p>The name is decoded from k6's underscore-safe form back to the dotted endpoint label. A trend with no
 * samples is reported as `null`, never as zero — an endpoint the profile did not exercise is absent data,
 * not a fast endpoint.
 */
function endpointLatencies(metrics) {
  const out = {};
  for (const [name, metric] of Object.entries(metrics)) {
    if (!name.startsWith("ep_") || !metric.values) {
      continue;
    }
    // Split the known prefixes explicitly rather than replacing every underscore, so labels that
    // legitimately contain an underscore survive.
    const raw = name.slice(3);
    const endpoint = raw.replace(/^auth_/, "auth.").replace(/^attempt_/, "attempt.").replace(/^response_/, "response.").replace(/^session_/, "session.");
    out[endpoint] = {
      count: metric.values.count ?? null,
      p50: metric.values["p(50)"] ?? null,
      p95: metric.values["p(95)"] ?? null,
      p99: metric.values["p(99)"] ?? null,
      max: metric.values.max ?? null,
    };
  }
  return out;
}

/**
 * Threshold outcomes, read from each metric's own `thresholds` map.
 *
 * <p>k6 does not expose a top-level threshold summary in `handleSummary` (verified against this k6
 * build), so they are collected per metric. The expression text is preserved so the evidence can show the
 * exact gate that was evaluated.
 */
function thresholdResults(metrics) {
  const out = {};
  for (const [metricName, metric] of Object.entries(metrics)) {
    for (const [expression, result] of Object.entries(metric.thresholds ?? {})) {
      out[`${metricName}: ${expression}`] = { ok: result.ok === true, failures: result.failures ?? [] };
    }
  }
  return out;
}

export function handleSummary(data) {
  const metrics = data.metrics ?? {};
  const run = {
    profile: PROFILE,
    baseUrl: BASE_URL,
    targetUsers: USERS,
    questionsPerAttempt: QUESTIONS_PER_ATTEMPT,
    configuredDurationSeconds: DURATION_SECONDS,
    // --- measured, never inferred ---
    measured: {
      iterations: scalar(metrics, "iterations", "count"),
      httpRequests: scalar(metrics, "http_reqs", "count"),
      httpFailedRate: scalar(metrics, "http_req_failed"),
      applicableLatency: percentiles(metrics, "http_req_duration"),
      submissionSuccessRate: scalar(metrics, "submission_success_rate"),
      examFailureRate: scalar(metrics, "exam_failure_rate"),
      snapshotInsertSuspected: scalar(metrics, "snapshot_insert_suspected", "count"),
      crossUserExposure: scalar(metrics, "cross_user_exposure", "count"),
      wrongSnapshot: scalar(metrics, "wrong_snapshot", "count"),
      iterationsCompleted: scalar(metrics, "iterations", "count"),
      dataSentBytes: scalar(metrics, "data_sent", "count"),
      dataReceivedBytes: scalar(metrics, "data_received", "count"),
    },
    perEndpoint: endpointLatencies(metrics),
    thresholds: thresholdResults(metrics),
    /**
     * Thresholds are only meaningful for the profile that generates them; an empty list means the run did
     * not exercise the gate, not that it passed.
     */
    gatesAssessed: Object.keys(thresholdResults(metrics)),
    allThresholdsPassed:
      Object.keys(thresholdResults(metrics)).length === 0
        ? null
        : Object.values(thresholdResults(metrics)).every((t) => t.ok),
    // --- provenance, carried so the evidence file is self-describing ---
    provenance: {
      generatedAt: new Date().toISOString(),
      k6Version: __VU !== undefined ? "see run log" : null,
      release: __ENV.P7_RELEASE ?? null,
      environment: __ENV.P7_ENVIRONMENT ?? null,
      operator: __ENV.P7_OPERATOR ?? null,
      seed: __ENV.P7_SEED ?? null,
      label: __ENV.P7_LABEL ?? null,
    },
  };

  const outName = `load/out/p7-${PROFILE}-${USERS}.summary.json`;
  return {
    [outName]: JSON.stringify(run, null, 2),
    stdout: `\n[evidence] wrote ${outName}\n`,
  };
}
