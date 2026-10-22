"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AttemptResultView } from "@/features/evaluation/components/attempt-result-view";

/** The attempt-bound result view ({@code specs/15-P6-Evaluation-V1.5.md §95}). */
export default function AttemptResultPage() {
  const params = useParams<{ id: string }>();
  const attemptId = params?.id ?? "";

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Results</p>
        <h1 className="text-2xl font-semibold tracking-tight">Attempt result</h1>
        <p className="font-mono text-xs text-slate-500">{attemptId}</p>
      </header>
      <AuthenticatedGuard>
        <AttemptResultView attemptId={attemptId} />
      </AuthenticatedGuard>
    </main>
  );
}
