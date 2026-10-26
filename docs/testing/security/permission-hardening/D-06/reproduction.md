# D-06 — No SUPER_ADMIN recovery path for a locked-out ADMIN

| Field | Value |
|---|---|
| Defect | D-06 |
| Severity | HIGH |
| Status | **Confirmed — real** |
| Reproduced | 2026-10-26, against the running local stack |
| Frozen contract | `10-P1` §63 ("Emergency path: ... recovery is a documented direct database procedure operated by a DBA under change control, not a UI feature"); ADR-0032 (`MAX_ADMINS_PER_ORG = 1`); ADR-0033 (platform surface); `05-API-Contract` §72.5 (`POST /organizations/{id}/admins` — create only) |
| Contract | The frozen contract **explicitly** places recovery outside the API. This is therefore not a contract violation — it is a contract that ADR-0032's one-admin cap has made costly. See §Conflict below. |

## Reproduction

~~~text
POST /api/v1/auth/login {superadmin@platform.test, SYSTEM_ADMIN}          -> 200

POST /api/v1/platform/organizations/<orgId>/admins/<userId>/reset-password -> 404
POST /api/v1/platform/organizations/<orgId>/admins/<userId>/unlock-sessions -> 404
GET  /api/v1/platform/users                                                -> 404
~~~

`grep -rn 'platform/users' backend/src/main/java` returns nothing; §72 defines nine endpoints
(§72.1–§72.9) and none of them is a recovery action.

The only mutating platform capability is `POST /platform/organizations/{id}/admins` (`§72.5`),
which is guarded by `PLATFORM_ADMIN_CREATE` and refuses when the organization already has an
ADMIN (`AdminCapExceededException`, `UserService.java:285-313`). With `MAX_ADMINS_PER_ORG = 1`
live in `business_rule`, a locked-out organization **cannot** be given a second admin, and the
existing one cannot be reset. The installation is dependent on database access.

## The conflict

`10-P1` §63 puts recovery out of API scope on the argument that it "should not be possible given
the last-admin rule". That argument holds for *deletion* of the last ADMIN. It does not hold for
the failure modes that actually occur: a forgotten password, a compromised credential, or an
ADMIN whose account was archived by mistake. ADR-0032's one-ADMIN-per-org cap removes the
"create a replacement" escape hatch that a multi-admin deployment would have had.

## Residual if not fixed

Every ADMIN lockout costs a DBA session against production. This is the documented, accepted
procedure — the cost is real but bounded, and it is the reason this defect is HIGH rather than
CRITICAL.

## Fix class
Endpoint + ADR + `05-API-Contract` §72 amendment.

## Gate
**Gate A.** Task 7.