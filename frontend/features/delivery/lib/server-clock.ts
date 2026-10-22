/**
 * Server-authoritative clock helpers ({@code specs/14-P5-Delivery-V1.5.md §54, §132}).
 *
 * <p>The browser clock is never the deadline authority (§74). Every server payload carries
 * {@code serverTime}; comparing it with the local clock yields an offset that corrects both a skewed
 * machine clock and the drift accumulated while a tab was asleep. The rendered countdown is
 * `deadline - (clientNow + offset)`, so the browser only animates a value the server owns.
 *
 * <p>Pure functions so the timer's behaviour is unit-testable without a browser.
 */

/** The observed difference between the server clock and the local clock, in milliseconds. */
export function computeOffsetMs(serverTimeIso: string | null | undefined, clientNowMs: number): number {
  if (!serverTimeIso) {
    return 0;
  }
  const serverMs = Date.parse(serverTimeIso);
  if (Number.isNaN(serverMs)) {
    return 0;
  }
  return serverMs - clientNowMs;
}

/**
 * Remaining whole seconds until the deadline as measured on the corrected clock, clamped at zero.
 * Returns {@code null} for an untimed attempt (no deadline).
 */
export function remainingSecondsWithOffset(
  deadlineIso: string | null | undefined,
  clientNowMs: number,
  offsetMs: number,
): number | null {
  if (!deadlineIso) {
    return null;
  }
  const deadlineMs = Date.parse(deadlineIso);
  if (Number.isNaN(deadlineMs)) {
    return null;
  }
  return Math.max(0, Math.floor((deadlineMs - (clientNowMs + offsetMs)) / 1000));
}

/**
 * Reconciles a freshly received deadline with the one already on screen.
 *
 * <p>A server response may only move the deadline <em>earlier</em>. Accepting a later deadline from a
 * stale or replayed payload would grant the student time the server never authorized, so the earlier
 * of the two is always kept (§132 "prevents false extension"). An untimed-to-timed transition is
 * accepted because it is the first authority the client has seen.
 */
export function reconcileDeadline(
  previousDeadlineIso: string | null | undefined,
  nextDeadlineIso: string | null | undefined,
): string | null {
  if (!nextDeadlineIso) {
    return previousDeadlineIso ?? null;
  }
  if (!previousDeadlineIso) {
    return nextDeadlineIso;
  }
  const previous = Date.parse(previousDeadlineIso);
  const next = Date.parse(nextDeadlineIso);
  if (Number.isNaN(previous)) {
    return nextDeadlineIso;
  }
  if (Number.isNaN(next)) {
    return previousDeadlineIso;
  }
  return next <= previous ? nextDeadlineIso : previousDeadlineIso;
}

/** Timer severity thresholds, expressed as a fraction of the total duration (§132). */
export const TIMER_WARNING_RATIO = 0.2;
export const TIMER_CRITICAL_SECONDS = 60;

export type TimerSeverity = "normal" | "warning" | "critical" | "expired";

/**
 * Classifies the remaining time so the UI can warn near the configured thresholds without ever
 * implying the timer can be extended.
 */
export function timerSeverity(remaining: number | null, durationSeconds: number | null): TimerSeverity {
  if (remaining === null) {
    return "normal";
  }
  if (remaining <= 0) {
    return "expired";
  }
  if (remaining <= TIMER_CRITICAL_SECONDS) {
    return "critical";
  }
  if (durationSeconds !== null && durationSeconds > 0) {
    const warningAt = Math.floor(durationSeconds * TIMER_WARNING_RATIO);
    if (remaining <= warningAt) {
      return "warning";
    }
  }
  return "normal";
}

/**
 * Whether the runtime should auto-submit because the server deadline has passed and the policy
 * allows it. The client does not decide the outcome — it triggers the same server submit endpoint the
 * student would, and the server remains authoritative (§55).
 */
export function shouldAutoSubmit(
  remaining: number | null,
  autoSubmitOnExpiry: boolean,
  alreadySubmitted: boolean,
): boolean {
  return remaining !== null && remaining <= 0 && autoSubmitOnExpiry && !alreadySubmitted;
}
