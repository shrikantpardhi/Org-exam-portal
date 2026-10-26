"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { ReportingWorkspace } from "@/features/reporting/components/reporting-workspace";

/**
 * Organization reporting workspace ({@code specs/22-P13-Reporting-V1.5.md} §111, §112).
 *
 * <p>Organization-scoped reports and executive summaries over the common Reports API; this screen
 * invents no report type and no endpoint of its own. Rendered inside {@code AdminShell} like every
 * other administrative screen — before this it was the one admin route with no sidebar, so it was
 * reachable only by typing the URL and had no way back.
 */
export default function AdminReportsPage() {
  return (
    <AdminShell
      title="Reports"
      description="Organization-scoped reports and executive summaries. Every metric carries the definition that produced it."
      permission="REPORT_EXECUTIVE_READ"
    >
      <ReportingWorkspace
        heading="Report catalog"
        scopeNote="Organization scope is applied by the server. An executive report aggregates this organization's data; this screen displays the aggregate and cannot request a scope the server does not authorize."
      />
    </AdminShell>
  );
}
