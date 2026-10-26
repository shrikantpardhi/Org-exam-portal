"use client";

import { useEffect, useState } from "react";

/**
 * Global search entry point ({@code docs/frontend/navigation.md} §44). The modal is the only
 * entry point; backend global search is a separate capability, so the modal lists the resources
 * not yet wired.
 */
export function GlobalSearch() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
 if (event.key === "Escape") setOpen(false);
 };
 window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <>
      <button
 type="button"
        onClick={() => setOpen(true)}
 className="hidden w-56 rounded-md border border-input px-3 py-1.5 text-left text-sm text-muted-foreground hover:border-input sm:block"
      >
        Search…
      </button>
      {open ? (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 p-6 pt-24">
          <div className="w-full max-w-lg rounded-lg border border-border bg-surface p-4 shadow-xl">
            <input
 autoFocus
 type="search"
 placeholder="Search…"
 aria-label="Search"
 className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
            <p className="mt-3 text-sm text-muted-foreground">
              Global search is coming in this task.
            </p>
          </div>
        </div>
      ) : null}
    </>
  );
}
