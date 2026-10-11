/**
 * Assessment API layer ({@code specs/13-P4-Assessment-V1.5.md §64-§76}).
 *
 * <p>All Axios calls for Assessment live here; components never call Axios directly.
 */
import { apiGet, apiPost } from "@/lib/api-client";
import {
  assessmentListItemSchema,
  assessmentResponseSchema,
  assessmentVersionResponseSchema,
  blueprintListItemSchema,
  validationResultSchema,
  type AssessmentListItem,
  type AssessmentResponse,
  type AssessmentVersionResponse,
  type BlueprintListItem,
  type ValidationResult,
} from "@/features/assessment/schemas/assessment";
import { z } from "zod";

function query(params: Record<string, string | number | undefined>): string {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== "");
  return entries.length === 0 ? "" : "?" + entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&");
}

export async function listBlueprints(): Promise<BlueprintListItem[]> {
  const result = await apiGet<unknown>("/blueprints");
  return z.array(blueprintListItemSchema).parse(result.data);
}

export async function listAssessments(filters: Record<string, string | number | undefined> = {}): Promise<AssessmentListItem[]> {
  const result = await apiGet<unknown>(`/assessments${query(filters)}`);
  return z.array(assessmentListItemSchema).parse(result.data);
}

export async function getAssessment(id: string): Promise<AssessmentResponse> {
  const result = await apiGet<unknown>(`/assessments/${id}`);
  return assessmentResponseSchema.parse(result.data);
}

export async function getAssessmentVersion(versionId: string): Promise<AssessmentVersionResponse> {
  const result = await apiGet<unknown>(`/assessment-versions/${versionId}`);
  return assessmentVersionResponseSchema.parse(result.data);
}

export async function validateAssessmentVersion(versionId: string): Promise<ValidationResult> {
  const result = await apiPost<unknown>(`/assessment-versions/${versionId}/validate`);
  return validationResultSchema.parse(result.data);
}

export async function publishAssessmentVersion(versionId: string): Promise<AssessmentVersionResponse> {
  const result = await apiPost<unknown>(`/assessment-versions/${versionId}/publish`);
  return assessmentVersionResponseSchema.parse(result.data);
}
