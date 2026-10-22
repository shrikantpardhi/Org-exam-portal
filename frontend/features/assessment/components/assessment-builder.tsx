"use client";

/**
 * Assessment builder screen ({@code specs/13-P4-Assessment-V1.5.md §100-§108}).
 *
 * <p>One screen for the whole authoring workflow. The section list used everywhere here is derived
 * from the bound BlueprintVersion configuration; the screen holds no section catalog of its own and
 * never branches on an exam name.
 */
import { useMemo, useState } from "react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { AssessmentQuestionTable } from "@/features/assessment/components/assessment-question-table";
import { PublishConfirmation } from "@/features/assessment/components/publish-confirmation";
import { QuestionPicker, type QuestionPickerSelection } from "@/features/assessment/components/question-picker";
import { RuleBuilder } from "@/features/assessment/components/rule-builder";
import { ValidationSummary } from "@/features/assessment/components/validation-summary";
import {
  useAddAssessmentQuestion,
  useAssessment,
  useAssessmentQuestions,
  useAssessmentVersion,
  useBlueprintVersion,
  usePublishAssessmentVersion,
  useRemoveAssessmentQuestion,
  useReorderAssessmentQuestions,
  useSelectionRules,
  useUpdateAssessmentQuestion,
  useValidateAssessmentVersion,
} from "@/features/assessment/hooks/use-assessment";
import { useQuestionTemplates } from "@/features/content/hooks/use-content";
import type { ValidationResult } from "@/features/assessment/schemas/assessment";
import type { AssessmentQuestion } from "@/features/assessment/schemas/assessment-builder";
import { ApiClientError } from "@/lib/errors";

/** Ascending order rewrite used before every reorder request (§43). */
export function withSequentialOrder(questions: readonly AssessmentQuestion[]): AssessmentQuestion[] {
  return questions.map((question, index) => ({ ...question, displayOrder: index + 1 }));
}

export function AssessmentBuilder({
  assessmentId,
  versionId,
}: {
  assessmentId: string;
  versionId: string;
}) {
  const assessment = useAssessment(assessmentId);
  const version = useAssessmentVersion(versionId);
  const questions = useAssessmentQuestions(versionId);
  const rules = useSelectionRules(versionId);
  const templates = useQuestionTemplates();
  const addQuestion = useAddAssessmentQuestion(versionId);
  const updateQuestion = useUpdateAssessmentQuestion(versionId);
  const removeQuestion = useRemoveAssessmentQuestion(versionId);
  const reorder = useReorderAssessmentQuestions(versionId);
  const validate = useValidateAssessmentVersion();
  const publish = usePublishAssessmentVersion(versionId);

  const blueprintVersion = useBlueprintVersion(
    assessment.data?.id ?? "",
    version.data?.blueprintVersionId ?? "",
  );

  const [validation, setValidation] = useState<ValidationResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const sectionNames = useMemo(
    () => (blueprintVersion.data?.configuration.sections ?? []).map((section) => section.name),
    [blueprintVersion.data],
  );
  const sectionTargets = useMemo(
    () => (blueprintVersion.data?.configuration.sections ?? []).map((section) => section.questionCount),
    [blueprintVersion.data],
  );

  const questionList = questions.data ?? [];
  const ruleList = rules.data ?? [];
  const totalMarks = questionList.reduce((total, question) => total + question.marks, 0);
  const published = version.data?.published === true;
  const readOnly = published;

  async function addSelection(selection: QuestionPickerSelection) {
    setError(null);
    setMessage(null);
    const sectionName = sectionNames[0];
    if (!sectionName) {
      setError("The bound blueprint version has no sections, so a question cannot be assigned.");
      return;
    }
    try {
      await addQuestion.mutateAsync({
        questionVersionId: selection.questionVersionId,
        sectionName,
        marks: selection.marks,
        negativeMarks: selection.negativeMarks,
        displayOrder: questionList.length + 1,
      });
      setMessage("Question added to the version.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The question could not be added.");
    }
  }

  async function move(assessmentQuestionId: string, direction: -1 | 1) {
    const index = questionList.findIndex((question) => question.id === assessmentQuestionId);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= questionList.length) {
      return;
    }
    const next = [...questionList];
    const [moved] = next.splice(index, 1);
    if (!moved) {
      return;
    }
    next.splice(target, 0, moved);
    const ordered = withSequentialOrder(next);
    setError(null);
    try {
      await reorder.mutateAsync(
        ordered.map((question) => ({
          assessmentQuestionId: question.id,
          displayOrder: question.displayOrder,
        })),
      );
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The new order was rejected.");
    }
  }

  async function runValidate() {
    setError(null);
    setMessage(null);
    try {
      const result = (await validate.mutateAsync(versionId)) as ValidationResult;
      setValidation(result);
      if (!result.valid) {
        setMessage(null);
      }
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "Validation could not be completed.");
    }
  }

  async function runPublish() {
    setError(null);
    setMessage(null);
    try {
      await publish.mutateAsync();
      setMessage("Assessment version published.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "Publication was rejected.");
    }
  }

  if (version.isPending) {
    return <p role="status">Loading assessment version…</p>;
  }

  if (version.isError) {
    return (
      <p role="alert" className="text-sm text-red-600">
        The assessment version could not be loaded.
      </p>
    );
  }

  return (
    <div className="space-y-10">
      <section aria-labelledby="builder-heading" className="space-y-3">
        <div>
          <p className="text-sm text-slate-500">Assessment builder</p>
          <h1 id="builder-heading" className="text-2xl font-semibold tracking-tight">
            {assessment.data?.name ?? "Assessment"}
          </h1>
        </div>
        <dl className="grid gap-2 text-sm sm:grid-cols-4">
          <div>
            <dt className="text-slate-500">Code</dt>
            <dd className="font-mono text-xs">{assessment.data?.code ?? assessmentId}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Version</dt>
            <dd>{version.data?.version ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Blueprint version</dt>
            <dd className="font-mono text-xs">{version.data?.blueprintVersionId ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">State</dt>
            <dd>{published ? "PUBLISHED (immutable)" : "DRAFT"}</dd>
          </div>
        </dl>
        <div className="flex gap-3 text-sm">
          <Link
            href={`/assessments/${assessmentId}/versions/${versionId}/preview`}
            className="underline"
          >
            Open authoring preview
          </Link>
        </div>
      </section>

      {published ? (
        <p role="status" className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
          This version is published and immutable. Authoring controls are disabled; create a new version
          to change it (§23).
        </p>
      ) : null}

      {blueprintVersion.isError ? (
        <p role="alert" className="text-sm text-red-600">
          The bound blueprint version could not be loaded, so section assignment is unavailable.
        </p>
      ) : null}

      <section aria-labelledby="version-editor-heading" className="space-y-3">
        <h2 id="version-editor-heading" className="text-lg font-semibold tracking-tight">
          Version editor
        </h2>
        <dl className="grid gap-3 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-slate-500">Engine type</dt>
            <dd>{version.data?.engineType ?? "Not set"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Duration (minutes)</dt>
            <dd>{version.data?.durationMinutes ?? "Not set"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Delivery policy</dt>
            <dd className="font-mono text-xs">{version.data?.deliveryPolicyId ?? "Not set"}</dd>
          </div>
        </dl>
        {/*
          These fields are set when the version is created (POST /assessments/{id}/versions). The frozen
          contract exposes no update route for them, and no endpoint lists delivery policies, so this
          screen shows the values rather than pretending to edit a resource the API cannot write. A new
          version is the supported way to change them.
        */}
        <p className="text-xs text-slate-500">
          Engine type, duration, and delivery policy are fixed when a version is created: the frozen
          contract exposes no version-update route and no endpoint lists delivery policies. Create a new
          version to change them.
        </p>
      </section>

      {!readOnly ? (
        <QuestionPicker onAdd={(selection) => void addSelection(selection)} addLabel="Add to version" />
      ) : null}

      <AssessmentQuestionTable
        questions={questionList}
        sectionNames={sectionNames}
        disabled={readOnly}
        pending={reorder.isPending}
        onRemove={(assessmentQuestionId) => void removeQuestion.mutateAsync(assessmentQuestionId)}
        onSectionChange={(assessmentQuestionId, sectionName) =>
          void updateQuestion.mutateAsync({ assessmentQuestionId, body: { sectionName } })
        }
        onScoreChange={(assessmentQuestionId, marks, negativeMarks) =>
          void updateQuestion.mutateAsync({ assessmentQuestionId, body: { marks, negativeMarks } })
        }
        onMove={(assessmentQuestionId, direction) => void move(assessmentQuestionId, direction)}
      />

      <RuleBuilder
        versionId={versionId}
        rules={ruleList}
        questions={questionList}
        sectionNames={sectionNames}
        sectionTargets={sectionTargets}
        templates={templates.data ?? []}
        disabled={readOnly}
      />

      <section aria-labelledby="builder-validation-heading" className="space-y-3">
        <h2 id="builder-validation-heading" className="text-lg font-semibold tracking-tight">
          Validation
        </h2>
        <Button variant="outline" onClick={() => void runValidate()} disabled={validate.isPending}>
          Validate version
        </Button>
        {validation ? <ValidationSummary result={validation} /> : null}
      </section>

      <PublishConfirmation
        assessmentName={assessment.data?.name ?? "Assessment"}
        version={version.data}
        sectionNames={sectionNames}
        questionCount={questionList.length}
        totalMarks={totalMarks}
        validation={validation}
        publishing={publish.isPending}
        published={published}
        onPublish={() => void runPublish()}
      />

      {message ? (
        <p role="status" className="text-sm text-green-700">
          {message}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}
