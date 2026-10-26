"use client";

import { useEffect } from "react";

import { Button } from "@/components/ui/button";

/**
 * Application-level error boundary.
 *
 * Shows a safe fallback and a way to recover. Stack traces are never displayed
 * (specs/09-P0-Foundation-V1.5.md §104).
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Surface the error for diagnostics without exposing it to the user.
    console.error("Unhandled UI error", error.digest ?? "");
  }, [error]);

  return (
    <main className="mx-auto flex w-full max-w-xl flex-col items-start gap-4 px-6 py-24">
      <h1 className="text-xl font-semibold">Something went wrong</h1>
      <p className="text-sm text-muted-foreground">
        The page could not be displayed. You can try again.
      </p>
      {error.digest ? (
        <p className="font-mono text-xs text-muted-foreground">Reference: {error.digest}</p>
      ) : null}
      <Button onClick={reset}>Try again</Button>
    </main>
  );
}
