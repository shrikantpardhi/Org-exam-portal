"use client";

/**
 * Blueprint detail route ({@code specs/13-P4-Assessment-V1.5.md §98}).
 *
 * <p>Lists the blueprint's versions and links into the builder. A published version is shown as
 * immutable so the operator creates a new version instead of editing it (§22).
 */
import Link from "next/link";
import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { useBlueprint, useBlueprintVersions } from "@/features/assessment/hooks/use-assessment";

export default function BlueprintDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";
  const blueprint = useBlueprint(id);
  const versions = useBlueprintVersions(id);

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <AuthenticatedGuard permission="BLUEPRINT_VIEW">
        <header className="space-y-1">
          <p className="text-sm text-slate-500">Blueprint</p>
          <h1 className="text-2xl font-semibold tracking-tight">
            {blueprint.data?.name ?? "Loading blueprint…"}
          </h1>
          <p className="font-mono text-xs text-slate-500">{blueprint.data?.code ?? id}</p>
          {blueprint.data?.description ? (
            <p className="text-sm text-slate-600">{blueprint.data.description}</p>
          ) : null}
          <p className="text-sm text-slate-500">Status: {blueprint.data?.status ?? "—"}</p>
        </header>

        {blueprint.isError ? (
          <p role="alert" className="text-sm text-red-600">
            The blueprint could not be loaded.
          </p>
        ) : null}

        {versions.isPending ? <p role="status">Loading versions…</p> : null}

        {versions.data && versions.data.length === 0 ? (
          <p role="note" className="text-sm text-slate-500">
            This blueprint has no versions yet.
          </p>
        ) : null}

        <ul className="space-y-3">
          {(versions.data ?? []).map((version) => (
            <li key={version.id} className="rounded border border-slate-200 p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium">Version {version.version}</span>
                <span className="text-sm text-slate-500">
                  {version.published ? "PUBLISHED (immutable)" : "DRAFT"}
                </span>
              </div>
              <div className="mt-2 flex flex-wrap gap-3 text-sm">
                <Link href={`/blueprints/${id}/versions/${version.id}`} className="underline">
                  {version.published ? "Create a new version from this configuration" : "Open in builder"}
                </Link>
              </div>
            </li>
          ))}
        </ul>
      </AuthenticatedGuard>
    </main>
  );
}
