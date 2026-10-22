/**
 * Authoring payload assembly ({@code specs/12-P3-Content-V1.5.md §88, §93, §102, §103}).
 *
 * <p>The editor keeps a wider form state than any single endpoint accepts. These pure functions
 * project that state onto the exact frozen request bodies — no extra keys, optional keys omitted
 * instead of sent as {@code null} — so the request payload is a single, testable decision rather
 * than something scattered through JSX.
 */
import {
  createQuestionPayloadSchema,
  optionRequestSchema,
  previousExamRequestSchema,
  previousExamUpdateRequestSchema,
  versionCreateRequestSchema,
  type CreateQuestionPayload,
  type Difficulty,
  type OptionRequest,
  type PreviousExamRequest,
  type PreviousExamUpdateRequest,
  type VersionCreateRequest,
} from "@/features/content/schemas/content";

/** The full editor state for the core fields ({@code §102}). All values are form strings. */
export interface QuestionEditorValues {
  templateId: string;
  examId: string;
  subjectId: string;
  chapterId?: string;
  topicId?: string;
  subTopicId?: string;
  languageId: string;
  title?: string;
  content: string;
  marks: string;
  negativeMarks?: string;
  difficulty: string;
}

function optionalText(value: string | undefined): string | undefined {
  if (value === undefined) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

/**
 * Drops keys whose value is {@code undefined}.
 *
 * <p>Zod keeps a declared key even when it is absent, so the parsed object would carry
 * {@code title: undefined}. Sending that key is harmless over JSON but makes the payload
 * ambiguous to read and to assert, so absent optional fields are removed entirely.
 */
function compact<T extends Record<string, unknown>>(value: T): T {
  const entries = Object.entries(value).filter(([, entry]) => entry !== undefined);
  return Object.fromEntries(entries) as T;
}

function optionalNumber(value: string | undefined): number | undefined {
  const text = optionalText(value);
  if (text === undefined) {
    return undefined;
  }
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : undefined;
}

/**
 * Assembles the exact {@code POST /questions} body.
 *
 * <p>Required taxonomy and scoring fields are always present; optional taxonomy is included only
 * when the author chose it. Anything the editor holds for its own use (tags, options, assets,
 * explanation, hint, previous exams) is deliberately not part of this payload — those have their
 * own frozen endpoints. Throws when the projection is not a valid create request.
 */
export function buildCreateQuestionPayload(values: QuestionEditorValues): CreateQuestionPayload {
  return compact(
    createQuestionPayloadSchema.parse({
      templateId: values.templateId,
      examId: values.examId,
      subjectId: values.subjectId,
      chapterId: optionalText(values.chapterId),
      topicId: optionalText(values.topicId),
      subTopicId: optionalText(values.subTopicId),
      title: optionalText(values.title),
      content: values.content,
      marks: Number(values.marks),
      negativeMarks: optionalNumber(values.negativeMarks),
      difficulty: values.difficulty as Difficulty,
      languageId: values.languageId,
    }),
  );
}

/** Assembles the exact {@code POST /questions/{id}/versions} body ({@code §90}). */
export function buildVersionCreateRequest(values: QuestionEditorValues): VersionCreateRequest {
  return compact(
    versionCreateRequestSchema.parse({
      title: optionalText(values.title),
      content: values.content,
      marks: Number(values.marks),
      negativeMarks: optionalNumber(values.negativeMarks),
      difficulty: values.difficulty as Difficulty,
      languageId: values.languageId,
    }),
  );
}

/** Draft-level metadata body: taxonomy plus tags, never versioned content ({@code §89, §109}). */
export function buildMetadataUpdate(
  values: Pick<QuestionEditorValues, "examId" | "subjectId" | "chapterId" | "topicId" | "subTopicId">,
  tagIds: string[],
): { examId?: string; subjectId?: string; chapterId?: string; topicId?: string; subTopicId?: string; tagIds: string[] } {
  const payload: {
    examId?: string;
    subjectId?: string;
    chapterId?: string;
    topicId?: string;
    subTopicId?: string;
    tagIds: string[];
  } = { tagIds };
  const examId = optionalText(values.examId);
  if (examId) payload.examId = examId;
  const subjectId = optionalText(values.subjectId);
  if (subjectId) payload.subjectId = subjectId;
  const chapterId = optionalText(values.chapterId);
  if (chapterId) payload.chapterId = chapterId;
  const topicId = optionalText(values.topicId);
  if (topicId) payload.topicId = topicId;
  const subTopicId = optionalText(values.subTopicId);
  if (subTopicId) payload.subTopicId = subTopicId;
  return payload;
}

/** Authoring option rows. */
export interface OptionRow {
  optionKey: string;
  content: string;
  displayOrder: number;
  isCorrect: boolean;
}

/**
 * Builds the option payloads in the editor's row order, renumbering {@code displayOrder} from zero
 * so the backend's {@code UNIQUE(question_version_id, display_order)} can never be violated by the
 * UI. Row order is the author's display order; reordering the rows reorders the payload.
 */
export function buildOptionPayloads(rows: OptionRow[]): OptionRequest[] {
  return rows.map((row, index) =>
    optionRequestSchema.parse({
      optionKey: row.optionKey.trim(),
      content: row.content,
      displayOrder: index,
      isCorrect: row.isCorrect,
    }),
  );
}

/** Marks one option correct and clears every other one (single-correct templates, {@code §32}). */
export function selectSingleCorrect(rows: OptionRow[], optionKey: string): OptionRow[] {
  return rows.map((row) => ({ ...row, isCorrect: row.optionKey === optionKey }));
}

/** Toggles an option's correctness without touching its siblings (multi-correct, {@code §33}). */
export function toggleCorrect(rows: OptionRow[], optionKey: string): OptionRow[] {
  return rows.map((row) => (row.optionKey === optionKey ? { ...row, isCorrect: !row.isCorrect } : row));
}

/**
 * Client-side plausibility check for an option set.
 *
 * <p>Returns human-readable problems only; the backend template registry remains authoritative
 * for how many options and how many correct answers a given template requires ({@code §103}).
 */
export function optionProblems(rows: OptionRow[], answerModel: "SINGLE_OPTION" | "MULTIPLE_OPTIONS"): string[] {
  const problems: string[] = [];
  if (rows.length < 2) {
    problems.push("At least two options are required.");
  }
  const keys = rows.map((row) => row.optionKey.trim());
  if (new Set(keys).size !== keys.length) {
    problems.push("Option keys must be unique.");
  }
  if (rows.some((row) => row.optionKey.trim().length === 0)) {
    problems.push("Every option needs a key.");
  }
  const correct = rows.filter((row) => row.isCorrect).length;
  if (answerModel === "SINGLE_OPTION" && correct !== 1) {
    problems.push("Single-choice questions need exactly one correct option.");
  }
  if (answerModel === "MULTIPLE_OPTIONS" && correct < 1) {
    problems.push("At least one option must be marked correct.");
  }
  return problems;
}

/**
 * Builds the previous-exam update body ({@code PUT .../previous-exams/{historyId}}).
 *
 * <p>The frozen update contract accepts year, shift and source only — the exam association is
 * deliberately not reassignable — so {@code examId} is never sent here.
 */
export function buildPreviousExamUpdateRequest(values: {
  year?: string;
  shift?: string;
  source?: string;
}): PreviousExamUpdateRequest {
  const year = optionalNumber(values.year);
  const shift = optionalText(values.shift);
  const source = optionalText(values.source);
  return previousExamUpdateRequestSchema.parse({
    ...(year !== undefined ? { year } : {}),
    ...(shift !== undefined ? { shift } : {}),
    ...(source !== undefined ? { source } : {}),
  });
}

/**
 * Builds the previous-exam create body from the editor row.
 *
 * <p>{@code examId} is always the id chosen from the Academic Exam masters; no typed exam name is
 * ever accepted, because the frozen contract expects {@code exam_id} ({@code §108}).
 */
export function buildPreviousExamRequest(values: {
  examId: string;
  year?: string;
  shift?: string;
  source?: string;
}): PreviousExamRequest {
  const year = optionalNumber(values.year);
  const shift = optionalText(values.shift);
  const source = optionalText(values.source);
  return previousExamRequestSchema.parse({
    examId: values.examId,
    ...(year !== undefined ? { year } : {}),
    ...(shift !== undefined ? { shift } : {}),
    ...(source !== undefined ? { source } : {}),
  });
}

