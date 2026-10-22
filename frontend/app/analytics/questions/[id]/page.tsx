"use client";

import { useParams } from "next/navigation";

import { QuestionAnalyticsDashboard } from "@/features/analytics/components/question-analytics-dashboard";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";

/** Question analytics ({@code specs/17-P8-Analytics-V1.5.md §54}). */
export default function QuestionAnalyticsPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Analytics</p>
        <h1 className="text-2xl font-semibold tracking-tight">Question analytics</h1>
      </header>
      <AuthenticatedGuard permission="ANALYTICS_VIEW">
        <QuestionAnalyticsDashboard questionId={id} />
      </AuthenticatedGuard>
    </main>
  );
}
