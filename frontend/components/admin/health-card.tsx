"use client";

import type { HealthComponent } from "@/features/administration/schemas/administration";

import { StatusBadge } from "./status-badge";

/** One system-health component ({@code specs/20-P11-Administration-V1.5.md §59, §123}). */
export function HealthCard({ component }: { component: HealthComponent }) {
  const detail = Object.entries(component.detail ?? {});

  return (
    <article
      aria-label={component.name}
      className="space-y-2 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="text-base font-semibold text-slate-900">{component.name}</h3>
        <StatusBadge status={component.status} />
      </div>
      <p className="text-sm text-slate-600">{component.message ?? "No detail reported."}</p>
      <p className="text-xs text-slate-500">
        Latency: {component.latencyMs === null ? "not reported" : `${component.latencyMs} ms`}
      </p>
      {detail.length > 0 ? (
        <dl className="space-y-1 text-xs text-slate-500">
          {detail.map(([key, value]) => (
            <div key={key} className="flex gap-1">
              <dt className="font-medium">{key}:</dt>
              <dd className="break-all">{value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
    </article>
  );
}
