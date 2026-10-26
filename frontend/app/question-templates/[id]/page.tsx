"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { useQuestionTemplate } from "@/features/content/hooks/use-content";

/**
 * Question template detail ({@code specs/12-P3-Content-V1.5.md §87, §113}).
 *
 * <p>Templates are configuration, not exam-specific code: the screen renders whatever the server
 * returned, so a new template needs no frontend change.
 */
export default function QuestionTemplatePage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";
  const template = useQuestionTemplate(id);

  return (
    <AuthenticatedGuard permission="QUESTION_TEMPLATE_VIEW">
      <div className="flex w-full flex-col gap-6">
        <h1 className="text-2xl font-semibold tracking-tight">Question template</h1>
        <p className="font-mono text-xs text-muted-foreground">{id}</p>

        {template.isPending ? (
          <p role="status" className="text-sm text-muted-foreground">
            Loading template…
          </p>
        ) : null}
        {template.isError ? (
          <p role="alert" className="text-sm text-red-600">
            The template could not be loaded.
          </p>
        ) : null}

        {template.data ? (
          <dl className="space-y-2 text-sm">
            <div>
              <dt className="font-medium">Code</dt>
              <dd className="font-mono text-xs">{template.data.code}</dd>
            </div>
            <div>
              <dt className="font-medium">Name</dt>
              <dd>{template.data.name}</dd>
            </div>
            <div>
              <dt className="font-medium">Description</dt>
              <dd>{template.data.description ?? "—"}</dd>
            </div>
            <div>
              <dt className="font-medium">LaTeX parsing</dt>
              <dd>{template.data.requiresLatexParsing ? "Required" : "Not required"}</dd>
            </div>
            <div>
              <dt className="font-medium">Status</dt>
              <dd>{template.data.status}</dd>
            </div>
          </dl>
        ) : null}
      </div>
    </AuthenticatedGuard>
  );
}
