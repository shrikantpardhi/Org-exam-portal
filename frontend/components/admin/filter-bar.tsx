"use client";

import type { FormEvent, ReactNode } from "react";

/** A labelled filter form for admin tables ({@code specs/20-P11-Administration-V1.5.md §123}). */
export function FilterBar({
  label,
  onSubmit,
  onReset,
  busy = false,
  children,
}: {
  label: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onReset?: () => void;
  busy?: boolean;
  children: ReactNode;
}) {
  return (
    <form
      role="search"
      aria-label={label}
      onSubmit={onSubmit}
      className="space-y-3 rounded-lg border border-border bg-surface p-4"
    >
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
        >
          {busy ? "Applying…" : "Apply filters"}
        </button>
        {onReset ? (
          <button
            type="button"
            onClick={onReset}
            className="rounded border border-input bg-surface px-4 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Reset
          </button>
        ) : null}
      </div>
    </form>
  );
}
