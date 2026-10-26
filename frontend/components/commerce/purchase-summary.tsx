"use client";

import { useId } from "react";

import { formatAmount } from "./format";
import { PaymentStatusBadge } from "./payment-status";

/**
 * The checkout / purchase summary ({@code specs/18-P9-Commerce-V1.5.md §78}).
 *
 * <p>The amount shown here is the server's value: a plan price before purchase, or the purchase
 * amount the server returned. No discount is subtracted and no total is computed in the browser.
 */
export function PurchaseSummary({
  packageName,
  planName,
  durationDays,
  amount,
  currency,
  couponCode,
  paymentStatus,
  amountLabel = "Amount (calculated by the server)",
}: {
  packageName: string;
  planName: string;
  durationDays?: number | null;
  amount: number | null;
  currency?: string | null;
  couponCode?: string | null;
  paymentStatus?: string | null;
  amountLabel?: string;
}) {
  const headingId = useId();

  return (
    <section
      aria-labelledby={headingId}
      className="space-y-3 rounded-lg border border-border bg-surface p-4 shadow-sm"
    >
      <h3 id={headingId} className="text-base font-semibold">
        Purchase summary
      </h3>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Package</dt>
          <dd className="mt-0.5 font-medium">{packageName}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Plan</dt>
          <dd className="mt-0.5 font-medium">{planName}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Duration</dt>
          <dd className="mt-0.5">{durationDays ?? "Not specified"} days</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Coupon</dt>
          <dd className="mt-0.5">{couponCode ?? "None"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">{amountLabel}</dt>
          <dd className="mt-0.5 text-lg font-semibold tabular-nums">
            {formatAmount(amount, currency) ?? "Not available"}
          </dd>
        </div>
        {paymentStatus ? (
          <div>
            <dt className="text-xs text-muted-foreground">Payment status</dt>
            <dd className="mt-1">
              <PaymentStatusBadge status={paymentStatus} />
            </dd>
          </div>
        ) : null}
      </dl>
    </section>
  );
}
