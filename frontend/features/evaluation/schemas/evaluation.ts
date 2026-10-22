/**
 * Result and scorecard response schemas ({@code specs/15-P6-Evaluation-V1.5.md §39, §90-§91, §116}).
 *
 * <p>A missing numeric projection stays null rather than becoming a misleading zero, and the
 * result status is kept as a controlled string so an unrecognised value is never rendered as a
 * published result. The scorecard response deliberately carries no file path — only the secure
 * reference the backend chooses to expose ({@code §58}).
 */
import { z } from "zod";

const decimal = z
  .union([z.number(), z.string()])
  .nullish()
  .transform((value) => {
    if (value === null || value === undefined) {
      return null;
    }
    const parsed = typeof value === "number" ? value : Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  });

const integer = z
  .union([z.number(), z.string()])
  .nullish()
  .transform((value) => {
    if (value === null || value === undefined) {
      return null;
    }
    const parsed = typeof value === "number" ? value : Number(value);
    return Number.isInteger(parsed) ? parsed : null;
  });

const nullableText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const timestamp = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

/** The frozen result vocabulary ({@code §7}); unknown values are surfaced, never treated as published. */
export const resultStatusSchema = z
  .string()
  .nullish()
  .transform((value) => (value ?? "UNKNOWN").toUpperCase());

/** The frozen {@code ResultResponse} projection ({@code §116}). */
export const resultResponseSchema = z.object({
  id: z.string(),
  evaluationId: nullableText,
  attemptId: nullableText,
  assessmentVersionId: nullableText,
  obtainedMarks: decimal,
  percentage: decimal,
  rank: integer,
  percentile: decimal,
  resultStatus: resultStatusSchema,
  supersededOn: timestamp,
});

/** The frozen {@code ScorecardResponse}; the private storage path is never part of it ({@code §58}). */
export const scorecardResponseSchema = z.object({
  scorecardId: z.string(),
  resultId: z.string(),
  status: nullableText,
  generatedOn: timestamp,
  secureDownloadReference: nullableText,
});

/** The attempt fields the result list needs; extra runtime fields are ignored. */
export const attemptSummarySchema = z.object({
  attemptId: z.string(),
  assessmentVersionId: nullableText,
  attemptMode: nullableText,
  attemptStatus: nullableText,
  startedOn: timestamp,
  submittedOn: timestamp,
});

export type ResultStatus = z.infer<typeof resultStatusSchema>;
export type ResultResponse = z.infer<typeof resultResponseSchema>;
export type ScorecardResponse = z.infer<typeof scorecardResponseSchema>;
export type AttemptSummary = z.infer<typeof attemptSummarySchema>;
