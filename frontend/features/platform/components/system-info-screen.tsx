"use client";

import { describeAdminError, formatDateTime } from "@/components/admin/format";
import { DataTable, type DataTableColumn } from "@/components/admin/data-table";
import { StatusBadge } from "@/components/admin/status-badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { usePlatformSystem } from "@/features/platform/hooks/use-platform";
import type { DependencyStatus } from "@/features/platform/schemas/platform";

function formatUptime(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  if (days > 0) {
    return days + "d " + (hours % 24) + "h";
  }
  if (hours > 0) {
    return hours + "h " + (minutes % 60) + "m";
  }
  return minutes + "m";
}

/** Platform system information and dependency health (super-admin-flow §11). */
export function SystemInfoScreen() {
  const query = usePlatformSystem();

  if (query.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading system information…</p>;
  }

  if (query.isError) {
    return (
      <div role="alert" className="rounded-lg border border-destructive bg-destructive-subtle p-4 text-sm text-destructive">
        {describeAdminError(query.error, "System information could not be loaded.")}
        <button type="button" onClick={() => void query.refetch()} className="ml-3 underline">
          Retry
        </button>
      </div>
    );
  }

  const info = query.data;

  const columns: DataTableColumn<DependencyStatus>[] = [
    { key: "name", header: "Dependency", render: (dep) => dep.name },
    {
      key: "status",
      header: "Status",
      render: (dep) => <StatusBadge status={dep.status} />,
    },
  ];

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Versions</CardTitle>
          <CardDescription>Application, frontend and schema version.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Application</dt>
              <dd>{info.appVersion ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Frontend</dt>
              <dd>{info.frontendVersion ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Migration</dt>
              <dd>
                {info.migrationVersion != null ? "V" + info.migrationVersion : "—"}
                {info.lastMigration ? " (" + info.lastMigration + ")" : ""}
              </dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Applied on</dt>
              <dd>{formatDateTime(info.appliedOn) ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Uptime</dt>
              <dd>{formatUptime(info.uptimeSeconds)}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Service status</CardTitle>
          <CardDescription>
            Each backing service this installation depends on, as the server last observed it.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {/*
            Whatever the server reports is rendered, in the server's order: the list is a report of
            dependencies, not a fixed form, so adding a dependency (redis, background workers) must
            not need a client change.
          */}
          <DataTable
            caption="Service status"
            columns={columns}
            rows={info.dependencies}
            getRowKey={(dep) => dep.name}
            emptyMessage="No dependency status reported."
          />
        </CardContent>
      </Card>
    </div>
  );
}
