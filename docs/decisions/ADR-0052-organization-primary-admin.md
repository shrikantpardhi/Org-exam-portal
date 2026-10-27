# ADR-0052 — An organization has one primary administrator

- **Status:** Proposed
- **Date:** 2026-10-26
- **Amends:** `specs/04-Database-Schema-V1.5.md` §25.2 (`organization`), `specs/10-P1-Identity-V1.5.md` §63
- **Related:** ADR-0032 (admin cap), ADR-0042 (recovery), ADR-0053 (platform admin management)
- **Amended by:** ADR-0054 (2026-10-27) — primacy transfer implemented, cap raised, archiving made reversible

## Context

`MAX_ADMINS_PER_ORG` allowed exactly one administrator per organization, which made "who may change
another user's roles" trivial to answer: there was only ever one candidate. Raising the cap to allow
several administrators removes that answer.

With several administrators and no designated authority, any of them could revoke the others' ADMIN
assignments — including the account created to own that decision. The last-admin guard in
`UserService` (`LAST_ADMIN`) stops the organization being emptied, but it does not stop the *wrong*
administrator from ending up in charge. The permission `ADMIN_ROLE_ASSIGN` does not help either: it is
held by whoever needs to assign roles, so it cannot distinguish two holders.

A designated authority is required. It must be:

1. **Unique per organization** — two primaries is the problem it exists to solve.
2. **Explicit** — inferring it from `created_on` would silently hand the power to whoever happened to
   be oldest, which is not a decision anyone made.
3. **Recoverable** — an organization must not be able to enter a state it cannot leave.

## Decision

1. Add `organization.primary_admin_user_id UUID NULL` with a foreign key to `user(id)` and
   `ON DELETE SET NULL` (`V39`). Uniqueness per organization is a schema property, not a convention.
2. `UserService.replaceRoles` gains `guardPrimaryAdminAuthority`: when the actor is an ADMIN of the same
   organization and is not its primary administrator, an attempt to change another user's roles raises
   `PRIMARY_ADMIN_REQUIRED` (403).
3. **Fail-closed:** if the organization has no primary (`NULL`), role editing is refused for everyone in
   it. The message tells the operator to have a primary designated.
4. The first administrator created for an organization becomes its primary, set by the existing
   `POST /platform/organizations/{id}/admins` path. This is the escape from the fail-closed state, so
   that endpoint is deliberately *not* itself subject to the guard.
5. **No backfill.** Existing organizations keep `NULL` and acquire a primary on their next administrator
   creation.
6. The primary administrator cannot be deactivated while designated (ADR-0053 returns 409), because the
   organization would otherwise lose the only account able to reassign roles.

## Why not a flag on user_role

`user_role.is_primary` would permit several primaries per organization — the guard would then be
"any primary may edit," which is not a guard at all. It would also be an amendment to a second frozen
table to express something that is a property of the organization, not of a role assignment.

## Consequences

- Existing deployments behave as before until an organization gains a second administrator: with one
  administrator there is no other user whose roles they could change.
- An organization administrator can still change **their own** profile fields; only role changes are
  restricted. Self role-edit remains refused for everyone, primary included (`SELF_ROLE_MODIFICATION`).
- ~~**Transferring primacy is not implemented.**~~ **Superseded 2026-10-27 by ADR-0054.** Governance was
  subsequently specified as "the platform owner decides, audited", and
  `PATCH /api/v1/platform/organizations/{id}/primary-admin` (§72.14) now transfers the designation under
  `PLATFORM_ADMIN_PRIMARY_ASSIGN` (`V43`, SUPER_ADMIN only). GAP-197 is closed. The original caution was
  correct about the missing input, not about the difficulty: the act needed a named decision-maker, and
  once the owner was named the remaining design was small.
- ~~`MAX_ADMINS_PER_ORG` keeps its current seeded value.~~ **Superseded 2026-10-27 by ADR-0054.** `V42`
  raises it from `1` to `5`, which is what makes the primary rule reachable in the first place: with a cap
  of one there is never a second administrator to lead over.

## Amendment (2026-10-27) — ADR-0054

Two properties of this decision changed, and one did not:

1. **The designation is no longer permanent.** The platform owner transfers it (§72.14). The fail-closed
   rule itself is unchanged: no primary means role editing is refused for the whole organization.
2. **The primary can no longer be archived.** With archiving now reversible (ADR-0054), archiving the
   primary would leave the organization with an authority who cannot log in, so
   `DELETE .../admins/{userId}` refuses with `409`. Transfer primacy first, then archive.
3. **Unchanged:** the primary is still the only account permitted to change other users' roles, and a
   newly created administrator is still designated implicitly when the organization has none. That
   implicit designation is deliberately *not* permission-gated — it is the only path out of the
   fail-closed state, and gating it would make that state permanent.
