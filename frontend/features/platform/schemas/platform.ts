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
  // ADR-0052: which administrator may edit roles in this organization. Null means none is
  // designated, in which case the server refuses role editing for the whole organization.
  primaryAdminUserId: nullableText,
  primaryAdminEmail: nullableText,
});

export const adminSummarySchema = z.object({
  userId: z.string(),
  email: z.string(),
  invitationSentAt: timestamp,
  // ADR-0054: the create response reports whether this administrator is now the primary one.
  // The screen uses it to tell the operator the handover actually happened, rather than making
  // them open the list to guess.
  isPrimary: z.boolean().default(false),
});

/**
 * One organization administrator (ADR-0053, {@code GET .../admins}).
 *
 * <p>{@code isPrimary} is server-reported rather than derived by comparing ids on the client: the
 * screen exists so a human can tell who may edit roles, so the answer comes from the authority.
 */
export const adminListEntrySchema = z.object({
  userId: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  status,
  isPrimary: z.boolean().default(false),
  createdAt: timestamp,
  roles: z.array(z.string()).default([]),
});

export type AdminListEntry = z.infer<typeof adminListEntrySchema>;

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

/**
 * The ten organization profile columns added by migration V37 (ADR-0051, ADR-0056).
 *
 * <p>Every one is optional on both the create and the update wire contract: the columns predate the
 * create form collecting them, and an organization may legitimately have no public address. The
 * order here is the order the fieldsets render, so a column is added in one place.
 */
export const ORGANIZATION_PROFILE_FIELDS = [
  "addressLine1",
  "addressLine2",
  "city",
  "stateRegion",
  "postalCode",
  "country",
  "contactEmail",
  "contactPhone",
  "websiteUrl",
  "logoUrl",
] as const;

export type OrganizationProfileField = (typeof ORGANIZATION_PROFILE_FIELDS)[number];

/**
 * A free-text profile field.
 *
 * <p>An empty string is accepted and means "not provided": the form always submits every field, so
 * rejecting "" here would force the operator to distinguish "never filled in" from "cleared" — a
 * distinction the server deliberately collapses to NULL ({@code blankToNull}).
 */
const optionalProfileText = (max: number) => z.string().trim().max(max).optional();

/**
 * The contact email: optional like every other profile column, but when non-empty it must be a real
 * address. {@code z.string().email()} alone would reject the empty string, which is exactly the
 * "not provided" case, so validity is a refinement rather than a type.
 */
const optionalContactEmail = z
  .string()
  .trim()
  .max(254)
  .refine((value) => value === "" || z.string().email().safeParse(value).success, {
    message: "Enter a valid email address",
  })
  .optional();

/**
 * The profile shape shared by the create and update bodies.
 *
 * <p>The bounds mirror the V37 columns and the backend request records exactly, so a value that
 * passes here cannot be rejected or truncated by the server.
 */
const organizationProfileShape = {
  description: optionalProfileText(500),
  addressLine1: optionalProfileText(200),
  addressLine2: optionalProfileText(200),
  city: optionalProfileText(100),
  stateRegion: optionalProfileText(100),
  postalCode: optionalProfileText(20),
  country: optionalProfileText(100),
  contactEmail: optionalContactEmail,
  contactPhone: optionalProfileText(32),
  websiteUrl: optionalProfileText(500),
  logoUrl: optionalProfileText(500),
} as const;

/**
 * The create body ({@code POST /platform/organizations}, ADR-0056).
 *
 * <p>Only the code and the name are required. The profile is collected at creation because an
 * operator registering an institute already knows its address and contact point; leaving it out
 * would mean creating a bare record and re-opening it to fill in ten fields.
 */
export const createOrganizationSchema = z.object({
  code: z.string().trim().min(1, "A code is required").max(64),
  name: z.string().trim().min(1, "A name is required").max(200),
  ...organizationProfileShape,
});

/**
 * The profile-correction body ({@code PATCH /platform/organizations/{id}}, ADR-0056).
 *
 * <p>A true PATCH: an omitted field is left unchanged and an empty string clears it — that is how an
 * operator removes a stale phone number. {@code name} is the only field refused when blank, because
 * the column is NOT NULL: omitting it keeps the current name, but sending whitespace is a mistake
 * rather than a clear.
 */
export const updateOrganizationSchema = z.object({
  name: z.string().trim().min(1, "A name is required").max(200).optional(),
  ...organizationProfileShape,
});

export const createAdminSchema = z.object({
  firstName: z.string().trim().min(1, "First name is required").max(100),
  lastName: z.string().trim().min(1, "Last name is required").max(100),
  email: z.string().trim().email("Enter a valid email address").max(255),
  mobile: z.string().trim().max(20).optional(),
  // ADR-0054: an organization must designate a primary administrator, because only that account
  // may edit roles. Offering the choice at creation avoids a separate handover step.
  makePrimary: z.boolean().default(false),
});

/**
 * The organization status-change body ({@code PATCH /platform/organizations/{id}/status}).
 *
 * <p>Only ACTIVE and INACTIVE are offered. ARCHIVED is deliberately not part of this union: the
 * server refuses it here (400) because archiving is a different, destructive lifecycle step, and a
 * client that could express it through this control would be lying about what the button does.
 *
 * <p>INACTIVE is not cosmetic: the authentication service refuses a login while the organization is
 * not ACTIVE, so this suspends every user of the tenant.
 */
export const changeOrganizationStatusSchema = z.object({
  status: z.enum(["ACTIVE", "INACTIVE"]),
});

export type OrganizationStatus = z.infer<typeof changeOrganizationStatusSchema>["status"];

/**
 * The primary-administrator transfer body ({@code PATCH .../primary-admin}, ADR-0054).
 *
 * <p>A uuid is required: the server resolves the target by id, not by email, so a typo can never
 * silently designate the wrong account.
 */
export const setPrimaryAdminSchema = z.object({
  userId: z.string().trim().min(1, "A user id is required"),
});

export type OrganizationSummary = z.infer<typeof organizationSummarySchema>;
export type OrganizationDetail = z.infer<typeof organizationDetailSchema>;
export type AdminSummary = z.infer<typeof adminSummarySchema>;
export type DependencyStatus = z.infer<typeof dependencyStatusSchema>;
export type PlatformStatus = z.infer<typeof platformStatusSchema>;
export type PlatformSystem = z.infer<typeof platformSystemSchema>;
export type ProvidersResponse = z.infer<typeof providersResponseSchema>;
export type CreateOrganization = z.infer<typeof createOrganizationSchema>;
/** The profile-correction payload ({@code PATCH /platform/organizations/{id}}, ADR-0056). */
export type UpdateOrganization = z.infer<typeof updateOrganizationSchema>;
/**
 * The editable subset of an organization, keyed exactly like the wire body.
 *
 * <p>Form state is this shape with every field a string (empty meaning "not set"), which is what
 * seeds the edit dialog and what the create form submits; the narrowed {@link UpdateOrganization}
 * is what goes over the wire after validation.
 */
export type OrganizationProfileInput = {
  [K in OrganizationProfileField | "name"]?: string;
};
/**
 * The create-administrator request body.
 *
 * <p>Typed as the schema's <em>input</em>, not its output, because {@code makePrimary} is optional
 * on the wire: the server defaults it to false. A caller that omits it is asking for exactly that
 * default, so the type must permit the omission.
 */
export type CreateAdmin = z.input<typeof createAdminSchema>;
export type SetPrimaryAdmin = z.infer<typeof setPrimaryAdminSchema>;

export interface PlatformPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
