"use client";

import Link from "next/link";

import {
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import { describeCommunicationError } from "@/components/communication/format";
import { TemplatePreview } from "@/features/communication/components/template-preview";
import { useEmailTemplate } from "@/features/communication/hooks/use-communication";

/** Template preview ({@code specs/19-P10-Communication-V1.5.md §70-§72, §110}). */
export function TemplatePreviewScreen({ templateId }: { templateId: string }) {
  const query = useEmailTemplate(templateId);

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

  if (!query.data) {
    return <CommunicationLoading label="Loading template…" />;
  }

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <p className="font-mono text-xs text-muted-foreground">{query.data.code}</p>
        <h2 className="text-xl font-semibold tracking-tight">Preview: {query.data.name}</h2>
      </header>
      <TemplatePreview template={query.data} />
      <Link
        href={`/admin/communication/templates/${query.data.id}`}
        className="rounded text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Back to template
      </Link>
    </div>
  );
}
