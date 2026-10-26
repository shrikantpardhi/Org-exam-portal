"use client";

import { ButtonLink } from "@/components/ui/button";
import { AdminShell } from "@/features/administration/components/admin-shell";
import { OrganizationAnalyticsDashboard } from "@/features/analytics/components/organization-analytics-dashboard";

/**
 * Organization analytics, gated by {@code ANALYTICS_VIEW} ({@code specs/19-P10 §56, §108}).
 *
 * <p>Reached from the admin sidebar. The page is wrapped in {@code AdminShell} so it renders inside
 * the organization workspace like every other administrative screen; before this it rendered as an
 * anonymous full-width page with no navigation, which is why it looked unreachable.
 */
export default function OrganizationAnalyticsPage() {
  return (
    <AdminShell
      title="Organization analytics"
      description="Assessment performance across this organization, aggregated from the owning modules."
      permission="ANALYTICS_VIEW"
      actions={
        <ButtonLink variant="outline" href="/admin">Back to dashboard</ButtonLink>
      }
    >
      <OrganizationAnalyticsDashboard />
    </AdminShell>
  );
}
