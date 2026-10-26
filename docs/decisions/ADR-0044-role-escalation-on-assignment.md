# ADR-0044 — role assignment is bounded by what the assigner holds

- **Status:** Accepted (2026-10-26), on Gate A approval
- **Related:** ADR-0038 (role immutability), ADR-0039 (reserved set), ADR-0040 (bounded delegation)
- **Closes:** D-08 Rule A

## Context

D-08 reproduced a two-step escalation:

~~~text
POST /roles  {"code":"ELEV_TEST", ...}                              -> 201
PUT  /roles/<ELEV_TEST>/permissions  {ADMIN_ROLE_ASSIGN}             -> 200
~~~

The actor held `ROLE_CREATE` and `ROLE_PERMISSION_ASSIGN` but not `ADMIN_ROLE_ASSIGN`.

**Step 2 is already closed.** ADR-0040's bounded delegation (Task 4) refuses it with
`PERMISSION_NOT_HELD`, because `ADMIN_ROLE_ASSIGN` is a code the actor does not hold.

That leaves the sequence the mission called **Rule A**:

~~~text
1. an administrator who DOES hold ADMIN_ROLE_ASSIGN puts it on a role;
2. a second administrator, who does NOT hold it, assigns that role to a user.   <- not blocked
~~~

Step 2 moves authority without naming a permission. It names a *role*. Every check that inspects the
request body is therefore blind to it.

## Decision

`RoleService`-adjacent assignment refuses a role whose **effective permission set** contains a code
the assigning caller does not hold: 403 `ROLE_ESCALATION_DETECTED`.

Implemented as `RolePermissionPolicy.checkAssignable`, called from `UserService` on both paths that
can attach roles:

| Path | Why both |
|---|---|
| `POST /users` (create) | the path a caller with `USER_CREATE` actually uses |
| `PUT /users/{id}` → `replaceRoles` | the path D-08's reproduction exercised |

Guarding only `replaceRoles` would have left the original escalation reachable through creation —
which is exactly the defect this ADR closes, one endpoint over.

### Why a distinct error code

`PERMISSION_NOT_HELD` says *the caller named a permission it lacks*. That is a statement about the
**request**. `ROLE_ESCALATION_DETECTED` says *the caller named a well-formed role whose content it
cannot fully see*. That is a statement about the **role**. Collapsing them would make an operator
unable to tell a bad request from an over-broad role, and the remediation differs.

### Why the bypass satisfies this too

`UNBOUNDED_PERMISSION_ASSIGN` means, literally, unbounded. A holder is exempt from the check for the
same reason it is exempt from the reserved set: the permission exists to say "this principal is not
bounded by what it holds".

### The ADMIN carve-out

The rule as first written was too strong, and the existing test suite proved it. `ADMIN` carries 144
permission codes, so a caller holding only `ADMIN_ROLE_ASSIGN` can never satisfy a full containment
check for that role. Combined with `MAX_ADMINS_PER_ORG = 1` there would be no path to create an
ADMIN in a fresh organization at all — `ADMIN_ROLE_ASSIGN` would be a dead permission.

So: **holding `ADMIN_ROLE_ASSIGN` satisfies the check for the ADMIN role specifically, and for that
role only.** The permission is, by definition, the authority to hand out the ADMIN role; requiring it
to also imply the 143 other codes was a mistake in the guard, not a finding about the system.

Every other role is still checked in full.

## Consequences

- **Revocation is never blocked.** The guard is on the *desired* set at assignment time; removing a
  role needs no permission the remover lacks. Consistent with ADR-0040, which constrains granting
  and not revoking — a control that made emergencies harder to fix would be a net loss.
- **Delegation becomes transitive-by-permission, not transitive-by-trust.** An ADMIN can hand out
  exactly the roles whose content it could have built itself. That is the intended ceiling for the
  organization tier.
- **A one-role deployment sees no behaviour change.** `STAFF`, `STUDENT`, `FINANCE`, `SUPPORT`,
  `REVIEWER` hold only codes any ADMIN holds, so ordinary user administration is unaffected —
  asserted by a test rather than assumed.

## Residuals

| Residual | Why it stays open |
|---|---|
| The check reads the caller's cached effective permissions | a stale cache can briefly permit an over-broad assignment; the window is bounded by the 5-minute TTL and by per-user eviction on every role change |
| `role` is still globally scoped | D-05. An ADMIN that cannot see another organization's roles can still be assigned one by id. Gate B's org-scoped roles close this; until then the check above is what bounds it |
| `MAX_ADMINS_PER_ORG = 1` prevented performing the two-account step live | asserted by test, not demonstrated against the running stack |