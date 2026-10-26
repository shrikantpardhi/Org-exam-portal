"use client";

import { StudentAnalyticsDashboard } from "@/features/analytics/components/student-analytics-dashboard";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";

/** The student's own analytics ({@code specs/17-P8-Analytics-V1.5.md §50-§52}). */
export default function StudentAnalyticsPage() {
  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Analytics</p>
        <h1 className="text-2xl font-semibold tracking-tight">My analytics</h1>
      </header>
      <AuthenticatedGuard>
        <StudentAnalyticsDashboard />
      </AuthenticatedGuard>
    </div>
  );
}
