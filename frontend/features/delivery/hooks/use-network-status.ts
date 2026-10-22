"use client";

import { useEffect, useState } from "react";

/**
 * The browser's connectivity state ({@code specs/14-P5-Delivery-V1.5.md §78, §130}).
 *
 * <p>Used only to decide whether to show the non-blocking offline indicator and whether to attempt an
 * immediate flush. It is never the authority on whether a save succeeded: a save is persisted only
 * when the server acknowledges it, and {@code navigator.onLine} is famously optimistic (a captive
 * portal reports online while every request fails).
 *
 * <p>The initial value is {@code true} during server rendering and the first client paint, because
 * reading {@code navigator} during render would break hydration. The real value arrives in the effect.
 */
export function useNetworkStatus(): { online: boolean } {
  const [online, setOnline] = useState(true);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    setOnline(window.navigator.onLine);
    const handleOnline = () => setOnline(true);
    const handleOffline = () => setOnline(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  return { online };
}
