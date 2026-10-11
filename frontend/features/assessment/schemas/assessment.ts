/**
 * Assessment schemas ({@code specs/13-P4-Assessment-V1.5.md §87}).
 *
 * <p>Responses are validated at the boundary. The engine type is display-only and never a
 * frontend branching key.
 */
import { z } from "zod";

export const engineTypeSchema = z.enum(["TCS_ION", "IBPS", "NTA", "UPSC", "RAILWAY", "CUSTOM"]);
export const scheduleModeSchema = z.enum(["PRACTICE", "EXAM"]);
export type EngineType = z.infer<typeof engineTypeSchema>;

export const blueprintListItemSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  status: z.string(),
  updatedOn: z.string().nullish(),
});
export type BlueprintListItem = z.infer<typeof blueprintListItemSchema>;

export const assessmentVersionSummarySchema = z.object({
  id: z.string(),
  version: z.number(),
  published: z.boolean(),
  engineType: engineTypeSchema.nullish(),
  durationMinutes: z.number().nullish(),
});

export const assessmentListItemSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  status: z.string(),
  updatedOn: z.string().nullish(),
});
export type AssessmentListItem = z.infer<typeof assessmentListItemSchema>;

export const assessmentResponseSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  status: z.string(),
  versions: z.array(assessmentVersionSummarySchema),
});
export type AssessmentResponse = z.infer<typeof assessmentResponseSchema>;

export const validationIssueSchema = z.object({ code: z.string(), message: z.string() });
export const validationResultSchema = z.object({
  valid: z.boolean(),
  errors: z.array(validationIssueSchema),
});
export type ValidationResult = z.infer<typeof validationResultSchema>;

export const assessmentVersionResponseSchema = z.object({
  id: z.string(),
  assessmentId: z.string(),
  blueprintVersionId: z.string(),
  version: z.number(),
  totalQuestions: z.number().nullish(),
  totalMarks: z.union([z.number(), z.string()]).nullish(),
  durationMinutes: z.number().nullish(),
  engineType: engineTypeSchema.nullish(),
  deliveryPolicyId: z.string().nullish(),
  published: z.boolean(),
});
export type AssessmentVersionResponse = z.infer<typeof assessmentVersionResponseSchema>;
