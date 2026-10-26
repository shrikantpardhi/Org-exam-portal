import { LanguagesManager } from "@/features/academic/components/languages-manager";

export default function AcademicLanguagesPage() {
  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Languages</h1>
      <LanguagesManager />
    </div>
  );
}
