import { describe, expect, it } from "vitest";

import {
  formatDuration,
  isStaleResponse,
  remainingSeconds,
  toIfMatch,
} from "@/features/delivery/lib/runtime-helpers";

describe("stale-write helpers", () => {
  it("normalizes an ETag to the quoted If-Match form", () => {
    expect(toIfMatch("resp-123-456")).toBe('"resp-123-456"');
    expect(toIfMatch('"resp-123-456"')).toBe('"resp-123-456"');
    expect(toIfMatch(null)).toBeUndefined();
    expect(toIfMatch("")).toBeUndefined();
  });

  it("detects the STALE_RESPONSE precondition code", () => {
    expect(isStaleResponse({ errors: [{ code: "STALE_RESPONSE" }] })).toBe(true);
    expect(isStaleResponse({ errors: [{ code: "INVALID_STATE" }] })).toBe(false);
    expect(isStaleResponse(undefined)).toBe(false);
  });
});

describe("server-authoritative timer", () => {
  it("clamps remaining time at zero and returns null when untimed", () => {
    const deadline = "2026-10-10T10:00:00Z";
    const before = Date.parse(deadline) - 30_000;
    expect(remainingSeconds(deadline, before)).toBe(30);
    expect(remainingSeconds(deadline, Date.parse(deadline) + 5_000)).toBe(0);
    expect(remainingSeconds(null, before)).toBeNull();
    expect(remainingSeconds("not-a-date", before)).toBeNull();
  });

  it("formats durations as HH:MM:SS", () => {
    expect(formatDuration(3661)).toBe("01:01:01");
    expect(formatDuration(-5)).toBe("00:00:00");
  });
});
