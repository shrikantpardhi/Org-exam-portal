"use client";

import {
  AnalyticsEmpty,
  AnalyticsError,
  AnalyticsLoading,
  AnalyticsStaleNotice,
} from "@/components/analytics/analytics-states";
import { FreshnessIndicator } from "@/components/analytics/freshness-indicator";
import { describeAnalyticsError, formatInteger } from "@/components/analytics/format";
import { MetricCard } from "@/components/analytics/metric-card";
import { useOrganizationAnalytics } from "@/features/analytics/hooks/use-analytics";

/**
 * Organization analytics dashboard ({@code specs/17-P8-Analytics-V1.5.md §40-§44, §56}).
 *
 * <p>Staff access is permission-driven: the screen is rendered only behind the
 * {@code ANALYTICS_VIEW} affordance and the backend independently enforces the same permission
 * and organization scope ({@code §108, §148}). Revenue widgets remain Commerce-owned and are
 * deliberately absent ({@code §56}).
 */
export function OrganizationAnalyticsDashboard() {
  const query = useOrganizationAnalytics();

  if (query.isPending) {
    return <AnalyticsLoading label="Loading organization analytics…" />;
  }

  const errorMessage = describeAnalyticsError(
    query.error,
    "Organization analytics could not be loaded.",
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

  const analytics = query.data;
  if (!analytics) {
    return <AnalyticsLoading label="Loading organization analytics…" />;
  }

  const isEmpty =
    (analytics.totalStudents ?? 0) === 0 &&
    (analytics.totalAssessments ?? 0) === 0 &&
    (analytics.totalAttempts ?? 0) === 0;

  return (
    <section className="space-y-6">
      {query.isError ? (
        <AnalyticsStaleNotice
          message="Refreshing organization analytics failed; the values below are the last known ones."
          onRetry={() => {
            void query.refetch();
          }}
        />
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-mono text-xs text-muted-foreground">
            {analytics.organizationId ?? "Current organization"}
          </p>
          <p className="text-sm text-muted-foreground">
            Organization-scoped aggregates. Access is limited to the organization and permissions
            assigned in Identity.
          </p>
        </div>
        <FreshnessIndicator
          lastCalculatedOn={analytics.lastCalculatedOn}
          freshness={analytics.freshness}
        />
      </div>

      {isEmpty ? (
        <AnalyticsEmpty
          message="No organization analytics yet."
          hint="Aggregates appear after students have attempted and evaluated assessments."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            label="Students"
            value={formatInteger(analytics.totalStudents)}
            description="Active users holding the Student role; archived and inactive users are excluded."
          />
          <MetricCard
            label="Assessments"
            value={formatInteger(analytics.totalAssessments)}
            description="Qualifying Assessment records; drafts and archived assessments are excluded."
          />
          <MetricCard
            label="Attempts"
            value={formatInteger(analytics.totalAttempts)}
            description="Attempts under the approved inclusion policy, without double-counting retries."
          />
          <MetricCard
            label="Active users"
            value={formatInteger(analytics.activeUsers)}
            description="Students with at least one attempt started inside the configured 30-day window."
          />
        </div>
      )}
    </section>
  );
}
