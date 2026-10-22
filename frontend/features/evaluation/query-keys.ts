/**
 * Result query keys ({@code specs/06-Architecture-Freeze-V1.5.md §23}).
 *
 * <p>Server result state is keyed separately from any local UI state; a route parameter is part of
 * the key, so two results never share a cache entry.
 */
export const evaluationKeys = {
  all: () => ["evaluation"] as const,
  studentResults: () => ["evaluation", "results", "mine"] as const,
  attempts: () => ["evaluation", "attempts", "mine"] as const,
  result: (resultId: string) => ["evaluation", "result", resultId] as const,
  attemptResult: (attemptId: string) => ["evaluation", "attemptResult", attemptId] as const,
  scorecard: (resultId: string) => ["evaluation", "scorecard", resultId] as const,
};
