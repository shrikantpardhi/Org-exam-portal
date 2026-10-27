import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PublicLayout } from "@/components/public/public-layout";

/**
 * The public footer.
 *
 * <p>The landing page advertised Terms, Privacy and Support, but only `/docs` and its `[slug]`
 * articles are bundled — there is no `/docs/terms`, `/docs/privacy` or `/docs/support` page, so all
 * three links 404'd. This pins every footer destination to a route the app actually serves.
 */
describe("PublicLayout footer", () => {
  it("points every documentation link at the docs index", () => {
    render(
      <PublicLayout>
        <p>page</p>
      </PublicLayout>,
    );

    const footer = screen.getByRole("contentinfo");
    for (const label of ["Terms", "Privacy", "Support", "Docs"]) {
      // "Docs" also appears in the header nav, so every lookup is scoped to the footer.
      const link = within(footer).getByRole("link", { name: label });
      expect(link).toHaveAttribute("href", "/docs");
    }
  });

  it("never links to a docs sub-page that is not bundled", () => {
    render(
      <PublicLayout>
        <p>page</p>
      </PublicLayout>,
    );

    for (const dead of ["/docs/terms", "/docs/privacy", "/docs/support"]) {
      expect(document.querySelector(`a[href="${dead}"]`)).toBeNull();
    }
  });

  it("keeps the header sign-in and brand links intact", () => {
    render(
      <PublicLayout>
        <p>page</p>
      </PublicLayout>,
    );

    const banner = screen.getByRole("banner");
    expect(within(banner).getByRole("link", { name: "Sign in" })).toHaveAttribute(
      "href",
      "/login",
    );
    expect(within(banner).getByRole("link", { name: "Exam Platform" })).toHaveAttribute(
      "href",
      "/",
    );
  });
});
