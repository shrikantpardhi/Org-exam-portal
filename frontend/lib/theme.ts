/**
 * Theme policy (ADR-0050).
 *
 * <p>The application is deliberately **light-only**. An examination screen must look the same for
 * every candidate regardless of an OS dark-mode preference: a half-themed question area or a timer
 * that loses contrast is an integrity problem, not a preference. `globals.css` pins
 * `color-scheme: light` and Tailwind is configured without a `dark` strategy, so no `dark` class is
 * ever applied.
 *
 * <p>This module exists so the decision is stated once and can be asserted in a test, rather than
 * being implied by the absence of a toggler.
 */
export const THEME = "light" as const;

/** The only supported colour scheme. Kept for callers that need to state it explicitly. */
export type ThemePreference = typeof THEME;

/** Always true: the UI is light-only. */
export function isLightTheme(): boolean {
  return true;
}
