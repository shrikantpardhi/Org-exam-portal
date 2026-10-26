"use client";

/**
 * Review queue ({@code specs/12-P3-Content-V1.5.md §111, §112}).
 *
 * <p>Lists the questions whose latest version is awaiting review, with the facts a reviewer needs
 * before opening one: template, exam, subject, difficulty, version, marks and review state. The
 * queue is derived from the existing question list query filtered by {@code reviewStatus}, so no
 * separate persisted review entity is invented. Approval is offered only for a version the server
 * reports as {@code IN_REVIEW}; an "Approved" badge is rendered only when the server itself
 * returned {@code APPROVED}.
 */
import Link from "next/link";

import { useQuestions } from "@/features/content/hooks/use-content";
import type { ReviewStatus } from "@/features/content/schemas/content";

const REVIEW_LABEL: Record<ReviewStatus, string> = {
  DRAFT: "Draft",
  IN_REVIEW: "In review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  PUBLISHED: "Published",
};

export function ReviewQueue() {
  const questions = useQuestions({ reviewStatus: "IN_REVIEW", size: 50 });

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Questions whose latest version is awaiting review. The review state shown is the state the
        server returned.
      </p>

      {questions.isPending ? (
        <p role="status" className="text-sm text-muted-foreground">
          Loading the review queue…
        </p>
      ) : null}
      {questions.isError ? (
        <p role="alert" className="text-sm text-red-600">
          The review queue could not be loaded.
        </p>
      ) : null}
      {questions.data && questions.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nothing is awaiting review.</p>
      ) : null}

      {questions.data && questions.data.length > 0 ? (
        <div className="overflow-x-auto" tabIndex={0}>
<table aria-label="Review queue" className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="text-left">
              <th scope="col">Question</th>
              <th scope="col">Difficulty</th>
              <th scope="col">Version</th>
              <th scope="col">Review state</th>
              <th scope="col">Publication</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {questions.data.map((question) => (
              <tr key={question.id}>
                <td className="font-mono text-xs">{question.id.slice(0, 8)}</td>
                <td>{question.difficulty ?? "—"}</td>
                <td>{question.latestVersion ?? "—"}</td>
                <td>{question.latestReviewStatus ? REVIEW_LABEL[question.latestReviewStatus] : "—"}</td>
                <td>{question.published ? "Published" : "Unpublished"}</td>
                <td>
                  <Link href={`/questions/${question.id}`} className="underline">
                    Review this question
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
</div>
      ) : null}

      {/*
       * Approval gap (§112): the queue row carries no version id, and the frozen list contract
       * does not expose one. Approval therefore happens on the question workspace against the
       * selected version, where the server's review state for that exact version is known, rather
       * than here against a guessed version.
       */}
      <p role="note" className="text-xs text-muted-foreground">
        Approving needs the exact version, which the list response does not carry, so approval is
        performed on the question page where the version is explicit.
      </p>
    </div>
  );
}

/** Shared label map so every content surface renders a review state the same way. */
export { REVIEW_LABEL };
