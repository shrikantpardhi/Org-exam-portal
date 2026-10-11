import { TagsManager } from "@/features/academic/components/tags-manager";

export default function AcademicTagsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Tags</h1>
      <TagsManager />
    </main>
  );
}
