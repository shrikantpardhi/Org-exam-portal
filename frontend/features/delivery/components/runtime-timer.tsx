"use client";

import { formatDuration } from "@/features/delivery/lib/runtime-helpers";
import { timerSeverity, type TimerSeverity } from "@/features/delivery/lib/server-clock";

/**
 * Server-authoritative countdown ({@code specs/14-P5-Delivery-V1.5.md §54-§56, §132}).
 *
 * <p>Presentation-only (§132). It receives an already-computed remaining value and renders it; it never
 * computes the deadline itself and never extends it. Keeping the arithmetic in the runtime
 * ({@link remainingFromAnchor}) means the number on screen and the number that triggers auto-submit are
 * necessarily the same, which a second independent calculation here could not guarantee.
 *
 * <p>{@code durationSeconds} is accepted only so severity can be derived when the caller does not pass
 * it explicitly.
 */
export function RuntimeTimer({
  remaining,
  serverTime,
  severity,
  durationSeconds = null,
}: {
  /** Whole seconds remaining on the server-authoritative deadline, or null when untimed. */
  remaining: number | null;
  /** The server's own clock reading, shown as a tooltip for support purposes. */
  serverTime: string;
  severity?: TimerSeverity;
  durationSeconds?: number | null;
}) {
  const label = remaining === null ? "Untimed" : formatDuration(remaining);
  const level = severity ?? timerSeverity(remaining, durationSeconds);
  const expired = level === "expired";

  const tone =
    expired || level === "critical"
      ? "font-mono text-lg text-red-600"
      : level === "warning"
        ? "font-mono text-lg text-amber-600"
        : "font-mono text-lg text-foreground";

  return (
    <div className="flex items-center gap-2">
      {level === "warning" || level === "critical" ? (
        <span role="status" aria-live="polite" className="text-xs font-medium text-amber-700">
          {level === "critical" ? "Less than a minute" : "Time is running short"}
        </span>
      ) : null}
      <div
        role="timer"
        aria-live="polite"
        aria-label="Time remaining"
        title={`Server time ${serverTime}`}
        className={tone}
      >
        {label}
      </div>
    </div>
  );
}
