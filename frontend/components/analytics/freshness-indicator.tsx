"use client";

import { formatTimestamp } from "./format";

/**
 * Freshness indicator ({@code specs/17-P8-Analytics-V1.5.md §51, §69, §144}).
 *
 * <p>Analytics is derived and may lag the transactional data. The UI therefore shows both the
 * refresh timestamp ({@code lastCalculatedOn}) and the API freshness status, so a slightly delayed
 * value is never presented as live.
 */
const LABELS: Record<string, string> = {
  CURRENT: "Up to date",
  STALE: "Stale",
  REFRESHING: "Refreshing",
  EMPTY: "Not calculated yet",
};

const STYLES: Record<string, string> = {
  CURRENT: "border-success bg-success-subtle text-success",
  STALE: "border-warning bg-warning-subtle text-warning",
  REFRESHING: "border-info bg-info-subtle text-info",
  EMPTY: "border-border bg-muted text-muted-foreground",
};

export function freshnessLabel(freshness: string | null | undefined): string {
  const key = (freshness ?? "").toUpperCase();
  return LABELS[key] ?? "Unknown freshness";
}

export function FreshnessIndicator({
  lastCalculatedOn,
  freshness,
  className,
}: {
  lastCalculatedOn: string | null;
  freshness: string | null;
  className?: string;
}) {
  const key = (freshness ?? "").toUpperCase();
  const updated = formatTimestamp(lastCalculatedOn);

  return (
    <p
      className={`flex flex-wrap items-center gap-2 text-xs text-muted-foreground ${className ?? ""}`}
      data-freshness={key || "UNKNOWN"}
    >
      <span
        className={`rounded-full border px-2 py-0.5 font-medium ${
          STYLES[key] ?? "border-border bg-muted text-muted-foreground"
        }`}
      >
        {freshnessLabel(freshness)}
      </span>
      {updated ? (
        <span>
          Updated{" "}
          <time dateTime={lastCalculatedOn ?? undefined}>{updated}</time>
        </span>
      ) : (
        <span>No calculation timestamp yet</span>
      )}
    </p>
  );
}
