import { describe, expect, it } from "vitest";

import {
  buildScheduleWindow,
  isoToLocalDateTime,
  localDateTimeToIso,
} from "@/features/assessment/lib/schedule-time";

/**
 * Schedule time conversion ({@code specs/13-P4-Assessment-V1.5.md §109}).
 *
 * <p>A `datetime-local` value carries no zone. It is interpreted in the operator's local zone and
 * sent as an instant; an end at or before the start is rejected before the request is made.
 */
describe("schedule time", () => {
  it("converts a local datetime input to an ISO-8601 instant", () => {
    const iso = localDateTimeToIso("2026-10-22T09:30");

    expect(iso).toBe(new Date("2026-10-22T09:30").toISOString());
    // An instant, not a zoneless local string.
    expect(iso).toMatch(/Z$/);
  });

  it("round-trips an instant back into a local input value", () => {
    const iso = localDateTimeToIso("2026-10-22T09:30");
    expect(isoToLocalDateTime(iso)).toBe("2026-10-22T09:30");
  });

  it("rejects an end before the start before the request is made", () => {
    const result = buildScheduleWindow({
      startTime: "2026-10-22T10:00",
      endTime: "2026-10-22T09:00",
    });

    expect(result.ok).toBe(false);
    expect(result.error).toBe("End time must be after the start time.");
    expect(result.startTime).toBeUndefined();
    expect(result.endTime).toBeUndefined();
  });

  it("rejects an empty or identical window", () => {
    expect(buildScheduleWindow({ startTime: "", endTime: "2026-10-22T09:00" }).ok).toBe(false);
    expect(
      buildScheduleWindow({ startTime: "2026-10-22T09:00", endTime: "2026-10-22T09:00" }).ok,
    ).toBe(false);
  });

  it("accepts a valid window and returns both instants", () => {
    const result = buildScheduleWindow({
      startTime: "2026-10-22T09:00",
      endTime: "2026-10-22T12:00",
    });

    expect(result.ok).toBe(true);
    expect(result.startTime).toBe(new Date("2026-10-22T09:00").toISOString());
    expect(result.endTime).toBe(new Date("2026-10-22T12:00").toISOString());
  });
});
