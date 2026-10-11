import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { MasterForm } from "@/features/academic/components/master-form";
import { examFormSchema } from "@/features/academic/schemas/academic";

const FIELDS = [
  { name: "code", label: "Code" },
  { name: "name", label: "Name" },
];

function renderForm(onSubmit: (values: Record<string, unknown>) => Promise<void>) {
  return render(
    <MasterForm schema={examFormSchema} fields={FIELDS} submitLabel="Create exam" onSubmit={onSubmit} />,
  );
}

describe("MasterForm", () => {
  it("validates required fields with Zod before submitting", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderForm(onSubmit);

    await userEvent.click(screen.getByRole("button", { name: /create exam/i }));

    expect(await screen.findByText("Code is required")).toBeInTheDocument();
    expect(await screen.findByText("Name is required")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("submits valid values", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderForm(onSubmit);

    await userEvent.type(screen.getByLabelText("Code"), "SSC-CGL");
    await userEvent.type(screen.getByLabelText("Name"), "SSC CGL");
    await userEvent.click(screen.getByRole("button", { name: /create exam/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0]?.[0]).toMatchObject({ code: "SSC-CGL", name: "SSC CGL" });
  });
});
