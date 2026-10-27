"use client";

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { DataGrid, PAGE_SIZE_OPTIONS, type DataGridColumn } from "@/components/ui/data-grid";

interface Row {
  id: string;
  name: string;
}

const rows: Row[] = [
  { id: "1", name: "Alpha" },
  { id: "2", name: "Beta" },
];

const columns: DataGridColumn<Row>[] = [
  { key: "name", header: "Name", sortField: "name", render: (row) => row.name },
];

function renderGrid(overrides: Partial<React.ComponentProps<typeof DataGrid<Row>>> = {}) {
  const props = {
    caption: "Test rows",
    columns,
    rows,
    getRowKey: (row: Row) => row.id,
    emptyMessage: "Nothing here",
    page: 0,
    pageCount: 1,
    pageSize: 10 as const,
    onPageSizeChange: vi.fn(),
    onPageChange: vi.fn(),
    totalElements: 2,
    ...overrides,
  };
  return { props, ...render(<DataGrid {...props} />) };
}

describe("DataGrid", () => {
  it("renders rows in a semantic table with a caption", () => {
    renderGrid();
    expect(screen.getByRole("table", { name: "Test rows" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: /Name/ })).toBeInTheDocument();
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText("Beta")).toBeInTheDocument();
  });

  it("offers exactly the 10/50/100 page sizes and reports a change", async () => {
    const user = userEvent.setup();
    const { props } = renderGrid();
    const select = screen.getByLabelText("Rows per page");

    // The option set is the requirement: a client-side-only "show all" would defeat the server
    // paging these lists depend on.
    expect(PAGE_SIZE_OPTIONS).toEqual([10, 50, 100]);
    expect(screen.getAllByRole("option").map((option) => option.getAttribute("value"))).toEqual([
      "10",
      "50",
      "100",
    ]);

    await user.selectOptions(select, "50");
    expect(props.onPageSizeChange).toHaveBeenCalledWith(50);
  });

  it("shows the visible range and disables Previous on the first page", async () => {
    const user = userEvent.setup();
    const { props } = renderGrid({ totalElements: 120, page: 0, pageCount: 12 });

    expect(screen.getByText("Showing 1–2 of 120")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(props.onPageChange).toHaveBeenCalledWith(1);
  });

  it("disables Next on the last page", () => {
    renderGrid({ page: 11, pageCount: 12, totalElements: 120 });
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    expect(screen.getByText("Page 12 of 12")).toBeInTheDocument();
  });

  it("exposes sort state through aria-sort and calls back on click", async () => {
    const user = userEvent.setup();
    const onSortChange = vi.fn();
    const { rerender } = renderGrid({ onSortChange, sort: { field: "name", direction: "asc" } });

    expect(screen.getByRole("columnheader", { name: /Name/ })).toHaveAttribute("aria-sort", "ascending");
    await user.click(screen.getByRole("button", { name: /Name/ }));
    expect(onSortChange).toHaveBeenCalledWith("name");

    rerender(
      <DataGrid
        caption="Test rows"
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.id}
        emptyMessage="Nothing here"
        page={0}
        pageCount={1}
        pageSize={10}
        onPageSizeChange={vi.fn()}
        onPageChange={vi.fn()}
        totalElements={2}
        sort={{ field: "name", direction: "desc" }}
        onSortChange={onSortChange}
      />,
    );
    expect(screen.getByRole("columnheader", { name: /Name/ })).toHaveAttribute("aria-sort", "descending");
  });

  it("renders loading, error and empty states", () => {
    const { rerender } = renderGrid({ loading: true });
    expect(screen.getByRole("status")).toHaveTextContent("Loading…");

    rerender(
      <DataGrid
        caption="Test rows"
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.id}
        emptyMessage="Nothing here"
        page={0}
        pageCount={1}
        pageSize={10}
        onPageSizeChange={vi.fn()}
        onPageChange={vi.fn()}
        totalElements={2}
        error="Could not load"
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Could not load");

    rerender(
      <DataGrid
        caption="Test rows"
        columns={columns}
        rows={[]}
        getRowKey={(row) => row.id}
        emptyMessage="Nothing here"
        page={0}
        pageCount={1}
        pageSize={10}
        onPageSizeChange={vi.fn()}
        onPageChange={vi.fn()}
        totalElements={0}
      />,
    );
    expect(screen.getByRole("note")).toHaveTextContent("Nothing here");
  });
});
