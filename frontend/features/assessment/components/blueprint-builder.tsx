"use client";

/**
 * Blueprint builder screen ({@code specs/13-P4-Assessment-V1.5.md §22, §99}).
 *
 * <p>Loads an existing BlueprintVersion configuration into the form and writes a complete frozen
 * configuration back. Saving creates a new version, because the frozen Blueprint API exposes no
 * update route — and a published version is immutable anyway (§22), so the screen routes the operator
 * to a new version instead of mutating one in place.
 */
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { BlueprintConfigurationEditor } from "@/features/assessment/components/blueprint-configuration-editor";
import { BlueprintSectionEditor } from "@/features/assessment/components/blueprint-section-editor";
import {
  useBlueprint,
  useBlueprintVersion,
  useCreateBlueprintVersion,
  usePublishBlueprintVersion,
} from "@/features/assessment/hooks/use-assessment";
import {
  DEFAULT_BLUEPRINT_FORM,
  describeBlueprintGaps,
  fromBlueprintConfiguration,
  toBlueprintConfiguration,
  type BlueprintFormValues,
} from "@/features/assessment/schemas/blueprint-config";
import { useLanguages } from "@/features/academic/hooks/use-academic";
import { ApiClientError } from "@/lib/errors";

export function BlueprintBuilder({
  blueprintId,
  versionId,
}: {
  blueprintId: string;
  versionId: string;
}) {
  const router = useRouter();
  const blueprint = useBlueprint(blueprintId);
  const version = useBlueprintVersion(blueprintId, versionId);
  const languages = useLanguages();
  const createVersion = useCreateBlueprintVersion(blueprintId);
  const publish = usePublishBlueprintVersion(blueprintId, versionId);

  const [values, setValues] = useState<BlueprintFormValues>(DEFAULT_BLUEPRINT_FORM);
  const [loadedVersionId, setLoadedVersionId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Load the stored configuration once per version. Re-renders never clobber local edits.
  useEffect(() => {
    const response = version.data;
    if (!response || loadedVersionId === response.id) {
      return;
    }
    setValues(fromBlueprintConfiguration(response.configuration));
    setLoadedVersionId(response.id);
  }, [version.data, loadedVersionId]);

  const gaps = useMemo(() => describeBlueprintGaps(values), [values]);
  const published = version.data?.published === true;

  async function saveAsNewVersion() {
    setMessage(null);
    setError(null);
    try {
      const created = await createVersion.mutateAsync(toBlueprintConfiguration(values));
      const createdVersionId = (created as { id?: string }).id;
      setMessage("A new blueprint version was created from this configuration.");
      if (createdVersionId) {
        router.push(`/blueprints/${blueprintId}/versions/${createdVersionId}`);
      }
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The blueprint version could not be saved.");
    }
  }

  async function publishVersion() {
    setMessage(null);
    setError(null);
    try {
      await publish.mutateAsync();
      setMessage("Blueprint version published.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "Publication was rejected.");
    }
  }

  if (version.isPending) {
    return <p role="status">Loading blueprint version…</p>;
  }

  if (version.isError) {
    return (
      <p role="alert" className="text-sm text-red-600">
        The blueprint version could not be loaded.
      </p>
    );
  }

  return (
    <div className="space-y-8">
      <section aria-labelledby="blueprint-header-heading" className="space-y-2">
        <h1 id="blueprint-header-heading" className="text-2xl font-semibold tracking-tight">
          {blueprint.data?.name ?? "Blueprint"}
        </h1>
        <dl className="grid gap-2 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-muted-foreground">Code</dt>
            <dd className="font-mono text-xs">{blueprint.data?.code ?? blueprintId}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Status</dt>
            <dd>{blueprint.data?.status ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Version</dt>
            <dd>{version.data?.version ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Version state</dt>
            <dd>{published ? "PUBLISHED (immutable)" : "DRAFT"}</dd>
          </div>
        </dl>
      </section>

      {published ? (
        <p role="status" className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
          This blueprint version is published and immutable. Saving creates a new version from this
          configuration so the published one is never edited in place (§22).
        </p>
      ) : null}

      <BlueprintSectionEditor sections={values.sections} onChange={(sections) => setValues({ ...values, sections })} />
      <BlueprintConfigurationEditor
        values={values}
        onChange={setValues}
        languages={languages.data ?? []}
      />

      <section aria-labelledby="blueprint-summary-heading" className="space-y-3">
        <h2 id="blueprint-summary-heading" className="text-lg font-semibold tracking-tight">
          Validation summary
        </h2>
        {gaps.valid ? (
          <p role="status" className="text-sm text-green-700">
            The configuration is complete. The server still validates the saved version before publication.
          </p>
        ) : (
          <ul role="alert" className="list-inside list-disc space-y-1 text-sm text-red-600">
            {gaps.missing.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        )}
      </section>

      <div className="flex flex-wrap gap-3">
        <Button onClick={() => void saveAsNewVersion()} disabled={createVersion.isPending || !gaps.valid}>
          {published ? "Create new version from this configuration" : "Save as new version"}
        </Button>
        <Button
          variant="outline"
          onClick={() => void publishVersion()}
          disabled={published || publish.isPending || !gaps.valid}
        >
          Publish
        </Button>
      </div>

      {message ? (
        <p role="status" className="text-sm text-green-700">
          {message}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}
