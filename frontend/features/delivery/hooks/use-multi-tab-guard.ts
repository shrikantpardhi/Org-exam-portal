"use client";

import { useEffect, useRef, useState } from "react";

import {
  HEARTBEAT_INTERVAL_MS,
  OWNER_STALE_MS,
  TabLock,
  createTabId,
  type OwnershipState,
} from "@/features/delivery/lib/tab-lock";

/**
 * Makes one tab the owner of an attempt ({@code specs/14-P5-Delivery-V1.5.md §77, §123}).
 *
 * <p>Claims ownership on mount, refreshes a heartbeat while mounted, and stops the owning tab's claim
 * when it unmounts. A tab that loses ownership (another tab claimed it after this one went quiet)
 * reports {@code blocked} so the runtime can switch itself to read-only instead of racing the new
 * owner.
 *
 * <p>Storage failure resolves to {@code unavailable}, which the runtime treats as "run normally":
 * an examination must not be blocked because `localStorage` is disabled. The guard is a safety net
 * against accidental double-editing, not an authorization control — the server re-checks everything.
 */
export function useMultiTabGuard(attemptId: string): { ownership: OwnershipState; tabId: string } {
  const [ownership, setOwnership] = useState<OwnershipState>("owner");
  const [tabId] = useState(() => createTabId());
  const lockRef = useRef<TabLock | null>(null);

  useEffect(() => {
    if (!attemptId) {
      return;
    }
    // `localStorage` throws in some privacy modes; treat that as unavailable rather than fatal.
    let storage: Storage | null = null;
    try {
      storage = window.localStorage;
      // A trivial write confirms the store is actually usable, not merely present.
      storage.setItem("exam:probe", "1");
      storage.removeItem("exam:probe");
    } catch {
      storage = null;
    }

    const lock = new TabLock(attemptId, tabId, storage);
    lockRef.current = lock;
    setOwnership(lock.claim());

    const heartbeat = window.setInterval(() => {
      const next = lock.heartbeat();
      // Only surface a change; a steady owner should not re-render every few seconds.
      setOwnership((previous) => (previous === next ? previous : next));
    }, HEARTBEAT_INTERVAL_MS);

    // `pagehide` rather than `beforeunload`: it fires on tab close, navigation and mobile backgrounding,
    // and does not require a dialog the student would have to dismiss.
    const release = () => lock.release();
    window.addEventListener("pagehide", release);

    return () => {
      window.clearInterval(heartbeat);
      window.removeEventListener("pagehide", release);
      lock.release();
      lockRef.current = null;
    };
  }, [attemptId, tabId]);

  return { ownership, tabId };
}

/** Re-exported so a caller can reason about staleness without importing the lib directly. */
export { OWNER_STALE_MS };
