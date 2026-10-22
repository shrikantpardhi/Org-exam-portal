"use client";

/** The unread count pill; it renders nothing at zero. */
export function UnreadBadge({ count }: { count: number }) {
  if (!Number.isFinite(count) || count <= 0) {
    return null;
  }
  return (
    <span
      aria-label={`${count} unread notifications`}
      className="absolute -right-1 -top-1 rounded-full bg-red-600 px-1.5 py-0.5 text-xs font-semibold text-white"
    >
      {count > 99 ? "99+" : count}
    </span>
  );
}
