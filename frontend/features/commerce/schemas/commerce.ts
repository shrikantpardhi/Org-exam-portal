/**
 * Commerce response and form schemas ({@code specs/18-P9-Commerce-V1.5.md §46-§51}).
 *
 * <p>Amounts are parsed exactly as the server sends them. The frontend never derives a price: every
 * displayed amount comes from a plan, a coupon-validation response, or the purchase the server
 * created ({@code §11.1, §78}). Payment and lifecycle states stay controlled strings so an
 * unrecognised value is never rendered as success.
 */
import { z } from "zod";

const decimal = z
  .union([z.number(), z.string()])
  .nullish()
  .transform((value) => {
    if (value === null || value === undefined) {
      return null;
    }
    const parsed = typeof value === "number" ? value : Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  });

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

const text = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const timestamp = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

export const lifecycleStatusSchema = z
  .string()
  .nullish()
  .transform((value) => (value ?? "UNKNOWN").toUpperCase());

export const paymentStatusSchema = z
  .string()
  .nullish()
  .transform((value) => (value ?? "UNKNOWN").toUpperCase());

export const accessPackageSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: text,
  status: lifecycleStatusSchema,
});

export const packageCollectionSchema = z.object({
  id: z.string(),
  accessPackageId: z.string(),
  assessmentCollectionId: z.string(),
  status: lifecycleStatusSchema,
});

export const subscriptionPlanSchema = z.object({
  id: z.string(),
  accessPackageId: z.string(),
  code: z.string(),
  name: z.string(),
  durationDays: integer,
  price: decimal,
  status: lifecycleStatusSchema,
});

export const couponSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  discountValue: decimal,
  validFrom: timestamp,
  validTo: timestamp,
  status: lifecycleStatusSchema,
});

/** The server's coupon decision. The client never re-derives validity or the final amount. */
export const couponValidationSchema = z.object({
  valid: z.boolean(),
  code: z.string(),
  subscriptionPlanId: text,
  listPrice: decimal,
  discount: decimal,
  finalAmount: decimal,
  currency: text,
  reason: text,
});

export const purchaseSchema = z.object({
  id: z.string(),
  subscriptionPlanId: z.string(),
  accessPackageId: z.string(),
  planName: z.string(),
  couponId: text,
  purchaseDate: timestamp,
  amount: decimal,
  paymentStatus: paymentStatusSchema,
});

export const paymentInitiationSchema = z.object({
  purchaseId: z.string(),
  paymentStatus: paymentStatusSchema,
  paymentReference: text,
  checkoutToken: text,
  nextAction: text,
});

export const paymentViewSchema = z.object({
  purchaseId: z.string(),
  paymentStatus: paymentStatusSchema,
  paymentReference: text,
  paymentMethod: text,
  paymentDate: timestamp,
});

export const accessGrantSchema = z.object({
  id: z.string(),
  userId: text,
  accessPackageId: z.string(),
  packageName: z.string(),
  startDate: timestamp,
  endDate: timestamp,
  status: lifecycleStatusSchema,
  active: z
    .boolean()
    .nullish()
    .transform((value) => value ?? false),
});

export const refundResponseSchema = z.object({
  purchaseId: z.string(),
  paymentStatus: paymentStatusSchema,
  refunded: z.boolean(),
});

export const reconciliationFindingSchema = z.object({
  paymentReference: text,
  purchaseId: text,
  localStatus: text,
  providerStatus: text,
  state: text,
});

export const reconciliationReportSchema = z.object({
  checked: z.number().int(),
  mismatches: z.number().int(),
  findings: z.array(reconciliationFindingSchema).default([]),
});

export type AccessPackage = z.infer<typeof accessPackageSchema>;
export type PackageCollection = z.infer<typeof packageCollectionSchema>;
export type SubscriptionPlan = z.infer<typeof subscriptionPlanSchema>;
export type Coupon = z.infer<typeof couponSchema>;
export type CouponValidation = z.infer<typeof couponValidationSchema>;
export type Purchase = z.infer<typeof purchaseSchema>;
export type PaymentInitiation = z.infer<typeof paymentInitiationSchema>;
export type PaymentView = z.infer<typeof paymentViewSchema>;
export type AccessGrant = z.infer<typeof accessGrantSchema>;
export type RefundResponse = z.infer<typeof refundResponseSchema>;
export type ReconciliationReport = z.infer<typeof reconciliationReportSchema>;

// ------------------------------------------------------------------ form schemas

const isoDateTime = z
  .string()
  .trim()
  .min(1, "A date and time is required")
  .transform((value) => new Date(value).toISOString());

const optionalIsoDateTime = z
  .string()
  .trim()
  .optional()
  .transform((value) => (value ? new Date(value).toISOString() : null));

export const createPackageFormSchema = z.object({
  code: z.string().trim().min(1, "A code is required").max(50),
  name: z.string().trim().min(1, "A name is required").max(200),
  description: z.string().trim().max(2000).optional(),
});

export const createPlanFormSchema = z.object({
  accessPackageId: z.string().min(1, "Choose a package"),
  code: z.string().trim().min(1, "A code is required").max(50),
  name: z.string().trim().min(1, "A name is required").max(150),
  durationDays: z.coerce.number().int().positive("The duration must be positive"),
  price: z.coerce.number().nonnegative("The price cannot be negative"),
});

export const createCouponFormSchema = z.object({
  code: z.string().trim().min(1, "A code is required").max(50),
  name: z.string().trim().min(1, "A name is required").max(150),
  discountValue: z.coerce.number().nonnegative("The discount cannot be negative"),
  validFrom: optionalIsoDateTime,
  validTo: optionalIsoDateTime,
});

export const createAccessGrantFormSchema = z.object({
  userId: z.string().trim().min(1, "A user id is required"),
  accessPackageId: z.string().min(1, "Choose a package"),
  startDate: isoDateTime,
  endDate: optionalIsoDateTime,
});

export const addCollectionFormSchema = z.object({
  collectionId: z.string().trim().min(1, "A collection id is required"),
});

export type CreatePackageForm = z.infer<typeof createPackageFormSchema>;
export type CreatePlanForm = z.infer<typeof createPlanFormSchema>;
export type CreateCouponForm = z.infer<typeof createCouponFormSchema>;
export type CreateAccessGrantForm = z.infer<typeof createAccessGrantFormSchema>;
export type AddCollectionForm = z.infer<typeof addCollectionFormSchema>;
