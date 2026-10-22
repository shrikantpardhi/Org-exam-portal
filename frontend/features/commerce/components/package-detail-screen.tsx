"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import {
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import { describeCommerceError } from "@/components/commerce/format";
import { PackageDetail } from "@/components/commerce/package-detail";
import {
  useAccessPackage,
  usePackageCollections,
  usePlans,
} from "@/features/commerce/hooks/use-commerce";

/** Package detail and plan choice ({@code specs/18-P9-Commerce-V1.5.md §77}). */
export function PackageDetailScreen({ packageId }: { packageId: string }) {
  const router = useRouter();
  const pkgQuery = useAccessPackage(packageId);
  const collectionsQuery = usePackageCollections(packageId);
  const plansQuery = usePlans(packageId, "ACTIVE");
  const [selectedPlanId, setSelectedPlanId] = useState("");

  if (packageId.length === 0) {
    return <CommerceError message="No package was selected." />;
  }

  if (pkgQuery.isPending || collectionsQuery.isPending || plansQuery.isPending) {
    return <CommerceLoading label="Loading package…" />;
  }

  if (pkgQuery.isError) {
    return (
      <CommerceError
        message={describeCommerceError(pkgQuery.error, "The package could not be loaded.")}
        onRetry={() => {
          void pkgQuery.refetch();
        }}
      />
    );
  }

  if (!pkgQuery.data) {
    return <CommerceLoading label="Loading package…" />;
  }

  const plans = plansQuery.data ?? [];
  const activePlanId = plans.some((plan) => plan.id === selectedPlanId)
    ? selectedPlanId
    : plans[0]?.id ?? "";

  return (
    <PackageDetail
      pkg={pkgQuery.data}
      collections={collectionsQuery.data ?? []}
      plans={plans}
      selectedPlanId={activePlanId}
      onSelectPlan={setSelectedPlanId}
      onContinue={() => {
        if (activePlanId) {
          router.push(`/student/checkout?planId=${activePlanId}`);
        }
      }}
    />
  );
}
