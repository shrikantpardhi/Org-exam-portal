"use client";

import { formatDateTime, paymentStatusLabel } from "./format";

/**
 * Payment state display ({@code specs/18-P9-Commerce-V1.5.md §79, §80}).
 *
 * <p>These are UI labels over the server's authoritative status. The component never derives a
 * status from a client action; it only renders what the purchase or payment read returned.
 */
const TONES: Record<string, string> = {
  PENDING: "border-amber-200 bg-amber-50 text-amber-800",
  SUCCESS: "border-emerald-200 bg-emerald-50 text-emerald-700",
  FAILED: "border-red-200 bg-red-50 text-red-700",
  REFUNDED: "border-border bg-muted text-muted-foreground",
  UNKNOWN: "border-border bg-muted text-muted-foreground",
};

const DETAILS: Record<string, string> = {
  PENDING: "Waiting for the payment provider to confirm. The platform applies the result when the provider calls back.",
  SUCCESS: "The payment is confirmed. The server applies the access grant; this page never unlocks content locally.",
  FAILED: "The payment did not complete. No access grant was applied; you can retry the payment.",
  REFUNDED: "The payment was refunded and its access grant was revoked.",
  UNKNOWN: "The server returned a payment status this screen does not recognise.",
};

export function PaymentStatusBadge({ status }: { status: string }) {
  const key = (status ?? "").toUpperCase() || "UNKNOWN";
  return (
    <span
      data-payment-status={key}
      className={`rounded-full border px-2 py-0.5 text-xs font-medium ${
        TONES[key] ?? TONES.UNKNOWN
      }`}
    >
      {paymentStatusLabel(status)}
    </span>
  );
}

export function PaymentStatus({
  status,
  paymentDate,
  paymentReference,
  paymentMethod,
}: {
  status: string;
  paymentDate?: string | null;
  paymentReference?: string | null;
  paymentMethod?: string | null;
}) {
  const key = (status ?? "").toUpperCase() || "UNKNOWN";
  const paidOn = formatDateTime(paymentDate);

  return (
    <section
      aria-label="Payment status"
      role="status"
      className={`space-y-2 rounded-lg border p-4 ${TONES[key] ?? TONES.UNKNOWN}`}
    >
      <div className="flex flex-wrap items-center gap-2">
        <PaymentStatusBadge status={status} />
        {paidOn ? (
          <span className="text-xs">
            <time dateTime={paymentDate ?? undefined}>{paidOn}</time>
          </span>
        ) : null}
      </div>
      <p className="text-sm">{DETAILS[key] ?? DETAILS.UNKNOWN}</p>
      {paymentMethod ? <p className="text-xs">Method: {paymentMethod}</p> : null}
      {paymentReference ? (
        <p className="break-all font-mono text-xs">Reference: {paymentReference}</p>
      ) : null}
    </section>
  );
}
