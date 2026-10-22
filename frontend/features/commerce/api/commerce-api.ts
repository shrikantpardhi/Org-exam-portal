/**
 * Commerce API layer ({@code specs/18-P9-Commerce-V1.5.md §29, §39-§42}).
 *
 * <p>Only frozen endpoints are called. The client submits a plan id (and optionally a coupon code),
 * never an amount; the server calculates the price and the payment state ({@code §11.1, §71, §78}).
 */
import type { z } from "zod";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/api-client";
import {
  accessGrantSchema,
  accessPackageSchema,
  couponSchema,
  couponValidationSchema,
  packageCollectionSchema,
  paymentInitiationSchema,
  paymentViewSchema,
  purchaseSchema,
  reconciliationReportSchema,
  refundResponseSchema,
  subscriptionPlanSchema,
  type AccessGrant,
  type AccessPackage,
  type PackageCollection,
  type PaymentInitiation,
  type PaymentView,
  type Purchase,
  type SubscriptionPlan,
} from "@/features/commerce/schemas/commerce";

async function load<T extends z.ZodTypeAny>(path: string, schema: T, params?: Record<string, unknown>): Promise<z.infer<T>> {
  const result = await apiGet<unknown>(path, params);
  return schema.parse(result.data);
}

async function send<T extends z.ZodTypeAny>(path: string, schema: T, body?: unknown): Promise<z.infer<T>> {
  const result = await apiPost<unknown>(path, body);
  return schema.parse(result.data);
}

// ------------------------------------------------------------------ catalog

export function listAccessPackages(status?: string): Promise<AccessPackage[]> {
  return load("/access-packages", accessPackageSchema.array(), status ? { status } : undefined);
}

export function getAccessPackage(packageId: string): Promise<AccessPackage> {
  return load(`/access-packages/${packageId}`, accessPackageSchema);
}

export function listPackageCollections(packageId: string): Promise<PackageCollection[]> {
  return load(`/access-packages/${packageId}/collections`, packageCollectionSchema.array());
}

export function addPackageCollection(packageId: string, collectionId: string): Promise<PackageCollection> {
  return send(`/access-packages/${packageId}/collections`, packageCollectionSchema, { collectionId });
}

export async function removePackageCollection(packageId: string, collectionId: string): Promise<void> {
  await apiDelete(`/access-packages/${packageId}/collections/${collectionId}`);
}

export function createAccessPackage(body: Record<string, unknown>): Promise<AccessPackage> {
  return send("/access-packages", accessPackageSchema, body);
}

export function updateAccessPackage(packageId: string, body: Record<string, unknown>): Promise<AccessPackage> {
  return apiPut<unknown>(`/access-packages/${packageId}`, body).then((result) =>
    accessPackageSchema.parse(result.data),
  );
}

export async function archiveAccessPackage(packageId: string): Promise<void> {
  await apiDelete(`/access-packages/${packageId}`);
}

export function listSubscriptionPlans(params: { accessPackageId?: string; status?: string } = {}): Promise<SubscriptionPlan[]> {
  return load("/subscription-plans", subscriptionPlanSchema.array(), {
    ...(params.accessPackageId ? { accessPackageId: params.accessPackageId } : {}),
    ...(params.status ? { status: params.status } : {}),
  });
}

export function getSubscriptionPlan(planId: string): Promise<SubscriptionPlan> {
  return load(`/subscription-plans/${planId}`, subscriptionPlanSchema);
}

export function createSubscriptionPlan(body: Record<string, unknown>): Promise<SubscriptionPlan> {
  return send("/subscription-plans", subscriptionPlanSchema, body);
}

export async function archiveSubscriptionPlan(planId: string): Promise<void> {
  await apiDelete(`/subscription-plans/${planId}`);
}

export function listCoupons(status?: string) {
  return load("/coupons", couponSchema.array(), status ? { status } : undefined);
}

export function createCoupon(body: Record<string, unknown>) {
  return send("/coupons", couponSchema, body);
}

export async function archiveCoupon(couponId: string): Promise<void> {
  await apiDelete(`/coupons/${couponId}`);
}

/** The server decides validity, discount and the final payable amount ({@code §33}). */
export function validateCoupon(code: string, subscriptionPlanId: string) {
  return send("/coupons/validate", couponValidationSchema, { code, subscriptionPlanId });
}

// ------------------------------------------------------------------ student purchase and payment

export function createPurchase(subscriptionPlanId: string, couponCode?: string | null): Promise<Purchase> {
  return send("/purchases", purchaseSchema, {
    subscriptionPlanId,
    ...(couponCode ? { couponCode } : {}),
  });
}

export function listMyPurchases(): Promise<Purchase[]> {
  return load("/purchases", purchaseSchema.array());
}

export function getPurchase(purchaseId: string): Promise<Purchase> {
  return load(`/purchases/${purchaseId}`, purchaseSchema);
}

export function initiatePayment(purchaseId: string): Promise<PaymentInitiation> {
  return send(`/purchases/${purchaseId}/payment`, paymentInitiationSchema);
}

export function getPurchasePayment(purchaseId: string): Promise<PaymentView> {
  return load(`/purchases/${purchaseId}/payment`, paymentViewSchema);
}

export function refundPurchase(purchaseId: string) {
  return send(`/purchases/${purchaseId}/refund`, refundResponseSchema);
}

// ------------------------------------------------------------------ access grants

export function listAccessGrants(params: { userId?: string; active?: boolean } = {}): Promise<AccessGrant[]> {
  return load("/access-grants", accessGrantSchema.array(), {
    ...(params.userId ? { userId: params.userId } : {}),
    ...(params.active === undefined ? {} : { active: params.active }),
  });
}

export function createAccessGrant(body: Record<string, unknown>): Promise<AccessGrant> {
  return send("/access-grants", accessGrantSchema, body);
}

export function getReconciliation() {
  return load("/access-grants/reconciliation", reconciliationReportSchema);
}
