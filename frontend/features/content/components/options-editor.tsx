"use client";

/**
 * Authoring options editor ({@code specs/12-P3-Content-V1.5.md §28-§33, §103}).
 *
 * <p>Rows carry {@code optionKey}, content, display order and correctness. Reordering rewrites the
 * display order; single-correct templates keep exactly one correct answer, multi-correct templates
 * toggle independently. Client-side checks cover only obvious mistakes — the backend template
 * registry remains authoritative for how many options and correct answers a template requires.
 */
import { useState } from "react";

import { Button } from "@/components/ui/button";
import type { OptionDraft } from "@/features/content/schemas/content";

export type AnswerModel = "SINGLE_OPTION" | "MULTIPLE_OPTIONS";

const KEY_LABELS = ["A", "B", "C", "D", "E", "F", "G", "H"];

export function nextOptionKey(rows: OptionDraft[]): string {
  for (const label of KEY_LABELS) {
    if (!rows.some((row) => row.optionKey === label)) {
      return label;
    }
  }
  return `O${rows.length + 1}`;
}

/** Reorders rows by index moves, keeping display order contiguous. */
export function moveOption(rows: OptionDraft[], from: number, to: number): OptionDraft[] {
  if (from < 0 || to < 0 || from >= rows.length || to >= rows.length || from === to) {
    return rows;
  }
  const next = [...rows];
  const [moved] = next.splice(from, 1);
  if (!moved) {
    return rows;
  }
  next.splice(to, 0, moved);
  return next.map((row, index) => ({ ...row, displayOrder: index }));
}

export function OptionsEditor({
  rows,
  onChange,
  answerModel,
  disabled = false,
  problems = [],
}: {
  rows: OptionDraft[];
  onChange: (next: OptionDraft[]) => void;
  answerModel: AnswerModel;
  disabled?: boolean;
  problems?: string[];
}) {
  const [announcement, setAnnouncement] = useState("");

  function update(index: number, patch: Partial<OptionDraft>) {
    onChange(rows.map((row, position) => (position === index ? { ...row, ...patch } : row)));
  }

  function setCorrect(index: number) {
    const selected = rows[index];
    if (!selected) {
      return;
    }
    if (answerModel === "SINGLE_OPTION") {
      onChange(rows.map((row, position) => ({ ...row, isCorrect: position === index })));
      setAnnouncement(`Option ${selected.optionKey} is the only correct answer.`);
      return;
    }
    onChange(
      rows.map((row, position) => (position === index ? { ...row, isCorrect: !row.isCorrect } : row)),
    );
    setAnnouncement(
      `Option ${selected.optionKey} is now ${selected.isCorrect ? "not correct" : "correct"}.`,
    );
  }

  function add() {
    const key = nextOptionKey(rows);
    const defaultCorrect = answerModel === "SINGLE_OPTION" && rows.length === 0;
    onChange([
      ...rows,
      { optionKey: key, content: "", displayOrder: rows.length, isCorrect: defaultCorrect },
    ]);
    setAnnouncement(`Option ${key} added.`);
  }

  function remove(index: number) {
    const removed = rows[index];
    onChange(rows.filter((_, position) => position !== index).map((row, position) => ({
      ...row,
      displayOrder: position,
    })));
    if (removed) {
      setAnnouncement(`Option ${removed.optionKey} removed.`);
    }
  }

  return (
    <section aria-label="Options editor" className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Options</h2>
        <Button variant="outline" size="sm" disabled={disabled} onClick={add}>
          Add option
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        {answerModel === "SINGLE_OPTION"
          ? "Single-choice template: exactly one correct answer."
          : "Multiple-choice template: one or more correct answers."}{" "}
        The backend template configuration is authoritative for the final option rules.
      </p>

      {rows.length === 0 ? <p className="text-sm text-muted-foreground">No options yet.</p> : null}

      <ol aria-label="Option rows" className="space-y-2">
        {rows.map((row, index) => (
          <li key={`${row.optionKey}-${index}`} className="rounded border border-border p-3">
            <div className="grid gap-2 sm:grid-cols-[6rem_1fr_auto]">
              <label className="space-y-1 text-sm">
                <span className="font-medium">Option key</span>
                <input
                  aria-label={`Option ${index + 1} key`}
                  value={row.optionKey}
                  maxLength={20}
                  disabled={disabled}
                  onChange={(event) => update(index, { optionKey: event.target.value })}
                  className="w-full rounded-md border border-input px-2 py-1 text-sm"
                />
              </label>
              <label className="space-y-1 text-sm">
                <span className="font-medium">Option content</span>
                <input
                  aria-label={`Option ${index + 1} content`}
                  value={row.content}
                  disabled={disabled}
                  onChange={(event) => update(index, { content: event.target.value })}
                  className="w-full rounded-md border border-input px-2 py-1 text-sm"
                />
              </label>
              <div className="flex flex-col justify-end gap-1 text-sm">
                <span className="font-medium">Display order</span>
                <span aria-label={`Option ${index + 1} display order`} className="font-mono">
                  {index}
                </span>
              </div>
            </div>

            <div className="mt-2 flex flex-wrap items-center gap-3">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type={answerModel === "SINGLE_OPTION" ? "radio" : "checkbox"}
                  name={answerModel === "SINGLE_OPTION" ? "correct-option" : undefined}
                  aria-label={`Option ${index + 1} correct`}
                  checked={row.isCorrect}
                  disabled={disabled}
                  onChange={() => setCorrect(index)}
                />
                Correct answer
              </label>
              <Button
                variant="ghost"
                size="sm"
                disabled={disabled || index === 0}
                aria-label={`Move option ${index + 1} up`}
                onClick={() => onChange(moveOption(rows, index, index - 1))}
              >
                Move up
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={disabled || index === rows.length - 1}
                aria-label={`Move option ${index + 1} down`}
                onClick={() => onChange(moveOption(rows, index, index + 1))}
              >
                Move down
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={disabled}
                aria-label={`Remove option ${index + 1}`}
                onClick={() => remove(index)}
                className="text-red-600"
              >
                Remove
              </Button>
            </div>
          </li>
        ))}
      </ol>

      {problems.length > 0 ? (
        <ul aria-label="Option problems" className="space-y-1 text-sm text-amber-700">
          {problems.map((problem) => (
            <li key={problem}>{problem}</li>
          ))}
        </ul>
      ) : null}

      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </section>
  );
}
