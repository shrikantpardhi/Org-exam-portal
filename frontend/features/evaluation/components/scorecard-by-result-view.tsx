"use client";

import { describeResultError, isNotFound } from "@/components/results/format";
import { ScorecardView } from "@/components/results/scorecard-view";
import {
  ResultVisibilityNotice,
  ResultsEmpty,
  ResultsError,
  ResultsLoading,
} from "@/components/results/result-states";
import { useResult, useScorecard } from "@/features/evaluation/hooks/use-results";

/**
 * The scorecard screen for a result ({@code specs/15-P6-Evaluation-V1.5.md §58, §98}).
 *
 * <p>The scorecard is read only for a published result the caller owns. A missing scorecard is an
 * empty state: generation is an authorized staff action, never a student one.
 */
export function ScorecardByResultView({ resultId }: { resultId: string }) {
  const resultQuery = useResult(resultId);
  const result = resultQuery.data;
  const published = result?.resultStatus === "PUBLISHED" && result.supersededOn === null;
  const scorecardQuery = useScorecard(resultId, published === true);

  if (resultId.length === 0) {
    return <ResultsError message="No result was selected." />;
  }

  if (resultQuery.isPending) {
    return <ResultsLoading label="Loading result…" />;
  }

  if (resultQuery.isError) {
    return (
      <ResultsError
        message={describeResultError(resultQuery.error)}
        onRetry={() => {
          void resultQuery.refetch();
        }}
      />
    );
  }

  if (!result) {
    return <ResultsLoading label="Loading result…" />;
  }

  if (!published) {
    return (
      <ResultVisibilityNotice
        status={result.resultStatus === "PUBLISHED" ? "SUPERSEDED" : result.resultStatus}
      />
    );
  }

  if (scorecardQuery.isPending) {
    return <ResultsLoading label="Loading scorecard…" />;
  }

  if (scorecardQuery.isError) {
    if (isNotFound(scorecardQuery.error)) {
      return (
        <ResultsEmpty
          message="No scorecard has been generated for this result yet."
          hint="An authorized evaluator generates the scorecard from the published result."
        />
      );
    }
    return (
      <ResultsError
        message={describeResultError(scorecardQuery.error, "The scorecard could not be loaded.")}
        onRetry={() => {
          void scorecardQuery.refetch();
        }}
      />
    );
  }

  if (!scorecardQuery.data) {
    return <ResultsLoading label="Loading scorecard…" />;
  }

  return <ScorecardView scorecard={scorecardQuery.data} result={result} />;
}
