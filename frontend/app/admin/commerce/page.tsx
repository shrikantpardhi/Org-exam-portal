"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { CommerceAdmin } from "@/features/commerce/components/commerce-admin";

/** Commerce administration ({@code specs/18-P9-Commerce-V1.5.md §86, §87}). */
export default function AdminCommercePage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Administration</p>
        <h1 className="text-2xl font-semibold tracking-tight">Commerce</h1>
        <p className="text-sm text-slate-500">
          Access packages, assessment-collection mapping and payment reconciliation.
        </p>
      </header>
      <AuthenticatedGuard permission="ACCESS_PACKAGE_MANAGE">
        <CommerceAdmin />
      </AuthenticatedGuard>
    </main>
  );
}
