import { z } from "zod";

import { apiGet, apiPut } from "@/lib/api-client";

/**
 * The caller's organization identity and profile ({@code GET /api/v1/admin/organization-config}).
 *
 * <p>Every profile field is nullable: an organization that has not filled in its profile yet returns
 * nulls rather than empty strings, and the form renders empty inputs for them.
 */
export const organizationProfileSchema = z.object({
  organization: z.object({
    name: z.string(),
    code: z.string(),
    timezone: z.string(),
    description: z.string().nullable().optional(),
    addressLine1: z.string().nullable().optional(),
    addressLine2: z.string().nullable().optional(),
    city: z.string().nullable().optional(),
    stateRegion: z.string().nullable().optional(),
    postalCode: z.string().nullable().optional(),
    country: z.string().nullable().optional(),
    contactEmail: z.string().nullable().optional(),
    contactPhone: z.string().nullable().optional(),
    websiteUrl: z.string().nullable().optional(),
    logoUrl: z.string().nullable().optional(),
  }),
});

export type OrganizationProfile = z.infer<typeof organizationProfileSchema>["organization"];

const organizationNameSchema = z.object({
  organization: z.object({ name: z.string() }),
});

/** Resolves the signed-in ADMIN's organization name from {@code GET /admin/organization-config}. */
export async function getOrganizationName(): Promise<string> {
  const result = await apiGet<unknown>("/admin/organization-config");
  return organizationNameSchema.parse(result.data).organization.name;
}

/** Reads the caller's organization profile. */
export async function getOrganizationProfile(): Promise<OrganizationProfile> {
  const result = await apiGet<unknown>("/admin/organization-config");
  return organizationProfileSchema.parse(result.data).organization;
}

/**
 * Updates the caller's organization profile ({@code PUT /admin/organization-config/profile}).
 *
 * <p>The organization is derived from the authenticated principal on the server, so no id is sent.
 * A blank value is transmitted as null, which is how an optional field is cleared.
 */
export async function updateOrganizationProfile(input: Partial<OrganizationProfile>): Promise<OrganizationProfile> {
  const payload: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    if (value === undefined) {
      continue;
    }
    const trimmed = typeof value === "string" ? value.trim() : value;
    payload[key] = typeof trimmed === "string" && trimmed.length === 0 ? null : trimmed;
  }
  const result = await apiPut<unknown>("/admin/organization-config/profile", payload);
  return organizationProfileSchema.shape.organization.parse(result.data);
}
