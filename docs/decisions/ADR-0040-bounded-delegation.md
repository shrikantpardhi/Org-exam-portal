# ADR-0040 — Bounded delegation: no caller may grant what it does not hold

- **Status:** Accepted
- **Phase:** Permission model hardening (D-04, D-09)
- **Spec:** `specs/20-P11-Administration-V1.5.md` §17; `specs/10-P1-Identity-V1.5.md` §52, §65
- **Amends:** ADR-0023 decision 2 ("No god mode" — now enforced, not merely stated)
- **Closes:** D-09 (root cause) and D-04 (its most damaging instance)
- **Migration:** none. The bypass permission is seeded by `V33`, which D-03 already required.

## Context

`10-P1` §52 says "an unrestricted API for arbitrary permission mutation is not exposed unless
explicitly present in the API contract". `PUT /roles/{id}/permissions` was exactly that API. Its only
gate was `ROLE_PERMISSION_ASSIGN`, which grants authority over the entire 163-code catalogue.

Reproduced live. The acting ADMIN held 144 codes and lacked `ADMIN_ROLE_ASSIGN`,
`PLATFORM_SYSTEM_VIEW`, and all `SUPPORT_*`. It granted `ADMIN_ROLE_ASSIGN` to STAFF (200) and
`PLATFORM_ORGANIZATION_VIEW` to STAFF (200).

`ADMIN_ROLE_ASSIGN` is the sharper case: `10-P1` §65 lists it as *reserved-but-not-granted* for
exactly this reason, and `UserService.replaceRoles` gates ADMIN assignment on it. An ADMIN that can
grant it can manufacture a second administrator; the one-ADMIN-per-org cap is a business rule
(data), not a security invariant, so it is not a sufficient brake.

## Decision

`RolePermissionPolicy.checkDelegation` refuses any desired permission set containing a code the caller
does not itself hold:

~~~java
Set<String> held = Set.copyOf(authorizationService.effectivePermissions(actorId));
boolean grantingSomethingUnheld = desired.stream()
        .map(Permission::getCode)
        .anyMatch(code -> !held.contains(code));
if (grantingSomethingUnheld) {
    throw new PermissionNotHeldException();   // 403 PERMISSION_NOT_HELD
}
~~~

Four properties are deliberate:

1. **It inverts the default.** D-03 names the dangerous codes; that list must be maintained against
   every future permission. D-09's rule makes *every* permission safe without a list edit. D-03 is
   therefore defence in depth, not the primary control.
2. **It constrains granting, not revoking.** A caller that acquired an escalated code by some
   earlier accident must still be able to remove it; a rule that blocked revocation would preserve
   the escalation it exists to prevent.
3. **The unbounded bypass satisfies it.** A principal holding `UNBOUNDED_PERMISSION_ASSIGN` (ADR-0039)
   is unbounded by definition, so one permission satisfies both rules and operators have a single
   knob.
4. **Checked on the whole desired set**, after the reserved check, before any row is written.

## Why D-04 and D-09 are one ADR

D-04 has no independent root cause. It is D-09 applied to one specific code. Splitting them would
produce two guards that must be kept consistent, and the second would be dead code the moment the
first fired correctly.

The brief also required the two refusals to be distinguishable, and they are:

| Rule | Code | Meaning |
|---|---|---|
| Reserved set (ADR-0039) | `RESERVED_PERMISSION_DENIED` | structural — nobody may place this without the bypass |
| Bounded delegation (this ADR) | `PERMISSION_NOT_HELD` | authority — *you* do not hold this |

`BoundedDelegationIntegrationTest.theDelegationRefusalIsDistinctFromTheReservedRefusal` asserts both
codes in one test, so a reordering regression cannot silently swap which rule fires.

## Rejected alternatives

- **Deny-list the dangerous codes.** Rejected: it is D-03's mechanism, and D-03 exists *because* the
  list was wrong. Both are kept, with the deny-list subordinate.
- **Require an approval workflow for escalations.** Rejected as new scope; the mission's guardrails
  name five refusal codes and no workflow.
- **Compare permission *counts* rather than codes.** Rejected: a swap (lose `USER_VIEW`, gain
  `ADMIN_ROLE_ASSIGN`) keeps the count and escalates.
- **Enforce in the controller.** Rejected: `20-P11` §17 makes the service layer the boundary, and
  `UserService` will need the same rule when Task 8 lands.

## Consequences

- `PUT /roles/{id}/permissions` returns `403 PERMISSION_NOT_HELD` whenever the desired set contains
  a code the caller lacks.
- No existing test breaks: every role test that grants permissions does so with an ADMIN holding
  them, or through a fixture that constructs the grant directly in the repository.
- **Behaviour change for operators:** an administrator can no longer delegate a permission it has
  not been given. This is the point, but it must be in the release notes — a deployment that relies
  on an ADMIN seeding roles with codes it does not hold will now fail loudly instead of silently
  succeeding.
- The rule reads the caller's effective permissions, which are cached for 5 minutes
  (`AuthorizationService`). A mid-request cache reduction scenario is covered by the Task 6 pinning
  tests; the risk is that a caller whose cache is stale may briefly grant a code it no longer holds.
  Accepted: the TTL is 5 minutes, eviction is per-user on every role change, and the window is
  strictly smaller than the pre-fix exposure (unbounded, permanent).

## Rollback

Remove the `checkDelegation` call site. D-04 and D-09 return exactly as reproduced, and `V33`'s
`UNBOUNDED_PERMISSION_ASSIGN` becomes a permission that does nothing.