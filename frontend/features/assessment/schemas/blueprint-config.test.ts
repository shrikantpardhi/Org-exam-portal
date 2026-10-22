import { describe, expect, it } from "vitest";

import {
  DEFAULT_BLUEPRINT_FORM,
  fromBlueprintConfiguration,
  normalizeSectionOrders,
  toBlueprintConfiguration,
  type BlueprintFormValues,
  type BlueprintSectionFormValue,
} from "@/features/assessment/schemas/blueprint-config";

/**
 * Blueprint configuration round-trip ({@code specs/13-P4-Assessment-V1.5.md §9, §12, §19-§21}).
 *
 * <p>The backend rejects unknown properties, so the exact key set matters as much as the values:
 * a leaked form-only key would make the saved configuration unparseable.
 */
function formWithSections(): BlueprintFormValues {
  return {
    ...DEFAULT_BLUEPRINT_FORM,
    sections: [
      {
        key: "row-a",
        name: "Reasoning",
        displayOrder: 1,
        questionCount: 25,
        marks: 50,
        durationMinutes: 20,
      },
      {
        key: "row-b",
        name: "Quantitative Aptitude",
        displayOrder: 2,
        questionCount: 35,
        marks: 70,
        durationMinutes: 40,
      },
    ],
    language: { languageIds: ["lang-1", "lang-2"], defaultLanguageId: "lang-1" },
  };
}

describe("blueprint configuration", () => {
  it("serializes exactly the frozen shape with no extra keys", () => {
    const configuration = toBlueprintConfiguration(formWithSections());

    expect(configuration).toEqual({
      sections: [
        { name: "Reasoning", displayOrder: 1, questionCount: 25, marks: 50, durationMinutes: 20 },
        {
          name: "Quantitative Aptitude",
          displayOrder: 2,
          questionCount: 35,
          marks: 70,
          durationMinutes: 40,
        },
      ],
      navigation: { allowSectionSwitch: true, mode: "SEQUENTIAL" },
      timer: { mode: "GLOBAL", serverAuthoritative: true, autoSubmitOnExpiry: true },
      submission: { allowManualSubmit: true, autoSubmitOnExpiry: true, requireConfirmation: true },
      validation: { responseRequiredBeforeNext: false, sectionCompletionRequired: false },
      presentation: { palettePosition: "RIGHT", showProgress: true },
      calculator: { enabled: false },
      language: { languageIds: ["lang-1", "lang-2"], defaultLanguageId: "lang-1" },
    });

    // The React list key must never reach the payload.
    expect(configuration.sections[0]).not.toHaveProperty("key");
    expect(Object.keys(configuration).sort()).toEqual([
      "calculator",
      "language",
      "navigation",
      "presentation",
      "sections",
      "submission",
      "timer",
      "validation",
    ]);
  });

  it("round-trips a loaded configuration back into form state without loss", () => {
    const form = formWithSections();
    const configuration = toBlueprintConfiguration(form);
    const reloaded = fromBlueprintConfiguration(configuration);

    // Compare the persisted fields; the form-only `key` is regenerated on load.
    const persisted = (section: BlueprintSectionFormValue) => ({
      name: section.name,
      displayOrder: section.displayOrder,
      questionCount: section.questionCount,
      marks: section.marks,
      durationMinutes: section.durationMinutes,
    });

    expect(reloaded.sections.map(persisted)).toEqual(form.sections.map(persisted));
    expect(reloaded.navigation).toEqual(form.navigation);
    expect(reloaded.timer).toEqual(form.timer);
    expect(reloaded.submission).toEqual(form.submission);
    expect(reloaded.presentation).toEqual(form.presentation);
    expect(reloaded.language).toEqual(form.language);

    // A second pass must serialize identically, proving the mapping is stable.
    expect(toBlueprintConfiguration(reloaded)).toEqual(configuration);
  });

  it("normalizes section order to an ascending sequence after reordering", () => {
    const sections = formWithSections().sections;
    // Simulate "move the second row first" then renormalize.
    const reordered = normalizeSectionOrders([sections[1]!, sections[0]!]);

    expect(reordered.map((section) => section.displayOrder)).toEqual([1, 2]);
    expect(reordered.map((section) => section.name)).toEqual(["Quantitative Aptitude", "Reasoning"]);
  });

  it("preserves names that merely look like an exam name", () => {
    const values: BlueprintFormValues = {
      ...DEFAULT_BLUEPRINT_FORM,
      sections: [
        { key: "a", name: "IBPS", displayOrder: 1, questionCount: 5, marks: 5, durationMinutes: 5 },
        { key: "b", name: "SSC CGL Reasoning", displayOrder: 2, questionCount: 5, marks: 5, durationMinutes: 5 },
      ],
      language: { languageIds: ["l1"], defaultLanguageId: "l1" },
    };

    const configuration = toBlueprintConfiguration(values);

    expect(configuration.sections.map((section) => section.name)).toEqual(["IBPS", "SSC CGL Reasoning"]);
    expect(configuration.navigation.mode).toBe("SEQUENTIAL");
  });
});
