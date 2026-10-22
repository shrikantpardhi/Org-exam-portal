/**
 * Question palette state and progress arithmetic ({@code specs/14-P5-Delivery-V1.5.md §131, §133}).
 *
 * <p>The palette classifies each question from server-backed response state plus local "dirty" flags.
 * The classification is a pure function of that input so the legend, the counts and the submission
 * dialog all agree, and so it can be asserted without rendering.
 *
 * <p>Statuses are intentionally limited to what the contract can prove. The runtime never claims a
 * question is "correct" or "incorrect" — correctness is not available to the student client (§74,
 * §127) and the palette must not imply it.
 */

export type QuestionStatus =
  | "Answered"
  | "NotAnswered"
  | "MarkedForReview"
  | "AnsweredMarkedForReview"
  | "NotVisited";

export interface QuestionStateInput {
  readonly visited: boolean;
  readonly answered: boolean;
  readonly markedForReview: boolean;
}

/**
 * Derives the palette status.
 *
 * <p>The precedence is the conventional examination one: "answered and marked" is its own state, and
 * "marked but unanswered" is distinct from "unanswered". A question that was never opened is
 * {@code NotVisited} even if a stale response row exists, because "not visited" is about the student's
 * navigation, which the server records as a {@code visited} flag on the response.
 */
export function questionStatus(input: QuestionStateInput): QuestionStatus {
  if (input.answered && input.markedForReview) {
    return "AnsweredMarkedForReview";
  }
  if (input.markedForReview) {
    return "MarkedForReview";
  }
  if (input.answered) {
    return "Answered";
  }
  return input.visited ? "NotAnswered" : "NotVisited";
}

export interface PaletteCounts {
  readonly total: number;
  readonly answered: number;
  readonly notAnswered: number;
  readonly markedForReview: number;
  readonly notVisited: number;
}

/** The counts shown in the legend and in the submission confirmation (§133). */
export function paletteCounts(statuses: readonly QuestionStatus[]): PaletteCounts {
  let answered = 0;
  let notAnswered = 0;
  let markedForReview = 0;
  let notVisited = 0;
  for (const status of statuses) {
    switch (status) {
      case "Answered":
        answered += 1;
        break;
      case "AnsweredMarkedForReview":
        answered += 1;
        markedForReview += 1;
        break;
      case "MarkedForReview":
        markedForReview += 1;
        notAnswered += 1;
        break;
      case "NotAnswered":
        notAnswered += 1;
        break;
      case "NotVisited":
        notVisited += 1;
        notAnswered += 1;
        break;
    }
  }
  return { total: statuses.length, answered, notAnswered, markedForReview, notVisited };
}

/**
 * The unanswered count the submission dialog reports. It deliberately counts every non-answered
 * question, including never-visited ones, because that is what the student is about to submit.
 */
export function unansweredCount(counts: PaletteCounts): number {
  return counts.notAnswered;
}

/** Groups questions by section in configuration order, for the section navigator (§131). */
export function groupBySection(
  questions: readonly { sectionName: string | null; displayOrder: number; id: string }[],
  sectionOrder: readonly string[],
): { section: string; ids: string[] }[] {
  const buckets = new Map<string, string[]>();
  for (const question of questions) {
    const name = question.sectionName ?? "Unsectioned";
    const bucket = buckets.get(name);
    if (bucket) {
      bucket.push(question.id);
    } else {
      buckets.set(name, [question.id]);
    }
  }
  // The configured order leads; any section the configuration did not name follows in first-seen
  // order, so a question is never hidden from the navigator because the configuration is incomplete.
  const ordered: { section: string; ids: string[] }[] = [];
  for (const name of sectionOrder) {
    const ids = buckets.get(name);
    if (ids) {
      ordered.push({ section: name, ids });
      buckets.delete(name);
    }
  }
  for (const [section, ids] of buckets) {
    ordered.push({ section, ids });
  }
  return ordered;
}

/** The human label for a status, kept in one place so the legend and the palette agree. */
export function statusLabel(status: QuestionStatus): string {
  switch (status) {
    case "Answered":
      return "Answered";
    case "NotAnswered":
      return "Not answered";
    case "MarkedForReview":
      return "Marked for review";
    case "AnsweredMarkedForReview":
      return "Answered & marked";
    case "NotVisited":
      return "Not visited";
  }
}
