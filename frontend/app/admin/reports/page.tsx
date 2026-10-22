"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { ReportingWorkspace } from "@/features/reporting/components/reporting-workspace";

/**
 * Admin reporting workspace ({@code specs/22-P13-Reporting-V1.5.md §111, §112}).
 *
 * <p>Adds organization scope and executive summaries to the staff workspace. The common Reports
 * API is used; this screen invents no report type and no endpoint of its own.
 */
export default function AdminReportsPage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Administration</p>
        <h1 className="text-2xl font-semibold tracking-tight">Reporting</h1>
        <p className="text-sm text-slate-500">
          Organization-scoped operations reports and executive summaries. Metrics always carry the
          definition that produced them.
        </p>
      </header>
      <AuthenticatedGuard permission="REPORT_EXECUTIVE_READ">
        <ReportingWorkspace
          heading="Report catalog"
          scopeNote="Organization scope is applied by the server. An executive report aggregates the organization's data; this screen displays the aggregate and cannot request a scope the server does not authorize."
        />
      </AuthenticatedGuard>
    </main>
  );
}
