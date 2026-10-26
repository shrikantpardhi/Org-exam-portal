import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";

export default function AcademicSubTopicsPage() {
  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Sub-topics</h1>
      <p className="text-sm text-muted-foreground">Expand a topic to load its sub-topics.</p>
      <AcademicHierarchy />
    </div>
  );
}
