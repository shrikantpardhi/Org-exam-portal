"use client";

import type { ReactNode } from "react";

/**
 * Loading, empty and error states for commerce screens
 * ({@code specs/18-P9-Commerce-V1.5.md §75-§89}).
 */
const BUTTON_CLASS =
  "mt-2 rounded border border-destructive bg-surface px-3 py-1 text-sm font-medium text-destructive " +
  "hover:bg-destructive-subtle focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 " +
  "focus-visible:outline-red-600";

export function CommerceLoading({ label = "Loading commerce data…" }: { label?: string }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
      {label}
    </p>
  );
}

export function CommerceEmpty({ message, hint }: { message: string; hint?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-input bg-muted p-4">
      <p role="note" className="text-sm font-medium text-foreground">
        {message}
      </p>
      {hint ? <p className="mt-1 text-sm text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function CommerceError({ message, onRetry }: { message: string; onRetry?: () => void }) {
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

/** A small lifecycle pill shared by catalog and administration screens. */
export function LifecycleBadge({ status }: { status: string }) {
  const key = (status ?? "").toUpperCase();
  const style =
    key === "ACTIVE"
      ? "border-success bg-success-subtle text-success"
      : "border-border bg-muted text-muted-foreground";
  return (
    <span data-status={key || "UNKNOWN"} className={`rounded-full border px-2 py-0.5 text-xs font-medium ${style}`}>
      {key === "ACTIVE" ? "Active" : key === "INACTIVE" ? "Inactive" : key === "ARCHIVED" ? "Archived" : "Unknown"}
    </span>
  );
}
