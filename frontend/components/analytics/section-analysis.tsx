"use client";

import { useId } from "react";

import type { SectionAnalytics } from "@/features/analytics/schemas/analytics";

import { AnalyticsEmpty } from "./analytics-states";
import { FreshnessIndicator } from "./freshness-indicator";
import { formatDecimal, formatInteger, formatPercent, formatValueWithUnit } from "./format";

/**
 * Section analysis ({@code specs/17-P8-Analytics-V1.5.md §45-§48, §53}).
 *
 * <p>Section metrics are a dynamic read aggregation; no section table exists. The table is the
 * accessible representation of the comparison, and each accuracy cell carries a decorative bar so
 * the visual reading never replaces the tabular one ({@code §145}).
 */
export function SectionAnalysis({
  sections,
  lastCalculatedOn,
  freshness,
}: {
  sections: SectionAnalytics[];
  lastCalculatedOn?: string | null;
  freshness?: string | null;
}) {
  const headingId = useId();
  const showFreshness = freshness !== undefined || lastCalculatedOn !== undefined;

  return (
    <section
      aria-labelledby={headingId}
      className="space-y-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 id={headingId} className="text-base font-semibold text-slate-900">
            Section analysis
          </h3>
          <p className="text-sm text-slate-500">
            Accuracy is correct answers divided by evaluated questions in that section. Average time
            uses the persisted per-question time, not a section timer.
          </p>
        </div>
        {showFreshness ? (
          <FreshnessIndicator
            lastCalculatedOn={lastCalculatedOn ?? null}
            freshness={freshness ?? null}
          />
        ) : null}
      </div>

      {sections.length === 0 ? (
        <AnalyticsEmpty message="No section mapping available." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Section performance by assessment section</caption>
            <thead>
              <tr className="border-b border-slate-200">
                <th scope="col" className="py-2 pr-3">
                  Section
                </th>
                <th scope="col" className="py-2 pr-3">
                  Questions
                </th>
                <th scope="col" className="py-2 pr-3">
                  Marks
                </th>
                <th scope="col" className="py-2 pr-3">
                  Accuracy
                </th>
                <th scope="col" className="py-2">
                  Average time
                </th>
              </tr>
            </thead>
            <tbody>
              {sections.map((section, index) => (
                <tr
                  key={`${section.sectionName}-${index}`}
                  className="border-b border-slate-100 align-top"
                >
                  <th scope="row" className="py-2 pr-3 font-medium text-slate-800">
                    {section.sectionName}
                  </th>
                  <td className="py-2 pr-3 tabular-nums">
                    {formatInteger(section.questionCount) ?? "Not available"}
                  </td>
                  <td className="py-2 pr-3 tabular-nums">
                    {section.obtainedMarks === null || section.totalMarks === null
                      ? "Not available"
                      : `${formatDecimal(section.obtainedMarks, 2)} / ${formatDecimal(
                          section.totalMarks,
                          2,
                        )}`}
                  </td>
                  <td className="py-2 pr-3">
                    <span className="tabular-nums">
                      {formatPercent(section.accuracy) ?? "Not available"}
                    </span>
                    {section.accuracy !== null ? (
                      <span
                        aria-hidden="true"
                        className="mt-1 block h-1.5 w-full max-w-32 overflow-hidden rounded bg-slate-100"
                      >
                        <span
                          className="block h-full rounded bg-blue-600"
                          style={{
                            width: `${Math.min(Math.max(section.accuracy, 0), 100)}%`,
                          }}
                        />
                      </span>
                    ) : null}
                  </td>
                  <td className="py-2 tabular-nums">
                    {formatValueWithUnit(section.averageTime, "s") ?? "Not available"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
