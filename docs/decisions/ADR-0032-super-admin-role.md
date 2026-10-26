# ADR-0032 — SUPER_ADMIN installation-owner role

- **Status:** Accepted
- **Phase:** SUPER_ADMIN / ADMIN UI (amendment raised by the product owner's install-owner requirement)
- **Spec:** `specs/10-P1-Identity-V1.5.md` §9, §63; `specs/20-P11-Administration-V1.5.md` §18
- **Closes:** GAP-116 (SUPER_ADMIN permissions are now seeded)
- **Migration:** `V29__super_admin_and_platform.sql`

## Context

The frozen role catalogue (`10-P1 §9`, `§64`) seeds exactly six roles — `ADMIN`,
`REVIEWER`, `STAFF`, `STUDENT`, `SUPPORT`, `FINANCE` — and has no notion of an
installation owner. The product owner requires a `SUPER_ADMIN` who:

1. is created once at bootstrap and belongs to no business organization;
2. creates the first business organization and that organization's `ADMIN`;
3. is the only principal who can cross organization boundaries (a V2 concern).

That is a role, not a permission: it must be carried in the JWT `roles` claim so the
frontend can route a login to the platform home, and it must be provable without
resolving the whole permission set first.

## Decision

Add a `SUPER_ADMIN` role to the catalogue. It is:

- **Permanent** — no API or service path creates it; it is seeded by `V29` and its
  single user by the environment-gated bootstrap CLI.
- **Unique** — exactly one `SUPER_ADMIN` user per deployment.
- **Non-demotable** — a `SUPER_ADMIN` user cannot have the role revoked.
- **Non-archivable / non-inactivatable** — the role and its user cannot be archived,
  inactivated, or otherwise taken out of service at runtime.
- **Non-creatable at runtime** — neither `SUPER_ADMIN` itself nor any other principal
  may create another `SUPER_ADMIN` user.

Three rules are appended to `20-P11 §18` (privileged role change policy):

1. `SUPER_ADMIN` cannot be demoted.
2. `SUPER_ADMIN` cannot be archived.
3. The last active `SUPER_ADMIN` is a protected invariant.

Because the owner also decided **one ADMIN per organization** (where the frozen P11
model allowed several), `V29` seeds `business_rule` `MAX_ADMINS_PER_ORG = 1` and
`UserService.assignRole` enforces the cap (see C3).

## Rejected alternatives

- **SUPER_ADMIN as a permission.** Rejected: permissions are granted per-role and a
  bare permission grants no identity, no role-claim for routing, and no notion of
  uniqueness. It would also allow the privilege to be attached to any role by any
  future catalog edit, which is the opposite of "one installation owner".
- **SUPER_ADMIN as a header flag / trusted client claim.** Rejected: `10-P1 §38`
  (JWT payload principle) forbids trusting client-supplied role claims; a header is a
  client claim by another name.

## Consequences

- `SUPER_ADMIN` joins `10-P1 §9` with a "see ADR-0032" note; the three rules join
  `20-P11 §18`.
- The platform permission catalogue (`PLATFORM_*`) is granted to `SUPER_ADMIN` only, **plus the
  base role/permission read permissions required by the platform seeds screens** (`ROLE_VIEW`,
  `PERMISSION_VIEW`; `V32`, D-01). That sentence describes the *scope* of the grant, not an
  exhaustive set: the seeds screens (`docs/frontend/super-admin-flow.md` §12-§13) are specified as
  read-only views of both catalogues, and `10-P1 §68` gates those reads on `ROLE_VIEW` and
  `PERMISSION_VIEW`. Read as exhaustive, the original sentence withheld the permission its own
  screens require. Authoring permissions (`ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN`)
  remain ungranted and are Gate A's decision.
- `MAX_ADMINS_PER_ORG = 1` supersedes the previous multi-admin reading of
  `20-P11`; the existing P11 multiple-admin test is rewritten to assert the cap in
  the same change set.

## Rollback

`DROP` the `SUPER_ADMIN` role is insufficient and would leave the bootstrap CLI
orphaned. A correct rollback removes the bootstrap dependency first, then the role
and its grants. This ADR records that dependency: the bootstrap CLI must be disabled
or removed before the role is dropped, otherwise a fresh install would boot without
its installation owner.
