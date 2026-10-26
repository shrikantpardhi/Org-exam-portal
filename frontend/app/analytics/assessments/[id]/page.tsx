"use client";

import { useParams } from "next/navigation";

import { AssessmentAnalyticsDashboard } from "@/features/analytics/components/assessment-analytics-dashboard";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";

/** Assessment analytics ({@code specs/17-P8-Analytics-V1.5.md §53}). */
export default function AssessmentAnalyticsPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Analytics</p>
        <h1 className="text-2xl font-semibold tracking-tight">Assessment analytics</h1>
      </header>
      <AuthenticatedGuard permission="ANALYTICS_VIEW">
        <AssessmentAnalyticsDashboard assessmentId={id} />
      </AuthenticatedGuard>
    </div>
  );
}
