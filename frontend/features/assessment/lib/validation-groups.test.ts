import { describe, expect, it } from "vitest";

import {
  categoryForCode,
  groupValidationIssues,
  summarizeGroup,
} from "@/features/assessment/lib/validation-groups";

/**
 * Validation grouping ({@code specs/13-P4-Assessment-V1.5.md §106}).
 *
 * <p>Error codes come from the backend's structured checks. The panel must group them by prefix and
 * must never silently drop an unrecognised code.
 */
describe("validation grouping", () => {
  it("maps each frozen code family to its category", () => {
    expect(categoryForCode("BLUEPRINT_INVALID")).toBe("Blueprint");
    expect(categoryForCode("QUESTION_COUNT_MISMATCH")).toBe("Questions");
    expect(categoryForCode("SECTION_COUNT_MISMATCH")).toBe("Sections");
    expect(categoryForCode("TOTAL_MARKS_MISMATCH")).toBe("Scoring");
    expect(categoryForCode("DURATION_INVALID")).toBe("Timing");
    expect(categoryForCode("ENGINE_CONFIGURATION_INVALID")).toBe("Engine");
    expect(categoryForCode("DELIVERY_POLICY_NOT_FOUND")).toBe("Engine");
    expect(categoryForCode("PUBLICATION_BLOCKED")).toBe("Publication");
    expect(categoryForCode("SOMETHING_NEW")).toBe("Other");
  });

  it("groups issues with counts and keeps unknown codes in Other", () => {
    const groups = groupValidationIssues({
      valid: false,
      errors: [
        { code: "QUESTION_COUNT_MISMATCH", message: "requires 100 but 98 configured" },
        { code: "QUESTION_VERSION_NOT_PUBLISHED", message: "Question version x is not published" },
        { code: "SECTION_COUNT_MISMATCH", message: "Section A requires 25 but has 24" },
        { code: "BLUEPRINT_INVALID", message: "The blueprint configuration is invalid" },
        { code: "TOTAL_MARKS_MISMATCH", message: "marks do not match" },
        { code: "BRAND_NEW_RULE", message: "an issue the UI has never seen" },
      ],
    });

    const byCategory = new Map(groups.map((group) => [group.category, group]));

    expect(byCategory.get("Questions")?.count).toBe(2);
    expect(byCategory.get("Sections")?.count).toBe(1);
    expect(byCategory.get("Blueprint")?.count).toBe(1);
    expect(byCategory.get("Scoring")?.count).toBe(1);
    expect(byCategory.get("Other")?.count).toBe(1);
    expect(byCategory.get("Other")?.issues[0]?.code).toBe("BRAND_NEW_RULE");

    // Empty categories are omitted rather than rendered as "0 issues".
    expect(byCategory.has("Timing")).toBe(false);
    expect(byCategory.has("Engine")).toBe(false);

    // Order follows the frozen display order.
    expect(groups.map((group) => group.category)).toEqual([
      "Blueprint",
      "Questions",
      "Sections",
      "Scoring",
      "Other",
    ]);
  });

  it("reports a valid result as zero issues", () => {
    const groups = groupValidationIssues({ valid: true, errors: [] });
    expect(groups).toEqual([]);
  });

  it("produces a readable count summary", () => {
    const [group] = groupValidationIssues({
      valid: false,
      errors: [{ code: "QUESTION_COUNT_MISMATCH", message: "x" }],
    });
    expect(group).toBeDefined();
    expect(summarizeGroup(group!)).toBe("1 question issue");
  });
});
