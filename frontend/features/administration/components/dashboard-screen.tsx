"use client";

import { describeAdminError, formatDateTime } from "@/components/admin/format";
import { useAdminDashboard } from "@/features/administration/hooks/use-administration";

/** The admin dashboard ({@code specs/20-P11-Administration-V1.5.md §21-§23}). */
export function DashboardScreen() {
  const query = useAdminDashboard();

  if (query.isPending) {
    return <p role="status" className="text-sm text-slate-500">Loading dashboard…</p>;
  }

  if (query.isError) {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        <p>{describeAdminError(query.error, "The dashboard could not be loaded.")}</p>
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

  const dashboard = query.data;
  if (!dashboard) {
    return <p role="status" className="text-sm text-slate-500">Loading dashboard…</p>;
  }

  const cards = [
    { label: "Active users", value: dashboard.activeUsers },
    { label: "Students", value: dashboard.students },
    { label: "Audit events (24h)", value: dashboard.auditEventsLast24h },
    { label: "Business rules", value: dashboard.businessRules },
    { label: "Lookups", value: dashboard.lookups },
    { label: "Active lookup values", value: dashboard.activeLookupValues },
  ];

  const generated = formatDateTime(dashboard.generatedOn);

  return (
    <div className="space-y-6">
      <section aria-label="Dashboard cards" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {cards.map((card) => (
          <section key={card.label} aria-label={card.label} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-medium text-slate-600">{card.label}</h2>
            <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{card.value}</p>
          </section>
        ))}
      </section>

      <section aria-labelledby="dashboard-sources" className="space-y-2">
        <h2 id="dashboard-sources" className="text-base font-semibold">
          Card ownership
        </h2>
        <p className="text-sm text-slate-500">
          Cards are derived from owning modules, never from a transactional copy.
        </p>
        {dashboard.sources.length === 0 ? (
          <p role="note" className="text-sm text-slate-500">No card sources reported.</p>
        ) : (
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Dashboard card ownership</caption>
            <thead>
              <tr className="border-b border-slate-200">
                <th scope="col" className="py-2 pr-3">Card</th>
                <th scope="col" className="py-2">Owning module</th>
              </tr>
            </thead>
            <tbody>
              {dashboard.sources.map((source) => (
                <tr key={source.card} className="border-b border-slate-100">
                  <th scope="row" className="py-2 pr-3 font-normal">{source.card}</th>
                  <td className="py-2">{source.owner}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="text-xs text-slate-500">
          {generated ? `Generated ${generated}` : "Generation time not reported"}
        </p>
      </section>

    </div>
  );
}
