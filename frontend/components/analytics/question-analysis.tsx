"use client";

import { useId } from "react";

import type { QuestionAnalytics } from "@/features/analytics/schemas/analytics";

import { AnalyticsEmpty } from "./analytics-states";
import { FreshnessIndicator } from "./freshness-indicator";
import { MetricCard } from "./metric-card";
import { TrendChart } from "./trend-chart";
import { formatDecimal, formatInteger, formatPercent } from "./format";

/**
 * Question analytics ({@code specs/17-P8-Analytics-V1.5.md §28-§35, §54}).
 *
 * <p>The difficulty index uses the frozen formula {@code 1 - correct / total}, where the
 * denominator counts answered outcomes only; unanswered attempts are reported separately as
 * skipped and never inflate difficulty ({@code §35}).
 */
export function QuestionAnalysis({
  analytics,
  title = "Question analysis",
}: {
  analytics: QuestionAnalytics;
  title?: string;
}) {
  const headingId = useId();
  const hasPopulation = (analytics.totalAttempts ?? 0) > 0;

  return (
    <section aria-labelledby={headingId} className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 id={headingId} className="text-xl font-semibold tracking-tight">
            {title}
          </h2>
          <p className="text-sm text-slate-500">
            Question-level identity. The difficulty index and outcome shares use answered
            outcomes; skipped responses are reported separately.
          </p>
        </div>
        <FreshnessIndicator
          lastCalculatedOn={analytics.lastCalculatedOn}
          freshness={analytics.freshness}
        />
      </div>

      {!hasPopulation ? (
        <AnalyticsEmpty
          message="No responses recorded for this question yet."
          hint="Outcome shares and the difficulty index appear after the first evaluated response."
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <MetricCard
              label="Attempts"
              value={formatInteger(analytics.totalAttempts)}
              description="Evaluated attempts recorded for this question."
            />
            <MetricCard
              label="Correct"
              value={formatPercent(analytics.correctPercentage)}
              description="Share of answered attempts scored correct."
            />
            <MetricCard
              label="Incorrect"
              value={formatPercent(analytics.incorrectPercentage)}
              description="Share of answered attempts scored incorrect."
            />
            <MetricCard
              label="Skipped"
              value={formatPercent(analytics.skippedPercentage)}
              description="Share of the response population left unanswered."
            />
            <MetricCard
              label="Average time"
              value={formatDecimal(analytics.averageTime, 1)}
              unit="s"
              description="Mean persisted time per answered response."
            />
            <MetricCard
              label="Difficulty index"
              value={formatDecimal(analytics.difficultyIndex, 2)}
              unit="of 1"
              description="1.00 is the hardest and 0.00 the easiest; answered outcomes only."
            />
          </div>

          <TrendChart
            title="Response distribution"
            description="Share of answered attempts by outcome. The skipped share comes from the response population, so the three shares may not total 100%."
            unit="%"
            max={100}
            points={[
              { label: "Correct", value: analytics.correctPercentage },
              { label: "Incorrect", value: analytics.incorrectPercentage },
              { label: "Skipped", value: analytics.skippedPercentage },
            ]}
            lastCalculatedOn={analytics.lastCalculatedOn}
            freshness={analytics.freshness}
            emptyMessage="No response outcome data yet."
          />
        </>
      )}
    </section>
  );
}
