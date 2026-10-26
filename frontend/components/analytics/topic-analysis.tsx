"use client";

import { useId } from "react";

import type { StudentTopicItem } from "@/features/analytics/schemas/analytics";

import { AnalyticsEmpty } from "./analytics-states";
import { FreshnessIndicator } from "./freshness-indicator";
import { formatInteger, formatPercent, shortId } from "./format";

/**
 * Topic performance ({@code specs/17-P8-Analytics-V1.5.md §20, §55}).
 *
 * <p>The weak-area flag is calculated by the backend from a configurable accuracy threshold
 * ({@code §20}); the UI never turns the threshold into an unexplained official classification.
 * A topic below the minimum reliable sample is labelled as such and is not called weak.
 */
export function TopicAnalysis({
  topics,
  lastCalculatedOn,
  freshness,
}: {
  topics: StudentTopicItem[];
  lastCalculatedOn?: string | null;
  freshness?: string | null;
}) {
  const headingId = useId();
  const showFreshness = freshness !== undefined || lastCalculatedOn !== undefined;

  return (
    <section
      aria-labelledby={headingId}
      className="space-y-3 rounded-lg border border-border bg-surface p-4 shadow-sm"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 id={headingId} className="text-base font-semibold text-foreground">
            Topic performance
          </h3>
          <p className="text-sm text-muted-foreground">
            Accuracy is correct answers divided by evaluated questions mapped to the topic. The
            weak-area flag comes from the platform accuracy threshold; topics below the minimum
            reliable sample are not classified.
          </p>
        </div>
        {showFreshness ? (
          <FreshnessIndicator
            lastCalculatedOn={lastCalculatedOn ?? null}
            freshness={freshness ?? null}
          />
        ) : null}
      </div>

      {topics.length === 0 ? (
        <AnalyticsEmpty message="No topic mapping available." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Accuracy by topic</caption>
            <thead>
              <tr className="border-b border-border">
                <th scope="col" className="py-2 pr-3">
                  Topic
                </th>
                <th scope="col" className="py-2 pr-3">
                  Evaluated
                </th>
                <th scope="col" className="py-2 pr-3">
                  Accuracy
                </th>
                <th scope="col" className="py-2">
                  Sample
                </th>
              </tr>
            </thead>
            <tbody>
              {topics.map((topic) => (
                <tr key={topic.topicId} className="border-b border-border align-top">
                  <th scope="row" className="py-2 pr-3 font-normal">
                    <code className="rounded bg-muted px-1 py-0.5 text-xs" title={topic.topicId}>
                      {shortId(topic.topicId)}
                    </code>
                  </th>
                  <td className="py-2 pr-3 tabular-nums">
                    {formatInteger(topic.evaluatedOccurrences) ?? "Not available"}
                  </td>
                  <td className="py-2 pr-3">
                    <span className="tabular-nums">
                      {formatPercent(topic.accuracy) ?? "Not available"}
                    </span>
                    {topic.accuracy !== null ? (
                      <span
                        aria-hidden="true"
                        className="mt-1 block h-1.5 w-full max-w-32 overflow-hidden rounded bg-muted"
                      >
                        <span
                          className={`block h-full rounded ${
                            topic.weakArea ? "bg-amber-500" : "bg-blue-600"
                          }`}
                          style={{ width: `${Math.min(Math.max(topic.accuracy, 0), 100)}%` }}
                        />
                      </span>
                    ) : null}
                  </td>
                  <td className="py-2 text-xs">
                    {topic.weakArea ? (
                      <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 font-medium text-amber-800">
                        Weak area
                      </span>
                    ) : topic.robustSample ? (
                      <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 font-medium text-emerald-700">
                        Reliable sample
                      </span>
                    ) : (
                      <span className="rounded-full border border-border bg-muted px-2 py-0.5 text-muted-foreground">
                        Small sample
                      </span>
                    )}
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
