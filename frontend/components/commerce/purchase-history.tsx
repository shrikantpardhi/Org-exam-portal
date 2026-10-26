"use client";

import Link from "next/link";

import type { Purchase } from "@/features/commerce/schemas/commerce";

import { formatAmount, formatDate } from "./format";
import { PaymentStatusBadge } from "./payment-status";

/**
 * Student purchase history ({@code specs/18-P9-Commerce-V1.5.md §81}).
 *
 * <p>Shows purchase date, plan, amount and payment status. Provider payloads and internal audit
 * metadata are not part of the projection and are never rendered.
 */
export function PurchaseHistory({ purchases }: { purchases: Purchase[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Purchase history</caption>
        <thead>
          <tr className="border-b border-border">
            <th scope="col" className="py-2 pr-3">
              Purchased
            </th>
            <th scope="col" className="py-2 pr-3">
              Plan
            </th>
            <th scope="col" className="py-2 pr-3">
              Amount
            </th>
            <th scope="col" className="py-2 pr-3">
              Payment
            </th>
            <th scope="col" className="py-2">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {purchases.map((purchase) => (
            <tr key={purchase.id} className="border-b border-border">
              <th scope="row" className="py-2 pr-3 font-normal">
                {formatDate(purchase.purchaseDate) ?? "Not available"}
              </th>
              <td className="py-2 pr-3">{purchase.planName}</td>
              <td className="py-2 pr-3 tabular-nums">
                {formatAmount(purchase.amount) ?? "Not available"}
              </td>
              <td className="py-2 pr-3">
                <PaymentStatusBadge status={purchase.paymentStatus} />
              </td>
              <td className="py-2">
                <Link
                  href={`/student/purchases/${purchase.id}`}
                  className="rounded font-medium text-info underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                >
                  View
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
