"use client";

import { useEffect, useId, useState, type FormEvent } from "react";

import type { CouponValidation } from "@/features/commerce/schemas/commerce";
import { useValidateCoupon } from "@/features/commerce/hooks/use-commerce";

import { describeCommerceError, formatAmount } from "./format";

export interface AppliedCoupon {
  code: string;
  finalAmount: number | null;
  currency: string | null;
}

/**
 * Coupon entry ({@code specs/18-P9-Commerce-V1.5.md §33, §77, §78}).
 *
 * <p>Validity, discount and the final payable amount are the server's answer. The component only
 * displays them; it never subtracts a discount locally. A plan change clears the coupon because a
 * coupon is validated against one plan.
 */
export function CouponInput({
  subscriptionPlanId,
  onValidationChange,
}: {
  subscriptionPlanId: string;
  onValidationChange: (coupon: AppliedCoupon | null) => void;
}) {
  const inputId = useId();
  const [code, setCode] = useState("");
  const [validation, setValidation] = useState<CouponValidation | null>(null);
  const mutation = useValidateCoupon();

  useEffect(() => {
    setCode("");
    setValidation(null);
    onValidationChange(null);
  }, [subscriptionPlanId, onValidationChange]);

  const apply = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = code.trim();
    if (!trimmed || !subscriptionPlanId) {
      setValidation(null);
      onValidationChange(null);
      return;
    }
    try {
      const result = await mutation.mutateAsync({ code: trimmed, subscriptionPlanId });
      setValidation(result);
      onValidationChange(
        result.valid
          ? { code: result.code, finalAmount: result.finalAmount, currency: result.currency }
          : null,
      );
    } catch {
      setValidation(null);
      onValidationChange(null);
    }
  };

  const clear = () => {
    setCode("");
    setValidation(null);
    onValidationChange(null);
    mutation.reset();
  };

  return (
    <section aria-labelledby={`${inputId}-heading`} className="space-y-2 rounded-lg border border-slate-200 bg-white p-4">
      <h3 id={`${inputId}-heading`} className="text-base font-semibold">
        Coupon
      </h3>
      <form onSubmit={apply} className="flex flex-wrap items-end gap-2">
        <div className="flex-1 space-y-1">
          <label htmlFor={inputId} className="text-sm font-medium">
            Coupon code
          </label>
          <input
            id={inputId}
            value={code}
            onChange={(event) => {
              setCode(event.target.value);
              setValidation(null);
              onValidationChange(null);
            }}
            disabled={subscriptionPlanId.length === 0}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            autoComplete="off"
          />
        </div>
        <button
          type="submit"
          disabled={subscriptionPlanId.length === 0 || mutation.isPending}
          className="rounded border border-slate-300 bg-white px-3 py-2 text-sm font-medium disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          {mutation.isPending ? "Checking…" : "Apply coupon"}
        </button>
        {validation ? (
          <button
            type="button"
            onClick={clear}
            className="rounded border border-slate-300 bg-white px-3 py-2 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Remove
          </button>
        ) : null}
      </form>

      {validation && validation.valid ? (
        <p role="status" className="text-sm text-emerald-700">
          Coupon {validation.code} applied. Discount{" "}
          {formatAmount(validation.discount, validation.currency) ?? "not available"}; payable{" "}
          {formatAmount(validation.finalAmount, validation.currency) ?? "not available"}.
        </p>
      ) : null}

      {validation && !validation.valid ? (
        <p role="alert" className="text-sm text-amber-800">
          Coupon not applied: {validation.reason ?? "the coupon is not valid for this plan."}
        </p>
      ) : null}

      {mutation.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeCommerceError(mutation.error, "The coupon could not be validated.")}
        </p>
      ) : null}
    </section>
  );
}
