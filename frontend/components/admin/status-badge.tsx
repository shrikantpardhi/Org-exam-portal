"use client";

import { statusLabel } from "./format";

/** A lifecycle or health status pill ({@code specs/20-P11-Administration-V1.5.md §129}). */
const TONES: Record<string, string> = {
  ACTIVE: "border-success bg-success-subtle text-success",
  INACTIVE: "border-border bg-muted text-muted-foreground",
  ARCHIVED: "border-border bg-muted text-muted-foreground",
  UP: "border-success bg-success-subtle text-success",
  DOWN: "border-destructive bg-destructive-subtle text-destructive",
  DEGRADED: "border-warning bg-warning-subtle text-warning",
  UNKNOWN: "border-border bg-muted text-muted-foreground",
};

export function StatusBadge({ status }: { status: string }) {
  const key = (status ?? "").toUpperCase();
  return (
    <span
      data-status={key || "UNKNOWN"}
      className={`inline-block rounded-full border px-2 py-0.5 text-xs font-medium ${
        TONES[key] ?? "border-border bg-muted text-muted-foreground"
      }`}
    >
      {statusLabel(status)}
    </span>
  );
}
