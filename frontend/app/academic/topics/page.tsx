import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";

export default function AcademicTopicsPage() {
  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Topics</h1>
      <p className="text-sm text-muted-foreground">Expand a chapter to load its topics.</p>
      <AcademicHierarchy />
    </div>
  );
}
