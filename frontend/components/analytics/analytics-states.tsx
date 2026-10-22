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
  "mt-2 rounded border border-red-300 bg-white px-3 py-1 text-sm font-medium text-red-700 " +
  "hover:bg-red-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 " +
  "focus-visible:outline-red-600";

export function AnalyticsLoading({ label = "Loading analytics…" }: { label?: string }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-slate-500">
      {label}
    </p>
  );
}

export function AnalyticsEmpty({ message, hint }: { message: string; hint?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4">
      <p role="note" className="text-sm font-medium text-slate-700">
        {message}
      </p>
      {hint ? <p className="mt-1 text-sm text-slate-500">{hint}</p> : null}
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
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
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
      className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
    >
      <p>{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 rounded border border-amber-300 bg-white px-3 py-1 text-sm font-medium text-amber-800 hover:bg-amber-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-700"
        >
          Refresh again
        </button>
      ) : null}
    </div>
  );
}
