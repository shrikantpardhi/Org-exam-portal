import { ExamsManager } from "@/features/academic/components/exams-manager";

export default function AcademicExamsPage() {
  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Exams</h1>
      <ExamsManager />
    </div>
  );
}
