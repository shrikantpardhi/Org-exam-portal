"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { PurchaseDetailView } from "@/features/commerce/components/purchase-detail-view";

/** One purchase and its payment state ({@code specs/18-P9-Commerce-V1.5.md §79, §80}). */
export default function StudentPurchasePage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Student · Purchases</p>
        <h1 className="text-2xl font-semibold tracking-tight">Purchase</h1>
      </header>
      <AuthenticatedGuard>
        <PurchaseDetailView purchaseId={id} />
      </AuthenticatedGuard>
    </main>
  );
}
