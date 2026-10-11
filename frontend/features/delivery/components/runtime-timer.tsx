"use client";

import { useEffect, useState } from "react";

import { formatDuration, remainingSeconds } from "@/features/delivery/lib/runtime-helpers";

/**
 * Server-authoritative countdown ({@code specs/14-P5-Delivery-V1.5.md §54-§56, §132}).
 *
 * <p>The deadline comes from the server; the local clock only animates the display. When a server
 * response indicates less time remains, the caller passes a new deadline and the UI resynchronizes.
 */
export function RuntimeTimer({ deadline, serverTime }: { deadline: string | null; serverTime: string }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const remaining = remainingSeconds(deadline, now);
  const label = remaining === null ? "Untimed" : formatDuration(remaining);
  const expired = remaining !== null && remaining <= 0;

  return (
    <div
      role="timer"
      aria-live="polite"
      title={`Server time ${serverTime}`}
      className={expired ? "font-mono text-lg text-red-600" : "font-mono text-lg"}
    >
      {label}
    </div>
  );
}
