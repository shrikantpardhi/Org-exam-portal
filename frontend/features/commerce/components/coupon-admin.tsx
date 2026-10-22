"use client";

import { useState } from "react";

import { CommerceForm } from "@/components/commerce/commerce-form";
import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
  LifecycleBadge,
} from "@/components/commerce/commerce-states";
import { describeCommerceError, formatAmount, formatDateTime } from "@/components/commerce/format";
import { createCouponFormSchema } from "@/features/commerce/schemas/commerce";
import {
  useArchiveCoupon,
  useCoupons,
  useCreateCoupon,
} from "@/features/commerce/hooks/use-commerce";

/**
 * Coupon administration ({@code specs/18-P9-Commerce-V1.5.md §84}).
 *
 * <p>Only the supported fixed-amount concept is offered. Percentage mode, usage limits and similar
 * unsupported ideas are deliberately absent ({@code §84}).
 */
export function CouponAdmin() {
  const query = useCoupons();
  const create = useCreateCoupon();
  const archive = useArchiveCoupon();
  const [message, setMessage] = useState<string | null>(null);

  if (query.isPending) {
    return <CommerceLoading label="Loading coupons…" />;
  }

  if (query.isError) {
    return (
      <CommerceError
        message={describeCommerceError(query.error, "Coupons could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const coupons = query.data ?? [];

  return (
    <div className="space-y-8">
      <section aria-labelledby="create-coupon-heading" className="space-y-3">
        <h2 id="create-coupon-heading" className="text-lg font-semibold">
          Create a coupon
        </h2>
        <CommerceForm
          schema={createCouponFormSchema}
          submitLabel="Create coupon"
          fields={[
            { name: "code", label: "Code" },
            { name: "name", label: "Name" },
            { name: "discountValue", label: "Discount amount", type: "number", step: "0.01" },
            { name: "validFrom", label: "Valid from", type: "datetime-local" },
            { name: "validTo", label: "Valid to", type: "datetime-local" },
          ]}
          onSubmit={async (values) => {
            await create.mutateAsync({ ...values });
            setMessage("Coupon created.");
          }}
        />
        {message ? (
          <p role="status" className="text-sm text-emerald-700">
            {message}
          </p>
        ) : null}
      </section>

      <section aria-labelledby="coupon-list-heading" className="space-y-3">
        <h2 id="coupon-list-heading" className="text-lg font-semibold">
          Coupons
        </h2>
        {coupons.length === 0 ? (
          <CommerceEmpty message="No coupons exist yet." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Coupons</caption>
              <thead>
                <tr className="border-b border-slate-200">
                  <th scope="col" className="py-2 pr-3">Code</th>
                  <th scope="col" className="py-2 pr-3">Name</th>
                  <th scope="col" className="py-2 pr-3">Discount</th>
                  <th scope="col" className="py-2 pr-3">Valid</th>
                  <th scope="col" className="py-2 pr-3">Status</th>
                  <th scope="col" className="py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {coupons.map((coupon) => (
                  <tr key={coupon.id} className="border-b border-slate-100">
                    <th scope="row" className="py-2 pr-3 font-mono text-xs">{coupon.code}</th>
                    <td className="py-2 pr-3">{coupon.name}</td>
                    <td className="py-2 pr-3 tabular-nums">
                      {formatAmount(coupon.discountValue) ?? "Not available"}
                    </td>
                    <td className="py-2 pr-3 text-xs">
                      {formatDateTime(coupon.validFrom) ?? "Open"} —{" "}
                      {formatDateTime(coupon.validTo) ?? "Open"}
                    </td>
                    <td className="py-2 pr-3"><LifecycleBadge status={coupon.status} /></td>
                    <td className="py-2">
                      {coupon.status !== "ARCHIVED" ? (
                        <button
                          type="button"
                          onClick={() => {
                            archive.mutate(coupon.id);
                          }}
                          className="rounded text-sm font-medium text-red-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
                        >
                          Archive
                        </button>
                      ) : (
                        <span className="text-xs text-slate-500">Archived</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {archive.isError ? (
          <CommerceError
            message={describeCommerceError(archive.error, "The coupon could not be archived.")}
          />
        ) : null}
      </section>
    </div>
  );
}
