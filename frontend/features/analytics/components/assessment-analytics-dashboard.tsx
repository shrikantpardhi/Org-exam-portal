"use client";

import Link from "next/link";

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
  shortId,
} from "@/components/analytics/format";
import { MetricCard } from "@/components/analytics/metric-card";
import { SectionAnalysis } from "@/components/analytics/section-analysis";
import { TrendChart } from "@/components/analytics/trend-chart";
import {
  useAssessmentAnalytics,
  useAssessmentQuestions,
} from "@/features/analytics/hooks/use-analytics";

/**
 * Assessment analytics dashboard ({@code specs/17-P8-Analytics-V1.5.md §53}).
 *
 * <p>Section analysis and question difficulty are dynamic read aggregations; the frozen analytics
 * table is intentionally compact, so they are derived from the section and question read models
 * rather than from invented trend tables.
 */
export function AssessmentAnalyticsDashboard({ assessmentId }: { assessmentId: string }) {
  const query = useAssessmentAnalytics(assessmentId);
  const questions = useAssessmentQuestions(assessmentId);

  if (assessmentId.length === 0) {
    return <AnalyticsError message="No assessment was selected." />;
  }

  if (query.isPending) {
    return <AnalyticsLoading label="Loading assessment analytics…" />;
  }

  const errorMessage = describeAnalyticsError(
    query.error,
    "Assessment analytics could not be loaded.",
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
    return <AnalyticsLoading label="Loading assessment analytics…" />;
  }

  const { summary, sections } = dashboard;
  const isEmpty = (summary.totalAttempts ?? 0) === 0;

  const questionPoints = (questions.data ?? [])
    .slice(0, 12)
    .map((question) => ({
      label: shortId(question.questionId),
      value: question.difficultyIndex,
    }));

  return (
    <section className="space-y-6">
      {query.isError ? (
        <AnalyticsStaleNotice
          message="Refreshing assessment analytics failed; the values below are the last known ones."
          onRetry={() => {
            void query.refetch();
          }}
        />
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-mono text-xs text-slate-500">{assessmentId}</p>
          <p className="text-sm text-slate-500">
            Derived from evaluated attempts. The grain is the Assessment, across its versions.
          </p>
        </div>
        <FreshnessIndicator
          lastCalculatedOn={summary.lastCalculatedOn}
          freshness={summary.freshness}
        />
      </div>

      {isEmpty ? (
        <AnalyticsEmpty
          message="No assessment data for this period."
          hint="Metrics appear after the first attempt on this assessment has been evaluated."
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Total attempts"
              value={formatInteger(summary.totalAttempts)}
              description="Evaluated attempts recorded for the assessment."
            />
            <MetricCard
              label="Average score"
              value={formatDecimal(summary.averageScore, 2)}
              unit="marks"
              description="Mean obtained marks; the scale is the published total marks."
            />
            <MetricCard
              label="Highest score"
              value={formatDecimal(summary.highestScore, 2)}
              unit="marks"
              description="Best obtained marks among evaluated attempts."
            />
            <MetricCard
              label="Lowest score"
              value={formatDecimal(summary.lowestScore, 2)}
              unit="marks"
              description="Lowest obtained marks among evaluated attempts."
            />
          </div>

          <SectionAnalysis
            sections={sections}
            lastCalculatedOn={summary.lastCalculatedOn}
            freshness={summary.freshness}
          />

          <section aria-labelledby="assessment-question-heading" className="space-y-3">
            <div>
              <h3 id="assessment-question-heading" className="text-base font-semibold">
                Question difficulty
              </h3>
              <p className="text-sm text-slate-500">
                Difficulty index per question, 1.00 the hardest and 0.00 the easiest; answered
                outcomes only.
              </p>
            </div>

            {questions.isPending ? (
              <AnalyticsLoading label="Loading question analytics…" />
            ) : null}
            {questions.isError ? (
              <AnalyticsError
                message={describeAnalyticsError(
                  questions.error,
                  "Question analytics could not be loaded.",
                )}
                onRetry={() => {
                  void questions.refetch();
                }}
              />
            ) : null}
            {questions.data && questions.data.length === 0 ? (
              <AnalyticsEmpty message="No question mapping available." />
            ) : null}
            {questions.data && questions.data.length > 0 ? (
              <>
                <TrendChart
                  title="Question difficulty"
                  description="Difficulty index for up to 12 questions; 1.00 is the hardest and 0.00 the easiest."
                  unit=""
                  max={1}
                  points={questionPoints}
                  emptyMessage="No question difficulty data yet."
                />
                <ul className="flex flex-wrap gap-3 text-sm">
                  {questions.data.map((question) => (
                    <li key={question.questionId}>
                      <Link
                        href={`/analytics/questions/${question.questionId}`}
                        className="rounded underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                      >
                        {shortId(question.questionId)}
                      </Link>
                    </li>
                  ))}
                </ul>
              </>
            ) : null}
          </section>
        </>
      )}
    </section>
  );
}
