"use client";

/**
 * Assessment builder route ({@code specs/13-P4-Assessment-V1.5.md §98, §100}).
 */
import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AssessmentBuilder } from "@/features/assessment/components/assessment-builder";

export default function AssessmentBuilderPage() {
  const params = useParams<{ id: string; versionId: string }>();
  const assessmentId = params?.id ?? "";
  const versionId = params?.versionId ?? "";

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-16">
      <AuthenticatedGuard permission="ASSESSMENT_VERSION_VIEW">
        <AssessmentBuilder assessmentId={assessmentId} versionId={versionId} />
      </AuthenticatedGuard>
    </div>
  );
}
