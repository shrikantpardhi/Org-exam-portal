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
  CURRENT: "border-emerald-200 bg-emerald-50 text-emerald-700",
  STALE: "border-amber-200 bg-amber-50 text-amber-800",
  REFRESHING: "border-blue-200 bg-blue-50 text-blue-700",
  EMPTY: "border-slate-200 bg-slate-100 text-slate-600",
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
      className={`flex flex-wrap items-center gap-2 text-xs text-slate-500 ${className ?? ""}`}
      data-freshness={key || "UNKNOWN"}
    >
      <span
        className={`rounded-full border px-2 py-0.5 font-medium ${
          STYLES[key] ?? "border-slate-200 bg-slate-100 text-slate-600"
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
