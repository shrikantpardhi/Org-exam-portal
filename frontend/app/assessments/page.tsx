import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AssessmentList } from "@/features/assessment/components/assessment-list";

/**
 * Assessment list ({@code specs/13-P4-Assessment-V1.5.md §98}).
 *
 * <p>Guarded by {@code ASSESSMENT_VIEW} so the screen is not reachable by a user without the
 * permission. The backend still authorizes every request; this only avoids rendering a screen whose
 * data the caller cannot read.
 */
export default function AssessmentsPage() {
  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Assessments</h1>
      <AuthenticatedGuard permission="ASSESSMENT_VIEW">
        <AssessmentList />
      </AuthenticatedGuard>
    </div>
  );
}
