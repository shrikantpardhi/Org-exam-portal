import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { extractHeadings, renderMarkdown, slugify } from "@/lib/markdown";

describe("slugify", () => {
  it("lowercases, strips punctuation, and hyphenates", () => {
    expect(slugify("Hello World")).toBe("hello-world");
    expect(slugify("Roles & Permissions")).toBe("roles-permissions");
  });
});

describe("extractHeadings", () => {
  it("returns heading text with slugs and levels", () => {
    const markdown = "# Title\n\n## Section One\n\n### Sub";
    expect(extractHeadings(markdown)).toEqual([
      { id: "title", text: "Title", level: 1 },
      { id: "section-one", text: "Section One", level: 2 },
      { id: "sub", text: "Sub", level: 3 },
    ]);
  });

  it("ignores headings inside fenced code blocks", () => {
    const markdown = "# Real\n\n```\n# not a heading\n```";
    expect(extractHeadings(markdown)).toEqual([{ id: "real", text: "Real", level: 1 }]);
  });
});

describe("renderMarkdown", () => {
  it("renders headings, bold, lists, and code blocks", () => {
    const { container } = render(<>{renderMarkdown("# Hi\n\nSome **bold** text.\n\n- one\n- two\n")}</>);
    expect(container.querySelector("h1")?.textContent).toBe("Hi");
    expect(container.textContent).toContain("bold");
    expect(container.querySelectorAll("li")).toHaveLength(2);
  });

  it("renders raw HTML as text, never as elements", () => {
    const { container } = render(<>{renderMarkdown("<script>alert(1)</script>")}</>);
    expect(container.querySelector("script")).toBeNull();
  });

  it("renders links with parsed label", () => {
    const { container } = render(<>{renderMarkdown("[Getting started](/docs/getting-started)")}</>);
    const link = container.querySelector("a");
    expect(link?.getAttribute("href")).toBe("/docs/getting-started");
    expect(link?.textContent).toBe("Getting started");
  });
});
