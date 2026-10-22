/**
 * Hybrid authoring arithmetic ({@code specs/13-P4-Assessment-V1.5.md §33, §103}).
 *
 * <p>The builder shows manual count, rule count, target count, selected count, and remaining count.
 * The server ultimately materializes the concrete AssessmentQuestion set, so these numbers describe
 * authoring intent only — they never decide what gets persisted.
 */
import type {
  AssessmentQuestion,
  SelectionRule,
} from "@/features/assessment/schemas/assessment-builder";

export interface HybridCounts {
  /** Questions belonging to a section that has no selection rule (added by hand). */
  manualCount: number;
  /** Questions belonging to a section that does have a selection rule (selected by rule). */
  ruleCount: number;
  /** manualCount + ruleCount. */
  selectedCount: number;
  /** Sum of the blueprint's section question counts. */
  targetCount: number;
  /** targetCount - selectedCount; negative means the version has more questions than intended. */
  remainingCount: number;
  /** Sum of every rule's target count. */
  ruleTargetCount: number;
}

export function computeHybridCounts({
  questions,
  rules,
  sectionTargets,
}: {
  questions: readonly AssessmentQuestion[];
  rules: readonly SelectionRule[];
  sectionTargets: readonly number[];
}): HybridCounts {
  const ruledSections = new Set(rules.map((rule) => rule.sectionName));

  let manualCount = 0;
  let ruleCount = 0;
  for (const question of questions) {
    if (question.sectionName && ruledSections.has(question.sectionName)) {
      ruleCount += 1;
    } else {
      manualCount += 1;
    }
  }

  const selectedCount = manualCount + ruleCount;
  const targetCount = sectionTargets.reduce((total, value) => total + value, 0);
  const ruleTargetCount = rules.reduce((total, rule) => total + rule.targetCount, 0);

  return {
    manualCount,
    ruleCount,
    selectedCount,
    targetCount,
    remainingCount: targetCount - selectedCount,
    ruleTargetCount,
  };
}
