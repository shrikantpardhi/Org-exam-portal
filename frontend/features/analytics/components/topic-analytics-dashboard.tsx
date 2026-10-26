"use client";

import {
  AnalyticsEmpty,
  AnalyticsError,
  AnalyticsLoading,
  AnalyticsStaleNotice,
} from "@/components/analytics/analytics-states";
import { FreshnessIndicator } from "@/components/analytics/freshness-indicator";
import {
  describeAnalyticsError,
  formatDecimal,
  formatInteger,
  formatPercent,
} from "@/components/analytics/format";
import { MetricCard } from "@/components/analytics/metric-card";
import { useTopicAnalytics } from "@/features/analytics/hooks/use-analytics";

/**
 * Topic analytics screen ({@code specs/17-P8-Analytics-V1.5.md §55}).
 *
 * <p>The topic hierarchy is owned by the Academic domain. The read model exposes the topic id and
 * its derived metrics; no taxonomy is invented here.
 */
export function TopicAnalyticsDashboard({ topicId }: { topicId: string }) {
  const query = useTopicAnalytics(topicId);

  if (topicId.length === 0) {
    return <AnalyticsError message="No topic was selected." />;
  }

  if (query.isPending) {
    return <AnalyticsLoading label="Loading topic analytics…" />;
  }

  const errorMessage = describeAnalyticsError(query.error, "Topic analytics could not be loaded.");

  if (query.isError && !query.data) {
    return (
      <AnalyticsError
        message={errorMessage}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const analytics = query.data;
  if (!analytics) {
    return <AnalyticsLoading label="Loading topic analytics…" />;
  }

  const isEmpty = (analytics.totalAttempts ?? 0) === 0;

  return (
    <section className="space-y-6">
      {query.isError ? (
        <AnalyticsStaleNotice
          message="Refreshing topic analytics failed; the values below are the last known ones."
          onRetry={() => {
            void query.refetch();
          }}
        />
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-3">
        <p className="font-mono text-xs text-muted-foreground">{topicId}</p>
        <FreshnessIndicator
          lastCalculatedOn={analytics.lastCalculatedOn}
          freshness={analytics.freshness}
        />
      </div>

      {isEmpty ? (
        <AnalyticsEmpty
          message="No topic mapping available."
          hint="Topic metrics appear once evaluated questions are mapped to this topic."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <MetricCard
            label="Attempts"
            value={formatInteger(analytics.totalAttempts)}
            description="Evaluated attempts that produced an outcome for this topic."
          />
          <MetricCard
            label="Accuracy"
            value={formatPercent(analytics.accuracy)}
            description="Correct answers divided by evaluated questions mapped to this topic."
          />
          <MetricCard
            label="Average score"
            value={formatDecimal(analytics.averageScore, 2)}
            unit="marks"
            description="Mean awarded marks for questions mapped to this topic."
          />
          <MetricCard
            label="Sample taken"
            value={formatInteger(analytics.sampleSize)}
            description="Evaluated question occurrences that feed this topic."
          />
          <MetricCard
            label="Sample status"
            value={analytics.robustSample ? "Reliable sample" : "Small sample"}
            description="A reliable sample reaches the platform configured minimum; below it, accuracy is reported but not classified as a weak area."
          />
        </div>
      )}
    </section>
  );
}
