"use client";

import type { ReactNode } from "react";

/**
 * The three analytics states every dashboard must expose
 * ({@code specs/17-P8-Analytics-V1.5.md §144, §146, §147}).
 *
 * <p>An empty state is never a zero metric, and an error state never replaces stale values with
 * zeros. The retry control is a real button, so it is reachable by keyboard.
 */
const BUTTON_CLASS =
  "mt-2 rounded border border-destructive bg-surface px-3 py-1 text-sm font-medium text-destructive " +
  "hover:bg-destructive-subtle focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 " +
  "focus-visible:outline-red-600";

export function AnalyticsLoading({ label = "Loading analytics…" }: { label?: string }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
      {label}
    </p>
  );
}

export function AnalyticsEmpty({ message, hint }: { message: string; hint?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-input bg-muted p-4">
      <p role="note" className="text-sm font-medium text-foreground">
        {message}
      </p>
      {hint ? <p className="mt-1 text-sm text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function AnalyticsError({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div role="alert" className="rounded-lg border border-destructive bg-destructive-subtle p-4 text-sm text-destructive">
      <p>{message}</p>
      {onRetry ? (
        <button type="button" onClick={onRetry} className={BUTTON_CLASS}>
          Try again
        </button>
      ) : null}
    </div>
  );
}

/** A non-blocking notice kept above the last known values when a refresh fails ({@code §147}). */
export function AnalyticsStaleNotice({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-warning bg-warning-subtle p-4 text-sm text-warning"
    >
      <p>{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 rounded border border-warning bg-surface px-3 py-1 text-sm font-medium text-warning hover:bg-amber-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-700"
        >
          Refresh again
        </button>
      ) : null}
    </div>
  );
}
