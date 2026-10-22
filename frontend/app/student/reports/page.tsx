"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { ReportingWorkspace } from "@/features/reporting/components/reporting-workspace";

/**
 * Student self-service reports ({@code specs/22-P13-Reporting-V1.5.md §109, §112}).
 *
 * <p>Own attempts, own performance, own topic performance, and own results only. Organization-wide
 * reports are not exposed to students, and the backend scopes every student request to the caller
 * regardless of the filters supplied here ({@code §76}).
 */
export default function StudentReportsPage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Student</p>
        <h1 className="text-2xl font-semibold tracking-tight">My reports</h1>
        <p className="text-sm text-slate-500">
          Generate a report of your own attempt and performance data, then download it when it is
          ready.
        </p>
      </header>
      <AuthenticatedGuard permission="REPORT_STUDENT_READ">
        <ReportingWorkspace
          heading="Available reports"
          scopeNote="Student reports are always scoped to you by the backend. A report cannot be pointed at another student, and a filter cannot widen that scope."
        />
      </AuthenticatedGuard>
    </main>
  );
}
