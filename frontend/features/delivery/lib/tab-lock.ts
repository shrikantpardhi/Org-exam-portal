/**
 * The single-owner tab lock ({@code specs/14-P5-Delivery-V1.5.md §77, §123, §124}).
 *
 * <p>Two tabs editing one attempt is a real integrity hazard: each holds its own ETag and its own
 * in-memory answers, so tab B's save silently discards tab A's work and one of them will submit a
 * state the student never saw. The guard therefore makes one tab the *owner* of the attempt and turns
 * every other tab read-only with an explanation, rather than attempting a cross-tab merge the
 * contract does not define.
 *
 * <p>Ownership is claimed by writing a timestamped token under a per-attempt key. A tab whose token is
 * replaced stops being the owner the next time it checks. A heartbeat keeps the token fresh; a token
 * older than {@link OWNER_STALE_MS} is treated as abandoned so closing a laptop lid does not lock the
 * student out of their own attempt.
 *
 * <p>The policy decisions live in pure functions ({@link canClaim}, {@link isOwnerStale},
 * {@link evaluateOwnership}) so they are testable without a browser. {@link TabLock} is the thin
 * storage adapter, and it degrades to "I am the owner" when storage is unavailable, because refusing
 * to run an examination over an unavailable `localStorage` would be worse than the hazard it guards.
 */

/** How long an unmet heartbeat may go before the owner is considered gone. */
export const OWNER_STALE_MS = 15_000;
/** How often the owning tab refreshes its token. */
export const HEARTBEAT_INTERVAL_MS = 5_000;

export interface OwnerRecord {
  /** A per-tab random id, so a tab can tell its own token from another tab's. */
  readonly ownerId: string;
  /** Epoch milliseconds of the last heartbeat. */
  readonly beatAtMs: number;
}

/** Whether an existing owner record may be taken over: absent, or silent for too long. */
export function isOwnerStale(record: OwnerRecord | null, nowMs: number, staleMs = OWNER_STALE_MS): boolean {
  if (!record) {
    return true;
  }
  if (!Number.isFinite(record.beatAtMs)) {
    return true;
  }
  return nowMs - record.beatAtMs > staleMs;
}

/**
 * Whether this tab may become the owner: always when there is no live owner, and also when the live
 * owner is this same tab re-claiming after a refresh.
 */
export function canClaim(
  record: OwnerRecord | null,
  tabId: string,
  nowMs: number,
  staleMs = OWNER_STALE_MS,
): boolean {
  if (!record) {
    return true;
  }
  if (record.ownerId === tabId) {
    return true;
  }
  return isOwnerStale(record, nowMs, staleMs);
}

export type OwnershipState = "owner" | "blocked" | "unavailable";

/**
 * Resolves what this tab should do given the stored record.
 *
 * <p>{@code unavailable} means storage could not be read at all; the caller treats that as
 * single-tab operation rather than blocking the examination.
 */
export function evaluateOwnership(
  record: OwnerRecord | null,
  tabId: string,
  nowMs: number,
  storageAvailable = true,
  staleMs = OWNER_STALE_MS,
): OwnershipState {
  if (!storageAvailable) {
    return "unavailable";
  }
  return canClaim(record, tabId, nowMs, staleMs) ? "owner" : "blocked";
}

/** A fresh per-tab identifier. Not a security token — it only distinguishes tabs. */
export function createTabId(random: () => number = Math.random): string {
  return `tab-${Date.now().toString(36)}-${Math.floor(random() * 1e9).toString(36)}`;
}

function storageKey(attemptId: string): string {
  return `exam:runtime:owner:${attemptId}`;
}

/** Reads and writes the owner record, tolerating a hostile or absent `localStorage`. */
export class TabLock {
  private readonly key: string;

  constructor(
    private readonly attemptId: string,
    private readonly tabId: string,
    private readonly storage: Pick<Storage, "getItem" | "setItem" | "removeItem"> | null,
    private readonly now: () => number = () => Date.now(),
  ) {
    this.key = storageKey(attemptId);
  }

  /** The current record, or {@code null} when absent, malformed, or unreadable. */
  read(): OwnerRecord | null {
    if (!this.storage) {
      return null;
    }
    try {
      const raw = this.storage.getItem(this.key);
      if (!raw) {
        return null;
      }
      const parsed: unknown = JSON.parse(raw);
      if (typeof parsed !== "object" || parsed === null) {
        return null;
      }
      const { ownerId, beatAtMs } = parsed as { ownerId?: unknown; beatAtMs?: unknown };
      if (typeof ownerId !== "string" || typeof beatAtMs !== "number") {
        return null;
      }
      return { ownerId, beatAtMs };
    } catch {
      return null;
    }
  }

  /** This tab's own record, with a fresh heartbeat. */
  private ownRecord(): OwnerRecord {
    return { ownerId: this.tabId, beatAtMs: this.now() };
  }

  /**
   * Attempts to take ownership. Returns the resolved state so the caller can render the read-only
   * banner without a second read.
   */
  claim(staleMs = OWNER_STALE_MS): OwnershipState {
    if (!this.storage) {
      return "unavailable";
    }
    const record = this.read();
    const state = evaluateOwnership(record, this.tabId, this.now(), true, staleMs);
    if (state !== "owner") {
      return state;
    }
    try {
      this.storage.setItem(this.key, JSON.stringify(this.ownRecord()));
    } catch {
      return "unavailable";
    }
    return "owner";
  }

  /** Refreshes this tab's token, but never steals ownership from another live tab. */
  heartbeat(staleMs = OWNER_STALE_MS): OwnershipState {
    if (!this.storage) {
      return "unavailable";
    }
    const record = this.read();
    if (record && record.ownerId !== this.tabId && !isOwnerStale(record, this.now(), staleMs)) {
      return "blocked";
    }
    try {
      this.storage.setItem(this.key, JSON.stringify(this.ownRecord()));
      return "owner";
    } catch {
      return "unavailable";
    }
  }

  /** Releases ownership so a reload or a newly opened tab starts clean. */
  release(): void {
    if (!this.storage) {
      return;
    }
    try {
      const record = this.read();
      if (!record || record.ownerId === this.tabId) {
        this.storage.removeItem(this.key);
      }
    } catch {
      // Releasing is best-effort; a stale token expires on its own.
    }
  }
}
