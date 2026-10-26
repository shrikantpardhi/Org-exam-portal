"use client";

import { useState } from "react";

import { ReportCatalog } from "@/components/reporting/ReportCatalog";
import { ReportFilterForm } from "@/components/reporting/ReportFilterForm";
import { ReportHistory } from "@/components/reporting/ReportHistory";
import { ReportJobStatus } from "@/components/reporting/ReportJobStatus";
import { describeReportError } from "@/features/reporting/format";
import {
  useReportCatalog,
  useReportHistory,
  useReportJob,
  useRequestReport,
} from "@/features/reporting/hooks/use-reporting";
import type {
  ReportCatalogEntry,
  ReportRequestInput,
  ReportResult,
} from "@/features/reporting/schemas/reporting";

/**
 * The reporting workspace shared by the student, staff and admin screens
 * ({@code specs/22-P13-Reporting-V1.5.md §109-§112}).
 *
 * <p>The flow is the frozen one: select a report, enter filters, validate, generate, then either
 * render a small synchronous result or follow a queued job to completion and download it. Only the
 * copy differs between the three audiences; the behaviour does not, so it lives here once.
 */
export function ReportingWorkspace({
  scopeNote,
  heading = "Reports",
  historyLimit = 50,
}: {
  /** The audience-specific statement of what the backend scopes automatically. */
  scopeNote: string;
  heading?: string;
  historyLimit?: number;
}) {
  const catalog = useReportCatalog();
  const history = useReportHistory(historyLimit);
  const request = useRequestReport();

  const [selected, setSelected] = useState<ReportCatalogEntry | null>(null);
  const [result, setResult] = useState<ReportResult | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const job = useReportJob(jobId);

  const selectReport = (entry: ReportCatalogEntry) => {
    setSelected(entry);
    setResult(null);
    setJobId(null);
    setFormError(null);
    request.reset();
  };

  const generate = (body: ReportRequestInput) => {
    setFormError(null);
    setResult(null);
    setJobId(null);
    request.mutate(body, {
      onSuccess: (outcome) => {
        if (outcome.kind === "completed") {
          setResult(outcome.result);
          return;
        }
        setJobId(outcome.jobId);
        // The mutation's onSuccess already invalidates history, so the queued job appears there.
      },
      onError: (error) => {
        setFormError(describeReportError(error, "The report could not be requested."));
      },
    });
  };

  return (
    <div className="space-y-6">
      <p role="note" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">
        {scopeNote}
      </p>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">{heading}</h2>
        {catalog.isPending ? (
          <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
            Loading the report catalog…
          </p>
        ) : catalog.isError ? (
          <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <p>{describeReportError(catalog.error, "The report catalog could not be loaded.")}</p>
            <button
              type="button"
              onClick={() => {
                void catalog.refetch();
              }}
              className="mt-2 rounded border border-red-300 bg-surface px-3 py-1 text-sm font-medium text-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
            >
              Try again
            </button>
          </div>
        ) : (
          <ReportCatalog
            entries={catalog.data ?? []}
            selectedType={selected?.reportType ?? null}
            onSelect={selectReport}
          />
        )}
      </section>

      {selected ? (
        <ReportFilterForm
          // Keyed by type so switching reports resets the entered values and the chosen format.
          key={selected.reportType}
          entry={selected}
          busy={request.isPending}
          onSubmit={generate}
        />
      ) : (
        <p role="note" className="rounded-lg border border-dashed border-input bg-muted p-4 text-sm text-foreground">
          Select a report to choose its filters and format.
        </p>
      )}

      {formError ? (
        <p role="alert" className="text-sm text-red-600">
          {formError}
        </p>
      ) : null}

      {result ? (
        <section aria-label="Report result" className="space-y-2 rounded-lg border border-border bg-surface p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-lg font-semibold tracking-tight">Report result</h2>
            <span className="text-xs text-muted-foreground">
              {result.rowCount === null ? "Row count unavailable" : `${result.rowCount} rows`} ·{" "}
              {result.format}
            </span>
          </div>
          {result.freshnessNote ? (
            <p className="text-sm text-muted-foreground">{result.freshnessNote}</p>
          ) : null}
          {result.artifactName ? (
            <p className="text-xs text-muted-foreground">Artifact: {result.artifactName}</p>
          ) : null}

          {result.metrics.length > 0 ? (
            <dl className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {result.metrics.map((metric, index) => (
                <div
                  key={`${metric.name}-${index}`}
                  className="rounded border border-border bg-muted p-3"
                >
                  <dt className="text-xs font-medium text-muted-foreground">{metric.name}</dt>
                  <dd className="text-lg font-semibold">{metric.value}</dd>
                  {metric.definition ? (
                    <p className="mt-1 text-xs text-muted-foreground">{metric.definition}</p>
                  ) : null}
                </div>
              ))}
            </dl>
          ) : null}

          {result.rows.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <caption className="sr-only">Report rows</caption>
                <thead>
                  <tr className="border-b border-border">
                    {Object.keys(result.rows[0] ?? {}).map((column) => (
                      <th key={column} scope="col" className="py-2 pr-3 font-medium">
                        {column}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {result.rows.map((row, index) => (
                    <tr key={index} className="border-b border-border">
                      {Object.keys(result.rows[0] ?? {}).map((column) => (
                        <td key={column} className="py-2 pr-3">
                          {row[column] ?? ""}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p role="note" className="text-sm text-muted-foreground">
              The report returned no rows for those filters.
            </p>
          )}
        </section>
      ) : null}

      <ReportJobStatus
        job={job.data ?? null}
        jobId={jobId}
        pollingStopped={job.pollsExhausted}
      />

      <ReportHistory
        entries={history.data ?? []}
        loading={history.isPending}
        error={history.isError ? describeReportError(history.error, "Report history could not be loaded.") : null}
        onRetry={() => {
          void history.refetch();
        }}
      />
    </div>
  );
}
