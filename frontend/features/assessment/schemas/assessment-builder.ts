/**
 * Assessment authoring schemas — blueprint versions, questions, selection rules, preview,
 * schedules, and collections ({@code specs/13-P4-Assessment-V1.5.md §63, §87, §107, §109, §110}).
 *
 * <p>Every response is validated at the boundary. Amounts are parsed through a number-or-string
 * union because the backend serializes {@code BigDecimal} either way.
 */
import { z } from "zod";

import { blueprintConfigurationSchema } from "@/features/assessment/schemas/blueprint-config";

const numberish = z.union([z.number(), z.string()]).transform((value) => Number(value));

export const statusSchema = z.enum(["ACTIVE", "INACTIVE", "ARCHIVED"]);
export const difficultySchema = z.enum(["EASY", "MEDIUM", "HARD"]);
export const scheduleModeSchema = z.enum(["PRACTICE", "EXAM"]);
/**
 * The materialize mode accepted by the server ({@code 13-P4 §63}).
 *
 * <p>The frozen contract defines exactly one mode, {@code REPLACE_UNMATERIALIZED}: rules that have
 * never been materialized are filled in and already-materialized rules are skipped, which makes the
 * operation idempotent (§48). The server rejects every other value, so the UI must not offer a choice
 * it cannot honour — an earlier draft of this screen offered MANUAL/RULE/HYBRID, which the server
 * answers with {@code VALIDATION_ERROR}.
 */
export const materializeModeSchema = z.literal("REPLACE_UNMATERIALIZED");

export type MaterializeMode = z.infer<typeof materializeModeSchema>;

/** The single mode the UI sends. Exported so tests and callers cannot drift from the contract. */
export const MATERIALIZE_MODE: MaterializeMode = "REPLACE_UNMATERIALIZED";

export type Difficulty = z.infer<typeof difficultySchema>;
export type ScheduleMode = z.infer<typeof scheduleModeSchema>;

// ----- Blueprint -----

export const blueprintVersionSummarySchema = z.object({
  id: z.string(),
  version: z.number(),
  published: z.boolean(),
  publishedOn: z.string().nullish(),
});
export type BlueprintVersionSummary = z.infer<typeof blueprintVersionSummarySchema>;

export const blueprintResponseSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  status: z.string(),
  versions: z.array(blueprintVersionSummarySchema),
});
export type BlueprintResponse = z.infer<typeof blueprintResponseSchema>;

/**
 * A stored configuration may be an incomplete draft, so groups absent from an older row fall back to
 * the frozen defaults on read. Writes always emit the complete shape.
 */
const readConfigurationSchema = blueprintConfigurationSchema.extend({
  navigation: blueprintConfigurationSchema.shape.navigation.default({
    allowSectionSwitch: true,
    mode: "SEQUENTIAL",
  }),
  timer: blueprintConfigurationSchema.shape.timer.default({
    mode: "GLOBAL",
    serverAuthoritative: true,
    autoSubmitOnExpiry: true,
  }),
  submission: blueprintConfigurationSchema.shape.submission.default({
    allowManualSubmit: true,
    autoSubmitOnExpiry: true,
    requireConfirmation: true,
  }),
  validation: blueprintConfigurationSchema.shape.validation.default({
    responseRequiredBeforeNext: false,
    sectionCompletionRequired: false,
  }),
  presentation: blueprintConfigurationSchema.shape.presentation.default({
    palettePosition: "RIGHT",
    showProgress: true,
  }),
  calculator: blueprintConfigurationSchema.shape.calculator.default({ enabled: false }),
  language: blueprintConfigurationSchema.shape.language.default({
    languageIds: [],
    defaultLanguageId: "",
  }),
});

export const blueprintVersionResponseSchema = z.object({
  id: z.string(),
  blueprintId: z.string(),
  version: z.number(),
  configuration: readConfigurationSchema,
  published: z.boolean(),
  publishedBy: z.string().nullish(),
  publishedOn: z.string().nullish(),
  status: z.string(),
});
export type BlueprintVersionResponse = z.infer<typeof blueprintVersionResponseSchema>;

// ----- Assessment version operations -----

export const assessmentQuestionSchema = z.object({
  id: z.string(),
  assessmentVersionId: z.string(),
  questionVersionId: z.string(),
  displayOrder: z.number(),
  sectionName: z.string().nullish(),
  marks: numberish,
  negativeMarks: numberish,
});
export type AssessmentQuestion = z.infer<typeof assessmentQuestionSchema>;

export const selectionRuleSchema = z.object({
  id: z.string(),
  assessmentVersionId: z.string(),
  sectionName: z.string(),
  targetCount: z.number(),
  displayOrder: z.number(),
  materializedOn: z.string().nullish(),
  materializedQuestionCount: z.number().nullish(),
  ruleConfiguration: z.unknown(),
});
export type SelectionRule = z.infer<typeof selectionRuleSchema>;

export const previewQuestionSchema = z.object({
  assessmentQuestionId: z.string(),
  questionVersionId: z.string(),
  displayOrder: z.number(),
  marks: numberish,
  negativeMarks: numberish,
  contentFormat: z.string().nullish(),
  content: z.string().nullish(),
  renderedContent: z.string().nullish(),
});

export const previewSectionSchema = z.object({
  sectionName: z.string(),
  questions: z.array(previewQuestionSchema),
});

export const previewResponseSchema = z.object({
  assessmentVersionId: z.string(),
  totalQuestions: z.number().nullish(),
  totalMarks: numberish.nullish(),
  durationMinutes: z.number().nullish(),
  engineType: z.string().nullish(),
  sections: z.array(previewSectionSchema),
});
export type PreviewResponse = z.infer<typeof previewResponseSchema>;
export type PreviewSection = z.infer<typeof previewSectionSchema>;

// ----- Question bank (server-side picker) -----

export const questionListItemSchema = z.object({
  id: z.string(),
  templateId: z.string().nullish(),
  examId: z.string().nullish(),
  subjectId: z.string().nullish(),
  difficulty: difficultySchema.nullish(),
  latestVersion: z.number().nullish(),
  latestReviewStatus: z.string().nullish(),
  published: z.boolean(),
  updatedOn: z.string().nullish(),
});
export type QuestionListItem = z.infer<typeof questionListItemSchema>;

export const versionListItemSchema = z.object({
  id: z.string(),
  version: z.number(),
  reviewStatus: z.string().nullish(),
  published: z.boolean(),
  marks: numberish.nullish(),
  negativeMarks: numberish.nullish(),
  difficulty: difficultySchema.nullish(),
  publishedOn: z.string().nullish(),
});
export type VersionListItem = z.infer<typeof versionListItemSchema>;

// ----- Schedule -----

export const scheduleSchema = z.object({
  id: z.string(),
  assessmentVersionId: z.string(),
  mode: scheduleModeSchema,
  startTime: z.string(),
  endTime: z.string(),
  status: z.string(),
});
export type Schedule = z.infer<typeof scheduleSchema>;

// ----- Collection -----

export const collectionSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  status: z.string(),
  assessmentIds: z.array(z.string()),
});
export type Collection = z.infer<typeof collectionSchema>;

/**
 * The rule-configuration keys the server actually reads ({@code 13-P4 §45, §63}).
 *
 * <p>These names are dictated by {@code QuestionSelectionService.criteria}: the server reads
 * {@code templateCode} (the template's code, not its id), {@code tagIds} (a list, not a single id),
 * and {@code difficulty} as a list. Sending {@code templateId}/{@code tagId} would parse cleanly and
 * silently match nothing, so the mapping below is the contract, not a preference.
 */
export const RULE_DIMENSION_KEYS = [
  "difficulty",
  "templateCode",
  "tagIds",
  "examId",
  "subjectId",
  "chapterId",
  "topicId",
  "languageId",
  "publishedOnly",
] as const;

export type RuleDimensionKey = (typeof RULE_DIMENSION_KEYS)[number];

/** Editable rule-filter draft. Blank strings mean "no filter" and are not serialized. */
export interface RuleConfigurationForm {
  difficulty: Difficulty | "";
  /** The template code the server filters on — not the template id. */
  templateCode: string;
  /** One tag id per entry; the server receives these as a list. */
  tagIds: string[];
  examId: string;
  subjectId: string;
  chapterId: string;
  topicId: string;
  languageId: string;
  publishedOnly: boolean;
}

export const EMPTY_RULE_FORM: RuleConfigurationForm = {
  difficulty: "",
  templateCode: "",
  tagIds: [],
  examId: "",
  subjectId: "",
  chapterId: "",
  topicId: "",
  languageId: "",
  publishedOnly: true,
};

/**
 * Builds `ruleConfiguration` from the exposed dimensions only. A blank input is omitted instead of
 * sent as an empty string, because the server parses ids as UUIDs.
 */
export function toRuleConfiguration(form: RuleConfigurationForm): Record<string, unknown> {
  const configuration: Record<string, unknown> = {};
  if (form.difficulty) {
    configuration.difficulty = [form.difficulty];
  }
  if (form.templateCode.trim().length > 0) {
    configuration.templateCode = form.templateCode.trim();
  }
  const tagIds = form.tagIds.map((value) => value.trim()).filter((value) => value.length > 0);
  if (tagIds.length > 0) {
    configuration.tagIds = tagIds;
  }
  for (const key of ["examId", "subjectId", "chapterId", "topicId", "languageId"] as const) {
    const value = form[key].trim();
    if (value.length > 0) {
      configuration[key] = value;
    }
  }
  configuration.publishedOnly = form.publishedOnly;
  return configuration;
}

/** The server requires at least one scoping dimension ({@code §45}); mirrors that rule in the UI. */
export function isRuleScoped(form: RuleConfigurationForm): boolean {
  return (
    form.examId.trim().length > 0 ||
    form.subjectId.trim().length > 0 ||
    form.topicId.trim().length > 0 ||
    form.tagIds.length > 0
  );
}

/** Maps a stored rule configuration back into the editable draft for display. */
export function fromRuleConfiguration(configuration: unknown): RuleConfigurationForm {
  if (typeof configuration !== "object" || configuration === null) {
    return { ...EMPTY_RULE_FORM, tagIds: [] };
  }
  const source = configuration as Record<string, unknown>;
  const difficulties = Array.isArray(source.difficulty) ? source.difficulty : [];
  const first = difficulties.find((value): value is Difficulty => difficultySchema.safeParse(value).success);
  const text = (key: string): string => (typeof source[key] === "string" ? (source[key] as string) : "");
  const tagIds = Array.isArray(source.tagIds)
    ? source.tagIds.filter((value): value is string => typeof value === "string")
    : [];
  return {
    difficulty: first ?? "",
    templateCode: text("templateCode"),
    tagIds,
    examId: text("examId"),
    subjectId: text("subjectId"),
    chapterId: text("chapterId"),
    topicId: text("topicId"),
    languageId: text("languageId"),
    publishedOnly: source.publishedOnly === undefined ? true : source.publishedOnly === true,
  };
}
