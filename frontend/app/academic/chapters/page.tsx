import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";

export default function AcademicChaptersPage() {
  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Chapters</h1>
      <p className="text-sm text-muted-foreground">Expand a subject to load its chapters.</p>
      <AcademicHierarchy />
    </div>
  );
}
