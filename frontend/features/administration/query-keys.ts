/**
 * Administration query keys ({@code specs/20-P11-Administration-V1.5.md §124}).
 *
 * <p>List keys include their filters and page so a filter change never reuses another page's data.
 */
export const adminKeys = {
  all: () => ["administration"] as const,
  dashboard: () => ["administration", "dashboard"] as const,
  users: (filters: unknown) => ["administration", "users", filters] as const,
  user: (userId: string) => ["administration", "user", userId] as const,
  roles: (sort?: string) => ["administration", "roles", sort ?? "DEFAULT"] as const,
  rolePermissions: (roleId: string) => ["administration", "roles", roleId, "permissions"] as const,
  permissions: (page: number, sort?: string) =>
    ["administration", "permissions", page, sort ?? "DEFAULT"] as const,
  settings: () => ["administration", "settings"] as const,
  organizationProfile: () => ["administration", "organization-profile"] as const,
  businessRules: (status?: string) => ["administration", "business-rules", status ?? "ALL"] as const,
  lookups: (filters: unknown) => ["administration", "lookups", filters] as const,
  audit: (filters: unknown) => ["administration", "audit", filters] as const,
  health: () => ["administration", "system-health"] as const,
};
