"use client";

import { HealthCard } from "@/components/admin/health-card";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError, formatDateTime } from "@/components/admin/format";
import { useSystemHealth } from "@/features/administration/hooks/use-administration";

/** Aggregated system health ({@code specs/20-P11-Administration-V1.5.md §59, §116-§121}). */
export function SystemHealthScreen() {
  const query = useSystemHealth();

  if (query.isPending) {
    return <p role="status" className="text-sm text-slate-500">Checking system health…</p>;
  }

  if (query.isError) {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        <p>{describeAdminError(query.error, "System health could not be loaded.")}</p>
        <button
          type="button"
          onClick={() => {
            void query.refetch();
          }}
          className="mt-2 rounded border border-red-300 bg-white px-3 py-1 text-sm font-medium text-red-700"
        >
          Try again
        </button>
      </div>
    );
  }

  const health = query.data;
  if (!health) {
    return <p role="status" className="text-sm text-slate-500">Checking system health…</p>;
  }

  const checked = formatDateTime(health.checkedOn);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-medium text-slate-700">Overall</span>
        <StatusBadge status={health.overall} />
        <span className="text-xs text-slate-500">
          {checked ? `Checked ${checked}` : "Check time not reported"}
        </span>
      </div>

      {health.components.length === 0 ? (
        <p role="note" className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-700">
          No health components reported.
        </p>
      ) : (
        <section aria-label="Health components" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {health.components.map((component) => (
            <HealthCard key={component.name} component={component} />
          ))}
        </section>
      )}
    </div>
  );
}
