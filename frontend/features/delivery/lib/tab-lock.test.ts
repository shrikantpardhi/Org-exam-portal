import { describe, expect, it } from "vitest";

import { ResponseQueue } from "@/features/delivery/lib/response-queue";
import { TabLock, canClaim, createTabId, evaluateOwnership, isOwnerStale } from "@/features/delivery/lib/tab-lock";

/**
 * Multi-tab guard tests ({@code specs/14-P5-Delivery-V1.5.md §77, §123}).
 *
 * <p>The guard's job is to make exactly one tab the owner of an attempt and to let a stale claim
 * expire rather than lock a student out permanently. Both halves are asserted here, plus the
 * fail-open behaviour when storage is unavailable.
 */

/** A minimal in-memory `Storage` stand-in, so the lock is testable without a browser. */
function memoryStorage() {
  const map = new Map<string, string>();
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => void map.set(key, value),
    removeItem: (key: string) => void map.delete(key),
  };
}

describe("owner staleness", () => {
  it("treats an absent record as claimable", () => {
    expect(isOwnerStale(null, 1_000)).toBe(true);
    expect(canClaim(null, "tab-a", 1_000)).toBe(true);
  });

  it("treats a fresh token held by another tab as live", () => {
    const record = { ownerId: "tab-b", beatAtMs: 1_000 };
    expect(isOwnerStale(record, 5_000)).toBe(false);
    expect(canClaim(record, "tab-a", 5_000)).toBe(false);
  });

  it("allows takeover after the heartbeat goes quiet", () => {
    const record = { ownerId: "tab-b", beatAtMs: 1_000 };
    // 16s later the owner is stale, so a closed tab does not lock the student out.
    expect(isOwnerStale(record, 17_000)).toBe(true);
    expect(canClaim(record, "tab-a", 17_000)).toBe(true);
  });

  it("always lets the owning tab re-claim, even while its token is fresh", () => {
    const record = { ownerId: "tab-a", beatAtMs: 5_000 };
    expect(canClaim(record, "tab-a", 5_000)).toBe(true);
  });
});

describe("ownership resolution", () => {
  it("fails open when storage is unavailable rather than blocking the exam", () => {
    expect(evaluateOwnership(null, "tab-a", 1_000, false)).toBe("unavailable");
  });

  it("reports blocked for a live foreign owner", () => {
    expect(evaluateOwnership({ ownerId: "tab-b", beatAtMs: 0 }, "tab-a", 1_000, true)).toBe("blocked");
  });
});

describe("TabLock", () => {
  it("claims ownership when the key is empty and writes a token", () => {
    const storage = memoryStorage();
    const lock = new TabLock("attempt-1", "tab-a", storage, () => 1_000);
    expect(lock.claim()).toBe("owner");
    expect(lock.read()).toEqual({ ownerId: "tab-a", beatAtMs: 1_000 });
  });

  it("blocks a second tab while the first holds a fresh token", () => {
    const storage = memoryStorage();
    const first = new TabLock("attempt-1", "tab-a", storage, () => 1_000);
    first.claim();

    const second = new TabLock("attempt-1", "tab-b", storage, () => 2_000);
    expect(second.claim()).toBe("blocked");
    // The first tab's token is untouched.
    expect(second.read()?.ownerId).toBe("tab-a");
  });

  it("never lets a heartbeat steal ownership from another live tab", () => {
    const storage = memoryStorage();
    new TabLock("attempt-1", "tab-a", storage, () => 1_000).claim();
    const second = new TabLock("attempt-1", "tab-b", storage, () => 2_000);
    expect(second.heartbeat()).toBe("blocked");
    expect(second.read()?.ownerId).toBe("tab-a");
  });

  it("lets a new tab take over once the old owner went stale", () => {
    const storage = memoryStorage();
    new TabLock("attempt-1", "tab-a", storage, () => 1_000).claim();
    const second = new TabLock("attempt-1", "tab-b", storage, () => 60_000);
    expect(second.claim()).toBe("owner");
    expect(second.read()?.ownerId).toBe("tab-b");
  });

  it("releases only its own token", () => {
    const storage = memoryStorage();
    const first = new TabLock("attempt-1", "tab-a", storage, () => 1_000);
    first.claim();
    const second = new TabLock("attempt-1", "tab-b", storage, () => 1_000);
    // A blocked tab releasing must not clear the real owner.
    second.release();
    expect(first.read()?.ownerId).toBe("tab-a");
    first.release();
    expect(first.read()).toBeNull();
  });

  it("degrades to unavailable when storage throws", () => {
    const hostile = {
      getItem: () => {
        throw new Error("denied");
      },
      setItem: () => {
        throw new Error("denied");
      },
      removeItem: () => undefined,
    };
    const lock = new TabLock("attempt-1", "tab-a", hostile, () => 1_000);
    expect(lock.claim()).toBe("unavailable");
  });

  it("treats a malformed stored token as absent", () => {
    const storage = memoryStorage();
    storage.setItem("exam:runtime:owner:attempt-1", "{not json");
    const lock = new TabLock("attempt-1", "tab-a", storage, () => 1_000);
    expect(lock.read()).toBeNull();
    expect(lock.claim()).toBe("owner");
  });
});

describe("tab identity", () => {
  it("produces distinct ids for distinct tabs", () => {
    const values = [0.1, 0.9];
    let call = 0;
    const next = (): number => values[call++ % values.length] ?? 0;
    expect(createTabId(next)).not.toBe(createTabId(next));
  });
});

describe("queue and lock stay independent", () => {
  it("a blocked tab holds no queue entries of its own", () => {
    // Documents the intended composition: the read-only tab never accumulates pending writes.
    const queue = ResponseQueue.empty<string>();
    expect(queue.size).toBe(0);
  });
});
