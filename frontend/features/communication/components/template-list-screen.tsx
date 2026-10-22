"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";

import { CommunicationForm } from "@/components/communication/communication-form";
import {
  CommunicationEmpty,
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import {
  describeCommunicationError,
  lifecycleLabel,
} from "@/components/communication/format";
import { createTemplateFormSchema } from "@/features/communication/schemas/communication";
import {
  useArchiveEmailTemplate,
  useCreateEmailTemplate,
  useEmailTemplates,
} from "@/features/communication/hooks/use-communication";
import { useAuth } from "@/lib/auth/auth-context";

/** Email template administration ({@code specs/19-P10-Communication-V1.5.md §110}). */
export function TemplateListScreen() {
  const { can } = useAuth();
  const [draftSearch, setDraftSearch] = useState("");
  const [search, setSearch] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const query = useEmailTemplates(undefined, search || undefined);
  const create = useCreateEmailTemplate();
  const archive = useArchiveEmailTemplate();

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSearch(draftSearch.trim());
  };

  if (query.isPending) {
    return <CommunicationLoading label="Loading templates…" />;
  }

  if (query.isError) {
    return (
      <CommunicationError
        message={describeCommunicationError(query.error, "Templates could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const templates = query.data ?? [];

  return (
    <div className="space-y-8">
      <form onSubmit={submitSearch} className="flex flex-wrap items-end gap-2" aria-label="Search templates">
        <div className="flex-1 space-y-1">
          <label htmlFor="template-search" className="text-sm font-medium">
            Search by code
          </label>
          <input
            id="template-search"
            value={draftSearch}
            onChange={(event) => setDraftSearch(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
        >
          Search
        </button>
      </form>

      <section aria-labelledby="template-list-heading" className="space-y-3">
        <h2 id="template-list-heading" className="text-lg font-semibold">
          Templates
        </h2>
        {templates.length === 0 ? (
          <CommunicationEmpty message="No email templates match this view." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Email templates</caption>
              <thead>
                <tr className="border-b border-slate-200">
                  <th scope="col" className="py-2 pr-3">Code</th>
                  <th scope="col" className="py-2 pr-3">Name</th>
                  <th scope="col" className="py-2 pr-3">Subject</th>
                  <th scope="col" className="py-2 pr-3">Status</th>
                  <th scope="col" className="py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {templates.map((template) => (
                  <tr key={template.id} className="border-b border-slate-100">
                    <th scope="row" className="py-2 pr-3 font-mono text-xs">{template.code}</th>
                    <td className="py-2 pr-3">{template.name}</td>
                    <td className="py-2 pr-3">{template.subject ?? "Not set"}</td>
                    <td className="py-2 pr-3">{lifecycleLabel(template.status)}</td>
                    <td className="py-2">
                      <div className="flex flex-wrap gap-3">
                        <Link
                          href={`/admin/communication/templates/${template.id}`}
                          className="rounded font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        >
                          Edit
                        </Link>
                        <Link
                          href={`/admin/communication/templates/${template.id}/preview`}
                          className="rounded font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        >
                          Preview
                        </Link>
                        {can("COMMUNICATION_TEMPLATE_DELETE") && template.status !== "ARCHIVED" ? (
                          <button
                            type="button"
                            onClick={() => {
                              archive.mutate(template.id);
                            }}
                            className="rounded text-sm font-medium text-red-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
                          >
                            Archive
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {archive.isError ? (
          <CommunicationError
            message={describeCommunicationError(archive.error, "The template could not be archived.")}
          />
        ) : null}
      </section>

      {can("COMMUNICATION_TEMPLATE_WRITE") ? (
        <section aria-labelledby="create-template-heading" className="space-y-3">
          <h2 id="create-template-heading" className="text-lg font-semibold">
            Create a template
          </h2>
          <CommunicationForm
            schema={createTemplateFormSchema}
            submitLabel="Create template"
            resetOnSuccess
            fields={[
              { name: "code", label: "Code (immutable after activation)" },
              { name: "name", label: "Name" },
              { name: "subject", label: "Subject" },
              { name: "body", label: "Body ({{variableName}} placeholders allowed)", type: "textarea" },
            ]}
            onSubmit={async (values) => {
              await create.mutateAsync({ ...values });
              setMessage("Template created.");
            }}
          />
          {message ? (
            <p role="status" className="text-sm text-emerald-700">
              {message}
            </p>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
