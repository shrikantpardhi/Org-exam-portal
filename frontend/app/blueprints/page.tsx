"use client";

/**
 * Blueprint list ({@code specs/13-P4-Assessment-V1.5.md §98, §99}).
 *
 * <p>Each row links into the blueprint detail, where versions are chosen and the builder is opened.
 * The list stays a list: version authoring happens on the detail and builder screens.
 */
import Link from "next/link";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { useBlueprints } from "@/features/assessment/hooks/use-assessment";
import type { ApiClientError } from "@/lib/errors";

export default function BlueprintsPage() {
  const blueprints = useBlueprints();

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Blueprints</h1>
        <p className="text-sm text-muted-foreground">
          A blueprint defines the sections and delivery configuration an assessment version is built
          against.
        </p>
      </header>

      <AuthenticatedGuard permission="BLUEPRINT_VIEW">
        {blueprints.isPending ? <p role="status">Loading blueprints…</p> : null}
        {blueprints.isError ? (
          <p role="alert" className="text-sm text-red-600">
            {(blueprints.error as ApiClientError).message}
          </p>
        ) : null}
        {blueprints.data && blueprints.data.length === 0 ? (
          <p role="note" className="text-sm text-muted-foreground">
            No blueprints yet.
          </p>
        ) : null}

        <ul className="space-y-2 text-sm">
          {blueprints.data?.map((blueprint) => (
            <li key={blueprint.id} className="rounded border border-border p-3">
              <Link href={`/blueprints/${blueprint.id}`} className="font-medium underline">
                {blueprint.name}
              </Link>
              <span className="ml-2 font-mono text-xs text-muted-foreground">{blueprint.code}</span>
              <span className="ml-2 text-muted-foreground">{blueprint.status}</span>
            </li>
          ))}
        </ul>
      </AuthenticatedGuard>
    </div>
  );
}
