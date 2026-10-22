"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  addPackageCollection,
  archiveAccessPackage,
  archiveCoupon,
  archiveSubscriptionPlan,
  createAccessGrant,
  createAccessPackage,
  createCoupon,
  createPurchase,
  createSubscriptionPlan,
  getAccessPackage,
  getPurchase,
  getPurchasePayment,
  getReconciliation,
  getSubscriptionPlan,
  initiatePayment,
  listAccessGrants,
  listAccessPackages,
  listCoupons,
  listMyPurchases,
  listPackageCollections,
  listSubscriptionPlans,
  refundPurchase,
  removePackageCollection,
  updateAccessPackage,
  validateCoupon,
} from "@/features/commerce/api/commerce-api";
import { commerceKeys } from "@/features/commerce/query-keys";
import type { ApiClientError } from "@/lib/errors";

// ------------------------------------------------------------------ catalog reads

export function useAccessPackages(status = "ACTIVE") {
  return useQuery({
    queryKey: commerceKeys.packages(status),
    queryFn: () => listAccessPackages(status),
  });
}

export function useAllAccessPackages() {
  return useQuery({ queryKey: commerceKeys.packages(undefined), queryFn: () => listAccessPackages() });
}

export function useAccessPackage(packageId: string) {
  return useQuery({
    queryKey: commerceKeys.package(packageId),
    queryFn: () => getAccessPackage(packageId),
    enabled: packageId.length > 0,
  });
}

export function usePackageCollections(packageId: string) {
  return useQuery({
    queryKey: commerceKeys.collections(packageId),
    queryFn: () => listPackageCollections(packageId),
    enabled: packageId.length > 0,
  });
}

export function usePlans(accessPackageId?: string, status?: string, enabled = true) {
  return useQuery({
    queryKey: commerceKeys.plans(accessPackageId, status),
    queryFn: () => listSubscriptionPlans({ accessPackageId, status }),
    enabled,
  });
}

export function usePlan(planId: string) {
  return useQuery({
    queryKey: commerceKeys.plan(planId),
    queryFn: () => getSubscriptionPlan(planId),
    enabled: planId.length > 0,
  });
}

export function useCoupons(status?: string) {
  return useQuery({ queryKey: commerceKeys.coupons(status), queryFn: () => listCoupons(status) });
}

// ------------------------------------------------------------------ student purchases and payment

export function useMyPurchases() {
  return useQuery({ queryKey: commerceKeys.purchases(), queryFn: listMyPurchases });
}

/**
 * One purchase. With {@code pollWhilePending} the read repeats while the server still reports
 * PENDING, so the UI follows the provider confirmation without ever changing the state itself
 * ({@code specs/18-P9-Commerce-V1.5.md §37, §79}).
 */
export function usePurchase(purchaseId: string, pollWhilePending = false) {
  return useQuery({
    queryKey: commerceKeys.purchase(purchaseId),
    queryFn: () => getPurchase(purchaseId),
    enabled: purchaseId.length > 0,
    refetchInterval: pollWhilePending
      ? (query) => (query.state.data?.paymentStatus === "PENDING" ? 5000 : false)
      : false,
  });
}

export function usePurchasePayment(purchaseId: string, enabled = true) {
  return useQuery({
    queryKey: commerceKeys.payment(purchaseId),
    queryFn: () => getPurchasePayment(purchaseId),
    enabled: purchaseId.length > 0 && enabled,
    retry: false,
  });
}

export function useCreatePurchase() {
  const queryClient = useQueryClient();
  return useMutation<
    Awaited<ReturnType<typeof createPurchase>>,
    ApiClientError,
    { subscriptionPlanId: string; couponCode?: string | null }
  >({
    mutationFn: ({ subscriptionPlanId, couponCode }) => createPurchase(subscriptionPlanId, couponCode),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: commerceKeys.purchases() });
    },
  });
}

export function useInitiatePayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (purchaseId: string) => initiatePayment(purchaseId),
    onSuccess: (_data, purchaseId) => {
      void queryClient.invalidateQueries({ queryKey: commerceKeys.purchase(purchaseId) });
      void queryClient.invalidateQueries({ queryKey: commerceKeys.payment(purchaseId) });
    },
  });
}

export function useValidateCoupon() {
  return useMutation({
    mutationFn: ({ code, subscriptionPlanId }: { code: string; subscriptionPlanId: string }) =>
      validateCoupon(code, subscriptionPlanId),
  });
}

// ------------------------------------------------------------------ access grants

export function useAccessGrants(userId?: string) {
  return useQuery({
    queryKey: commerceKeys.grants(userId),
    queryFn: () => listAccessGrants(userId ? { userId } : {}),
    enabled: userId === undefined || userId.length > 0,
  });
}

export function useCreateAccessGrant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createAccessGrant(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "grants"] });
    },
  });
}

// ------------------------------------------------------------------ finance and administration

export function useRefundPurchase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (purchaseId: string) => refundPurchase(purchaseId),
    onSuccess: (_data, purchaseId) => {
      void queryClient.invalidateQueries({ queryKey: commerceKeys.purchase(purchaseId) });
      void queryClient.invalidateQueries({ queryKey: commerceKeys.purchases() });
      void queryClient.invalidateQueries({ queryKey: ["commerce", "grants"] });
    },
  });
}

export function useCreateCoupon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createCoupon(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "coupons"] });
    },
  });
}

export function useArchiveCoupon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (couponId: string) => archiveCoupon(couponId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "coupons"] });
    },
  });
}

export function useCreatePlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createSubscriptionPlan(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "plans"] });
    },
  });
}

export function useArchivePlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (planId: string) => archiveSubscriptionPlan(planId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "plans"] });
    },
  });
}

export function useCreatePackage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createAccessPackage(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "packages"] });
    },
  });
}

export function useUpdatePackage(packageId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => updateAccessPackage(packageId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: commerceKeys.package(packageId) });
      void queryClient.invalidateQueries({ queryKey: ["commerce", "packages"] });
    },
  });
}

export function useArchivePackage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (packageId: string) => archiveAccessPackage(packageId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["commerce", "packages"] });
    },
  });
}

export function useAddPackageCollection(packageId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (collectionId: string) => addPackageCollection(packageId, collectionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: commerceKeys.collections(packageId) });
    },
  });
}

export function useRemovePackageCollection(packageId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (collectionId: string) => removePackageCollection(packageId, collectionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: commerceKeys.collections(packageId) });
    },
  });
}

export function useReconciliation() {
  return useQuery({ queryKey: commerceKeys.reconciliation(), queryFn: getReconciliation });
}
