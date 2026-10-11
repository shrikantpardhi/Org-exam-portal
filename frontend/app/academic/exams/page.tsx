import { ExamsManager } from "@/features/academic/components/exams-manager";

export default function AcademicExamsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Exams</h1>
      <ExamsManager />
    </main>
  );
}
