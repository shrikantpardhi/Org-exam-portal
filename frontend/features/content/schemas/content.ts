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
