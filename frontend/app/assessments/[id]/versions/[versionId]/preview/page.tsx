"use client";

/**
 * Assessment preview route ({@code specs/13-P4-Assessment-V1.5.md §98, §107}).
 */
import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AssessmentPreview } from "@/features/assessment/components/assessment-preview";
import { useAssessmentVersion } from "@/features/assessment/hooks/use-assessment";

export default function AssessmentPreviewPage() {
  const params = useParams<{ id: string; versionId: string }>();
  const versionId = params?.versionId ?? "";
  const version = useAssessmentVersion(versionId);

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <AuthenticatedGuard permission="ASSESSMENT_VERSION_VIEW">
        <AssessmentPreview versionId={versionId} version={version.data} />
      </AuthenticatedGuard>
    </main>
  );
}
