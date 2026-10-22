/**
 * Result and scorecard read API ({@code specs/15-P6-Evaluation-V1.5.md §90-§91, §95-§98}).
 *
 * <p>There is no student-scoped result-list endpoint: {@code GET /results} requires
 * {@code RESULT_VIEW}, which only staff and admin roles hold. The student list therefore composes
 * the frozen, self-scoped {@code GET /attempts} read with the frozen
 * {@code GET /attempts/{attemptId}/result} read, and treats a 404 as "not published" — the same
 * response the backend returns for another student's or an unpublished result, so neither is
 * distinguishable and neither is shown ({@code §39, §87}).
 */
import { z } from "zod";

import { apiGet } from "@/lib/api-client";
import { ApiClientError } from "@/lib/errors";
import {
  attemptSummarySchema,
  resultResponseSchema,
  scorecardResponseSchema,
  type AttemptSummary,
  type ResultResponse,
  type ScorecardResponse,
} from "@/features/evaluation/schemas/evaluation";

/** Bound on the composed student list so one dashboard open cannot fan out without limit. */
export const MAX_STUDENT_RESULT_ATTEMPTS = 25;

export interface StudentResultItem {
  result: ResultResponse;
  attempt: AttemptSummary;
}

/** {@code GET /attempts} — the caller's own attempts (self-scoped by the backend). */
export async function getMyAttempts(): Promise<AttemptSummary[]> {
  const result = await apiGet<unknown>("/attempts");
  return z.array(attemptSummarySchema).parse(result.data);
}

/** {@code GET /results/{id}} — ownership and publication are enforced by the backend. */
export async function getResult(resultId: string): Promise<ResultResponse> {
  const result = await apiGet<unknown>(`/results/${resultId}`);
  return resultResponseSchema.parse(result.data);
}

/** {@code GET /attempts/{attemptId}/result} — the current result of the caller's attempt. */
export async function getAttemptResult(attemptId: string): Promise<ResultResponse> {
  const result = await apiGet<unknown>(`/attempts/${attemptId}/result`);
  return resultResponseSchema.parse(result.data);
}

/** {@code GET /results/{resultId}/scorecard} — the secure scorecard reference or status. */
export async function getScorecard(resultId: string): Promise<ScorecardResponse> {
  const result = await apiGet<unknown>(`/results/${resultId}/scorecard`);
  return scorecardResponseSchema.parse(result.data);
}

function isNotAvailable(error: unknown): boolean {
  return error instanceof ApiClientError && error.status === 404;
}

/**
 * The student's published results, most recent attempt first.
 *
 * <p>Only terminal attempts are queried. A 404 means the result is not published (or belongs to
 * someone else), so the entry is omitted; any other failure is surfaced instead of being hidden.
 * A 200 response is accepted only when it is PUBLISHED and not superseded, so a backend regression
 * cannot leak a provisional result into the UI ({@code §39}).
 */
export async function getStudentResults(): Promise<StudentResultItem[]> {
  const attempts = await getMyAttempts();
  const candidates = attempts
    .filter(
      (attempt) =>
        attempt.attemptStatus === "EVALUATED" || attempt.attemptStatus === "SUBMITTED",
    )
    .sort((left, right) => timestampOf(right.startedOn) - timestampOf(left.startedOn))
    .slice(0, MAX_STUDENT_RESULT_ATTEMPTS);

  const settled = await Promise.allSettled(
    candidates.map(async (attempt) => ({
      attempt,
      result: await getAttemptResult(attempt.attemptId),
    })),
  );

  const items: StudentResultItem[] = [];
  for (const outcome of settled) {
    if (outcome.status === "fulfilled") {
      const { attempt, result } = outcome.value;
      if (result.resultStatus === "PUBLISHED" && result.supersededOn === null) {
        items.push({ attempt, result });
      }
      continue;
    }
    if (!isNotAvailable(outcome.reason)) {
      throw outcome.reason;
    }
  }

  return items.sort(
    (left, right) =>
      timestampOf(right.attempt.startedOn) - timestampOf(left.attempt.startedOn),
  );
}

function timestampOf(value: string | null): number {
  if (!value) {
    return 0;
  }
  const parsed = new Date(value).getTime();
  return Number.isNaN(parsed) ? 0 : parsed;
}
