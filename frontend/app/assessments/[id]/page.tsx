"use client";

import { useParams } from "next/navigation";

import { AnalyticsLink } from "@/components/analytics/analytics-link";
import { AssessmentValidationPanel } from "@/features/assessment/components/assessment-validation-panel";
import { useAssessment } from "@/features/assessment/hooks/use-assessment";

/** Assessment detail with version validation and publication ({@code §100, §106, §108}). */
export default function AssessmentDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";
  const assessment = useAssessment(id);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">{assessment.data?.name ?? "Assessment"}</h1>
      <p className="font-mono text-xs text-slate-500">{id}</p>
      <AnalyticsLink href={`/analytics/assessments/${id}`} label="View assessment analytics" />

      {assessment.isPending ? <p role="status">Loading…</p> : null}
      {assessment.isError ? <p role="alert">The assessment could not be loaded.</p> : null}

      {assessment.data ? (
        <ul className="space-y-4">
          {assessment.data.versions.map((version) => (
            <li key={version.id} className="rounded border border-slate-200 p-4">
              <div className="flex items-center justify-between">
                <span className="font-medium">Version {version.version}</span>
                <span className="text-sm text-slate-500">
                  {version.published ? "PUBLISHED" : "DRAFT"}
                  {version.engineType ? ` · ${version.engineType}` : ""}
                </span>
              </div>
              <div className="mt-3">
                <AssessmentValidationPanel versionId={version.id} published={version.published} />
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </main>
  );
}
