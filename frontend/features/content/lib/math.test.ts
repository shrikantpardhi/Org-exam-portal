import { describe, expect, it } from "vitest";

import {
  BLOCK_MATH_CLOSE,
  BLOCK_MATH_OPEN,
  INLINE_MATH_CLOSE,
  INLINE_MATH_OPEN,
  MATH_PLACEHOLDER,
  insertBlockMath,
  insertInlineMath,
  wrapSelection,
} from "@/features/content/lib/math";

const ANY_DOLLAR_MATH = /\${1,2}[^$]+\${1,2}/;

describe("math authoring helpers", () => {
  it("wraps a selection in inline parentheses delimiters", () => {
    const result = insertInlineMath({ value: "Solve a+b", selectionStart: 6, selectionEnd: 9 });

    expect(result.value).toBe(`Solve ${INLINE_MATH_OPEN}a+b${INLINE_MATH_CLOSE}`);
    expect(result.value).not.toMatch(ANY_DOLLAR_MATH);
    expect(result.value).not.toContain("$");
  });

  it("wraps a selection in block square-bracket delimiters", () => {
    const result = insertBlockMath({ value: "Evaluate:\nx=1", selectionStart: 10, selectionEnd: 13 });

    expect(result.value).toBe(`Evaluate:\n${BLOCK_MATH_OPEN}x=1${BLOCK_MATH_CLOSE}`);
    expect(result.value).not.toContain("$");
  });

  it("never emits dollar delimiters for any mode", () => {
    for (const mode of ["inline", "block"] as const) {
      const result = wrapSelection({ value: "abc", selectionStart: 0, selectionEnd: 3 }, mode);
      expect(result.value).not.toContain("$");
      expect(result.value.startsWith("\\")).toBe(true);
    }
  });

  it("leaves currency text such as $500 untouched", () => {
    const value = "The cost is $500 for 3 items.";

    // The author has not selected anything, so the helper only inserts at the caret.
    const result = insertInlineMath({ value, selectionStart: value.length, selectionEnd: value.length });

    expect(result.value).toContain("The cost is $500 for 3 items.");
    expect(result.value.endsWith(`${INLINE_MATH_OPEN}${MATH_PLACEHOLDER}${INLINE_MATH_CLOSE}`)).toBe(true);
  });

  it("keeps currency text inside a wrapped selection exactly as typed", () => {
    const value = "Price $500 only";
    const result = wrapSelection(
      { value, selectionStart: value.indexOf("$500"), selectionEnd: value.indexOf("$500") + 4 },
      "block",
    );

    expect(result.value).toBe(`Price ${BLOCK_MATH_OPEN}$500${BLOCK_MATH_CLOSE} only`);
    expect(result.value).not.toMatch(ANY_DOLLAR_MATH);
  });

  it("inserts a placeholder pair with an empty selection and selects the placeholder", () => {
    const result = insertInlineMath({ value: "ab", selectionStart: 1, selectionEnd: 1 });

    expect(result.value).toBe(`a${INLINE_MATH_OPEN}${MATH_PLACEHOLDER}${INLINE_MATH_CLOSE}b`);
    expect(result.value.slice(result.selectionStart, result.selectionEnd)).toBe(MATH_PLACEHOLDER);
  });

  it("inserts a block placeholder pair with an empty selection", () => {
    const result = insertBlockMath({ value: "", selectionStart: 0, selectionEnd: 0 });

    expect(result.value).toBe(`${BLOCK_MATH_OPEN}${MATH_PLACEHOLDER}${BLOCK_MATH_CLOSE}`);
  });

  it("clamps out-of-range offsets instead of dropping text", () => {
    const result = wrapSelection({ value: "abc", selectionStart: 99, selectionEnd: 120 }, "inline");

    expect(result.value).toBe(`abc${INLINE_MATH_OPEN}${MATH_PLACEHOLDER}${INLINE_MATH_CLOSE}`);
  });
});
