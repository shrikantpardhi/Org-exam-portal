"use client";

import { useParams } from "next/navigation";

import { VersionPreview } from "@/features/content/components/version-preview";
import { useOptions, useVersion } from "@/features/content/hooks/use-content";

/** Version detail with canonical preview and options ({@code §56, §103, §168}). */
export default function QuestionVersionPage() {
  const params = useParams<{ id: string; versionId: string }>();
  const questionId = params?.id ?? "";
  const versionId = params?.versionId ?? "";
  const version = useVersion(questionId, versionId);
  const options = useOptions(questionId, versionId);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">
        Version {version.data?.version ?? ""} — preview
      </h1>

      {version.isPending ? <p role="status">Loading…</p> : null}
      {version.isError ? <p role="alert">The version could not be loaded.</p> : null}

      {version.data ? (
        <>
          <p className="text-xs text-muted-foreground">Content format: {version.data.contentFormat}</p>
          <VersionPreview html={version.data.renderedContent} />

          <section className="space-y-2">
            <h2 className="text-lg font-semibold">Options</h2>
            {options.data && options.data.length === 0 ? (
              <p className="text-sm text-muted-foreground">No options.</p>
            ) : null}
            <ul className="space-y-1 text-sm">
              {options.data?.map((option) => (
                <li key={option.id}>
                  <span className="font-mono">{option.optionKey}</span>: {option.content}
                  {option.isCorrect ? <span className="ml-2 text-success">(correct)</span> : null}
                </li>
              ))}
            </ul>
          </section>
        </>
      ) : null}
    </div>
  );
}
