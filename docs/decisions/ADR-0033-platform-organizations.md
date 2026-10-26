# ADR-0033 — PLATFORM system organization and organization creation from SUPER_ADMIN

- **Status:** Accepted
- **Phase:** SUPER_ADMIN / ADMIN UI (amendment raised by the owner's "create organization from the UI" requirement)
- **Spec:** `specs/10-P1-Identity-V1.5.md` §7; `specs/20-P11-Administration-V1.5.md` §8
- **Migration:** `V29__super_admin_and_platform.sql`

## Context

The frozen deployment model is single-organization (`10-P1 §7`): every user belongs
to exactly one `organization`, and there is no create-organization surface. The owner
requires that `SUPER_ADMIN`, on first login, creates the first business organization
and its `ADMIN`. Those two facts collide: a `SUPER_ADMIN` user must belong to *some*
organization (the column is `NOT NULL`), but before the first business organization
exists there is none.

## Decision

Introduce a `PLATFORM` **system organization**:

- `V29` seeds `organization` with `code = 'PLATFORM'`, `name = 'Platform'`,
  `status = 'ACTIVE'`.
- The `SUPER_ADMIN` user belongs to `PLATFORM`.
- `PLATFORM` is **filtered out of every business-organization list endpoint** — it is a
  holder, not a tenant. `GET /api/v1/platform/organizations` and every business list
  exclude `code = 'PLATFORM'`.
- A new SUPER_ADMIN-only surface creates business organizations:
  `POST /api/v1/platform/organizations`, `GET /api/v1/platform/organizations`,
  `GET /api/v1/platform/organizations/{id}`, and
  `POST /api/v1/platform/organizations/{id}/admins`.

Multi-organization remains a V2 concern: the pilot runs with exactly one business
organization, and `/platform/multi-org` is a disabled placeholder.

## Rejected alternative

- **Bootstrap creates the business organization.** Rejected: the owner explicitly
  assigned business-organization creation to `SUPER_ADMIN` on first login, not to a
  script. A scripted business org would also bake a tenant name into the deployment
  image, which is an operator error waiting to happen.

## Consequences

- `SUPER_ADMIN` is seeded into `PLATFORM`; the bootstrap CLI must resolve (or create)
  `PLATFORM` before creating the user.
- Business-org list endpoints must filter `PLATFORM`; a detail lookup of `PLATFORM`
  through the business surface returns 404.
- Single-org invariant (`10-P1 §7`) is preserved for the pilot; only the
  SUPER_ADMIN-only platform surface sees more than one row.

## Rollback

Remove the `PLATFORM` row only after any `SUPER_ADMIN` user has been reassigned or
removed (the `user.organization_id` foreign key forbids deleting a referenced row).
This ADR records that dependency for the rollback procedure.
