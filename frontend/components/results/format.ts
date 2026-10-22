/**
 * Display helpers for results ({@code specs/15-P6-Evaluation-V1.5.md §32, §95}).
 *
 * <p>A missing value is shown as "Not available"; it is never replaced by zero. Errors never leak
 * transport or backend internals, and a 404 for a result is presented as "not available" because
 * the backend uses the same response for another student's result and an unpublished one.
 */
import { ApiClientError } from "@/lib/errors";

const dateTimeFormatter = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
});

export function formatTimestamp(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : dateTimeFormatter.format(parsed);
}

export function formatDecimal(value: number | null | undefined, digits = 2): string | null {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return null;
  }
  return value.toFixed(digits);
}

export function formatPercent(value: number | null | undefined, digits = 2): string | null {
  const formatted = formatDecimal(value, digits);
  return formatted === null ? null : `${formatted}%`;
}

export function shortId(value: string | null | undefined): string {
  if (!value) {
    return "Unknown";
  }
  return value.length > 8 ? value.slice(0, 8) : value;
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiClientError && error.status === 404;
}

/** A safe message for a result read; ownership failures are indistinguishable from absence. */
export function describeResultError(
  error: unknown,
  fallback = "This result could not be loaded.",
): string {
  if (error instanceof ApiClientError) {
    if (error.status === 404 || error.status === 403) {
      return "This result is not available. Only your own published results can be viewed.";
    }
    return error.message;
  }
  return fallback;
}
