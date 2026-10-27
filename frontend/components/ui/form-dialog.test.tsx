import { render, screen } from "@testing-library/react";
import type { ComponentProps } from "react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { FormDialog } from "@/components/ui/form-dialog";

/**
 * The shared form dialog.
 *
 * <p>These are the guarantees a call site depends on and cannot re-prove cheaply: the dialog is
 * labelled and modal, Escape cancels, focus starts inside it rather than back at the top of the
 * document, and a field-less Enter still submits through the real form element.
 */
function renderDialog(
  props: Partial<ComponentProps<typeof FormDialog>> = {},
  fields = <input aria-label="Name" defaultValue="" />,
) {
  const onCancel = vi.fn();
  const onSubmit = vi.fn();
  const result = render(
    <FormDialog
      open
      title="Edit roles"
      description="Select any number of roles."
      submitLabel="Save roles"
      onCancel={onCancel}
      onSubmit={onSubmit}
      {...props}
    >
      {fields}
    </FormDialog>,
  );
  return { ...result, onCancel, onSubmit };
}

describe("FormDialog", () => {
  it("renders nothing while closed", () => {
    render(
      <FormDialog open={false} title="Edit roles" onCancel={vi.fn()} onSubmit={vi.fn()}>
        <input aria-label="Name" />
      </FormDialog>,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("is a labelled, modal dialog carrying the title and description", () => {
    renderDialog();

    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    // The accessible name comes from the heading, and the description is announced with it.
    expect(dialog).toHaveAccessibleName("Edit roles");
    expect(dialog).toHaveAccessibleDescription("Select any number of roles.");
    expect(screen.getByRole("heading", { name: "Edit roles" })).toBeInTheDocument();
  });

  it("moves focus into the first field when it opens", () => {
    renderDialog();
    expect(screen.getByLabelText("Name")).toHaveFocus();
  });

  it("submits through the form so a click on the submit button calls onSubmit", async () => {
    const user = userEvent.setup();
    const { onSubmit, onCancel } = renderDialog();

    await user.click(screen.getByRole("button", { name: "Save roles" }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("keeps a real form element so Enter in a field submits", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderDialog();

    const field = screen.getByLabelText("Name");
    await user.click(field);
    await user.keyboard("{Enter}");

    expect(onSubmit).toHaveBeenCalled();
    expect(screen.getByRole("dialog").querySelector("form")).not.toBeNull();
  });

  it("cancels on Escape and on the Cancel button", async () => {
    const user = userEvent.setup();
    const { onCancel } = renderDialog();

    await user.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onCancel).toHaveBeenCalledTimes(2);
  });

  it("disables both actions and relabels submit while busy", () => {
    renderDialog({ busy: true });

    expect(screen.getByRole("button", { name: "Working…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
  });

  it("shows the caller's children as the field body", () => {
    renderDialog({}, <input aria-label="Field" />);
    expect(screen.getByLabelText("Field")).toBeInTheDocument();
  });
});
