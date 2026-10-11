"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  archiveExam,
  archiveLanguage,
  archiveTag,
  createChapter,
  createExam,
  createLanguage,
  createSubject,
  createSubTopic,
  createTag,
  createTopic,
  listChapters,
  listExams,
  listLanguages,
  listSubTopics,
  listSubjects,
  listTags,
  listTopics,
} from "@/features/academic/api/academic-api";
import { academicKeys } from "@/features/academic/query-keys";
import type { ApiClientError } from "@/lib/errors";

// ----- Read queries (child queries only run when their component mounts - lazy loading) -----

export function useExams(filters: Record<string, unknown> = {}) {
  return useQuery({ queryKey: academicKeys.exams(filters), queryFn: () => listExams(filters) });
}

export function useSubjects(examId: string) {
  return useQuery({
    queryKey: academicKeys.subjects(examId),
    queryFn: () => listSubjects(examId),
    enabled: examId.length > 0,
  });
}

export function useChapters(subjectId: string) {
  return useQuery({
    queryKey: academicKeys.chapters(subjectId),
    queryFn: () => listChapters(subjectId),
    enabled: subjectId.length > 0,
  });
}

export function useTopics(chapterId: string) {
  return useQuery({
    queryKey: academicKeys.topics(chapterId),
    queryFn: () => listTopics(chapterId),
    enabled: chapterId.length > 0,
  });
}

export function useSubTopics(topicId: string) {
  return useQuery({
    queryKey: academicKeys.subTopics(topicId),
    queryFn: () => listSubTopics(topicId),
    enabled: topicId.length > 0,
  });
}

export function useLanguages(filters: Record<string, unknown> = {}) {
  return useQuery({ queryKey: academicKeys.languages(filters), queryFn: () => listLanguages(filters) });
}

export function useTags(filters: Record<string, unknown> = {}) {
  return useQuery({ queryKey: academicKeys.tags(filters), queryFn: () => listTags(filters) });
}

// ----- Mutations (invalidate the affected keys on success) -----

export function useCreateExam() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string; description?: string }>({
    mutationFn: createExam,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["exams"] }),
  });
}

export function useArchiveExam() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: archiveExam,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["exams"] }),
  });
}

export function useCreateSubject(examId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string }>({
    mutationFn: (body) => createSubject({ examId, ...body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["subjects", { examId }] }),
  });
}

export function useCreateChapter(subjectId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string }>({
    mutationFn: (body) => createChapter({ subjectId, ...body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["chapters", { subjectId }] }),
  });
}

export function useCreateTopic(chapterId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string }>({
    mutationFn: (body) => createTopic({ chapterId, ...body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["topics", { chapterId }] }),
  });
}

export function useCreateSubTopic(topicId: string) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string }>({
    mutationFn: (body) => createSubTopic({ topicId, ...body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["subTopics", { topicId }] }),
  });
}

export function useCreateLanguage() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string }>({
    mutationFn: createLanguage,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["languages"] }),
  });
}

export function useArchiveLanguage() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: archiveLanguage,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["languages"] }),
  });
}

export function useCreateTag() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, { code: string; name: string; description?: string }>({
    mutationFn: createTag,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["tags"] }),
  });
}

export function useArchiveTag() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiClientError, string>({
    mutationFn: archiveTag,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["tags"] }),
  });
}
