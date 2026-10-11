/**
 * Content/Question Bank API layer ({@code specs/12-P3-Content-V1.5.md §87-§91}).
 *
 * <p>All Axios calls for Content live here; components never call Axios directly.
 */
import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/api-client";
import {
  optionSchema,
  questionListItemSchema,
  questionResponseSchema,
  questionTemplateSchema,
  versionResponseSchema,
  type QuestionForm,
  type QuestionListItem,
  type QuestionOption,
  type QuestionResponse,
  type QuestionTemplate,
  type VersionResponse,
} from "@/features/content/schemas/content";
import { z } from "zod";

export interface QuestionListParams {
  page?: number;
  size?: number;
  search?: string;
  difficulty?: string;
  status?: string;
  reviewStatus?: string;
  isPublished?: boolean;
}

function query(params: Record<string, string | number | boolean | undefined>): string {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== "");
  return entries.length === 0 ? "" : "?" + entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&");
}

export async function listQuestionTemplates(): Promise<QuestionTemplate[]> {
  const result = await apiGet<unknown>("/question-templates");
  return z.array(questionTemplateSchema).parse(result.data);
}

export async function listQuestions(params: QuestionListParams = {}): Promise<QuestionListItem[]> {
  const result = await apiGet<unknown>(`/questions${query({ ...params })}`);
  return z.array(questionListItemSchema).parse(result.data);
}

export async function getQuestion(id: string): Promise<QuestionResponse> {
  const result = await apiGet<unknown>(`/questions/${id}`);
  return questionResponseSchema.parse(result.data);
}

export async function createQuestion(form: QuestionForm): Promise<QuestionResponse> {
  const result = await apiPost<unknown>("/questions", form);
  return questionResponseSchema.parse(result.data);
}

export async function createVersion(questionId: string, body: Partial<QuestionForm>): Promise<VersionResponse> {
  const result = await apiPost<unknown>(`/questions/${questionId}/versions`, body);
  return versionResponseSchema.parse(result.data);
}

export async function getVersion(questionId: string, versionId: string): Promise<VersionResponse> {
  const result = await apiGet<unknown>(`/questions/${questionId}/versions/${versionId}`);
  return versionResponseSchema.parse(result.data);
}

export async function listOptions(questionId: string, versionId: string): Promise<QuestionOption[]> {
  const result = await apiGet<unknown>(`/questions/${questionId}/versions/${versionId}/options`);
  return z.array(optionSchema).parse(result.data);
}

export async function submitReview(questionId: string, versionId: string): Promise<VersionResponse> {
  const result = await apiPost<unknown>(`/questions/${questionId}/versions/${versionId}/submit-review`);
  return versionResponseSchema.parse(result.data);
}

export async function approveVersion(
  questionId: string,
  versionId: string,
  remarks: string,
): Promise<VersionResponse> {
  const result = await apiPost<unknown>(`/questions/${questionId}/versions/${versionId}/approve`, { remarks });
  return versionResponseSchema.parse(result.data);
}

export async function rejectVersion(
  questionId: string,
  versionId: string,
  remarks: string,
): Promise<VersionResponse> {
  const result = await apiPost<unknown>(`/questions/${questionId}/versions/${versionId}/reject`, { remarks });
  return versionResponseSchema.parse(result.data);
}

export async function publishVersion(questionId: string, versionId: string): Promise<VersionResponse> {
  const result = await apiPost<unknown>(`/questions/${questionId}/versions/${versionId}/publish`);
  return versionResponseSchema.parse(result.data);
}

export async function addOption(
  questionId: string,
  versionId: string,
  body: { optionKey: string; content: string; displayOrder: number; isCorrect: boolean },
): Promise<QuestionOption> {
  const result = await apiPost<unknown>(`/questions/${questionId}/versions/${versionId}/options`, body);
  return optionSchema.parse(result.data);
}

export async function updateOption(
  questionId: string,
  versionId: string,
  optionId: string,
  body: { content?: string; displayOrder?: number; isCorrect?: boolean },
): Promise<QuestionOption> {
  const result = await apiPut<unknown>(
    `/questions/${questionId}/versions/${versionId}/options/${optionId}`,
    body,
  );
  return optionSchema.parse(result.data);
}

export async function deleteOption(questionId: string, versionId: string, optionId: string): Promise<void> {
  await apiDelete(`/questions/${questionId}/versions/${versionId}/options/${optionId}`);
}
