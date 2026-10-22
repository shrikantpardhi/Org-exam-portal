import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MetricCard } from "@/components/analytics/metric-card";

describe("MetricCard", () => {
  it("shows an explicit missing value instead of a fabricated zero", () => {
    render(
      <MetricCard
        label="Accuracy"
        value={null}
        description="Correct answers divided by answered questions."
      />,
    );

    const card = screen.getByRole("region", { name: "Accuracy" });
    expect(card).toHaveTextContent("Not available");
    expect(card).toHaveTextContent("Correct answers divided by answered questions.");
    expect(card).not.toHaveTextContent("0");
  });

  it("renders a measured zero as zero", () => {
    render(<MetricCard label="Lowest score" value="0.00" unit="marks" />);

    expect(screen.getByRole("region", { name: "Lowest score" })).toHaveTextContent("0.00");
  });

  it("shows the freshness status and the calculation timestamp", () => {
    const { container } = render(
      <MetricCard
        label="Attempts"
        value="4"
        lastCalculatedOn="2026-10-20T10:00:00Z"
        freshness="CURRENT"
      />,
    );

    expect(screen.getByText("Up to date")).toBeInTheDocument();
    const timestamp = container.querySelector("time");
    expect(timestamp).not.toBeNull();
    expect(timestamp).toHaveAttribute("dateTime", "2026-10-20T10:00:00Z");
  });

  it("states that analytics are not calculated yet when there is no timestamp", () => {
    render(<MetricCard label="Attempts" value={null} lastCalculatedOn={null} freshness="EMPTY" />);

    expect(screen.getByText("Not calculated yet")).toBeInTheDocument();
    expect(screen.getByText("No calculation timestamp yet")).toBeInTheDocument();
  });
});
