/**
 * Content/Question Bank API layer ({@code specs/12-P3-Content-V1.5.md §87-§91, §105-§109}).
 *
 * <p>All Axios calls for Content live here; components never call Axios directly. Every response
 * is parsed with its Zod schema at this boundary, so an unexpected server shape fails fast instead
 * of leaking into a component.
 */
import { apiClient, apiDelete, apiGet, apiPost, apiPut } from "@/lib/api-client";
import {
  assetSchema,
  optionSchema,
  previousExamSchema,
  questionListItemSchema,
  questionResponseSchema,
  questionTemplateSchema,
  versionListItemSchema,
  versionResponseSchema,
  versionTextContentSchema,
  type ContentAsset,
  type CreateQuestionPayload,
  type OptionRequest,
  type PreviousExam,
  type PreviousExamRequest,
  type PreviousExamUpdateRequest,
  type QuestionListItem,
  type QuestionMetadataUpdate,
  type QuestionOption,
  type QuestionResponse,
  type QuestionTemplate,
  type TemplateForm,
  type VersionCreateRequest,
  type VersionListItem,
  type VersionResponse,
  type VersionTextContent,
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
  templateId?: string;
  examId?: string;
  subjectId?: string;
  chapterId?: string;
  topicId?: string;
  tagId?: string;
  languageId?: string;
}

function query(params: Record<string, string | number | boolean | undefined>): string {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== "");
  return entries.length === 0 ? "" : "?" + entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&");
}

export async function listQuestionTemplates(): Promise<QuestionTemplate[]> {
  const result = await apiGet<unknown>("/question-templates");
  return z.array(questionTemplateSchema).parse(result.data);
}

export async function getQuestionTemplate(id: string): Promise<QuestionTemplate> {
  const result = await apiGet<unknown>(`/question-templates/${id}`);
  return questionTemplateSchema.parse(result.data);
}

export async function createQuestionTemplate(body: TemplateForm): Promise<QuestionTemplate> {
  const result = await apiPost<unknown>("/question-templates", body);
  return questionTemplateSchema.parse(result.data);
}

export async function updateQuestionTemplate(
  id: string,
  body: Partial<TemplateForm> & { status?: string },
): Promise<QuestionTemplate> {
  const result = await apiPut<unknown>(`/question-templates/${id}`, body);
  return questionTemplateSchema.parse(result.data);
}

export async function listQuestions(params: QuestionListParams = {}): Promise<QuestionListItem[]> {
  const result = await apiGet<unknown>(`/questions${query({ ...params })}`);
  return z.array(questionListItemSchema).parse(result.data);
}

export async function getQuestion(id: string): Promise<QuestionResponse> {
  const result = await apiGet<unknown>(`/questions/${id}`);
  return questionResponseSchema.parse(result.data);
}

export async function createQuestion(form: CreateQuestionPayload): Promise<QuestionResponse> {
  const result = await apiPost<unknown>("/questions", form);
  return questionResponseSchema.parse(result.data);
}

/** Draft-level metadata only; versioned content changes by creating a new version ({@code §89}). */
export async function updateQuestion(
  questionId: string,
  body: QuestionMetadataUpdate,
): Promise<QuestionResponse> {
  const result = await apiPut<unknown>(`/questions/${questionId}`, body);
  return questionResponseSchema.parse(result.data);
}

/**
 * The version history for a question ({@code §110}).
 *
 * <p>Each row carries its own review state and published flag; the UI must render those server
 * values rather than any local draft state.
 */
export async function listVersions(questionId: string): Promise<VersionListItem[]> {
  const result = await apiGet<unknown>(`/questions/${questionId}/versions`);
  return z.array(versionListItemSchema).parse(result.data);
}

export async function createVersion(
  questionId: string,
  body: VersionCreateRequest,
): Promise<VersionResponse> {
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
  body: OptionRequest,
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

// ----- Assets ({@code §105}) -----

export async function listAssets(versionId: string): Promise<ContentAsset[]> {
  const result = await apiGet<unknown>(`/question-versions/${versionId}/assets`);
  return z.array(assetSchema).parse(result.data);
}

/**
 * Uploads one asset as {@code multipart/form-data}.
 *
 * <p>The browser sets the multipart boundary, so no explicit {@code Content-Type} is forced: an
 * explicit header without a boundary makes the request unparseable on the server. The field name
 * is the frozen {@code file}. No filesystem path is read or returned ({@code §15, §105}).
 */
export async function uploadAsset(versionId: string, file: File): Promise<ContentAsset> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await apiClient.post(`/question-versions/${versionId}/assets`, formData);
  return assetSchema.parse(response.data.data);
}

export async function deleteAsset(versionId: string, assetId: string): Promise<void> {
  await apiDelete(`/question-versions/${versionId}/assets/${assetId}`);
}

// ----- Explanation and hint ({@code §106, §107}) -----

export async function getExplanation(versionId: string): Promise<VersionTextContent> {
  const result = await apiGet<unknown>(`/question-versions/${versionId}/explanation`);
  return versionTextContentSchema.parse(result.data);
}

export async function createExplanation(versionId: string, content: string): Promise<VersionTextContent> {
  const result = await apiPost<unknown>(`/question-versions/${versionId}/explanation`, { content });
  return versionTextContentSchema.parse(result.data);
}

export async function updateExplanation(versionId: string, content: string): Promise<VersionTextContent> {
  const result = await apiPut<unknown>(`/question-versions/${versionId}/explanation`, { content });
  return versionTextContentSchema.parse(result.data);
}

export async function getHint(versionId: string): Promise<VersionTextContent> {
  const result = await apiGet<unknown>(`/question-versions/${versionId}/hint`);
  return versionTextContentSchema.parse(result.data);
}

export async function createHint(versionId: string, content: string): Promise<VersionTextContent> {
  const result = await apiPost<unknown>(`/question-versions/${versionId}/hint`, { content });
  return versionTextContentSchema.parse(result.data);
}

export async function updateHint(versionId: string, content: string): Promise<VersionTextContent> {
  const result = await apiPut<unknown>(`/question-versions/${versionId}/hint`, { content });
  return versionTextContentSchema.parse(result.data);
}

// ----- Previous exam history ({@code §108}) -----

export async function listPreviousExams(questionId: string): Promise<PreviousExam[]> {
  const result = await apiGet<unknown>(`/questions/${questionId}/previous-exams`);
  return z.array(previousExamSchema).parse(result.data);
}

export async function addPreviousExam(
  questionId: string,
  body: PreviousExamRequest,
): Promise<PreviousExam> {
  const result = await apiPost<unknown>(`/questions/${questionId}/previous-exams`, body);
  return previousExamSchema.parse(result.data);
}

export async function updatePreviousExam(
  questionId: string,
  historyId: string,
  body: PreviousExamUpdateRequest,
): Promise<PreviousExam> {
  const result = await apiPut<unknown>(`/questions/${questionId}/previous-exams/${historyId}`, body);
  return previousExamSchema.parse(result.data);
}

export async function deletePreviousExam(questionId: string, historyId: string): Promise<void> {
  await apiDelete(`/questions/${questionId}/previous-exams/${historyId}`);
}

