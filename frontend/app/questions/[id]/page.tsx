"use client";

import Link from "next/link";
import { useParams } from "next/navigation";

import { AnalyticsLink } from "@/components/analytics/analytics-link";
import { ReviewActions } from "@/features/content/components/review-actions";
import { useQuestion } from "@/features/content/hooks/use-content";

/** Question detail with version history and review actions ({@code §54, §55, §110}). */
export default function QuestionDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";
  const question = useQuestion(id);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Question</h1>
      <p className="font-mono text-xs text-slate-500">{id}</p>
      <AnalyticsLink href={`/analytics/questions/${id}`} label="View question analytics" />

      {question.isPending ? <p role="status">Loading…</p> : null}
      {question.isError ? <p role="alert">The question could not be loaded.</p> : null}

      {question.data ? (
        <ul className="space-y-4">
          {question.data.versions.map((version) => (
            <li key={version.id} className="rounded border border-slate-200 p-4">
              <div className="flex items-center justify-between">
                <span className="font-medium">Version {version.version}</span>
                <span className="text-sm text-slate-500">{version.reviewStatus}</span>
              </div>
              <Link
                href={`/questions/${id}/versions/${version.id}`}
                className="text-sm underline"
              >
                Open version
              </Link>
              <div className="mt-3">
                <ReviewActions
                  questionId={id}
                  versionId={version.id}
                  reviewStatus={version.reviewStatus}
                  published={version.published}
                />
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </main>
  );
}
