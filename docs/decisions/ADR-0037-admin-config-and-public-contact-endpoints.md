# ADR-0037 — ADMIN org-scoped config and public contact endpoints

- **Status:** Accepted
- **Phase:** SUPER_ADMIN / ADMIN UI + landing page (amendment)
- **Spec:** `specs/05-API-Contract-V1.5.md` (new §73)
- **Closes:** — (new non-platform endpoints for the ADMIN config surface and the landing contact form)
- **Migration:** none (reuses `organization_setting`, `business_rule`, `lookup`, `background_job`, and the email pipeline)

## Context

The ADMIN config surface needs an organization-scoped, read-only configuration read, and the
landing page contact form needs a public submission path. Neither exists in the frozen contract.
The ADMIN surface must not reuse the SUPER_ADMIN-only `GET /platform/system` (which leaks
installation-level detail), and the contact form must not invent a business table.

## Decision

1. **`GET /api/v1/admin/organization-config`** — ADMIN-only (`ADMIN` role), read-only. Returns the
   caller's organization-scoped configuration composed from existing sources:
   `{ organization: {name, code, timezone}, featureFlags, providerStatus,
      lastMigrationVersion, appVersion }`. No secrets, no database internals, no container status.
2. **`POST /api/v1/public/contact`** — public (no authentication), rate-limited per source IP.
   Body `{ name, email, message, organization? }`, validated. The submission is handed off to the
   Communication email pipeline (support address) and/or recorded as a `background_job` of type
   `CONTACT_REQUEST`. It creates no user and writes no business data.

Both are additive; neither introduces a table nor weakens an existing boundary.

## Rejected alternatives

- **Reuse `GET /platform/system` for ADMIN.** Rejected: it is SUPER_ADMIN-only and exposes
  installation-level detail (git commit, dependencies, platform org) that an organization admin
  must not see.
- **Persist contact submissions in a new table.** Rejected: frozen rule "do not invent tables";
  the `background_job`/email handoff is sufficient for V1.5.

## Consequences

- The ADMIN config surface and the landing contact form are backed by real, tested endpoints.
- Contact submission is rate-limited and never blocks the request path (email/job handoff).

## Rollback

Remove the two endpoints; both are additive with no schema impact.
