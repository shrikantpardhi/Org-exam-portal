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
  formatDate,
  formatDecimal,
  formatInteger,
  formatPercent,
  shortId,
} from "@/components/analytics/format";
import { MetricCard } from "@/components/analytics/metric-card";
import { ProgressView } from "@/components/analytics/progress-view";
import { TopicAnalysis } from "@/components/analytics/topic-analysis";
import { TrendChart } from "@/components/analytics/trend-chart";
import { useStudentAnalytics } from "@/features/analytics/hooks/use-analytics";

/**
 * Student analytics dashboard ({@code specs/17-P8-Analytics-V1.5.md §50-§52}).
 *
 * <p>The read is self-scoped by the backend: the path carries no user id, so a student can only
 * ever load their own analytics. When no evaluated attempt qualifies, a clear empty state is shown
 * instead of zero percent accuracy or a zero average score ({@code §52}).
 */
export function StudentAnalyticsDashboard() {
  const query = useStudentAnalytics();

  if (query.isPending) {
    return <AnalyticsLoading label="Loading your analytics…" />;
  }

  const errorMessage = describeAnalyticsError(
    query.error,
    "Your analytics could not be loaded.",
  );

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

  const dashboard = query.data;
  if (!dashboard) {
    return <AnalyticsLoading label="Loading your analytics…" />;
  }

  const { summary, recentAssessments, topics } = dashboard;
  const isEmpty =
    (summary.assessmentsAttempted ?? 0) === 0 &&
    recentAssessments.length === 0 &&
    topics.length === 0;

  const trendPoints = [...recentAssessments]
    .sort((left, right) => {
      const leftTime = left.startedOn ? new Date(left.startedOn).getTime() : 0;
      const rightTime = right.startedOn ? new Date(right.startedOn).getTime() : 0;
      return leftTime - rightTime;
    })
    .map((assessment) => ({
      label: formatDate(assessment.startedOn) ?? shortId(assessment.attemptId),
      value: assessment.percentage,
    }));

  const reliableTopics = topics.filter((topic) => topic.robustSample).length;

  return (
    <section className="space-y-6">
      {query.isError ? (
        <AnalyticsStaleNotice
          message="Refreshing your analytics failed; the values below are the last known ones."
          onRetry={() => {
            void query.refetch();
          }}
        />
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">Your performance</h2>
          <p className="text-sm text-muted-foreground">
            Derived from your evaluated attempts. Only published and authorized result data is
            used.
          </p>
        </div>
        <FreshnessIndicator
          lastCalculatedOn={summary.lastCalculatedOn}
          freshness={summary.freshness}
        />
      </div>

      {isEmpty ? (
        <AnalyticsEmpty
          message="No evaluated attempts yet."
          hint="Your analytics appear after an attempt has been evaluated. Until then no accuracy or average score is shown."
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Assessments attempted"
              value={formatInteger(summary.assessmentsAttempted)}
              description="Qualifying attempts with an evaluated result."
            />
            <MetricCard
              label="Average score"
              value={formatDecimal(summary.averageScore, 2)}
              unit="marks"
              description="Mean obtained marks; the scale is each assessment's published total marks."
            />
            <MetricCard
              label="Accuracy"
              value={formatPercent(summary.accuracy)}
              description="Correct answers divided by answered questions across evaluated attempts."
            />
            <MetricCard
              label="Average time"
              value={formatDecimal(summary.averageTime, 1)}
              unit="s"
              description="Mean persisted time per answered question."
            />
          </div>

          <TrendChart
            title="Score trend"
            description="Percentage obtained per evaluated attempt, oldest first. The percentage is relative to each assessment's published total marks."
            unit="%"
            max={100}
            points={trendPoints}
            lastCalculatedOn={summary.lastCalculatedOn}
            freshness={summary.freshness}
            emptyMessage="No evaluated attempt to chart yet."
          />

          <TopicAnalysis
            topics={topics}
            lastCalculatedOn={summary.lastCalculatedOn}
            freshness={summary.freshness}
          />

          {topics.length > 0 ? (
            <ProgressView
              title="Topic sample coverage"
              description="Share of your topics that have reached the platform minimum reliable sample. A topic below it is reported but not classified as a weak area."
              completed={reliableTopics}
              total={topics.length}
              unitLabel="topics with a reliable sample"
              emptyMessage="No topic mapping available."
            />
          ) : null}

          {recentAssessments.length > 0 ? (
            <section aria-labelledby="recent-assessments-heading" className="space-y-2">
              <h3 id="recent-assessments-heading" className="text-base font-semibold">
                Recent assessments
              </h3>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <caption className="sr-only">Your evaluated attempts</caption>
                  <thead>
                    <tr className="border-b border-border">
                      <th scope="col" className="py-2 pr-3">
                        Assessment
                      </th>
                      <th scope="col" className="py-2 pr-3">
                        Mode
                      </th>
                      <th scope="col" className="py-2 pr-3">
                        Score
                      </th>
                      <th scope="col" className="py-2 pr-3">
                        Percentage
                      </th>
                      <th scope="col" className="py-2 pr-3">
                        Result
                      </th>
                      <th scope="col" className="py-2">
                        Started
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentAssessments.map((assessment) => (
                      <tr key={assessment.attemptId} className="border-b border-border">
                        <th scope="row" className="py-2 pr-3 font-normal">
                          <code
                            className="rounded bg-muted px-1 py-0.5 text-xs"
                            title={assessment.assessmentId ?? undefined}
                          >
                            {shortId(assessment.assessmentId)}
                          </code>
                        </th>
                        <td className="py-2 pr-3">{assessment.attemptMode ?? "Not available"}</td>
                        <td className="py-2 pr-3 tabular-nums">
                          {formatDecimal(assessment.score, 2) ?? "Not available"}
                        </td>
                        <td className="py-2 pr-3 tabular-nums">
                          {formatPercent(assessment.percentage) ?? "Not available"}
                        </td>
                        <td className="py-2 pr-3">{assessment.resultStatus ?? "Not available"}</td>
                        <td className="py-2">{formatDate(assessment.startedOn) ?? "Not available"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ) : null}
        </>
      )}
    </section>
  );
}
