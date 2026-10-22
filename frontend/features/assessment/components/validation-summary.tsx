"use client";

/**
 * Grouped validation panel ({@code specs/13-P4-Assessment-V1.5.md §106}).
 *
 * <p>Issues are grouped by a category derived from the issue code prefix and shown with counts. The
 * grouping is presentational only: the decision to publish always comes from the server's
 * {@code valid} flag, never from counting rendered rows.
 */
import type { ValidationResult } from "@/features/assessment/schemas/assessment";
import { groupValidationIssues, summarizeGroup } from "@/features/assessment/lib/validation-groups";

export function ValidationSummary({ result }: { result: ValidationResult }) {
  if (result.valid) {
    return (
      <p role="status" className="text-sm text-green-700">
        The server validated this assessment version successfully.
      </p>
    );
  }

  const groups = groupValidationIssues(result);

  return (
    <div className="space-y-3">
      <p role="alert" className="text-sm font-medium text-red-700">
        {result.errors.length} validation issue{result.errors.length === 1 ? "" : "s"} must be resolved
        before publication.
      </p>
      <ul className="space-y-3">
        {groups.map((group) => (
          <li key={group.category} data-testid={`validation-group-${group.category}`}>
            <h3 className="text-sm font-semibold">
              {group.category} ({group.count}) — {summarizeGroup(group)}
            </h3>
            <ul className="mt-1 space-y-1">
              {group.issues.map((issue, index) => (
                <li key={`${issue.code}-${index}`} className="text-sm">
                  <span className="font-mono text-xs text-slate-500">{issue.code}</span> — {issue.message}
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </div>
  );
}
