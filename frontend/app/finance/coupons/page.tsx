"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { CouponAdmin } from "@/features/commerce/components/coupon-admin";

/** Coupon administration ({@code specs/18-P9-Commerce-V1.5.md §84}). */
export default function FinanceCouponsPage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Finance</p>
        <h1 className="text-2xl font-semibold tracking-tight">Coupons</h1>
        <p className="text-sm text-slate-500">
          Fixed-amount coupons. Validity and the final payable amount are calculated by the server.
        </p>
      </header>
      <AuthenticatedGuard permission="COUPON_VIEW">
        <CouponAdmin />
      </AuthenticatedGuard>
    </main>
  );
}
