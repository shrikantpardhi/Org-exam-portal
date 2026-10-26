"use client";

import { useQuestionTemplates } from "@/features/content/hooks/use-content";
import type { ApiClientError } from "@/lib/errors";

export default function QuestionTemplatesPage() {
  const templates = useQuestionTemplates();

  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Question templates</h1>

      {templates.isPending ? <p role="status">Loading…</p> : null}
      {templates.isError ? (
        <p role="alert">{(templates.error as ApiClientError).message}</p>
      ) : null}

      <ul className="space-y-2 text-sm">
        {templates.data?.map((template) => (
          <li key={template.id} className="rounded border border-border p-3">
            <span className="font-mono text-xs">{template.code}</span> — {template.name}
            {template.requiresLatexParsing ? <span className="ml-2 text-muted-foreground">(LaTeX)</span> : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
