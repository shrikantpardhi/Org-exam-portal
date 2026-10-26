"use client";

import { FreshnessIndicator } from "./freshness-indicator";

/**
 * A single labelled metric ({@code specs/17-P8-Analytics-V1.5.md §144}).
 *
 * <p>A null metric is shown as "Not available" with an explanation, never as "0", so a missing
 * population cannot be mistaken for a measured zero ({@code §90, §146}).
 */
export interface MetricCardProps {
  label: string;
  /** A formatted value. Pass {@code null} when the metric has no population. */
  value: string | null;
  unit?: string;
  /** What the metric measures. A percentage is never shown without this ({@code §144}). */
  description?: string;
  /** Shown in place of the value when the metric has no population. */
  emptyValue?: string;
  freshness?: string | null;
  lastCalculatedOn?: string | null;
}

export function MetricCard({
  label,
  value,
  unit,
  description,
  emptyValue = "Not available",
  freshness,
  lastCalculatedOn,
}: MetricCardProps) {
  const showFreshness = freshness !== undefined || lastCalculatedOn !== undefined;

  return (
    <section
      aria-label={label}
      className="rounded-lg border border-border bg-surface p-4 shadow-sm"
    >
      <h3 className="text-sm font-medium text-muted-foreground">{label}</h3>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-foreground">
        {value === null ? (
          <span className="text-sm font-normal text-muted-foreground">{emptyValue}</span>
        ) : (
          <>
            {value}
            {unit ? <span className="ml-1 text-sm font-normal text-muted-foreground">{unit}</span> : null}
          </>
        )}
      </p>
      {description ? <p className="mt-1 text-xs text-muted-foreground">{description}</p> : null}
      {showFreshness ? (
        <FreshnessIndicator
          lastCalculatedOn={lastCalculatedOn ?? null}
          freshness={freshness ?? null}
          className="mt-2"
        />
      ) : null}
    </section>
  );
}
