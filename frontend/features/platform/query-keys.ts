/**
 * Platform (SUPER_ADMIN) query keys (specs/05-API-Contract-V1.5.md §72).
 *
 * <p>Admin-list keys carry the whole filter object, so switching the status filter or the page can
 * never reuse another view's cached page (ADR-0054).
 */
export const platformKeys = {
  all: () => ["platform"] as const,
  organizations: (filters: unknown) => ["platform", "organizations", filters] as const,
  organization: (id: string) => ["platform", "organization", id] as const,
  organizationAdmins: (id: string, filters: unknown) =>
    ["platform", "organization", id, "admins", filters] as const,
  /**
   * The primary-administrator segment names the transfer target.
   *
   * <p>Prefix matching is the point: the prefix key
   * {@code ["platform", "organization", id]} is what a transfer invalidates, so both the
   * organization detail and the admin lists are refreshed by one call.
   */
  organizationPrimaryAdmin: (id: string) =>
    ["platform", "organization", id, "primary-admin"] as const,
  providers: () => ["platform", "providers"] as const,
  status: () => ["platform", "status"] as const,
  system: () => ["platform", "system"] as const,
};
