"use client";

import type { ReactNode } from "react";

/** A keyboard-operable notification panel ({@code specs/19-P10-Communication-V1.5.md §105}). */
export function NotificationPopover({
  id,
  open,
  onClose,
  children,
}: {
  id: string;
  open: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  if (!open) {
    return null;
  }

  return (
    <section
      id={id}
      role="region"
      aria-label="Notifications"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          onClose();
        }
      }}
      className="absolute right-0 z-20 mt-2 w-80 space-y-3 rounded-lg border border-border bg-surface p-4 shadow-lg"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">Notifications</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close notifications"
          className="rounded px-2 py-1 text-sm text-muted-foreground hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Close
        </button>
      </div>
      {children}
    </section>
  );
}
