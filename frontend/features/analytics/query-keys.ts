/**
 * Analytics query keys ({@code specs/17-P8-Analytics-V1.5.md §143}).
 *
 * <p>Server analytics data is keyed separately from any future filter or chart display state, so a
 * filter change never mutates a cached server payload.
 */
export const analyticsKeys = {
  all: () => ["analytics"] as const,
  student: () => ["analytics", "student"] as const,
  studentAssessments: () => ["analytics", "student", "assessments"] as const,
  studentTopics: () => ["analytics", "student", "topics"] as const,
  assessment: (assessmentId: string) => ["analytics", "assessments", assessmentId] as const,
  assessmentQuestions: (assessmentId: string) =>
    ["analytics", "assessments", assessmentId, "questions"] as const,
  question: (questionId: string) => ["analytics", "questions", questionId] as const,
  topic: (topicId: string) => ["analytics", "topics", topicId] as const,
  organization: () => ["analytics", "organization"] as const,
};
