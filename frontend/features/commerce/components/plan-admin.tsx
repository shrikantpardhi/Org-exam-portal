"use client";

import { useState } from "react";

import { CommerceForm } from "@/components/commerce/commerce-form";
import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
  LifecycleBadge,
} from "@/components/commerce/commerce-states";
import { describeCommerceError, formatAmount, shortId } from "@/components/commerce/format";
import { createPlanFormSchema } from "@/features/commerce/schemas/commerce";
import {
  useAllAccessPackages,
  useArchivePlan,
  useCreatePlan,
  usePlans,
} from "@/features/commerce/hooks/use-commerce";

/** Subscription plan administration ({@code specs/18-P9-Commerce-V1.5.md §85}). */
export function PlanAdmin() {
  const plansQuery = usePlans(undefined, undefined);
  const packagesQuery = useAllAccessPackages();
  const create = useCreatePlan();
  const archive = useArchivePlan();
  const [message, setMessage] = useState<string | null>(null);

  if (plansQuery.isPending || packagesQuery.isPending) {
    return <CommerceLoading label="Loading plans…" />;
  }

  if (plansQuery.isError) {
    return (
      <CommerceError
        message={describeCommerceError(plansQuery.error, "Plans could not be loaded.")}
        onRetry={() => {
          void plansQuery.refetch();
        }}
      />
    );
  }

  const plans = plansQuery.data ?? [];
  const packages = packagesQuery.data ?? [];
  const packageNames = new Map(packages.map((pkg) => [pkg.id, pkg.name]));

  return (
    <div className="space-y-8">
      <section aria-labelledby="create-plan-heading" className="space-y-3">
        <h2 id="create-plan-heading" className="text-lg font-semibold">
          Create a plan
        </h2>
        <CommerceForm
          schema={createPlanFormSchema}
          submitLabel="Create plan"
          fields={[
            {
              name: "accessPackageId",
              label: "Access package",
              type: "select",
              options: packages.map((pkg) => ({ value: pkg.id, label: `${pkg.name} (${pkg.code})` })),
            },
            { name: "code", label: "Code" },
            { name: "name", label: "Name" },
            { name: "durationDays", label: "Duration (days)", type: "number" },
            { name: "price", label: "Price", type: "number", step: "0.01" },
          ]}
          onSubmit={async (values) => {
            await create.mutateAsync({ ...values });
            setMessage("Plan created.");
          }}
        />
        {message ? (
          <p role="status" className="text-sm text-emerald-700">
            {message}
          </p>
        ) : null}
      </section>

      <section aria-labelledby="plan-list-heading" className="space-y-3">
        <h2 id="plan-list-heading" className="text-lg font-semibold">
          Plans
        </h2>
        {plans.length === 0 ? (
          <CommerceEmpty message="No plans exist yet." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Subscription plans</caption>
              <thead>
                <tr className="border-b border-border">
                  <th scope="col" className="py-2 pr-3">Code</th>
                  <th scope="col" className="py-2 pr-3">Name</th>
                  <th scope="col" className="py-2 pr-3">Package</th>
                  <th scope="col" className="py-2 pr-3">Duration</th>
                  <th scope="col" className="py-2 pr-3">Price</th>
                  <th scope="col" className="py-2 pr-3">Status</th>
                  <th scope="col" className="py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {plans.map((plan) => (
                  <tr key={plan.id} className="border-b border-border">
                    <th scope="row" className="py-2 pr-3 font-mono text-xs">{plan.code}</th>
                    <td className="py-2 pr-3">{plan.name}</td>
                    <td className="py-2 pr-3">
                      {packageNames.get(plan.accessPackageId) ?? shortId(plan.accessPackageId)}
                    </td>
                    <td className="py-2 pr-3">{plan.durationDays ?? "Not set"} days</td>
                    <td className="py-2 pr-3 tabular-nums">{formatAmount(plan.price) ?? "Not available"}</td>
                    <td className="py-2 pr-3"><LifecycleBadge status={plan.status} /></td>
                    <td className="py-2">
                      {plan.status !== "ARCHIVED" ? (
                        <button
                          type="button"
                          onClick={() => {
                            archive.mutate(plan.id);
                          }}
                          className="rounded text-sm font-medium text-red-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
                        >
                          Archive
                        </button>
                      ) : (
                        <span className="text-xs text-muted-foreground">Archived</span>
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
            message={describeCommerceError(archive.error, "The plan could not be archived.")}
          />
        ) : null}
      </section>
    </div>
  );
}
