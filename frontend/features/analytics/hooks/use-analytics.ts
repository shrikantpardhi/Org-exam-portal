"use client";

import { useQuery } from "@tanstack/react-query";

import {
  getAssessmentAnalytics,
  getAssessmentQuestions,
  getOrganizationAnalytics,
  getQuestionAnalytics,
  getStudentAnalytics,
  getStudentAssessments,
  getStudentTopics,
  getTopicAnalytics,
} from "@/features/analytics/api/analytics-api";
import { analyticsKeys } from "@/features/analytics/query-keys";

/**
 * TanStack Query hooks for the frozen analytics read endpoints
 * ({@code specs/17-P8-Analytics-V1.5.md §142, §143}).
 *
 * <p>Analytics is server state, so it lives in TanStack Query; no filter or chart display state is
 * stored here. Reads inherit the application retry policy: a network failure may retry, a
 * non-network error does not.
 */
export function useStudentAnalytics() {
  return useQuery({ queryKey: analyticsKeys.student(), queryFn: getStudentAnalytics });
}

export function useStudentAssessments() {
  return useQuery({
    queryKey: analyticsKeys.studentAssessments(),
    queryFn: getStudentAssessments,
  });
}

export function useStudentTopics() {
  return useQuery({ queryKey: analyticsKeys.studentTopics(), queryFn: getStudentTopics });
}

export function useAssessmentAnalytics(assessmentId: string) {
  return useQuery({
    queryKey: analyticsKeys.assessment(assessmentId),
    queryFn: () => getAssessmentAnalytics(assessmentId),
    enabled: assessmentId.length > 0,
  });
}

export function useAssessmentQuestions(assessmentId: string) {
  return useQuery({
    queryKey: analyticsKeys.assessmentQuestions(assessmentId),
    queryFn: () => getAssessmentQuestions(assessmentId),
    enabled: assessmentId.length > 0,
  });
}

export function useQuestionAnalytics(questionId: string) {
  return useQuery({
    queryKey: analyticsKeys.question(questionId),
    queryFn: () => getQuestionAnalytics(questionId),
    enabled: questionId.length > 0,
  });
}

export function useTopicAnalytics(topicId: string) {
  return useQuery({
    queryKey: analyticsKeys.topic(topicId),
    queryFn: () => getTopicAnalytics(topicId),
    enabled: topicId.length > 0,
  });
}

export function useOrganizationAnalytics() {
  return useQuery({
    queryKey: analyticsKeys.organization(),
    queryFn: getOrganizationAnalytics,
  });
}
