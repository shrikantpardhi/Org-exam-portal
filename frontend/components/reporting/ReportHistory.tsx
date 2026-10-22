"use client";

import { useState } from "react";

import { downloadReportArtifact, reportDownloadUrl } from "@/features/reporting/api/reporting-api";
import {
  describeReportError,
  formatReportDateTime,
  reportStatusLabel,
  reportStatusTone,
} from "@/features/reporting/format";
import type { ReportHistoryEntry } from "@/features/reporting/schemas/reporting";

/**
 * The caller's report history ({@code specs/22-P13-Reporting-V1.5.md §29, §30, §109-§111}).
 *
 * <p>History is served from {@code background_job} and is never reconstructed from browser state
 * ({@code §185}). A download link is offered only for a completed job, because no artifact exists
 * for any other status.
 */
export function ReportHistory({
  entries,
  loading = false,
  error,
  onRetry,
}: {
  entries: ReportHistoryEntry[];
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
}) {
  const [downloading, setDownloading] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const download = async (entry: ReportHistoryEntry) => {
    setDownloading(entry.jobId);
    setDownloadError(null);
    try {
      await downloadReportArtifact(entry.jobId);
    } catch (caught) {
      setDownloadError(describeReportError(caught, "The report artifact could not be downloaded."));
    } finally {
      setDownloading(null);
    }
  };

  if (loading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-500">
        Loading report history…
      </p>
    );
  }

  if (error) {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        <p>{error}</p>
        {onRetry ? (
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 rounded border border-red-300 bg-white px-3 py-1 text-sm font-medium text-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
          >
            Try again
          </button>
        ) : null}
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <p role="note" className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-700">
        No report history yet.
      </p>
    );
  }

  return (
    <div className="space-y-2 rounded-lg border border-slate-200 bg-white p-4">
      <h2 className="text-lg font-semibold tracking-tight">Report history</h2>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Report history</caption>
          <thead>
            <tr className="border-b border-slate-200">
              <th scope="col" className="py-2 pr-3 font-medium">Report</th>
              <th scope="col" className="py-2 pr-3 font-medium">Status</th>
              <th scope="col" className="py-2 pr-3 font-medium">Queued</th>
              <th scope="col" className="py-2 pr-3 font-medium">Download</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => {
              const completed =
                entry.status === "COMPLETED" || entry.status === "COMPLETED_WITH_ERRORS";
              const available = completed && Boolean(entry.downloadUrl);
              return (
                <tr key={entry.jobId} data-job-id={entry.jobId} className="border-b border-slate-100 align-top">
                  <td className="py-2 pr-3">
                    <span className="font-medium">{entry.reportType}</span>
                    <span className="block text-xs text-slate-500">{entry.jobId}</span>
                  </td>
                  <td className="py-2 pr-3">
                    <span
                      data-report-status={entry.status}
                      className={`inline-block rounded-full border px-2 py-0.5 text-xs font-medium ${reportStatusTone(entry.status)}`}
                    >
                      {reportStatusLabel(entry.status)}
                    </span>
                  </td>
                  <td className="py-2 pr-3 text-slate-600">{formatReportDateTime(entry.queuedOn)}</td>
                  <td className="py-2 pr-3">
                    {available ? (
                      <a
                        href={reportDownloadUrl(entry.jobId)}
                        onClick={(event) => {
                          event.preventDefault();
                          void download(entry);
                        }}
                        className="text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                      >
                        {downloading === entry.jobId ? "Downloading…" : "Download"}
                      </a>
                    ) : (
                      <span className="text-xs text-slate-500">Not available</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {downloadError ? (
        <p role="alert" className="text-xs text-red-600">
          {downloadError}
        </p>
      ) : null}
    </div>
  );
}
