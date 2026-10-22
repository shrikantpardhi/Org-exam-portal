"use client";

import { useParams } from "next/navigation";

import { TopicAnalyticsDashboard } from "@/features/analytics/components/topic-analytics-dashboard";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";

/** Topic analytics ({@code specs/17-P8-Analytics-V1.5.md §55}). */
export default function TopicAnalyticsPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Analytics</p>
        <h1 className="text-2xl font-semibold tracking-tight">Topic analytics</h1>
      </header>
      <AuthenticatedGuard permission="ANALYTICS_VIEW">
        <TopicAnalyticsDashboard topicId={id} />
      </AuthenticatedGuard>
    </main>
  );
}
