import { describe, expect, it } from "vitest";

import {
  groupBySection,
  paletteCounts,
  questionStatus,
  statusLabel,
  unansweredCount,
} from "@/features/delivery/lib/palette";

/**
 * Palette and progress tests ({@code specs/14-P5-Delivery-V1.5.md §131, §133}).
 *
 * <p>The palette must never imply correctness — it reports interaction state only. These tests pin
 * the precedence rules and the arithmetic the submission dialog shows.
 */
describe("question status precedence", () => {
  it("is NotVisited when the student never opened the question", () => {
    expect(questionStatus({ visited: false, answered: false, markedForReview: false })).toBe("NotVisited");
  });

  it("is NotAnswered once visited without a response", () => {
    expect(questionStatus({ visited: true, answered: false, markedForReview: false })).toBe("NotAnswered");
  });

  it("is Answered when a response exists", () => {
    expect(questionStatus({ visited: true, answered: true, markedForReview: false })).toBe("Answered");
  });

  it("is MarkedForReview when marked but unanswered", () => {
    expect(questionStatus({ visited: true, answered: false, markedForReview: true })).toBe("MarkedForReview");
  });

  it("gives answered-and-marked its own state", () => {
    expect(questionStatus({ visited: true, answered: true, markedForReview: true })).toBe(
      "AnsweredMarkedForReview",
    );
  });

  it("never reports correctness", () => {
    const all = (
      ["Answered", "NotAnswered", "MarkedForReview", "AnsweredMarkedForReview", "NotVisited"] as const
    ).map(statusLabel);
    expect(all.join(" ").toLowerCase()).not.toContain("correct");
    expect(all.join(" ").toLowerCase()).not.toContain("wrong");
  });
});

describe("palette counts", () => {
  it("counts a mixed palette consistently", () => {
    const counts = paletteCounts([
      "Answered",
      "Answered",
      "NotAnswered",
      "MarkedForReview",
      "AnsweredMarkedForReview",
      "NotVisited",
    ]);
    expect(counts.total).toBe(6);
    expect(counts.answered).toBe(3);
    // Not answered includes marked-but-unanswered and never-visited.
    expect(counts.notAnswered).toBe(3);
    expect(counts.markedForReview).toBe(2);
    expect(counts.notVisited).toBe(1);
  });

  it("counts an answer that is also marked once as answered", () => {
    const counts = paletteCounts(["AnsweredMarkedForReview"]);
    expect(counts.answered).toBe(1);
    expect(counts.markedForReview).toBe(1);
  });

  it("reports zero for an empty palette", () => {
    expect(paletteCounts([])).toEqual({
      total: 0,
      answered: 0,
      notAnswered: 0,
      markedForReview: 0,
      notVisited: 0,
    });
  });

  it("the submission unanswered count matches the palette", () => {
    const counts = paletteCounts(["Answered", "NotVisited", "NotAnswered"]);
    expect(unansweredCount(counts)).toBe(2);
  });
});

describe("section grouping", () => {
  const questions = [
    { id: "q3", sectionName: "Reasoning", displayOrder: 3 },
    { id: "q1", sectionName: "Quantitative Aptitude", displayOrder: 1 },
    { id: "q2", sectionName: "Reasoning", displayOrder: 2 },
    { id: "q4", sectionName: null, displayOrder: 4 },
  ];

  it("orders sections by the configured section order", () => {
    const groups = groupBySection(questions, ["Quantitative Aptitude", "Reasoning"]);
    expect(groups.map((group) => group.section)).toEqual([
      "Quantitative Aptitude",
      "Reasoning",
      "Unsectioned",
    ]);
  });

  it("keeps a section the configuration did not name, so no question is hidden", () => {
    const groups = groupBySection(questions, ["Quantitative Aptitude"]);
    expect(groups.map((group) => group.section)).toContain("Reasoning");
    expect(groups.map((group) => group.section)).toContain("Unsectioned");
  });

  it("groups every question of a section together", () => {
    const groups = groupBySection(questions, ["Reasoning", "Quantitative Aptitude"]);
    const reasoning = groups.find((group) => group.section === "Reasoning");
    expect(reasoning?.ids).toEqual(["q3", "q2"]);
  });

  it("returns nothing for no questions", () => {
    expect(groupBySection([], ["A"])).toEqual([]);
  });
});
