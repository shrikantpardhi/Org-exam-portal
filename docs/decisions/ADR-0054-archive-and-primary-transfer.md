# ADR-0054 — Reversible archive and primary-administrator transfer

- **Status:** Proposed
- **Date:** 2026-10-27
- **Supersedes:** none
- **Amends:** ADR-0052 (primary administrator), ADR-0053 (platform administrator management)
- **Closes:** GAP-197 (no way to change an organization's primary administrator)
- **Opens:** GAP-200 (no restore counterpart on the organization-side `/admin/users` surface)

## Context

ADR-0052 made one organization administrator — the **primary** — the only account permitted to
change other users' roles, and designated that account implicitly when the first administrator was
created. ADR-0053 then gave the platform owner endpoints to create, list, edit and deactivate
administrators. Read together, they produced three defects that became visible as soon as the
feature was used:

1. **Primacy was permanent.** Nothing could change `organization.primary_admin_user_id` after
   creation. If the primary left the organization, the only route was to delete their account,
   which destroys exactly the audit history the system is required to retain. Recorded as GAP-197.
2. **Deactivation was one-way and destroyed role history.** `deactivateAdmin` set both the account
   and its `ADMIN` assignment to `INACTIVE`. Reinstating the administrator later was impossible
   through the platform surface, and the role evidence was overwritten rather than retained.
3. **Multi-administrator configurations were unreachable.** `MAX_ADMINS_PER_ORG` was seeded at
   `1`, so an organization could never hold a second administrator — which makes "choose which one
   leads" and "give an administrator extra roles" vacuous.

Product direction (2026-10-27): the platform owner chooses and can change the primary administrator;
the owner may assign, edit and remove any number of roles on an organization administrator; and
retiring an administrator is an **archive** that is visible as archived and can be reversed,
because the record is wanted "for history purpose".

## Decision

### 1. Archiving is a status change, not a deletion, and it is reversible

`DELETE /api/v1/platform/organizations/{id}/admins/{userId}` now sets the account to
`Status.ARCHIVED` and a new `POST .../admins/{userId}/restore` reverses it. The row, its audit
history and its role assignments all survive; this reuses the existing `Status` enum, so no schema
change is required.

### 2. The `ADMIN` role assignment stays ACTIVE while the account is archived

This is the load-bearing decision, and it looks wrong at first glance — an archived administrator
holding an active role assignment. It is deliberate:

- **Restore must not need a snapshot.** Re-activating an assignment that was never deactivated is a
  one-field write. If archiving dropped the assignment, restore would have to reconstruct the role
  set from somewhere, and the only faithful source would be an archive-history table — a new table,
  which frozen rule 1 forbids inventing for convenience.
- **The cap query must therefore exclude archived accounts.** `countActiveAssignmentsByRoleCodeInOrganization`
  and `findActiveUserIdsByRoleCodeInOrganization` gained `u.status <> ARCHIVED`. Without it an
  archived administrator occupies a cap slot forever and no replacement can be created — archive
  would be a slower spelling of delete.
- **`INACTIVE` is not excluded.** An account someone deactivated by other means still counts toward
  the cap. That is a state an operator has to resolve, not a free slot, and silently treating it as
  free would let the cap be exceeded without anyone deciding to.

Access revocation does not depend on clearing the assignment, because
`PermissionRepository.findEffectivePermissionCodes` already filters `user.status = ACTIVE`. An
archived account resolves to **no permissions**. But authorization is decided from a *cached*
permission set and never re-reads account status per request, so archiving **must** also expire the
account's sessions and evict its permission cache (`SessionService.expireAllForUser` +
`AuthorizationService.evictAfterCommit`). Without those two calls an archived administrator keeps
working until their token expires. `UserService.archive` already did this; the platform path did not.

### 3. Primacy transfer is a separate, narrower permission

New endpoint `PATCH /{id}/primary-admin`, gated by `PLATFORM_ADMIN_PRIMARY_ASSIGN` (`V43`), which
`SUPER_ADMIN` holds and nothing below does. Not folded into `PLATFORM_ADMIN_ROLE_UPDATE`: editing an
administrator's roles changes what one account may do, while transferring primacy changes **who may
edit everyone else's roles**. The second is strictly stronger and should be revocable independently —
the same reasoning `V41` used to split `VIEW` from `CREATE`.

Refusals and their reasons: an archived target is `409` (pointing role-editing authority at an
account that cannot log in would lock the organization out of its own administration); a user of
another organization is `404`, never `403` (ADR-0051 cross-tenant rule); re-designating the current
primary is a successful no-op, so a retry is safe.

### 4. Creating with an explicit primary is allowed, implicit designation is not permission-gated

`POST .../admins` gained optional `makePrimary`. When true the created account becomes primary and
the caller must hold `PLATFORM_ADMIN_PRIMARY_ASSIGN`. When the organization has **no** primary, the
created account is designated regardless of that permission — it is the only recovery path out of
the fail-closed state ADR-0052 created, and gating it would make that state permanent.

### 5. The cap is raised to 5

`V42` updates the `MAX_ADMINS_PER_ORG` rule from `1` to `5`. Product decision, not an architectural
one, but recorded here because it is what makes decisions 1–4 reachable at all. It is an `UPDATE` of
the existing row, not a new rule: the code, meaning and readers are unchanged, and `business_rule`
has `UNIQUE(code)` so a second row is impossible by design.

### 6. Role editing is validated as a whole set and escalation-checked

`PATCH .../admins/{userId}/roles` resolves every code, validates the complete set, and only then
mutates — a partially-invalid request changes nothing. It now also calls
`RolePermissionPolicy.checkAssignable`, matching `UserService.replaceRoles`. Without that call the
endpoint was a privilege-escalation hole: a caller could attach a role carrying permissions it does
not hold (D-08 Rule A, ADR-0044). The `ADMIN`/`SUPER_ADMIN` codes remain refused: membership is
governed by the cap and the primary rule, and a platform-tier role must never be grantable from an
organization screen.

## Consequences

**Positive.** GAP-197 closes. Retirement is reversible, so history is retained instead of
overwritten. The organization screen can display a truthful distinction between currently-serving and
archived administrators. Role editing is no longer an escalation path.

**Negative.** "Archived account with an ACTIVE role assignment" is an invariant a future reader may
mistake for a bug and "fix" by clearing the assignment — which would silently break restore. That is
why the exclusion is documented at the query, not only here. The admin-count and both cap guards now
depend on the same exclusion, so a change to one of them in isolation would make the cap and the
displayed count disagree.

**Neutral.** `MAX_ADMINS_PER_ORG = 5` is a policy number, not a design one; per-organization override
remains unimplemented and would be a separate change to rule scoping.

## Alternatives considered

- **Delete the account.** Rejected: violates §10-P1 user-row retention and destroys audit history,
  which is precisely what the request asked to preserve.
- **Archive by clearing the role assignment.** Rejected above: makes restore require a snapshot or a
  new history table, and loses the role evidence the archive is meant to keep.
- **Archive-history table.** Considered and rejected for this turn: frozen rule 1 forbids inventing
  tables, and the requirement ("visible as archived, un-archivable, kept for history") is met by
  status plus audit rows. It would become necessary only if "restore exactly the previous role set"
  were required — that option was offered and declined.
- **Reuse `PLATFORM_ADMIN_ROLE_UPDATE` for primacy transfer.** Rejected: conflates "what one account
  may do" with "who decides what others may do".
- **Exclude `INACTIVE` as well as `ARCHIVED` from the cap.** Rejected: it would let the cap be
  exceeded by deactivating instead of archiving, with no decision recorded anywhere.

## Verification

`PlatformAdminArchiveAndPrimaryIntegrationTest` covers: creation with and without `makePrimary`;
implicit designation of a first administrator and non-stealing by later ones; transfer, idempotent
retransfer, cross-organization 404 and archived-target 409; archive keeping the role assignment while
removing the account from the default list and from effective permissions; restore; restore refusing
a live account; archiving freeing exactly one cap slot at the cap; the primary and last-administrator
guards; archived accounts being refused for role editing and primacy; whole-set role replacement.

## Related

- ADR-0032 — organization administrator cap (`MAX_ADMINS_PER_ORG`)
- ADR-0044 — D-08 permission model, Rule A (no escalation via role assignment)
- ADR-0051 — cross-tenant 404-not-403 rule
- ADR-0052 — organization primary administrator (amended here)
- ADR-0053 — platform administrator management (amended here)
- `specs/05-API-Contract-V1.5.md` §72.5, §72.10–§72.15
- GAP-197 (closed), GAP-200 (opened)
