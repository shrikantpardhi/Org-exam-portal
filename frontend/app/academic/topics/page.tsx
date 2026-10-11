import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";

export default function AcademicTopicsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Topics</h1>
      <p className="text-sm text-slate-600">Expand a chapter to load its topics.</p>
      <AcademicHierarchy />
    </main>
  );
}
