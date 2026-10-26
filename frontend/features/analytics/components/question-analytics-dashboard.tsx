"use client";

import {
  AnalyticsError,
  AnalyticsLoading,
  AnalyticsStaleNotice,
} from "@/components/analytics/analytics-states";
import { describeAnalyticsError } from "@/components/analytics/format";
import { QuestionAnalysis } from "@/components/analytics/question-analysis";
import { useQuestionAnalytics } from "@/features/analytics/hooks/use-analytics";

/** Question analytics screen ({@code specs/17-P8-Analytics-V1.5.md §54}). */
export function QuestionAnalyticsDashboard({ questionId }: { questionId: string }) {
  const query = useQuestionAnalytics(questionId);

  if (questionId.length === 0) {
    return <AnalyticsError message="No question was selected." />;
  }

  if (query.isPending) {
    return <AnalyticsLoading label="Loading question analytics…" />;
  }

  const errorMessage = describeAnalyticsError(
    query.error,
    "Question analytics could not be loaded.",
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

  if (!query.data) {
    return <AnalyticsLoading label="Loading question analytics…" />;
  }

  return (
    <section className="space-y-6">
      {query.isError ? (
        <AnalyticsStaleNotice
          message="Refreshing question analytics failed; the values below are the last known ones."
          onRetry={() => {
            void query.refetch();
          }}
        />
      ) : null}
      <p className="font-mono text-xs text-muted-foreground">{questionId}</p>
      <QuestionAnalysis analytics={query.data} title="Question analytics" />
    </section>
  );
}
