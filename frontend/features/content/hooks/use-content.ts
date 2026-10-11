"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  approveVersion,
  createQuestion,
  getQuestion,
  getVersion,
  listOptions,
  listQuestionTemplates,
  listQuestions,
  publishVersion,
  rejectVersion,
  submitReview,
  type QuestionListParams,
} from "@/features/content/api/content-api";
import type { ApiClientError } from "@/lib/errors";
import type { QuestionForm } from "@/features/content/schemas/content";

/** Stable domain query keys ({@code specs/12-P3-Content-V1.5.md §68 convention}). */
export const contentKeys = {
  templates: () => ["question-templates"] as const,
  questions: (filters: unknown = {}) => ["questions", filters] as const,
  question: (id: string) => ["question", id] as const,
  versions: (questionId: string) => ["questionVersions", questionId] as const,
  version: (questionId: string, versionId: string) => ["questionVersion", questionId, versionId] as const,
  options: (questionId: string, versionId: string) => ["questionOptions", questionId, versionId] as const,
};

export function useQuestionTemplates() {
  return useQuery({ queryKey: contentKeys.templates(), queryFn: listQuestionTemplates });
}

export function useQuestions(filters: QuestionListParams = {}) {
  return useQuery({ queryKey: contentKeys.questions(filters), queryFn: () => listQuestions(filters) });
}

export function useQuestion(id: string) {
  return useQuery({ queryKey: contentKeys.question(id), queryFn: () => getQuestion(id), enabled: id.length > 0 });
}

export function useVersion(questionId: string, versionId: string) {
  return useQuery({
    queryKey: contentKeys.version(questionId, versionId),
    queryFn: () => getVersion(questionId, versionId),
    enabled: questionId.length > 0 && versionId.length > 0,
  });
}

export function useOptions(questionId: string, versionId: string) {
  return useQuery({
    queryKey: contentKeys.options(questionId, versionId),
    queryFn: () => listOptions(questionId, versionId),
    enabled: questionId.length > 0 && versionId.length > 0,
  });
}

export function useCreateQuestion() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, QuestionForm>({
    mutationFn: createQuestion,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["questions"] }),
  });
}

function useReviewAction(
  action: (questionId: string, versionId: string, remarks?: string) => Promise<unknown>,
  questionId: string,
) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { versionId: string; remarks?: string }>({
    mutationFn: ({ versionId, remarks }) => action(questionId, versionId, remarks),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["questionVersion", questionId] });
      queryClient.invalidateQueries({ queryKey: ["question", questionId] });
      queryClient.invalidateQueries({ queryKey: ["questions"] });
    },
  });
}

export function useSubmitReview(questionId: string) {
  return useReviewAction((q, v) => submitReview(q, v), questionId);
}

export function useApproveVersion(questionId: string) {
  return useReviewAction((q, v, remarks) => approveVersion(q, v, remarks ?? ""), questionId);
}

export function useRejectVersion(questionId: string) {
  return useReviewAction((q, v, remarks) => rejectVersion(q, v, remarks ?? ""), questionId);
}

export function usePublishVersion(questionId: string) {
  return useReviewAction((q, v) => publishVersion(q, v), questionId);
}
