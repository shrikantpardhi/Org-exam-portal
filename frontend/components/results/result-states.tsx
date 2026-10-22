"use client";

import type { ReactNode } from "react";

/**
 * Loading, empty and error states for result screens
 * ({@code specs/15-P6-Evaluation-V1.5.md §95, §98}).
 *
 * <p>The error state never reveals whether a result exists but belongs to someone else: the
 * backend answers 404 for another student's result and for an unpublished one, and the UI keeps
 * that ambiguity ({@code §39, §87}).
 */
const BUTTON_CLASS =
  "mt-2 rounded border border-red-300 bg-white px-3 py-1 text-sm font-medium text-red-700 " +
  "hover:bg-red-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 " +
  "focus-visible:outline-red-600";

export function ResultsLoading({ label = "Loading results…" }: { label?: string }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-slate-500">
      {label}
    </p>
  );
}

export function ResultsEmpty({ message, hint }: { message: string; hint?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4">
      <p role="note" className="text-sm font-medium text-slate-700">
        {message}
      </p>
      {hint ? <p className="mt-1 text-sm text-slate-500">{hint}</p> : null}
    </div>
  );
}

export function ResultsError({ message, onRetry }: { message: string; onRetry?: () => void }) {
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

/**
 * The student-facing publication guard ({@code §39}). It renders no mark, rank or percentage, so an
 * unpublished or superseded result can never be presented as a result even if the read regressed.
 */
export function ResultVisibilityNotice({ status }: { status: string }) {
  const superseded = (status ?? "").toUpperCase() === "SUPERSEDED";
  return (
    <div role="note" className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4">
      <p className="text-sm font-medium text-slate-700">
        {superseded ? "This result has been superseded." : "This result is not published yet."}
      </p>
      <p className="mt-1 text-sm text-slate-500">
        {superseded
          ? "A newer published result replaced it. Only the current published result is shown."
          : "Unpublished results are not shown to students. A result appears here after it is published."}
      </p>
    </div>
  );
}
