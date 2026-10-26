# D-10 — User list has no explicit organization scope

| Field | Value |
|---|---|
| Defect | D-10 |
| Severity | MEDIUM |
| Status | **Confirmed — real, and worse than the brief assumed** |
| Reproduced | 2026-10-26, against the running local stack |
| Frozen contract | `10-P1` §54 (user list filters: `status`, `roleId`, `search` — no organization filter is defined); `10-P1` §151 ("every authenticated user belongs to an organization through `organization_id`"); ADR-0033 (PLATFORM is a second organization); ADR-0037 ("no cross-org leakage") |
| Contract conflict | The brief describes this as "no explicit `organizationId` filter; cross-org visibility is mitigated by `SUPER_ADMIN_IMMUTABLE`". **The mitigation does not exist.** `GET /users` is not org-scoped at all, and the SUPER_ADMIN *user* is fully visible and readable by a `DEFAULT`-organization ADMIN. |

## Reproduction

~~~text
GET /api/v1/users?page=0&size=3   (as ADMIN of DEFAULT)
  -> 200, pagination.totalElements = 5010
  -> row DTO keys: id, firstName, lastName, email, status, roles
~~~

The live deployment holds 5,010 users in 2 organizations: 5,009 in `DEFAULT` and 1 in `PLATFORM`.
The ADMIN sees all 5,010 — the PLATFORM organization's user included.

~~~text
GET /api/v1/users?search=superadmin   (as ADMIN of DEFAULT)
  -> 200, [{"email":"superadmin@platform.test","roles":["SUPER_ADMIN"]}]

GET /api/v1/users/3dde4fb3-413c-4b57-bf78-69d9fee128f3   (as ADMIN of DEFAULT)
  -> 200, {"organizationId":"cd812eb6-69e7-4592-a26b-87b4861b4143", ...}
~~~

So an organization ADMIN can enumerate, read, and — subject to `USER_UPDATE` — attempt to mutate
users belonging to the PLATFORM organization. The `SUPER_ADMIN_IMMUTABLE` guard blocks the
*archive* and *role* paths for that one user, but not the read, and not the update of any other
PLATFORM-org user.

## Root cause

`backend/src/main/java/com/company/exam/identity/service/UserService.java:360-384` —
`userSpecification(status, roleId, search)` builds predicates for `status`, `search` and `roleId`
only. `organizationId` is never referenced. `UserController:54-59` exposes no org parameter either.

`UserDtos` does carry `organizationId` on the detail projection, so the data is available and
simply unused as a filter.

## Note on the brief's premise

The brief says "cross-org visibility is only mitigated by `SUPER_ADMIN_IMMUTABLE` for the
`SUPER_ADMIN` user; nothing else". Verified: `guardSuperAdminImmutability` (`UserService`) covers
archive and role replacement for the SUPER_ADMIN user only. It is not a visibility mitigation at
all. The correct statement is: **nothing mitigates it.**

## Blast radius

R2 is unmet for the user surface: an organization ADMIN reads the other organization's users. This
is the live tenant leak previously observed but not yet registered.

## Residual if not fixed
Cross-organization user enumeration and read by any `USER_VIEW` holder.

## Fix class
Service + test (Task 10). No schema change: `organization_id` already exists on `user`.

## Gate
None — Task 10 is in the third mission, after Gate B, because it pairs naturally with Task 9's
scoping work. It is *not* gated on a schema amendment.