# ADR-0016: P3 content rendering boundary and decisions

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P3 — Content and Question Bank
- **Frozen source:** `specs/12-P3-Content-V1.5.md`, `specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md §40-§47`,
  `specs/04-Database-Schema-V1.5.md §27`

## Context

P3 owns canonical examination content and its rendering contract. The frozen baseline fixes
`MARKDOWN_LATEX_V1`, KaTeX server-side rendering, a `[asset: <uuid>]` shortcode contract, published
immutability, and a review workflow, but leaves several implementation choices open. This record
captures them.

## Decisions

**1. Delimiters and currency.** Only `\( ... \)` (inline) and `\[ ... \]` (block) are mathematical
delimiters. `$$...$$` is rejected with `UNSUPPORTED_MATH_DELIMITER`. A single `$...$` is **not**
treated as math and is preserved as ordinary text, so currency such as `$500` is never
misinterpreted.

**2. Raw HTML and executable MDX are rejected before persistence** with `RAW_HTML_NOT_ALLOWED` and
`INVALID_MARKDOWN`. Validation runs in the content pipeline (`§13, §14, §58`) and the renderer emits
only its own tags with all author text HTML-escaped, so unsafe markup cannot reach output.

**3. KaTeX SSR boundary.** The Java module enforces the content security contract and produces
sanitized HTML in which math is carried in controlled placeholders
(`<span class="katex-tex" data-display="…" data-tex="…">`). KaTeX server-side rendering is performed
at the **Next.js rendering boundary** (the server-side rendering tier of this stack), because KaTeX is
a JavaScript renderer and no Java KaTeX exists in the frozen stack. The renderer version is part of
the cache identity (`§62, §131, §47`) so output is reproducible. The editor preview and delivery use
the same renderer — there is no separate permissive editor renderer (`§56, §168`).

**4. Asset shortcodes.** `[asset: <uuid>]` is validated and resolved against assets of the same
QuestionVersion; a cross-version reference returns `ASSET_VERSION_MISMATCH`, a missing asset
`ASSET_NOT_FOUND`. The storage path is never exposed as an authorization primitive (`§42`). Protected
asset **streaming** is served by the frozen files endpoint delivered with Delivery (`§61`,
`08-CDUI §45`); P3 persists metadata and validates ownership.

**5. Review workflow.** DRAFT → IN_REVIEW → APPROVED/REJECTED → PUBLISHED, with REJECTED resubmittable.
Illegal transitions return 409. Self-review is blocked: an actor who submitted a version cannot
approve or reject it (`§21`).

**6. Publication gate and immutability.** Publication is a dedicated business action
(`QuestionPublicationService`) that validates template, content, options, correctness, taxonomy, and
language. Published versions are immutable; every mutation against a published version is rejected
and corrections require a new version (`§64-§67`).

**7. Draft version content is not editable in place.** The frozen endpoint inventory has no
`PUT` for version content, so changing canonical content creates a new version (`§24, §85`). Only
options, assets, explanation, and hint are mutable on a draft.

**8. Tag association** is exposed through `PUT /questions/{id}` with `tagIds`; there is no standalone
question-tag endpoint in the frozen contract. Tag master data remains Academic, verified through the
Academic contract (`§49, §50`).

**9. Template registry.** Template codes map to data-driven rules (`QuestionTypeRegistry`); an
unknown template blocks publication. JSONB configuration is parsed into a typed structure, not
scattered raw lookups (`§121-§124`).

**10. Migration numbering.** P1 used `V2`/`V3`, Academic `V4`/`V5`, so Content is
`V6__create_content_tables.sql` and `V7__seed_content_master_data.sql` (`GAP-016/GAP-039`).

**11. Permissions.** The `QUESTION_*` codes from `§128` are seeded as implementation candidates and
granted by role (ADMIN all; REVIEWER read+review; STAFF authoring) (`GAP-036`).

## Consequences

- The frozen schema and API surface are preserved; no table or column is added.
- Rendering safety is enforced server-side and is independent of the client.
- Published content is immutable and reproducible; corrections create new versions.
