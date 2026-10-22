"use client";

/**
 * Question workspace: version-bound authoring surface ({@code specs/12-P3-Content-V1.5.md §101-§110}).
 *
 * <p>Everything that belongs to a version — content, options, assets, explanation, hint — is bound
 * to the version the author selected, so no panel can edit across versions. Draft, review,
 * approved, rejected and published are rendered from the server's review state, never inferred
 * from local form state. A published version is immutable: editing it is not offered, and the
 * author is routed to create a new version whose fields are copied from the published one.
 */
import Link from "next/link";
import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { AssetPanel } from "@/features/content/components/asset-panel";
import { MathTextField } from "@/features/content/components/math-text-field";
import { PreviousExamEditor } from "@/features/content/components/previous-exam-editor";
import { ReviewActions } from "@/features/content/components/review-actions";
import { TagMultiSelect } from "@/features/content/components/tag-multi-select";
import { VersionHistory } from "@/features/content/components/version-history";
import { ExplanationPanel, HintPanel } from "@/features/content/components/version-text-panel";
import { getVersion } from "@/features/content/api/content-api";
import {
  useCreateVersion,
  useOptions,
  useQuestion,
  useUpdateQuestion,
  useVersions,
  useVersion,
} from "@/features/content/hooks/use-content";
import { buildMetadataUpdate } from "@/features/content/lib/payload";
import { ApiClientError } from "@/lib/errors";

export function QuestionWorkspace({ questionId }: { questionId: string }) {
  const question = useQuestion(questionId);
  const versions = useVersions(questionId);
  const createVersion = useCreateVersion(questionId);
  const updateQuestion = useUpdateQuestion(questionId);

  const [pickedVersionId, setPickedVersionId] = useState("");
  const [tags, setTags] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  /**
   * Content for the next version.
   *
   * <p>The frozen contract has no version-content update route: a version's content is supplied
   * when the version is created ({@code §86}). The composer is therefore the authoring surface for
   * the next version, and it is prefilled from the selected version so an author edits a copy.
   */
  const [nextContent, setNextContent] = useState("");
  const [nextContentVersionId, setNextContentVersionId] = useState("");
  const contentField = useRef<HTMLTextAreaElement | null>(null);

  const rows = versions.data ?? [];
  const effectiveVersionId = pickedVersionId.length > 0 ? pickedVersionId : selectedVersionIdOf(rows, "");
  const selected = rows.find((row) => row.id === effectiveVersionId);
  const effectiveTags = tags ?? question.data?.tagIds ?? [];

  const options = useOptions(questionId, effectiveVersionId);
  const version = useVersion(questionId, effectiveVersionId);

  /**
   * Copies the source version into a new draft version.
   *
   * <p>Published versions are immutable, so the only correct action is a new version. The new
   * version's body is taken from the source version's own server values rather than from this
   * screen, so a stale form cannot seed the new draft with wrong content.
   */
  async function handleCreateVersion(baseVersionId: string) {
    setError(null);
    setStatus(null);
    try {
      const base = await getVersion(questionId, baseVersionId);
      if (!base.languageId) {
        setError("The source version has no language, so a new version cannot be created from it.");
        return;
      }
      const created = await createVersion.mutateAsync({
        title: base.title ?? undefined,
        content: base.content ?? "",
        marks: Number(base.marks ?? 0),
        negativeMarks: base.negativeMarks === null || base.negativeMarks === undefined
          ? undefined
          : Number(base.negativeMarks),
        difficulty: base.difficulty ?? "MEDIUM",
        languageId: base.languageId,
      });
      setPickedVersionId(created.id);
      setStatus(`Version ${created.version} created as a new draft from version ${base.version}.`);
    } catch (failure) {
      setError(
        failure instanceof ApiClientError
          ? failure.message
          : "A new version could not be created from the selected version.",
      );
    }
  }

  async function handleSaveTags() {
    setError(null);
    setStatus(null);
    try {
      await updateQuestion.mutateAsync(
        buildMetadataUpdate(
          {
            examId: question.data?.examId ?? "",
            subjectId: question.data?.subjectId ?? "",
            chapterId: question.data?.chapterId ?? "",
            topicId: question.data?.topicId ?? "",
            subTopicId: question.data?.subTopicId ?? "",
          },
          effectiveTags,
        ),
      );
      setStatus("Tags saved on the question.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The tags could not be saved.");
    }
  }

  return (
    <div className="space-y-8">
      <VersionHistory
        versions={rows}
        selectedVersionId={effectiveVersionId}
        onSelect={setPickedVersionId}
        onCreateNewVersion={(versionId) => void handleCreateVersion(versionId)}
        pending={versions.isPending}
        error={versions.isError ? "The version history could not be loaded." : null}
      />

      {selected ? (
        <>
          <section aria-label="Selected version" className="space-y-2">
            <h2 className="text-lg font-semibold">Selected version</h2>
            <p className="text-sm text-slate-600">
              Version {selected.version} · review state {selected.reviewStatus} ·{" "}
              {selected.published ? "published" : "not published"} · marks {selected.marks ?? "—"} ·
              negative marks {selected.negativeMarks ?? "—"} · difficulty{" "}
              {selected.difficulty ?? "—"}
            </p>
            <Link href={`/questions/${questionId}/versions/${selected.id}`} className="text-sm underline">
              Open version preview
            </Link>
            <ReviewActions
              questionId={questionId}
              versionId={selected.id}
              reviewStatus={selected.reviewStatus}
              published={selected.published}
            />
          </section>

          <section aria-label="Version options" className="space-y-2">
            <h2 className="text-lg font-semibold">Options</h2>
            {options.isPending ? (
              <p role="status" className="text-sm text-slate-500">
                Loading options…
              </p>
            ) : null}
            {options.data && options.data.length === 0 ? (
              <p className="text-sm text-slate-500">This version has no options.</p>
            ) : null}
            {options.data && options.data.length > 0 ? (
              <table aria-label="Version option list" className="w-full text-sm">
                <thead>
                  <tr className="text-left">
                    <th scope="col">Key</th>
                    <th scope="col">Content</th>
                    <th scope="col">Display order</th>
                    <th scope="col">Correct</th>
                  </tr>
                </thead>
                <tbody>
                  {options.data.map((option) => (
                    <tr key={option.id}>
                      <td>{option.optionKey}</td>
                      <td>{option.content ?? "—"}</td>
                      <td>{option.displayOrder}</td>
                      <td>{option.isCorrect ? "Yes" : "No"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : null}
            {selected.published ? (
              <p role="note" className="text-sm text-amber-700">
                This version is published, so its options are immutable. Create a new version to
                change them.
              </p>
            ) : null}
          </section>

          <AssetPanel
            versionId={selected.id}
            published={selected.published}
            onInsertReference={(shortcode) => {
              // Insert at the composer's caret, not into the stored version: content is only
              // written when a version is created.
              const element = contentField.current;
              const start = element?.selectionStart ?? nextContent.length;
              const end = element?.selectionEnd ?? nextContent.length;
              setNextContent(`${nextContent.slice(0, start)}${shortcode}${nextContent.slice(end)}`);
            }}
          />
          <ExplanationPanel versionId={selected.id} published={selected.published} />
          <HintPanel versionId={selected.id} published={selected.published} />
        </>
      ) : null}

      {selected ? (
        <section aria-label="Next version content" className="space-y-2">
          <h2 className="text-lg font-semibold">Content for the next version</h2>
          <p className="text-xs text-slate-500">
            A version&apos;s content is fixed when the version is created, so edits here apply to the
            next version. Create a new version to publish them.
          </p>
          <MathTextField
            label="Next version content"
            value={nextContentVersionId === selected.id ? nextContent : (version.data?.content ?? "")}
            onChange={(value) => {
              setNextContentVersionId(selected.id);
              setNextContent(value);
            }}
            rows={6}
            disabled={selected.published}
            textareaRef={contentField}
          />
          {selected.published ? (
            <p role="note" className="text-sm text-amber-700">
              Version {selected.version} is published and immutable. Use “Create a new version” in the
              history above to author replacement content.
            </p>
          ) : (
            <p className="text-xs text-slate-500">
              This draft version&apos;s stored content cannot be rewritten through the frozen contract;
              create a new version to save changes.
            </p>
          )}
        </section>
      ) : null}

      {question.isError ? (
        <p role="alert" className="text-sm text-red-600">
          The question could not be loaded.
        </p>
      ) : null}

      <TagMultiSelect selected={effectiveTags} onChange={setTags} />
      <div>
        <Button
          aria-label="Save tags"
          onClick={() => void handleSaveTags()}
          disabled={updateQuestion.isPending}
        >
          {updateQuestion.isPending ? "Saving…" : "Save tags"}
        </Button>
      </div>

      <PreviousExamEditor questionId={questionId} />

      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
      {status ? (
        <p role="status" className="text-sm text-slate-600">
          {status}
        </p>
      ) : null}
    </div>
  );
}

/** Defaults to the newest unpublished version, else the newest version. */
function selectedVersionIdOf(
  rows: { id: string; published: boolean }[] | undefined,
  fallback: string,
): string {
  if (!rows || rows.length === 0) {
    return fallback;
  }
  const draft = rows.find((row) => !row.published);
  return draft?.id ?? rows[rows.length - 1]?.id ?? fallback;
}
