"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { ReviewQueue } from "@/features/content/components/review-queue";

/**
 * Review queue route ({@code specs/12-P3-Content-V1.5.md §111, §113}).
 *
 * <p>The static {@code /questions/review} segment is matched before {@code /questions/[id]}, so the
 * queue never collides with a question id.
 */
export default function ReviewQueuePage() {
  return (
    <AuthenticatedGuard permission="QUESTION_REVIEW">
      <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
        <h1 className="text-2xl font-semibold tracking-tight">Review queue</h1>
        <ReviewQueue />
      </div>
    </AuthenticatedGuard>
  );
}
