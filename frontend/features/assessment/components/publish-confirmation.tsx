"use client";

/**
 * Publication confirmation ({@code specs/13-P4-Assessment-V1.5.md §108}).
 *
 * <p>Shows assessment, version, blueprint version, question count, total marks, duration, and the
 * section list. Publish is enabled only after the server returned {@code valid: true} for this exact
 * version; the confirmation dialog cannot bypass that gate.
 */
import { Button } from "@/components/ui/button";
import type { AssessmentVersionResponse, ValidationResult } from "@/features/assessment/schemas/assessment";

export function PublishConfirmation({
  assessmentName,
  version,
  sectionNames,
  questionCount,
  totalMarks,
  validation,
  publishing = false,
  published = false,
  onPublish,
}: {
  assessmentName: string;
  version?: AssessmentVersionResponse | undefined;
  sectionNames: string[];
  questionCount: number;
  totalMarks: number;
  validation: ValidationResult | null;
  publishing?: boolean;
  published?: boolean;
  onPublish: () => void;
}) {
  const validated = validation?.valid === true;

  if (published) {
    return (
      <p className="text-sm text-slate-600">
        This assessment version is published and immutable. A change requires a new version.
      </p>
    );
  }

  return (
    <section aria-labelledby="publish-heading" className="space-y-3">
      <h2 id="publish-heading" className="text-lg font-semibold tracking-tight">
        Publication confirmation
      </h2>

      <dl className="grid gap-2 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-slate-500">Assessment</dt>
          <dd>{assessmentName}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Version</dt>
          <dd>{version?.version ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Blueprint version</dt>
          <dd className="font-mono text-xs">{version?.blueprintVersionId ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Questions</dt>
          <dd>{questionCount}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Total marks</dt>
          <dd>{totalMarks}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Duration (minutes)</dt>
          <dd>{version?.durationMinutes ?? "—"}</dd>
        </div>
      </dl>

      <div>
        <h3 className="text-sm font-medium">Sections</h3>
        {sectionNames.length === 0 ? (
          <p className="text-sm text-slate-500">No sections are defined on the bound blueprint version.</p>
        ) : (
          <ul className="list-inside list-disc text-sm">
            {sectionNames.map((name) => (
              <li key={name}>{name}</li>
            ))}
          </ul>
        )}
      </div>

      <Button onClick={onPublish} disabled={!validated || publishing}>
        Publish
      </Button>
      {!validated ? (
        <p className="text-sm text-slate-500">
          Run validation first. Publish stays disabled until the server reports the version as valid.
        </p>
      ) : null}
    </section>
  );
}
