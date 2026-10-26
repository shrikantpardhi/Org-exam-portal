"use client";

import type { AccessGrant } from "@/features/commerce/schemas/commerce";

import { formatDate } from "./format";

/**
 * One entitlement ({@code specs/18-P9-Commerce-V1.5.md §82}).
 *
 * <p>The projection is read-only: package, validity window and the server's active flag. Nothing
 * on this card unlocks content; entitlement is evaluated by the backend.
 */
export function AccessGrantCard({ grant }: { grant: AccessGrant }) {
  const starts = formatDate(grant.startDate);
  const ends = formatDate(grant.endDate);

  return (
    <article
      aria-label={grant.packageName}
      className="space-y-2 rounded-lg border border-border bg-surface p-4 shadow-sm"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="text-base font-semibold text-foreground">{grant.packageName}</h3>
        <span
          data-active={grant.active}
          className={`rounded-full border px-2 py-0.5 text-xs font-medium ${
            grant.active
              ? "border-success bg-success-subtle text-success"
              : "border-border bg-muted text-muted-foreground"
          }`}
        >
          {grant.active
            ? "Active"
            : grant.status === "ARCHIVED"
              ? "Archived"
              : "Inactive"}
        </span>
      </div>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-xs text-muted-foreground">Starts</dt>
          <dd className="mt-0.5">
            {starts ? <time dateTime={grant.startDate ?? undefined}>{starts}</time> : "Not available"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Ends</dt>
          <dd className="mt-0.5">
            {ends ? (
              <time dateTime={grant.endDate ?? undefined}>{ends}</time>
            ) : (
              "Open-ended"
            )}
          </dd>
        </div>
      </dl>
    </article>
  );
}
