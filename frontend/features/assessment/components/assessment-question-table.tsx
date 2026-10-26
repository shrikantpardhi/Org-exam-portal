"use client";

/**
 * Assessment question table: section assignment, scoring, and ordering
 * ({@code specs/13-P4-Assessment-V1.5.md §43, §44, §104, §105}).
 *
 * <p>Section names come from a controlled list derived from the BlueprintVersion configuration — the
 * operator never types a free-text section label, and the builder never ships a section catalog of its
 * own. Scoring is editable per question, and the table states plainly that a QuestionVersion default
 * may differ from the AssessmentQuestion's actual score (§105).
 */
import { Button } from "@/components/ui/button";
import type { AssessmentQuestion } from "@/features/assessment/schemas/assessment-builder";

const fieldClass =
  "w-full rounded-md border border-input px-2 py-1 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-900";

export function AssessmentQuestionTable({
  questions,
  sectionNames,
  disabled = false,
  pending = false,
  onRemove,
  onSectionChange,
  onScoreChange,
  onMove,
}: {
  questions: AssessmentQuestion[];
  sectionNames: string[];
  disabled?: boolean;
  pending?: boolean;
  onRemove: (assessmentQuestionId: string) => void;
  onSectionChange: (assessmentQuestionId: string, sectionName: string) => void;
  onScoreChange: (assessmentQuestionId: string, marks: number, negativeMarks: number) => void;
  onMove: (assessmentQuestionId: string, direction: -1 | 1) => void;
}) {
  return (
    <section aria-labelledby="assessment-questions-heading" className="space-y-3">
      <div>
        <h2 id="assessment-questions-heading" className="text-lg font-semibold tracking-tight">
          Questions, sections, and scoring
        </h2>
        <p className="text-sm text-muted-foreground">
          The QuestionVersion default marks and the AssessmentQuestion actual marks may differ. The value
          saved here is the score used for this assessment.
        </p>
      </div>

      {sectionNames.length === 0 ? (
        <p role="alert" className="text-sm text-red-600">
          The bound blueprint version has no sections, so a question cannot be assigned yet.
        </p>
      ) : null}

      {questions.length === 0 ? (
        <p role="note" className="text-sm text-muted-foreground">
          No questions are configured on this version yet.
        </p>
      ) : (
        <div className="overflow-x-auto" role="region" aria-label="Scrollable table" tabIndex={0}>
<table className="w-full min-w-[640px] text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-2">Order</th>
              <th className="py-2">Question version</th>
              <th className="py-2">Section</th>
              <th className="py-2">Marks</th>
              <th className="py-2">Negative</th>
              <th className="py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {questions.map((question, index) => (
              <tr key={question.id} className="border-b border-border" data-testid="assessment-question-row">
                <td className="py-2">{question.displayOrder}</td>
                <td className="py-2 font-mono text-xs">{question.questionVersionId}</td>
                <td className="py-2">
                  <select
                    aria-label={`Section for question ${question.displayOrder}`}
                    value={question.sectionName ?? ""}
                    disabled={disabled}
                    onChange={(event) => onSectionChange(question.id, event.target.value)}
                    className={fieldClass}
                  >
                    <option value="">Unassigned</option>
                    {sectionNames.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="py-2">
                  <input
                    aria-label={`Marks for question ${question.displayOrder}`}
                    type="number"
                    min={0}
                    step="0.01"
                    defaultValue={question.marks}
                    disabled={disabled}
                    onBlur={(event) => onScoreChange(question.id, Number(event.target.value), question.negativeMarks)}
                    className={fieldClass}
                  />
                </td>
                <td className="py-2">
                  <input
                    aria-label={`Negative marks for question ${question.displayOrder}`}
                    type="number"
                    min={0}
                    step="0.01"
                    defaultValue={question.negativeMarks}
                    disabled={disabled}
                    onBlur={(event) => onScoreChange(question.id, question.marks, Number(event.target.value))}
                    className={fieldClass}
                  />
                </td>
                <td className="py-2">
                  <div className="flex flex-wrap gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      aria-label={`Move question ${question.displayOrder} up`}
                      disabled={disabled || pending || index === 0}
                      onClick={() => onMove(question.id, -1)}
                    >
                      Up
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      aria-label={`Move question ${question.displayOrder} down`}
                      disabled={disabled || pending || index === questions.length - 1}
                      onClick={() => onMove(question.id, 1)}
                    >
                      Down
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`Remove question ${question.displayOrder}`}
                      disabled={disabled}
                      onClick={() => onRemove(question.id)}
                    >
                      Remove
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
</div>
      )}
    </section>
  );
}
