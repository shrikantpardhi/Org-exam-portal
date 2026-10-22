/**
 * Analytics API layer ({@code specs/17-P8-Analytics-V1.5.md §57, §58, §142}).
 *
 * <p>Only the nine frozen read endpoints are called; no write endpoint exists ({@code §58}). Each
 * response is validated with Zod at the boundary, so an unexpected server shape fails fast instead
 * of leaking into a chart. Components never call Axios directly.
 */
import type { z } from "zod";

import { apiGet } from "@/lib/api-client";
import {
  assessmentDashboardSchema,
  organizationAnalyticsSchema,
  questionAnalyticsSchema,
  studentAssessmentItemSchema,
  studentDashboardSchema,
  studentTopicItemSchema,
  topicAnalyticsSchema,
} from "@/features/analytics/schemas/analytics";

async function load<T extends z.ZodTypeAny>(path: string, schema: T): Promise<z.infer<T>> {
  const result = await apiGet<unknown>(path);
  return schema.parse(result.data);
}

/** {@code GET /analytics/student} — the student dashboard read model ({@code §50}). */
export function getStudentAnalytics() {
  return load("/analytics/student", studentDashboardSchema);
}

/** {@code GET /analytics/student/assessments} — the student's evaluated attempts. */
export function getStudentAssessments() {
  return load("/analytics/student/assessments", studentAssessmentItemSchema.array());
}

/** {@code GET /analytics/student/topics} — the student's topic performance. */
export function getStudentTopics() {
  return load("/analytics/student/topics", studentTopicItemSchema.array());
}

/** {@code GET /analytics/assessments/{id}} — summary plus section analysis ({@code §53}). */
export function getAssessmentAnalytics(assessmentId: string) {
  return load(`/analytics/assessments/${assessmentId}`, assessmentDashboardSchema);
}

/** {@code GET /analytics/assessments/{id}/questions} — question difficulty for an assessment. */
export function getAssessmentQuestions(assessmentId: string) {
  return load(`/analytics/assessments/${assessmentId}/questions`, questionAnalyticsSchema.array());
}

/** {@code GET /analytics/questions/{id}} — the question dashboard read model ({@code §54}). */
export function getQuestionAnalytics(questionId: string) {
  return load(`/analytics/questions/${questionId}`, questionAnalyticsSchema);
}

/** {@code GET /analytics/topics/{id}} — the topic dashboard read model ({@code §55}). */
export function getTopicAnalytics(topicId: string) {
  return load(`/analytics/topics/${topicId}`, topicAnalyticsSchema);
}

/** {@code GET /analytics/organization} — the organization dashboard read model ({@code §56}). */
export function getOrganizationAnalytics() {
  return load("/analytics/organization", organizationAnalyticsSchema);
}
