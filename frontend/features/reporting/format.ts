/**
 * Display helpers for reporting ({@code specs/22-P13-Reporting-V1.5.md §28, §109-§112}).
 *
 * <p>Statuses are rendered through one controlled map so an unrecognized server value is shown as
 * "Unknown" and never as a successful state.
 */
import { ApiClientError } from "@/lib/errors";

const dateTimeFormatter = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
});

const STATUS_LABELS: Record<string, string> = {
  QUEUED: "Queued",
  RUNNING: "Running",
  COMPLETED: "Completed",
  COMPLETED_WITH_ERRORS: "Completed with errors",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
  UNKNOWN: "Unknown",
};

export function reportStatusLabel(value: string | null | undefined): string {
  const key = (value ?? "").toUpperCase();
  return STATUS_LABELS[key] ?? "Unknown";
}

/** The tone classes for a job status pill. Unknown is neutral, never green. */
export function reportStatusTone(value: string | null | undefined): string {
  switch ((value ?? "").toUpperCase()) {
    case "COMPLETED":
      return "border-success bg-success-subtle text-success";
    case "RUNNING":
      return "border-info bg-info-subtle text-info";
    case "QUEUED":
      return "border-warning bg-warning-subtle text-warning";
    case "COMPLETED_WITH_ERRORS":
      return "border-warning bg-warning-subtle text-warning";
    case "FAILED":
      return "border-destructive bg-destructive-subtle text-destructive";
    case "CANCELLED":
      return "border-slate-200 bg-slate-100 text-slate-600";
    default:
      return "border-slate-200 bg-slate-100 text-slate-600";
  }
}

export function formatReportDateTime(value: string | null | undefined): string {
  if (!value) {
    return "Not available";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "Not available" : dateTimeFormatter.format(parsed);
}

/** A human label for a filter key the catalog advertises ({@code §11}). */
export function filterLabel(key: string): string {
  switch (key) {
    case "from":
      return "From";
    case "to":
      return "To";
    case "query":
      return "Search";
    case "entityName":
      return "Entity";
    case "action":
      return "Action";
    default:
      // "assessmentId" -> "Assessment id", "studentId" -> "Student id".
      if (key.endsWith("Id")) {
        const base = key.slice(0, -2).replace(/([a-z0-9])([A-Z])/g, "$1 $2").toLowerCase();
        return `${base.charAt(0).toUpperCase()}${base.slice(1)} id`;
      }
      return key.charAt(0).toUpperCase() + key.slice(1);
  }
}

/** A safe, correlation-aware error message ({@code §28}: no stack traces). */
export function describeReportError(
  error: unknown,
  fallback = "The reporting request could not be completed.",
): string {
  if (error instanceof ApiClientError) {
    return error.requestId ? `${error.message} (request ${error.requestId})` : error.message;
  }
  return fallback;
}
