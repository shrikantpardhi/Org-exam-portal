import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { VersionPreview } from "@/features/content/components/version-preview";

describe("VersionPreview", () => {
  it("renders server-sanitized canonical HTML, preserving currency text and math placeholders", () => {
    const html =
      '<p>The cost is $500 and <span class="katex-tex" data-display="false" data-tex="x"></span> is math.</p>';

    render(<VersionPreview html={html} />);

    expect(screen.getByRole("document")).toHaveTextContent("The cost is $500");
    expect(screen.getByRole("document").querySelector(".katex-tex")).not.toBeNull();
  });

  it("shows a placeholder when there is no rendered content", () => {
    render(<VersionPreview html={null} />);

    expect(screen.getByText("No rendered content.")).toBeInTheDocument();
  });
});
