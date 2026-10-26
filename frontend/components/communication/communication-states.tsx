"use client";

import type { ReactNode } from "react";

/** Loading, empty and error states for communication screens ({@code §104-§112}). */
const BUTTON_CLASS =
  "mt-2 rounded border border-red-300 bg-surface px-3 py-1 text-sm font-medium text-red-700 " +
  "hover:bg-red-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 " +
  "focus-visible:outline-red-600";

export function CommunicationLoading({ label = "Loading communication data…" }: { label?: string }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
      {label}
    </p>
  );
}

export function CommunicationEmpty({ message, hint }: { message: string; hint?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-input bg-muted p-4">
      <p role="note" className="text-sm font-medium text-foreground">
        {message}
      </p>
      {hint ? <p className="mt-1 text-sm text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function CommunicationError({
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
