"use client";

/**
 * Question picker ({@code specs/13-P4-Assessment-V1.5.md §25, §35, §101}).
 *
 * <p>Search, filters, and pagination all run on the server; the browser never downloads the question
 * bank. The list returns a question id, not the version id an assessment needs, so choosing a
 * question loads its versions and the operator picks the exact version. Only a published version may
 * be added — an assessment must bind published content (§35).
 */
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { useExams, useLanguages, useSubjects } from "@/features/academic/hooks/use-academic";
import { useQuestionSearch, useQuestionVersions } from "@/features/assessment/hooks/use-assessment";
import type { Difficulty, VersionListItem } from "@/features/assessment/schemas/assessment-builder";

const fieldClass =
  "w-full rounded-md border border-input px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

const PAGE_SIZE = 10;

export interface QuestionPickerSelection {
  questionId: string;
  questionVersionId: string;
  marks: number;
  negativeMarks: number;
}

/** Picks the newest published version, or null when nothing is published. */
export function defaultPublishedVersion(versions: readonly VersionListItem[]): VersionListItem | null {
  const published = versions.filter((version) => version.published);
  if (published.length === 0) {
    return null;
  }
  return published.reduce((best, candidate) => (candidate.version > best.version ? candidate : best));
}

export function QuestionPicker({
  onAdd,
  disabled = false,
  addLabel = "Add",
}: {
  onAdd: (selection: QuestionPickerSelection) => void;
  disabled?: boolean;
  addLabel?: string;
}) {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [difficulty, setDifficulty] = useState<Difficulty | "">("");
  const [examId, setExamId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [languageId, setLanguageId] = useState("");
  const [selectedQuestionId, setSelectedQuestionId] = useState<string | null>(null);
  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null);

  const exams = useExams();
  const subjects = useSubjects(examId);
  const languages = useLanguages();

  const params = useMemo(
    () => ({
      page,
      size: PAGE_SIZE,
      search: search.length > 0 ? search : undefined,
      difficulty: difficulty.length > 0 ? difficulty : undefined,
      examId: examId.length > 0 ? examId : undefined,
      subjectId: subjectId.length > 0 ? subjectId : undefined,
      languageId: languageId.length > 0 ? languageId : undefined,
    }),
    [page, search, difficulty, examId, subjectId, languageId],
  );

  const questions = useQuestionSearch(params);
  const versions = useQuestionVersions(selectedQuestionId);

  const selectedVersion = useMemo(() => {
    const list = versions.data ?? [];
    if (selectedVersionId === null) {
      return defaultPublishedVersion(list);
    }
    return list.find((version) => version.id === selectedVersionId) ?? null;
  }, [versions.data, selectedVersionId]);

  function submitSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setSearch(searchInput.trim());
  }

  function chooseQuestion(questionId: string) {
    setSelectedQuestionId(questionId);
    setSelectedVersionId(null);
  }

  const canAdd = selectedQuestionId !== null && selectedVersion !== null && selectedVersion.published;

  return (
    <section aria-labelledby="question-picker-heading" className="space-y-4">
      <div>
        <h2 id="question-picker-heading" className="text-lg font-semibold tracking-tight">
          Question picker
        </h2>
        <p className="text-sm text-muted-foreground">
          Search runs on the server with pagination; the question bank is never downloaded in bulk.
        </p>
      </div>

      <form onSubmit={submitSearch} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div className="lg:col-span-3">
          <label htmlFor="question-search" className="text-sm font-medium">
            Search
          </label>
          <div className="flex gap-2">
            <input
              id="question-search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Question text or keyword"
              className={fieldClass}
            />
            <Button type="submit" variant="outline">
              Search
            </Button>
          </div>
        </div>

        <div>
          <label htmlFor="question-difficulty" className="text-sm font-medium">
            Difficulty
          </label>
          <select
            id="question-difficulty"
            value={difficulty}
            onChange={(event) => {
              setPage(0);
              setDifficulty(event.target.value as Difficulty | "");
            }}
            className={fieldClass}
          >
            <option value="">Any difficulty</option>
            <option value="EASY">EASY</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HARD">HARD</option>
          </select>
        </div>

        <div>
          <label htmlFor="question-exam" className="text-sm font-medium">
            Exam
          </label>
          <select
            id="question-exam"
            value={examId}
            onChange={(event) => {
              setPage(0);
              setExamId(event.target.value);
              setSubjectId("");
            }}
            className={fieldClass}
          >
            <option value="">Any exam</option>
            {(exams.data ?? []).map((exam) => (
              <option key={exam.id} value={exam.id}>
                {exam.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="question-subject" className="text-sm font-medium">
            Subject
          </label>
          <select
            id="question-subject"
            value={subjectId}
            onChange={(event) => {
              setPage(0);
              setSubjectId(event.target.value);
            }}
            disabled={examId.length === 0}
            className={fieldClass}
          >
            <option value="">Any subject</option>
            {(subjects.data ?? []).map((subject) => (
              <option key={subject.id} value={subject.id}>
                {subject.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="question-language" className="text-sm font-medium">
            Language
          </label>
          <select
            id="question-language"
            value={languageId}
            onChange={(event) => {
              setPage(0);
              setLanguageId(event.target.value);
            }}
            className={fieldClass}
          >
            <option value="">Any language</option>
            {(languages.data ?? []).map((language) => (
              <option key={language.id} value={language.id}>
                {language.name}
              </option>
            ))}
          </select>
        </div>
      </form>

      {questions.isPending ? <p role="status">Loading questions…</p> : null}
      {questions.isError ? (
        <p role="alert" className="text-sm text-destructive">
          Questions could not be loaded.
        </p>
      ) : null}
      {questions.data && questions.data.length === 0 ? (
        <p role="note" className="text-sm text-muted-foreground">
          No question matched this search.
        </p>
      ) : null}

      {questions.data && questions.data.length > 0 ? (
        <div className="overflow-x-auto" role="region" aria-label="Scrollable table" tabIndex={0}>
<table className="w-full min-w-[640px] text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-2">Question</th>
              <th className="py-2">Difficulty</th>
              <th className="py-2">Latest version</th>
              <th className="py-2">Published</th>
              <th className="py-2">Select</th>
            </tr>
          </thead>
          <tbody>
            {questions.data.map((question) => (
              <tr key={question.id} className="border-b border-border">
                <td className="py-2 font-mono text-xs">{question.id}</td>
                <td className="py-2">{question.difficulty ?? "—"}</td>
                <td className="py-2">{question.latestVersion ?? "—"}</td>
                <td className="py-2">{question.published ? "Yes" : "No"}</td>
                <td className="py-2">
                  <Button
                    size="sm"
                    variant={selectedQuestionId === question.id ? "default" : "outline"}
                    aria-pressed={selectedQuestionId === question.id}
                    onClick={() => chooseQuestion(question.id)}
                    disabled={disabled}
                  >
                    {selectedQuestionId === question.id ? "Selected" : "Select"}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
</div>
      ) : null}

      <div className="flex items-center gap-3">
        <Button
          variant="outline"
          size="sm"
          onClick={() => setPage((current) => Math.max(0, current - 1))}
          disabled={page === 0}
        >
          Previous page
        </Button>
        <span className="text-sm text-muted-foreground">Page {page + 1}</span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setPage((current) => current + 1)}
          disabled={(questions.data?.length ?? 0) < PAGE_SIZE}
        >
          Next page
        </Button>
      </div>

      {selectedQuestionId ? (
        <div className="space-y-3 rounded-lg border border-border p-4">
          <h3 className="text-sm font-semibold">Choose the exact question version</h3>
          <p className="text-xs text-muted-foreground">
            An assessment binds an exact published QuestionVersion, never “the latest version”.
          </p>
          {versions.isPending ? <p role="status">Loading versions…</p> : null}
          {versions.data && versions.data.length === 0 ? (
            <p role="note" className="text-sm text-muted-foreground">
              This question has no versions.
            </p>
          ) : null}
          <ul className="space-y-2">
            {(versions.data ?? []).map((version) => (
              <li key={version.id} className="flex flex-wrap items-center gap-3 text-sm">
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="question-version"
                    value={version.id}
                    checked={selectedVersion?.id === version.id}
                    disabled={!version.published}
                    onChange={() => setSelectedVersionId(version.id)}
                  />
                  Version {version.version}
                </label>
                <span className="text-muted-foreground">{version.published ? "Published" : "Not published"}</span>
                <span className="text-muted-foreground">
                  {version.marks ?? "—"} marks / {version.negativeMarks ?? "—"} negative
                </span>
              </li>
            ))}
          </ul>

          <Button
            disabled={disabled || !canAdd}
            onClick={() => {
              if (!selectedQuestionId || !selectedVersion) {
                return;
              }
              onAdd({
                questionId: selectedQuestionId,
                questionVersionId: selectedVersion.id,
                marks: selectedVersion.marks ?? 0,
                negativeMarks: selectedVersion.negativeMarks ?? 0,
              });
            }}
          >
            {addLabel}
          </Button>
          {!canAdd ? (
            <p className="text-xs text-muted-foreground">Select a published version to enable adding it.</p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
