"use client";

import { statusLabel } from "./format";

/** A lifecycle or health status pill ({@code specs/20-P11-Administration-V1.5.md §129}). */
const TONES: Record<string, string> = {
  ACTIVE: "border-emerald-200 bg-emerald-50 text-emerald-700",
  INACTIVE: "border-slate-200 bg-slate-100 text-slate-600",
  ARCHIVED: "border-slate-200 bg-slate-100 text-slate-600",
  UP: "border-emerald-200 bg-emerald-50 text-emerald-700",
  DOWN: "border-red-200 bg-red-50 text-red-700",
  DEGRADED: "border-amber-200 bg-amber-50 text-amber-800",
  UNKNOWN: "border-slate-200 bg-slate-100 text-slate-600",
};

export function StatusBadge({ status }: { status: string }) {
  const key = (status ?? "").toUpperCase();
  return (
    <span
      data-status={key || "UNKNOWN"}
      className={`inline-block rounded-full border px-2 py-0.5 text-xs font-medium ${
        TONES[key] ?? "border-slate-200 bg-slate-100 text-slate-600"
      }`}
    >
      {statusLabel(status)}
    </span>
  );
}
