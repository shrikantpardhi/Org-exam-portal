# D-07 — Self-permission rule does not cover role STATUS

| Field | Value |
|---|---|
| Defect | D-07 |
| Severity | HIGH |
| Status | **Confirmed — real, and it locked the reproduction session out** |
| Reproduced | 2026-10-26, against the running local stack |
| Frozen contract | `10-P1` §63 ("A user cannot modify their own permissions → 403 SELF_PERMISSION_MODIFICATION"); `20-P11` §18 (same rule) |
| Contract conflict | The rule governs *permissions*. `PUT /roles/{id}` changes *status*, which is not permissions. The rule's intent — no self-lockout, no self-restoration — is defeated by a field the rule does not name. |

## Reproduction

~~~text
# ADMIN is a member of the ADMIN role
PUT /api/v1/roles/<ADMIN roleId>  {"status": "INACTIVE"}   -> 200 "Role updated"
~~~

Resulting state, and the immediate consequence:

~~~sql
SELECT code, status FROM role;
-- ADMIN | INACTIVE      <-- the caller just deactivated their own role
~~~

~~~text
PUT /api/v1/roles/<ADMIN roleId>  {"status": "ACTIVE"}    -> 403   # cannot undo it
PUT /api/v1/roles/<ADMIN roleId>  {"name": "X"}           -> 403   # cannot even rename
PUT /api/v1/roles/<STAFF roleId>  {"status": "INACTIVE"}  -> 403   # and now nothing else works
GET /api/v1/roles                                         -> 403
~~~

After `evictUsersOfRole`, the caller's `ROLE_VIEW`, `ROLE_UPDATE` and every other permission
granted through `ADMIN` disappear. The ADMIN self-locked out with one legitimate-looking call and
has no way back in through the API.

## Restoration performed

The reproduction locked the running stack's administrator out. Restored directly:

~~~sql
UPDATE role SET status = 'ACTIVE', updated_on = now() WHERE code IN ('ADMIN','STAFF');
-- UPDATE 2
~~~

and then the authorization cache entry had to be evicted by hand, because the negative result had
already been cached for the 5-minute TTL:

~~~text
docker exec ... redis-cli DEL 'exam:authz:perms:af9257be-69f0-491a-b5ba-9d73729a5223'  -> 1
GET /api/v1/roles -> 200
~~~

That cache behaviour is itself worth noting: the eviction on write (`evictUsersOfRole`) ran, but a
read that raced it re-populated the cache with the locked-out set. Recovery from this defect is not
instantaneous even after the data is repaired.

## Root cause

`backend/src/main/java/com/company/exam/identity/service/RoleService.java:91-106` — `update()`
performs no self-membership check at all. Compare `replacePermissions()` at line 121-124, which
does. The two methods disagree about the same rule.

## Blast radius

Self-lockout (availability) and privilege restoration: an ADMIN whose role was deactivated could
reactivate it, except that the same edit grants the permission needed to perform it — so the
*restoration* direction is blocked by permission loss while the *lockout* direction is wide open.
An attacker with a temporary ADMIN session can permanently disable the role.

## Residual if not fixed
`SELF_PERMISSION_MODIFICATION` is bypassable through `PUT /roles/{id}`. Its intent is not met.

## Fix class
Guard + ADR.

## Gate
None. In-scope for Task 5.