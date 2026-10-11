/**
 * Shared response schemas.
 *
 * Zod validates API responses at the boundary so that unexpected server shapes fail fast instead
 * of leaking into the UI. The delivery-configuration schemas belong to the ExamEngine/CDUI
 * contract and are added later; they are not duplicated here
 * (specs/09-P0-Foundation-V1.5.md §47).
 */
import { z } from "zod";

export const paginationSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});

export function apiEnvelopeSchema<T extends z.ZodTypeAny>(data: T) {
  return z.object({
    success: z.literal(true),
    message: z.string(),
    data,
    pagination: paginationSchema.optional(),
  });
}

export const errorItemSchema = z.object({
  field: z.string().nullish(),
  code: z.string(),
  message: z.string(),
});

export const apiErrorEnvelopeSchema = z.object({
  success: z.literal(false),
  message: z.string(),
  errors: z.array(errorItemSchema).optional(),
  timestamp: z.string().optional(),
  path: z.string().optional(),
  requestId: z.string().nullish(),
});
