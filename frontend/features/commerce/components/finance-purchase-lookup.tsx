"use client";

import { useState, type FormEvent } from "react";

import {
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import {
  describeCommerceError,
  formatAmount,
  formatDateTime,
  isNotFound,
} from "@/components/commerce/format";
import { PaymentStatus } from "@/components/commerce/payment-status";
import { PurchaseSummary } from "@/components/commerce/purchase-summary";
import {
  useAccessPackage,
  usePurchase,
  useRefundPurchase,
} from "@/features/commerce/hooks/use-commerce";
import { useAuth } from "@/lib/auth/auth-context";

/** Finance purchase, payment and refund workspace ({@code specs/18-P9-Commerce-V1.5.md §83, §89}). */
export function FinancePurchaseLookup({ mode }: { mode: "purchases" | "payments" | "refunds" }) {
  const { can } = useAuth();
  const [draftId, setDraftId] = useState("");
  const [purchaseId, setPurchaseId] = useState("");
  const [confirmingRefund, setConfirmingRefund] = useState(false);
  const purchaseQuery = usePurchase(purchaseId, false);
  const packageQuery = useAccessPackage(purchaseQuery.data?.accessPackageId ?? "");
  const refund = useRefundPurchase();

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPurchaseId(draftId.trim());
    setConfirmingRefund(false);
    refund.reset();
  };

  return (
    <div className="space-y-6">
      <form onSubmit={search} className="flex flex-wrap items-end gap-2" aria-label="Find a purchase">
        <div className="flex-1 space-y-1">
          <label htmlFor="finance-purchase-id" className="text-sm font-medium">
            Purchase id
          </label>
          <input
            id="finance-purchase-id"
            value={draftId}
            onChange={(event) => setDraftId(event.target.value)}
            placeholder="Paste a purchase UUID"
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          className="rounded bg-primary px-4 py-2 text-sm font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
        >
          Load purchase
        </button>
      </form>

      {purchaseId.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Load a purchase by its identifier. The backend enforces the finance permission and the
          ownership scope; this workspace never widens either.
        </p>
      ) : purchaseQuery.isPending ? (
        <CommerceLoading label="Loading purchase…" />
      ) : purchaseQuery.isError ? (
        <CommerceError
          message={
            isNotFound(purchaseQuery.error)
              ? "No purchase matches that identifier."
              : describeCommerceError(purchaseQuery.error, "The purchase could not be loaded.")
          }
          onRetry={() => {
            void purchaseQuery.refetch();
          }}
        />
      ) : purchaseQuery.data ? (
        <>
          <PurchaseSummary
            packageName={packageQuery.data?.name ?? "Access package"}
            planName={purchaseQuery.data.planName}
            durationDays={null}
            amount={purchaseQuery.data.amount}
            currency={null}
            couponCode={purchaseQuery.data.couponId ? "Applied" : null}
            paymentStatus={purchaseQuery.data.paymentStatus}
            amountLabel="Purchase amount (recorded by the server)"
          />

          <dl className="grid gap-3 rounded-lg border border-border bg-surface p-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs text-muted-foreground">Purchased</dt>
              <dd className="mt-0.5">
                {formatDateTime(purchaseQuery.data.purchaseDate) ?? "Not available"}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Purchase id</dt>
              <dd className="mt-0.5 break-all font-mono text-xs">{purchaseQuery.data.id}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Amount</dt>
              <dd className="mt-0.5 tabular-nums">
                {formatAmount(purchaseQuery.data.amount) ?? "Not available"}
              </dd>
            </div>
          </dl>

          {mode === "payments" ? (
            <section aria-labelledby="finance-payment-heading" className="space-y-2">
              <h2 id="finance-payment-heading" className="text-base font-semibold">
                Payment
              </h2>
              <PaymentStatus status={purchaseQuery.data.paymentStatus} />
              <p className="text-sm text-muted-foreground">
                The provider detail is restricted to the purchase owner. The status above is the
                authoritative purchase status returned by the server.
              </p>
            </section>
          ) : null}

          {mode === "refunds" ? (
            <section aria-labelledby="finance-refund-heading" className="space-y-3">
              <h2 id="finance-refund-heading" className="text-base font-semibold">
                Refund
              </h2>
              {purchaseQuery.data.paymentStatus === "REFUNDED" ? (
                <p role="status" className="rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
                  This purchase is already refunded.
                </p>
              ) : purchaseQuery.data.paymentStatus !== "SUCCESS" ? (
                <p className="text-sm text-muted-foreground">
                  Only a successful payment can be refunded. The backend validates the current
                  payment state before calling the provider.
                </p>
              ) : !can("PURCHASE_REFUND") ? (
                <p role="alert" className="text-sm text-red-600">
                  You do not have permission to refund purchases.
                </p>
              ) : refund.isSuccess && refund.data?.refunded ? (
                <div role="status" className="space-y-1 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
                  <p className="font-medium">Refund confirmed by the server.</p>
                  <p>The purchase is now {refund.data.paymentStatus} and its access was revoked.</p>
                </div>
              ) : (
                <div className="space-y-2 rounded-lg border border-border bg-surface p-4">
                  <p className="text-sm text-muted-foreground">
                    A refund calls the payment provider and revokes the access grant created by this
                    purchase. The effect is applied by the server.
                  </p>
                  {confirmingRefund ? (
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          void refund
                            .mutateAsync(purchaseId)
                            .then(() => purchaseQuery.refetch())
                            .catch(() => undefined);
                        }}
                        disabled={refund.isPending}
                        className="rounded bg-red-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
                      >
                        {refund.isPending ? "Refunding…" : "Confirm refund"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmingRefund(false)}
                        className="rounded border border-input bg-surface px-4 py-2 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmingRefund(true)}
                      className="rounded border border-red-300 bg-surface px-4 py-2 text-sm font-medium text-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
                    >
                      Refund purchase
                    </button>
                  )}
                  {refund.isError ? (
                    <p role="alert" className="text-sm text-red-600">
                      {describeCommerceError(refund.error, "The refund could not be completed.")}
                    </p>
                  ) : null}
                </div>
              )}
            </section>
          ) : null}
        </>
      ) : (
        <CommerceLoading label="Loading purchase…" />
      )}
    </div>
  );
}
