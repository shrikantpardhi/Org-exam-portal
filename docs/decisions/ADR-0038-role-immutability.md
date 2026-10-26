# ADR-0038 — Role immutability: the SUPER_ADMIN role is contract-fixed

- **Status:** Accepted
- **Phase:** Permission model hardening (D-02)
- **Spec:** `specs/20-P11-Administration-V1.5.md` §18; `specs/10-P1-Identity-V1.5.md` §9, §63
- **Amends:** ADR-0032 (which asserted the invariants for the SUPER_ADMIN **user**, not the role)
- **Closes:** D-02
- **Migration:** none. The rule is code plus a code-keyed constant; no schema and no seed change.

## Context

ADR-0032 declares `SUPER_ADMIN` "permanent, unique, non-demotable, non-archivable". The
implementation enforced that against the SUPER_ADMIN **user** only:
`UserService.guardSuperAdminImmutability` blocks archiving that account and replacing its roles, and
`SuperAdminImmutabilityIntegrationTest` pins both. Nothing enforced it against the SUPER_ADMIN
**role**.

The two are not the same object, and the gap was exploitable. Reproduced against the running stack:

~~~text
PUT /api/v1/roles/<SUPER_ADMIN role id>/permissions   as ADMIN
  {"permissionIds": [<3 ids>]}                        -> 200 "Role permissions updated"
~~~

Five of the eight `PLATFORM_*` grants were deactivated. The installation owner could still log in —
authentication does not consult the permission catalogue — so the damage was silent: the portal
loaded and every platform query returned 403. Recovery required direct database access, because
`SUPER_ADMIN` held no `ROLE_UPDATE` with which to undo it.

`RoleService.replacePermissions` had one guard, and it was the wrong one: a membership check
(`10-P1 §63` `SELF_PERMISSION_MODIFICATION`). An ADMIN does not belong to the SUPER_ADMIN role, so
the guard did not fire. `RoleService.update` had no guard at all, which is D-07's vector against the
same role.

## Decision

`RoleService` refuses every mutation of a **contract-fixed role**, identified by role **code**
(`IdentityConstants.IMMUTABLE_ROLE_CODES`), not by identifier:

~~~java
private void guardRoleImmutability(Role role) {
    if (IdentityConstants.IMMUTABLE_ROLE_CODES.contains(role.getCode())) {
        throw new RoleImmutabilityException();   // 409 ROLE_IMMUTABLE
    }
}
~~~

Called from `update` (rename, describe, status) and from `replacePermissions` (the permission set).

Four properties are deliberate:

1. **No caller exemption.** `SUPER_ADMIN` is refused too. A guard that exempts the principal it
   protects makes the invariant conditional on that principal's continued good behaviour, which is
   the failure mode the invariant exists to prevent.
2. **Keyed on code, never on a seeded UUID** (G-02). A re-seed that issues a fresh identifier for
   the role leaves the rule intact.
3. **Checked before every other refusal in the method**, so the answer does not vary with the role's
   current status. Out-of-band damage that leaves the role `INACTIVE` must not become a way to
   "repair" it into a different shape.
4. **A distinct error code.** `409 ROLE_IMMUTABLE`, not `SUPER_ADMIN_IMMUTABLE`. The latter guards
   the SUPER_ADMIN *account*, is raised from a different service, and answers a different question.
   Collapsing them would make a test unable to say which invariant fired.

## Guard precedence

Fixed here once and asserted test-by-test:

~~~text
1. ROLE_IMMUTABLE                    409   contract-fixed role      (this ADR, D-02)
2. INVALID_STATE                     409   inactive role
3. SELF_PERMISSION_MODIFICATION      403   caller's own role        (10-P1 §63)
4. RESERVED_PERMISSION_DENIED        403   reserved code            (ADR-0039, D-03)
5. PERMISSION_NOT_HELD               403   bounded delegation       (ADR-0040, D-04/D-09)
6. ROLE_ESCALATION_DETECTED          403   two-step escalation      (ADR-0044, D-08)
~~~

The order matters: a later rule must never mask an earlier one. An ADMIN stripping the SUPER_ADMIN
role is a contract violation first and a delegation failure second; the response must say
`ROLE_IMMUTABLE`.

## Rejected alternatives

- **Exempt `SUPER_ADMIN` from the guard.** Rejected: makes the invariant self-referential, and
  `SUPER_ADMIN` does not hold `ROLE_UPDATE` in any case (V32 grants only the two catalogue *read*
  permissions), so the exemption would be dead code that still reads as a policy statement.
- **Enforce in the controller.** Rejected: `RoleService` is called by the user-side paths too, and
  `20-P11` §17 makes the service layer the security boundary.
- **Reuse `SUPER_ADMIN_IMMUTABLE`.** Rejected as above — different object, different service,
  different question.
- **A `role.immutable` column.** Rejected: it would put a contract invariant into mutable data,
  where any future writer could flip it, and would need a frozen-schema amendment (rule 3) for a
  constant that already exists in the code.

## Consequences

- `PUT /roles/{id}` and `PUT /roles/{id}/permissions` return `409 ROLE_IMMUTABLE` for
  `SUPER_ADMIN`, for every caller.
- D-07's status vector against the SUPER_ADMIN role closes here as a side effect; `PUT /roles/{id}`
  against an ADMIN's *own* role is D-07 proper and is closed separately by ADR-0043.
- `SuperAdminImmutabilityIntegrationTest` continues to cover the *account*; the new
  `RoleImmutabilityIntegrationTest` covers the *role*. Neither subsumes the other.
- A future second immutable role is a one-line change to `IMMUTABLE_ROLE_CODES`, with its own test.
- The `SUPER_ADMIN` role can no longer be re-permissioned even by an operator using the API. A
  legitimate change to the platform catalogue is a migration (`V32` set the precedent), which is the
  correct channel: it is reviewable, ordered, and replayed on every environment.

## Rollback

Remove the `guardRoleImmutability` call sites and the constant. There is no data migration to
reverse. Reverting re-opens D-02 exactly as reproduced.
