"use client";

/**
 * Assessment detail with version validation and publication ({@code §100, §106, §108}).
 *
 * <p>Each version links into the builder and the authoring preview. Validation and publication stay
 * available here as a quick path; the builder owns the full authoring workflow.
 */
import Link from "next/link";
import { useParams } from "next/navigation";

import { AnalyticsLink } from "@/components/analytics/analytics-link";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AssessmentValidationPanel } from "@/features/assessment/components/assessment-validation-panel";
import { useAssessment } from "@/features/assessment/hooks/use-assessment";

export default function AssessmentDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <AuthenticatedGuard permission="ASSESSMENT_VIEW">
        <AssessmentDetail id={id} />
      </AuthenticatedGuard>
    </main>
  );
}

/**
 * The detail view proper. Split out so the guard wraps the data-reading hooks — a hook that runs
 * above the guard would fetch for a caller who may not read the assessment.
 */
function AssessmentDetail({ id }: { id: string }) {
  const assessment = useAssessment(id);

  return (
    <>
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
              <div className="mt-2 flex flex-wrap gap-3 text-sm">
                <Link href={`/assessments/${id}/versions/${version.id}/builder`} className="underline">
                  {version.published ? "View builder" : "Open builder"}
                </Link>
                <Link href={`/assessments/${id}/versions/${version.id}/preview`} className="underline">
                  Authoring preview
                </Link>
              </div>
              <div className="mt-3">
                <AssessmentValidationPanel versionId={version.id} published={version.published} />
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </>
  );
}
