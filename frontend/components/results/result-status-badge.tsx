"use client";

/**
 * Result status badge ({@code specs/15-P6-Evaluation-V1.5.md §7, §39}).
 *
 * <p>Every frozen status has a human label. An unknown value is shown as "Unknown" rather than
 * being passed through, so a new backend state cannot masquerade as PUBLISHED in the UI.
 */
const LABELS: Record<string, string> = {
  PUBLISHED: "Published",
  READY_FOR_PUBLICATION: "Ready for publication",
  CALCULATED: "Calculated",
  SUPERSEDED: "Superseded",
};

const STYLES: Record<string, string> = {
  PUBLISHED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  READY_FOR_PUBLICATION: "border-amber-200 bg-amber-50 text-amber-800",
  CALCULATED: "border-slate-200 bg-slate-100 text-slate-600",
  SUPERSEDED: "border-slate-200 bg-slate-100 text-slate-600",
};

export function ResultStatusBadge({ status }: { status: string }) {
  const key = (status ?? "").toUpperCase();
  return (
    <span
      data-status={key || "UNKNOWN"}
      className={`rounded-full border px-2 py-0.5 text-xs font-medium ${
        STYLES[key] ?? "border-slate-200 bg-slate-100 text-slate-600"
      }`}
    >
      {LABELS[key] ?? "Unknown"}
    </span>
  );
}
