import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ResultDetail } from "@/features/evaluation/components/result-detail";
import type { ResultResponse } from "@/features/evaluation/schemas/evaluation";

const published: ResultResponse = {
  id: "r1",
  evaluationId: "e1",
  attemptId: "a1",
  assessmentVersionId: "v1",
  obtainedMarks: 82.5,
  percentage: 82.5,
  rank: 4,
  percentile: 91.5,
  resultStatus: "PUBLISHED",
  supersededOn: null,
};

describe("ResultDetail visibility", () => {
  it("renders a published result with scorecard and attempt links", () => {
    render(<ResultDetail result={published} />);

    expect(screen.getByRole("article", { name: "Your result" })).toHaveTextContent("82.50");
    expect(screen.getByRole("link", { name: "View scorecard" })).toHaveAttribute(
      "href",
      "/results/r1/scorecard",
    );
    expect(screen.getByRole("link", { name: "Attempt-bound result" })).toHaveAttribute(
      "href",
      "/attempts/a1/result",
    );
  });

  it("refuses to render a calculated result", () => {
    render(<ResultDetail result={{ ...published, resultStatus: "CALCULATED" }} />);

    expect(screen.getByText("This result is not published yet.")).toBeInTheDocument();
    expect(screen.queryByText("Obtained marks")).toBeNull();
    expect(screen.queryByText("82.50")).toBeNull();
    expect(screen.queryByText("#4")).toBeNull();
  });

  it("refuses to render a superseded result", () => {
    render(
      <ResultDetail
        result={{ ...published, supersededOn: "2026-10-21T10:00:00Z" }}
      />,
    );

    expect(screen.getByText("This result has been superseded.")).toBeInTheDocument();
    expect(screen.queryByText("82.50")).toBeNull();
  });
});
