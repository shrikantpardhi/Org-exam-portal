import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "@/components/admin/confirm-dialog";
import { DataTable } from "@/components/admin/data-table";
import { HealthCard } from "@/components/admin/health-card";

describe("admin components", () => {
  it("renders a health component with its status and latency", () => {
    render(
      <HealthCard
        component={{
          name: "database",
          status: "UP",
          latencyMs: 12,
          message: "Responding",
          detail: { role: "primary" },
        }}
      />,
    );

    const card = screen.getByRole("article", { name: "database" });
    expect(card).toHaveTextContent("Up");
    expect(card).toHaveTextContent("12 ms");
    expect(card).toHaveTextContent("primary");
  });

  it("confirms and cancels through the accessible dialog", () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <ConfirmDialog
        open
        title="Archive role"
        description="This changes role access."
        confirmLabel="Archive"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    const dialog = screen.getByRole("dialog", { name: "Archive role" });
    expect(dialog).toHaveAttribute("aria-modal", "true");

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    fireEvent.keyDown(window, { key: "Escape" });
    fireEvent.click(screen.getByRole("button", { name: "Archive" }));

    expect(onCancel).toHaveBeenCalledTimes(2);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("renders the table empty state and rows with column headers", () => {
    const { rerender } = render(
      <DataTable
        caption="Audit events"
        columns={[{ key: "action", header: "Action", render: (row: { action: string }) => row.action }]}
        rows={[]}
        getRowKey={(row) => row.action}
        emptyMessage="No audit events match the filter."
      />,
    );

    expect(screen.getByText("No audit events match the filter.")).toBeInTheDocument();

    rerender(
      <DataTable
        caption="Audit events"
        columns={[{ key: "action", header: "Action", render: (row: { action: string }) => row.action }]}
        rows={[{ action: "CREATE" }]}
        getRowKey={(row) => row.action}
        emptyMessage="No audit events match the filter."
      />,
    );

    expect(screen.getByRole("columnheader", { name: "Action" })).toBeInTheDocument();
    expect(screen.getByText("CREATE")).toBeInTheDocument();
  });
});
