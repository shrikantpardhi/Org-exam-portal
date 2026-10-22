"use client";

import { useId, useState } from "react";

import {
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import { MarkAllReadAction } from "@/components/communication/mark-all-read-action";
import { NotificationBell } from "@/components/communication/notification-bell";
import { NotificationFilters } from "@/components/communication/notification-filters";
import { NotificationList } from "@/components/communication/notification-list";
import { NotificationPopover } from "@/components/communication/notification-popover";
import { describeCommunicationError } from "@/components/communication/format";
import {
  useMarkAllRead,
  useMarkRead,
  useNotifications,
  useUnreadCount,
} from "@/features/communication/hooks/use-communication";
import type { NotificationFilter } from "@/features/communication/query-keys";

/** The bell and its popover ({@code specs/19-P10-Communication-V1.5.md §105-§107}). */
export function NotificationCenter() {
  const popoverId = useId();
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState<NotificationFilter>("ALL");
  const list = useNotifications(filter);
  const unread = useUnreadCount();
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();

  return (
    <div className="relative inline-block">
      <NotificationBell
        unreadCount={unread.data ?? 0}
        open={open}
        onToggle={() => setOpen((value) => !value)}
        controlsId={popoverId}
      />
      <NotificationPopover id={popoverId} open={open} onClose={() => setOpen(false)}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <NotificationFilters value={filter} onChange={setFilter} />
          <MarkAllReadAction
            unreadCount={unread.data ?? 0}
            onMarkAll={() => markAll.mutate()}
            pending={markAll.isPending}
          />
        </div>

        {list.isPending ? <CommunicationLoading label="Loading notifications…" /> : null}
        {list.isError ? (
          <CommunicationError
            message={describeCommunicationError(list.error, "Notifications could not be loaded.")}
            onRetry={() => {
              void list.refetch();
            }}
          />
        ) : null}
        {list.data ? (
          <NotificationList
            notifications={list.data.items}
            onMarkRead={(notificationId) => markRead.mutate(notificationId)}
            pendingId={markRead.isPending ? markRead.variables ?? null : null}
            emptyMessage={
              filter === "UNREAD" ? "No unread notifications." : "No notifications yet."
            }
          />
        ) : null}
      </NotificationPopover>
    </div>
  );
}
