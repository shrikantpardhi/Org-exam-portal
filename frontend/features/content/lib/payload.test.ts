import { describe, expect, it } from "vitest";

import {
  assetReference,
  insertAssetReference,
  isAssetReference,
  referencedAssetIds,
} from "@/features/content/lib/asset-reference";
import {
  buildCreateQuestionPayload,
  buildOptionPayloads,
  buildPreviousExamRequest,
  buildPreviousExamUpdateRequest,
  buildVersionCreateRequest,
  optionProblems,
  selectSingleCorrect,
  toggleCorrect,
  type OptionRow,
} from "@/features/content/lib/payload";

const EDITOR = {
  templateId: "tpl-1",
  examId: "exam-1",
  subjectId: "subject-1",
  chapterId: "",
  topicId: "",
  subTopicId: "",
  languageId: "lang-1",
  title: "  Fractions  ",
  content: "What is \\(1/2\\) of 100?",
  marks: "2",
  negativeMarks: "0.5",
  difficulty: "MEDIUM",
};

function rows(): OptionRow[] {
  return [
    { optionKey: "A", content: "25", displayOrder: 0, isCorrect: false },
    { optionKey: "B", content: "50", displayOrder: 1, isCorrect: true },
    { optionKey: "C", content: "75", displayOrder: 2, isCorrect: false },
  ];
}

describe("buildCreateQuestionPayload", () => {
  it("emits exactly the frozen request keys and no client-only fields", () => {
    const payload = buildCreateQuestionPayload(EDITOR);

    expect(Object.keys(payload).sort()).toEqual(
      ["content", "difficulty", "examId", "languageId", "marks", "negativeMarks", "subjectId", "templateId", "title"].sort(),
    );
    expect(payload.templateId).toBe("tpl-1");
    expect(payload.examId).toBe("exam-1");
    expect(payload.subjectId).toBe("subject-1");
    expect(payload.languageId).toBe("lang-1");
    expect(payload.title).toBe("Fractions");
    expect(payload.marks).toBe(2);
    expect(payload.negativeMarks).toBe(0.5);
    expect(payload.difficulty).toBe("MEDIUM");
    expect(payload.content).toContain("\\(1/2\\)");
    expect("chapterId" in payload).toBe(false);
    expect("tagIds" in payload).toBe(false);
    expect("options" in payload).toBe(false);
  });

  it("includes optional taxonomy only when it was chosen", () => {
    const payload = buildCreateQuestionPayload({
      ...EDITOR,
      chapterId: "chapter-1",
      topicId: "topic-1",
      subTopicId: "sub-1",
    });

    expect(payload.chapterId).toBe("chapter-1");
    expect(payload.topicId).toBe("topic-1");
    expect(payload.subTopicId).toBe("sub-1");
  });

  it("omits optional keys rather than sending nulls", () => {
    const payload = buildCreateQuestionPayload({ ...EDITOR, title: "", negativeMarks: "" });

    expect("title" in payload).toBe(false);
    expect("negativeMarks" in payload).toBe(false);
  });

  it("rejects a payload the contract cannot represent", () => {
    expect(() => buildCreateQuestionPayload({ ...EDITOR, templateId: "" })).toThrow();
    expect(() => buildCreateQuestionPayload({ ...EDITOR, marks: "-1" })).toThrow();
    expect(() => buildCreateQuestionPayload({ ...EDITOR, difficulty: "TRIVIAL" })).toThrow();
  });
});

describe("buildVersionCreateRequest", () => {
  it("emits version body keys only, never taxonomy", () => {
    const payload = buildVersionCreateRequest(EDITOR);

    expect(Object.keys(payload).sort()).toEqual(
      ["content", "difficulty", "languageId", "marks", "negativeMarks", "title"].sort(),
    );
    expect("examId" in payload).toBe(false);
    expect("templateId" in payload).toBe(false);
  });
});

describe("option payloads", () => {
  it("renumbers display order contiguously from the row order", () => {
    const payloads = buildOptionPayloads(rows());

    expect(payloads.map((payload) => payload.displayOrder)).toEqual([0, 1, 2]);
    expect(payloads.map((payload) => payload.optionKey)).toEqual(["A", "B", "C"]);
    expect(payloads[1]?.isCorrect).toBe(true);
  });

  it("preserves correctness after a reorder", () => {
    const reordered = [rows()[2], rows()[0], rows()[1]] as OptionRow[];
    const payloads = buildOptionPayloads(reordered);

    expect(payloads.map((payload) => payload.optionKey)).toEqual(["C", "A", "B"]);
    expect(payloads.filter((payload) => payload.isCorrect).map((payload) => payload.optionKey)).toEqual(["B"]);
  });

  it("keeps exactly one correct option when single-correct selection changes", () => {
    const next = selectSingleCorrect(rows(), "C");

    expect(next.filter((row) => row.isCorrect).map((row) => row.optionKey)).toEqual(["C"]);
    expect(next).toHaveLength(3);
  });

  it("toggles one option without disturbing the others for multi-correct", () => {
    const next = toggleCorrect(rows(), "A");

    expect(next.filter((row) => row.isCorrect).map((row) => row.optionKey).sort()).toEqual(["A", "B"]);
  });

  it("reports obvious option problems but leaves authoritative rules to the backend", () => {
    expect(optionProblems(rows(), "SINGLE_OPTION")).toEqual([]);
    expect(optionProblems([{ optionKey: "A", content: "x", displayOrder: 0, isCorrect: false }], "SINGLE_OPTION")).toEqual([
      "At least two options are required.",
      "Single-choice questions need exactly one correct option.",
    ]);
    expect(
      optionProblems(
        [
          { optionKey: "A", content: "x", displayOrder: 0, isCorrect: true },
          { optionKey: "A", content: "y", displayOrder: 1, isCorrect: true },
        ],
        "MULTIPLE_OPTIONS",
      ),
    ).toEqual(["Option keys must be unique."]);
  });
});

describe("previous exam payloads", () => {
  it("submits the chosen examId and never a typed exam name", () => {
    const payload = buildPreviousExamRequest({ examId: "exam-1", year: "2023", shift: "Morning", source: "Prelims" });

    expect(payload).toEqual({ examId: "exam-1", year: 2023, shift: "Morning", source: "Prelims" });
    expect(Object.keys(payload)).not.toContain("examName");
  });

  it("omits blank optional metadata", () => {
    const payload = buildPreviousExamRequest({ examId: "exam-1", year: "", shift: "  ", source: "" });

    expect(payload).toEqual({ examId: "exam-1" });
  });

  it("rejects a missing exam", () => {
    expect(() => buildPreviousExamRequest({ examId: "" })).toThrow();
  });

  it("keeps the exam out of the update body", () => {
    const payload = buildPreviousExamUpdateRequest({ year: "2022", shift: "Evening", source: "Mains" });

    expect(payload).toEqual({ year: 2022, shift: "Evening", source: "Mains" });
    expect("examId" in payload).toBe(false);
  });
});

describe("asset references", () => {
  const id = "11111111-2222-3333-4444-555555555555";

  it("inserts the canonical shortcode and never a filesystem path", () => {
    const result = insertAssetReference("See ", 4, 4, id);

    expect(result.value).toBe(`See [asset: ${id}]`);
    expect(result.value).not.toContain("/");
    expect(result.value).not.toContain("\\");
    expect(referencedAssetIds(result.value)).toEqual([id]);
  });

  it("replaces the selection with the shortcode", () => {
    const result = insertAssetReference("a OLD b", 2, 5, id);

    expect(result.value).toBe(`a ${assetReference(id)} b`);
  });

  it("reports only canonical shortcodes as references", () => {
    expect(isAssetReference(assetReference(id))).toBe(true);
    expect(isAssetReference("/var/assets/diagram.png")).toBe(false);
    expect(isAssetReference("C:\\assets\\diagram.png")).toBe(false);
  });

  it("deduplicates repeated references", () => {
    expect(referencedAssetIds(`${assetReference(id)} and ${assetReference(id)}`)).toEqual([id]);
  });
});
