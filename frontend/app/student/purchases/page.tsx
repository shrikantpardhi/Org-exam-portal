"use client";

import Link from "next/link";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { PurchaseHistoryScreen } from "@/features/commerce/components/purchase-history-screen";

/** Student purchase history ({@code specs/18-P9-Commerce-V1.5.md §81}). */
export default function StudentPurchasesPage() {
  return (
    <div className="flex w-full max-w-5xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Student</p>
        <h1 className="text-2xl font-semibold tracking-tight">My purchases</h1>
        <p className="text-sm text-muted-foreground">
          Purchase date, plan, amount and the payment status reported by the server.{" "}
          <Link href="/student/store" className="underline">
            Browse the store
          </Link>
        </p>
      </header>
      <AuthenticatedGuard>
        <PurchaseHistoryScreen />
      </AuthenticatedGuard>
    </div>
  );
}
