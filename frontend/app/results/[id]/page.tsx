"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { ResultByIdView } from "@/features/evaluation/components/result-by-id-view";

/** A single result ({@code specs/15-P6-Evaluation-V1.5.md §95}). */
export default function ResultPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <div className="flex w-full max-w-4xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Results</p>
        <h1 className="text-2xl font-semibold tracking-tight">Result</h1>
      </header>
      <AuthenticatedGuard>
        <ResultByIdView resultId={id} />
      </AuthenticatedGuard>
    </div>
  );
}
