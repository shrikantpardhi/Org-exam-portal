"use client";

import { useParams } from "next/navigation";

import { AnalyticsLink } from "@/components/analytics/analytics-link";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { QuestionWorkspace } from "@/features/content/components/question-workspace";

/**
 * Question workspace route ({@code specs/12-P3-Content-V1.5.md §110, §113}).
 *
 * <p>Authoring needs the update permission; the guard is a UI affordance and the backend still
 * authorizes every request.
 */
export default function QuestionDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <AuthenticatedGuard permission="QUESTION_UPDATE">
      <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Question workspace</h1>
          <p className="font-mono text-xs text-slate-500">{id}</p>
          <AnalyticsLink href={`/analytics/questions/${id}`} label="View question analytics" />
        </div>
        <QuestionWorkspace questionId={id} />
      </main>
    </AuthenticatedGuard>
  );
}
