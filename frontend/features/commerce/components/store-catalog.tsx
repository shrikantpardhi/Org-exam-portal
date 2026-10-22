"use client";

import { PackageCard } from "@/components/commerce/package-card";
import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import { describeCommerceError } from "@/components/commerce/format";
import { useAccessPackages } from "@/features/commerce/hooks/use-commerce";

/**
 * Student store ({@code specs/18-P9-Commerce-V1.5.md §77}).
 *
 * <p>The catalog is ordinary cached metadata; it grants nothing. Only active packages are shown,
 * and a purchase is later created from a plan the server prices.
 */
export function StoreCatalog() {
  const query = useAccessPackages("ACTIVE");

  if (query.isPending) {
    return <CommerceLoading label="Loading the store…" />;
  }

  if (query.isError) {
    return (
      <CommerceError
        message={describeCommerceError(query.error, "The store could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const packages = query.data ?? [];

  if (packages.length === 0) {
    return (
      <CommerceEmpty
        message="No packages are available yet."
        hint="The catalog appears once an access package is published."
      />
    );
  }

  return (
    <section aria-label="Access packages" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {packages.map((pkg) => (
        <PackageCard key={pkg.id} pkg={pkg} href={`/student/packages/${pkg.id}`} />
      ))}
    </section>
  );
}
