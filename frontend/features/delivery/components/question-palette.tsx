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

const STATUS_STYLES: Record<QuestionStatus, string> = {
  Answered: "bg-emerald-600 text-white border-emerald-700",
  NotAnswered: "bg-red-100 text-red-800 border-red-300",
  MarkedForReview: "bg-violet-600 text-white border-violet-700",
  AnsweredMarkedForReview: "bg-violet-600 text-white border-violet-700 ring-2 ring-emerald-500",
  NotVisited: "bg-white text-slate-700 border-slate-300",
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
    <div className="rounded border border-slate-200 bg-white p-3">
      <h2 className="text-sm font-semibold text-slate-900">Question palette</h2>
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
                className={`w-full justify-center border ${STATUS_STYLES[status]} ${
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
    <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
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
