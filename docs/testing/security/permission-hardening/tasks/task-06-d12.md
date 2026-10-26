# Task 6 / D-12 — SUPER_ADMIN permission set pinned

**Status:** complete

| Field | Value |
|---|---|
| Defect | D-12 |
| Fix class | Test only — no ADR number claimed, no migration |
| Test | `SuperAdminPermissionCatalogueTest` (5 tests) |

## What is pinned

| Test | Asserts |
|---|---|
| `theSuperAdminPermissionSetIsExactlyThePlatformCataloguePlusTheCatalogueReads` | the set is exactly 10 codes — 8 `PLATFORM_*` + `ROLE_VIEW` + `PERMISSION_VIEW` |
| `everyPlatformPermissionIsHeldBySuperAdmin` | every permission whose code starts `PLATFORM_` is held; there are exactly 8 |
| `noOtherRoleHoldsAPlatformPermission` | no non-SUPER_ADMIN role holds a `PLATFORM_*` code |
| `superAdminHoldsNoAdministrativeSurfacePermission` | 12 named `/admin/*` and escalation codes are absent |
| `theSuperAdminRoleIsActiveAndUnique` | exactly one `SUPER_ADMIN` role row, ACTIVE |

## Why the set is written out literally

A derived expectation moves with the thing it is meant to pin. `EXPECTED` is a literal `Set.of(...)`;
changing the installation owner's capability therefore requires editing the test, which is the point.

## What the test would have caught

| Defect | Would this test have failed? |
|---|---|
| D-01 (missing `ROLE_VIEW`/`PERMISSION_VIEW`) | **Yes** — `EXPECTED` contains them |
| D-02 (ADMIN stripping the SUPER_ADMIN set) | **Yes** — the set would have shrunk to 3 |
| D-03 (a `PLATFORM_*` code placed on another role) | **Yes** — `noOtherRoleHoldsAPlatformPermission` |
| A future `PLATFORM_*` seeded but not granted | **Yes** — `everyPlatformPermissionIsHeldBySuperAdmin` |

## Interaction with Task 1

Task 1 (`V32`) and Task 6 are the same change seen from two directions: `V32` makes the set correct,
and this test makes it *stay* correct. That is why the brief orders Task 6 last among the in-scope
work — it pins the finished state, not an intermediate one.

## Deliberate non-assertions

The test does **not** assert that `SUPER_ADMIN` can or cannot author roles. That is Gate A's decision:
if Task 7 grants `ROLE_UPDATE`, `superAdminHoldsNoAdministrativeSurfacePermission` must be edited in
the same change set, which is the intended forcing function.
