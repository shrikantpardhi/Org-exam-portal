"use client";

/**
 * Authoring preview ({@code specs/13-P4-Assessment-V1.5.md §57, §107}).
 *
 * <p>Renders the frozen preview response grouped by section with marks. It resembles the delivery
 * experience but remains authoring-only: it creates no runtime session, persists nothing, and shows
 * no correctness.
 */
import { useAssessmentPreview } from "@/features/assessment/hooks/use-assessment";
import type { AssessmentVersionResponse } from "@/features/assessment/schemas/assessment";

export function AssessmentPreview({
  versionId,
  version,
}: {
  versionId: string;
  version?: AssessmentVersionResponse | undefined;
}) {
  const preview = useAssessmentPreview(versionId);

  if (preview.isPending) {
    return <p role="status">Loading preview…</p>;
  }

  if (preview.isError) {
    return (
      <p role="alert" className="text-sm text-red-600">
        The preview could not be loaded.
      </p>
    );
  }

  const data = preview.data;

  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">Authoring preview</h1>
        <p role="note" className="rounded border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
          This is an authoring preview. It creates no runtime session and persists nothing. Correct
          answers are never shown here.
        </p>
        <dl className="grid gap-2 text-sm sm:grid-cols-4">
          <div>
            <dt className="text-slate-500">Questions</dt>
            <dd>{data?.totalQuestions ?? version?.totalQuestions ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Total marks</dt>
            <dd>{data?.totalMarks ?? version?.totalMarks ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Duration</dt>
            <dd>{data?.durationMinutes ?? version?.durationMinutes ?? "—"} minutes</dd>
          </div>
          <div>
            <dt className="text-slate-500">Engine</dt>
            <dd>{data?.engineType ?? version?.engineType ?? "—"}</dd>
          </div>
        </dl>
      </section>

      {data && data.sections.length === 0 ? (
        <p role="note" className="text-sm text-slate-500">
          This version has no configured questions yet.
        </p>
      ) : null}

      {data?.sections.map((section) => (
        <section key={section.sectionName} aria-labelledby={`preview-${section.sectionName}`} className="space-y-3">
          <h2 id={`preview-${section.sectionName}`} className="text-lg font-semibold tracking-tight">
            {section.sectionName || "Unassigned"}
          </h2>
          <ol className="space-y-3">
            {section.questions.map((question) => (
              <li key={question.assessmentQuestionId} className="rounded-lg border border-slate-200 p-4">
                <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                  <span>Question {question.displayOrder}</span>
                  <span>
                    +{question.marks} / -{question.negativeMarks}
                  </span>
                </div>
                <div className="mt-2 space-y-2 text-sm">
                  <p className="whitespace-pre-wrap">{question.content}</p>
                  {question.renderedContent ? (
                    <div
                      className="prose prose-sm max-w-none"
                      dangerouslySetInnerHTML={{ __html: question.renderedContent }}
                    />
                  ) : null}
                </div>
              </li>
            ))}
          </ol>
        </section>
      ))}
    </div>
  );
}
