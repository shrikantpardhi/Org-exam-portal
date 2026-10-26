"use client";

import { StudentResultsList } from "@/features/evaluation/components/student-results-list";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";

/**
 * The student's result list ({@code specs/15-P6-Evaluation-V1.5.md §39, §95}).
 *
 * <p>The route is authentication-gated; ownership and publication are enforced by the backend, and
 * the list independently hides anything that is not a published, current result.
 */
export default function ResultsPage() {
  return (
    <div className="flex w-full max-w-5xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Results</p>
        <h1 className="text-2xl font-semibold tracking-tight">My results</h1>
        <p className="text-sm text-muted-foreground">
          Only published results are shown. A result appears after evaluation completes and it is
          published.
        </p>
      </header>
      <AuthenticatedGuard>
        <StudentResultsList />
      </AuthenticatedGuard>
    </div>
  );
}
