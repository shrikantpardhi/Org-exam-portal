"use client";

import { describeResultError } from "@/components/results/format";
import { ResultsError, ResultsLoading } from "@/components/results/result-states";
import { ResultDetail } from "@/features/evaluation/components/result-detail";
import { useResult } from "@/features/evaluation/hooks/use-results";

/** {@code GET /results/{id}} — the caller must own a published result ({@code §39, §95}). */
export function ResultByIdView({ resultId }: { resultId: string }) {
  const query = useResult(resultId);

  if (resultId.length === 0) {
    return <ResultsError message="No result was selected." />;
  }

  if (query.isPending) {
    return <ResultsLoading label="Loading result…" />;
  }

  if (query.isError) {
    return (
      <ResultsError
        message={describeResultError(query.error)}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  if (!query.data) {
    return <ResultsLoading label="Loading result…" />;
  }

  return <ResultDetail result={query.data} />;
}
