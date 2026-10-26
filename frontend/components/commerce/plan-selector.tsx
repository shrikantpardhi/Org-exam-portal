"use client";

import { useId } from "react";

import type { SubscriptionPlan } from "@/features/commerce/schemas/commerce";

import { CommerceEmpty } from "./commerce-states";
import { formatAmount } from "./format";

/**
 * Plan selection ({@code specs/18-P9-Commerce-V1.5.md §77, §78}).
 *
 * <p>Durations and prices are read from the server's plan response. Native radio inputs provide
 * keyboard navigation and a visible focus ring.
 */
export function PlanSelector({
  plans,
  selectedPlanId,
  onSelect,
  legend = "Choose a plan",
}: {
  plans: SubscriptionPlan[];
  selectedPlanId: string;
  onSelect: (planId: string) => void;
  legend?: string;
}) {
  const groupName = useId();

  if (plans.length === 0) {
    return <CommerceEmpty message="No active plan is available for this package." />;
  }

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-semibold text-foreground">{legend}</legend>
      <ul className="space-y-2">
        {plans.map((plan) => {
          const selected = plan.id === selectedPlanId;
          return (
            <li key={plan.id}>
              <label
                className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 ${
                  selected ? "border-blue-400 bg-info-subtle" : "border-border bg-surface"
                } focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-blue-600`}
              >
                <input
                  type="radio"
                  name={groupName}
                  value={plan.id}
                  checked={selected}
                  onChange={() => onSelect(plan.id)}
                  className="mt-1"
                />
                <span className="flex-1">
                  <span className="block text-sm font-medium text-foreground">{plan.name}</span>
                  <span className="mt-0.5 block text-xs text-muted-foreground">
                    {plan.durationDays ?? "Unknown"} days of access
                  </span>
                </span>
                <span className="text-sm font-semibold tabular-nums text-foreground">
                  {formatAmount(plan.price) ?? "Price not set"}
                </span>
              </label>
            </li>
          );
        })}
      </ul>
    </fieldset>
  );
}
