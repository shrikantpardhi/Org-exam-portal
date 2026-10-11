import { describe, expect, it } from "vitest";

import {
  attemptQuestionSchema,
  deliveryConfigurationSchema,
} from "@/features/delivery/schemas/delivery";

const CONFIGURATION = {
  capabilities: {
    practice: false,
    allowClearResponse: true,
    allowMarkForReview: true,
    allowExplanation: false,
    allowHint: false,
  },
  navigation: {
    mode: "FREE",
    allowBackNavigation: true,
    allowSectionSwitch: true,
    allowQuestionReview: true,
  },
  timer: { mode: "GLOBAL", durationSeconds: 3600, enforced: true },
  submission: { autoSubmitOnExpiry: false, allowManualSubmit: true },
  validation: { requireResponseWithinTemplate: true, maxResponseLength: 10000, autoSaveIntervalSeconds: 10 },
  presentation: {
    showPalette: true,
    showTimer: true,
    requiresLatexParsing: true,
    contentFormat: "MARKDOWN_LATEX_V1",
    sectionOrder: ["General"],
  },
};

describe("delivery configuration schema", () => {
  it("validates the normalized configuration", () => {
    expect(deliveryConfigurationSchema.safeParse(CONFIGURATION).success).toBe(true);
  });

  it("rejects unsupported or executable configuration keys", () => {
    const hostile = { ...CONFIGURATION, renderScript: "alert(1)" };
    expect(deliveryConfigurationSchema.safeParse(hostile).success).toBe(false);
  });

  it("rejects a content format other than MARKDOWN_LATEX_V1", () => {
    const wrong = { ...CONFIGURATION, presentation: { ...CONFIGURATION.presentation, contentFormat: "HTML" } };
    expect(deliveryConfigurationSchema.safeParse(wrong).success).toBe(false);
  });
});

describe("student question payload", () => {
  const question = {
    attemptQuestionId: "aq1",
    questionVersionId: "qv1",
    displayOrder: 1,
    sectionName: "General",
    marks: 4,
    negativeMarks: 1,
    requiresLatexParsing: true,
    contentFormat: "MARKDOWN_LATEX_V1",
    content: "Solve \\(x^2 = 4\\).",
    renderedContent: "<p>Solve…</p>",
    options: [{ optionId: "o1", optionKey: "A", displayOrder: 1, content: "1", renderedContent: "1" }],
  };

  it("parses an authorized option payload", () => {
    expect(attemptQuestionSchema.safeParse(question).success).toBe(true);
  });

  it("rejects a payload that leaks the correct answer", () => {
    const leaked = {
      ...question,
      options: [{ ...question.options[0], isCorrect: true }],
    };
    expect(attemptQuestionSchema.safeParse(leaked).success).toBe(false);
  });
});
