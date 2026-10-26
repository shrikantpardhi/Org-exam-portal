"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";

import { CommerceLoading } from "@/components/commerce/commerce-states";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { CheckoutFlow } from "@/features/commerce/components/checkout-flow";

function CheckoutRoute() {
  const params = useSearchParams();
  const planId = params.get("planId") ?? "";
  return <CheckoutFlow planId={planId} />;
}

/** Checkout ({@code specs/18-P9-Commerce-V1.5.md §78}). */
export default function StudentCheckoutPage() {
  return (
    <div className="flex w-full max-w-3xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Student · Store</p>
        <h1 className="text-2xl font-semibold tracking-tight">Checkout</h1>
        <p className="text-sm text-muted-foreground">
          The server calculates the payable amount. The client submits only the plan and an optional
          coupon code.
        </p>
      </header>
      <AuthenticatedGuard>
        <Suspense fallback={<CommerceLoading label="Loading checkout…" />}>
          <CheckoutRoute />
        </Suspense>
      </AuthenticatedGuard>
    </div>
  );
}
