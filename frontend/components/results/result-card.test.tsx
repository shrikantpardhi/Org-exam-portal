import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ResultCard } from "@/components/results/result-card";
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

describe("ResultCard", () => {
  it("shows obtained marks, percentage, rank, percentile and status", () => {
    render(<ResultCard result={published} />);

    const card = screen.getByRole("article", { name: "Result r1" });
    expect(card).toHaveTextContent("82.50");
    expect(card).toHaveTextContent("82.50%");
    expect(card).toHaveTextContent("#4");
    expect(card).toHaveTextContent("91.50");
    expect(card).toHaveTextContent("Published");
  });

  it("renders a missing rank as unranked, never as rank zero", () => {
    render(<ResultCard result={{ ...published, rank: null }} />);

    const card = screen.getByRole("article", { name: "Result r1" });
    expect(card).toHaveTextContent("Unranked");
    expect(card).not.toHaveTextContent("#0");
  });

  it("labels a non-published status explicitly", () => {
    render(<ResultCard result={{ ...published, resultStatus: "CALCULATED" }} />);

    expect(screen.getByText("Calculated")).toBeInTheDocument();
    expect(screen.queryByText("Published")).toBeNull();
  });

  it("links to the full result when a destination is supplied", () => {
    render(<ResultCard result={published} href="/results/r1" />);

    expect(screen.getByRole("link", { name: "View result" })).toHaveAttribute(
      "href",
      "/results/r1",
    );
  });
});
