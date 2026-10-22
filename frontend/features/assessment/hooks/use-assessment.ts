"use client";

/**
 * Assessment authoring hooks ({@code specs/13-P4-Assessment-V1.5.md §97}).
 *
 * <p>Server state only. Every mutation invalidates the keys it can change, so the builder panels stay
 * in sync without the UI keeping a second copy of server truth.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  addAssessmentQuestion,
  addAssessmentToCollection,
  closeSchedule,
  createAssessmentVersion,
  createBlueprintVersion,
  createCollection,
  createSchedule,
  createSelectionRule,
  deleteSelectionRule,
  getAssessment,
  getAssessmentPreview,
  getAssessmentVersion,
  getBlueprint,
  getBlueprintVersion,
  getCollection,
  listAssessmentQuestions,
  listAssessments,
  listBlueprintVersions,
  listBlueprints,
  listCollections,
  listQuestionVersions,
  listSchedules,
  listSelectionRules,
  materializeSelectionRules,
  publishAssessmentVersion,
  publishBlueprintVersion,
  removeAssessmentFromCollection,
  removeAssessmentQuestion,
  reorderAssessmentQuestions,
  searchQuestions,
  updateAssessmentQuestion,
  updateCollection,
  updateSchedule,
  updateSelectionRule,
  validateAssessmentVersion,
  type QuestionSearchParams,
} from "@/features/assessment/api/assessment-api";
import {
  toRuleConfiguration,
  type MaterializeMode,
  type RuleConfigurationForm,
  type ScheduleMode,
} from "@/features/assessment/schemas/assessment-builder";
import type { ApiClientError } from "@/lib/errors";

export const assessmentKeys = {
  blueprints: () => ["blueprints"] as const,
  blueprint: (id: string) => ["blueprint", id] as const,
  blueprintVersions: (id: string) => ["blueprintVersions", id] as const,
  blueprintVersion: (id: string, versionId: string) => ["blueprintVersion", id, versionId] as const,
  assessments: (filters: unknown = {}) => ["assessments", filters] as const,
  assessment: (id: string) => ["assessment", id] as const,
  version: (versionId: string) => ["assessmentVersion", versionId] as const,
  questions: (versionId: string) => ["assessmentQuestions", versionId] as const,
  rules: (versionId: string) => ["selectionRules", versionId] as const,
  preview: (versionId: string) => ["assessmentPreview", versionId] as const,
  questionSearch: (params: QuestionSearchParams) => ["questionSearch", params] as const,
  questionVersions: (questionId: string) => ["questionVersions", questionId] as const,
  schedules: (filters: unknown = {}) => ["schedules", filters] as const,
  collections: () => ["collections"] as const,
  collection: (id: string) => ["collection", id] as const,
};

// ----- Blueprint reads -----

export function useBlueprints() {
  return useQuery({ queryKey: assessmentKeys.blueprints(), queryFn: listBlueprints });
}

export function useBlueprint(id: string) {
  return useQuery({
    queryKey: assessmentKeys.blueprint(id),
    queryFn: () => getBlueprint(id),
    enabled: id.length > 0,
  });
}

export function useBlueprintVersions(id: string) {
  return useQuery({
    queryKey: assessmentKeys.blueprintVersions(id),
    queryFn: () => listBlueprintVersions(id),
    enabled: id.length > 0,
  });
}

export function useBlueprintVersion(blueprintId: string, versionId: string) {
  return useQuery({
    queryKey: assessmentKeys.blueprintVersion(blueprintId, versionId),
    queryFn: () => getBlueprintVersion(blueprintId, versionId),
    enabled: blueprintId.length > 0 && versionId.length > 0,
  });
}

export function useCreateBlueprintVersion(blueprintId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, unknown>({
    mutationFn: (configuration) => createBlueprintVersion(blueprintId, configuration),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assessmentKeys.blueprintVersions(blueprintId) });
      queryClient.invalidateQueries({ queryKey: assessmentKeys.blueprint(blueprintId) });
      queryClient.invalidateQueries({ queryKey: assessmentKeys.blueprints() });
    },
  });
}

export function usePublishBlueprintVersion(blueprintId: string, versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, void>({
    mutationFn: () => publishBlueprintVersion(blueprintId, versionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assessmentKeys.blueprintVersion(blueprintId, versionId) });
      queryClient.invalidateQueries({ queryKey: assessmentKeys.blueprintVersions(blueprintId) });
    },
  });
}

// ----- Assessment reads -----

export function useAssessments(filters: Record<string, string | number | undefined> = {}) {
  return useQuery({
    queryKey: assessmentKeys.assessments(filters),
    queryFn: () => listAssessments(filters),
  });
}

export function useAssessment(id: string) {
  return useQuery({
    queryKey: assessmentKeys.assessment(id),
    queryFn: () => getAssessment(id),
    enabled: id.length > 0,
  });
}

export function useAssessmentVersion(versionId: string) {
  return useQuery({
    queryKey: assessmentKeys.version(versionId),
    queryFn: () => getAssessmentVersion(versionId),
    enabled: versionId.length > 0,
  });
}

export function useAssessmentQuestions(versionId: string) {
  return useQuery({
    queryKey: assessmentKeys.questions(versionId),
    queryFn: () => listAssessmentQuestions(versionId),
    enabled: versionId.length > 0,
  });
}

export function useSelectionRules(versionId: string) {
  return useQuery({
    queryKey: assessmentKeys.rules(versionId),
    queryFn: () => listSelectionRules(versionId),
    enabled: versionId.length > 0,
  });
}

export function useAssessmentPreview(versionId: string, enabled = true) {
  return useQuery({
    queryKey: assessmentKeys.preview(versionId),
    queryFn: () => getAssessmentPreview(versionId),
    enabled: enabled && versionId.length > 0,
  });
}

export function useQuestionSearch(params: QuestionSearchParams) {
  return useQuery({
    queryKey: assessmentKeys.questionSearch(params),
    queryFn: () => searchQuestions(params),
  });
}

export function useQuestionVersions(questionId: string | null) {
  return useQuery({
    queryKey: assessmentKeys.questionVersions(questionId ?? ""),
    queryFn: () => listQuestionVersions(questionId ?? ""),
    enabled: questionId !== null && questionId.length > 0,
  });
}

// ----- Assessment version mutations -----

function invalidateVersion(queryClient: ReturnType<typeof useQueryClient>, versionId: string): void {
  queryClient.invalidateQueries({ queryKey: assessmentKeys.questions(versionId) });
  queryClient.invalidateQueries({ queryKey: assessmentKeys.rules(versionId) });
  queryClient.invalidateQueries({ queryKey: assessmentKeys.preview(versionId) });
  queryClient.invalidateQueries({ queryKey: assessmentKeys.version(versionId) });
}

export function useCreateAssessmentVersion(assessmentId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    { blueprintVersionId: string; durationMinutes: number; engineType?: string; deliveryPolicyId?: string }
  >({
    mutationFn: (body) => createAssessmentVersion(assessmentId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: assessmentKeys.assessment(assessmentId) }),
  });
}

export function useAddAssessmentQuestion(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    { questionVersionId: string; sectionName: string; marks: number; negativeMarks: number; displayOrder: number }
  >({
    mutationFn: (body) => addAssessmentQuestion(versionId, body),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useUpdateAssessmentQuestion(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    {
      assessmentQuestionId: string;
      body: { displayOrder?: number; sectionName?: string; marks?: number; negativeMarks?: number };
    }
  >({
    mutationFn: ({ assessmentQuestionId, body }) =>
      updateAssessmentQuestion(versionId, assessmentQuestionId, body),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useRemoveAssessmentQuestion(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: (assessmentQuestionId) => removeAssessmentQuestion(versionId, assessmentQuestionId),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useReorderAssessmentQuestions(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    { assessmentQuestionId: string; displayOrder: number }[]
  >({
    mutationFn: (questionOrders) => reorderAssessmentQuestions(versionId, questionOrders),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useCreateSelectionRule(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    { sectionName: string; targetCount: number; displayOrder: number; form: RuleConfigurationForm }
  >({
    mutationFn: ({ sectionName, targetCount, displayOrder, form }) =>
      createSelectionRule(versionId, {
        sectionName,
        targetCount,
        displayOrder,
        ruleConfiguration: toRuleConfiguration(form),
      }),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useUpdateSelectionRule(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    { ruleId: string; targetCount: number; form: RuleConfigurationForm }
  >({
    mutationFn: ({ ruleId, targetCount, form }) =>
      updateSelectionRule(versionId, ruleId, {
        targetCount,
        ruleConfiguration: toRuleConfiguration(form),
      }),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useDeleteSelectionRule(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: (ruleId) => deleteSelectionRule(versionId, ruleId),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useMaterializeSelectionRules(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, MaterializeMode>({
    mutationFn: (mode) => materializeSelectionRules(versionId, mode),
    onSuccess: () => invalidateVersion(queryClient, versionId),
  });
}

export function useValidateAssessmentVersion() {
  return useMutation<unknown, ApiClientError, string>({ mutationFn: validateAssessmentVersion });
}

export function usePublishAssessmentVersion(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, void>({
    mutationFn: () => publishAssessmentVersion(versionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["assessmentVersion", versionId] });
      queryClient.invalidateQueries({ queryKey: ["assessment"] });
      queryClient.invalidateQueries({ queryKey: ["assessments"] });
    },
  });
}

// ----- Schedules -----

export function useSchedules(filters: { assessmentVersionId?: string; status?: string } = {}) {
  return useQuery({
    queryKey: assessmentKeys.schedules(filters),
    queryFn: () => listSchedules(filters),
  });
}

export function useCreateSchedule() {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    { assessmentVersionId: string; mode: ScheduleMode; startTime: string; endTime: string }
  >({
    mutationFn: createSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["schedules"] }),
  });
}

export function useUpdateSchedule() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { id: string; startTime: string; endTime: string }>({
    mutationFn: ({ id, startTime, endTime }) => updateSchedule(id, { startTime, endTime }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["schedules"] }),
  });
}

export function useCloseSchedule() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: closeSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["schedules"] }),
  });
}

// ----- Collections -----

export function useCollections() {
  return useQuery({ queryKey: assessmentKeys.collections(), queryFn: listCollections });
}

export function useCollection(id: string) {
  return useQuery({
    queryKey: assessmentKeys.collection(id),
    queryFn: () => getCollection(id),
    enabled: id.length > 0,
  });
}

export function useCreateCollection() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string; description?: string }>({
    mutationFn: createCollection,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: assessmentKeys.collections() }),
  });
}

export function useUpdateCollection(collectionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    unknown,
    ApiClientError,
    { name?: string; description?: string; status?: string }
  >({
    mutationFn: (body) => updateCollection(collectionId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assessmentKeys.collection(collectionId) });
      queryClient.invalidateQueries({ queryKey: assessmentKeys.collections() });
    },
  });
}

export function useAddAssessmentToCollection(collectionId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: (assessmentId) => addAssessmentToCollection(collectionId, assessmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assessmentKeys.collection(collectionId) });
      queryClient.invalidateQueries({ queryKey: assessmentKeys.collections() });
    },
  });
}

export function useRemoveAssessmentFromCollection(collectionId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: (assessmentId) => removeAssessmentFromCollection(collectionId, assessmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assessmentKeys.collection(collectionId) });
      queryClient.invalidateQueries({ queryKey: assessmentKeys.collections() });
    },
  });
}
