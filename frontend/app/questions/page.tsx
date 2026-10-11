import { QuestionList } from "@/features/content/components/question-list";

export default function QuestionsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Question bank</h1>
      <QuestionList />
    </main>
  );
}
