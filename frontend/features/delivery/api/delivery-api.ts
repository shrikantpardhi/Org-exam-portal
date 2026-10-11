/**
 * Delivery runtime API layer ({@code specs/14-P5-Delivery-V1.5.md §70}).
 *
 * <p>All Axios calls for the runtime examination live here; components never call Axios directly.
 * Response save carries the {@code If-Match} ETag and reads the new ETag from the response header.
 */
import { apiClient, apiGet, apiPost } from "@/lib/api-client";
import {
  attemptQuestionSchema,
  attemptResponseSchema,
  responseSaveRequestSchema,
  responseViewSchema,
  sessionResponseSchema,
  startResponseSchema,
  submitResponseSchema,
  type AttemptQuestion,
  type AttemptResponse,
  type ResponseSaveRequest,
  type ResponseView,
  type SessionResponse,
  type StartResponse,
  type SubmitResponse,
} from "@/features/delivery/schemas/delivery";
import { z } from "zod";

export interface SavedResponse {
  view: ResponseView;
  etag: string;
}

function etagOf(headers: Record<string, unknown>, fallback: string): string {
  const header = headers["etag"];
  return typeof header === "string" && header.length > 0 ? header : fallback;
}

export async function startAssessment(scheduleId: string): Promise<StartResponse> {
  const result = await apiPost<unknown>(`/assessment-schedules/${scheduleId}/start`);
  return startResponseSchema.parse(result.data);
}

export async function getActiveSession(): Promise<SessionResponse> {
  const result = await apiGet<unknown>("/sessions/active");
  return sessionResponseSchema.parse(result.data);
}

export async function recoverSession(sessionId: string): Promise<SessionResponse> {
  const result = await apiPost<unknown>(`/sessions/${sessionId}/recover`);
  return sessionResponseSchema.parse(result.data);
}

export async function getAttempt(attemptId: string): Promise<AttemptResponse> {
  const result = await apiGet<unknown>(`/attempts/${attemptId}`);
  return attemptResponseSchema.parse(result.data);
}

export async function getAttemptQuestions(attemptId: string): Promise<AttemptQuestion[]> {
  const result = await apiGet<unknown>(`/attempts/${attemptId}/questions`);
  return z.array(attemptQuestionSchema).parse(result.data);
}

export async function getResponse(attemptId: string, questionVersionId: string): Promise<ResponseView> {
  const response = await apiClient.get(`/attempts/${attemptId}/responses/${questionVersionId}`);
  const view = responseViewSchema.parse(response.data.data);
  return { ...view, etag: etagOf(response.headers as Record<string, unknown>, view.etag) };
}

export async function saveResponse(
  attemptId: string,
  questionVersionId: string,
  body: ResponseSaveRequest,
  ifMatch?: string,
): Promise<SavedResponse> {
  const payload = responseSaveRequestSchema.parse(body);
  const response = await apiClient.put(
    `/attempts/${attemptId}/responses/${questionVersionId}`,
    payload,
    ifMatch ? { headers: { "If-Match": ifMatch } } : undefined,
  );
  const view = responseViewSchema.parse(response.data.data);
  return { view, etag: etagOf(response.headers as Record<string, unknown>, view.etag) };
}

export async function submitAttempt(attemptId: string): Promise<SubmitResponse> {
  const result = await apiPost<unknown>(`/attempts/${attemptId}/submit`, { confirmed: true });
  return submitResponseSchema.parse(result.data);
}
