import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Alert } from "./alert";
import { Badge } from "./badge";
import { EmptyState } from "./empty-state";
import { Input } from "./input";
import { Label } from "./label";
import { PageHeader } from "./page-header";
import { Spinner } from "./spinner";
import { TableWrapper, Table, THead, TBody, TR, TH, TD } from "./table";

/**
 * The shared UI primitives.
 *
 * <p>These assert the *contracts that matter for accessibility*, not the class strings: the role an
 * assistive technology receives, the association between a label and its control, and the fact that a
 * wide table is still reachable. Class-string assertions would break on every design tweak and prove
 * nothing about the user experience.
 */
describe("Alert", () => {
  it("interrupts for danger and stays quiet otherwise", () => {
    const { rerender } = render(<Alert tone="danger">Failed</Alert>);
    expect(screen.getByRole("alert")).toHaveTextContent("Failed");

    rerender(<Alert tone="success">Saved</Alert>);
    expect(screen.getByRole("status")).toHaveTextContent("Saved");
  });

  it("renders an optional title", () => {
    render(
      <Alert tone="warning" title="Heads up">
        Details
      </Alert>,
    );
    expect(screen.getByText("Heads up")).toBeInTheDocument();
    expect(screen.getByText("Details")).toBeInTheDocument();
  });
});

describe("Badge", () => {
  it("always renders its text label, never colour alone", () => {
    render(<Badge variant="success">Active</Badge>);
    expect(screen.getByText("Active")).toBeInTheDocument();
  });
});

describe("EmptyState", () => {
  it("explains the state and offers an action", () => {
    render(<EmptyState title="No assessments" description="Nothing scheduled yet." action={<button>Create</button>} />);
    expect(screen.getByText("No assessments")).toBeInTheDocument();
    expect(screen.getByText("Nothing scheduled yet.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create" })).toBeInTheDocument();
  });
});

describe("Spinner", () => {
  it("announces progress to a screen reader", () => {
    render(<Spinner label="Loading results" />);
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.getByText("Loading results")).toBeInTheDocument();
  });
});

describe("Label and Input", () => {
  it("associates a label with its control", () => {
    render(
      <>
        <Label htmlFor="email">Email</Label>
        <Input id="email" />
      </>,
    );
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("announces an invalid field", () => {
    render(<Input id="code" aria-invalid aria-describedby="code-error" />);
    expect(screen.getByRole("textbox")).toHaveAttribute("aria-invalid", "true");
  });
});

describe("PageHeader", () => {
  it("renders a single h1 with its description and actions", () => {
    render(<PageHeader title="Users" description="Everyone in the organization." actions={<button>New</button>} />);
    expect(screen.getByRole("heading", { level: 1, name: "Users" })).toBeInTheDocument();
    expect(screen.getByText("Everyone in the organization.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New" })).toBeInTheDocument();
  });
});

describe("Table", () => {
  it("exposes a labelled, keyboard-focusable scroll region", () => {
    render(
      <TableWrapper label="Assessments">
        <Table>
          <THead>
            <TR>
              <TH>Title</TH>
            </TR>
          </THead>
          <TBody>
            <TR>
              <TD>Midterm</TD>
            </TR>
          </TBody>
        </Table>
      </TableWrapper>,
    );

    const region = screen.getByRole("region", { name: "Assessments" });
    expect(region).toHaveAttribute("tabindex", "0");
    expect(screen.getByRole("columnheader", { name: "Title" })).toHaveAttribute("scope", "col");
    expect(screen.getByRole("cell", { name: "Midterm" })).toBeInTheDocument();
  });
});
