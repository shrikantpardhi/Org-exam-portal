"use client";

import { useId } from "react";

import type {
  ResultResponse,
  ScorecardResponse,
} from "@/features/evaluation/schemas/evaluation";

import { ResultCard } from "./result-card";
import { formatTimestamp, shortId } from "./format";

/**
 * Scorecard view ({@code specs/15-P6-Evaluation-V1.5.md §57-§61, §98}).
 *
 * <p>The scorecard payload carries metadata and a secure reference, never the private
 * {@code file_path} ({@code §58}). The file bytes are served by the secure files boundary, so this
 * view shows the reference as text and does not fabricate a download URL.
 */
const STATUS_LABELS: Record<string, string> = {
  GENERATED: "Generated",
  PENDING: "Pending",
  FAILED: "Failed",
};

const STATUS_STYLES: Record<string, string> = {
  GENERATED: "border-success bg-success-subtle text-success",
  PENDING: "border-warning bg-warning-subtle text-warning",
  FAILED: "border-destructive bg-destructive-subtle text-destructive",
};

export function ScorecardView({
  scorecard,
  result,
}: {
  scorecard: ScorecardResponse;
  result?: ResultResponse | null;
}) {
  const headingId = useId();
  const statusKey = (scorecard.status ?? "").toUpperCase();
  const generated = formatTimestamp(scorecard.generatedOn);
  const showResult =
    result != null && result.resultStatus === "PUBLISHED" && result.supersededOn === null;

  return (
    <section aria-labelledby={headingId} className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 id={headingId} className="text-xl font-semibold tracking-tight">
            Scorecard
          </h2>
          <p className="text-sm text-muted-foreground">
            A presentation artifact generated from the published result.
          </p>
        </div>
        <span
          data-scorecard-status={statusKey || "UNKNOWN"}
          className={`rounded-full border px-2 py-0.5 text-xs font-medium ${
            STATUS_STYLES[statusKey] ?? "border-border bg-muted text-muted-foreground"
          }`}
        >
          {STATUS_LABELS[statusKey] ?? "Unknown status"}
        </span>
      </div>

      <dl className="grid gap-4 rounded-lg border border-border bg-surface p-4 shadow-sm sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Scorecard</dt>
          <dd className="mt-1 font-mono text-sm">
            <code title={scorecard.scorecardId}>{shortId(scorecard.scorecardId)}</code>
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Generated on</dt>
          <dd className="mt-1 text-sm">
            {generated ? (
              <time dateTime={scorecard.generatedOn ?? undefined}>{generated}</time>
            ) : (
              "Not generated yet"
            )}
          </dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-xs text-muted-foreground">Secure reference</dt>
          <dd className="mt-1 break-all font-mono text-sm">
            <code>{scorecard.secureDownloadReference ?? "Not available"}</code>
          </dd>
        </div>
      </dl>

      <p className="text-xs text-muted-foreground">
        The secure reference identifies the artifact behind the files boundary. The file itself is
        served when the secure download boundary is enabled.
      </p>

      {showResult ? <ResultCard result={result} heading="Result summary" /> : null}
    </section>
  );
}
