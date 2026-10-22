import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { TrendChart } from "@/components/analytics/trend-chart";

const points = [
  { label: "Attempt 1", value: 72.5 },
  { label: "Attempt 2", value: null },
  { label: "Attempt 3", value: 61 },
];

function renderChart(overrides: Partial<Parameters<typeof TrendChart>[0]> = {}) {
  return render(
    <TrendChart
      title="Score trend"
      description="Percentage obtained per evaluated attempt."
      unit="%"
      points={points}
      max={100}
      lastCalculatedOn="2026-10-20T10:00:00Z"
      freshness="CURRENT"
      {...overrides}
    />,
  );
}

describe("TrendChart", () => {
  it("renders a chart with a focusable point per value", () => {
    renderChart();

    expect(screen.getByRole("group", { name: /Score trend chart/ })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Attempt 1: 72.5%" })).toHaveAttribute(
      "tabindex",
      "0",
    );
    expect(screen.getByRole("img", { name: "Attempt 3: 61.0%" })).toBeInTheDocument();
  });

  it("renders a tabular fallback inside a keyboard-operable disclosure", () => {
    renderChart();

    const disclosure = screen.getByText("View data table");
    expect(disclosure.tagName).toBe("SUMMARY");

    const table = screen.getByRole("table", { name: "Score trend: data table" });
    expect(within(table).getByRole("rowheader", { name: "Attempt 1" })).toBeInTheDocument();
    expect(within(table).getByText("72.5%")).toBeInTheDocument();
    expect(within(table).getByText("No value")).toBeInTheDocument();
  });

  it("opens the data table when the disclosure is activated", () => {
    renderChart();

    const summary = screen.getByText("View data table");
    const details = summary.closest("details");
    expect(details).not.toBeNull();
    expect(details).not.toHaveAttribute("open");

    fireEvent.click(summary);

    expect(details).toHaveAttribute("open");
  });

  it("shows the empty state instead of an empty axis", () => {
    renderChart({ points: [], emptyMessage: "No trend data yet." });

    expect(screen.getByText("No trend data yet.")).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: /Score trend chart/ })).toBeNull();
  });

  it("shows the freshness of the charted data", () => {
    const { container } = renderChart();

    expect(screen.getByText("Up to date")).toBeInTheDocument();
    expect(container.querySelector("time")).toHaveAttribute(
      "dateTime",
      "2026-10-20T10:00:00Z",
    );
  });
});
