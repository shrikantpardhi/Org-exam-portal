"use client";

/**
 * Collection detail route ({@code specs/13-P4-Assessment-V1.5.md §98, §110}).
 */
import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { CollectionDetail } from "@/features/assessment/components/collection-list";

export default function CollectionDetailPage() {
  const params = useParams<{ id: string }>();
  const collectionId = params?.id ?? "";

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <AuthenticatedGuard permission="ASSESSMENT_COLLECTION_VIEW">
        <CollectionDetail collectionId={collectionId} />
      </AuthenticatedGuard>
    </main>
  );
}
