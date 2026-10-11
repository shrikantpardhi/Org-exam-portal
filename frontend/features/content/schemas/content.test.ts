import { describe, expect, it } from "vitest";

import { questionFormSchema } from "@/features/content/schemas/content";

const base = {
  templateId: "t",
  examId: "e",
  subjectId: "s",
  languageId: "l",
  content: "What is 2+2?",
  marks: "2",
  negativeMarks: "0",
  difficulty: "MEDIUM",
};

describe("questionFormSchema", () => {
  it("accepts a valid payload and coerces numeric marks", () => {
    const parsed = questionFormSchema.parse(base);

    expect(parsed.marks).toBe(2);
    expect(parsed.negativeMarks).toBe(0);
  });

  it("rejects missing content", () => {
    expect(questionFormSchema.safeParse({ ...base, content: "" }).success).toBe(false);
  });

  it("rejects negative marks", () => {
    expect(questionFormSchema.safeParse({ ...base, marks: "-1" }).success).toBe(false);
  });

  it("rejects an unknown difficulty", () => {
    expect(questionFormSchema.safeParse({ ...base, difficulty: "TRIVIAL" }).success).toBe(false);
  });
});
