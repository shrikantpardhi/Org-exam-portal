"use client";

/**
 * Blueprint builder route ({@code specs/13-P4-Assessment-V1.5.md §98, §99}).
 *
 * <p>The version id identifies an existing BlueprintVersion; saving writes a new version because the
 * frozen contract exposes no configuration update route.
 */
import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { BlueprintBuilder } from "@/features/assessment/components/blueprint-builder";

export default function BlueprintVersionPage() {
  const params = useParams<{ id: string; versionId: string }>();
  const blueprintId = params?.id ?? "";
  const versionId = params?.versionId ?? "";

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <AuthenticatedGuard permission="BLUEPRINT_VIEW">
        <BlueprintBuilder blueprintId={blueprintId} versionId={versionId} />
      </AuthenticatedGuard>
    </main>
  );
}
