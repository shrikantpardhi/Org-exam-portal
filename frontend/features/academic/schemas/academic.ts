/**
 * Academic response/request schemas ({@code specs/11-P2-Academic-V1.5.md §45}).
 *
 * <p>Responses are validated at the boundary so an unexpected server shape fails fast.
 */
import { z } from "zod";

export const statusSchema = z.enum(["ACTIVE", "INACTIVE", "ARCHIVED"]);
export type Status = z.infer<typeof statusSchema>;

const codeField = (max: number) => z.string().trim().min(1, "Code is required").max(max);
const nameField = (max: number) => z.string().trim().min(1, "Name is required").max(max);

export const examSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  status: statusSchema,
  updatedOn: z.string().nullish(),
});
export const subjectSchema = z.object({
  id: z.string(),
  examId: z.string(),
  code: z.string(),
  name: z.string(),
  status: statusSchema,
  updatedOn: z.string().nullish(),
});
export const chapterSchema = z.object({
  id: z.string(),
  subjectId: z.string(),
  code: z.string(),
  name: z.string(),
  status: statusSchema,
  updatedOn: z.string().nullish(),
});
export const topicSchema = z.object({
  id: z.string(),
  chapterId: z.string(),
  code: z.string(),
  name: z.string(),
  status: statusSchema,
  updatedOn: z.string().nullish(),
});
export const subTopicSchema = z.object({
  id: z.string(),
  topicId: z.string(),
  code: z.string(),
  name: z.string(),
  status: statusSchema,
  updatedOn: z.string().nullish(),
});
export const languageSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  status: statusSchema,
  updatedOn: z.string().nullish(),
});
export const tagSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  status: statusSchema,
  updatedOn: z.string().nullish(),
});

export type Exam = z.infer<typeof examSchema>;
export type Subject = z.infer<typeof subjectSchema>;
export type Chapter = z.infer<typeof chapterSchema>;
export type Topic = z.infer<typeof topicSchema>;
export type SubTopic = z.infer<typeof subTopicSchema>;
export type Language = z.infer<typeof languageSchema>;
export type Tag = z.infer<typeof tagSchema>;

export const codeNameFormSchema = z.object({
  code: codeField(50),
  name: nameField(150),
  description: z.string().max(20000).optional(),
});

export const examFormSchema = codeNameFormSchema;
export const languageFormSchema = z.object({ code: codeField(20), name: nameField(100) });
export const tagFormSchema = z.object({
  code: codeField(50),
  name: nameField(100),
  description: z.string().max(20000).optional(),
});

export type CodeNameForm = z.infer<typeof codeNameFormSchema>;
export type LanguageForm = z.infer<typeof languageFormSchema>;
export type TagForm = z.infer<typeof tagFormSchema>;
