import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";

export default function AcademicSubjectsPage() {
  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Subjects</h1>
      <p className="text-sm text-muted-foreground">Expand an exam to load its subjects.</p>
      <AcademicHierarchy />
    </div>
  );
}
