import { QuestionForm } from "@/features/content/components/question-form";

export default function NewQuestionPage() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">New question</h1>
      <p className="text-sm text-slate-600">
        Content uses MARKDOWN_LATEX_V1: inline math as <code>\( ... \)</code> and block math as{" "}
        <code>\[ ... \]</code>. Currency such as $500 stays ordinary text.
      </p>
      <QuestionForm />
    </main>
  );
}
