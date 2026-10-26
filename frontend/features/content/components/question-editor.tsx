"use client";

/**
 * Full question editor — create mode ({@code specs/12-P3-Content-V1.5.md §101-§104, §109, §113}).
 *
 * <p>Core fields, taxonomy, language and the options draft are authored here; taxonomy values come
 * from the Academic masters so no exam name is ever hard-coded. Options are collected as a local
 * draft because an option belongs to a QuestionVersion, which only exists once the question has
 * been created — the editor therefore saves the question first and then continues on the question
 * workspace, where the version-bound panels (options, assets, explanation, hint, previous exams)
 * become available.
 */
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { useLanguages } from "@/features/academic/hooks/use-academic";
import { MathTextField } from "@/features/content/components/math-text-field";
import { OptionsEditor, type AnswerModel } from "@/features/content/components/options-editor";
import { TaxonomySelector, type TaxonomyValue, EMPTY_TAXONOMY } from "@/features/content/components/taxonomy-selector";
import { useCreateQuestion, useQuestionTemplates } from "@/features/content/hooks/use-content";
import { addOption } from "@/features/content/api/content-api";
import { buildCreateQuestionPayload, buildOptionPayloads } from "@/features/content/lib/payload";
import { optionProblems, type OptionRow } from "@/features/content/lib/payload";
import type { OptionDraft } from "@/features/content/schemas/content";
import { ApiClientError } from "@/lib/errors";

const INITIAL_OPTIONS: OptionDraft[] = [
  { optionKey: "A", content: "", displayOrder: 0, isCorrect: true },
  { optionKey: "B", content: "", displayOrder: 1, isCorrect: false },
];

export function QuestionEditor() {
  const router = useRouter();
  const templates = useQuestionTemplates();
  const languages = useLanguages();
  const createQuestion = useCreateQuestion();

  const [templateId, setTemplateId] = useState("");
  const [taxonomy, setTaxonomy] = useState<TaxonomyValue>(EMPTY_TAXONOMY);
  const [languageId, setLanguageId] = useState("");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [marks, setMarks] = useState("1");
  const [negativeMarks, setNegativeMarks] = useState("0");
  const [difficulty, setDifficulty] = useState("MEDIUM");
  const [options, setOptions] = useState<OptionDraft[]>(INITIAL_OPTIONS);
  const [answerModel, setAnswerModel] = useState<AnswerModel>("SINGLE_OPTION");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);

  const problems = optionProblems(options as OptionRow[], answerModel);

  async function submit() {
    setServerError(null);
    const nextErrors: Record<string, string> = {};
    if (!templateId) nextErrors.templateId = "Template is required.";
    if (!taxonomy.examId) nextErrors.examId = "Exam is required.";
    if (!taxonomy.subjectId) nextErrors.subjectId = "Subject is required.";
    if (!languageId) nextErrors.languageId = "Language is required.";
    if (content.trim().length === 0) nextErrors.content = "Content is required.";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    try {
      const payload = buildCreateQuestionPayload({
        templateId,
        ...taxonomy,
        languageId,
        title,
        content,
        marks,
        negativeMarks,
        difficulty,
      });
      const created = await createQuestion.mutateAsync(payload);
      const firstVersion = created.versions[0];
      // Options belong to a version, so they are written after the initial draft version exists.
      const filled = options.filter((row) => row.content.trim().length > 0);
      if (firstVersion && filled.length > 0) {
        for (const option of buildOptionPayloads(filled)) {
          await addOption(created.id, firstVersion.id, option);
        }
      }
      router.push(`/questions/${created.id}`);
    } catch (failure) {
      if (failure instanceof ApiClientError) {
        setServerError(failure.message);
        return;
      }
      setServerError("The payload could not be assembled. Check the marks and taxonomy values.");
    }
  }

  return (
    <form
      aria-label="Question editor"
      className="space-y-6"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <section aria-label="Core fields" className="space-y-3">
        <h2 className="text-lg font-semibold">Question</h2>

        <div className="space-y-1">
          <label htmlFor="template" className="text-sm font-medium">
            Template
          </label>
          <select
            id="template"
            aria-label="Template"
            value={templateId}
            onChange={(event) => setTemplateId(event.target.value)}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          >
            <option value="">Select a template</option>
            {(templates.data ?? []).map((template) => (
              <option key={template.id} value={template.id}>
                {template.name}
              </option>
            ))}
          </select>
          {errors.templateId ? (
            <p role="alert" className="text-sm text-destructive">
              {errors.templateId}
            </p>
          ) : null}
        </div>

        <TaxonomySelector value={taxonomy} onChange={setTaxonomy} errors={errors} />

        <div className="space-y-1">
          <label htmlFor="language" className="text-sm font-medium">
            Language
          </label>
          <select
            id="language"
            aria-label="Language"
            value={languageId}
            onChange={(event) => setLanguageId(event.target.value)}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          >
            <option value="">Select a language</option>
            {(languages.data ?? []).map((language) => (
              <option key={language.id} value={language.id}>
                {language.name}
              </option>
            ))}
          </select>
          {errors.languageId ? (
            <p role="alert" className="text-sm text-destructive">
              {errors.languageId}
            </p>
          ) : null}
        </div>

        <div className="space-y-1">
          <label htmlFor="title" className="text-sm font-medium">
            Title
          </label>
          <input
            id="title"
            aria-label="Title"
            value={title}
            maxLength={255}
            onChange={(event) => setTitle(event.target.value)}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>

        <MathTextField label="Content" value={content} onChange={setContent} rows={5} />
        {errors.content ? (
          <p role="alert" className="text-sm text-destructive">
            {errors.content}
          </p>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-3">
          <div className="space-y-1">
            <label htmlFor="marks" className="text-sm font-medium">
              Marks
            </label>
            <input
              id="marks"
              aria-label="Marks"
              type="number"
              step="0.25"
              min="0"
              value={marks}
              onChange={(event) => setMarks(event.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="negativeMarks" className="text-sm font-medium">
              Negative marks
            </label>
            <input
              id="negativeMarks"
              aria-label="Negative marks"
              type="number"
              step="0.25"
              min="0"
              value={negativeMarks}
              onChange={(event) => setNegativeMarks(event.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="difficulty" className="text-sm font-medium">
              Difficulty
            </label>
            <select
              id="difficulty"
              aria-label="Difficulty"
              value={difficulty}
              onChange={(event) => setDifficulty(event.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            >
              <option value="EASY">EASY</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HARD">HARD</option>
            </select>
          </div>
        </div>
      </section>

      <section aria-label="Answer mode" className="space-y-1">
        <h2 className="text-lg font-semibold">Answer mode</h2>
        <p className="text-xs text-muted-foreground">
          The template configuration remains authoritative for how correctness is validated.
        </p>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="answer-model"
            aria-label="Single correct option"
            checked={answerModel === "SINGLE_OPTION"}
            onChange={() => setAnswerModel("SINGLE_OPTION")}
          />
          Single correct option
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="answer-model"
            aria-label="Multiple correct options"
            checked={answerModel === "MULTIPLE_OPTIONS"}
            onChange={() => setAnswerModel("MULTIPLE_OPTIONS")}
          />
          Multiple correct options
        </label>
      </section>

      <OptionsEditor
        rows={options}
        onChange={setOptions}
        answerModel={answerModel}
        problems={problems}
      />

      <p role="note" className="text-sm text-muted-foreground">
        Saving creates the question and its first draft version. Options, assets, explanation, hint,
        tags and previous exam history are version-bound, so they are authored on the question page
        once the version exists.
      </p>

      {serverError ? (
        <p role="alert" className="text-sm text-destructive">
          {serverError}
        </p>
      ) : null}

      <Button type="submit" disabled={createQuestion.isPending}>
        {createQuestion.isPending ? "Saving…" : "Create question"}
      </Button>
    </form>
  );
}
