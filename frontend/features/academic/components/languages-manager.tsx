"use client";

import { MasterForm } from "@/features/academic/components/master-form";
import {
  useArchiveLanguage,
  useCreateLanguage,
  useLanguages,
} from "@/features/academic/hooks/use-academic";
import { languageFormSchema } from "@/features/academic/schemas/academic";
import { useAuth } from "@/lib/auth/auth-context";
import type { ApiClientError } from "@/lib/errors";

/** Language master management ({@code specs/11-P2-Academic-V1.5.md §65}). */
export function LanguagesManager() {
  const { can } = useAuth();
  const languages = useLanguages();
  const createLanguage = useCreateLanguage();
  const archiveLanguage = useArchiveLanguage();
  const canArchive = can("LANGUAGE_ARCHIVE");

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">Languages</h2>

      {can("LANGUAGE_CREATE") ? (
        <MasterForm
          schema={languageFormSchema}
          fields={[
            { name: "code", label: "Code" },
            { name: "name", label: "Name" },
          ]}
          submitLabel="Create language"
          onSubmit={async (values) => {
            await createLanguage.mutateAsync(values);
          }}
        />
      ) : null}

      {languages.isPending ? (
        <p role="status" className="text-sm text-muted-foreground">
          Loading languages…
        </p>
      ) : null}
      {languages.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {(languages.error as ApiClientError).message}
        </p>
      ) : null}
      {languages.data && languages.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">No languages yet.</p>
      ) : null}

      {languages.data && languages.data.length > 0 ? (
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
            {languages.data.map((language) => (
              <tr key={language.id} className="border-b border-border">
                <td className="py-2 font-mono text-xs">{language.code}</td>
                <td className="py-2">{language.name}</td>
                <td className="py-2">{language.status}</td>
                {canArchive ? (
                  <td className="py-2">
                    <button
                      type="button"
                      className="text-destructive underline"
                      onClick={() => void archiveLanguage.mutateAsync(language.id)}
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
