/**
 * Display helpers for analytics values ({@code specs/17-P8-Analytics-V1.5.md §144}).
 *
 * <p>A missing metric is rendered as "Not available" by the caller, never as zero. A percentage is
 * always shown with the unit and with a description of what it measures.
 */
import { ApiClientError } from "@/lib/errors";

const dateTimeFormatter = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
});

const dateFormatter = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium" });

export function formatTimestamp(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : dateTimeFormatter.format(parsed);
}

export function formatDate(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : dateFormatter.format(parsed);
}

export function formatDecimal(value: number | null | undefined, digits = 1): string | null {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return null;
  }
  return value.toFixed(digits);
}

export function formatInteger(value: number | null | undefined): string | null {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return null;
  }
  return String(Math.trunc(value));
}

export function formatPercent(value: number | null | undefined, digits = 1): string | null {
  const formatted = formatDecimal(value, digits);
  return formatted === null ? null : `${formatted}%`;
}

/** Formats a value with its unit: {@code 72.4%}, {@code 45.0 s}, {@code 12.50 marks}. */
export function formatValueWithUnit(
  value: number | null | undefined,
  unit: string,
  digits = 1,
): string | null {
  const formatted = formatDecimal(value, digits);
  if (formatted === null) {
    return null;
  }
  if (!unit) {
    return formatted;
  }
  return unit === "%" ? `${formatted}%` : `${formatted} ${unit}`;
}

/** A short, stable label for a UUID identifier that the read model does not name. */
export function shortId(value: string | null | undefined): string {
  if (!value) {
    return "Unknown";
  }
  return value.length > 8 ? value.slice(0, 8) : value;
}

/** A safe, user-facing message. Internal details are never displayed ({@code §147}). */
export function describeAnalyticsError(
  error: unknown,
  fallback = "Analytics could not be loaded.",
): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }
  return fallback;
}
