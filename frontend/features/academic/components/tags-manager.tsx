"use client";

import { MasterForm } from "@/features/academic/components/master-form";
import { useArchiveTag, useCreateTag, useTags } from "@/features/academic/hooks/use-academic";
import { tagFormSchema } from "@/features/academic/schemas/academic";
import { useAuth } from "@/lib/auth/auth-context";
import type { ApiClientError } from "@/lib/errors";

/** Tag master management ({@code specs/11-P2-Academic-V1.5.md §66}). Tags are metadata, not taxonomy. */
export function TagsManager() {
  const { can } = useAuth();
  const tags = useTags();
  const createTag = useCreateTag();
  const archiveTag = useArchiveTag();
  const canArchive = can("TAG_ARCHIVE");

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">Tags</h2>

      {can("TAG_CREATE") ? (
        <MasterForm
          schema={tagFormSchema}
          fields={[
            { name: "code", label: "Code" },
            { name: "name", label: "Name" },
            { name: "description", label: "Description" },
          ]}
          submitLabel="Create tag"
          onSubmit={async (values) => {
            await createTag.mutateAsync(values);
          }}
        />
      ) : null}

      {tags.isPending ? (
        <p role="status" className="text-sm text-muted-foreground">
          Loading tags…
        </p>
      ) : null}
      {tags.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {(tags.error as ApiClientError).message}
        </p>
      ) : null}
      {tags.data && tags.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">No tags yet.</p>
      ) : null}

      {tags.data && tags.data.length > 0 ? (
        <div className="overflow-x-auto" role="region" aria-label="Scrollable table" tabIndex={0}>
<table className="w-full min-w-[640px] text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-2">Code</th>
              <th className="py-2">Name</th>
              <th className="py-2">Status</th>
              {canArchive ? <th className="py-2">Actions</th> : null}
            </tr>
          </thead>
          <tbody>
            {tags.data.map((tag) => (
              <tr key={tag.id} className="border-b border-border">
                <td className="py-2 font-mono text-xs">{tag.code}</td>
                <td className="py-2">{tag.name}</td>
                <td className="py-2">{tag.status}</td>
                {canArchive ? (
                  <td className="py-2">
                    <button
                      type="button"
                      className="text-red-600 underline"
                      onClick={() => void archiveTag.mutateAsync(tag.id)}
                    >
                      Archive
                    </button>
                  </td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
</div>
      ) : null}
    </section>
  );
}
