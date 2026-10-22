/**
 * Analytics response schemas ({@code specs/17-P8-Analytics-V1.5.md §137-§141}).
 *
 * <p>The backend projects a missing population as {@code null}, never as a zero
 * ({@code §90}), so every nullable metric stays nullable here. Coercing a missing metric to zero
 * would display a misleading value ({@code §146}). Decimal values arrive as JSON numbers; a
 * numeric string is also accepted so a plain-number Jackson setting cannot break the dashboard.
 */
import { z } from "zod";

/** A nullable decimal metric. A missing population stays null. */
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

/** A nullable integer metric. A missing population stays null. */
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

/** An integer that the contract always sends (for example a section question count). */
const requiredInteger = z.number().int();

const booleanFlag = z
  .boolean()
  .nullish()
  .transform((value) => value ?? false);

const timestamp = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const nullableId = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

/** The API freshness concept ({@code §69}). The status is a view concept, never a stored column. */
export const freshnessSchema = z.string();

export const studentAnalyticsSchema = z.object({
  assessmentsAttempted: integer,
  averageScore: decimal,
  accuracy: decimal,
  averageTime: decimal,
  lastCalculatedOn: timestamp,
  freshness: freshnessSchema,
});

export const studentAssessmentItemSchema = z.object({
  assessmentId: nullableId,
  attemptId: z.string(),
  assessmentVersionId: nullableId,
  attemptMode: nullableId,
  score: decimal,
  percentage: decimal,
  resultStatus: nullableId,
  startedOn: timestamp,
});

export const studentTopicItemSchema = z.object({
  topicId: z.string(),
  evaluatedOccurrences: integer,
  accuracy: decimal,
  averageScore: decimal,
  robustSample: booleanFlag,
  weakArea: booleanFlag,
});

export const sectionAnalyticsSchema = z.object({
  sectionName: z
    .string()
    .nullish()
    .transform((value) => value ?? "Unsectioned"),
  questionCount: requiredInteger,
  totalMarks: decimal,
  obtainedMarks: decimal,
  accuracy: decimal,
  averageTime: decimal,
});

export const assessmentAnalyticsSchema = z.object({
  assessmentId: z.string(),
  totalAttempts: integer,
  averageScore: decimal,
  highestScore: decimal,
  lowestScore: decimal,
  lastCalculatedOn: timestamp,
  freshness: freshnessSchema,
});

export const questionAnalyticsSchema = z.object({
  questionId: z.string(),
  totalAttempts: integer,
  correctAttempts: integer,
  incorrectAttempts: integer,
  averageTime: decimal,
  difficultyIndex: decimal,
  correctPercentage: decimal,
  incorrectPercentage: decimal,
  skippedPercentage: decimal,
  lastCalculatedOn: timestamp,
  freshness: freshnessSchema,
});

export const topicAnalyticsSchema = z.object({
  topicId: z.string(),
  totalAttempts: integer,
  averageScore: decimal,
  accuracy: decimal,
  sampleSize: requiredInteger,
  robustSample: booleanFlag,
  lastCalculatedOn: timestamp,
  freshness: freshnessSchema,
});

export const organizationAnalyticsSchema = z.object({
  organizationId: nullableId,
  totalStudents: integer,
  totalAssessments: integer,
  totalAttempts: integer,
  activeUsers: integer,
  lastCalculatedOn: timestamp,
  freshness: freshnessSchema,
});

export const studentDashboardSchema = z.object({
  summary: studentAnalyticsSchema,
  recentAssessments: z.array(studentAssessmentItemSchema).default([]),
  topics: z.array(studentTopicItemSchema).default([]),
});

export const assessmentDashboardSchema = z.object({
  summary: assessmentAnalyticsSchema,
  sections: z.array(sectionAnalyticsSchema).default([]),
});

export type Freshness = z.infer<typeof freshnessSchema>;
export type StudentAnalytics = z.infer<typeof studentAnalyticsSchema>;
export type StudentAssessmentItem = z.infer<typeof studentAssessmentItemSchema>;
export type StudentTopicItem = z.infer<typeof studentTopicItemSchema>;
export type SectionAnalytics = z.infer<typeof sectionAnalyticsSchema>;
export type AssessmentAnalytics = z.infer<typeof assessmentAnalyticsSchema>;
export type QuestionAnalytics = z.infer<typeof questionAnalyticsSchema>;
export type TopicAnalytics = z.infer<typeof topicAnalyticsSchema>;
export type OrganizationAnalytics = z.infer<typeof organizationAnalyticsSchema>;
export type StudentDashboard = z.infer<typeof studentDashboardSchema>;
export type AssessmentDashboard = z.infer<typeof assessmentDashboardSchema>;
