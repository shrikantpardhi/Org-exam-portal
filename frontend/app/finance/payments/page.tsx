"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { FinancePurchaseLookup } from "@/features/commerce/components/finance-purchase-lookup";

/** Finance payment view ({@code specs/18-P9-Commerce-V1.5.md §83}). */
export default function FinancePaymentsPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Finance</p>
        <h1 className="text-2xl font-semibold tracking-tight">Payments</h1>
        <p className="text-sm text-slate-500">
          The authoritative payment status of a purchase. Provider payloads are never displayed.
        </p>
      </header>
      <AuthenticatedGuard permission="PURCHASE_VIEW">
        <FinancePurchaseLookup mode="payments" />
      </AuthenticatedGuard>
    </main>
  );
}
