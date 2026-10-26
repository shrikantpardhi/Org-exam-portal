"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  usePublishAssessmentVersion,
  useValidateAssessmentVersion,
} from "@/features/assessment/hooks/use-assessment";
import type { ValidationResult } from "@/features/assessment/schemas/assessment";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiClientError } from "@/lib/errors";

/**
 * Validation and publication panel ({@code specs/13-P4-Assessment-V1.5.md §106, §108, §195}).
 *
 * <p>Validation issues are shown before publication; the publish action is permission-aware and the
 * backend re-validates and blocks an incomplete assessment.
 */
export function AssessmentValidationPanel({
  versionId,
  published,
}: {
  versionId: string;
  published: boolean;
}) {
  const { can } = useAuth();
  const validate = useValidateAssessmentVersion();
  const publish = usePublishAssessmentVersion(versionId);
  const [result, setResult] = useState<ValidationResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function runValidate() {
    setError(null);
    try {
      setResult((await validate.mutateAsync(versionId)) as ValidationResult);
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "Validation could not be completed.");
    }
  }

  async function runPublish() {
    setError(null);
    try {
      await publish.mutateAsync();
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "Publication was rejected.");
    }
  }

  if (published) {
    return <p className="text-sm text-muted-foreground">This assessment version is published and immutable.</p>;
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        {can("ASSESSMENT_VERSION_VALIDATE") ? (
          <Button variant="outline" onClick={() => void runValidate()}>
            Validate
          </Button>
        ) : null}
        {can("ASSESSMENT_VERSION_PUBLISH") ? (
          <Button disabled={!result?.valid} onClick={() => void runPublish()}>
            Publish
          </Button>
        ) : null}
      </div>

      {result ? (
        result.valid ? (
          <p role="status" className="text-sm text-green-700">
            Assessment is valid and ready to publish.
          </p>
        ) : (
          <ul role="alert" className="space-y-1 text-sm text-red-600">
            {result.errors.map((issue, index) => (
              <li key={index}>
                <span className="font-mono text-xs">{issue.code}</span> — {issue.message}
              </li>
            ))}
          </ul>
        )
      ) : null}

      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}
