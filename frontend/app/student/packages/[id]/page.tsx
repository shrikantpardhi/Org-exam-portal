"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { PackageDetailScreen } from "@/features/commerce/components/package-detail-screen";

/** Package detail and plan selection ({@code specs/18-P9-Commerce-V1.5.md §77}). */
export default function StudentPackagePage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Student · Store</p>
        <h1 className="text-2xl font-semibold tracking-tight">Access package</h1>
      </header>
      <AuthenticatedGuard>
        <PackageDetailScreen packageId={id} />
      </AuthenticatedGuard>
    </main>
  );
}
