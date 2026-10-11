"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  getAssessment,
  getAssessmentVersion,
  listAssessments,
  listBlueprints,
  publishAssessmentVersion,
  validateAssessmentVersion,
} from "@/features/assessment/api/assessment-api";
import type { ApiClientError } from "@/lib/errors";

export const assessmentKeys = {
  blueprints: () => ["blueprints"] as const,
  assessments: (filters: unknown = {}) => ["assessments", filters] as const,
  assessment: (id: string) => ["assessment", id] as const,
  version: (versionId: string) => ["assessmentVersion", versionId] as const,
};

export function useBlueprints() {
  return useQuery({ queryKey: assessmentKeys.blueprints(), queryFn: listBlueprints });
}

export function useAssessments(filters: Record<string, string | number | undefined> = {}) {
  return useQuery({ queryKey: assessmentKeys.assessments(filters), queryFn: () => listAssessments(filters) });
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
