"use client";

import { describeResultError } from "@/components/results/format";
import { ResultsError, ResultsLoading } from "@/components/results/result-states";
import { ResultDetail } from "@/features/evaluation/components/result-detail";
import { useAttemptResult } from "@/features/evaluation/hooks/use-results";

/**
 * {@code GET /attempts/{attemptId}/result} — the attempt-bound result view.
 *
 * <p>The read is self-scoped by the backend; another student's attempt and an unpublished result
 * both answer 404, so the view cannot be used to discover either ({@code §39, §95}).
 */
export function AttemptResultView({ attemptId }: { attemptId: string }) {
  const query = useAttemptResult(attemptId);

  if (attemptId.length === 0) {
    return <ResultsError message="No attempt was selected." />;
  }

  if (query.isPending) {
    return <ResultsLoading label="Loading attempt result…" />;
  }

  if (query.isError) {
    return (
      <ResultsError
        message={describeResultError(
          query.error,
          "This attempt does not have a published result yet.",
        )}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  if (!query.data) {
    return <ResultsLoading label="Loading attempt result…" />;
  }

  return <ResultDetail result={query.data} />;
}
