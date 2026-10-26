/**
 * Platform (SUPER_ADMIN) query keys (specs/05-API-Contract-V1.5.md §72).
 */
export const platformKeys = {
  all: () => ["platform"] as const,
  organizations: (filters: unknown) => ["platform", "organizations", filters] as const,
  organization: (id: string) => ["platform", "organization", id] as const,
  providers: () => ["platform", "providers"] as const,
  status: () => ["platform", "status"] as const,
  system: () => ["platform", "system"] as const,
};
