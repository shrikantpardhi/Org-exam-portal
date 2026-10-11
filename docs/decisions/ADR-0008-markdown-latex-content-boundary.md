# ADR-0008: Markdown-LaTeX content boundary

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/01-Master-PRD-V1.5.md §13`,
  `specs/README.md` (Content representation), `specs/09-P0-Foundation-V1.5.md §139`

## Decision

All renderable examination content uses the single contract `MARKDOWN_LATEX_V1`:

- Governed fields: `question_version.content`, `question_option.content`,
  `explanation.content`, `hint.content`.
- Inline math: `\( ... \)`.
- Block math: `\[ ... \]`.
- `$...$` and `$$...$$` are **rejected**; dollar signs remain ordinary text.
- Raw HTML is rejected at authoring; executable MDX is prohibited.
- Assets use `[asset: <uuid>]` shortcodes resolved server-side with authorization; the asset
  must belong to the same `question_version`.
- Renderer: KaTeX **server-side** rendering (no MathJax in the student delivery path).

## P0 scope

P0 establishes the package boundary, the security dependency boundary, and the test-fixture
location only. It does not implement the full question renderer, which belongs to the Content
slice. Markdown/LaTeX content is never trusted as HTML — sanitization is mandatory before
serialization.

## Consequences

- Content rendering is one pipeline, not several.
- A future change to the content contract is a versioned contract change, not a local edit.
