import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  addAssessmentQuestion,
  materializeSelectionRules,
  publishAssessmentVersion,
  reorderAssessmentQuestions,
  searchQuestions,
  validateAssessmentVersion,
} from "@/features/assessment/api/assessment-api";
import { apiGet, apiPost, apiPut } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const apiGetMock = vi.mocked(apiGet);
const apiPostMock = vi.mocked(apiPost);
const apiPutMock = vi.mocked(apiPut);

const assessmentQuestion = {
  id: "aq-1",
  assessmentVersionId: "av-1",
  questionVersionId: "qv-1",
  displayOrder: 1,
  sectionName: "Reasoning",
  marks: 2,
  negativeMarks: 0.5,
};

const assessmentVersion = {
  id: "av-1",
  assessmentId: "a-1",
  blueprintVersionId: "bv-1",
  version: 2,
  totalQuestions: 1,
  totalMarks: 2,
  durationMinutes: 60,
  engineType: "NTA",
  published: true,
};

/**
 * Frozen API paths for the builder ({@code specs/13-P4-Assessment-V1.5.md §31, §43, §63, §101}).
 *
 * <p>Every call must use a route that exists in the frozen contract. No invented endpoint, and the
 * version-scoped routes must carry the version id.
 */
describe("assessment builder API", () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiPostMock.mockReset();
    apiPutMock.mockReset();
  });

  it("reads the question picker from the frozen question search route", async () => {
    apiGetMock.mockResolvedValue({ data: [], message: "ok" });

    await searchQuestions({ page: 0, size: 10, isPublished: true });

    expect(apiGetMock.mock.calls[0]![0]).toBe("/questions?page=0&size=10&isPublished=true");
  });

  it("adds a question to the assessment version", async () => {
    apiPostMock.mockResolvedValue({ data: assessmentQuestion, message: "ok" });

    await addAssessmentQuestion("av-1", {
      questionVersionId: "qv-1",
      sectionName: "Reasoning",
      marks: 2,
      negativeMarks: 0.5,
      displayOrder: 1,
    });

    expect(apiPostMock.mock.calls[0]![0]).toBe("/assessment-versions/av-1/questions");
    expect(apiPostMock.mock.calls[0]![1]).toMatchObject({
      questionVersionId: "qv-1",
      sectionName: "Reasoning",
      marks: 2,
      displayOrder: 1,
    });
  });

  it("sends the reorder body through the frozen order route", async () => {
    apiPutMock.mockResolvedValue({ data: {}, message: "ok" });

    await reorderAssessmentQuestions("av-1", [
      { assessmentQuestionId: "aq-2", displayOrder: 1 },
      { assessmentQuestionId: "aq-1", displayOrder: 2 },
    ]);

    expect(apiPutMock.mock.calls[0]![0]).toBe("/assessment-versions/av-1/questions/order");
    expect(apiPutMock.mock.calls[0]![1]).toEqual({
      questionOrders: [
        { assessmentQuestionId: "aq-2", displayOrder: 1 },
        { assessmentQuestionId: "aq-1", displayOrder: 2 },
      ],
    });
  });

  it("posts the only materialize mode the contract defines", async () => {
    apiPostMock.mockResolvedValue({ data: [assessmentQuestion], message: "ok" });

    // REPLACE_UNMATERIALIZED is the single frozen value (13-P4 §63); the server rejects anything else.
    const materialized = await materializeSelectionRules("av-1", "REPLACE_UNMATERIALIZED");

    expect(apiPostMock.mock.calls[0]![0]).toBe("/assessment-versions/av-1/rules/materialize");
    expect(apiPostMock.mock.calls[0]![1]).toEqual({ mode: "REPLACE_UNMATERIALIZED" });
    expect(materialized).toHaveLength(1);
  });

  it("rejects an unsupported materialize mode before it reaches the network", async () => {
    // The Zod literal is the guard that stops a stale MANUAL/RULE/HYBRID choice from being sent.
    await expect(
      materializeSelectionRules("av-1", "HYBRID" as unknown as "REPLACE_UNMATERIALIZED"),
    ).rejects.toThrow();
    expect(apiPostMock).not.toHaveBeenCalled();
  });

  it("validates and publishes through version-scoped routes", async () => {
    apiPostMock.mockResolvedValue({ data: { valid: true, errors: [] }, message: "ok" });

    const validation = await validateAssessmentVersion("av-1");
    expect(apiPostMock.mock.calls[0]![0]).toBe("/assessment-versions/av-1/validate");
    expect(validation.valid).toBe(true);

    apiPostMock.mockResolvedValue({ data: assessmentVersion, message: "ok" });
    await publishAssessmentVersion("av-1");
    expect(apiPostMock.mock.calls[1]![0]).toBe("/assessment-versions/av-1/publish");
  });
});
