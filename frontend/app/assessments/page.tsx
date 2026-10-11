import { AssessmentList } from "@/features/assessment/components/assessment-list";

export default function AssessmentsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Assessments</h1>
      <AssessmentList />
    </main>
  );
}
