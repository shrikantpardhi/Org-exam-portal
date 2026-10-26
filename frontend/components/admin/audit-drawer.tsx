"use client";

import { useEffect, useId, useRef } from "react";

import type { AuditLog } from "@/features/administration/schemas/administration";

import { formatDateTime, shortId } from "./format";

/**
 * The audit detail drawer ({@code specs/20-P11-Administration-V1.5.md §73}).
 *
 * <p>Read-only by construction: it renders one immutable event and exposes no mutation control.
 */
export function AuditDrawer({
  entry,
  open,
  onClose,
}: {
  entry: AuditLog | null;
  open: boolean;
  onClose: () => void;
}) {
  const titleId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      closeRef.current?.focus();
    }
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  if (!open || !entry) {
    return null;
  }

  const created = formatDateTime(entry.createdOn);

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-primary/40" role="presentation">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="h-full w-full max-w-md overflow-y-auto bg-surface p-6 shadow-xl"
      >
        <div className="flex items-start justify-between gap-3">
          <h2 id={titleId} className="text-lg font-semibold">
            Audit event
          </h2>
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            aria-label="Close audit details"
            className="rounded border border-input px-3 py-1 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Close
          </button>
        </div>

        <dl className="mt-4 space-y-3 text-sm">
          <div>
            <dt className="text-xs text-muted-foreground">When</dt>
            <dd className="mt-0.5">
              {created ? <time dateTime={entry.createdOn ?? undefined}>{created}</time> : "Not available"}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Actor</dt>
            <dd className="mt-0.5 break-all font-mono text-xs">{entry.userId ?? "System"}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Entity</dt>
            <dd className="mt-0.5">
              {entry.entityName ?? "Not available"}
              {entry.entityId ? ` · ${shortId(entry.entityId)}` : ""}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Action</dt>
            <dd className="mt-0.5">{entry.action ?? "Not available"}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Remarks</dt>
            <dd className="mt-0.5 whitespace-pre-wrap">{entry.remarks ?? "No remarks."}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Event id</dt>
            <dd className="mt-0.5 break-all font-mono text-xs">{entry.id}</dd>
          </div>
        </dl>
      </section>
    </div>
  );
}
