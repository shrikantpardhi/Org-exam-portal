/**
 * Stale-write and timer helpers ({@code specs/14-P5-Delivery-V1.5.md §43, §54-§56}).
 *
 * <p>Pure functions so the runtime's optimistic-concurrency and server-authoritative timer
 * behaviour is unit-testable without a browser.
 */

/** Normalizes an ETag to the quoted form required by {@code If-Match}. */
export function toIfMatch(etag: string | null | undefined): string | undefined {
  if (!etag) {
    return undefined;
  }
  const bare = etag.replace(/^"+|"+$/g, "");
  return bare.length === 0 ? undefined : `"${bare}"`;
}

/**
 * Detects the {@code STALE_RESPONSE} precondition code in a normalized API error body, so the
 * caller can re-fetch the current response, merge, and retry ({@code §43}).
 */
export function isStaleResponse(body: unknown): boolean {
  if (typeof body !== "object" || body === null) {
    return false;
  }
  const errors = (body as { errors?: Array<{ code?: string }> }).errors;
  return Array.isArray(errors) && errors.some((error) => error?.code === "STALE_RESPONSE");
}

/**
 * Remaining seconds until the server-authoritative deadline, clamped at zero. Returns {@code null}
 * when the attempt is untimed. The browser clock is a display aid only; the deadline comes from the
 * server ({@code §54}).
 */
export function remainingSeconds(deadlineIso: string | null | undefined, nowMs: number): number | null {
  if (!deadlineIso) {
    return null;
  }
  const deadline = Date.parse(deadlineIso);
  if (Number.isNaN(deadline)) {
    return null;
  }
  return Math.max(0, Math.floor((deadline - nowMs) / 1000));
}

/** Renders a duration in seconds as {@code HH:MM:SS}. */
export function formatDuration(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;
  return [hours, minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
}
