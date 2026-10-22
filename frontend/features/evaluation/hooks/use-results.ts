"use client";

import { useQuery } from "@tanstack/react-query";

import {
  getAttemptResult,
  getResult,
  getScorecard,
  getStudentResults,
} from "@/features/evaluation/api/evaluation-api";
import { evaluationKeys } from "@/features/evaluation/query-keys";

/**
 * TanStack Query hooks for the frozen result endpoints
 * ({@code specs/15-P6-Evaluation-V1.5.md §95-§98}).
 *
 * <p>Every read is self-scoped by the backend; the hooks add no client-side authorization.
 */
export function useStudentResults() {
  return useQuery({
    queryKey: evaluationKeys.studentResults(),
    queryFn: getStudentResults,
  });
}

export function useResult(resultId: string) {
  return useQuery({
    queryKey: evaluationKeys.result(resultId),
    queryFn: () => getResult(resultId),
    enabled: resultId.length > 0,
  });
}

export function useAttemptResult(attemptId: string) {
  return useQuery({
    queryKey: evaluationKeys.attemptResult(attemptId),
    queryFn: () => getAttemptResult(attemptId),
    enabled: attemptId.length > 0,
  });
}

/**
 * The scorecard of a published result. A missing scorecard is a legitimate empty state, not an
 * error, so the read is never retried ({@code §98}).
 */
export function useScorecard(resultId: string, enabled = true) {
  return useQuery({
    queryKey: evaluationKeys.scorecard(resultId),
    queryFn: () => getScorecard(resultId),
    enabled: resultId.length > 0 && enabled,
    retry: false,
  });
}
