"use client";

import { Button } from "@/components/ui/button";
import { statusLabel, type QuestionStatus } from "@/features/delivery/lib/palette";

/**
 * The question palette and legend ({@code specs/14-P5-Delivery-V1.5.md §131, §133}).
 *
 * <p>Presentation-only: it renders statuses it is given and reports a click. It never derives
 * correctness and never decides navigation policy — the runtime does that from the configuration, and
 * the backend enforces it regardless.
 *
 * <p>Each button carries the status in its accessible name, so a screen-reader user hears
 * "Question 4, Answered and marked" rather than a colour.
 */

// Examination runtime palette (specs/Design-Theme.md §21). These are the -600/-700 "state" variants,
// not the -subtle backgrounds: a candidate must be able to tell answered from marked at a glance, and
// the runtime deliberately avoids soft tints for state. Colour is always backed by the accessible name.
const STATUS_STYLES: Record<QuestionStatus, string> = {
  Answered: "bg-success text-success-foreground border-success",
  NotAnswered: "bg-destructive-subtle text-destructive border-destructive",
  MarkedForReview: "bg-violet-600 text-white border-violet-700",
  AnsweredMarkedForReview: "bg-violet-600 text-white border-violet-700 ring-2 ring-success",
  NotVisited: "bg-surface text-foreground border-input",
};

export function QuestionPalette({
  statuses,
  currentIndex,
  onJump,
  disabled = false,
}: {
  /** Status for every question, in display order. */
  statuses: readonly QuestionStatus[];
  /** Zero-based index of the question on screen. */
  currentIndex: number;
  onJump: (index: number) => void;
  /** True when navigation is locked (read-only second tab, or a submitted attempt). */
  disabled?: boolean;
}) {
  return (
    <div className="rounded-md border border-border bg-surface p-3">
      <h2 className="text-sm font-semibold text-foreground">Question palette</h2>
      <ol className="mt-2 grid grid-cols-5 gap-2" aria-label="Questions">
        {statuses.map((status, index) => {
          const isCurrent = index === currentIndex;
          return (
            <li key={index}>
              <Button
                variant="outline"
                size="sm"
                disabled={disabled}
                aria-current={isCurrent ? "true" : undefined}
                aria-label={`Question ${index + 1}, ${statusLabel(status)}`}
                className={`h-11 w-full min-w-11 justify-center border ${STATUS_STYLES[status]} ${
                  isCurrent ? "outline outline-2 outline-offset-2 outline-slate-900" : ""
                }`}
                onClick={() => onJump(index)}
              >
                {index + 1}
              </Button>
            </li>
          );
        })}
      </ol>
      <Legend />
    </div>
  );
}

/** The colour key, spelled out so the palette is usable without colour perception. */
function Legend() {
  const entries: QuestionStatus[] = [
    "Answered",
    "NotAnswered",
    "MarkedForReview",
    "AnsweredMarkedForReview",
    "NotVisited",
  ];
  return (
    <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
      {entries.map((status) => (
        <li key={status} className="flex items-center gap-1">
          <span
            aria-hidden="true"
            className={`inline-block h-3 w-3 rounded-sm border ${STATUS_STYLES[status]}`}
          />
          {statusLabel(status)}
        </li>
      ))}
    </ul>
  );
}
