/**
 * Academic API layer ({@code specs/11-P2-Academic-V1.5.md §30-§37}).
 *
 * <p>All Axios calls for Academic live here; components never call Axios directly. List endpoints
 * return the standard paged envelope, which the client unwraps.
 */
import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/api-client";
import {
  chapterSchema,
  examSchema,
  languageSchema,
  subTopicSchema,
  subjectSchema,
  tagSchema,
  topicSchema,
  type Chapter,
  type Exam,
  type Language,
  type SubTopic,
  type Subject,
  type Tag,
  type Topic,
} from "@/features/academic/schemas/academic";
import { z } from "zod";

function listSchema<T extends z.ZodTypeAny>(item: T) {
  return z.array(item);
}

export interface ListParams {
  page?: number;
  size?: number;
  search?: string;
  status?: string;
}

function query(params: Record<string, string | number | undefined>): string {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== "");
  if (entries.length === 0) {
    return "";
  }
  return "?" + entries.map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`).join("&");
}

// ----- Exams -----
export async function listExams(params: ListParams = {}): Promise<Exam[]> {
  const result = await apiGet<unknown>(`/exams${query({ ...params })}`);
  return listSchema(examSchema).parse(result.data);
}

export async function createExam(body: { code: string; name: string; description?: string }): Promise<Exam> {
  const result = await apiPost<unknown>("/exams", body);
  return examSchema.parse(result.data);
}

export async function archiveExam(id: string): Promise<void> {
  await apiDelete(`/exams/${id}`);
}

// ----- Subjects -----
export async function listSubjects(examId: string, params: ListParams = {}): Promise<Subject[]> {
  const result = await apiGet<unknown>(`/subjects${query({ examId, ...params })}`);
  return listSchema(subjectSchema).parse(result.data);
}

export async function createSubject(body: {
  examId: string;
  code: string;
  name: string;
  description?: string;
}): Promise<Subject> {
  const result = await apiPost<unknown>("/subjects", body);
  return subjectSchema.parse(result.data);
}

// ----- Chapters -----
export async function listChapters(subjectId: string, params: ListParams = {}): Promise<Chapter[]> {
  const result = await apiGet<unknown>(`/chapters${query({ subjectId, ...params })}`);
  return listSchema(chapterSchema).parse(result.data);
}

export async function createChapter(body: {
  subjectId: string;
  code: string;
  name: string;
}): Promise<Chapter> {
  const result = await apiPost<unknown>("/chapters", body);
  return chapterSchema.parse(result.data);
}

// ----- Topics -----
export async function listTopics(chapterId: string, params: ListParams = {}): Promise<Topic[]> {
  const result = await apiGet<unknown>(`/topics${query({ chapterId, ...params })}`);
  return listSchema(topicSchema).parse(result.data);
}

export async function createTopic(body: { chapterId: string; code: string; name: string }): Promise<Topic> {
  const result = await apiPost<unknown>("/topics", body);
  return topicSchema.parse(result.data);
}

// ----- SubTopics -----
export async function listSubTopics(topicId: string, params: ListParams = {}): Promise<SubTopic[]> {
  const result = await apiGet<unknown>(`/sub-topics${query({ topicId, ...params })}`);
  return listSchema(subTopicSchema).parse(result.data);
}

export async function createSubTopic(body: { topicId: string; code: string; name: string }): Promise<SubTopic> {
  const result = await apiPost<unknown>("/sub-topics", body);
  return subTopicSchema.parse(result.data);
}

// ----- Languages -----
export async function listLanguages(params: ListParams = {}): Promise<Language[]> {
  const result = await apiGet<unknown>(`/languages${query({ ...params })}`);
  return listSchema(languageSchema).parse(result.data);
}

export async function createLanguage(body: { code: string; name: string }): Promise<Language> {
  const result = await apiPost<unknown>("/languages", body);
  return languageSchema.parse(result.data);
}

export async function updateLanguage(id: string, body: { name?: string; status?: string }): Promise<Language> {
  const result = await apiPut<unknown>(`/languages/${id}`, body);
  return languageSchema.parse(result.data);
}

// ----- Tags -----
export async function listTags(params: ListParams = {}): Promise<Tag[]> {
  const result = await apiGet<unknown>(`/tags${query({ ...params })}`);
  return listSchema(tagSchema).parse(result.data);
}

export async function createTag(body: { code: string; name: string; description?: string }): Promise<Tag> {
  const result = await apiPost<unknown>("/tags", body);
  return tagSchema.parse(result.data);
}

export async function updateTag(
  id: string,
  body: { name?: string; description?: string; status?: string },
): Promise<Tag> {
  const result = await apiPut<unknown>(`/tags/${id}`, body);
  return tagSchema.parse(result.data);
}

export async function archiveLanguage(id: string): Promise<void> {
  await apiDelete(`/languages/${id}`);
}

export async function archiveTag(id: string): Promise<void> {
  await apiDelete(`/tags/${id}`);
}
