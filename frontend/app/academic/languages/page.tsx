import { LanguagesManager } from "@/features/academic/components/languages-manager";

export default function AcademicLanguagesPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Languages</h1>
      <LanguagesManager />
    </main>
  );
}
