"use client";

/**
 * Version-bound explanation and hint editor ({@code specs/12-P3-Content-V1.5.md §39, §40, §106, §107}).
 *
 * <p>Both records belong to exactly one QuestionVersion, so the panel is bound to the version the
 * author selected — it can never edit an explanation independently of a version. A published
 * version is immutable: the whole control is disabled and the reason is stated, rather than letting
 * the author fill in a form the backend will reject.
 */
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { MathTextField } from "@/features/content/components/math-text-field";
import { useExplanation, useHint, useSaveExplanation, useSaveHint } from "@/features/content/hooks/use-content";
import type { VersionTextContent } from "@/features/content/schemas/content";
import { ApiClientError } from "@/lib/errors";

export type VersionTextKind = "explanation" | "hint";

const COPY: Record<VersionTextKind, { heading: string; absent: string; present: string; saved: string }> = {
  explanation: {
    heading: "Explanation",
    absent: "No explanation on this version yet.",
    present: "This version already has an explanation.",
    saved: "Explanation saved.",
  },
  hint: {
    heading: "Hint",
    absent: "This version has no hint.",
    present: "This version already has a hint.",
    saved: "Hint saved.",
  },
};

/** Explanation editor; mounts its own version-bound query ({@code §106}). */
export function ExplanationPanel({ versionId, published }: { versionId: string; published: boolean }) {
  const query = useExplanation(versionId);
  return (
    <VersionTextEditor
      kind="explanation"
      versionId={versionId}
      published={published}
      query={query}
    />
  );
}

/** Hint editor; absence is stated rather than hidden ({@code §107}). */
export function HintPanel({ versionId, published }: { versionId: string; published: boolean }) {
  const query = useHint(versionId);
  return <VersionTextEditor kind="hint" versionId={versionId} published={published} query={query} />;
}

interface QueryLike {
  data: VersionTextContent | null | undefined;
  isPending: boolean;
  isError: boolean;
}

function VersionTextEditor({
  kind,
  versionId,
  published,
  query,
}: {
  kind: VersionTextKind;
  versionId: string;
  published: boolean;
  query: QueryLike;
}) {
  const copy = COPY[kind];
  const existing = Boolean(query.data);
  const explanationSave = useSaveExplanation(versionId, existing);
  const hintSave = useSaveHint(versionId, existing);
  const save = kind === "explanation" ? explanationSave : hintSave;

  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    setDraft(query.data?.content ?? "");
    setStatus(null);
  }, [query.data, versionId]);

  const disabled = published || query.isPending;

  async function submit() {
    setError(null);
    setStatus(null);
    if (draft.trim().length === 0) {
      setError("Content is required.");
      return;
    }
    try {
      await save.mutateAsync(draft);
      setStatus(copy.saved);
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The request could not be completed.");
    }
  }

  return (
    <section aria-label={copy.heading} className="space-y-2">
      <h2 className="text-lg font-semibold">{copy.heading}</h2>

      {published ? (
        <p role="note" className="text-sm text-amber-700">
          This version is published, so its {kind} cannot be changed. Create a new version to author a
          different {kind}.
        </p>
      ) : null}

      <p className="text-xs text-muted-foreground">
        {query.isPending ? `Loading ${kind}…` : query.isError ? `The ${kind} could not be loaded.` : existing ? copy.present : copy.absent}
      </p>

      <MathTextField label={copy.heading} value={draft} onChange={setDraft} disabled={disabled} rows={3} />

      <div className="flex items-center gap-2">
        <Button disabled={disabled || save.isPending} aria-label={`Save ${kind}`} onClick={() => void submit()}>
          {save.isPending ? "Saving…" : `Save ${kind}`}
        </Button>
        {published ? (
          <span className="text-xs text-muted-foreground">
            {copy.heading} editing is disabled while the version is published.
          </span>
        ) : null}
      </div>

      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
      {status ? (
        <p role="status" className="text-sm text-muted-foreground">
          {status}
        </p>
      ) : null}
    </section>
  );
}
