import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { BlueprintSectionEditor } from "@/features/assessment/components/blueprint-section-editor";
import {
  newSectionKey,
  type BlueprintSectionFormValue,
} from "@/features/assessment/schemas/blueprint-config";

/**
 * Section editor behaviour ({@code specs/13-P4-Assessment-V1.5.md §12-§14, §99}).
 *
 * <p>Rows are added, removed, and reordered; `displayOrder` stays ascending and the operator's typed
 * names survive the reorder. A name that looks like an exam name is ordinary text — the editor has no
 * branch to react to it.
 */
function Harness({ initial = [] }: { initial?: BlueprintSectionFormValue[] }) {
  const [sections, setSections] = useState(initial);
  return <BlueprintSectionEditor sections={sections} onChange={setSections} />;
}

function row(name: string, displayOrder: number): BlueprintSectionFormValue {
  return {
    key: newSectionKey(),
    name,
    displayOrder,
    questionCount: 10,
    marks: 10,
    durationMinutes: 10,
  };
}

describe("BlueprintSectionEditor", () => {
  it("adds a named section and keeps displayOrder ascending", async () => {
    render(<Harness />);

    expect(screen.queryAllByTestId("blueprint-section-row")).toHaveLength(0);

    await userEvent.click(screen.getByRole("button", { name: "Add section" }));
    await userEvent.click(screen.getByRole("button", { name: "Add section" }));

    const rows = screen.getAllByTestId("blueprint-section-row");
    expect(rows).toHaveLength(2);

    await userEvent.type(screen.getByLabelText("Section 1 name"), "Reasoning");
    await userEvent.type(screen.getByLabelText("Section 2 name"), "Physics");

    expect(screen.getByLabelText("Section 1 name")).toHaveValue("Reasoning");
    expect(screen.getByLabelText("Section 2 name")).toHaveValue("Physics");
    expect(screen.getByLabelText("Section 1 display order")).toHaveValue(1);
    expect(screen.getByLabelText("Section 2 display order")).toHaveValue(2);
  });

  it("reorders rows while preserving typed names and renumbering orders", async () => {
    render(<Harness initial={[row("Reasoning", 1), row("Physics", 2)]} />);

    await userEvent.click(screen.getByRole("button", { name: "Move section 2 up" }));

    expect(screen.getByLabelText("Section 1 name")).toHaveValue("Physics");
    expect(screen.getByLabelText("Section 2 name")).toHaveValue("Reasoning");
    expect(screen.getByLabelText("Section 1 display order")).toHaveValue(1);
    expect(screen.getByLabelText("Section 2 display order")).toHaveValue(2);
  });

  it("removes a row and renumbers the remainder", async () => {
    render(<Harness initial={[row("Reasoning", 1), row("Physics", 2), row("Chemistry", 3)]} />);

    await userEvent.click(screen.getByRole("button", { name: "Remove section 2" }));

    expect(screen.getAllByTestId("blueprint-section-row")).toHaveLength(2);
    expect(screen.getByLabelText("Section 1 name")).toHaveValue("Reasoning");
    expect(screen.getByLabelText("Section 2 name")).toHaveValue("Chemistry");
    expect(screen.getByLabelText("Section 2 display order")).toHaveValue(2);
    expect(screen.queryByLabelText("Section 3 name")).not.toBeInTheDocument();
  });

  it("treats an exam-looking name as ordinary text", async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole("button", { name: "Add section" }));
    await userEvent.type(screen.getByLabelText("Section 1 name"), "IBPS");

    // Nothing about the value changes the editor's structure: still exactly one editable row.
    expect(screen.getByLabelText("Section 1 name")).toHaveValue("IBPS");
    expect(screen.getAllByTestId("blueprint-section-row")).toHaveLength(1);
    // Every other field is still present and independent of the name.
    expect(screen.getByLabelText("Section 1 question count")).toBeInTheDocument();
    expect(screen.getByLabelText("Section 1 marks")).toBeInTheDocument();
    expect(screen.getByLabelText("Section 1 duration minutes")).toBeInTheDocument();
  });
});
