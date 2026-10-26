"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { ScorecardByResultView } from "@/features/evaluation/components/scorecard-by-result-view";

/** The scorecard of a result ({@code specs/15-P6-Evaluation-V1.5.md §58, §98}). */
export default function ScorecardPage() {
  const params = useParams<{ id: string }>();
  const resultId = params?.id ?? "";

  return (
    <div className="flex w-full max-w-4xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Results</p>
        <h1 className="text-2xl font-semibold tracking-tight">Scorecard</h1>
      </header>
      <AuthenticatedGuard>
        <ScorecardByResultView resultId={resultId} />
      </AuthenticatedGuard>
    </div>
  );
}
