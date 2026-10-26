# Documentation Site — Design

In-product, public documentation at `/docs`, curated markdown bundled with the
frontend (ADR-0036). Internal `docs/` and `specs/` folders are **not** published.

## Route and layout

- **Route:** `/docs` and children. Public — no shell, no token.
- **Layout:** `components/docs/DocsLayout.tsx` — top header (logo · search · theme ·
  "Sign in"), a left **docs sidebar** (list of pages), the **content** region (rendered
  markdown), and a right **table of contents** of the current page's headings (hidden below
  1024 px).
- **Active page** is highlighted in the sidebar; the right TOC tracks the scroll position.

## Content pages (curated markdown)

Bundled under `frontend/content/docs/`, one `.md` per page:

| Route | Page |
|---|---|
| `/docs` | Overview / getting started (landing for the section) |
| `/docs/getting-started` | Getting started |
| `/docs/roles` | Roles (SUPER_ADMIN, ADMIN, STAFF, REVIEWER, SUPPORT, FINANCE, STUDENT) |
| `/docs/api` | API reference (generated from OpenAPI) |
| `/docs/configuration` | Configuration guide (settings, rules, lookups, providers, flags) |
| `/docs/architecture` | Architecture overview (public-only) |
| `/docs/terms` | Terms of service |
| `/docs/privacy` | Privacy policy |
| `/docs/support` | Support |

## Markdown handling

- Rendered from the same markdown pipeline the platform uses for content, **but without**
  the interactive/exam-specific features; no raw HTML, no executable MDX (frozen rule 17).
- The API reference page renders the generated OpenAPI document (browsable + searchable),
  built from the backend's OpenAPI output at build time.

## Version indicator

- `/docs` shows "Documentation for v1.5" (from the same version source as the footer),
  anchoring the section to the current release. The tag updates with each release, not per page.

## Search

- Full-text search over the bundled markdown only (client-side index built at build time).
- No external search service. Results link to the matching page + anchor.

## Edit link

- **None.** These are curated, release-versioned pages; there is no "edit on GitHub" link.

## Open questions

- None. New public docs pages require an ADR update (ADR-0036) before they are added.
