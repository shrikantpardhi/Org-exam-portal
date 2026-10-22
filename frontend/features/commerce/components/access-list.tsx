"use client";

import { AccessGrantCard } from "@/components/commerce/access-grant-card";
import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import { describeCommerceError } from "@/components/commerce/format";
import { useAccessGrants } from "@/features/commerce/hooks/use-commerce";

/**
 * My access ({@code specs/18-P9-Commerce-V1.5.md §82}).
 *
 * <p>A read projection over the caller's AccessGrant rows. The backend scopes the list to the
 * caller, and no local action can add access; entitlements are created by payment or by an
 * authorized manual grant.
 */
export function AccessList() {
  const query = useAccessGrants();

  if (query.isPending) {
    return <CommerceLoading label="Loading your access…" />;
  }

  if (query.isError) {
    return (
      <CommerceError
        message={describeCommerceError(query.error, "Your access could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const grants = query.data ?? [];

  if (grants.length === 0) {
    return (
      <CommerceEmpty
        message="No access grants yet."
        hint="Access appears after a payment is confirmed by the server, or after an authorized manual grant."
      />
    );
  }

  const ordered = [...grants].sort((left, right) => Number(right.active) - Number(left.active));

  return (
    <section aria-label="Your access grants" className="grid gap-4 sm:grid-cols-2">
      {ordered.map((grant) => (
        <AccessGrantCard key={grant.id} grant={grant} />
      ))}
    </section>
  );
}
