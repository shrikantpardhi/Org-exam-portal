"use client";

import type {
  AccessPackage,
  PackageCollection,
  SubscriptionPlan,
} from "@/features/commerce/schemas/commerce";

import { CommerceEmpty } from "./commerce-states";
import { PlanSelector } from "./plan-selector";
import { shortId } from "./format";

/**
 * Package detail with its assessment collections and plans
 * ({@code specs/18-P9-Commerce-V1.5.md §77, §87}).
 *
 * <p>The collection list is the frozen mapping read; no collection name is invented because the
 * commerce read model exposes only the assessment-collection identifier.
 */
export function PackageDetail({
  pkg,
  collections,
  plans,
  selectedPlanId,
  onSelectPlan,
  onContinue,
  continuing = false,
}: {
  pkg: AccessPackage;
  collections: PackageCollection[];
  plans: SubscriptionPlan[];
  selectedPlanId: string;
  onSelectPlan: (planId: string) => void;
  onContinue?: () => void;
  continuing?: boolean;
}) {
  return (
    <section className="space-y-6">
      <header className="space-y-1">
        <p className="font-mono text-xs text-slate-500">{pkg.code}</p>
        <h2 className="text-xl font-semibold tracking-tight">{pkg.name}</h2>
        <p className="text-sm text-slate-500">{pkg.description ?? "No description provided."}</p>
      </header>

      <section aria-labelledby="package-collections" className="space-y-2">
        <h3 id="package-collections" className="text-base font-semibold">
          Included assessment collections
        </h3>
        {collections.length === 0 ? (
          <CommerceEmpty message="No collection mapping available." />
        ) : (
          <ul className="space-y-1 text-sm">
            {collections.map((collection) => (
              <li key={collection.id} className="rounded border border-slate-200 bg-white px-3 py-2">
                <code className="text-xs" title={collection.assessmentCollectionId}>
                  {shortId(collection.assessmentCollectionId)}
                </code>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <PlanSelector plans={plans} selectedPlanId={selectedPlanId} onSelect={onSelectPlan} />
        {onContinue ? (
          <button
            type="button"
            onClick={onContinue}
            disabled={selectedPlanId.length === 0 || continuing}
            className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
          >
            {continuing ? "Opening checkout…" : "Continue to checkout"}
          </button>
        ) : null}
      </section>
    </section>
  );
}
