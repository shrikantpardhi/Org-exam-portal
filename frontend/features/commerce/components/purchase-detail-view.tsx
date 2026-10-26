"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";

import {
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import {
  describeCommerceError,
  isNotFound,
  shortId,
} from "@/components/commerce/format";
import { PaymentStatus } from "@/components/commerce/payment-status";
import { PurchaseSummary } from "@/components/commerce/purchase-summary";
import {
  useAccessPackage,
  useInitiatePayment,
  usePurchase,
  usePurchasePayment,
} from "@/features/commerce/hooks/use-commerce";

/**
 * A student's purchase and its payment state ({@code specs/18-P9-Commerce-V1.5.md §79, §80}).
 *
 * <p>The screen follows the server: it polls while the purchase is PENDING and can be checked
 * manually, but it never sets a payment state. On SUCCESS it invalidates the entitlement and
 * catalog reads so access appears without a re-login; content itself is unlocked by the backend.
 */
export function PurchaseDetailView({ purchaseId }: { purchaseId: string }) {
  const queryClient = useQueryClient();
  const purchaseQuery = usePurchase(purchaseId, true);
  const packageId = purchaseQuery.data?.accessPackageId ?? "";
  const packageQuery = useAccessPackage(packageId);
  const paymentQuery = usePurchasePayment(purchaseId, Boolean(purchaseQuery.data));
  const initiate = useInitiatePayment();

  const status = purchaseQuery.data?.paymentStatus ?? "UNKNOWN";

  useEffect(() => {
    if (status === "SUCCESS") {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "grants"] });
      void queryClient.invalidateQueries({ queryKey: ["commerce", "packages"] });
    }
  }, [status, queryClient]);

  if (purchaseId.length === 0) {
    return <CommerceError message="No purchase was selected." />;
  }

  if (purchaseQuery.isPending) {
    return <CommerceLoading label="Loading purchase…" />;
  }

  if (purchaseQuery.isError) {
    return (
      <CommerceError
        message={
          isNotFound(purchaseQuery.error)
            ? "This purchase is not available."
            : describeCommerceError(purchaseQuery.error, "The purchase could not be loaded.")
        }
        onRetry={() => {
          void purchaseQuery.refetch();
        }}
      />
    );
  }

  const purchase = purchaseQuery.data;
  if (!purchase) {
    return <CommerceLoading label="Loading purchase…" />;
  }

  const payment = paymentQuery.data ?? null;
  const canPay = purchase.paymentStatus === "PENDING" || purchase.paymentStatus === "FAILED";

  const checkStatus = async () => {
    await Promise.all([purchaseQuery.refetch(), paymentQuery.refetch()]);
  };

  const pay = async () => {
    try {
      await initiate.mutateAsync(purchaseId);
      await checkStatus();
    } catch {
      // The mutation error is rendered below.
    }
  };

  return (
    <div className="space-y-6">
      <PurchaseSummary
        packageName={packageQuery.data?.name ?? `Package ${shortId(packageId)}`}
        planName={purchase.planName}
        durationDays={null}
        amount={purchase.amount}
        currency={null}
        couponCode={purchase.couponId ? `Applied (coupon ${shortId(purchase.couponId)})` : null}
        paymentStatus={purchase.paymentStatus}
        amountLabel="Purchase amount (recorded by the server)"
      />

      <PaymentStatus
        status={status}
        paymentDate={payment?.paymentDate}
        paymentReference={payment?.paymentReference}
        paymentMethod={payment?.paymentMethod}
      />

      {paymentQuery.isError && !isNotFound(paymentQuery.error) ? (
        <p role="alert" className="text-sm text-red-600">
          {describeCommerceError(paymentQuery.error, "The payment detail could not be loaded.")}
        </p>
      ) : null}

      {initiate.isError ? (
        <CommerceError
          message={describeCommerceError(initiate.error, "The payment could not be started.")}
        />
      ) : null}

      <div className="flex flex-wrap gap-3">
        {canPay ? (
          <button
            type="button"
            onClick={() => {
              void pay();
            }}
            disabled={initiate.isPending}
            className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
          >
            {initiate.isPending ? "Starting payment…" : "Pay now"}
          </button>
        ) : null}
        <button
          type="button"
          onClick={() => {
            void checkStatus();
          }}
          className="rounded border border-input bg-surface px-4 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Check payment status
        </button>
        <Link
          href="/student/purchases"
          className="rounded px-4 py-2 text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          All purchases
        </Link>
      </div>

      {purchase.paymentStatus === "SUCCESS" ? (
        <div role="status" className="space-y-1 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <p className="font-medium">Payment confirmed. Your access has been applied by the server.</p>
          <Link href="/student/access" className="underline">
            View my access
          </Link>
        </div>
      ) : null}

      {purchase.paymentStatus === "REFUNDED" ? (
        <p role="status" className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
          This purchase was refunded and its access grant was revoked.
        </p>
      ) : null}
    </div>
  );
}
