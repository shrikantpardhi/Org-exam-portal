/**
 * The frozen Blueprint `configuration` contract
 * ({@code specs/13-P4-Assessment-V1.5.md §9, §12, §19-§21}).
 *
 * <p>The backend deserializes this JSON into a typed record and rejects unknown properties, so every
 * nested key below is the complete allowed set. {@link toBlueprintConfiguration} therefore builds the
 * object key-by-key rather than spreading form state, and {@link fromBlueprintConfiguration} is the
 * exact inverse. Section names are free-text user labels (§13) — no catalog is hard-coded.
 */
import { z } from "zod";

const numberish = z.union([z.number(), z.string()]).transform((value) => Number(value));

export const navigationModeSchema = z.enum(["FREE", "SEQUENTIAL", "SECTION_SEQUENTIAL", "CUSTOM"]);
export const timerModeSchema = z.enum(["GLOBAL", "SECTION"]);
export const palettePositionSchema = z.enum(["RIGHT", "LEFT"]);

export type NavigationMode = z.infer<typeof navigationModeSchema>;
export type TimerMode = z.infer<typeof timerModeSchema>;
export type PalettePosition = z.infer<typeof palettePositionSchema>;

export const blueprintSectionConfigSchema = z
  .object({
    name: z.string().min(1),
    displayOrder: numberish,
    questionCount: numberish,
    marks: numberish,
    durationMinutes: numberish,
  })
  .strict();

export const blueprintConfigurationSchema = z
  .object({
    sections: z.array(blueprintSectionConfigSchema),
    navigation: z
      .object({
        allowSectionSwitch: z.boolean(),
        mode: z.string(),
      })
      .strict(),
    timer: z
      .object({
        mode: z.string(),
        serverAuthoritative: z.boolean(),
        autoSubmitOnExpiry: z.boolean(),
      })
      .strict(),
    submission: z
      .object({
        allowManualSubmit: z.boolean(),
        autoSubmitOnExpiry: z.boolean(),
        requireConfirmation: z.boolean(),
      })
      .strict(),
    validation: z
      .object({
        responseRequiredBeforeNext: z.boolean(),
        sectionCompletionRequired: z.boolean(),
      })
      .strict(),
    presentation: z
      .object({
        palettePosition: z.string(),
        showProgress: z.boolean(),
      })
      .strict(),
    calculator: z.object({ enabled: z.boolean() }).strict(),
    language: z
      .object({
        languageIds: z.array(z.string()),
        defaultLanguageId: z.string(),
      })
      .strict(),
  })
  .strict();

export type BlueprintConfiguration = z.infer<typeof blueprintConfigurationSchema>;

/** One editable section row. `key` is a stable React list key and is never serialized. */
export interface BlueprintSectionFormValue {
  key: string;
  name: string;
  displayOrder: number;
  questionCount: number;
  marks: number;
  durationMinutes: number;
}

/** The full editable form state of a blueprint version. */
export interface BlueprintFormValues {
  sections: BlueprintSectionFormValue[];
  navigation: { mode: NavigationMode; allowSectionSwitch: boolean };
  timer: { mode: TimerMode; serverAuthoritative: boolean; autoSubmitOnExpiry: boolean };
  submission: { allowManualSubmit: boolean; autoSubmitOnExpiry: boolean; requireConfirmation: boolean };
  validation: { responseRequiredBeforeNext: boolean; sectionCompletionRequired: boolean };
  presentation: { palettePosition: PalettePosition; showProgress: boolean };
  calculator: { enabled: boolean };
  language: { languageIds: string[]; defaultLanguageId: string };
}

const navigationModes: NavigationMode[] = ["FREE", "SEQUENTIAL", "SECTION_SEQUENTIAL", "CUSTOM"];
const timerModes: TimerMode[] = ["GLOBAL", "SECTION"];
const palettePositions: PalettePosition[] = ["RIGHT", "LEFT"];

/** Selectable option lists derived from the frozen contracts, never from an exam name. */
export const NAVIGATION_MODE_OPTIONS = navigationModes;
export const TIMER_MODE_OPTIONS = timerModes;
export const PALETTE_POSITION_OPTIONS = palettePositions;

/** Server-authoritative timer is not negotiable at runtime ({@code §18, 14-P5 §54}). */
export const DEFAULT_BLUEPRINT_FORM: BlueprintFormValues = {
  sections: [],
  navigation: { mode: "SEQUENTIAL", allowSectionSwitch: true },
  timer: { mode: "GLOBAL", serverAuthoritative: true, autoSubmitOnExpiry: true },
  submission: { allowManualSubmit: true, autoSubmitOnExpiry: true, requireConfirmation: true },
  validation: { responseRequiredBeforeNext: false, sectionCompletionRequired: false },
  presentation: { palettePosition: "RIGHT", showProgress: true },
  calculator: { enabled: false },
  language: { languageIds: [], defaultLanguageId: "" },
};

let sectionKeySeed = 0;

/** A fresh, stable key for a section row. */
export function newSectionKey(): string {
  sectionKeySeed += 1;
  return `section-${sectionKeySeed}`;
}

/** Builds a new empty section row at the next ascending display order. */
export function newSectionRow(displayOrder: number): BlueprintSectionFormValue {
  return {
    key: newSectionKey(),
    name: "",
    displayOrder,
    questionCount: 0,
    marks: 0,
    durationMinutes: 0,
  };
}

/**
 * Rewrites `displayOrder` to 1..n while preserving row order ({@code §14} permits gaps, but the
 * editor emits an ascending sequence so the saved configuration is always unambiguous).
 */
export function normalizeSectionOrders(
  sections: readonly BlueprintSectionFormValue[],
): BlueprintSectionFormValue[] {
  return sections.map((section, index) => ({ ...section, displayOrder: index + 1 }));
}

/**
 * Produces exactly the frozen JSON shape. Every key is written explicitly so no form-only key
 * (for example `key`) can leak into the payload.
 */
export function toBlueprintConfiguration(values: BlueprintFormValues): BlueprintConfiguration {
  return {
    sections: values.sections.map((section) => ({
      name: section.name,
      displayOrder: section.displayOrder,
      questionCount: section.questionCount,
      marks: section.marks,
      durationMinutes: section.durationMinutes,
    })),
    navigation: {
      allowSectionSwitch: values.navigation.allowSectionSwitch,
      mode: values.navigation.mode,
    },
    timer: {
      mode: values.timer.mode,
      serverAuthoritative: values.timer.serverAuthoritative,
      autoSubmitOnExpiry: values.timer.autoSubmitOnExpiry,
    },
    submission: {
      allowManualSubmit: values.submission.allowManualSubmit,
      autoSubmitOnExpiry: values.submission.autoSubmitOnExpiry,
      requireConfirmation: values.submission.requireConfirmation,
    },
    validation: {
      responseRequiredBeforeNext: values.validation.responseRequiredBeforeNext,
      sectionCompletionRequired: values.validation.sectionCompletionRequired,
    },
    presentation: {
      palettePosition: values.presentation.palettePosition,
      showProgress: values.presentation.showProgress,
    },
    calculator: { enabled: values.calculator.enabled },
    language: {
      languageIds: [...values.language.languageIds],
      defaultLanguageId: values.language.defaultLanguageId,
    },
  };
}

/** Maps a loaded configuration back into editable form state, preserving section order. */
export function fromBlueprintConfiguration(configuration: BlueprintConfiguration): BlueprintFormValues {
  return {
    sections: configuration.sections.map((section) => ({
      key: newSectionKey(),
      name: section.name,
      displayOrder: section.displayOrder,
      questionCount: section.questionCount,
      marks: section.marks,
      durationMinutes: section.durationMinutes,
    })),
    navigation: {
      mode: asNavigationMode(configuration.navigation.mode),
      allowSectionSwitch: configuration.navigation.allowSectionSwitch,
    },
    timer: {
      mode: asTimerMode(configuration.timer.mode),
      serverAuthoritative: configuration.timer.serverAuthoritative,
      autoSubmitOnExpiry: configuration.timer.autoSubmitOnExpiry,
    },
    submission: {
      allowManualSubmit: configuration.submission.allowManualSubmit,
      autoSubmitOnExpiry: configuration.submission.autoSubmitOnExpiry,
      requireConfirmation: configuration.submission.requireConfirmation,
    },
    validation: {
      responseRequiredBeforeNext: configuration.validation.responseRequiredBeforeNext,
      sectionCompletionRequired: configuration.validation.sectionCompletionRequired,
    },
    presentation: {
      palettePosition: asPalettePosition(configuration.presentation.palettePosition),
      showProgress: configuration.presentation.showProgress,
    },
    calculator: { enabled: configuration.calculator.enabled },
    language: {
      languageIds: [...configuration.language.languageIds],
      defaultLanguageId: configuration.language.defaultLanguageId,
    },
  };
}

function asNavigationMode(value: string): NavigationMode {
  return navigationModes.find((mode) => mode === value) ?? "SEQUENTIAL";
}

function asTimerMode(value: string): TimerMode {
  return timerModes.find((mode) => mode === value) ?? "GLOBAL";
}

function asPalettePosition(value: string): PalettePosition {
  return palettePositions.find((position) => position === value) ?? "RIGHT";
}

export interface BlueprintCompleteness {
  valid: boolean;
  missing: string[];
}

/**
 * Client-side completeness view for the builder summary ({@code §99}). Publication is still decided
 * by the server; this only explains what is obviously missing before the request is made.
 */
export function describeBlueprintGaps(values: BlueprintFormValues): BlueprintCompleteness {
  const missing: string[] = [];

  if (values.sections.length === 0) {
    missing.push("At least one section is required.");
  }

  const names = new Set<string>();
  for (const section of values.sections) {
    if (section.name.trim().length === 0) {
      missing.push("Every section needs a name.");
      continue;
    }
    if (names.has(section.name.trim())) {
      missing.push(`Section name "${section.name.trim()}" is used more than once.`);
    }
    names.add(section.name.trim());
    if (section.questionCount <= 0) {
      missing.push(`Section "${section.name}" needs a positive question count.`);
    }
    if (section.displayOrder <= 0) {
      missing.push(`Section "${section.name}" needs a positive display order.`);
    }
    if (section.marks < 0) {
      missing.push(`Section "${section.name}" cannot have negative marks.`);
    }
    if (section.durationMinutes < 0) {
      missing.push(`Section "${section.name}" cannot have a negative duration.`);
    }
  }

  if (values.language.languageIds.length === 0) {
    missing.push("Select at least one language.");
  }
  if (values.language.defaultLanguageId.trim().length === 0) {
    missing.push("Choose the default language.");
  } else if (!values.language.languageIds.includes(values.language.defaultLanguageId)) {
    missing.push("The default language must be one of the selected languages.");
  }

  return { valid: missing.length === 0, missing };
}
