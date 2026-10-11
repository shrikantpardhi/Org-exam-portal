import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";

export default function AcademicChaptersPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Chapters</h1>
      <p className="text-sm text-slate-600">Expand a subject to load its chapters.</p>
      <AcademicHierarchy />
    </main>
  );
}
