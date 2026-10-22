import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { AssessmentQuestionTable } from "@/features/assessment/components/assessment-question-table";
import type { AssessmentQuestion } from "@/features/assessment/schemas/assessment-builder";

/**
 * Section assignment and scoring ({@code specs/13-P4-Assessment-V1.5.md §43, §44, §104, §105}).
 *
 * <p>The section dropdown must offer exactly the bound blueprint's section names, in blueprint order,
 * with an "Unassigned" escape. No extra section can be typed into existence.
 */
function question(id: string, displayOrder: number, sectionName: string | null): AssessmentQuestion {
  return {
    id,
    assessmentVersionId: "v-1",
    questionVersionId: `qv-${id}`,
    displayOrder,
    sectionName,
    marks: 2,
    negativeMarks: 0.5,
  };
}

describe("AssessmentQuestionTable", () => {
  it("offers only the blueprint section names, in blueprint order", () => {
    render(
      <AssessmentQuestionTable
        questions={[question("aq-1", 1, "Reasoning")]}
        sectionNames={["Reasoning", "Quantitative Aptitude", "General Awareness"]}
        onRemove={vi.fn()}
        onSectionChange={vi.fn()}
        onScoreChange={vi.fn()}
        onMove={vi.fn()}
      />,
    );

    const select = screen.getByLabelText("Section for question 1");
    const options = Array.from((select as HTMLSelectElement).options).map((option) => option.value);

    expect(options).toEqual(["", "Reasoning", "Quantitative Aptitude", "General Awareness"]);
    // The unassigned option is the empty sentinel, not a section name.
    expect(screen.getByRole("option", { name: "Unassigned" })).toHaveValue("");
    expect(options).not.toContain("Physics");
  });

  it("emits the chosen section from the blueprint list", async () => {
    const onSectionChange = vi.fn();
    render(
      <AssessmentQuestionTable
        questions={[question("aq-1", 1, "Reasoning")]}
        sectionNames={["Reasoning", "Quantitative Aptitude"]}
        onRemove={vi.fn()}
        onSectionChange={onSectionChange}
        onScoreChange={vi.fn()}
        onMove={vi.fn()}
      />,
    );

    await userEvent.selectOptions(
      screen.getByLabelText("Section for question 1"),
      "Quantitative Aptitude",
    );

    expect(onSectionChange).toHaveBeenCalledWith("aq-1", "Quantitative Aptitude");
  });

  it("emits scoring changes independently of the version default", async () => {
    const onScoreChange = vi.fn();
    render(
      <AssessmentQuestionTable
        questions={[question("aq-1", 1, "Reasoning")]}
        sectionNames={["Reasoning"]}
        onRemove={vi.fn()}
        onSectionChange={vi.fn()}
        onScoreChange={onScoreChange}
        onMove={vi.fn()}
      />,
    );

    const marks = screen.getByLabelText("Marks for question 1");
    await userEvent.clear(marks);
    await userEvent.type(marks, "5");
    await userEvent.tab();

    expect(onScoreChange).toHaveBeenCalledWith("aq-1", 5, 0.5);
  });

  it("disables reordering at the boundaries", () => {
    render(
      <AssessmentQuestionTable
        questions={[question("aq-1", 1, null), question("aq-2", 2, null)]}
        sectionNames={["Reasoning"]}
        onRemove={vi.fn()}
        onSectionChange={vi.fn()}
        onScoreChange={vi.fn()}
        onMove={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: "Move question 1 up" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Move question 2 down" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Move question 1 down" })).toBeEnabled();
  });

  it("warns when the blueprint has no sections", () => {
    render(
      <AssessmentQuestionTable
        questions={[]}
        sectionNames={[]}
        onRemove={vi.fn()}
        onSectionChange={vi.fn()}
        onScoreChange={vi.fn()}
        onMove={vi.fn()}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(/has no sections/);
  });
});
