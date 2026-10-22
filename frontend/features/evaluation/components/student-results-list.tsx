"use client";

import { ResultCard } from "@/components/results/result-card";
import { describeResultError } from "@/components/results/format";
import { ResultsEmpty, ResultsError, ResultsLoading } from "@/components/results/result-states";
import { useStudentResults } from "@/features/evaluation/hooks/use-results";

/**
 * The student's published results ({@code specs/15-P6-Evaluation-V1.5.md §39, §95}).
 *
 * <p>Only published, non-superseded results are listed. The list is composed from the frozen,
 * self-scoped attempt and per-attempt result reads, so another student's result cannot appear even
 * if its identifier were known.
 */
export function StudentResultsList() {
  const query = useStudentResults();

  if (query.isPending) {
    return <ResultsLoading label="Loading your results…" />;
  }

  if (query.isError) {
    return (
      <ResultsError
        message={describeResultError(query.error, "Your results could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const items = query.data ?? [];

  if (items.length === 0) {
    return (
      <ResultsEmpty
        message="No published results yet."
        hint="A result appears here after your attempt is evaluated and the result is published."
      />
    );
  }

  return (
    <section aria-label="Published results" className="space-y-3">
      <p className="text-sm text-slate-500">
        {items.length} published {items.length === 1 ? "result" : "results"}
      </p>
      <ul className="grid gap-4 sm:grid-cols-2">
        {items.map(({ result }) => (
          <li key={result.id}>
            <ResultCard result={result} href={`/results/${result.id}`} />
          </li>
        ))}
      </ul>
    </section>
  );
}
