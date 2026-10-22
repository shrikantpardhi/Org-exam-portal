"use client";

import type { Notification } from "@/features/communication/schemas/communication";

import { CommunicationEmpty } from "./communication-states";
import { NotificationItem } from "./notification-item";

/** A notification list ({@code specs/19-P10-Communication-V1.5.md §105}). */
export function NotificationList({
  notifications,
  onMarkRead,
  pendingId,
  emptyMessage = "No notifications yet.",
}: {
  notifications: Notification[];
  onMarkRead: (notificationId: string) => void;
  pendingId?: string | null;
  emptyMessage?: string;
}) {
  if (notifications.length === 0) {
    return <CommunicationEmpty message={emptyMessage} />;
  }

  return (
    <ul className="space-y-2">
      {notifications.map((notification) => (
        <NotificationItem
          key={notification.id}
          notification={notification}
          onMarkRead={onMarkRead}
          pending={pendingId === notification.id}
        />
      ))}
    </ul>
  );
}
