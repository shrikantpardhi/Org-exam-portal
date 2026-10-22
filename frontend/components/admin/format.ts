/**
 * Display helpers for administration ({@code specs/20-P11-Administration-V1.5.md §69, §70}).
 */
import { ApiClientError } from "@/lib/errors";

const dateTimeFormatter = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
});

export function formatDateTime(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : dateTimeFormatter.format(parsed);
}

export function shortId(value: string | null | undefined): string {
  if (!value) {
    return "Unknown";
  }
  return value.length > 8 ? value.slice(0, 8) : value;
}

export function statusLabel(value: string | null | undefined): string {
  switch ((value ?? "").toUpperCase()) {
    case "ACTIVE":
      return "Active";
    case "INACTIVE":
      return "Inactive";
    case "ARCHIVED":
      return "Archived";
    case "UP":
      return "Up";
    case "DOWN":
      return "Down";
    case "DEGRADED":
      return "Degraded";
    case "UNKNOWN":
      return "Unknown";
    default:
      return value ?? "Unknown";
  }
}

/** A safe message plus the request id when the API supplied one ({@code §70}). */
export function describeAdminError(
  error: unknown,
  fallback = "The administration request could not be completed.",
): string {
  if (error instanceof ApiClientError) {
    return error.requestId ? `${error.message} (request ${error.requestId})` : error.message;
  }
  return fallback;
}
