"use client";

import { useBlueprints } from "@/features/assessment/hooks/use-assessment";
import type { ApiClientError } from "@/lib/errors";

export default function BlueprintsPage() {
  const blueprints = useBlueprints();

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Blueprints</h1>

      {blueprints.isPending ? <p role="status">Loading blueprints…</p> : null}
      {blueprints.isError ? <p role="alert">{(blueprints.error as ApiClientError).message}</p> : null}
      {blueprints.data && blueprints.data.length === 0 ? (
        <p className="text-sm text-slate-500">No blueprints yet.</p>
      ) : null}

      <ul className="space-y-2 text-sm">
        {blueprints.data?.map((blueprint) => (
          <li key={blueprint.id} className="rounded border border-slate-200 p-3">
            <span className="font-mono text-xs">{blueprint.code}</span> — {blueprint.name}
          </li>
        ))}
      </ul>
    </main>
  );
}
