# ADR-0050 - Semantic design tokens and a light-only theme

- **Status:** Accepted
- **Date:** 2026-10-26
- **Supersedes:** the implicit dual-theme implementation described in `docs/frontend/navigation.md`
- **Related:** [`specs/Design-Theme.md`](../specs/Design-Theme.md) §21, ADR-0037

## Context

The frontend shipped two contradictory theme systems at once:

1. `app/globals.css` declared `color-scheme: light` and a light palette.
2. `components/theme/theme-toggle.tsx` let a user put a `dark` class on `<html>`, and 168 `dark:`
   variants across 19 files responded to it.

Because `color-scheme` was pinned to light, the "dark" theme was never a validated mode: native
controls (scrollbars, date pickers, form widgets) stayed light while component surfaces turned dark.
It was also untested — no test, screenshot, or review covered it.

`specs/Design-Theme.md` §21 states the governing rule directly: *"No dark mode until validated. If the
runtime is only validated in one mode at launch, only that mode is offered."*

Separately, 1139 raw palette literals (`bg-slate-100`, `text-red-600`, ...) were spread across the
frontend, so a palette change meant editing dozens of files and any new screen could silently pick a
slightly different grey.

## Decision

1. **Semantic tokens.** `app/globals.css` defines all colour as HSL custom properties on `:root`
   (`--background`, `--surface`, `--border`, `--input`, `--ring`, `--foreground`, `--muted`,
   `--primary`, `--destructive`, `--success`, `--warning`, `--info`, each with `-foreground` /
   `-subtle` / `-hover` variants where meaningful). `tailwind.config.ts` maps them into the `colors`
   scale, so utilities like `bg-surface` and `text-muted-foreground` exist and no component needs a
   literal.
2. **Light only.** The toggle is deleted, `darkMode` is removed from the Tailwind config, all 168
   `dark:` variants are stripped, the root layout no longer injects a pre-hydration theme script, and
   `lib/theme.ts` exposes a constant `THEME = "light"` instead of a preference. `color-scheme: light`
   now agrees with what is actually rendered.
3. **Shared primitives.** `components/ui/` grew from 2 to 12 components so colour and spacing
   decisions are made once, not per screen.

## Rationale for removing rather than fixing dark mode

Fixing it would mean: validating a second palette, auditing every screen in both modes, verifying
contrast in both, and maintaining both forever. That is a real feature with real cost, and nothing in
V1.5 requires it. The specification explicitly permits offering only the validated mode. Removing an
unvalidated, contradictory feature is smaller, safer, and honest; shipping a toggle that half-works is
worse than not shipping one.

## Consequences

- One place to change colour. A brand palette change is an edit to `:root`, not a sweep.
- Native controls and component surfaces finally agree.
- The exam runtime is unchanged: `specs/Design-Theme.md` §21 also requires no gradients and 44×44 px
  palette cells, and both were corrected in the same pass (the exam palette cells were 36 px).
- **Migration is incomplete.** About 800 module-level palette literals remain in feature components
  (GAP-189). They are functional, and the tokens now exist so each module can migrate independently.
- A future dark mode must be validated first, then re-introduced deliberately — that is the point of
  the rule, not an accident of this decision.
