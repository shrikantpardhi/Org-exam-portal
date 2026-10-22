"use client";

import Link from "next/link";
import { useId } from "react";

import { ResultCard } from "@/components/results/result-card";
import { ResultVisibilityNotice } from "@/components/results/result-states";
import type { ResultResponse } from "@/features/evaluation/schemas/evaluation";

/**
 * The student-facing result view ({@code specs/15-P6-Evaluation-V1.5.md §39, §95, §116}).
 *
 * <p>Only a PUBLISHED, non-superseded result is rendered. This is defence in depth: the backend
 * already answers 404 for anything else, and the UI independently refuses to display provisional
 * marks, rank or percentile.
 */
export function ResultDetail({ result }: { result: ResultResponse }) {
  const identifiersId = useId();
  const visible = result.resultStatus === "PUBLISHED" && result.supersededOn === null;

  if (!visible) {
    return (
      <ResultVisibilityNotice
        status={result.resultStatus === "PUBLISHED" ? "SUPERSEDED" : result.resultStatus}
      />
    );
  }

  return (
    <section className="space-y-6">
      <ResultCard result={result} heading="Your result" />

      <nav aria-label="Result actions" className="flex flex-wrap gap-4 text-sm">
        <Link
          href={`/results/${result.id}/scorecard`}
          className="rounded font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          View scorecard
        </Link>
        {result.attemptId ? (
          <Link
            href={`/attempts/${result.attemptId}/result`}
            className="rounded font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Attempt-bound result
          </Link>
        ) : null}
      </nav>

      <section
        aria-labelledby={identifiersId}
        className="space-y-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
      >
        <h2 id={identifiersId} className="text-base font-semibold">
          Result identifiers
        </h2>
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-xs text-slate-500">Result</dt>
            <dd className="mt-1 break-all font-mono text-xs">{result.id}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Assessment version</dt>
            <dd className="mt-1 break-all font-mono text-xs">
              {result.assessmentVersionId ?? "Not available"}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Evaluation</dt>
            <dd className="mt-1 text-slate-600">Evaluation internals are not exposed.</dd>
          </div>
        </dl>
      </section>
    </section>
  );
}
