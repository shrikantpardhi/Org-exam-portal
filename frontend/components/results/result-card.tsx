"use client";

import Link from "next/link";

import type { ResultResponse } from "@/features/evaluation/schemas/evaluation";

import { RankBadge } from "./rank-badge";
import { ResultStatusBadge } from "./result-status-badge";
import { formatDecimal, formatPercent, shortId } from "./format";

/**
 * The official result summary ({@code specs/15-P6-Evaluation-V1.5.md §95, §116}).
 *
 * <p>Shows obtained marks, percentage, rank, percentile and status. A missing mark, percentage or
 * percentile is "Not available", and a missing rank is "Unranked"; neither becomes zero.
 */
export interface ResultCardProps {
  result: ResultResponse;
  /** Visible heading; defaults to a short result identifier. */
  heading?: string;
  /** When set, the card links to the full result view. */
  href?: string;
}

export function ResultCard({ result, heading, href }: ResultCardProps) {
  const title = heading ?? `Result ${shortId(result.id)}`;
  const marks = formatDecimal(result.obtainedMarks, 2);
  const percentage = formatPercent(result.percentage, 2);
  const percentile = formatDecimal(result.percentile, 2);

  return (
    <article
      aria-label={title}
      className="rounded-lg border border-border bg-surface p-4 shadow-sm"
    >
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-base font-semibold text-foreground">{title}</h2>
          <p className="mt-1 font-mono text-xs text-muted-foreground">
            Attempt {shortId(result.attemptId)} · Version {shortId(result.assessmentVersionId)}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ResultStatusBadge status={result.resultStatus} />
          <RankBadge rank={result.rank} />
        </div>
      </header>

      <dl className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">Obtained marks</dt>
          <dd className="mt-1 text-lg font-semibold tabular-nums text-foreground">
            {marks ?? <span className="text-sm font-normal text-muted-foreground">Not available</span>}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Percentage</dt>
          <dd className="mt-1 text-lg font-semibold tabular-nums text-foreground">
            {percentage ?? (
              <span className="text-sm font-normal text-muted-foreground">Not available</span>
            )}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Rank</dt>
          <dd className="mt-1 text-lg font-semibold tabular-nums text-foreground">
            {result.rank !== null && result.rank > 0 ? `#${result.rank}` : (
              <span className="text-sm font-normal text-muted-foreground">Unranked</span>
            )}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Percentile</dt>
          <dd
            className="mt-1 text-lg font-semibold tabular-nums text-foreground"
            title="Share of the compared cohort this result ranks above."
          >
            {percentile ?? (
              <span className="text-sm font-normal text-muted-foreground">Not available</span>
            )}
          </dd>
        </div>
      </dl>

      {href ? (
        <footer className="mt-4 text-sm">
          <Link
            href={href}
            className="rounded font-medium text-info underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            View result
          </Link>
        </footer>
      ) : null}
    </article>
  );
}
