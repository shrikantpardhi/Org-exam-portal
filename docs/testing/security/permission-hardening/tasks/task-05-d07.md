# Task 5 / D-07 — self-modification covers role status

**Status:** complete

| Field | Value |
|---|---|
| Defect | D-07 |
| Fix class | Guard + ADR |
| ADR | ADR-0043 |
| Migration | none |
| Tests | `SelfRoleStatusIntegrationTest` (5) |

## Change set

| File | Change |
|---|---|
| `backend/src/main/java/com/company/exam/identity/service/RoleService.java` | status change on a role the caller actively holds throws `SelfPermissionModificationException` |
| `backend/src/test/java/.../SelfRoleStatusIntegrationTest.java` | **new** — 5 tests |
| `docs/decisions/ADR-0043-self-role-status.md` | **new** |

## What is blocked and what is not

| Operation on the caller's own role | Allowed? |
|---|---|
| rename | **yes** — cosmetic, cannot remove authority |
| re-describe | **yes** |
| deactivate | **no** — 403 `SELF_PERMISSION_MODIFICATION` |
| archive | **no** |
| replace permissions | **no** (pre-existing `replacePermissions` guard) |

## Test list

| Test | Asserts |
|---|---|
| `anAdminCannotDeactivateItsOwnRole` | the exact pre-fix 200 call now returns 403, and the role stays ACTIVE |
| `anAdminCannotArchiveItsOwnRole` | archive is refused too |
| `cosmeticEditsToTheCallersOwnRoleStayAllowed` | the description edit still returns 200 |
| `anAdminMayStillChangeTheStatusOfARoleItDoesNotHold` | membership-scoped, not a blanket ban |
| `theStatusGuardDoesNotReplaceTheImmutabilityGuard` | SUPER_ADMIN role still returns `ROLE_IMMUTABLE`, not `SELF_PERMISSION_MODIFICATION` |

## Cache note carried forward

During reproduction, repairing the `role` row was **not sufficient**: the negative authorization result
had been cached for its 5-minute TTL, so the Redis key `exam:authz:perms:<userId>` had to be deleted
by hand. That is a recovery-procedure fact, not a defect in this fix, and it is carried into the Gate A
memo because Task 7's unlock endpoint must evict explicitly.