# ADR-0036 — Public documentation section at `/docs`

- **Status:** Accepted
- **Phase:** SUPER_ADMIN / ADMIN UI + documentation site (amendment)
- **Spec:** new public route (frontend surface); `docs/frontend/docs-site.md`
- **Closes:** — (new public docs surface; resolves conflict C8)
- **Migration:** none (frontend-only curated content)

## Context

The owner requires an in-product documentation section treated as the platform's official
documentation. The frozen spec has no `/docs` route, and the repository's internal `docs/`
(ADRs, gap register, testing notes) and `specs/` folders are engineering artefacts that must not
be published.

## Decision

`/docs` is a first-class **public** route served from curated markdown bundled with the frontend
at `frontend/content/docs/`. The published content is exactly:

- getting started (create an account, first login, first assessment)
- roles (SUPER_ADMIN, ADMIN, STAFF, REVIEWER, SUPPORT, FINANCE, STUDENT)
- API reference (generated from the OpenAPI document, browsable and searchable)
- configuration guide (organization settings, business rules, lookups, providers, feature flags)
- architecture overview (public-only: modular monolith, PostgreSQL, snapshot model, evaluation,
  AccessGrant)
- terms of service
- privacy policy
- support

The internal `docs/` and `specs/` folders are **not** published. New public docs pages require
an ADR update. Content is curated, versioned with each release, and searchable in-browser (no
external service).

## Rejected alternatives

- **Publish the internal `docs/` and `specs/` folders.** Rejected: they contain ADRs, the gap
  register, load-test results and internal decisions that are not user-facing documentation.
- **External documentation service.** Rejected: out of scope; the docs must live inside the UI and
  be versioned with the release.

## Consequences

- The docs render at `/docs` with a sidebar, table of contents, and full-text search.
- The API reference is generated from the backend OpenAPI document at build time.
- A version indicator ("Documentation for v1.5") anchors the section to the release.

## Rollback

Remove the `/docs` route and `frontend/content/docs/`; no backend or schema impact.
