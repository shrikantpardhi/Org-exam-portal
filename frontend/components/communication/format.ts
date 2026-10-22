/**
 * Display helpers for communication ({@code specs/19-P10-Communication-V1.5.md §74, §93}).
 */
import { ApiClientError } from "@/lib/errors";

const dateTimeFormatter = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
});

const dateFormatter = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium" });

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

/** Formats an ISO timestamp for a {@code datetime-local} input, in local time. */
export function toDateTimeLocal(value: string | null | undefined): string {
  if (!value) {
    return "";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "";
  }
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())}T${pad(
    parsed.getHours(),
  )}:${pad(parsed.getMinutes())}`;
}

export function shortId(value: string | null | undefined): string {
  if (!value) {
    return "Unknown";
  }
  return value.length > 8 ? value.slice(0, 8) : value;
}

const LIFECYCLE_LABELS: Record<string, string> = {
  ACTIVE: "Active",
  INACTIVE: "Draft",
  ARCHIVED: "Archived",
};

export function lifecycleLabel(status: string | null | undefined): string {
  return LIFECYCLE_LABELS[(status ?? "").toUpperCase()] ?? "Unknown";
}

export function announcementStatusLabel(status: string | null | undefined): string {
  switch ((status ?? "").toUpperCase()) {
    case "ACTIVE":
      return "Published";
    case "INACTIVE":
      return "Draft";
    case "ARCHIVED":
      return "Archived";
    default:
      return "Unknown";
  }
}

export function describeCommunicationError(
  error: unknown,
  fallback = "The communication request could not be completed.",
): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }
  return fallback;
}
