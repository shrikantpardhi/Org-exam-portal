"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { QuestionEditor } from "@/features/content/components/question-editor";

/**
 * New question route ({@code specs/12-P3-Content-V1.5.md §102-§104, §113}).
 *
 * <p>Creating a question requires the create permission. Content uses MARKDOWN_LATEX_V1, so math is
 * authored with the approved delimiters and currency such as {@code $500} stays ordinary text.
 */
export default function NewQuestionPage() {
  return (
    <AuthenticatedGuard permission="QUESTION_CREATE">
      <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
        <h1 className="text-2xl font-semibold tracking-tight">New question</h1>
        <p className="text-sm text-muted-foreground">
          Content uses MARKDOWN_LATEX_V1: inline math as <code>\( ... \)</code> and block math as{" "}
          <code>\[ ... \]</code>. Currency such as $500 stays ordinary text.
        </p>
        <QuestionEditor />
      </div>
    </AuthenticatedGuard>
  );
}
