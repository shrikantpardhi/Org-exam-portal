import { QuestionList } from "@/features/content/components/question-list";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";

/**
 * Question bank ({@code specs/12-P3-Content-V1.5.md §101}).
 *
 * <p>Guarded by {@code QUESTION_VIEW}. The list is server-paged and server-filtered, so this screen
 * never downloads the bank.
 */
export default function QuestionsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Question bank</h1>
      <AuthenticatedGuard permission="QUESTION_VIEW">
        <QuestionList />
      </AuthenticatedGuard>
    </main>
  );
}
