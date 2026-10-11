/**
 * Runtime delivery schemas ({@code specs/14-P5-Delivery-V1.5.md §35, §73-§74}).
 *
 * <p>Every runtime payload is validated at the boundary. The normalized configuration is strictly
 * typed and rejects unknown keys, so the frontend never interprets unexpected or executable
 * configuration. Student payloads are strict: a leaked {@code isCorrect} fails parsing rather than
 * rendering.
 */
import { z } from "zod";

export const attemptStatusSchema = z.enum(["NOT_STARTED", "IN_PROGRESS", "SUBMITTED", "EVALUATED"]);
export const snapshotStatusSchema = z.enum(["PENDING", "PREPARING", "READY", "FAILED"]);
export const attemptModeSchema = z.enum(["PRACTICE", "EXAM"]);

export const deliveryConfigurationSchema = z
  .object({
    capabilities: z
      .object({
        practice: z.boolean(),
        allowClearResponse: z.boolean(),
        allowMarkForReview: z.boolean(),
        allowExplanation: z.boolean(),
        allowHint: z.boolean(),
      })
      .strict(),
    navigation: z
      .object({
        mode: z.string(),
        allowBackNavigation: z.boolean(),
        allowSectionSwitch: z.boolean(),
        allowQuestionReview: z.boolean(),
      })
      .strict(),
    timer: z
      .object({ mode: z.string(), durationSeconds: z.number().nullable(), enforced: z.boolean() })
      .strict(),
    submission: z
      .object({ autoSubmitOnExpiry: z.boolean(), allowManualSubmit: z.boolean() })
      .strict(),
    validation: z
      .object({
        requireResponseWithinTemplate: z.boolean(),
        maxResponseLength: z.number(),
        autoSaveIntervalSeconds: z.number(),
      })
      .strict(),
    presentation: z
      .object({
        showPalette: z.boolean(),
        showTimer: z.boolean(),
        requiresLatexParsing: z.boolean(),
        contentFormat: z.literal("MARKDOWN_LATEX_V1"),
        sectionOrder: z.array(z.string()),
      })
      .strict(),
  })
  .strict();
export type DeliveryConfiguration = z.infer<typeof deliveryConfigurationSchema>;

export const timerSchema = z.object({
  mode: z.string(),
  durationSeconds: z.number().nullable(),
  deadline: z.string().nullable(),
});
export type RuntimeTimer = z.infer<typeof timerSchema>;

export const optionViewSchema = z
  .object({
    optionId: z.string(),
    optionKey: z.string(),
    displayOrder: z.number(),
    content: z.string().nullable(),
    renderedContent: z.string().nullable(),
  })
  .strict();
export type OptionView = z.infer<typeof optionViewSchema>;

export const attemptQuestionSchema = z
  .object({
    attemptQuestionId: z.string(),
    questionVersionId: z.string(),
    displayOrder: z.number(),
    sectionName: z.string().nullable(),
    marks: z.union([z.number(), z.string()]).nullable(),
    negativeMarks: z.union([z.number(), z.string()]).nullable(),
    requiresLatexParsing: z.boolean(),
    contentFormat: z.literal("MARKDOWN_LATEX_V1"),
    content: z.string().nullable(),
    renderedContent: z.string().nullable(),
    options: z.array(optionViewSchema),
  })
  .strict();
export type AttemptQuestion = z.infer<typeof attemptQuestionSchema>;

export const progressSchema = z.object({
  totalQuestions: z.number(),
  answered: z.number(),
  visited: z.number(),
  markedForReview: z.number(),
});

export const attemptResponseSchema = z.object({
  attemptId: z.string(),
  assessmentVersionId: z.string(),
  scheduleId: z.string(),
  attemptMode: attemptModeSchema.nullable(),
  attemptStatus: attemptStatusSchema.nullable(),
  snapshotStatus: snapshotStatusSchema.nullable(),
  startedOn: z.string().nullable(),
  submittedOn: z.string().nullable(),
  serverTime: z.string(),
  timer: timerSchema,
  deliveryConfiguration: deliveryConfigurationSchema,
  progress: progressSchema,
});
export type AttemptResponse = z.infer<typeof attemptResponseSchema>;

export const responseViewSchema = z.object({
  attemptId: z.string(),
  questionVersionId: z.string(),
  response: z.string().nullable(),
  timeSpentSeconds: z.number().nullable(),
  markedForReview: z.boolean(),
  visited: z.boolean(),
  etag: z.string(),
});
export type ResponseView = z.infer<typeof responseViewSchema>;

export const sessionResponseSchema = z.object({
  sessionId: z.string(),
  attemptId: z.string(),
  scheduleId: z.string(),
  sessionStatus: z.string(),
  startedOn: z.string().nullable(),
  lastActivityOn: z.string().nullable(),
  endedOn: z.string().nullable(),
  serverTime: z.string(),
  timer: timerSchema,
});
export type SessionResponse = z.infer<typeof sessionResponseSchema>;

export const startResponseSchema = z.object({
  sessionId: z.string(),
  attemptId: z.string(),
  assessmentVersionId: z.string(),
  mode: attemptModeSchema.nullable(),
  startedOn: z.string().nullable(),
  serverTime: z.string(),
  timer: timerSchema,
  deliveryConfiguration: deliveryConfigurationSchema,
});
export type StartResponse = z.infer<typeof startResponseSchema>;

export const submitResponseSchema = z.object({
  attemptId: z.string(),
  status: attemptStatusSchema,
  submittedOn: z.string().nullable(),
});
export type SubmitResponse = z.infer<typeof submitResponseSchema>;

export const responseSaveRequestSchema = z.object({
  response: z.string().nullable(),
  timeSpentSeconds: z.number().int().nonnegative().nullable(),
  markedForReview: z.boolean(),
  visited: z.boolean(),
});
export type ResponseSaveRequest = z.infer<typeof responseSaveRequestSchema>;
