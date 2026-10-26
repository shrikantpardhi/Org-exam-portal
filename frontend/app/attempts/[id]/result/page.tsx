"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AttemptResultView } from "@/features/evaluation/components/attempt-result-view";

/** The attempt-bound result view ({@code specs/15-P6-Evaluation-V1.5.md §95}). */
export default function AttemptResultPage() {
  const params = useParams<{ id: string }>();
  const attemptId = params?.id ?? "";

  return (
    <div className="flex w-full max-w-4xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Results</p>
        <h1 className="text-2xl font-semibold tracking-tight">Attempt result</h1>
        <p className="font-mono text-xs text-muted-foreground">{attemptId}</p>
      </header>
      <AuthenticatedGuard>
        <AttemptResultView attemptId={attemptId} />
      </AuthenticatedGuard>
    </div>
  );
}
