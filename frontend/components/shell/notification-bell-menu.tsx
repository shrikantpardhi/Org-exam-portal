"use client";

import { useState } from "react";

import { NotificationBell } from "@/components/communication/notification-bell";
import { NotificationPopover } from "@/components/communication/notification-popover";
import { useNotifications, useUnreadCount } from "@/features/communication/hooks/use-communication";

/** Header bell + popover ({@code docs/frontend/navigation.md} §45). */
export function NotificationBellMenu() {
 const [open, setOpen] = useState(false);
 const { data: unreadCount } = useUnreadCount();
 const { data } = useNotifications("UNREAD");
  const notifications = data?.items ?? [];

  return (
    <div className="relative">
      <NotificationBell
        unreadCount={unreadCount ?? 0}
        open={open}
        onToggle={() => setOpen((value) => !value)}
 controlsId="header-notifications"
      />
      <NotificationPopover id="header-notifications" open={open} onClose={() => setOpen(false)}>
        {notifications.length === 0 ? (
          <p className="text-sm text-muted-foreground">No unread notifications.</p>
        ) : (
          <ul className="space-y-2">
            {notifications.map((notification) => (
              <li key={notification.id} className="rounded border border-border p-2">
                <p className="text-sm font-medium text-foreground">{notification.title}</p>
                {notification.message ? (
                  <p className="text-xs text-muted-foreground">{notification.message}</p>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </NotificationPopover>
    </div>
  );
}
