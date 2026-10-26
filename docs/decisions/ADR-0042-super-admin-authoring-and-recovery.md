# ADR-0042 — SUPER_ADMIN role authoring and administrator recovery

- **Status:** Accepted (2026-10-26), on Gate A approval
- **Supersedes:** the sentence in `specs/10-P1-Identity-V1.5.md` §63 that places administrator
  recovery outside the API
- **Related:** ADR-0032 (SUPER_ADMIN role), ADR-0034 (password-reset delivery), ADR-0038 (role
  immutability), ADR-0039 (reserved set), ADR-0040 (bounded delegation), ADR-0044 (D-08 Rule A)
- **Closes:** D-01's authoring question, D-06

## Context

Gate A asked two questions and answered both by widening the platform tier's capability.

**Question 1 — may SUPER_ADMIN author roles?** Requirement R1 says the SUPER_ADMIN *"manages
organizations, admins, roles, permissions"*. The frozen specification did not settle whether
*manages* means *supervises* or *authors*. `V32` (D-01) took the narrow reading and granted only the
two catalogue reads, so the platform seeds screens worked and role authoring stayed with each
organization's ADMIN.

**Question 2 — how does an ADMIN recover from a lockout?** `10-P1` §63 says:

> Emergency path: ... recovery is a documented direct database procedure operated by a DBA under
> change control, **not a UI feature**.

That was written when a business organization could hold several ADMINs: a locked-out administrator
could be replaced. ADR-0032 then capped the role at one per organization (`MAX_ADMINS_PER_ORG = 1`,
`V29`), which removed the replacement path. The only recovery left was a database session — and, as
D-07's reproduction showed, a Redis key deletion on top of it, because a negative authorization
result is cached for its five-minute TTL.

## Decision

### 1. SUPER_ADMIN authors roles, using create-then-assign

`V34` grants `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN` to `SUPER_ADMIN`.

`CreateRoleRequest` is **not** changed: it keeps `code`, `name`, `description`, and permissions are
assigned by a second `PUT /roles/{id}/permissions`. This matters because adding a `permissions` field
to a creation DTO is an additive change to a frozen contract (`05-API-Contract` §24), which would
have needed its own amendment. Create-then-assign needs none, and it exercises the same guarded code
path (`replacePermissions`) that every other assignment uses.

### 2. SUPER_ADMIN holds the reserved-set bypass

`V34` also grants `UNBOUNDED_PERMISSION_ASSIGN`. Without it, bounded delegation (ADR-0040) would
confine the installation owner to the sixteen codes it holds, so authoring could not build any role
carrying a `PLATFORM_*` code or `ADMIN_ROLE_ASSIGN` — a capability unable to perform its own purpose.

### 3. Two recovery endpoints, both organization-scoped

| Endpoint | Effect |
|---|---|
| `POST /api/v1/platform/organizations/{orgId}/admins/{userId}/reset-password` | issues a single-use reset token through `PasswordResetDelivery`; forces a password change on next login |
| `POST /api/v1/platform/organizations/{orgId}/admins/{userId}/unlock-sessions` | revokes every active session and evicts the authorization cache |

Both require the new `PLATFORM_ADMIN_RECOVER`. The organization is in the path **and** checked
against the target's own `organization_id`, so an operator cannot reach an administrator by guessing a
user id. Every call is audited (`ADMIN_RECOVER`, `ADMIN_UNLOCK`).

### 4. A cross-organization user list, read-only

`GET /api/v1/platform/users` with `PLATFORM_ADMIN_VIEW`. Paginated; filters `search`, `status`,
`organizationId`; returns `id`, names, `email`, `status`, `organizationId`, `organizationCode`,
`roles`.

There is **no** `PUT` or `DELETE` on this path. Platform-tier read across organizations is
supervision; platform-tier *write* across organizations is the cross-tenant mutation requirement R2
forbids, and adding it would have re-opened D-10 at a higher tier.

## Refusal order in the recovery path

| # | Condition | Response |
|---|---|---|
| 1 | organization does not exist | 404 `NOT_FOUND` |
| 2 | organization is `PLATFORM` | 404 `NOT_FOUND` — the system organization is not a tenant and has no recoverable administrator |
| 3 | user does not exist, or is not in that organization | 404 `NOT_FOUND` — never confirms cross-organization existence |
| 4 | user is ARCHIVED | 422 `USER_ARCHIVED` |
| 5 | user does not hold `ADMIN` | 422 `NOT_AN_ADMIN` |
| 6 | user is the SUPER_ADMIN account | 409 `SUPER_ADMIN_IMMUTABLE` |

Rule 6 sits after 5 deliberately in the *code* and produces the **more specific** answer for the case
that would otherwise be reported as 422: a SUPER_ADMIN is not an ADMIN either, but telling an operator
"not an administrator" would send them looking for a data problem that does not exist. The
SUPER_ADMIN's own lockout stays a DBA procedure — there is nobody above the installation owner.

## Consequences

- **`SuperAdminPermissionCatalogueTest` was edited** (10 codes → 16). This is the forcing function
  Task 6 built: the owner's capability cannot widen without a reviewer seeing the edit.
- **D-05's weight increases.** `SUPER_ADMIN` and every organization's `ADMIN` now write the *same
  global role rows*. Gate B's org-scoped roles become more urgent, not less.
- **`RESERVED_PERMISSION_DENIED` is unreachable for `SUPER_ADMIN`.** It stays reachable for `ADMIN`
  and every other principal, which is the case D-03 was about. Pinned by a test asserting the ADMIN
  refusal still fires after the bypass was granted.
- **Recovery cannot grant roles.** An operator who could recover an account *and* re-permission it
  would be a second, quieter path to the escalation ADR-0040 closes. Not built, deliberately.
- **No password disclosure.** The endpoint never returns or sets a password; it issues a token.

## Residuals

| Residual | Why it stays open |
|---|---|
| A locked-out SUPER_ADMIN still needs a DBA | It is the recovery principal; nothing is above it |
| Recovery depends on the target's mailbox | If the mailbox is the compromised asset, the DBA path remains |
| Cache eviction is explicit only on this path | Any other out-of-band repair still needs a manual `exam:authz:perms:<userId>` deletion; D-06's unlock endpoint mitigates the common case, not the general one |