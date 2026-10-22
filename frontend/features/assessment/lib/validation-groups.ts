/**
 * Validation grouping for the authoring panel ({@code specs/13-P4-Assessment-V1.5.md §106}).
 *
 * <p>The UI groups issues by category derived from the issue code prefix. Unknown codes are never
 * dropped — they land in "Other" so an unrecognised server rule stays visible to the operator.
 */
import type { ValidationResult } from "@/features/assessment/schemas/assessment";

export const VALIDATION_CATEGORIES = [
  "Blueprint",
  "Questions",
  "Sections",
  "Scoring",
  "Timing",
  "Engine",
  "Publication",
  "Other",
] as const;

export type ValidationCategory = (typeof VALIDATION_CATEGORIES)[number];

export interface GroupedIssue {
  code: string;
  message: string;
}

export interface ValidationGroup {
  category: ValidationCategory;
  issues: GroupedIssue[];
  count: number;
}

/**
 * Maps a frozen issue code to its display category.
 *
 * <p>Prefixes come from the backend's structured checks: blueprint configuration, question
 * eligibility, section counts, marks, duration, engine/policy, and publication readiness.
 */
export function categoryForCode(code: string): ValidationCategory {
  const normalized = code.toUpperCase();

  if (normalized.startsWith("BLUEPRINT")) {
    return "Blueprint";
  }
  if (normalized.startsWith("QUESTION") || normalized.startsWith("CONTENT")) {
    return "Questions";
  }
  if (normalized.startsWith("SECTION")) {
    return "Sections";
  }
  if (normalized.startsWith("TOTAL_MARKS") || normalized.startsWith("SCORING") || normalized.startsWith("MARK")) {
    return "Scoring";
  }
  if (normalized.startsWith("DURATION") || normalized.startsWith("TIMER") || normalized.startsWith("SCHEDULE")) {
    return "Timing";
  }
  if (normalized.startsWith("ENGINE") || normalized.startsWith("DELIVERY_POLICY")) {
    return "Engine";
  }
  if (normalized.startsWith("PUBLISH") || normalized.startsWith("PUBLICATION")) {
    return "Publication";
  }
  return "Other";
}

/** Groups a validation result into the frozen display categories, preserving category order. */
export function groupValidationIssues(result: ValidationResult): ValidationGroup[] {
  const buckets = new Map<ValidationCategory, GroupedIssue[]>();
  for (const issue of result.errors) {
    const category = categoryForCode(issue.code);
    const bucket = buckets.get(category) ?? [];
    bucket.push({ code: issue.code, message: issue.message });
    buckets.set(category, bucket);
  }

  return VALIDATION_CATEGORIES.flatMap((category) => {
    const issues = buckets.get(category);
    return issues && issues.length > 0 ? [{ category, issues, count: issues.length }] : [];
  });
}

/**
 * A short human summary such as "2 questions missing" ({@code §106}).
 *
 * <p>Deliberately generic: it counts what the server reported instead of re-deriving the rule, so it
 * cannot drift from backend validation.
 */
const CATEGORY_NOUN: Record<ValidationCategory, string> = {
  Blueprint: "blueprint",
  Questions: "question",
  Sections: "section",
  Scoring: "scoring",
  Timing: "timing",
  Engine: "engine",
  Publication: "publication",
  Other: "other",
};

export function summarizeGroup(group: ValidationGroup): string {
  const noun = CATEGORY_NOUN[group.category];
  const suffix = group.count === 1 ? "" : "s";
  return `${group.count} ${noun} issue${suffix}`;
}
