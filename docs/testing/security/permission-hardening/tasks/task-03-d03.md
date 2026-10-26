# Task 3 / D-03 — reserved permission set

**Status:** complete

| Field | Value |
|---|---|
| Defect | D-03 |
| Fix class | Guard + ADR + seed |
| ADR | ADR-0039 |
| Migration | `V33__reserved_permission_set.sql` |
| Tests | `ReservedPermissionIntegrationTest` (5) |

## Change set

| File | Change |
|---|---|
| `backend/src/main/resources/db/migration/V33__reserved_permission_set.sql` | **new** — `UNBOUNDED_PERMISSION_ASSIGN` permission + `RESERVED_PERMISSIONS` business rule |
| `backend/src/main/java/com/company/exam/identity/exception/ReservedPermissionException.java` | **new** — 403 `RESERVED_PERMISSION_DENIED` |
| `backend/src/main/java/com/company/exam/identity/service/RolePermissionPolicy.java` | **new** — data-driven reserved set with fail-closed default |
| `backend/src/main/java/com/company/exam/identity/service/RoleService.java` | `checkReserved` after `validatePermissions`, before any write |
| `backend/src/test/java/.../ReservedPermissionIntegrationTest.java` | **new** — 5 tests |
| `docs/decisions/ADR-0039-reserved-permission-set.md` | **new** |

## Why data and not a Java constant

ADR-0023 decision 6 ("rules are data, never code"). A `PLATFORM_` **prefix** rule means a future
platform permission is reserved the moment it is seeded, with no code change and no list to maintain.

## Fail-closed behaviour

| Rule state | Effect |
|---|---|
| ACTIVE with prefixes | only matching codes are reserved |
| missing / INACTIVE / unreadable | **every** permission is reserved, bypass held by nobody, so all assignment is refused |
| ACTIVE with no prefixes and no codes | same as missing (refuses everything) |

`ReservedPermissionIntegrationTest` covers the refusal and the satisfiable bypass; the fail-closed
branch is exercised by construction in `RolePermissionPolicy.reservedSet()` and logged at WARN.

## Test list

| Test | Asserts |
|---|---|
| `anAdminCannotPlaceAPlatformPermissionOnARole` | the exact pre-fix 200 call now returns 403 `RESERVED_PERMISSION_DENIED`, and no row is written |
| `aReservedRefusalLeavesTheExistingSetUntouched` | a mixed request is refused as a whole |
| `theBypassPermissionIsGrantedToNoSeededRole` | `V33` attaches the bypass to no seeded role |
| `aHolderOfTheBypassPermissionMayPlaceAReservedCode` | the rule is satisfiable, not a blanket ban |
| `theReservedRuleIsDeclaredAsData` | the rule exists with the `PLATFORM_` prefix and the bypass name |

## Known residual

The `RESERVED_PERMISSIONS` rule is editable through `PUT /business-rules/{id}`, which an ADMIN can
reach. Recorded as **GAP-183**; widening the rule is safe (it narrows what may be assigned), narrowing
it is not. Not in the mission's twelve defects, so no task was invented for it.