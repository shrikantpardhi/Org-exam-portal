# Task 1 / D-01 — SUPER_ADMIN base catalogue read

**Status:** in progress (implementation complete, verification pending)

| Field | Value |
|---|---|
| Defect | D-01 |
| Fix class | Config — one forward migration, no ADR number claimed |
| Migration | `V32__grant_super_admin_catalogue_read.sql` |
| ADR | ADR-0032 amended (Consequences sentence), not superseded |
| Endpoints | none added, none changed |
| Frontend | none (the screens were already rendered; only their query 403'd) |

## What changed

| File | Change |
|---|---|
| `backend/src/main/resources/db/migration/V32__grant_super_admin_catalogue_read.sql` | **new** — grants `ROLE_VIEW` and `PERMISSION_VIEW` to the `SUPER_ADMIN` role, idempotently |
| `backend/src/test/java/com/company/exam/integration/identity/SuperAdminCatalogueReadIntegrationTest.java` | **new** — 3 tests: role list 200, permission list 200 (incl. a `PLATFORM_*` code), role authoring still 403 |
| `docs/decisions/ADR-0032-super-admin-role.md` | Consequences: "granted to `SUPER_ADMIN` only" clarified to include the two catalogue read permissions |
| `docs/frontend/super-admin-flow.md` | §12-§13 now name the exact permission each screen needs |

## Why a clarification and not a new ADR

ADR-0032's sentence "The platform permission catalogue (`PLATFORM_*`) is granted to `SUPER_ADMIN`
only" is a statement about **scope**, not about an exhaustive set. The seeds screens were specified
in the same change set (`super-admin-flow.md` §12-§13, both marked read-only, both requiring the
`PLATFORM_SYSTEM_VIEW` page gate). A contract that specifies a screen and withholds the permission
the screen's read path requires is internally inconsistent; the amendment resolves the inconsistency
in the direction the spec already intended. No behaviour outside the two read endpoints changes.

## What is deliberately NOT granted

`ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN`, `USER_VIEW`, `AUDIT_LOG_VIEW`,
`ANALYTICS_VIEW`, `SYSTEM_HEALTH_VIEW`.

The first three are Gate A's decision (Task 7 decides whether SUPER_ADMIN may author roles). The rest
belong to the `/admin/*` surface, which is not SUPER_ADMIN's surface. Task 6's catalogue test pins
all of it.
