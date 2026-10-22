"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { PlanAdmin } from "@/features/commerce/components/plan-admin";

/** Subscription plan administration ({@code specs/18-P9-Commerce-V1.5.md §85}). */
export default function FinancePlansPage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Finance</p>
        <h1 className="text-2xl font-semibold tracking-tight">Subscription plans</h1>
        <p className="text-sm text-slate-500">
          Plans bind an access package to a duration and a price. The server validates uniqueness and
          pricing.
        </p>
      </header>
      <AuthenticatedGuard permission="SUBSCRIPTION_PLAN_VIEW">
        <PlanAdmin />
      </AuthenticatedGuard>
    </main>
  );
}
