# ADR-0043 — Self-modification covers role status, not only permissions

- **Status:** Accepted
- **Phase:** Permission model hardening (D-07)
- **Spec:** `specs/10-P1-Identity-V1.5.md` §63; `specs/20-P11-Administration-V1.5.md` §18
- **Closes:** D-07
- **Migration:** none

## Context

`10-P1` §63 freezes the rule: "a user cannot modify their own permissions" → `403
SELF_PERMISSION_MODIFICATION`. `RoleService.replacePermissions` implemented it as a membership check:
if the caller is an ACTIVE member of the role being edited, refuse.

`RoleService.update` — the other write path on the same object — had **no guard at all**. Reproduced
live:

~~~text
PUT /api/v1/roles/<own ADMIN role id>  {"status": "INACTIVE"}   -> 200 "Role updated"
PUT /api/v1/roles/<own ADMIN role id>  {"status": "ACTIVE"}     -> 403   (cannot undo)
GET /api/v1/roles                                                -> 403   (nothing works)
~~~

The administrator lost every permission granted through the ADMIN role, including `ROLE_UPDATE`,
`ROLE_VIEW` and `USER_UPDATE` — the permissions needed to reverse the change. Recovery required
SQL, and even then the negative result had already been cached for its 5-minute TTL, so the cache key
had to be deleted by hand as well.

The letter of §63 governs *permissions*; status is not permissions. The intent — no self-lockout, no
self-restoration — is defeated by a field the rule does not name.

## Decision

`RoleService.update` refuses a **status change** on a role the caller actively holds:

~~~java
if (request.status() != null && request.status() != role.getStatus()) {
    if (actorId != null && userRoleRepository.findByUserIdAndRoleId(actorId, id)
            .filter(userRole -> userRole.getStatus() == Status.ACTIVE).isPresent()) {
        throw new SelfPermissionModificationException();
    }
    role.setStatus(request.status());
    ...
}
~~~

Renaming and re-describing the caller's own role stay allowed. Three reasons:

1. **They cannot remove authority.** The rule protects the caller's ability to administer, not the
   role's label.
2. **A blanket refusal would break ordinary administration.** An ADMIN is the only principal that
   can describe the ADMIN role; refusing all self-edits would make its description unmaintainable.
3. **It keeps the rule testable as a rule.** `cosmeticEditsToTheCallersOwnRoleStayAllowed` fails if a
   future change widens the guard into a blanket ban, which would be a usability regression that no
   other test would catch.

## Why the same code

`SELF_PERMISSION_MODIFICATION`, not a new code. §63 names it, the mission's guardrail G-06 restricts
the refusal vocabulary, and the semantic is identical: the caller is attempting to change its own
authority. A new code would imply a distinction the frozen contract does not draw.

## Precedence

Per ADR-0038, `ROLE_IMMUTABLE` is checked first. Editing the SUPER_ADMIN role's status returns
`409 ROLE_IMMUTABLE` even for a SUPER_ADMIN member, because immutability is a property of the role
and self-modification is a property of the caller. `theStatusGuardDoesNotReplaceTheImmutabilityGuard`
pins this.

## Rejected alternatives

- **Block any `PUT` on the caller's own role.** Rejected: breaks description maintenance, and the
  authority risk is entirely in the status field.
- **Allow deactivation but auto-restore.** Rejected: no such mechanism exists, and a background
  restorer would be a new subsystem for a defect a guard closes.
- **Only block the last ADMIN's deactivation.** Rejected: that is `LAST_ADMIN` (a different rule
  about the deployment's population). It does not cover an ADMIN among several, and it would leave
  the self-lockout path open for every non-last administrator.

## Consequences

- `PUT /roles/{id}` returns `403 SELF_PERMISSION_MODIFICATION` on any status change to a role the
  caller actively holds.
- Deactivating a role the caller does **not** hold still works (asserted).
- `SelfRoleStatusIntegrationTest` (5 tests) covers deactivate, archive, the allowed cosmetic edit,
  the allowed non-member status change, and the precedence against `ROLE_IMMUTABLE`.
- The cache behaviour observed during reproduction is unchanged and remains a separate concern: a
  negative result is cached for 5 minutes, so recovery from an out-of-band status change needs both
  the data repair and an eviction. That is recorded in the D-07 reproduction and carried into the
  Gate A memo as a recovery-procedure note, because Task 7's endpoints must account for it.

## Rollback

Remove the guard. D-07 returns exactly as reproduced.