# Task 7 / D-06 — SUPER_ADMIN recovery for a locked-out ADMIN

**Status:** complete (Gate A approved 2026-10-26)

| Field | Value |
|---|---|
| Defect | D-06 |
| Fix class | Endpoint + permission + ADR |
| ADR | ADR-0042 |
| Migration | `V34__super_admin_authoring_and_recovery.sql` |
| Tests | 15 in `SuperAdminAuthoringAndRecoveryIntegrationTest` (shared with Task 8); 5 in `SuperAdminPermissionCatalogueTest` (edited) |

## Scope note: this amended a policy, it did not fix a bug

`10-P1` §63 says recovery is "a documented direct database procedure ... **not a UI feature**". That
was a deliberate decision, taken when several ADMINs could share an organization. ADR-0032's
`MAX_ADMINS_PER_ORG = 1` removed the replacement path and made the earlier decision unsafe. ADR-0042
says so explicitly rather than presenting new scope as remediation.

## Change set

| File | Change |
|---|---|
| `backend/src/main/resources/db/migration/V34__super_admin_authoring_and_recovery.sql` | **new** — `PLATFORM_ADMIN_VIEW`, `PLATFORM_ADMIN_RECOVER`; grants authoring + bypass to SUPER_ADMIN |
| `backend/src/main/java/com/company/exam/platform/service/PlatformAdminRecoveryService.java` | **new** — the two recovery operations |
| `backend/src/main/java/com/company/exam/platform/controller/PlatformAdminRecoveryController.java` | **new** — `POST .../reset-password`, `POST .../unlock-sessions` |
| `backend/src/main/java/com/company/exam/platform/service/PlatformUserService.java` | **new** — cross-organization list |
| `backend/src/main/java/com/company/exam/platform/controller/PlatformUserController.java` | **new** — `GET /api/v1/platform/users` |
| `backend/src/main/java/com/company/exam/platform/dto/PlatformDtos.java` | `AdminRecoveryResponse`, `PlatformUserSummary` |
| `backend/src/test/java/.../SuperAdminAuthoringAndRecoveryIntegrationTest.java` | **new** — 15 tests |
| `backend/src/test/java/.../SuperAdminPermissionCatalogueTest.java` | **edited** — pinned set 10 → 16, per Gate A |
| `docs/decisions/ADR-0042-super-admin-authoring-and-recovery.md` | **new** |

## Refusal order (recovery)

~~~text
1. organization missing                     -> 404
2. organization is PLATFORM                 -> 404
3. user missing or in another organization  -> 404
4. user ARCHIVED                            -> 422 USER_ARCHIVED
5. user does not hold ADMIN                 -> 422 NOT_AN_ADMIN
6. user is the SUPER_ADMIN account          -> 409 SUPER_ADMIN_IMMUTABLE
~~~

Rule 6 must be evaluated before rule 5 in effect: a SUPER_ADMIN is not an ADMIN either, and
reporting `NOT_AN_ADMIN` for it sends an operator looking for a data problem that does not exist.
This was a real bug in the first draft — the test caught it (expected 409, got 422).

## What was deliberately not built

| Not built | Why |
|---|---|
| password disclosure | the endpoint issues a reset token; a SUPER_ADMIN who can read an ADMIN's password is a larger hole than the one being closed |
| role granting through recovery | would be ADR-0040's escalation with a different actor |
| `PUT`/`DELETE` on `/platform/users` | cross-tenant mutation is what R2 forbids; the list is read-only |

## Tests

| Test | Asserts |
|---|---|
| `aSuperAdminCanCreateARoleAndThenAssignPermissionsToIt` | create-then-assign works end to end; no DTO change |
| `aSuperAdminCanAssignAReservedPlatformPermissionBecauseItHoldsTheBypass` | A2b is effective |
| `anAdminStillCannotPlaceAPlatformPermissionEvenThoughSuperAdminCan` | the bypass did not re-open D-03 |
| `aSuperAdminCanListUsersAcrossOrganizations` | the list exists and carries organization context |
| `theCrossOrganizationUserListIsRefusedToAnOrganizationAdmin` | 403 for ADMIN — D-10's lesson applied at the platform tier |
| `theCrossOrganizationUserListIncludesThePlatformOrganization` | PLATFORM is not filtered out here (unlike ADR-0033's business lists) |
| `theUserListFiltersByOrganization` | the filter is real, not decorative |
| `aSuperAdminCanResetALockedOutAdminsPassword` | 200 + `mustChangePassword` set |
| `aResetRevokesTheTargetsActiveSessions` | the old credential stops working — containment, not just a flag |
| `recoveryRefusesAUserThatIsNotAnAdministrator` | 422 |
| `recoveryCannotReachAUserInAnotherOrganization` | 404 |
| `recoveryOfTheSuperAdminAccountIsRefused` | 409 `SUPER_ADMIN_IMMUTABLE` |
| `recoveryIsRefusedToAnOrganizationAdmin` | 403 |
| (Task 8's two tests share this class) | see `task-08-d08.md` |

## Three defects found in my own first draft

1. `authorizationService.evict(...)` — wrong method for a transactional path; corrected to
   `evictAfterCommit`, matching `PasswordResetService`.
2. `isSuperAdmin` was checked **after** `holdsAdminRole`, so the installation owner was reported as
   `NOT_AN_ADMIN` (422) instead of `SUPER_ADMIN_IMMUTABLE` (409).
3. The PLATFORM-organization test asserted a non-empty result against a fixture that seeds the
   organization but no user in it — it would have passed for the wrong reason if the list had
   filtered PLATFORM out, because both produce zero. The test now creates a resident first.

Recorded because a report that lists only the passing version of events is not a report.