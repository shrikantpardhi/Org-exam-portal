import { TagsManager } from "@/features/academic/components/tags-manager";

export default function AcademicTagsPage() {
  return (
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Tags</h1>
      <TagsManager />
    </div>
  );
}
