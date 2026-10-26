# Live re-verification — tasks 1-6 against the running stack

**Date:** 2026-10-26. **Stack:** `core` + `app` + `edge`, backend rebuilt from the working tree.
**Applied migrations:** 33, last = `V33__reserved_permission_set.sql` (confirmed by
`GET /api/v1/platform/system`).

Every defect reproduced in the pre-work was re-executed after the fix. The point is not that the
tests pass — they do — but that the **live deployment** now refuses what it used to allow.

## Results

| Defect | Pre-fix (recorded) | Post-fix (measured now) |
|---|---|---|
| D-01 | `GET /roles` → 403, `GET /permissions` → 403 for SUPER_ADMIN | `GET /roles` → **200**, `GET /permissions` → **200** |
| D-02 | `PUT /roles/{superAdminId}/permissions` → **200**, 5 of 8 grants deactivated | → **409 `ROLE_IMMUTABLE`**, set unchanged |
| D-03 | `PUT /roles/{staffId}/permissions` + `PLATFORM_ORGANIZATION_VIEW` → **200** | → **403 `RESERVED_PERMISSION_DENIED`**, STAFF still 63 |
| D-04 | `PUT /roles/{staffId}/permissions` + `ADMIN_ROLE_ASSIGN` → **200** | → **403 `PERMISSION_NOT_HELD`**, STAFF still 63 |
| D-07 | `PUT /roles/{ownAdminRoleId} {"status":"INACTIVE"}` → **200**, total lockout | → **403 `SELF_PERMISSION_MODIFICATION`**, role still ACTIVE |
| D-08 | `POST /roles` 201 then `PUT .../permissions {ADMIN_ROLE_ASSIGN}` → **200** | → **403 `PERMISSION_NOT_HELD`** (Task 4 closed step 1) |

## Commands actually run

~~~text
docker compose --profile core --profile app --profile edge build backend
docker compose --profile core --profile app --profile edge up -d backend

POST /api/v1/auth/login {superadmin@platform.test, SYSTEM_ADMIN}                 -> 200
GET  /api/v1/roles?size=100&sort=code                                            -> 200
GET  /api/v1/permissions?size=20&sort=code                                       -> 200
PUT  /api/v1/roles/{STAFF}  {"name":"X"}   (as SUPER_ADMIN)                     -> 403

POST /api/v1/auth/login {demo.admin2@example.test, ORGANIZATION_ADMIN}           -> 200
PUT  /api/v1/roles/{SUPER_ADMIN}/permissions  {"permissionIds":[]}              -> 409 ROLE_IMMUTABLE
PUT  /api/v1/roles/{ADMIN}  {"status":"INACTIVE"}                              -> 403 SELF_PERMISSION_MODIFICATION
PUT  /api/v1/roles/{STAFF}/permissions  {63 baseline + PLATFORM_ORGANIZATION_VIEW} -> 403 RESERVED_PERMISSION_DENIED
PUT  /api/v1/roles/{STAFF}/permissions  {63 baseline + ADMIN_ROLE_ASSIGN}        -> 403 PERMISSION_NOT_HELD
POST /api/v1/roles  {"code":"ELEV_LIVE",...}                                   -> 201
PUT  /api/v1/roles/{ELEV_LIVE}/permissions  {ADMIN_ROLE_ASSIGN}                  -> 403 PERMISSION_NOT_HELD
GET  /api/v1/platform/system                                                     -> migrationVersion 33
~~~

## State left behind

None of the post-fix calls mutated anything — that is what the guards do — so no restoration was
required. The one role created for the D-08 recheck (`ELEV_LIVE`) was deleted; the catalogue is back
to the 7 seeded roles. No Redis key needed manual eviction because no successful mutation occurred.

## What the live pass does NOT prove

- D-05, D-06, D-08 (step 2), D-10, D-11 are unchanged and still reproduce. They are behind Gate A and
  Gate B and nothing in tasks 1-6 was meant to touch them.
- `SUPER_ADMIN` role authoring still returns 403, which is the intended **undecided** state: Gate A
  question A2 decides whether it becomes 200.
- The frontend was not re-exercised in a browser; the two seeds screens serve from the same two
  endpoints that now return 200, but that is an inference, not an observation.
---

# Second pass — tasks 7-8 (Gate A), 2026-10-26

Backend rebuilt with `V34`. `GET /api/v1/platform/system` reports **migrationVersion 34**,
`V34__super_admin_authoring_and_recovery.sql`.

## Results

| Decision | Probe | Result |
|---|---|---|
| A1 | `POST /platform/organizations/{org}/admins/{user}/unlock-sessions` as an ADMIN | **403** |
| A1 | same path with a non-existent organization, as SUPER_ADMIN | **404** |
| A2 | `POST /roles` as SUPER_ADMIN (create) | **201** |
| A2 | `PUT /roles/{id}/permissions` as SUPER_ADMIN (assign) | **200** |
| A4 | `GET /platform/users` as SUPER_ADMIN | **200**, with `organizationId` + `organizationCode` + `roles` |
| A4 | `GET /platform/users` as an ADMIN | **403** |
| A4 | `GET /platform/users?organizationId=...` | **200**, `totalElements: 5009`, every row in that organization |

~~~text
GET  /api/v1/platform/system                      -> 200, migrationVersion 34
POST /api/v1/roles {code: LIVE_T7_..., name}      -> 201
PUT  /api/v1/roles/{LIVE_T7_...}/permissions      -> 200    (create-then-assign, no DTO change)
GET  /api/v1/platform/users?search=demo.admin2    -> 200
     data[0] = {email, organizationId, organizationCode: DEFAULT, roles: [STUDENT, ADMIN]}
GET  /api/v1/platform/users            (ADMIN)    -> 403
POST .../admins/{user}/unlock-sessions (ADMIN)    -> 403
POST .../admins/{user}/reset-password  (bad org)  -> 404
~~~

## State left behind

The role created for the A2 probe was deleted; the catalogue is back to 7. No user was mutated: the
recovery probes that would have changed state were issued with an ADMIN token (403) or a bogus
organization (404), both refused before any write.

## What this pass does NOT prove

- **D-05, D-10, D-11 are unchanged and still reproduce.** They are Gate B.
- The two-account Rule A step could not be performed live: `MAX_ADMINS_PER_ORG = 1` permits one ADMIN
  per organization, so there is no second administrator to run it. It is asserted by test only.
- The frontend was not re-exercised in a browser. The platform seeds screens now call endpoints that
  return 200, but that is an inference.
## Pass 3 — after tasks 9-11 (Gate B), migration 36

Date: 2026-10-26. Stack at `V36`. Backend container rebuilt so the two new migrations ran; before the
rebuild the live database was still at `V34`, and every check below would have measured the old code.

### D-10 — user list and detail scoping

| Probe | Before | After |
|---|---|---|
| `GET /users` as `DEFAULT` admin, search `superadmin` | 1 row (the installation owner) | **0 rows** |
| `GET /users/{superadminId}` as `DEFAULT` admin | 200 with the owner's record | **404** |
| `GET /platform/users` as `SUPER_ADMIN` | 5,010 | **5,010** (unchanged) |
| `GET /users` as `DEFAULT` admin, totalElements | 5,010 | **5,009** |

The 5,009 is not a failure. It is the correct count for that administrator's own organization; the
one row that disappeared is the PLATFORM-resident installation owner. The meaningful number is the
first two rows of the table, not the total.

### D-11 — audit trail scoping

| Measure | Value |
|---|---|
| `audit_log` rows, total | 5,867 |
| Attributed to `DEFAULT` | 5,866 |
| Attributed to `PLATFORM` | 1 |
| Unattributed (`NULL`) | 0 |
| Rows visible to the `DEFAULT` admin | **5,866** |

The backfill attributed every row it could derive (the pre-existing history was all DEFAULT activity
plus one PLATFORM action), so the ADMIN's visible count is one less than the total. That is the
demonstration: the row it cannot see is the other organization's.

### D-05 — role ownership

| Probe | Result |
|---|---|
| `POST /roles` as `DEFAULT` admin | **201**, role created |
| `role.organization_id` of the new role | `DEFAULT` |
| The seven seeded roles | `NULL` — platform-wide, unchanged |
| `GET /roles` as `DEFAULT` admin | 7 platform-wide + its own |
| Indexes on `role.code` | `uk_role_code_platform`, `uk_role_code_organization` |

Live probe role `LIVE_T9_SCOPE` was created, inspected, and **deleted**; the catalogue is back to 7
roles.

### A residual this pass found

`SUPER_ADMIN` belongs to the `PLATFORM` organization, so `GET /roles` returns platform-wide roles plus
`PLATFORM`-owned roles — currently the same 7, since every role is platform-wide. **If a business
organization creates a role, `SUPER_ADMIN` will not see it.** That follows from scoping the catalogue by
the caller's organization, and it means the platform tier cannot audit per-organization roles through
this endpoint. It is recorded as GAP-186 rather than fixed here, because "SUPER_ADMIN sees every
organization's roles" is a product decision about the platform surface, not a scoping bug — `GET
/platform/users` already exists as precedent for a cross-organization read, and a matching role surface
would be the consistent answer.
### Correction — the residual first written here was wrong, and it was a regression

The first version of this section recorded that `SUPER_ADMIN` no longer sees organization-owned roles
as an acceptable product consequence, tracked as GAP-186. **That was wrong**, and live probing proved
it:

~~~text
ADMIN visible roles: REVIEWER,STUDENT,SUPPORT,FINANCE,SUPER_ADMIN,STAFF,ADMIN,LIVE_T9_PROBE2
SUPER_ADMIN visible roles: REVIEWER,STUDENT,SUPPORT,FINANCE,SUPER_ADMIN,STAFF,ADMIN
~~~

`SUPER_ADMIN` could not see a role that a tenant had just created. Before `V35` every role was global,
so it saw all of them. The D-05 fix scoped **every** caller to its own organization, including the
system administrator — a **regression against R1 introduced by the fix for D-05**. Labelling it
"a product decision" would have shipped a defect in the exact requirement the mission was protecting.

**Why the test suite did not catch it.** Every role in the fixture was platform-wide, and in that state
the buggy and the correct code return the same list. A scoping change needs a fixture with data on
**both sides** of the boundary; this one had data only on one side. Two tests were added in
`PlatformRoleVisibilityIntegrationTest` — one for the platform tier seeing a tenant role, one for a
tenant still not seeing another tenant's role — and they were confirmed to fail against the buggy
version before the fix was kept.

**The fix** is `RoleService.isSystemAdministrator(actorId)`: the platform tier is identified by holding
the `SUPER_ADMIN` role, not by residing in the `PLATFORM` organization. Role membership is what the
requirement is about, and a residency rule would be both narrower than R1 and untestable without
breaking ADR-0032's one-owner invariant (the bootstrap check is keyed on PLATFORM residence).

**Re-verified live after the fix:** `SUPER_ADMIN` sees the tenant's role, the tenant still does not see
another tenant's role, and the probe role was deleted (catalogue back to 7).

**The lesson, recorded because it is the second time in this mission the same pattern appeared:** both
times, the defect was in the *fixture* rather than the assertion — the audit tests wrote no audited
rows, and these tests had no tenant-owned roles. An assertion can only prove something about data that
exists, and a fixture that exercises one side of a boundary cannot distinguish a correct scope from an
over-broad one.