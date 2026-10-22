/**
 * Math authoring helpers ({@code specs/12-P3-Content-V1.5.md §12, §104}).
 *
 * <p>The only approved delimiters are inline {@code \( ... \)} and block {@code \[ ... \]}. The
 * helpers never emit {@code $...$} or {@code $$...$$}, and they never rewrite text outside the
 * current selection — so ordinary currency such as {@code $500} is left exactly as it was typed.
 * These are pure functions so the behaviour is testable without a DOM editor.
 */

export const INLINE_MATH_OPEN = "\\(";
export const INLINE_MATH_CLOSE = "\\)";
export const BLOCK_MATH_OPEN = "\\[";
export const BLOCK_MATH_CLOSE = "\\]";

/** Placeholder inserted when the author invokes a math helper with nothing selected. */
export const MATH_PLACEHOLDER = "x";

export type MathMode = "inline" | "block";

export interface TextSelection {
  value: string;
  selectionStart: number;
  selectionEnd: number;
}

export interface InsertResult {
  value: string;
  /** Caret/selection offset after the insertion, so a textarea can restore it. */
  selectionStart: number;
  selectionEnd: number;
}

function delimiterPair(mode: MathMode): { open: string; close: string } {
  return mode === "inline"
    ? { open: INLINE_MATH_OPEN, close: INLINE_MATH_CLOSE }
    : { open: BLOCK_MATH_OPEN, close: BLOCK_MATH_CLOSE };
}

/**
 * Wraps the selected text in the approved delimiters.
 *
 * <p>With an empty selection a placeholder pair is inserted at the caret and the placeholder is
 * selected, so the author can type over it immediately. Text before and after the selection is
 * preserved byte for byte.
 */
export function wrapSelection(selection: TextSelection, mode: MathMode): InsertResult {
  const { open, close } = delimiterPair(mode);
  const start = clamp(selection.selectionStart, selection.value);
  const end = clamp(selection.selectionEnd, selection.value);
  const before = selection.value.slice(0, start);
  const selected = selection.value.slice(start, end);
  const after = selection.value.slice(end);

  if (selected.length === 0) {
    const inserted = `${open}${MATH_PLACEHOLDER}${close}`;
    const placeholderStart = before.length + open.length;
    return {
      value: `${before}${inserted}${after}`,
      selectionStart: placeholderStart,
      selectionEnd: placeholderStart + MATH_PLACEHOLDER.length,
    };
  }

  return {
    value: `${before}${open}${selected}${close}${after}`,
    selectionStart: start,
    selectionEnd: start + open.length + selected.length + close.length,
  };
}

/** Inline math affordance: {@code \( selection \)} ({@code §104}). */
export function insertInlineMath(selection: TextSelection): InsertResult {
  return wrapSelection(selection, "inline");
}

/** Block math affordance: {@code \[ selection \]} ({@code §104}). */
export function insertBlockMath(selection: TextSelection): InsertResult {
  return wrapSelection(selection, "block");
}

/**
 * True when the text contains a currency-sign habit that must not be reinterpreted as math.
 *
 * <p>This deliberately does not reject the content: {@code $500} is valid ordinary text
 * ({@code §104}); the check only lets the editor explain that no change was made.
 */
export function containsCurrencyText(value: string): boolean {
  return /\$\d/.test(value);
}

function clamp(offset: number, value: string): number {
  if (!Number.isFinite(offset)) {
    return value.length;
  }
  return Math.min(Math.max(offset, 0), value.length);
}
