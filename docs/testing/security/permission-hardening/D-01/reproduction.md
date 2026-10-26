# D-01 — SUPER_ADMIN lacks ROLE_VIEW / PERMISSION_VIEW

| Field | Value |
|---|---|
| Defect | D-01 |
| Severity | CRITICAL |
| Status | **Confirmed — real** |
| Reproduced | 2026-10-26, against the running local stack (edge `http://localhost:8081`) |
| Frozen contract | `specs/10-P1-Identity-V1.5.md` §68 (authorization matrix: `roles LIST` requires `ROLE_VIEW`, `permissions GET` requires `PERMISSION_VIEW`); `docs/frontend/super-admin-flow.md` §12–13 (seeds screens are read-only views of both catalogues) |
| Contract conflict | ADR-0032 Consequences: "The platform permission catalogue (`PLATFORM_*`) is granted to `SUPER_ADMIN` only" — read together with `V29` granting **exactly and only** the eight `PLATFORM_*` codes |

## Reproduction

~~~text
POST /api/v1/auth/login  {username: superadmin@platform.test, portal: SYSTEM_ADMIN}   -> 200

GET /api/v1/permissions?size=500   Authorization: Bearer <SUPER_ADMIN>   -> 403
GET /api/v1/roles?size=100         Authorization: Bearer <SUPER_ADMIN>   -> 403
~~~

Control (same endpoints, ADMIN principal):

~~~text
GET /api/v1/permissions?size=500   Authorization: Bearer <ADMIN>          -> 200 (163 total, page of 100)
GET /api/v1/roles?size=100         Authorization: Bearer <ADMIN>          -> 200 (7 roles)
~~~

## Observed vs expected

| | Observation |
|---|---|
| Observed | `SUPER_ADMIN` effective permission set is exactly the 8 `PLATFORM_*` codes. Both seeds screens 403. |
| Expected | The seeds screens render the role catalogue and the permission catalogue, read-only. |

Verified against the live row set:

~~~sql
SELECT p.code FROM permission p
JOIN role_permission rp ON rp.permission_id = p.id AND rp.status = 'ACTIVE'
JOIN role r ON r.id = rp.role_id
WHERE r.code = 'SUPER_ADMIN' ORDER BY p.code;
-- 8 rows, all PLATFORM_*
~~~

## Root cause

`V29__super_admin_and_platform.sql:50-59` attaches only the eight `PLATFORM_*` codes to the
`SUPER_ADMIN` role. `RoleController.list` (`RoleController.java:51`) and
`PermissionController.list` (`PermissionController.java:39`) are guarded by `ROLE_VIEW` and
`PERMISSION_VIEW`, which `SUPER_ADMIN` does not hold.

`backend/src/main/java/com/company/exam/identity/controller/RoleController.java:51`
`backend/src/main/java/com/company/exam/identity/controller/PermissionController.java:39`
`backend/src/main/resources/db/migration/V29__super_admin_and_platform.sql:54-57`

## Blast radius

`/platform/seeds/roles` and `/platform/seeds/permissions` are reachable routes
(`frontend/app/platform/seeds/roles/page.tsx`, `frontend/app/platform/seeds/permissions/page.tsx`)
that 403 on load. The pages themselves are permission-gated on `PLATFORM_SYSTEM_VIEW`, so the
*screen* opens and the *query* fails — a broken screen, not a blocked route.

## Residual if not fixed

Two documented screens cannot work. The install owner is the only principal who can see the
whole catalogue, and cannot see it.

## Fix class

Config (one migration). No ADR required beyond a one-sentence clarification of ADR-0032's
"PLATFORM_* only" sentence, because that sentence and the seeds-screen requirement are in
conflict.

## Gate

None. In-scope for Task 1.