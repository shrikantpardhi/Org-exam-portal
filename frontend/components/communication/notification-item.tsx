"use client";

import type { Notification } from "@/features/communication/schemas/communication";

import { formatDateTime } from "./format";
import { MarkReadAction } from "./mark-read-action";

/** One notification ({@code specs/19-P10-Communication-V1.5.md §105, §107}). */
export function NotificationItem({
  notification,
  onMarkRead,
  pending = false,
}: {
  notification: Notification;
  onMarkRead: (notificationId: string) => void;
  pending?: boolean;
}) {
  const unread = notification.notificationStatus === "UNREAD";
  const created = formatDateTime(notification.createdOn);

  return (
    <li
      data-notification-status={notification.notificationStatus}
      className={`space-y-1 rounded border p-3 ${
        unread ? "border-blue-200 bg-blue-50" : "border-slate-200 bg-white"
      }`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="text-sm font-semibold text-slate-900">
          {notification.title}
          {unread ? <span className="sr-only"> (unread)</span> : null}
        </p>
        <span
          className={`rounded-full border px-2 py-0.5 text-xs font-medium ${
            unread
              ? "border-blue-200 bg-white text-blue-700"
              : "border-slate-200 bg-slate-100 text-slate-600"
          }`}
        >
          {unread ? "Unread" : "Read"}
        </span>
      </div>
      <p className="text-sm text-slate-600">{notification.message ?? "No message."}</p>
      <div className="flex flex-wrap items-center justify-between gap-2">
        {created ? (
          <time dateTime={notification.createdOn ?? undefined} className="text-xs text-slate-500">
            {created}
          </time>
        ) : (
          <span className="text-xs text-slate-500">No timestamp</span>
        )}
        {unread ? (
          <MarkReadAction
            notificationId={notification.id}
            title={notification.title}
            onMarkRead={onMarkRead}
            pending={pending}
          />
        ) : null}
      </div>
    </li>
  );
}
