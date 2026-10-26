"use client";

/** Marks every notification read ({@code specs/19-P10-Communication-V1.5.md §107}). */
export function MarkAllReadAction({
  unreadCount,
  onMarkAll,
  pending = false,
}: {
  unreadCount: number;
  onMarkAll: () => void;
  pending?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onMarkAll}
      disabled={unreadCount === 0 || pending}
      className="rounded border border-input bg-surface px-3 py-1 text-sm font-medium text-foreground disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
    >
      {pending ? "Marking…" : `Mark all read${unreadCount > 0 ? ` (${unreadCount})` : ""}`}
    </button>
  );
}
