"use client";

import { useForm } from "react-hook-form";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { useExams, useLanguages, useSubjects } from "@/features/academic/hooks/use-academic";
import { useCreateQuestion, useQuestionTemplates } from "@/features/content/hooks/use-content";
import { questionFormSchema, type QuestionForm } from "@/features/content/schemas/content";
import { ApiClientError } from "@/lib/errors";

/**
 * Question create form ({@code specs/12-P3-Content-V1.5.md §102-§104}).
 *
 * <p>React Hook Form holds form state; Zod validates on submit; taxonomy is chosen from controlled
 * Academic selectors rather than typed ids. Math uses the approved {@code \\(...\\)} delimiters.
 */
export function QuestionForm() {
  const templates = useQuestionTemplates();
  const exams = useExams();
  const languages = useLanguages();
  const [examId, setExamId] = useState("");
  const subjects = useSubjects(examId);
  const createQuestion = useCreateQuestion();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Record<string, string>>();

  const submit = handleSubmit(async (raw) => {
    setServerError(null);
    const parsed = questionFormSchema.safeParse(raw);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const path = issue.path.join(".");
        if (path) {
          setError(path, { message: issue.message });
        }
      }
      return;
    }
    try {
      await createQuestion.mutateAsync(parsed.data as QuestionForm);
    } catch (error) {
      setServerError(error instanceof ApiClientError ? error.message : "The request could not be completed.");
    }
  });

  return (
    <form onSubmit={submit} aria-label="Create question" className="space-y-3">
      <Field label="Template" error={errors.templateId?.message}>
        <select aria-label="Template" {...register("templateId")} className="w-full rounded-md border px-3 py-2 text-sm">
          <option value="">Select a template</option>
          {templates.data?.map((template) => (
            <option key={template.id} value={template.id}>
              {template.name}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Exam" error={errors.examId?.message}>
        <select
          aria-label="Exam"
          {...register("examId", { onChange: (event) => setExamId(event.target.value) })}
          className="w-full rounded-md border px-3 py-2 text-sm"
        >
          <option value="">Select an exam</option>
          {exams.data?.map((exam) => (
            <option key={exam.id} value={exam.id}>
              {exam.name}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Subject" error={errors.subjectId?.message}>
        <select aria-label="Subject" {...register("subjectId")} className="w-full rounded-md border px-3 py-2 text-sm">
          <option value="">Select a subject</option>
          {subjects.data?.map((subject) => (
            <option key={subject.id} value={subject.id}>
              {subject.name}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Language" error={errors.languageId?.message}>
        <select aria-label="Language" {...register("languageId")} className="w-full rounded-md border px-3 py-2 text-sm">
          <option value="">Select a language</option>
          {languages.data?.map((language) => (
            <option key={language.id} value={language.id}>
              {language.name}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Title" error={errors.title?.message}>
        <input aria-label="Title" {...register("title")} className="w-full rounded-md border px-3 py-2 text-sm" />
      </Field>

      <Field label="Content" error={errors.content?.message}>
        <textarea
          aria-label="Content"
          rows={4}
          {...register("content")}
          className="w-full rounded-md border px-3 py-2 text-sm"
        />
      </Field>

      <div className="grid grid-cols-3 gap-3">
        <Field label="Marks" error={errors.marks?.message}>
          <input
            aria-label="Marks"
            type="number"
            step="0.25"
            defaultValue="1"
            {...register("marks")}
            className="w-full rounded-md border px-3 py-2 text-sm"
          />
        </Field>
        <Field label="Negative marks" error={errors.negativeMarks?.message}>
          <input
            aria-label="Negative marks"
            type="number"
            step="0.25"
            defaultValue="0"
            {...register("negativeMarks")}
            className="w-full rounded-md border px-3 py-2 text-sm"
          />
        </Field>
        <Field label="Difficulty" error={errors.difficulty?.message}>
          <select aria-label="Difficulty" {...register("difficulty")} className="w-full rounded-md border px-3 py-2 text-sm">
            <option value="EASY">EASY</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HARD">HARD</option>
          </select>
        </Field>
      </div>

      {serverError ? (
        <p role="alert" className="text-sm text-destructive">
          {serverError}
        </p>
      ) : null}

      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Saving…" : "Create question"}
      </Button>
    </form>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <span className="text-sm font-medium">{label}</span>
      {children}
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
