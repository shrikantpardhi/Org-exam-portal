/**
 * Commerce query keys ({@code specs/18-P9-Commerce-V1.5.md §76}).
 *
 * <p>Server catalog, purchase, payment and entitlement state live in TanStack Query; form and
 * selection state stay local to the screen and never become a second source of truth.
 */
export const commerceKeys = {
  packages: (status?: string) => ["commerce", "packages", status ?? "ALL"] as const,
  package: (packageId: string) => ["commerce", "package", packageId] as const,
  collections: (packageId: string) => ["commerce", "package", packageId, "collections"] as const,
  plans: (accessPackageId?: string, status?: string) =>
    ["commerce", "plans", accessPackageId ?? "ALL", status ?? "ALL"] as const,
  plan: (planId: string) => ["commerce", "plan", planId] as const,
  coupons: (status?: string) => ["commerce", "coupons", status ?? "ALL"] as const,
  purchases: () => ["commerce", "purchases", "mine"] as const,
  purchase: (purchaseId: string) => ["commerce", "purchase", purchaseId] as const,
  payment: (purchaseId: string) => ["commerce", "purchase", purchaseId, "payment"] as const,
  grants: (userId?: string) => ["commerce", "grants", userId ?? "mine"] as const,
  reconciliation: () => ["commerce", "reconciliation"] as const,
};
