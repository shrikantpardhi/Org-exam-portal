"use client";

import { OrganizationAnalyticsDashboard } from "@/features/analytics/components/organization-analytics-dashboard";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";

/** Organization analytics, gated by the analytics permission ({@code §56, §108}). */
export default function OrganizationAnalyticsPage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Analytics</p>
        <h1 className="text-2xl font-semibold tracking-tight">Organization analytics</h1>
      </header>
      <AuthenticatedGuard permission="ANALYTICS_VIEW">
        <OrganizationAnalyticsDashboard />
      </AuthenticatedGuard>
    </main>
  );
}
