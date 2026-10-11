import { describe, expect, it } from "vitest";

import {
  assessmentResponseSchema,
  engineTypeSchema,
  validationResultSchema,
} from "@/features/assessment/schemas/assessment";

describe("assessment schemas", () => {
  it("accepts the frozen engine types and rejects unknown ones", () => {
    expect(engineTypeSchema.safeParse("TCS_ION").success).toBe(true);
    expect(engineTypeSchema.safeParse("CUSTOM").success).toBe(true);
    expect(engineTypeSchema.safeParse("MYSTERY_ENGINE").success).toBe(false);
  });

  it("validates an assessment response with version summaries", () => {
    const parsed = assessmentResponseSchema.safeParse({
      id: "a1",
      code: "SSC-MOCK-001",
      name: "Mock 1",
      status: "ACTIVE",
      versions: [{ id: "v1", version: 1, published: false, engineType: "CUSTOM", durationMinutes: 60 }],
    });
    expect(parsed.success).toBe(true);
  });

  it("validates a validation result with structured issues", () => {
    const parsed = validationResultSchema.safeParse({
      valid: false,
      errors: [{ code: "QUESTION_COUNT_MISMATCH", message: "requires 100 but 98 configured" }],
    });
    expect(parsed.success).toBe(true);
  });
});
