"use client";

/**
 * Collection list route ({@code specs/13-P4-Assessment-V1.5.md §98, §110}).
 */
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { CollectionList } from "@/features/assessment/components/collection-list";

export default function CollectionsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Collections</h1>
        <p className="text-sm text-slate-500">
          A collection groups assessments for catalog and delivery grouping. Membership is managed by the
          server.
        </p>
      </header>
      <AuthenticatedGuard permission="ASSESSMENT_COLLECTION_VIEW">
        <CollectionList />
      </AuthenticatedGuard>
    </main>
  );
}
