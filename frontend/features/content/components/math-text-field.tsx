"use client";

/**
 * Content textarea with math authoring affordances ({@code specs/12-P3-Content-V1.5.md §12, §104}).
 *
 * <p>Buttons wrap the current selection in inline {@code \(...\)} or block {@code \[...\]}. With no
 * selection a placeholder pair is inserted at the caret. No button ever emits {@code $...$}; the
 * only dollar signs that can appear are the ones the author typed, so currency like {@code $500}
 * survives untouched.
 */
import { useRef } from "react";

import { Button } from "@/components/ui/button";
import { insertBlockMath, insertInlineMath } from "@/features/content/lib/math";

export function MathTextField({
  label,
  value,
  onChange,
  rows = 4,
  disabled = false,
  hint,
  textareaRef,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  rows?: number;
  disabled?: boolean;
  hint?: string;
  /** Optional external handle so a sibling panel can insert at this field's caret. */
  textareaRef?: React.RefObject<HTMLTextAreaElement | null>;
}) {
  const internalRef = useRef<HTMLTextAreaElement | null>(null);
  const textarea = textareaRef ?? internalRef;

  function apply(transform: (selection: { value: string; selectionStart: number; selectionEnd: number }) => {
    value: string;
    selectionStart: number;
    selectionEnd: number;
  }) {
    const element = textarea.current;
    if (!element) {
      return;
    }
    const result = transform({
      value,
      selectionStart: element.selectionStart,
      selectionEnd: element.selectionEnd,
    });
    onChange(result.value);
    // Restore the caret after React re-renders the controlled textarea.
    requestAnimationFrame(() => {
      const current = textarea.current;
      if (!current) {
        return;
      }
      current.focus();
      current.setSelectionRange(result.selectionStart, result.selectionEnd);
    });
  }

  return (
    <div className="space-y-1">
      <label htmlFor={`math-${label}`} className="text-sm font-medium">
        {label}
      </label>
      <textarea
        id={`math-${label}`}
        ref={textarea}
        aria-label={label}
        rows={rows}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
      />
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={disabled}
          aria-label="Insert inline math"
          onClick={() => apply(insertInlineMath)}
        >
          Inline math
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={disabled}
          aria-label="Insert block math"
          onClick={() => apply(insertBlockMath)}
        >
          Block math
        </Button>
        <span className="text-xs text-slate-500">
          {hint ?? "Inline math uses the round delimiters and block math the square ones. $500 stays ordinary text."}
        </span>
      </div>
    </div>
  );
}
