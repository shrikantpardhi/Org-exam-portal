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
  "mt-2 rounded border border-destructive bg-surface px-3 py-1 text-sm font-medium text-destructive " +
  "hover:bg-destructive-subtle focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 " +
  "focus-visible:outline-red-600";

export function ResultsLoading({ label = "Loading results…" }: { label?: string }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
      {label}
    </p>
  );
}

export function ResultsEmpty({ message, hint }: { message: string; hint?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-input bg-muted p-4">
      <p role="note" className="text-sm font-medium text-foreground">
        {message}
      </p>
      {hint ? <p className="mt-1 text-sm text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function ResultsError({ message, onRetry }: { message: string; onRetry?: () => void }) {
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

/**
 * The student-facing publication guard ({@code §39}). It renders no mark, rank or percentage, so an
 * unpublished or superseded result can never be presented as a result even if the read regressed.
 */
export function ResultVisibilityNotice({ status }: { status: string }) {
  const superseded = (status ?? "").toUpperCase() === "SUPERSEDED";
  return (
    <div role="note" className="rounded-lg border border-dashed border-input bg-muted p-4">
      <p className="text-sm font-medium text-foreground">
        {superseded ? "This result has been superseded." : "This result is not published yet."}
      </p>
      <p className="mt-1 text-sm text-muted-foreground">
        {superseded
          ? "A newer published result replaced it. Only the current published result is shown."
          : "Unpublished results are not shown to students. A result appears here after it is published."}
      </p>
    </div>
  );
}
