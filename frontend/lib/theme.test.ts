import { describe, expect, it } from "vitest";

import { THEME, isLightTheme } from "@/lib/theme";

/**
 * The light-only policy (ADR-0050).
 *
 * <p>These assertions are deliberately trivial in isolation. Their value is that they fail loudly if
 * someone reintroduces a theme switcher: the constant is named, so a future dark mode has to delete a
 * test that says why it is not allowed rather than quietly adding a `<ThemeToggle />`.
 */
describe("theme policy", () => {
  it("is light-only", () => {
    expect(THEME).toBe("light");
    expect(isLightTheme()).toBe(true);
  });
});
