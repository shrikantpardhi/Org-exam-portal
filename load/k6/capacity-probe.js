/**
 * Generator-capacity probe (no server interaction).
 *
 * <p>Answers one question that the ramp depends on: how many concurrent VUs can this host actually run
 * before the load generator itself becomes the bottleneck? The script does nothing but hold a VU open and
 * touch the JS runtime, so any memory or scheduling pressure is the generator's alone — no traffic, no
 * database, no target.
 *
 * <p>This exists because a measured p95 is meaningless if the generator ran out of memory midway. The
 * result is recorded as the generator ceiling in the evidence file.
 *
 * Usage: k6 run load/k6/capacity-probe.js -e P7_VUS=5000
 */

import { sleep } from "k6";

const VUS = Number(__ENV.P7_VUS ?? 1000);
const DURATION = __ENV.P7_DURATION ?? "30s";

export const options = {
  scenarios: {
    probe: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
    },
  },
  // No thresholds: this probe measures the host, it does not gate anything.
  summaryTrendStats: ["avg", "p(95)", "max"],
};

export default function () {
  // A small allocation loop keeps the interpreter genuinely active, so the measurement reflects a real
  // VU rather than an idle goroutine.
  const bucket = [];
  for (let i = 0; i < 50; i += 1) {
    bucket.push({ i, label: `vu-${__VU}` });
  }
  sleep(1);
}
