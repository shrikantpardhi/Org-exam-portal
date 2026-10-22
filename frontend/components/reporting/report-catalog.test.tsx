import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ReportCatalog } from "@/components/reporting/ReportCatalog";
import type { ReportCatalogEntry } from "@/features/reporting/schemas/reporting";

/**
 * Report catalog tests ({@code specs/22-P13-Reporting-V1.5.md §11, §13, §107}).
 *
 * <p>The catalog is the operator's only source of report types, so it must group them by category,
 * describe them, and make a registered-but-unimplemented report unmistakably unavailable.
 */
function entry(overrides: Partial<ReportCatalogEntry> = {}): ReportCatalogEntry {
  return {
    reportType: "STUDENT_PROFILE",
    category: "STUDENT",
    displayName: "Student Profile",
    description: "Identity and enrolment summary.",
    supportedFormats: ["CSV"],
    permission: "REPORT_STUDENT_READ",
    filterSchema: ["studentId"],
    executionMode: "SYNC",
    enabled: true,
    ...overrides,
  };
}

const ENTRIES: ReportCatalogEntry[] = [
  entry(),
  entry({
    reportType: "STUDENT_ATTEMPTS",
    displayName: "Student Attempts",
    description: "Every attempt for a student.",
    supportedFormats: ["CSV", "PDF"],
    filterSchema: ["studentId", "from", "to"],
  }),
  entry({
    reportType: "ASSESSMENT_ATTEMPTS",
    category: "ASSESSMENT",
    displayName: "Assessment Attempts",
    description: "Attempts per assessment version.",
    executionMode: "ASYNC_PREFERRED",
    filterSchema: ["from", "to"],
  }),
  entry({
    reportType: "CONTENT_VERSION_STATUS",
    category: "CONTENT",
    displayName: "Version Status",
    description: "Question versions by review status.",
    filterSchema: [],
  }),
  entry({
    reportType: "CONTENT_TAG_COVERAGE",
    category: "CONTENT",
    displayName: "Tag Coverage",
    description: "Questions per tag.",
    enabled: false,
  }),
];

describe("ReportCatalog", () => {
  it("renders every advertised entry with its formats, execution mode and category group", () => {
    render(<ReportCatalog entries={ENTRIES} selectedType={null} onSelect={() => {}} />);

    expect(screen.getByRole("heading", { name: "Report catalog" })).toBeInTheDocument();
    for (const item of ENTRIES) {
      expect(screen.getByText(item.displayName)).toBeInTheDocument();
      expect(screen.getByText(item.description ?? "")).toBeInTheDocument();
    }

    expect(screen.getByRole("heading", { name: /^Student$/ })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^Assessment$/ })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^Content$/ })).toBeInTheDocument();

    expect(screen.getByText("CSV, PDF")).toBeInTheDocument();
    expect(screen.getAllByText("Large reports are queued").length).toBe(1);
    expect(screen.getAllByText("Runs immediately").length).toBe(4);
  });

  it("filters to one category", async () => {
    const user = userEvent.setup();
    render(<ReportCatalog entries={ENTRIES} selectedType={null} onSelect={() => {}} />);

    await user.selectOptions(screen.getByLabelText("Category"), "ASSESSMENT");

    expect(screen.getByText("Assessment Attempts")).toBeInTheDocument();
    expect(screen.queryByText("Student Profile")).toBeNull();
    expect(screen.queryByText("Tag Coverage")).toBeNull();
  });

  it("makes a disabled entry unavailable and unselectable", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(<ReportCatalog entries={ENTRIES} selectedType={null} onSelect={onSelect} />);

    const disabledItem = screen.getByText("Tag Coverage").closest("li");
    expect(disabledItem).not.toBeNull();
    expect(disabledItem).toHaveAttribute("data-enabled", "false");
    expect(
      within(disabledItem as HTMLElement).getByText(
        /Not available yet — this report type has no data provider/i,
      ),
    ).toBeInTheDocument();
    // No select control exists at all, so it cannot be actioned by button or by keyboard.
    expect(within(disabledItem as HTMLElement).queryByRole("button")).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();

    // Enabled entries still have a working select action.
    const enabledItem = screen.getByText("Student Profile").closest("li") as HTMLElement;
    await user.click(within(enabledItem).getByRole("button", { name: "Select" }));
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect.mock.calls[0]?.[0]).toMatchObject({ reportType: "STUDENT_PROFILE" });
  });

  it("marks the selected entry and reports the selection on click", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(
      <ReportCatalog entries={ENTRIES} selectedType="STUDENT_ATTEMPTS" onSelect={onSelect} />,
    );

    const selectedItem = screen.getByText("Student Attempts").closest("li") as HTMLElement;
    expect(selectedItem).toHaveAttribute("aria-current", "true");
    expect(within(selectedItem).getByRole("button", { name: "Selected" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    await user.click(within(selectedItem).getByRole("button", { name: "Selected" }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ reportType: "STUDENT_ATTEMPTS" }));
  });

  it("shows an empty state when the caller has no reports", () => {
    render(<ReportCatalog entries={[]} selectedType={null} onSelect={() => {}} />);
    expect(screen.getByRole("note")).toHaveTextContent("No reports are available to you.");
  });
});
