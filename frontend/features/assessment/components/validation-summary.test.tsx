import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ValidationSummary } from "@/features/assessment/components/validation-summary";
import { PublishConfirmation } from "@/features/assessment/components/publish-confirmation";

/**
 * Validation panel and publish gating ({@code specs/13-P4-Assessment-V1.5.md §106, §108}).
 */
describe("ValidationSummary", () => {
  it("renders grouped categories with counts", () => {
    render(
      <ValidationSummary
        result={{
          valid: false,
          errors: [
            { code: "QUESTION_COUNT_MISMATCH", message: "requires 100 questions but 98 are configured" },
            { code: "QUESTION_VERSION_NOT_PUBLISHED", message: "question version is not published" },
            { code: "SECTION_COUNT_MISMATCH", message: "section A requires 25 but has 24" },
            { code: "MYSTERY_CODE", message: "an unrecognised server rule" },
          ],
        }}
      />,
    );

    expect(screen.getByText(/4 validation issues/)).toBeInTheDocument();
    expect(screen.getByTestId("validation-group-Questions")).toHaveTextContent("Questions (2)");
    expect(screen.getByTestId("validation-group-Sections")).toHaveTextContent("Sections (1)");
    expect(screen.getByTestId("validation-group-Other")).toHaveTextContent("Other (1)");
    expect(screen.getByText(/an unrecognised server rule/)).toBeInTheDocument();
    expect(screen.queryByTestId("validation-group-Timing")).not.toBeInTheDocument();
  });

  it("reports success without listing issues", () => {
    render(<ValidationSummary result={{ valid: true, errors: [] }} />);
    expect(screen.getByRole("status")).toHaveTextContent(/validated this assessment version/);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("PublishConfirmation", () => {
  const version = {
    id: "av-1",
    assessmentId: "a-1",
    blueprintVersionId: "bv-1",
    version: 3,
    totalQuestions: 2,
    totalMarks: 4,
    durationMinutes: 60,
    engineType: "NTA",
  } as never;

  it("shows the confirmation summary fields", () => {
    render(
      <PublishConfirmation
        assessmentName="SSC CGL Mock 1"
        version={version}
        sectionNames={["Reasoning", "Quantitative Aptitude"]}
        questionCount={2}
        totalMarks={4}
        validation={{ valid: true, errors: [] }}
        onPublish={vi.fn()}
      />,
    );

    expect(screen.getByText("SSC CGL Mock 1")).toBeInTheDocument();
    expect(screen.getByText("bv-1")).toBeInTheDocument();
    expect(screen.getByText("Reasoning")).toBeInTheDocument();
    expect(screen.getByText("Quantitative Aptitude")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Publish" })).toBeEnabled();
  });

  it("keeps publish disabled until validation passes", () => {
    render(
      <PublishConfirmation
        assessmentName="SSC CGL Mock 1"
        version={version}
        sectionNames={["Reasoning"]}
        questionCount={2}
        totalMarks={4}
        validation={null}
        onPublish={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
    expect(screen.getByText(/Run validation first/)).toBeInTheDocument();
  });

  it("keeps publish disabled when validation reports errors", () => {
    render(
      <PublishConfirmation
        assessmentName="SSC CGL Mock 1"
        version={version}
        sectionNames={["Reasoning"]}
        questionCount={2}
        totalMarks={4}
        validation={{
          valid: false,
          errors: [{ code: "TOTAL_MARKS_MISMATCH", message: "marks do not add up" }],
        }}
        onPublish={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
  });

  it("replaces the control with an immutability notice once published", () => {
    render(
      <PublishConfirmation
        assessmentName="SSC CGL Mock 1"
        version={version}
        sectionNames={["Reasoning"]}
        questionCount={2}
        totalMarks={4}
        validation={{ valid: true, errors: [] }}
        published
        onPublish={vi.fn()}
      />,
    );

    expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
    expect(screen.getByText(/published and immutable/)).toBeInTheDocument();
  });
});
