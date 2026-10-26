"use client";

import Link from "next/link";

import { describeAdminError } from "@/components/admin/format";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { usePlatformStatus, usePlatformSystem } from "@/features/platform/hooks/use-platform";

/** The platform landing: empty-state prompt or live counts plus a system summary (super-admin-flow §3-§4). */
export function DashboardScreen() {
  const status = usePlatformStatus();
  const system = usePlatformSystem();

  if (status.isPending || system.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading platform…</p>;
 }

 if (status.isError || system.isError) {
 const message = status.isError
 ? describeAdminError(status.error, "Platform status could not be loaded.")
 : describeAdminError(system.error, "System information could not be loaded.");
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        {message}
        <button
 type="button"
          onClick={() => {
 void status.refetch();
 void system.refetch();
 }}
 className="ml-3 underline"
        >
          Retry
        </button>
      </div>
 );
 }

 const stats = status.data;
 const info = system.data;

 const cards = [
 { label: "Organizations", value: stats.organizationCount },
 { label: "Administrators", value: stats.adminCount },
 { label: "Users", value: stats.userCount },
 ];

 const migration = info.migrationVersion != null
 ? "V" + info.migrationVersion + (info.lastMigration ? " (" + info.lastMigration + ")" : "")
 : "—";

  return (
    <div className="space-y-6">
      {stats.organizationCount === 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>Welcome. No business organization yet.</CardTitle>
            <CardDescription>
              Create the first organization and its ADMIN to bring the platform online.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link
 href="/platform/organizations/new"
 className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              + Create organization
            </Link>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-3">
          {cards.map((card) => (
            <Card key={card.label}>
              <CardHeader>
                <CardDescription>{card.label}</CardDescription>
                <CardTitle>{card.value}</CardTitle>
              </CardHeader>
            </Card>
          ))}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>System</CardTitle>
          <CardDescription>Version, migration and seed status.</CardDescription>
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
              <dd>{migration}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Seeds</dt>
              <dd>{stats.seedStatus ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Health</dt>
              <dd>{stats.systemHealth ?? "—"}</dd>
            </div>
          </dl>
          <div className="mt-4 flex flex-wrap gap-2">
            <Link
 href="/platform/organizations"
 className="rounded-md border border-input bg-surface px-3 py-2 text-sm text-foreground hover:bg-muted"
            >
              Organizations
            </Link>
            <Link
 href="/platform/providers"
 className="rounded-md border border-input bg-surface px-3 py-2 text-sm text-foreground hover:bg-muted"
            >
              Providers
            </Link>
            <Link
 href="/platform/system"
 className="rounded-md border border-input bg-surface px-3 py-2 text-sm text-foreground hover:bg-muted"
            >
              System info
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
