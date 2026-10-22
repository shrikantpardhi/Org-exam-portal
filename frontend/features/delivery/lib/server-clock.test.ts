import { describe, expect, it } from "vitest";

import {
  buildTimerAnchor,
  computeOffsetMs,
  reconcileAnchor,
  reconcileDeadline,
  remainingFromAnchor,
  remainingSecondsWithOffset,
  shouldAutoSubmit,
  timerSeverity,
} from "@/features/delivery/lib/server-clock";

/**
 * Server-clock and timer-authority tests ({@code specs/14-P5-Delivery-V1.5.md §54, §132}).
 *
 * <p>The rules under test protect the student from a tampered or drifting browser clock: the deadline
 * is server-supplied, and it may move only earlier. The anchor tests additionally pin the bug that a
 * naive per-render offset calculation causes — a countdown that never advances.
 */
describe("server clock offset", () => {
  it("derives the offset between the server clock and the local clock", () => {
    const clientNow = Date.parse("2026-10-22T10:00:00Z");
    // The server is 30 seconds ahead of this machine.
    expect(computeOffsetMs("2026-10-22T10:00:30Z", clientNow)).toBe(30_000);
  });

  it("treats a missing or unparseable server time as zero offset", () => {
    expect(computeOffsetMs(null, 1_000)).toBe(0);
    expect(computeOffsetMs("not-a-date", 1_000)).toBe(0);
  });

  it("measures remaining time on the corrected clock, not the raw local clock", () => {
    const clientNow = Date.parse("2026-10-22T10:00:00Z");
    const deadline = "2026-10-22T10:10:00Z";
    expect(remainingSecondsWithOffset(deadline, clientNow, 0)).toBe(600);
    // The server is 60s ahead, so only 540s genuinely remain.
    expect(remainingSecondsWithOffset(deadline, clientNow, 60_000)).toBe(540);
  });

  it("clamps at zero once the deadline has passed and returns null when untimed", () => {
    const clientNow = Date.parse("2026-10-22T10:20:00Z");
    expect(remainingSecondsWithOffset("2026-10-22T10:10:00Z", clientNow, 0)).toBe(0);
    expect(remainingSecondsWithOffset(null, clientNow, 0)).toBeNull();
  });
});

describe("timer anchor", () => {
  const serverNow = "2026-10-22T10:00:00Z";
  const deadline = "2026-10-22T10:10:00Z";
  const clientNow = Date.parse("2026-10-22T10:00:00Z");

  it("captures the server-computed remaining time at arrival", () => {
    const anchor = buildTimerAnchor(deadline, serverNow, clientNow);
    expect(anchor?.remainingSecondsAtArrival).toBe(600);
  });

  it("advances as local time passes — the countdown must actually count down", () => {
    const anchor = buildTimerAnchor(deadline, serverNow, clientNow);
    expect(remainingFromAnchor(anchor, clientNow)).toBe(600);
    expect(remainingFromAnchor(anchor, clientNow + 1_000)).toBe(599);
    expect(remainingFromAnchor(anchor, clientNow + 60_000)).toBe(540);
  });

  it("accounts for a machine whose clock is skewed from the server", () => {
    // The local clock is an hour behind the server; the anchor still reports the server's view.
    const anchor = buildTimerAnchor(deadline, serverNow, clientNow - 3_600_000);
    expect(anchor?.remainingSecondsAtArrival).toBe(600);
    expect(remainingFromAnchor(anchor, clientNow - 3_600_000)).toBe(600);
  });

  it("reflects a tab that was asleep, because elapsed local time is subtracted", () => {
    const anchor = buildTimerAnchor(deadline, serverNow, clientNow);
    // The tab wakes five minutes later. No time is added back.
    expect(remainingFromAnchor(anchor, clientNow + 300_000)).toBe(300);
  });

  it("clamps at zero and returns null for an untimed attempt", () => {
    const anchor = buildTimerAnchor(deadline, serverNow, clientNow);
    expect(remainingFromAnchor(anchor, clientNow + 3_600_000)).toBe(0);
    expect(buildTimerAnchor(null, serverNow, clientNow)).toBeNull();
    expect(remainingFromAnchor(null, clientNow)).toBeNull();
  });

  it("falls back to the local clock when the payload omits server time", () => {
    const anchor = buildTimerAnchor(deadline, null, clientNow);
    expect(anchor?.remainingSecondsAtArrival).toBe(600);
  });

  it("ignores an unparseable deadline rather than inventing a countdown", () => {
    expect(buildTimerAnchor("bad-date", serverNow, clientNow)).toBeNull();
  });
});

describe("anchor reconciliation prevents false extension", () => {
  const serverNow = "2026-10-22T10:00:00Z";
  const clientNow = Date.parse("2026-10-22T10:00:00Z");

  it("keeps the first anchor when none exists", () => {
    const first = buildTimerAnchor("2026-10-22T10:10:00Z", serverNow, clientNow);
    expect(reconcileAnchor(null, first)).toBe(first);
  });

  it("refuses a later deadline delivered by a stale payload", () => {
    const original = buildTimerAnchor("2026-10-22T10:10:00Z", serverNow, clientNow);
    // A replayed payload claims 30 minutes remain; that must not grant extra time.
    const inflated = buildTimerAnchor("2026-10-22T10:30:00Z", serverNow, clientNow);
    const kept = reconcileAnchor(original, inflated);
    expect(kept).toBe(original);
    expect(remainingFromAnchor(kept, clientNow)).toBe(600);
  });

  it("adopts an earlier deadline, because the server may shorten time", () => {
    const original = buildTimerAnchor("2026-10-22T10:10:00Z", serverNow, clientNow);
    const shortened = buildTimerAnchor("2026-10-22T10:05:00Z", serverNow, clientNow);
    expect(reconcileAnchor(original, shortened)).toBe(shortened);
  });

  it("compares at a common instant rather than by arrival time", () => {
    const original = buildTimerAnchor("2026-10-22T10:10:00Z", serverNow, clientNow);
    // Two minutes later the server reports a deadline ten minutes out — a genuine resync that keeps
    // total remaining time consistent with the original, so it is accepted as not-longer.
    const resync = buildTimerAnchor(
      "2026-10-22T10:10:00Z",
      "2026-10-22T10:02:00Z",
      clientNow + 120_000,
    );
    const kept = reconcileAnchor(original, resync);
    expect(remainingFromAnchor(kept, clientNow + 120_000)).toBe(480);
  });

  it("keeps the known anchor when the new payload is untimed", () => {
    const original = buildTimerAnchor("2026-10-22T10:10:00Z", serverNow, clientNow);
    expect(reconcileAnchor(original, null)).toBe(original);
  });
});

describe("deadline string reconciliation", () => {
  it("accepts the first deadline it is given", () => {
    expect(reconcileDeadline(null, "2026-10-22T10:10:00Z")).toBe("2026-10-22T10:10:00Z");
  });

  it("keeps the earlier deadline when a later one arrives", () => {
    expect(reconcileDeadline("2026-10-22T10:10:00Z", "2026-10-22T10:30:00Z")).toBe(
      "2026-10-22T10:10:00Z",
    );
  });

  it("adopts an earlier deadline", () => {
    expect(reconcileDeadline("2026-10-22T10:10:00Z", "2026-10-22T10:05:00Z")).toBe(
      "2026-10-22T10:05:00Z",
    );
  });

  it("keeps the known deadline when the new payload omits one", () => {
    expect(reconcileDeadline("2026-10-22T10:10:00Z", null)).toBe("2026-10-22T10:10:00Z");
  });
});

describe("timer severity thresholds", () => {
  it("is normal well before the warning threshold", () => {
    expect(timerSeverity(900, 1_800)).toBe("normal");
  });

  it("warns inside the final fifth of the duration", () => {
    expect(timerSeverity(300, 1_800)).toBe("warning");
  });

  it("is critical inside the final minute", () => {
    expect(timerSeverity(45, 1_800)).toBe("critical");
  });

  it("is expired at zero and never negative", () => {
    expect(timerSeverity(0, 1_800)).toBe("expired");
  });

  it("stays normal for an untimed attempt", () => {
    expect(timerSeverity(null, null)).toBe("normal");
  });
});

describe("auto-submit decision", () => {
  it("fires only when the deadline passed, the policy allows it, and it has not happened", () => {
    expect(shouldAutoSubmit(0, true, false)).toBe(true);
  });

  it("does not fire before expiry", () => {
    expect(shouldAutoSubmit(1, true, false)).toBe(false);
  });

  it("does not fire when the policy forbids it", () => {
    expect(shouldAutoSubmit(0, false, false)).toBe(false);
  });

  it("does not fire twice", () => {
    expect(shouldAutoSubmit(0, true, true)).toBe(false);
  });

  it("does not fire for an untimed attempt", () => {
    expect(shouldAutoSubmit(null, true, false)).toBe(false);
  });
});
