/**
 * Administration response and form schemas ({@code specs/20-P11-Administration-V1.5.md §8-§11, §122-§130}).
 *
 * <p>Configuration payloads are typed maps, never bound blindly into an entity. A status is kept as
 * a controlled string so an unknown server state is never rendered as ACTIVE.
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

const integer = z
  .union([z.number(), z.string()])
  .nullish()
  .transform((value) => {
    if (value === null || value === undefined) {
      return null;
    }
    const parsed = typeof value === "number" ? value : Number(value);
    return Number.isInteger(parsed) ? parsed : null;
  });

export const userSummarySchema = z.object({
  id: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  mobile: nullableText,
  status,
  roles: z.array(z.string()).default([]),
});

export const userDetailSchema = userSummarySchema.extend({
  organizationId: nullableText,
  createdOn: timestamp,
  updatedOn: timestamp,
});

// The frozen create body carries no status (a new account is ACTIVE) and no password (onboarding
// assigns a non-usable credential; activation flows through password reset — 10-P1 §56, §58).
export const createUserSchema = z.object({
  firstName: z.string().trim().min(1, "First name is required").max(100),
  lastName: z.string().trim().min(1, "Last name is required").max(100),
  email: z.string().trim().email("Enter a valid email address").max(255),
  mobile: z.string().trim().max(20).optional(),
  roles: z.array(z.string()).min(1, "Select at least one role"),
});

// Email is the immutable login identifier and status changes only through archive, so the frozen
// update body carries neither (10-P1 §53, §68).
export const updateUserSchema = z.object({
  firstName: z.string().trim().min(1, "First name is required").max(100),
  lastName: z.string().trim().min(1, "Last name is required").max(100),
  mobile: z.string().trim().max(20).optional(),
  roles: z.array(z.string()).min(1, "Select at least one role"),
});

export const roleSummarySchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: nullableText,
  status,
});

export const permissionSummarySchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: nullableText,
  status,
});

export const settingKeyDescriptorSchema = z.object({
  key: z.string(),
  type: z.string(),
  defaultValue: z.unknown(),
  mutable: z.boolean().default(true),
  securityClassification: nullableText,
});

export const organizationSettingSchema = z.object({
  id: nullableText,
  organizationId: nullableText,
  configuration: z.record(z.unknown()).default({}),
  registry: z.array(settingKeyDescriptorSchema).default([]),
});

export const businessRuleSchema = z.object({
  id: z.string(),
  code: z.string(),
  configuration: z.record(z.unknown()).default({}),
  status,
});

export const lookupSchema = z.object({
  id: z.string(),
  category: z.string(),
  code: z.string(),
  name: z.string(),
  displayOrder: integer,
  status,
});

export const auditLogSchema = z.object({
  id: z.string(),
  userId: nullableText,
  entityName: nullableText,
  entityId: nullableText,
  action: nullableText,
  remarks: nullableText,
  createdOn: timestamp,
});

export const dashboardSourceSchema = z.object({
  card: z.string(),
  owner: z.string(),
});

export const adminDashboardSchema = z.object({
  activeUsers: z.number().int(),
  students: z.number().int(),
  auditEventsLast24h: z.number().int(),
  businessRules: z.number().int(),
  lookups: z.number().int(),
  activeLookupValues: z.number().int(),
  generatedOn: timestamp,
  sources: z.array(dashboardSourceSchema).default([]),
});

export const healthComponentSchema = z.object({
  name: z.string(),
  status: z.string(),
  latencyMs: integer,
  message: nullableText,
  detail: z.record(z.string()).default({}),
});

export const systemHealthSchema = z.object({
  overall: z.string(),
  checkedOn: timestamp,
  components: z.array(healthComponentSchema).default([]),
});

export type UserSummary = z.infer<typeof userSummarySchema>;
export type UserDetail = z.infer<typeof userDetailSchema>;
export type CreateUser = z.infer<typeof createUserSchema>;
export type UpdateUser = z.infer<typeof updateUserSchema>;
export type RoleSummary = z.infer<typeof roleSummarySchema>;
export type PermissionSummary = z.infer<typeof permissionSummarySchema>;
export type SettingKeyDescriptor = z.infer<typeof settingKeyDescriptorSchema>;
export type OrganizationSetting = z.infer<typeof organizationSettingSchema>;
export type BusinessRule = z.infer<typeof businessRuleSchema>;
export type Lookup = z.infer<typeof lookupSchema>;
export type AuditLog = z.infer<typeof auditLogSchema>;
export type AdminDashboard = z.infer<typeof adminDashboardSchema>;
export type HealthComponent = z.infer<typeof healthComponentSchema>;
export type SystemHealth = z.infer<typeof systemHealthSchema>;

/** Standard server page metadata for admin tables. */
export interface AdminPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ------------------------------------------------------------------ json helpers

/** Parses an administrator-entered JSON object; arrays and scalars are rejected. */
export function parseJsonObject(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value);
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("The configuration must be a JSON object.");
  }
  return parsed as Record<string, unknown>;
}

const jsonObjectText = z
  .string()
  .trim()
  .refine((value) => {
    try {
      parseJsonObject(value);
      return true;
    } catch {
      return false;
    }
  }, "Enter a JSON object, for example {\"key\": \"value\"}");

export const businessRuleCreateSchema = z.object({
  code: z.string().trim().min(1, "A code is required").max(100),
  configuration: jsonObjectText,
});

export const businessRuleUpdateSchema = z.object({
  configuration: jsonObjectText,
});

export const lookupCreateSchema = z.object({
  category: z.string().trim().min(1, "A category is required").max(100),
  code: z.string().trim().min(1, "A code is required").max(100),
  name: z.string().trim().min(1, "A name is required").max(150),
  displayOrder: z.preprocess(
    (value) => (value === "" || value === undefined ? undefined : value),
    z.coerce.number().int().optional(),
  ),
});

export const lookupUpdateSchema = z.object({
  name: z.string().trim().min(1, "A name is required").max(150),
  displayOrder: z.preprocess(
    (value) => (value === "" || value === undefined ? undefined : value),
    z.coerce.number().int().optional(),
  ),
});

const optionalUuid = z
  .string()
  .trim()
  .optional()
  .refine(
    (value) =>
      !value || /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value),
    "Enter a valid identifier",
  );

export const auditFilterSchema = z.object({
  userId: optionalUuid,
  entityName: z.string().trim().max(100).optional(),
  entityId: optionalUuid,
  action: z.string().trim().max(100).optional(),
  from: z.string().trim().optional(),
  to: z.string().trim().optional(),
});

export type BusinessRuleCreate = z.infer<typeof businessRuleCreateSchema>;
export type BusinessRuleUpdate = z.infer<typeof businessRuleUpdateSchema>;
export type LookupCreate = z.infer<typeof lookupCreateSchema>;
export type LookupUpdate = z.infer<typeof lookupUpdateSchema>;
export type AuditFilterValues = z.infer<typeof auditFilterSchema>;

/** Converts a {@code datetime-local} value to the ISO instant the API expects. */
export function toIsoInstant(value: string | undefined): string | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}
