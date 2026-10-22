import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ScorecardView } from "@/components/results/scorecard-view";
import type { ResultResponse } from "@/features/evaluation/schemas/evaluation";

const result: ResultResponse = {
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

const scorecard = {
  scorecardId: "s1",
  resultId: "r1",
  status: "GENERATED",
  generatedOn: "2026-10-20T10:00:00Z",
  secureDownloadReference: "/api/v1/results/r1/scorecard",
};

describe("ScorecardView", () => {
  it("renders the scorecard status, generation time and secure reference", () => {
    const { container } = render(<ScorecardView scorecard={scorecard} result={result} />);

    expect(screen.getByRole("heading", { name: "Scorecard" })).toBeInTheDocument();
    expect(screen.getByText("Generated")).toBeInTheDocument();
    expect(screen.getByText("/api/v1/results/r1/scorecard")).toBeInTheDocument();
    expect(container.querySelector("time")).toHaveAttribute(
      "dateTime",
      "2026-10-20T10:00:00Z",
    );
    expect(screen.getByRole("article", { name: "Result summary" })).toHaveTextContent("82.50");
  });

  it("renders explicit placeholders when the scorecard has no status or reference", () => {
    render(
      <ScorecardView
        scorecard={{ ...scorecard, status: null, generatedOn: null, secureDownloadReference: null }}
      />,
    );

    expect(screen.getByText("Unknown status")).toBeInTheDocument();
    expect(screen.getByText("Not generated yet")).toBeInTheDocument();
    expect(screen.getByText("Not available")).toBeInTheDocument();
  });

  it("does not render an unpublished result alongside the scorecard", () => {
    render(
      <ScorecardView
        scorecard={scorecard}
        result={{ ...result, resultStatus: "CALCULATED" }}
      />,
    );

    expect(screen.queryByRole("article", { name: "Result summary" })).toBeNull();
    expect(screen.queryByText("82.50")).toBeNull();
  });
});
