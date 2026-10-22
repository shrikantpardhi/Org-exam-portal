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
 * A fixed reference point captured the instant a server payload arrives.
 *
 * <p>This exists because recomputing a clock offset on every render is wrong: the offset shrinks by
 * one second per second, which cancels the tick and freezes the countdown at a constant value. Storing
 * the *server-calculated* remaining time at arrival and then subtracting locally measured elapsed time
 * is immune to that, and is also immune to clock skew, because both operands of the subtraction come
 * from the server.
 */
export interface TimerAnchor {
  /** Whole seconds remaining according to the server at the moment the payload arrived. */
  readonly remainingSecondsAtArrival: number;
  /** The local clock reading at that same moment. */
  readonly clientMsAtArrival: number;
}

/**
 * Builds an anchor from a payload's deadline and server time.
 *
 * <p>Returns {@code null} for an untimed attempt, or when either timestamp is unparseable — in which
 * case the caller shows no countdown rather than inventing one.
 */
export function buildTimerAnchor(
  deadlineIso: string | null | undefined,
  serverTimeIso: string | null | undefined,
  clientNowMs: number,
): TimerAnchor | null {
  if (!deadlineIso) {
    return null;
  }
  const deadlineMs = Date.parse(deadlineIso);
  if (Number.isNaN(deadlineMs)) {
    return null;
  }
  // Prefer the server's own "now" so the anchor is independent of this machine's clock. Falling back
  // to the local clock only happens if the payload omitted `serverTime`, which the contract does not
  // allow but a degraded proxy might.
  const serverMs = serverTimeIso ? Date.parse(serverTimeIso) : Number.NaN;
  const referenceMs = Number.isNaN(serverMs) ? clientNowMs : serverMs;
  return {
    remainingSecondsAtArrival: Math.max(0, Math.floor((deadlineMs - referenceMs) / 1000)),
    clientMsAtArrival: clientNowMs,
  };
}

/**
 * Remaining whole seconds for an anchor at a given local instant, clamped at zero.
 *
 * <p>Measuring elapsed time locally (rather than recomputing an offset) is what makes the countdown
 * advance, and it correctly reflects a tab that was asleep: the local clock jumped forward, so the
 * elapsed subtraction consumes the right amount of time (§132 sleep/wake handling).
 */
export function remainingFromAnchor(anchor: TimerAnchor | null, clientNowMs: number): number | null {
  if (!anchor) {
    return null;
  }
  const elapsedSeconds = Math.floor((clientNowMs - anchor.clientMsAtArrival) / 1000);
  return Math.max(0, anchor.remainingSecondsAtArrival - elapsedSeconds);
}

/**
 * Keeps the earlier of two anchors, so a replayed or stale payload cannot extend the timer (§132).
 * An earlier deadline yields a smaller {@code remainingSecondsAtArrival} at the same reference moment.
 */
export function reconcileAnchor(
  previous: TimerAnchor | null,
  incoming: TimerAnchor | null,
): TimerAnchor | null {
  if (!incoming) {
    return previous;
  }
  if (!previous) {
    return incoming;
  }
  // Compare at a common instant: both anchors are projected onto the incoming anchor's arrival time.
  const previousProjected =
    previous.remainingSecondsAtArrival -
    Math.floor((incoming.clientMsAtArrival - previous.clientMsAtArrival) / 1000);
  return incoming.remainingSecondsAtArrival <= previousProjected ? incoming : previous;
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
