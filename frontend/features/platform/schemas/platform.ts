/**
 * Platform (SUPER_ADMIN) response and form schemas (specs/05-API-Contract-V1.5.md §72).
 *
 * Mirrors the administration schema conventions: a status is a controlled string so an unknown
 * server state is never rendered as ACTIVE, and nullable wire fields collapse to null.
 */
import { z } from "zod";

const status = z
  .string()
  .nullish()
  .transform((value) => (value ?? "UNKNOWN").toUpperCase());

const nullableText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const timestamp = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

export const organizationSummarySchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  status,
});

export const organizationDetailSchema = organizationSummarySchema.extend({
  description: nullableText,
  adminCount: z.number().int().default(0),
  userCount: z.number().int().default(0),
  // Organization profile (V37, ADR-0051). Nullable because an organization may not have filled
  // its profile in yet; the screen renders "Not set" rather than failing to parse.
  addressLine1: nullableText,
  addressLine2: nullableText,
  city: nullableText,
  stateRegion: nullableText,
  postalCode: nullableText,
  country: nullableText,
  contactEmail: nullableText,
  contactPhone: nullableText,
  websiteUrl: nullableText,
  logoUrl: nullableText,
});

export const adminSummarySchema = z.object({
  userId: z.string(),
  email: z.string(),
  invitationSentAt: timestamp,
});

export const dependencyStatusSchema = z.object({
  name: z.string(),
  status: z.string(),
});

export const platformStatusSchema = z.object({
  organizationCount: z.number().int().default(0),
  adminCount: z.number().int().default(0),
  userCount: z.number().int().default(0),
  seedStatus: nullableText,
  systemHealth: nullableText,
});

export const platformSystemSchema = z.object({
  appVersion: nullableText,
  frontendVersion: nullableText,
  gitCommit: nullableText,
  buildDate: nullableText,
  migrationVersion: z.number().int().nullish().transform((value) => value ?? null),
  lastMigration: nullableText,
  appliedOn: timestamp,
  uptimeSeconds: z.number().int().default(0),
  dependencies: z.array(dependencyStatusSchema).default([]),
});

export const emailProviderViewSchema = z.object({
  configured: z.boolean(),
  providerName: nullableText,
  fromAddress: nullableText,
  updatedAt: timestamp,
});

export const paymentProviderViewSchema = z.object({
  configured: z.boolean(),
  providerName: nullableText,
  webhookSecretSet: z.boolean(),
  updatedAt: timestamp,
});

export const providersResponseSchema = z.object({
  email: emailProviderViewSchema,
  payment: paymentProviderViewSchema,
});

export const createOrganizationSchema = z.object({
  code: z.string().trim().min(1, "A code is required").max(64),
  name: z.string().trim().min(1, "A name is required").max(200),
  description: z.string().trim().max(500).optional(),
});

export const createAdminSchema = z.object({
  firstName: z.string().trim().min(1, "First name is required").max(100),
  lastName: z.string().trim().min(1, "Last name is required").max(100),
  email: z.string().trim().email("Enter a valid email address").max(255),
  mobile: z.string().trim().max(20).optional(),
});

export type OrganizationSummary = z.infer<typeof organizationSummarySchema>;
export type OrganizationDetail = z.infer<typeof organizationDetailSchema>;
export type AdminSummary = z.infer<typeof adminSummarySchema>;
export type DependencyStatus = z.infer<typeof dependencyStatusSchema>;
export type PlatformStatus = z.infer<typeof platformStatusSchema>;
export type PlatformSystem = z.infer<typeof platformSystemSchema>;
export type ProvidersResponse = z.infer<typeof providersResponseSchema>;
export type CreateOrganization = z.infer<typeof createOrganizationSchema>;
export type CreateAdmin = z.infer<typeof createAdminSchema>;

export interface PlatformPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
