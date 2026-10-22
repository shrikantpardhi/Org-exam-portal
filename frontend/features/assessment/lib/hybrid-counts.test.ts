import { describe, expect, it } from "vitest";

import { computeHybridCounts } from "@/features/assessment/lib/hybrid-counts";
import type {
  AssessmentQuestion,
  SelectionRule,
} from "@/features/assessment/schemas/assessment-builder";

/**
 * Hybrid authoring arithmetic ({@code specs/13-P4-Assessment-V1.5.md §33, §103}).
 *
 * <p>The builder shows manual, rule, target, selected, and remaining counts. The numbers are
 * presentational; the server materializes the final set.
 */
function question(id: string, sectionName: string | null): AssessmentQuestion {
  return {
    id,
    assessmentVersionId: "v1",
    questionVersionId: `qv-${id}`,
    displayOrder: 1,
    sectionName,
    marks: 1,
    negativeMarks: 0,
  };
}

function rule(id: string, sectionName: string, targetCount: number): SelectionRule {
  return {
    id,
    assessmentVersionId: "v1",
    sectionName,
    targetCount,
    displayOrder: 1,
    materializedOn: null,
    materializedQuestionCount: null,
    ruleConfiguration: {},
  };
}

describe("hybrid counts", () => {
  it("separates manual and rule-selected questions and computes the remainder", () => {
    const counts = computeHybridCounts({
      questions: [
        question("1", "Reasoning"),
        question("2", "Reasoning"),
        question("3", "Physics"),
        question("4", "Physics"),
        question("5", "Physics"),
      ],
      rules: [rule("r1", "Physics", 3)],
      sectionTargets: [2, 5],
    });

    expect(counts.manualCount).toBe(2);
    expect(counts.ruleCount).toBe(3);
    expect(counts.selectedCount).toBe(5);
    expect(counts.targetCount).toBe(7);
    expect(counts.remainingCount).toBe(2);
    expect(counts.ruleTargetCount).toBe(3);
  });

  it("reports a negative remainder when the version has too many questions", () => {
    const counts = computeHybridCounts({
      questions: [question("1", "Reasoning"), question("2", "Reasoning"), question("3", "Reasoning")],
      rules: [],
      sectionTargets: [2],
    });

    expect(counts.manualCount).toBe(3);
    expect(counts.ruleCount).toBe(0);
    expect(counts.selectedCount).toBe(3);
    expect(counts.remainingCount).toBe(-1);
  });

  it("treats an unassigned question as manual", () => {
    const counts = computeHybridCounts({
      questions: [question("1", null)],
      rules: [rule("r1", "Physics", 3)],
      sectionTargets: [1, 3],
    });

    expect(counts.manualCount).toBe(1);
    expect(counts.ruleCount).toBe(0);
    expect(counts.targetCount).toBe(4);
  });

  it("handles an empty version", () => {
    const counts = computeHybridCounts({ questions: [], rules: [], sectionTargets: [] });
    expect(counts).toEqual({
      manualCount: 0,
      ruleCount: 0,
      selectedCount: 0,
      targetCount: 0,
      remainingCount: 0,
      ruleTargetCount: 0,
    });
  });
});
