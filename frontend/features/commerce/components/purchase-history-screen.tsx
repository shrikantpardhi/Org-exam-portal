"use client";

import Link from "next/link";

import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import { describeCommerceError } from "@/components/commerce/format";
import { PurchaseHistory } from "@/components/commerce/purchase-history";
import { useMyPurchases } from "@/features/commerce/hooks/use-commerce";

/** The student's purchase history ({@code specs/18-P9-Commerce-V1.5.md §81}). */
export function PurchaseHistoryScreen() {
  const query = useMyPurchases();

  if (query.isPending) {
    return <CommerceLoading label="Loading your purchases…" />;
  }

  if (query.isError) {
    return (
      <CommerceError
        message={describeCommerceError(query.error, "Your purchases could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const purchases = query.data ?? [];

  if (purchases.length === 0) {
    return (
      <CommerceEmpty
        message="No purchases yet."
        hint={
          <Link href="/student/store" className="underline">
            Browse the store
          </Link>
        }
      />
    );
  }

  return <PurchaseHistory purchases={purchases} />;
}
