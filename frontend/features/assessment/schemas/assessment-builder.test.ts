import { describe, expect, it } from "vitest";

import {
  EMPTY_RULE_FORM,
  fromRuleConfiguration,
  isRuleScoped,
  toRuleConfiguration,
  type RuleConfigurationForm,
} from "@/features/assessment/schemas/assessment-builder";

/**
 * Selection rule configuration ({@code specs/13-P4-Assessment-V1.5.md §45, §102}).
 *
 * <p>Blank filters are omitted rather than sent as empty strings, and the configuration uses only the
 * dimensions the P3 content contract exposes.
 */
describe("selection rule configuration", () => {
  it("omits blank dimensions so the server sees only real filters", () => {
    const form: RuleConfigurationForm = {
      ...EMPTY_RULE_FORM,
      difficulty: "HARD",
      examId: "exam-1",
      publishedOnly: true,
    };

    const configuration = toRuleConfiguration(form);

    expect(configuration).toEqual({
      difficulty: ["HARD"],
      examId: "exam-1",
      publishedOnly: true,
    });
    expect(configuration).not.toHaveProperty("subjectId");
    expect(configuration).not.toHaveProperty("tagIds");
    expect(configuration).not.toHaveProperty("languageId");
  });

  it("uses the plural tagIds list the server reads, never a singular tagId", () => {
    const configuration = toRuleConfiguration({ ...EMPTY_RULE_FORM, tagIds: ["tag-1"] });

    // QuestionSelectionService.criteria reads `tagIds` as a list (§45); `tagId` would be ignored.
    expect(configuration).toHaveProperty("tagIds", ["tag-1"]);
    expect(configuration).not.toHaveProperty("tagId");
  });

  it("omits tagIds entirely when no tag is selected", () => {
    const configuration = toRuleConfiguration(EMPTY_RULE_FORM);
    expect(configuration).not.toHaveProperty("tagIds");
  });

  it("sends the template code, because the server filters on templateCode not templateId", () => {
    const configuration = toRuleConfiguration({ ...EMPTY_RULE_FORM, templateCode: "MCQ_SINGLE" });

    expect(configuration).toHaveProperty("templateCode", "MCQ_SINGLE");
    expect(configuration).not.toHaveProperty("templateId");
  });

  it("only treats a rule as scoped when a real filter is set", () => {
    expect(isRuleScoped(EMPTY_RULE_FORM)).toBe(false);
    expect(isRuleScoped({ ...EMPTY_RULE_FORM, publishedOnly: true })).toBe(false);
    expect(isRuleScoped({ ...EMPTY_RULE_FORM, subjectId: "s-1" })).toBe(true);
    expect(isRuleScoped({ ...EMPTY_RULE_FORM, topicId: "t-1" })).toBe(true);
    expect(isRuleScoped({ ...EMPTY_RULE_FORM, tagIds: ["tag-1"] })).toBe(true);
    expect(isRuleScoped({ ...EMPTY_RULE_FORM, examId: "e-1" })).toBe(true);
  });

  it("reads a stored configuration back into the form", () => {
    const form = fromRuleConfiguration({
      difficulty: ["MEDIUM"],
      examId: "exam-1",
      templateCode: "MCQ_SINGLE",
      tagIds: ["tag-1", "tag-2"],
      publishedOnly: true,
    });

    expect(form.difficulty).toBe("MEDIUM");
    expect(form.examId).toBe("exam-1");
    expect(form.templateCode).toBe("MCQ_SINGLE");
    expect(form.tagIds).toEqual(["tag-1", "tag-2"]);
    expect(form.publishedOnly).toBe(true);
    expect(form.subjectId).toBe("");
  });

  it("round-trips a full configuration", () => {
    const form: RuleConfigurationForm = {
      difficulty: "EASY",
      templateCode: "MCQ_SINGLE",
      tagIds: ["tag-1"],
      examId: "exam-1",
      subjectId: "sub-1",
      chapterId: "chap-1",
      topicId: "topic-1",
      languageId: "lang-1",
      publishedOnly: true,
    };

    const configuration = toRuleConfiguration(form);
    expect(fromRuleConfiguration(configuration)).toEqual(form);
  });
});
