/**
 * Content schemas ({@code specs/12-P3-Content-V1.5.md §83, §125, §126}).
 *
 * <p>The student delivery projection never contains correctness; authoring DTOs may. Response
 * shapes are validated at the boundary.
 */
import { z } from "zod";

const numberish = z.union([z.number(), z.string()]);

export const reviewStatusSchema = z.enum(["DRAFT", "IN_REVIEW", "APPROVED", "REJECTED", "PUBLISHED"]);
export const difficultySchema = z.enum(["EASY", "MEDIUM", "HARD"]);
export type Difficulty = z.infer<typeof difficultySchema>;
export type ReviewStatus = z.infer<typeof reviewStatusSchema>;

export const questionTemplateSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  requiresLatexParsing: z.boolean(),
  status: z.string(),
});
export type QuestionTemplate = z.infer<typeof questionTemplateSchema>;

export const questionListItemSchema = z.object({
  id: z.string(),
  templateId: z.string().nullish(),
  examId: z.string().nullish(),
  subjectId: z.string().nullish(),
  difficulty: difficultySchema.nullish(),
  latestVersion: z.number().nullish(),
  latestReviewStatus: reviewStatusSchema.nullish(),
  published: z.boolean(),
  updatedOn: z.string().nullish(),
});
export type QuestionListItem = z.infer<typeof questionListItemSchema>;

export const versionSummarySchema = z.object({
  id: z.string(),
  version: z.number(),
  reviewStatus: reviewStatusSchema,
  published: z.boolean(),
  marks: numberish.nullish(),
  negativeMarks: numberish.nullish(),
  difficulty: difficultySchema.nullish(),
  publishedOn: z.string().nullish(),
});

export const questionResponseSchema = z.object({
  id: z.string(),
  templateId: z.string().nullish(),
  examId: z.string().nullish(),
  subjectId: z.string().nullish(),
  chapterId: z.string().nullish(),
  topicId: z.string().nullish(),
  subTopicId: z.string().nullish(),
  status: z.string(),
  versions: z.array(versionSummarySchema),
  tagIds: z.array(z.string()),
});
export type QuestionResponse = z.infer<typeof questionResponseSchema>;

export const versionResponseSchema = z.object({
  id: z.string(),
  questionId: z.string(),
  version: z.number(),
  title: z.string().nullish(),
  content: z.string().nullish(),
  contentFormat: z.string(),
  renderedContent: z.string().nullish(),
  marks: numberish.nullish(),
  negativeMarks: numberish.nullish(),
  difficulty: difficultySchema.nullish(),
  languageId: z.string().nullish(),
  published: z.boolean(),
  reviewStatus: reviewStatusSchema,
  reviewedOn: z.string().nullish(),
  reviewRemarks: z.string().nullish(),
});
export type VersionResponse = z.infer<typeof versionResponseSchema>;

/**
 * A row of {@code GET /questions/{id}/versions} ({@code §110}).
 *
 * <p>Carries exactly the facts the version history must show: version number, review state,
 * published state and the scoring metadata for that version.
 */
export const versionListItemSchema = z.object({
  id: z.string(),
  version: z.number(),
  reviewStatus: reviewStatusSchema,
  published: z.boolean(),
  marks: numberish.nullish(),
  negativeMarks: numberish.nullish(),
  difficulty: difficultySchema.nullish(),
  publishedOn: z.string().nullish(),
});
export type VersionListItem = z.infer<typeof versionListItemSchema>;

export const optionSchema = z.object({
  id: z.string(),
  questionVersionId: z.string(),
  optionKey: z.string(),
  content: z.string().nullish(),
  displayOrder: z.number(),
  isCorrect: z.boolean(),
});
export type QuestionOption = z.infer<typeof optionSchema>;

/** Create form: canonical content plus taxonomy and scoring metadata. */
export const questionFormSchema = z.object({
  templateId: z.string().min(1, "Template is required"),
  examId: z.string().min(1, "Exam is required"),
  subjectId: z.string().min(1, "Subject is required"),
  languageId: z.string().min(1, "Language is required"),
  title: z.string().max(255).optional(),
  content: z.string().min(1, "Content is required"),
  marks: z.coerce.number().nonnegative("Marks must be zero or greater"),
  negativeMarks: z.coerce.number().nonnegative("Negative marks must be zero or greater"),
  difficulty: difficultySchema,
});
export type QuestionForm = z.infer<typeof questionFormSchema>;

// ----- Templates (§87) -----

export const templateFormSchema = z.object({
  code: z.string().trim().min(1, "Code is required").max(50),
  name: z.string().trim().min(1, "Name is required").max(150),
  description: z.string().max(2000).optional(),
  configuration: z.string().optional(),
});
export type TemplateForm = z.infer<typeof templateFormSchema>;

// ----- Create / update payloads (§88, §102) -----

/**
 * The exact {@code POST /questions} body: request keys only, no client-only fields.
 *
 * <p>Optional keys are omitted rather than sent as {@code null}, so the payload is predictable.
 * {@code .strict()} guarantees the editor cannot leak an extra key into the request.
 */
export const createQuestionPayloadSchema = z
  .object({
    templateId: z.string().min(1, "Template is required"),
    examId: z.string().min(1, "Exam is required"),
    subjectId: z.string().min(1, "Subject is required"),
    chapterId: z.string().min(1).optional(),
    topicId: z.string().min(1).optional(),
    subTopicId: z.string().min(1).optional(),
    title: z.string().max(255).optional(),
    content: z.string().min(1, "Content is required"),
    marks: z.number().nonnegative("Marks must be zero or greater"),
    negativeMarks: z.number().nonnegative("Negative marks must be zero or greater").optional(),
    difficulty: difficultySchema,
    languageId: z.string().min(1, "Language is required"),
  })
  .strict();
export type CreateQuestionPayload = z.infer<typeof createQuestionPayloadSchema>;

/** The exact {@code POST /questions/{id}/versions} body. */
export const versionCreateRequestSchema = z
  .object({
    title: z.string().max(255).optional(),
    content: z.string().min(1, "Content is required"),
    marks: z.number().nonnegative("Marks must be zero or greater"),
    negativeMarks: z.number().nonnegative("Negative marks must be zero or greater").optional(),
    difficulty: difficultySchema,
    languageId: z.string().min(1, "Language is required"),
  })
  .strict();
export type VersionCreateRequest = z.infer<typeof versionCreateRequestSchema>;

/** Draft-level question metadata; versioned content is never changed through this route. */
export const questionMetadataUpdateSchema = z
  .object({
    templateId: z.string().min(1).optional(),
    examId: z.string().min(1).optional(),
    subjectId: z.string().min(1).optional(),
    chapterId: z.string().min(1).optional(),
    topicId: z.string().min(1).optional(),
    subTopicId: z.string().min(1).optional(),
    tagIds: z.array(z.string()).optional(),
  })
  .strict();
export type QuestionMetadataUpdate = z.infer<typeof questionMetadataUpdateSchema>;

// ----- Options (§103) -----

export const optionDraftSchema = z.object({
  optionKey: z.string().trim().min(1, "Option key is required").max(20, "Option key is at most 20 characters"),
  content: z.string().min(1, "Option content is required"),
  displayOrder: z.number().int().nonnegative(),
  isCorrect: z.boolean(),
});
export type OptionDraft = z.infer<typeof optionDraftSchema>;

/** The exact option create/update body; {@code isCorrect} is authoring-only and never student-facing. */
export const optionRequestSchema = z
  .object({
    optionKey: z.string().trim().min(1).max(20),
    content: z.string().min(1),
    displayOrder: z.number().int().nonnegative(),
    isCorrect: z.boolean(),
  })
  .strict();
export type OptionRequest = z.infer<typeof optionRequestSchema>;

// ----- Assets (§105) -----

export const assetSchema = z.object({
  id: z.string(),
  questionVersionId: z.string(),
  fileName: z.string(),
  fileType: z.string(),
  status: z.string(),
});
export type ContentAsset = z.infer<typeof assetSchema>;

// ----- Explanation and hint (§106, §107) -----

export const versionTextContentSchema = z.object({
  id: z.string(),
  questionVersionId: z.string(),
  content: z.string(),
  contentFormat: z.string(),
  renderedContent: z.string().nullish(),
});
export type VersionTextContent = z.infer<typeof versionTextContentSchema>;

/** Request body for explanation and hint create/update. */
export const textContentRequestSchema = z.object({
  content: z.string().trim().min(1, "Content is required"),
});
export type TextContentRequest = z.infer<typeof textContentRequestSchema>;

// ----- Previous exam history (§108) -----

export const previousExamSchema = z.object({
  id: z.string(),
  questionId: z.string(),
  examId: z.string(),
  year: z.number().nullish(),
  shift: z.string().nullish(),
  source: z.string().nullish(),
});
export type PreviousExam = z.infer<typeof previousExamSchema>;

/** The exact previous-exam create body; {@code examId} always comes from the Academic masters. */
export const previousExamRequestSchema = z
  .object({
    examId: z.string().min(1, "Exam is required"),
    year: z.number().int().min(1900, "Year must be 1900 or later").max(2100, "Year must be 2100 or earlier").optional(),
    shift: z.string().max(50).optional(),
    source: z.string().max(100).optional(),
  })
  .strict();
export type PreviousExamRequest = z.infer<typeof previousExamRequestSchema>;

/** `PUT /questions/{id}/previous-exams/{historyId}` body: the exam cannot be reassigned. */
export const previousExamUpdateRequestSchema = z
  .object({
    year: z.number().int().min(1900).max(2100).optional(),
    shift: z.string().max(50).optional(),
    source: z.string().max(100).optional(),
  })
  .strict();
export type PreviousExamUpdateRequest = z.infer<typeof previousExamUpdateRequestSchema>;

/** Form-level previous-exam values, before numeric coercion. */
export interface PreviousExamFormValues {
  examId: string;
  year?: string;
  shift?: string;
  source?: string;
}
