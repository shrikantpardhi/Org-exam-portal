/**
 * Schedule time conversion ({@code specs/13-P4-Assessment-V1.5.md §109}).
 *
 * <p>A {@code datetime-local} input has no zone, so it is interpreted in the operator's local zone
 * and converted to an ISO-8601 instant for the API, which accepts and validates an {@code Instant}.
 * The backend remains authoritative for the final values; this only prepares a well-formed request.
 */

/** Converts a `datetime-local` value (for example `2026-10-22T09:30`) to an ISO-8601 instant. */
export function localDateTimeToIso(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error("Invalid date/time");
  }
  return date.toISOString();
}

/** Converts an ISO instant back into a `datetime-local` value in the local zone. */
export function isoToLocalDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const pad = (part: number): string => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export interface ScheduleWindowInput {
  startTime: string;
  endTime: string;
}

export interface ScheduleWindowResult {
  ok: boolean;
  error?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Validates and converts a schedule window before the request is made.
 *
 * <p>An end before or equal to the start is rejected locally so the operator sees the problem
 * immediately; the backend still validates the final values.
 */
export function buildScheduleWindow(input: ScheduleWindowInput): ScheduleWindowResult {
  if (input.startTime.trim().length === 0 || input.endTime.trim().length === 0) {
    return { ok: false, error: "Both start time and end time are required." };
  }

  const start = new Date(input.startTime);
  const end = new Date(input.endTime);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return { ok: false, error: "Enter a valid start and end time." };
  }
  if (end.getTime() <= start.getTime()) {
    return { ok: false, error: "End time must be after the start time." };
  }

  return {
    ok: true,
    startTime: start.toISOString(),
    endTime: end.toISOString(),
  };
}
