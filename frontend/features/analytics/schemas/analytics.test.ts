import { describe, expect, it } from "vitest";

import {
  questionAnalyticsSchema,
  studentDashboardSchema,
  topicAnalyticsSchema,
} from "@/features/analytics/schemas/analytics";

describe("analytics schemas", () => {
  it("keeps a missing population null instead of a misleading zero", () => {
    const parsed = studentDashboardSchema.parse({
      summary: {
        assessmentsAttempted: null,
        averageScore: null,
        accuracy: null,
        averageTime: null,
        lastCalculatedOn: null,
        freshness: "EMPTY",
      },
      recentAssessments: [],
      topics: [],
    });

    expect(parsed.summary.assessmentsAttempted).toBeNull();
    expect(parsed.summary.averageScore).toBeNull();
    expect(parsed.summary.accuracy).toBeNull();
    expect(parsed.summary.freshness).toBe("EMPTY");
  });

  it("accepts decimal strings and strips unknown fields", () => {
    const parsed = questionAnalyticsSchema.parse({
      questionId: "q1",
      totalAttempts: 10,
      correctAttempts: 6,
      incorrectAttempts: 3,
      averageTime: "42.50",
      difficultyIndex: 0.4,
      correctPercentage: "60.00",
      incorrectPercentage: "30.00",
      skippedPercentage: null,
      lastCalculatedOn: "2026-10-20T10:00:00Z",
      freshness: "CURRENT",
      unexpected: "ignored",
    });

    expect(parsed.averageTime).toBe(42.5);
    expect(parsed.correctPercentage).toBe(60);
    expect(parsed.skippedPercentage).toBeNull();
    expect(parsed).not.toHaveProperty("unexpected");
  });

  it("defaults the dashboard lists when the backend omits them", () => {
    const parsed = studentDashboardSchema.parse({
      summary: {
        assessmentsAttempted: 1,
        averageScore: 10,
        accuracy: 50,
        averageTime: 20,
        lastCalculatedOn: "2026-10-20T10:00:00Z",
        freshness: "CURRENT",
      },
    });

    expect(parsed.recentAssessments).toEqual([]);
    expect(parsed.topics).toEqual([]);
  });

  it("keeps a topic sample guard typed even when the flag is absent", () => {
    const parsed = topicAnalyticsSchema.parse({
      topicId: "t1",
      totalAttempts: null,
      averageScore: null,
      accuracy: null,
      sampleSize: 0,
      lastCalculatedOn: null,
      freshness: "EMPTY",
    });

    expect(parsed.robustSample).toBe(false);
    expect(parsed.sampleSize).toBe(0);
  });
});
