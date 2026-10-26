# Task 2 / D-02 — contract-fixed roles are immutable

**Status:** complete

| Field | Value |
|---|---|
| Defect | D-02 |
| Fix class | Guard + ADR |
| ADR | ADR-0038 |
| Migration | none |
| Tests | `RoleImmutabilityIntegrationTest` (6) |

## Change set

| File | Change |
|---|---|
| `backend/src/main/java/com/company/exam/identity/exception/RoleImmutabilityException.java` | **new** — 409 `ROLE_IMMUTABLE` |
| `backend/src/main/java/com/company/exam/identity/IdentityConstants.java` | **new** `IMMUTABLE_ROLE_CODES` (code-keyed, G-02) |
| `backend/src/main/java/com/company/exam/identity/service/RoleService.java` | `guardRoleImmutability` called from `update` and `replacePermissions`, first in both |
| `backend/src/test/java/.../RoleImmutabilityIntegrationTest.java` | **new** — 6 tests |
| `docs/decisions/ADR-0038-role-immutability.md` | **new** |

## Test list

| Test | Asserts |
|---|---|
| `anAdminCannotReplaceThePermissionsOfTheSuperAdminRole` | the exact pre-fix 200 call now returns 409, and the 10-permission set is unchanged |
| `evenSuperAdminCannotReplaceThePermissionsOfItsOwnRole` | the call does not succeed for `SUPER_ADMIN` either (403 or 409, because `@PreAuthorize` refuses first) |
| `anAdminCannotRenameOrInactivateTheSuperAdminRole` | the other write path (`PUT /roles/{id}`) is guarded too |
| `theGuardIsKeyedOnTheRoleCodeNotOnItsIdentifier` | `ARCHIVED` also refused |
| `anOrdinaryRoleIsStillEditable` | the guard is not a blanket refusal |
| `theRefusalDoesNotDependOnTheRolesCurrentStatus` | still 409 after an out-of-band status change |

## Note on the SUPER_ADMIN self-test

The brief's expected code was 409. In practice `SUPER_ADMIN` is refused at `@PreAuthorize` (403)
first, because `V32` grants it `ROLE_VIEW` and `PERMISSION_VIEW` but **not** `ROLE_PERMISSION_ASSIGN`.
Both layers refuse, which is what the invariant requires. The test asserts "not 200, set unchanged"
rather than a specific status, so it stays valid whichever way Gate A decides §3 of the Gate A memo.

## Files not changed

`UserService.guardSuperAdminImmutability` is untouched: it guards the SUPER_ADMIN **account**, a
different object with a different refusal code (`SUPER_ADMIN_IMMUTABLE`). Conflating them would make a
test unable to say which invariant fired.