import { describe, expect, it } from "vitest";

import {
  attemptSummarySchema,
  resultResponseSchema,
  scorecardResponseSchema,
} from "@/features/evaluation/schemas/evaluation";

describe("result schemas", () => {
  it("parses the frozen result projection and coerces decimal strings", () => {
    const parsed = resultResponseSchema.parse({
      id: "r1",
      evaluationId: "e1",
      attemptId: "a1",
      assessmentVersionId: "v1",
      obtainedMarks: "82.50",
      percentage: 82.5,
      rank: 4,
      percentile: "91.5000",
      resultStatus: "published",
      supersededOn: null,
    });

    expect(parsed.obtainedMarks).toBe(82.5);
    expect(parsed.percentile).toBe(91.5);
    expect(parsed.resultStatus).toBe("PUBLISHED");
  });

  it("keeps a missing projection null rather than zero", () => {
    const parsed = resultResponseSchema.parse({
      id: "r1",
      obtainedMarks: null,
      percentage: null,
      rank: null,
      percentile: null,
      resultStatus: "CALCULATED",
    });

    expect(parsed.obtainedMarks).toBeNull();
    expect(parsed.rank).toBeNull();
    expect(parsed.supersededOn).toBeNull();
  });

  it("never treats a missing status as published", () => {
    const parsed = resultResponseSchema.parse({ id: "r1" });

    expect(parsed.resultStatus).toBe("UNKNOWN");
    expect(parsed.resultStatus).not.toBe("PUBLISHED");
  });

  it("parses a scorecard reference without any storage path", () => {
    const parsed = scorecardResponseSchema.parse({
      scorecardId: "s1",
      resultId: "r1",
      status: null,
      generatedOn: null,
      secureDownloadReference: "/api/v1/results/r1/scorecard",
    });

    expect(parsed.status).toBeNull();
    expect(parsed.generatedOn).toBeNull();
    expect(parsed).not.toHaveProperty("filePath");
  });

  it("strips the runtime fields the result list does not need", () => {
    const parsed = attemptSummarySchema.parse({
      attemptId: "a1",
      assessmentVersionId: "v1",
      attemptStatus: "EVALUATED",
      startedOn: "2026-10-01T10:00:00Z",
      deliveryConfiguration: { anything: true },
    });

    expect(parsed.attemptId).toBe("a1");
    expect(parsed).not.toHaveProperty("deliveryConfiguration");
  });
});
