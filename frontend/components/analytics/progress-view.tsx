"use client";

import { useId } from "react";

import { AnalyticsEmpty } from "./analytics-states";

/**
 * Progress view ({@code specs/17-P8-Analytics-V1.5.md §19}).
 *
 * <p>Progress is derived, never stored. The caller supplies the documented formula's numerator and
 * denominator; this component only renders them as a labelled progress bar with a textual value,
 * so the bar is not the sole representation.
 */
export interface ProgressViewProps {
  title: string;
  /** The documented formula, in words. */
  description: string;
  completed: number;
  total: number;
  /** Plural noun for the population, for example "topics with a reliable sample". */
  unitLabel: string;
  emptyMessage?: string;
}

export function ProgressView({
  title,
  description,
  completed,
  total,
  unitLabel,
  emptyMessage,
}: ProgressViewProps) {
  const headingId = useId();
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0;

  return (
    <section
      aria-labelledby={headingId}
      className="space-y-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
    >
      <div>
        <h3 id={headingId} className="text-base font-semibold text-slate-900">
          {title}
        </h3>
        <p className="text-sm text-slate-500">{description}</p>
      </div>

      {total === 0 ? (
        <AnalyticsEmpty message={emptyMessage ?? "No progress to show yet."} />
      ) : (
        <>
          <div
            role="progressbar"
            aria-labelledby={headingId}
            aria-valuemin={0}
            aria-valuemax={total}
            aria-valuenow={completed}
            aria-valuetext={`${completed} of ${total} ${unitLabel}`}
            className="h-2 w-full overflow-hidden rounded bg-slate-100"
          >
            <div className="h-full rounded bg-blue-600" style={{ width: `${percent}%` }} />
          </div>
          <p className="text-sm text-slate-700">
            <span className="font-semibold">{completed}</span> of {total} {unitLabel} ({percent}%)
          </p>
        </>
      )}
    </section>
  );
}
