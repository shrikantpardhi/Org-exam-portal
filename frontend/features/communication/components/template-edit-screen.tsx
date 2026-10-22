"use client";

import Link from "next/link";
import { useState } from "react";

import { CommunicationForm } from "@/components/communication/communication-form";
import {
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import {
  describeCommunicationError,
  lifecycleLabel,
} from "@/components/communication/format";
import { templateEditFormSchema } from "@/features/communication/schemas/communication";
import {
  useArchiveEmailTemplate,
  useEmailTemplate,
  useUpdateEmailTemplate,
} from "@/features/communication/hooks/use-communication";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Template editing ({@code specs/19-P10-Communication-V1.5.md §109-§112}).
 *
 * <p>The code is shown read-only because the business contract makes it a stable integration key
 * after activation. The form edits name, subject and body only.
 */
export function TemplateEditScreen({ templateId }: { templateId: string }) {
  const { can } = useAuth();
  const query = useEmailTemplate(templateId);
  const update = useUpdateEmailTemplate(templateId);
  const archive = useArchiveEmailTemplate();
  const [message, setMessage] = useState<string | null>(null);

  if (templateId.length === 0) {
    return <CommunicationError message="No template was selected." />;
  }

  if (query.isPending) {
    return <CommunicationLoading label="Loading template…" />;
  }

  if (query.isError) {
    return (
      <CommunicationError
        message={describeCommunicationError(query.error, "The template could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const template = query.data;
  if (!template) {
    return <CommunicationLoading label="Loading template…" />;
  }

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <p className="font-mono text-xs text-slate-500">{template.code}</p>
        <h2 className="text-xl font-semibold tracking-tight">{template.name}</h2>
        <p className="text-sm text-slate-500">Status: {lifecycleLabel(template.status)}</p>
      </header>

      <nav aria-label="Template actions" className="flex flex-wrap gap-4 text-sm">
        <Link
          href={`/admin/communication/templates/${template.id}/preview`}
          className="rounded font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Preview template
        </Link>
        <Link
          href="/admin/communication/templates"
          className="rounded font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          All templates
        </Link>
      </nav>

      {!can("COMMUNICATION_TEMPLATE_WRITE") ? (
        <p role="alert" className="text-sm text-red-600">
          You do not have permission to edit templates.
        </p>
      ) : null}

      <CommunicationForm
        schema={templateEditFormSchema}
        submitLabel="Save template"
        disabled={!can("COMMUNICATION_TEMPLATE_WRITE")}
        defaultValues={{
          name: template.name,
          subject: template.subject ?? "",
          body: template.body ?? "",
        }}
        fields={[
          { name: "name", label: "Name" },
          { name: "subject", label: "Subject" },
          { name: "body", label: "Body ({{variableName}} placeholders allowed)", type: "textarea" },
        ]}
        onSubmit={async (values) => {
          await update.mutateAsync({
            name: values.name,
            subject: values.subject ?? "",
            body: values.body ?? "",
          });
          setMessage("Template saved.");
        }}
      />

      {message ? (
        <p role="status" className="text-sm text-emerald-700">
          {message}
        </p>
      ) : null}

      {update.isError ? (
        <CommunicationError
          message={describeCommunicationError(update.error, "The template could not be saved.")}
        />
      ) : null}

      {can("COMMUNICATION_TEMPLATE_DELETE") && template.status !== "ARCHIVED" ? (
        <button
          type="button"
          onClick={() => {
            archive.mutate(template.id);
          }}
          className="rounded border border-red-300 bg-white px-4 py-2 text-sm font-medium text-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
        >
          Archive template
        </button>
      ) : null}
      {archive.isError ? (
        <CommunicationError
          message={describeCommunicationError(archive.error, "The template could not be archived.")}
        />
      ) : null}
    </div>
  );
}
