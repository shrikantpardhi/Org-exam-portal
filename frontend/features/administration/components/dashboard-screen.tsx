"use client";

import Link from "next/link";

import { describeAdminError, formatDateTime } from "@/components/admin/format";
import { Alert } from "@/components/ui/alert";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ButtonLink } from "@/components/ui/button";
import {
  useAdminDashboard,
  useAuditLogs,
} from "@/features/administration/hooks/use-administration";

/** The admin dashboard ({@code specs/20-P11-Administration-V1.5.md §21-§23}). */
export function DashboardScreen() {
  const query = useAdminDashboard();
  // Recent activity is the audit trail, read from its owning module rather than copied onto the
  // dashboard: the dashboard aggregates, it never becomes a second source of truth.
  const activityQuery = useAuditLogs({ page: 0, size: 8 });

  if (query.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading dashboard…</p>;
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
          className="mt-2 rounded border border-red-300 bg-surface px-3 py-1 text-sm font-medium text-red-700"
        >
          Try again
        </button>
      </div>
    );
  }

  const dashboard = query.data;
  if (!dashboard) {
    return <p role="status" className="text-sm text-muted-foreground">Loading dashboard…</p>;
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
          <section key={card.label} aria-label={card.label} className="rounded-lg border border-border bg-surface p-4 shadow-sm">
            <h2 className="text-sm font-medium text-muted-foreground">{card.label}</h2>
            <p className="mt-1 text-2xl font-semibold tabular-nums text-foreground">{card.value}</p>
          </section>
        ))}
      </section>

      <section aria-labelledby="recent-activity" className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 id="recent-activity" className="text-base font-semibold">Recent activity</h2>
            <p className="text-sm text-muted-foreground">
              The latest recorded events in this organization, newest first.
            </p>
          </div>
          <ButtonLink variant="outline" href="/admin/audit">View full audit log</ButtonLink>
        </div>

        {activityQuery.isPending ? (
          <p role="status" className="text-sm text-muted-foreground">Loading recent activity…</p>
        ) : null}
        {activityQuery.isError ? (
          <Alert tone="danger">
            {describeAdminError(activityQuery.error, "Recent activity could not be loaded.")}
          </Alert>
        ) : null}
        {activityQuery.isSuccess && (activityQuery.data.items ?? []).length === 0 ? (
          <EmptyState
            title="No activity yet"
            description="Administrative actions appear here as they happen."
          />
        ) : null}
        {activityQuery.isSuccess && (activityQuery.data.items ?? []).length > 0 ? (
          <Card>
            <CardContent className="p-0">
              <div className="overflow-x-auto" tabIndex={0}>
                <table className="w-full min-w-[640px] text-sm">
                  <caption className="sr-only">Recent administrative activity</caption>
                  <thead>
                    <tr className="border-b border-border text-left text-muted-foreground">
                      <th scope="col" className="px-4 py-2 font-medium">When</th>
                      <th scope="col" className="px-4 py-2 font-medium">Action</th>
                      <th scope="col" className="px-4 py-2 font-medium">Entity</th>
                      <th scope="col" className="px-4 py-2 font-medium">Detail</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(activityQuery.data.items ?? []).map((entry) => (
                      <tr key={entry.id} className="border-b border-border last:border-0">
                        <td className="whitespace-nowrap px-4 py-2 text-muted-foreground">
                          {formatDateTime(entry.createdOn) ?? "—"}
                        </td>
                        <td className="px-4 py-2 font-medium text-foreground">{entry.action}</td>
                        <td className="px-4 py-2 text-muted-foreground">{entry.entityName}</td>
                        <td className="px-4 py-2 text-muted-foreground">{entry.remarks ?? "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        ) : null}
      </section>

      <section aria-labelledby="dashboard-sources" className="space-y-2">
        <h2 id="dashboard-sources" className="text-base font-semibold">Where these numbers come from</h2>
        <p className="text-sm text-muted-foreground">
          Every card is aggregated from its owning module at read time, never from a transactional
          copy, so the numbers cannot drift from the records they describe.
        </p>
        {dashboard.sources.length === 0 ? (
          <p role="note" className="text-sm text-muted-foreground">No card sources reported.</p>
        ) : (
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            {dashboard.sources.map((source) => (
              <div key={source.card} className="flex justify-between gap-3 rounded-md border border-border bg-surface px-3 py-2">
                <dt className="text-foreground">{source.card}</dt>
                <dd className="text-muted-foreground">{source.owner}</dd>
              </div>
            ))}
          </dl>
        )}
        <p className="text-xs text-muted-foreground">
          {generated ? "Generated " + generated : "Generation time not reported"}
        </p>
      </section>

      <p className="text-sm text-muted-foreground">
        Looking for organization-wide results? The <Link className="underline underline-offset-2" href="/analytics/organization">organization analytics</Link> screen covers assessment performance.
      </p>
    </div>
  );
}
