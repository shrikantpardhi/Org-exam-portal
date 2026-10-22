/**
 * Assessment API layer ({@code specs/13-P4-Assessment-V1.5.md §63-§76, §107, §109, §110}).
 *
 * <p>All Axios calls for Assessment live here; components never call Axios directly. Only frozen
 * endpoints are used — no route is invented for a missing capability.
 */
import { z } from "zod";

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
import {
  assessmentQuestionSchema,
  blueprintResponseSchema,
  blueprintVersionResponseSchema,
  collectionSchema,
  materializeModeSchema,
  previewResponseSchema,
  questionListItemSchema,
  scheduleSchema,
  selectionRuleSchema,
  versionListItemSchema,
  type AssessmentQuestion,
  type BlueprintResponse,
  type BlueprintVersionResponse,
  type Collection,
  type MaterializeMode,
  type PreviewResponse,
  type QuestionListItem,
  type Schedule,
  type ScheduleMode,
  type SelectionRule,
  type VersionListItem,
} from "@/features/assessment/schemas/assessment-builder";
import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/api-client";

function query(params: Record<string, string | number | boolean | undefined>): string {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== "");
  return entries.length === 0 ? "" : "?" + entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&");
}

// ----- Blueprint -----

export async function listBlueprints(): Promise<BlueprintListItem[]> {
  const result = await apiGet<unknown>("/blueprints");
  return z.array(blueprintListItemSchema).parse(result.data);
}

export async function getBlueprint(id: string): Promise<BlueprintResponse> {
  const result = await apiGet<unknown>(`/blueprints/${id}`);
  return blueprintResponseSchema.parse(result.data);
}

export async function createBlueprint(body: {
  code: string;
  name: string;
  description?: string;
}): Promise<BlueprintResponse> {
  const result = await apiPost<unknown>("/blueprints", body);
  return blueprintResponseSchema.parse(result.data);
}

export async function updateBlueprint(
  id: string,
  body: { name?: string; description?: string; status?: string },
): Promise<BlueprintResponse> {
  const result = await apiPut<unknown>(`/blueprints/${id}`, body);
  return blueprintResponseSchema.parse(result.data);
}

export async function listBlueprintVersions(id: string): Promise<BlueprintVersionResponse[]> {
  const result = await apiGet<unknown>(`/blueprints/${id}/versions`);
  return z.array(blueprintVersionResponseSchema).parse(result.data);
}

export async function getBlueprintVersion(id: string, versionId: string): Promise<BlueprintVersionResponse> {
  const result = await apiGet<unknown>(`/blueprints/${id}/versions/${versionId}`);
  return blueprintVersionResponseSchema.parse(result.data);
}

export async function createBlueprintVersion(
  id: string,
  configuration: unknown,
): Promise<BlueprintVersionResponse> {
  const result = await apiPost<unknown>(`/blueprints/${id}/versions`, { configuration });
  return blueprintVersionResponseSchema.parse(result.data);
}

export async function publishBlueprintVersion(id: string, versionId: string): Promise<BlueprintVersionResponse> {
  const result = await apiPost<unknown>(`/blueprints/${id}/versions/${versionId}/publish`);
  return blueprintVersionResponseSchema.parse(result.data);
}

// ----- Assessment -----

export async function listAssessments(
  filters: Record<string, string | number | undefined> = {},
): Promise<AssessmentListItem[]> {
  const result = await apiGet<unknown>(`/assessments${query(filters)}`);
  return z.array(assessmentListItemSchema).parse(result.data);
}

export async function getAssessment(id: string): Promise<AssessmentResponse> {
  const result = await apiGet<unknown>(`/assessments/${id}`);
  return assessmentResponseSchema.parse(result.data);
}

export async function createAssessment(body: {
  code: string;
  name: string;
  description?: string;
}): Promise<AssessmentResponse> {
  const result = await apiPost<unknown>("/assessments", body);
  return assessmentResponseSchema.parse(result.data);
}

export async function updateAssessment(
  id: string,
  body: { name?: string; description?: string; status?: string },
): Promise<AssessmentResponse> {
  const result = await apiPut<unknown>(`/assessments/${id}`, body);
  return assessmentResponseSchema.parse(result.data);
}

export async function getAssessmentVersion(versionId: string): Promise<AssessmentVersionResponse> {
  const result = await apiGet<unknown>(`/assessment-versions/${versionId}`);
  return assessmentVersionResponseSchema.parse(result.data);
}

export async function createAssessmentVersion(
  assessmentId: string,
  body: {
    blueprintVersionId: string;
    durationMinutes: number;
    engineType?: string;
    deliveryPolicyId?: string;
  },
): Promise<AssessmentVersionResponse> {
  const result = await apiPost<unknown>(`/assessments/${assessmentId}/versions`, body);
  return assessmentVersionResponseSchema.parse(result.data);
}

// ----- Assessment version questions -----

export async function listAssessmentQuestions(versionId: string): Promise<AssessmentQuestion[]> {
  const result = await apiGet<unknown>(`/assessment-versions/${versionId}/questions`);
  return z.array(assessmentQuestionSchema).parse(result.data);
}

export async function addAssessmentQuestion(
  versionId: string,
  body: {
    questionVersionId: string;
    displayOrder: number;
    sectionName: string;
    marks: number;
    negativeMarks: number;
  },
): Promise<AssessmentQuestion> {
  const result = await apiPost<unknown>(`/assessment-versions/${versionId}/questions`, body);
  return assessmentQuestionSchema.parse(result.data);
}

export async function updateAssessmentQuestion(
  versionId: string,
  assessmentQuestionId: string,
  body: { displayOrder?: number; sectionName?: string; marks?: number; negativeMarks?: number },
): Promise<AssessmentQuestion> {
  const result = await apiPut<unknown>(
    `/assessment-versions/${versionId}/questions/${assessmentQuestionId}`,
    body,
  );
  return assessmentQuestionSchema.parse(result.data);
}

export async function removeAssessmentQuestion(
  versionId: string,
  assessmentQuestionId: string,
): Promise<void> {
  await apiDelete(`/assessment-versions/${versionId}/questions/${assessmentQuestionId}`);
}

export async function reorderAssessmentQuestions(
  versionId: string,
  questionOrders: { assessmentQuestionId: string; displayOrder: number }[],
): Promise<void> {
  await apiPut(`/assessment-versions/${versionId}/questions/order`, { questionOrders });
}

// ----- Selection rules -----

export async function listSelectionRules(versionId: string): Promise<SelectionRule[]> {
  const result = await apiGet<unknown>(`/assessment-versions/${versionId}/rules`);
  return z.array(selectionRuleSchema).parse(result.data);
}

export async function createSelectionRule(
  versionId: string,
  body: {
    sectionName: string;
    targetCount: number;
    displayOrder: number;
    ruleConfiguration: Record<string, unknown>;
  },
): Promise<SelectionRule> {
  const result = await apiPost<unknown>(`/assessment-versions/${versionId}/rules`, body);
  return selectionRuleSchema.parse(result.data);
}

export async function updateSelectionRule(
  versionId: string,
  ruleId: string,
  body: { targetCount?: number; displayOrder?: number; ruleConfiguration?: Record<string, unknown> },
): Promise<SelectionRule> {
  const result = await apiPut<unknown>(`/assessment-versions/${versionId}/rules/${ruleId}`, body);
  return selectionRuleSchema.parse(result.data);
}

export async function deleteSelectionRule(versionId: string, ruleId: string): Promise<void> {
  await apiDelete(`/assessment-versions/${versionId}/rules/${ruleId}`);
}

export async function materializeSelectionRules(
  versionId: string,
  mode: MaterializeMode,
): Promise<AssessmentQuestion[]> {
  const result = await apiPost<unknown>(`/assessment-versions/${versionId}/rules/materialize`, {
    mode: materializeModeSchema.parse(mode),
  });
  return z.array(assessmentQuestionSchema).parse(result.data);
}

// ----- Validation, preview, publication -----

export async function validateAssessmentVersion(versionId: string): Promise<ValidationResult> {
  const result = await apiPost<unknown>(`/assessment-versions/${versionId}/validate`);
  return validationResultSchema.parse(result.data);
}

export async function getAssessmentPreview(versionId: string): Promise<PreviewResponse> {
  const result = await apiGet<unknown>(`/assessment-versions/${versionId}/preview`);
  return previewResponseSchema.parse(result.data);
}

export async function publishAssessmentVersion(versionId: string): Promise<AssessmentVersionResponse> {
  const result = await apiPost<unknown>(`/assessment-versions/${versionId}/publish`);
  return assessmentVersionResponseSchema.parse(result.data);
}

// ----- Question picker (server-side search, §101) -----

export interface QuestionSearchParams {
  page?: number;
  size?: number;
  search?: string;
  status?: string;
  templateId?: string;
  examId?: string;
  subjectId?: string;
  chapterId?: string;
  topicId?: string;
  tagId?: string;
  languageId?: string;
  difficulty?: string;
  isPublished?: boolean;
  reviewStatus?: string;
  sort?: string;
}

export async function searchQuestions(params: QuestionSearchParams = {}): Promise<QuestionListItem[]> {
  const result = await apiGet<unknown>(`/questions${query({ ...params })}`);
  return z.array(questionListItemSchema).parse(result.data);
}

export async function listQuestionVersions(questionId: string): Promise<VersionListItem[]> {
  const result = await apiGet<unknown>(`/questions/${questionId}/versions`);
  return z.array(versionListItemSchema).parse(result.data);
}

// ----- Schedules -----

export async function listSchedules(
  filters: { assessmentVersionId?: string; status?: string } = {},
): Promise<Schedule[]> {
  const result = await apiGet<unknown>(`/assessment-schedules${query({ ...filters })}`);
  return z.array(scheduleSchema).parse(result.data);
}

export async function createSchedule(body: {
  assessmentVersionId: string;
  mode: ScheduleMode;
  startTime: string;
  endTime: string;
}): Promise<Schedule> {
  const result = await apiPost<unknown>("/assessment-schedules", body);
  return scheduleSchema.parse(result.data);
}

export async function updateSchedule(
  id: string,
  body: { startTime: string; endTime: string },
): Promise<Schedule> {
  const result = await apiPut<unknown>(`/assessment-schedules/${id}`, body);
  return scheduleSchema.parse(result.data);
}

export async function closeSchedule(id: string): Promise<Schedule> {
  const result = await apiPost<unknown>(`/assessment-schedules/${id}/close`);
  return scheduleSchema.parse(result.data);
}

// ----- Collections -----

export async function listCollections(): Promise<Collection[]> {
  const result = await apiGet<unknown>("/assessment-collections");
  return z.array(collectionSchema).parse(result.data);
}

export async function getCollection(id: string): Promise<Collection> {
  const result = await apiGet<unknown>(`/assessment-collections/${id}`);
  return collectionSchema.parse(result.data);
}

export async function createCollection(body: {
  code: string;
  name: string;
  description?: string;
}): Promise<Collection> {
  const result = await apiPost<unknown>("/assessment-collections", body);
  return collectionSchema.parse(result.data);
}

export async function updateCollection(
  id: string,
  body: { name?: string; description?: string; status?: string },
): Promise<Collection> {
  const result = await apiPut<unknown>(`/assessment-collections/${id}`, body);
  return collectionSchema.parse(result.data);
}

export async function addAssessmentToCollection(
  collectionId: string,
  assessmentId: string,
): Promise<Collection> {
  const result = await apiPost<unknown>(`/assessment-collections/${collectionId}/assessments`, {
    assessmentId,
  });
  return collectionSchema.parse(result.data);
}

export async function removeAssessmentFromCollection(
  collectionId: string,
  assessmentId: string,
): Promise<void> {
  await apiDelete(`/assessment-collections/${collectionId}/assessments/${assessmentId}`);
}
