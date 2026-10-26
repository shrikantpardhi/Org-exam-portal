# Tasks 1–6 — in-scope work complete, stopped before Gate A

**Mission:** permission model hardening. **Date:** 2026-10-26.
**Base commit:** `807a277`. **Migrations added:** `V32`, `V33`. **ADRs added:** ADR-0038, ADR-0039,
ADR-0040, ADR-0043. **ADR amended:** ADR-0032.

| Task | Defect | Status | Migration | ADR | Tests |
|---|---|---|---|---|---|
| 1 | D-01 | complete | `V32` | ADR-0032 amended | `SuperAdminCatalogueReadIntegrationTest` (3) |
| 2 | D-02 | complete | — | ADR-0038 | `RoleImmutabilityIntegrationTest` (6) |
| 3 | D-03 | complete | `V33` | ADR-0039 | `ReservedPermissionIntegrationTest` (5) |
| 4 | D-04, D-09 | complete | (uses `V33`) | ADR-0040 | `BoundedDelegationIntegrationTest` (5) |
| 5 | D-07 | complete | — | ADR-0043 | `SelfRoleStatusIntegrationTest` (5) |
| 6 | D-12 | complete | — | — | `SuperAdminPermissionCatalogueTest` (5) |

**29 tests added.** No existing test was modified, weakened, or deleted.

One of the 29 failed on the first full-suite run and was corrected, not weakened:
`SuperAdminPermissionCatalogueTest.noOtherRoleHoldsAPlatformPermission` iterated every role, but these
integration tests share one non-rollback database, so the ADR-0039 bypass test's disposable role
(which legitimately holds a `PLATFORM_*` code) made the assertion order-dependent. It is now scoped to
the seeded catalogue, which is what ADR-0032 actually constrains. The same scoping was applied to
`ReservedPermissionIntegrationTest.theBypassPermissionIsGrantedToNoSeededRole` for the same reason.

## Requirement status

| Requirement | Status |
|---|---|
| R1 — SUPER_ADMIN administers organizations, admins, roles, permissions | **Met** (D-01 fixed; further authoring scope is Gate A's decision) |
| R2 — ADMIN manages only within its own organization | **NOT met.** D-05, D-10, D-11 remain, and D-10 makes it observably false on a live deployment |
| R3 — no user modifies their own permissions | **Met** (D-02, D-07; D-08's two-step variant needs Gate A) |

## The three files that carry the fix

| File | Role |
|---|---|
| `backend/src/main/java/com/company/exam/identity/service/RoleService.java` | the guard chain, in fixed precedence |
| `backend/src/main/java/com/company/exam/identity/service/RolePermissionPolicy.java` | reserved set + bounded delegation, both data-driven |
| `backend/src/main/java/com/company/exam/identity/IdentityConstants.java` | the immutable role-code set |

## Guard precedence, as implemented

~~~text
RoleService.update:
  1. ROLE_IMMUTABLE                 409   (contract-fixed role)
  2. SELF_PERMISSION_MODIFICATION   403   (status change on a role the caller holds)

RoleService.replacePermissions:
  1. ROLE_IMMUTABLE                 409   (contract-fixed role)
  2. INVALID_STATE                  409   (inactive role)
  3. SELF_PERMISSION_MODIFICATION   403   (caller's own role)
  4. INVALID_PERMISSION             400   (unknown id)
  5. RESERVED_PERMISSION_DENIED     403   (reserved code, no bypass)
  6. PERMISSION_NOT_HELD            403   (code the caller does not hold)
~~~

## Corrections to the Section 3 record

One factual claim in the pre-work was wrong and has been withdrawn:

**D-03 — "the catalogue endpoint conceals `PLATFORM_*` from an ADMIN."** It does not. The earlier
reading came from a single page of 100 against a 163-code catalogue sorted by `code`; `PLATFORM_*`
sorts after the first page. Scanning every page returns all eight `PLATFORM_*` codes and
`ADMIN_ROLE_ASSIGN`. D-03 is therefore worse than recorded: the ADMIN can discover **and** assign
every reserved code. The D-03 reproduction file records the correction in place.

D-12's pre-work record cited `SuperAdminPermissionCatalogueTest` as its "test to add"; the test now
exists under that name and its expected set differs from the pre-work sketch (10 codes, not 8),
because Task 1 added `ROLE_VIEW` and `PERMISSION_VIEW` before Task 6 pinned the result.

## What is NOT done

- Task 7 (D-06 recovery endpoints) — Gate A.
- Task 8 (D-08 two-step escalation) — Gate A.
- Tasks 9–11 (D-05 org-scoped roles, D-10 user-list scope, D-11 `audit_log.organization_id`) — Gate B.
- `docs/known-gaps.md`, `docs/implementation-status.md`, `docs/PROJECT-SUMMARY.md` are updated in the
  same change set as this file, per AGENTS.md.
