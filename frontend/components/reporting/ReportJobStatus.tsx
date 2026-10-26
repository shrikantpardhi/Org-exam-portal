"use client";

import { useState } from "react";

import { downloadReportArtifact, reportDownloadUrl } from "@/features/reporting/api/reporting-api";
import {
  describeReportError,
  formatReportDateTime,
  reportStatusLabel,
  reportStatusTone,
} from "@/features/reporting/format";
import type { ReportJob } from "@/features/reporting/schemas/reporting";

/**
 * The status of one report job ({@code specs/22-P13-Reporting-V1.5.md §28, §112, §191}).
 *
 * <p>Queued and running are shown as in-progress; a failure shows the server's safe code and
 * message only (no stack trace); a completed job exposes the protected download. The whole block is
 * a polite live region so a status transition is announced, and the link is a real link that
 * performs an authenticated fetch, because the access token is not attached to a bare navigation.
 */
export function ReportJobStatus({
  job,
  jobId,
  pollingStopped = false,
}: {
  job: ReportJob | null;
  jobId: string | null;
  pollingStopped?: boolean;
}) {
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  if (!jobId) {
    return null;
  }

  if (!job) {
    return (
      <section
        role="status"
        aria-live="polite"
        aria-label="Report job status"
        className="rounded-lg border border-border bg-surface p-4 text-sm text-muted-foreground"
      >
        Reading report job {jobId}…
      </section>
    );
  }

  const status = job.status;
  const completed = status === "COMPLETED" || status === "COMPLETED_WITH_ERRORS";
  const terminal = completed || status === "FAILED" || status === "CANCELLED";
  const downloadHref = reportDownloadUrl(job.jobId);

  const download = async () => {
    setDownloading(true);
    setDownloadError(null);
    try {
      await downloadReportArtifact(job.jobId, job.artifactName);
    } catch (error) {
      setDownloadError(
        describeReportError(error, "The report artifact could not be downloaded."),
      );
    } finally {
      setDownloading(false);
    }
  };

  return (
    <section
      role="status"
      aria-live="polite"
      aria-label="Report job status"
      className="space-y-2 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-semibold tracking-tight">Report job</h2>
          <p className="text-xs text-muted-foreground">
            {job.reportType ?? "Report"} · {job.jobId}
          </p>
        </div>
        <span
          data-report-status={status}
          className={`rounded-full border px-2 py-0.5 text-xs font-medium ${reportStatusTone(status)}`}
        >
          {reportStatusLabel(status)}
        </span>
      </div>

      {status === "QUEUED" ? (
        <p className="text-sm text-muted-foreground">The report is queued and has not started yet.</p>
      ) : null}
      {status === "RUNNING" ? (
        <p className="text-sm text-muted-foreground">The report is running. This page updates itself.</p>
      ) : null}
      {status === "UNKNOWN" ? (
        <p className="text-sm text-warning">
          The server reported a status this screen does not recognize. It is not treated as complete.
        </p>
      ) : null}

      {status === "FAILED" || status === "COMPLETED_WITH_ERRORS" ? (
        <div role="alert" className="rounded border border-destructive bg-destructive-subtle p-3 text-sm text-destructive">
          <p className="font-medium">
            {status === "FAILED" ? "The report could not be generated." : "Completed with errors."}
          </p>
          {job.errorCode ? <p className="text-xs">Code: {job.errorCode}</p> : null}
          <p>{job.errorMessage ?? "The server did not provide a reason."}</p>
        </div>
      ) : null}

      <dl className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-2">
        <div>
          <dt className="text-xs font-medium text-muted-foreground">Queued</dt>
          <dd>{formatReportDateTime(job.queuedOn)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-muted-foreground">Completed</dt>
          <dd>{formatReportDateTime(job.completedOn)}</dd>
        </div>
      </dl>

      {completed ? (
        <div className="space-y-1">
          <a
            href={downloadHref}
            onClick={(event) => {
              // The bearer token is in sessionStorage, so a bare navigation would be unauthorized.
              event.preventDefault();
              void download();
            }}
            className="inline-block rounded border border-input bg-surface px-3 py-1 text-sm font-medium text-info underline hover:border-info focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {downloading ? "Downloading…" : "Download report"}
          </a>
          {job.artifactName ? (
            <p className="text-xs text-muted-foreground">{job.artifactName}</p>
          ) : null}
          {downloadError ? (
            <p role="alert" className="text-xs text-destructive">
              {downloadError}
            </p>
          ) : null}
        </div>
      ) : null}

      {terminal && !completed ? (
        <p className="text-sm text-muted-foreground">No artifact was produced for this job.</p>
      ) : null}

      {pollingStopped && !terminal ? (
        <p role="note" className="text-sm text-warning">
          Automatic status updates stopped after reaching the polling limit. Open the report history
          to check the final outcome.
        </p>
      ) : null}
    </section>
  );
}
