# Task 8 / D-08 Rule A — a role cannot be assigned by someone who cannot see all of it

**Status:** complete (Gate A approved 2026-10-26)

| Field | Value |
|---|---|
| Defect | D-08 |
| Fix class | Guard + ADR |
| ADR | ADR-0044 |
| Migration | none |
| Tests | 2 in `SuperAdminAuthoringAndRecoveryIntegrationTest` |

## Step 1 was already closed by Task 4

D-08's reproduction had two steps. The second —
`PUT /roles/<ELEV_TEST>/permissions {ADMIN_ROLE_ASSIGN}` — already returns 403
`PERMISSION_NOT_HELD` after ADR-0040's bounded delegation. Verified against the running stack, not
only in tests: the live recheck returned 403 where the pre-work recorded 200.

## What Rule A adds

The sequence bounded delegation cannot see:

~~~text
1. an administrator holding ADMIN_ROLE_ASSIGN puts it on a role         (legitimate)
2. a SECOND administrator, not holding it, assigns that role to a user  (escalation)
~~~

Step 2 names a *role*, not a permission. Every body-inspecting check is blind to it, so the guard
inspects the role's effective permission set instead.

## Change set

| File | Change |
|---|---|
| `backend/src/main/java/com/company/exam/identity/exception/RoleEscalationException.java` | **new** — 403 `ROLE_ESCALATION_DETECTED` |
| `backend/src/main/java/com/company/exam/identity/service/RolePermissionPolicy.java` | **new** `checkAssignable`; now also depends on `PermissionRepository` |
| `backend/src/main/java/com/company/exam/identity/service/UserService.java` | guard on **both** `create` and `replaceRoles` |
| `docs/decisions/ADR-0044-role-escalation-on-assignment.md` | **new** |

## Why both call sites

The first draft guarded only `replaceRoles`, and the test failed with **201**. `POST /users` attaches
roles through `assignRole(...)`, not through `replaceRoles`, so guarding one path left the escalation
reachable at `POST /users` — the path a caller with `USER_CREATE` actually uses. This is the same
class of mistake as closing one endpoint of a pair, and it is why the test creates the user through
the API rather than calling the service.

## Tests

| Test | Asserts |
|---|---|
| `aRoleCarryingAPermissionTheAssignerLacksCannotBeAssigned` | SUPER_ADMIN builds and loads the role; the ADMIN is refused 403 `ROLE_ESCALATION_DETECTED` at `POST /users` |
| `anAdminCanStillAssignAnOrdinaryRole` | STUDENT assignment still returns 201 — the guard is not a blanket refusal |

## Residuals

- `role` remains globally scoped (D-05), so an ADMIN cannot *see* the roles it is refused; the
  refusal is correct but the discovery experience is poor until Gate B.
- The two-account step could not be performed against the live stack because `MAX_ADMINS_PER_ORG = 1`
  permits one ADMIN per organization. It is asserted; the live stack cannot host the second account.
## The design conflict the full suite found

Rule A as first written was **too strong**, and the full suite proved it:

~~~text
AdminCapIntegrationTest.grantingAdminWhenTheOrgHasNoAdminSucceeds          expected 200, got 403
AdminCapIntegrationTest.grantingAdminWhenTheOrgAlreadyHasAnAdminIsRejected expected 409, got 403
IdentityRoleApiIntegrationTest.adminRoleAssignmentRequires...              expected 409, got 403
~~~

The cause is real, not test noise. `ADMIN` carries **144 permission codes**. A caller holding only
`ADMIN_ROLE_ASSIGN` can therefore never satisfy a full containment check for the ADMIN role, so
nobody but an existing ADMIN could ever create one — and ADR-0032's `MAX_ADMINS_PER_ORG = 1` means
there is no second ADMIN to do it. Rule A would have made `ADMIN_ROLE_ASSIGN` a dead permission and
the ADMIN role unassignable in a fresh organization.

**Resolution:** `ADMIN_ROLE_ASSIGN` is the authority to hand out the ADMIN role, so holding it
satisfies the check *for the ADMIN role specifically* — and for that role only. The carve-out is one
line and named, rather than a general weakening: every other role is still checked in full.

This is recorded rather than quietly fixed because it is the kind of change that looks like tuning a
test until it passes. It is not: the pre-existing tests encode a real product requirement (a first
ADMIN must be creatable), and the new guard contradicted it. The guard was wrong.

## Test-isolation defect I introduced

`SuperAdminBootstrapProvisioningIntegrationTest` then failed with `expected CREATED but was
ALREADY_EXISTS`. My first instinct — relax the assertion to accept either outcome — was wrong, and I
reverted it. The actual cause was my own new test: to prove the cross-organization list does not
filter `PLATFORM`, it created a user in `PLATFORM` **with the SUPER_ADMIN role**, breaking ADR-0032's
one-owner invariant from a neighbouring test.

The fixture is now roleless. The bootstrap assertion stays strict. A test that only passes because a
sibling stopped checking something is not passing.