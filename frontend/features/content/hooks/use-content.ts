"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  addOption,
  addPreviousExam,
  approveVersion,
  createExplanation,
  createHint,
  createQuestion,
  createQuestionTemplate,
  createVersion,
  deleteAsset,
  deleteOption,
  deletePreviousExam,
  getExplanation,
  getHint,
  getQuestion,
  getQuestionTemplate,
  getVersion,
  listAssets,
  listOptions,
  listPreviousExams,
  listQuestionTemplates,
  listQuestions,
  listVersions,
  publishVersion,
  rejectVersion,
  submitReview,
  updateExplanation,
  updateHint,
  updateOption,
  updatePreviousExam,
  updateQuestion,
  updateQuestionTemplate,
  uploadAsset,
  type QuestionListParams,
} from "@/features/content/api/content-api";
import type { ApiClientError } from "@/lib/errors";
import type {
  ContentAsset,
  CreateQuestionPayload,
  OptionRequest,
  PreviousExam,
  PreviousExamRequest,
  PreviousExamUpdateRequest,
  QuestionMetadataUpdate,
  QuestionOption,
  QuestionResponse,
  QuestionTemplate,
  TemplateForm,
  VersionCreateRequest,
  VersionResponse,
  VersionTextContent,
} from "@/features/content/schemas/content";

/** Stable domain query keys ({@code specs/12-P3-Content-V1.5.md §68 convention}). */
export const contentKeys = {
  templates: () => ["question-templates"] as const,
  template: (id: string) => ["question-template", id] as const,
  questions: (filters: unknown = {}) => ["questions", filters] as const,
  question: (id: string) => ["question", id] as const,
  versions: (questionId: string) => ["questionVersions", questionId] as const,
  version: (questionId: string, versionId: string) => ["questionVersion", questionId, versionId] as const,
  options: (questionId: string, versionId: string) => ["questionOptions", questionId, versionId] as const,
  assets: (versionId: string) => ["questionVersionAssets", versionId] as const,
  explanation: (versionId: string) => ["questionVersionExplanation", versionId] as const,
  hint: (versionId: string) => ["questionVersionHint", versionId] as const,
  previousExams: (questionId: string) => ["questionPreviousExams", questionId] as const,
};

export function useQuestionTemplates() {
  return useQuery({ queryKey: contentKeys.templates(), queryFn: listQuestionTemplates });
}

export function useQuestionTemplate(id: string) {
  return useQuery({
    queryKey: contentKeys.template(id),
    queryFn: () => getQuestionTemplate(id),
    enabled: id.length > 0,
  });
}

export function useQuestions(filters: QuestionListParams = {}) {
  return useQuery({ queryKey: contentKeys.questions(filters), queryFn: () => listQuestions(filters) });
}

export function useQuestion(id: string) {
  return useQuery({ queryKey: contentKeys.question(id), queryFn: () => getQuestion(id), enabled: id.length > 0 });
}

/** The version history, with each row's own review and published state ({@code §110}). */
export function useVersions(questionId: string) {
  return useQuery({
    queryKey: contentKeys.versions(questionId),
    queryFn: () => listVersions(questionId),
    enabled: questionId.length > 0,
  });
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

/** Version-bound assets; a new version starts with none ({@code §105}). */
export function useAssets(versionId: string) {
  return useQuery({
    queryKey: contentKeys.assets(versionId),
    queryFn: () => listAssets(versionId),
    enabled: versionId.length > 0,
  });
}

/**
 * Version-bound explanation ({@code §106}). A 404 is a legitimate "not authored yet" answer, so
 * it is not retried and is surfaced as {@code null} rather than as an error state.
 */
export function useExplanation(versionId: string) {
  return useQuery({
    queryKey: contentKeys.explanation(versionId),
    queryFn: async () => {
      try {
        return await getExplanation(versionId);
      } catch (error) {
        if (isMissing(error)) {
          return null;
        }
        throw error;
      }
    },
    enabled: versionId.length > 0,
    retry: false,
  });
}

/** Version-bound optional hint ({@code §107}); absence is reported, not treated as a failure. */
export function useHint(versionId: string) {
  return useQuery({
    queryKey: contentKeys.hint(versionId),
    queryFn: async () => {
      try {
        return await getHint(versionId);
      } catch (error) {
        if (isMissing(error)) {
          return null;
        }
        throw error;
      }
    },
    enabled: versionId.length > 0,
    retry: false,
  });
}

export function usePreviousExams(questionId: string) {
  return useQuery({
    queryKey: contentKeys.previousExams(questionId),
    queryFn: () => listPreviousExams(questionId),
    enabled: questionId.length > 0,
  });
}

function isMissing(error: unknown): boolean {
  return typeof error === "object" && error !== null && "status" in error && (error as { status?: unknown }).status === 404;
}

export function useCreateQuestionTemplate() {
  const queryClient = useQueryClient();
  return useMutation<QuestionTemplate, ApiClientError, TemplateForm>({
    mutationFn: createQuestionTemplate,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.templates() }),
  });
}

export function useUpdateQuestionTemplate(id: string) {
  const queryClient = useQueryClient();
  return useMutation<QuestionTemplate, ApiClientError, Partial<TemplateForm> & { status?: string }>({
    mutationFn: (body) => updateQuestionTemplate(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: contentKeys.templates() });
      queryClient.invalidateQueries({ queryKey: contentKeys.template(id) });
    },
  });
}

export function useCreateQuestion() {
  const queryClient = useQueryClient();
  return useMutation<QuestionResponse, ApiClientError, CreateQuestionPayload>({
    mutationFn: createQuestion,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["questions"] }),
  });
}

/** Draft-level metadata update, including the tag association ({@code §89, §109}). */
export function useUpdateQuestion(questionId: string) {
  const queryClient = useQueryClient();
  return useMutation<QuestionResponse, ApiClientError, QuestionMetadataUpdate>({
    mutationFn: (body) => updateQuestion(questionId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: contentKeys.question(questionId) });
      queryClient.invalidateQueries({ queryKey: ["questions"] });
    },
  });
}

/** Creates the next draft version of an existing question ({@code §90, §110}). */
export function useCreateVersion(questionId: string) {
  const queryClient = useQueryClient();
  return useMutation<VersionResponse, ApiClientError, VersionCreateRequest>({
    mutationFn: (body) => createVersion(questionId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: contentKeys.question(questionId) });
      queryClient.invalidateQueries({ queryKey: ["questions"] });
    },
  });
}

function useReviewAction(
  action: (questionId: string, versionId: string, remarks?: string) => Promise<VersionResponse>,
  questionId: string,
) {
  const queryClient = useQueryClient();
  return useMutation<VersionResponse, ApiClientError, { versionId: string; remarks?: string }>({
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

// ----- Options ({@code §103}) -----

export function useAddOption(questionId: string, versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<QuestionOption, ApiClientError, OptionRequest>({
    mutationFn: (body) => addOption(questionId, versionId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.options(questionId, versionId) }),
  });
}

export function useUpdateOption(questionId: string, versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<QuestionOption, ApiClientError, { optionId: string; body: { content?: string; displayOrder?: number; isCorrect?: boolean } }>({
    mutationFn: ({ optionId, body }) => updateOption(questionId, versionId, optionId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.options(questionId, versionId) }),
  });
}

export function useDeleteOption(questionId: string, versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, string>({
    mutationFn: (optionId) => deleteOption(questionId, versionId, optionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.options(questionId, versionId) }),
  });
}

// ----- Assets ({@code §105}) -----

export function useUploadAsset(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<ContentAsset, ApiClientError, File>({
    mutationFn: (file) => uploadAsset(versionId, file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.assets(versionId) }),
  });
}

export function useDeleteAsset(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, string>({
    mutationFn: (assetId) => deleteAsset(versionId, assetId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.assets(versionId) }),
  });
}

// ----- Explanation and hint ({@code §106, §107}) -----

/** Creates the explanation or updates the existing one, whichever the server state allows. */
export function useSaveExplanation(versionId: string, existing: boolean) {
  const queryClient = useQueryClient();
  return useMutation<VersionTextContent, ApiClientError, string>({
    mutationFn: (content) =>
      existing ? updateExplanation(versionId, content) : createExplanation(versionId, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: contentKeys.explanation(versionId) });
    },
  });
}

export function useSaveHint(versionId: string, existing: boolean) {
  const queryClient = useQueryClient();
  return useMutation<VersionTextContent, ApiClientError, string>({
    mutationFn: (content) => (existing ? updateHint(versionId, content) : createHint(versionId, content)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: contentKeys.hint(versionId) });
    },
  });
}

// ----- Previous exam history ({@code §108}) -----

export function useAddPreviousExam(questionId: string) {
  const queryClient = useQueryClient();
  return useMutation<PreviousExam, ApiClientError, PreviousExamRequest>({
    mutationFn: (body) => addPreviousExam(questionId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.previousExams(questionId) }),
  });
}

export function useUpdatePreviousExam(questionId: string) {
  const queryClient = useQueryClient();
  return useMutation<PreviousExam, ApiClientError, { historyId: string; body: PreviousExamUpdateRequest }>({
    mutationFn: ({ historyId, body }) => updatePreviousExam(questionId, historyId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.previousExams(questionId) }),
  });
}

export function useDeletePreviousExam(questionId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, string>({
    mutationFn: (historyId) => deletePreviousExam(questionId, historyId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contentKeys.previousExams(questionId) }),
  });
}


