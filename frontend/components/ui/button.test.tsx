import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Button } from "@/components/ui/button";

describe("Button", () => {
  it("renders an accessible button with an explicit type", () => {
    render(<Button>Save</Button>);

    const button = screen.getByRole("button", { name: "Save" });

    expect(button).toBeInTheDocument();
    expect(button).toHaveAttribute("type", "button");
  });

  it("exposes a disabled state to assistive technology", () => {
    render(<Button disabled>Save</Button>);

    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("applies variant styles without dropping custom classes", () => {
    render(<Button variant="outline" className="custom-class">Save</Button>);

    expect(screen.getByRole("button", { name: "Save" })).toHaveClass("custom-class");
  });
});
