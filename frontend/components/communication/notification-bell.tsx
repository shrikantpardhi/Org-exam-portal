"use client";

import { Bell } from "lucide-react";

import { UnreadBadge } from "./unread-badge";

/**
 * The notification bell ({@code specs/19-P10-Communication-V1.5.md §105}).
 *
 * <p>The bell is a disclosure button: it owns no data and changes no state beyond opening the
 * popover, so it can sit in any authenticated header.
 */
export function NotificationBell({
  unreadCount,
  open,
  onToggle,
  controlsId,
}: {
  unreadCount: number;
  open: boolean;
  onToggle: () => void;
  controlsId?: string;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      aria-controls={controlsId}
      aria-label="Notifications"
      className="relative rounded-full border border-input bg-surface p-2 hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
    >
      <Bell aria-hidden="true" className="h-5 w-5 text-foreground" />
      <UnreadBadge count={unreadCount} />
    </button>
  );
}
