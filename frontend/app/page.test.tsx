import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import LandingPage from "@/app/page";

/**
 * The public landing page.
 *
 * <p>Guards two things that are easy to erode: every call to action points at a route that exists, and
 * the page never claims a capability the build does not have. Invented statistics or testimonials are
 * the usual way a marketing page drifts away from the product.
 */
describe("landing page", () => {
  it("leads with the product positioning and a working sign-in action", () => {
    render(<LandingPage />);

    expect(
      screen.getByRole("heading", { level: 1, name: /run secure, large-scale examinations/i }),
    ).toBeInTheDocument();

    const signIn = screen.getAllByRole("link", { name: /sign in/i })[0];
    expect(signIn).toHaveAttribute("href", "/login");
  });

  it("links each portal to a real login route", () => {
    render(<LandingPage />);

    for (const href of ["/login/student", "/login/organization", "/login/admin"]) {
      expect(document.querySelector(`a[href="${href}"]`)).not.toBeNull();
    }
  });

  it("states that registration is not self-service, because it is not", () => {
    render(<LandingPage />);
    expect(screen.getByText(/no public self-registration/i)).toBeInTheDocument();
  });

  it("describes capabilities that exist rather than invented ones", () => {
    render(<LandingPage />);

    expect(screen.getByRole("heading", { name: /configurable examination engine/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /question bank with latex/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /server-authoritative delivery/i })).toBeInTheDocument();

    // The things a fabricated landing page tends to contain.
    expect(screen.queryByText(/testimonial/i)).toBeNull();
    expect(screen.queryByText(/trusted by \d/i)).toBeNull();
    expect(screen.queryByText(/million/i)).toBeNull();
  });

  it("has exactly one h1 and never skips a heading level on the way down", () => {
    render(<LandingPage />);
    const levels = screen.getAllByRole("heading").map((h) => Number(h.tagName.slice(1)));

    expect(levels.filter((level) => level === 1)).toHaveLength(1);

    // A jump from h2 straight to h4 breaks the document outline a screen-reader user navigates by.
    // Going *back up* (`3 -> 2`) is normal; only an increase of more than one level is a defect.
    levels.forEach((level, index) => {
      const previous = levels[index - 1];
      if (previous === undefined) return;
      expect(level - previous).toBeLessThanOrEqual(1);
    });
  });
});
