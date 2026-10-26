"use client";

/** Marks one notification read ({@code specs/19-P10-Communication-V1.5.md §107}). */
export function MarkReadAction({
  notificationId,
  title,
  onMarkRead,
  pending = false,
}: {
  notificationId: string;
  title: string;
  onMarkRead: (notificationId: string) => void;
  pending?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={() => onMarkRead(notificationId)}
      disabled={pending}
      aria-label={`Mark "${title}" as read`}
      className="rounded text-sm font-medium text-info underline disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
    >
      {pending ? "Marking…" : "Mark read"}
    </button>
  );
}
