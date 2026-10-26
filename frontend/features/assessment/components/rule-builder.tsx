"use client";

/**
 * Rule builder and hybrid counts ({@code specs/13-P4-Assessment-V1.5.md §45, §102, §103}).
 *
 * <p>Only dimensions backed by the P3 content contract are exposed: difficulty, templateId, tagId,
 * the taxonomy ids, languageId, and publishedOnly. The configuration is built key-by-key from those
 * inputs — no extra key is invented. The server performs the actual materialization, so this panel
 * describes intent and never persists a question set itself.
 */
import { useCallback, useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  useChapters,
  useExams,
  useLanguages,
  useSubjects,
  useTags,
  useTopics,
} from "@/features/academic/hooks/use-academic";
import {
  useCreateSelectionRule,
  useDeleteSelectionRule,
  useMaterializeSelectionRules,
  useQuestionSearch,
  useUpdateSelectionRule,
} from "@/features/assessment/hooks/use-assessment";
import { computeHybridCounts, type HybridCounts } from "@/features/assessment/lib/hybrid-counts";
import {
  EMPTY_RULE_FORM,
  MATERIALIZE_MODE,
  fromRuleConfiguration,
  isRuleScoped,
  type AssessmentQuestion,
  type Difficulty,
  type RuleConfigurationForm,
  type SelectionRule,
} from "@/features/assessment/schemas/assessment-builder";
import type { QuestionTemplate } from "@/features/content/schemas/content";
import { ApiClientError } from "@/lib/errors";

const fieldClass =
  "w-full rounded-md border border-input px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-900";

export function RuleBuilder({
  versionId,
  rules,
  questions,
  sectionNames,
  sectionTargets,
  templates,
  disabled = false,
}: {
  versionId: string;
  rules: SelectionRule[];
  questions: AssessmentQuestion[];
  sectionNames: string[];
  sectionTargets: number[];
  templates: QuestionTemplate[];
  disabled?: boolean;
}) {
  const createRule = useCreateSelectionRule(versionId);
  const updateRule = useUpdateSelectionRule(versionId);
  const deleteRule = useDeleteSelectionRule(versionId);
  const materialize = useMaterializeSelectionRules(versionId);

  const [sectionName, setSectionName] = useState("");
  const [targetCount, setTargetCount] = useState(0);
  const [form, setForm] = useState<RuleConfigurationForm>(EMPTY_RULE_FORM);
  const [editingRuleId, setEditingRuleId] = useState<string | null>(null);
  // The contract defines exactly one materialize mode, so there is no selector and no state to hold.
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const exams = useExams();
  const subjects = useSubjects(form.examId);
  const chapters = useChapters(form.subjectId);
  const topics = useTopics(form.chapterId);
  const languages = useLanguages();
  const tags = useTags();

  /** The template id behind a selected template code, for the question-search preview only. */
  const templateIdForCode = useCallback(
    (code: string) => templates.find((template) => template.code === code)?.id,
    [templates],
  );

  // A live pool estimate from the same server-side search the picker uses — never a full download.
  const pool = useQuestionSearch({
    page: 0,
    size: 1,
    difficulty: form.difficulty.length > 0 ? form.difficulty : undefined,
    // The question list filters by template *id*; the rule configuration stores the *code*. The
    // select carries the id and the code is looked up for the payload, so both sides stay correct.
    templateId: form.templateCode.length > 0 ? templateIdForCode(form.templateCode) : undefined,
    tagId: form.tagIds[0],
    examId: form.examId.length > 0 ? form.examId : undefined,
    subjectId: form.subjectId.length > 0 ? form.subjectId : undefined,
    chapterId: form.chapterId.length > 0 ? form.chapterId : undefined,
    topicId: form.topicId.length > 0 ? form.topicId : undefined,
    languageId: form.languageId.length > 0 ? form.languageId : undefined,
    isPublished: form.publishedOnly ? true : undefined,
  });

  const counts: HybridCounts = useMemo(
    () => computeHybridCounts({ questions, rules, sectionTargets }),
    [questions, rules, sectionTargets],
  );

  const scoped = isRuleScoped(form);
  const availableSections = sectionNames.filter(
    (name) => editingRuleId !== null || !rules.some((rule) => rule.sectionName === name),
  );

  function patch(patchValue: Partial<RuleConfigurationForm>) {
    setForm((current) => ({ ...current, ...patchValue }));
  }

  function startEdit(rule: SelectionRule) {
    setEditingRuleId(rule.id);
    setSectionName(rule.sectionName);
    setTargetCount(rule.targetCount);
    setForm(fromRuleConfiguration(rule.ruleConfiguration));
    setError(null);
    setMessage(null);
  }

  function reset() {
    setEditingRuleId(null);
    setSectionName("");
    setTargetCount(0);
    setForm(EMPTY_RULE_FORM);
  }

  async function submitRule() {
    setError(null);
    setMessage(null);
    if (sectionName.length === 0) {
      setError("Choose a section from the blueprint.");
      return;
    }
    if (targetCount <= 0) {
      setError("Target count must be greater than zero.");
      return;
    }
    if (!scoped) {
      setError("Scope the rule with at least one of exam, subject, topic, or tag.");
      return;
    }
    try {
      if (editingRuleId) {
        await updateRule.mutateAsync({ ruleId: editingRuleId, targetCount, form });
        setMessage("Selection rule updated.");
      } else {
        await createRule.mutateAsync({
          sectionName,
          targetCount,
          displayOrder: rules.length + 1,
          form,
        });
        setMessage("Selection rule created.");
      }
      reset();
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The selection rule was rejected.");
    }
  }

  async function runMaterialize() {
    setError(null);
    setMessage(null);
    try {
      await materialize.mutateAsync(MATERIALIZE_MODE);
      setMessage("The server materialized the rule-selected questions.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "Materialization was rejected.");
    }
  }

  return (
    <section aria-labelledby="rule-builder-heading" className="space-y-4">
      <div>
        <h2 id="rule-builder-heading" className="text-lg font-semibold tracking-tight">
          Rule builder
        </h2>
        <p className="text-sm text-muted-foreground">
          Filters exposed here are only those backed by published content. The server materializes the
          concrete question set; the browser never decides which questions are selected.
        </p>
      </div>

      <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-5" aria-label="Hybrid counts">
        <div>
          <dt className="text-muted-foreground">Manual count</dt>
          <dd data-testid="manual-count">{counts.manualCount}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Rule count</dt>
          <dd data-testid="rule-count">{counts.ruleCount}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Target count</dt>
          <dd data-testid="target-count">{counts.targetCount}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Selected count</dt>
          <dd data-testid="selected-count">{counts.selectedCount}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Remaining count</dt>
          <dd data-testid="remaining-count">{counts.remainingCount}</dd>
        </div>
      </dl>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <label htmlFor="rule-section" className="text-sm font-medium">
            Section
          </label>
          <select
            id="rule-section"
            value={sectionName}
            disabled={disabled}
            onChange={(event) => setSectionName(event.target.value)}
            className={fieldClass}
          >
            <option value="">Choose a section</option>
            {availableSections.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="rule-target" className="text-sm font-medium">
            Target count
          </label>
          <input
            id="rule-target"
            type="number"
            min={0}
            value={targetCount}
            disabled={disabled}
            onChange={(event) => setTargetCount(Number(event.target.value))}
            className={fieldClass}
          />
        </div>

        <div>
          <label htmlFor="rule-difficulty" className="text-sm font-medium">
            Difficulty
          </label>
          <select
            id="rule-difficulty"
            value={form.difficulty}
            disabled={disabled}
            onChange={(event) => patch({ difficulty: event.target.value as Difficulty | "" })}
            className={fieldClass}
          >
            <option value="">Any difficulty</option>
            <option value="EASY">EASY</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HARD">HARD</option>
          </select>
        </div>

        <div>
          <label htmlFor="rule-template" className="text-sm font-medium">
            Template
          </label>
          <select
            id="rule-template"
            value={form.templateCode}
            disabled={disabled}
            onChange={(event) => patch({ templateCode: event.target.value })}
            className={fieldClass}
          >
            <option value="">Any template</option>
            {templates.map((template) => (
              <option key={template.id} value={template.code}>
                {template.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="rule-exam" className="text-sm font-medium">
            Exam
          </label>
          <select
            id="rule-exam"
            value={form.examId}
            disabled={disabled}
            onChange={(event) =>
              patch({ examId: event.target.value, subjectId: "", chapterId: "", topicId: "" })
            }
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
          <label htmlFor="rule-subject" className="text-sm font-medium">
            Subject
          </label>
          <select
            id="rule-subject"
            value={form.subjectId}
            disabled={disabled || form.examId.length === 0}
            onChange={(event) => patch({ subjectId: event.target.value, chapterId: "", topicId: "" })}
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
          <label htmlFor="rule-chapter" className="text-sm font-medium">
            Chapter
          </label>
          <select
            id="rule-chapter"
            value={form.chapterId}
            disabled={disabled || form.subjectId.length === 0}
            onChange={(event) => patch({ chapterId: event.target.value, topicId: "" })}
            className={fieldClass}
          >
            <option value="">Any chapter</option>
            {(chapters.data ?? []).map((chapter) => (
              <option key={chapter.id} value={chapter.id}>
                {chapter.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="rule-topic" className="text-sm font-medium">
            Topic
          </label>
          <select
            id="rule-topic"
            value={form.topicId}
            disabled={disabled || form.chapterId.length === 0}
            onChange={(event) => patch({ topicId: event.target.value })}
            className={fieldClass}
          >
            <option value="">Any topic</option>
            {(topics.data ?? []).map((topic) => (
              <option key={topic.id} value={topic.id}>
                {topic.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="rule-tag" className="text-sm font-medium">
            Tag
          </label>
          {/* The server reads `tagIds` as a list (§45). Storing a single-element list keeps the payload
              exactly on-contract instead of relying on the server to accept a bare string. */}
          <select
            id="rule-tag"
            value={form.tagIds[0] ?? ""}
            disabled={disabled}
            onChange={(event) =>
              patch({ tagIds: event.target.value.length > 0 ? [event.target.value] : [] })
            }
            className={fieldClass}
          >
            <option value="">Any tag</option>
            {(tags.data ?? []).map((tag) => (
              <option key={tag.id} value={tag.id}>
                {tag.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="rule-language" className="text-sm font-medium">
            Language
          </label>
          <select
            id="rule-language"
            value={form.languageId}
            disabled={disabled}
            onChange={(event) => patch({ languageId: event.target.value })}
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

        <div className="flex items-end">
          <label htmlFor="rule-published-only" className="flex items-center gap-2 text-sm">
            <input
              id="rule-published-only"
              type="checkbox"
              checked={form.publishedOnly}
              disabled={disabled}
              onChange={(event) => patch({ publishedOnly: event.target.checked })}
              className="h-4 w-4 rounded border-input"
            />
            Published only
          </label>
        </div>
      </div>

      <p className="text-xs text-muted-foreground" data-testid="rule-pool-note">
        {pool.isSuccess
          ? `The current filter matched ${pool.data.length} question${pool.data.length === 1 ? "" : "s"} on the first page. The server re-resolves the full eligible pool when the rule is materialized.`
          : "The eligible pool is resolved by the server when the rule is materialized."}
      </p>

      <div className="flex flex-wrap gap-2">
        <Button onClick={() => void submitRule()} disabled={disabled || createRule.isPending || updateRule.isPending}>
          {editingRuleId ? "Save rule" : "Add rule"}
        </Button>
        {editingRuleId ? (
          <Button variant="outline" onClick={reset}>
            Cancel edit
          </Button>
        ) : null}
      </div>

      {rules.length === 0 ? (
        <p role="note" className="text-sm text-muted-foreground">
          No selection rules yet.
        </p>
      ) : (
        <ul className="space-y-2">
          {rules.map((rule) => (
            <li key={rule.id} className="flex flex-wrap items-center justify-between gap-3 rounded border border-border p-3">
              <div className="text-sm">
                <span className="font-medium">{rule.sectionName}</span>
                <span className="text-muted-foreground"> · target {rule.targetCount}</span>
                <span className="text-muted-foreground">
                  {" "}
                  · {rule.materializedOn ? `materialized ${rule.materializedQuestionCount ?? 0}` : "not materialized"}
                </span>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => startEdit(rule)}
                  disabled={disabled || rule.materializedOn != null}
                >
                  Edit
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => void deleteRule.mutateAsync(rule.id)}
                  disabled={disabled || rule.materializedOn != null}
                >
                  Delete
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <div className="space-y-2 rounded-lg border border-border p-4">
        <h3 className="text-sm font-semibold">Materialize</h3>
        <p className="text-xs text-muted-foreground">
          The server materializes the final question set from the rules. Manual questions already on the
          version are preserved.
        </p>
        <p className="text-xs text-muted-foreground">
          Only <code>REPLACE_UNMATERIALIZED</code> is defined by the contract: rules not yet materialized
          are filled in, and rules that already are are skipped, so re-running is safe.
        </p>
        <Button variant="outline" onClick={() => void runMaterialize()} disabled={disabled || materialize.isPending}>
          Materialize on server
        </Button>
      </div>

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
    </section>
  );
}
