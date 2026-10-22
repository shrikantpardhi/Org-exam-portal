/**
 * Display helpers for commerce ({@code specs/18-P9-Commerce-V1.5.md §46-§51, §78}).
 *
 * <p>Amounts are formatted, never calculated. A missing amount is "Not available"; a missing
 * currency simply omits the suffix rather than inventing one.
 */
import { ApiClientError } from "@/lib/errors";

const amountFormatter = new Intl.NumberFormat("en-IN", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const dateTimeFormatter = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
});

const dateFormatter = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium" });

export function formatAmount(value: number | null | undefined, currency?: string | null): string | null {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return null;
  }
  const formatted = amountFormatter.format(value);
  return currency ? `${formatted} ${currency}` : formatted;
}

export function formatDateTime(value: string | null | undefined): string | null {
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

export function shortId(value: string | null | undefined): string {
  if (!value) {
    return "Unknown";
  }
  return value.length > 8 ? value.slice(0, 8) : value;
}

const PAYMENT_LABELS: Record<string, string> = {
  PENDING: "Payment pending",
  SUCCESS: "Payment successful",
  FAILED: "Payment failed",
  REFUNDED: "Refunded",
  UNKNOWN: "Unknown payment status",
};

export function paymentStatusLabel(status: string | null | undefined): string {
  return PAYMENT_LABELS[(status ?? "").toUpperCase()] ?? "Unknown payment status";
}

const LIFECYCLE_LABELS: Record<string, string> = {
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  ARCHIVED: "Archived",
};

export function lifecycleLabel(status: string | null | undefined): string {
  return LIFECYCLE_LABELS[(status ?? "").toUpperCase()] ?? "Unknown status";
}

/** True when the backend hid an object the caller may not see ({@code §67, §68}). */
export function isNotFound(error: unknown): boolean {
  return error instanceof ApiClientError && error.status === 404;
}

/** A safe message for a commerce failure; provider internals are never surfaced. */
export function describeCommerceError(
  error: unknown,
  fallback = "The commerce request could not be completed.",
): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }
  return fallback;
}
