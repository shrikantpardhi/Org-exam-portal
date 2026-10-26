"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { FinancePurchaseLookup } from "@/features/commerce/components/finance-purchase-lookup";

/** Finance purchase workspace ({@code specs/18-P9-Commerce-V1.5.md §83}). */
export default function FinancePurchasesPage() {
  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Finance</p>
        <h1 className="text-2xl font-semibold tracking-tight">Purchases</h1>
        <p className="text-sm text-muted-foreground">
          Look up a purchase to review its plan, amount and payment status.
        </p>
      </header>
      <AuthenticatedGuard permission="PURCHASE_VIEW">
        <FinancePurchaseLookup mode="purchases" />
      </AuthenticatedGuard>
    </div>
  );
}
