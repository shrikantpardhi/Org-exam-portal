"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import { CouponInput, type AppliedCoupon } from "@/components/commerce/coupon-input";
import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import { describeCommerceError, shortId } from "@/components/commerce/format";
import { PlanSelector } from "@/components/commerce/plan-selector";
import { PurchaseSummary } from "@/components/commerce/purchase-summary";
import {
  useAccessPackage,
  useCreatePurchase,
  useInitiatePayment,
  usePlan,
  usePlans,
} from "@/features/commerce/hooks/use-commerce";

/**
 * Checkout ({@code specs/18-P9-Commerce-V1.5.md §78}).
 *
 * <p>The client submits only {@code subscriptionPlanId} and {@code couponCode}. The amount comes
 * from the plan or the server's coupon validation, and after creation from the purchase itself.
 * Starting a purchase also initiates payment through the provider boundary; the resulting status is
 * whatever the server reports.
 */
export function CheckoutFlow({ planId }: { planId: string }) {
  const router = useRouter();
  const planQuery = usePlan(planId);
  const packageId = planQuery.data?.accessPackageId ?? "";
  const packageQuery = useAccessPackage(packageId);
  const plansQuery = usePlans(packageId, "ACTIVE", packageId.length > 0);
  const createPurchase = useCreatePurchase();
  const initiatePayment = useInitiatePayment();
  const [selectedPlanId, setSelectedPlanId] = useState(planId);
  const [coupon, setCoupon] = useState<AppliedCoupon | null>(null);

  if (planId.length === 0) {
    return <CommerceEmpty message="No plan was selected." hint="Choose a plan from a package first." />;
  }

  if (planQuery.isPending) {
    return <CommerceLoading label="Loading checkout…" />;
  }

  if (planQuery.isError) {
    return (
      <CommerceError
        message={describeCommerceError(planQuery.error, "The checkout could not be loaded.")}
        onRetry={() => {
          void planQuery.refetch();
        }}
      />
    );
  }

  const plans = plansQuery.data ?? [];
  const activePlanId = plans.some((plan) => plan.id === selectedPlanId)
    ? selectedPlanId
    : plans[0]?.id ?? planId;
  const selectedPlan = plans.find((plan) => plan.id === activePlanId) ?? planQuery.data;
  const packageName = packageQuery.data?.name ?? `Package ${shortId(packageId)}`;
  const amount = coupon ? coupon.finalAmount : selectedPlan.price;
  const starting = createPurchase.isPending || initiatePayment.isPending;

  const startPurchase = async () => {
    if (!selectedPlan) {
      return;
    }
    try {
      const purchase = await createPurchase.mutateAsync({
        subscriptionPlanId: selectedPlan.id,
        couponCode: coupon?.code ?? null,
      });
      try {
        await initiatePayment.mutateAsync(purchase.id);
      } catch {
        // The purchase exists; its detail screen offers a retry without losing the purchase.
      }
      router.push(`/student/purchases/${purchase.id}`);
    } catch {
      // createPurchase.isError renders the server message below.
    }
  };

  return (
    <div className="space-y-6">
      <PlanSelector
        plans={plans.length > 0 ? plans : selectedPlan ? [selectedPlan] : []}
        selectedPlanId={activePlanId}
        onSelect={setSelectedPlanId}
      />

      <CouponInput
        subscriptionPlanId={activePlanId}
        onValidationChange={setCoupon}
        key={activePlanId}
      />

      <PurchaseSummary
        packageName={packageName}
        planName={selectedPlan?.name ?? "Selected plan"}
        durationDays={selectedPlan?.durationDays}
        amount={amount}
        currency={coupon?.currency ?? null}
        couponCode={coupon?.code ?? null}
        amountLabel={
          coupon ? "Payable (calculated by the server)" : "Plan price (set by the server)"
        }
      />

      {createPurchase.isError ? (
        <CommerceError
          message={describeCommerceError(createPurchase.error, "The purchase could not be created.")}
        />
      ) : null}

      <button
        type="button"
        onClick={() => {
          void startPurchase();
        }}
        disabled={starting || !selectedPlan}
        className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
      >
        {starting ? "Starting purchase…" : "Start purchase"}
      </button>
    </div>
  );
}
