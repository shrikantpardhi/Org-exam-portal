"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { ReportingWorkspace } from "@/features/reporting/components/reporting-workspace";

/**
 * Staff reporting workspace ({@code specs/22-P13-Reporting-V1.5.md §110, §112}).
 *
 * <p>Report category, report name, description, filters, format, generate, job status and history,
 * all restricted to the report types the server advertises for this caller's permissions.
 */
export default function StaffReportsPage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Staff</p>
        <h1 className="text-2xl font-semibold tracking-tight">Reporting workspace</h1>
        <p className="text-sm text-slate-500">
          Generate operational reports, follow a queued job to completion, and download the artifact.
        </p>
      </header>
      <AuthenticatedGuard permission="REPORT_OPERATIONS_READ">
        <ReportingWorkspace
          heading="Report catalog"
          scopeNote="Reports are scoped by the permissions the server reports for your account. A report type you are not permitted to request is never advertised here."
        />
      </AuthenticatedGuard>
    </main>
  );
}
