"use client";

import { useState } from "react";

import {
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import { MarkAllReadAction } from "@/components/communication/mark-all-read-action";
import { NotificationFilters } from "@/components/communication/notification-filters";
import { NotificationList } from "@/components/communication/notification-list";
import { describeCommunicationError } from "@/components/communication/format";
import {
  useMarkAllRead,
  useMarkRead,
  useNotifications,
  useUnreadCount,
} from "@/features/communication/hooks/use-communication";
import type { NotificationFilter } from "@/features/communication/query-keys";

/**
 * The student notification inbox ({@code specs/19-P10-Communication-V1.5.md §104-§107}).
 *
 * <p>The list is self-scoped by the backend. Marking read is optimistic and rolls back on failure.
 */
export function NotificationInbox() {
  const [filter, setFilter] = useState<NotificationFilter>("ALL");
  const list = useNotifications(filter);
  const unread = useUnreadCount();
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();

  return (
    <section aria-labelledby="notification-inbox-heading" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="notification-inbox-heading" className="text-lg font-semibold">
          Inbox
        </h2>
        <div className="flex flex-wrap items-center gap-3">
          <NotificationFilters value={filter} onChange={setFilter} />
          <MarkAllReadAction
            unreadCount={unread.data ?? 0}
            onMarkAll={() => markAll.mutate()}
            pending={markAll.isPending}
          />
        </div>
      </div>

      <p className="text-sm text-slate-500">
        {typeof unread.data === "number" ? `${unread.data} unread` : "Unread count unavailable"}
      </p>

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
    </section>
  );
}
