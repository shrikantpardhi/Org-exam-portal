/**
 * Stable, domain-oriented Academic query keys
 * ({@code specs/11-P2-Academic-V1.5.md §68}). Stable hierarchical keys matter more than naming.
 */
export const academicKeys = {
  exams: (filters: unknown = {}) => ["exams", filters] as const,
  subjects: (examId: string, filters: unknown = {}) => ["subjects", { examId, filters }] as const,
  chapters: (subjectId: string, filters: unknown = {}) => ["chapters", { subjectId, filters }] as const,
  topics: (chapterId: string, filters: unknown = {}) => ["topics", { chapterId, filters }] as const,
  subTopics: (topicId: string, filters: unknown = {}) => ["subTopics", { topicId, filters }] as const,
  languages: (filters: unknown = {}) => ["languages", filters] as const,
  tags: (filters: unknown = {}) => ["tags", filters] as const,
};
