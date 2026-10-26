"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  useApproveVersion,
  usePublishVersion,
  useRejectVersion,
  useSubmitReview,
} from "@/features/content/hooks/use-content";
import type { ReviewStatus } from "@/features/content/schemas/content";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiClientError } from "@/lib/errors";

/**
 * Review and publication actions ({@code specs/12-P3-Content-V1.5.md §111, §112, §167}).
 *
 * <p>Actions are shown only when the caller holds the permission and the version is in the
 * matching state. The backend rechecks every transition; illegal transitions return 409.
 */
export function ReviewActions({
  questionId,
  versionId,
  reviewStatus,
  published,
}: {
  questionId: string;
  versionId: string;
  reviewStatus: ReviewStatus;
  published: boolean;
}) {
  const { can } = useAuth();
  const [remarks, setRemarks] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = useSubmitReview(questionId);
  const approve = useApproveVersion(questionId);
  const reject = useRejectVersion(questionId);
  const publish = usePublishVersion(questionId);

  async function run(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The action could not be completed.");
    }
  }

  if (published) {
    return <p className="text-sm text-muted-foreground">This version is published and immutable.</p>;
  }

  return (
    <div className="space-y-3">
      {(reviewStatus === "DRAFT" || reviewStatus === "REJECTED") && can("QUESTION_SUBMIT_REVIEW") ? (
        <Button onClick={() => void run(() => submit.mutateAsync({ versionId }))}>Submit for review</Button>
      ) : null}

      {reviewStatus === "IN_REVIEW" && can("QUESTION_REVIEW") ? (
        <div className="space-y-2">
          <label htmlFor="remarks" className="text-sm font-medium">
            Review remarks
          </label>
          <textarea
            id="remarks"
            rows={2}
            value={remarks}
            onChange={(event) => setRemarks(event.target.value)}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
          <div className="flex gap-2">
            <Button onClick={() => void run(() => approve.mutateAsync({ versionId, remarks }))}>Approve</Button>
            <Button
              variant="outline"
              onClick={() => void run(() => reject.mutateAsync({ versionId, remarks }))}
            >
              Reject
            </Button>
          </div>
        </div>
      ) : null}

      {reviewStatus === "APPROVED" && can("QUESTION_PUBLISH") ? (
        <Button onClick={() => void run(() => publish.mutateAsync({ versionId }))}>Publish</Button>
      ) : null}

      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}
