"use client";

/**
 * Runtime hooks ({@code specs/14-P5-Delivery-V1.5.md §75-§77}).
 *
 * <p>Reads are queries; mutations are explicit. Autosave is a mutation the runtime awaits before
 * treating a response as persisted ({@code §42}).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  getActiveSession,
  getAttempt,
  getAttemptQuestions,
  recoverSession,
  saveResponse,
  startAssessment,
  submitAttempt,
} from "@/features/delivery/api/delivery-api";
import type { ResponseSaveRequest } from "@/features/delivery/schemas/delivery";
import type { ApiClientError } from "@/lib/errors";

export const deliveryKeys = {
  attempt: (attemptId: string) => ["delivery", "attempt", attemptId] as const,
  questions: (attemptId: string) => ["delivery", "questions", attemptId] as const,
  activeSession: () => ["delivery", "sessions", "active"] as const,
};

export function useAttempt(attemptId: string) {
  return useQuery({
    queryKey: deliveryKeys.attempt(attemptId),
    queryFn: () => getAttempt(attemptId),
    enabled: attemptId.length > 0,
  });
}

export function useAttemptQuestions(attemptId: string) {
  return useQuery({
    queryKey: deliveryKeys.questions(attemptId),
    queryFn: () => getAttemptQuestions(attemptId),
    enabled: attemptId.length > 0,
  });
}

export function useActiveSession() {
  return useQuery({ queryKey: deliveryKeys.activeSession(), queryFn: getActiveSession });
}

export function useStartAssessment() {
  return useMutation<unknown, ApiClientError, string>({ mutationFn: startAssessment });
}

export function useRecoverSession() {
  return useMutation<unknown, ApiClientError, string>({ mutationFn: recoverSession });
}

export function useSaveResponse(attemptId: string) {
  return useMutation<
    { etag: string },
    ApiClientError,
    { questionVersionId: string; body: ResponseSaveRequest; ifMatch?: string }
  >({
    mutationFn: async ({ questionVersionId, body, ifMatch }) =>
      saveResponse(attemptId, questionVersionId, body, ifMatch),
  });
}

export function useSubmitAttempt(attemptId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => submitAttempt(attemptId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: deliveryKeys.attempt(attemptId) });
    },
  });
}
