"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { FinancePurchaseLookup } from "@/features/commerce/components/finance-purchase-lookup";

/** Finance refunds ({@code specs/18-P9-Commerce-V1.5.md §89}). */
export default function FinanceRefundsPage() {
  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Finance</p>
        <h1 className="text-2xl font-semibold tracking-tight">Refunds</h1>
        <p className="text-sm text-muted-foreground">
          A refund is a server action: it checks permission and payment state, calls the provider,
          revokes the access grant and audits the result.
        </p>
      </header>
      <AuthenticatedGuard permission="PURCHASE_VIEW">
        <FinancePurchaseLookup mode="refunds" />
      </AuthenticatedGuard>
    </div>
  );
}
