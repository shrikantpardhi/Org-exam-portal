"use client";

import type { NotificationFilter } from "@/features/communication/query-keys";

/** All / Unread filter ({@code specs/19-P10-Communication-V1.5.md §105, §106}). */
export function NotificationFilters({
  value,
  onChange,
}: {
  value: NotificationFilter;
  onChange: (filter: NotificationFilter) => void;
}) {
  return (
    <div role="group" aria-label="Filter notifications" className="inline-flex rounded-md border border-input p-0.5">
      {(["ALL", "UNREAD"] as const).map((filter) => (
        <button
          key={filter}
          type="button"
          aria-pressed={value === filter}
          onClick={() => onChange(filter)}
          className={`rounded px-3 py-1 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
            value === filter ? "bg-primary text-white" : "text-foreground hover:bg-muted"
          }`}
        >
          {filter === "ALL" ? "All" : "Unread"}
        </button>
      ))}
    </div>
  );
}
