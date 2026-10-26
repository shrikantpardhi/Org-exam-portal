# ADR-0045 — organization-owned roles (shape S2)

- **Status:** Accepted (2026-10-26), on Gate B approval
- **Closes:** D-05, partially — see Consequences
- **Related:** ADR-0032 (SUPER_ADMIN role), ADR-0038 (role immutability), ADR-0040 (bounded delegation)

## Context

`04-Schema` §25.3 defines `role` with no `organization_id`; only `user`, `organization_setting` and
`organization_analytics` carry one. So an ADMIN of organization A could read and edit organization
B's roles, and every organization saw every other organization's catalogue.

Gate B was asked not "add the column" but **may two organizations hold a role with the same code?**
Three shapes were offered; **S2 was chosen** over the recommended S1.

## Decision

`role.organization_id` is nullable, with these meanings:

| Value | Meaning |
|---|---|
| `NULL` | platform-wide — shared by, and visible to, every organization |
| a UUID | owned by that organization — visible only to it |

`V35` adds the column, replaces the global unique on `code` with two partial uniques, and leaves the
seven seeded roles `NULL`.

### Why two partial unique indexes

PostgreSQL treats NULLs as distinct, so a plain `UNIQUE (organization_id, code)` would not constrain
the platform-wide rows at all — two roles could both be `code = 'ADMIN'` with `organization_id IS
NULL`. The pair is therefore:

~~~sql
CREATE UNIQUE INDEX uk_role_code_platform     ON role (code) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uk_role_code_organization ON role (organization_id, code)
    WHERE organization_id IS NOT NULL;
~~~

### The lookup rule that makes this safe

Every role resolved from `IdentityConstants` (`ADMIN`, `SUPER_ADMIN`, `STUDENT`, …) is a seeded,
platform-wide role. All 13 call sites were changed from `findByCode(code)` to
`findByCodeAndOrganizationIdIsNull(code)`, and the unscoped variant was **removed from the
repository** rather than left available. That is deliberate: leaving it would let a future caller
silently resolve a tenant's role where a platform role was meant.

### Creation always stamps an owner

`RoleService.create` sets `organization_id` from the caller's own organization and refuses when it
cannot be resolved. A platform-wide role is therefore **not creatable through the API** — it comes
from seeds and migrations only. Granting that would let one organization mint a role every other
organization must then see, which is D-05 in a new form.

### Read scope

`GET /roles` returns the caller's own roles plus the platform-wide ones. The scope comes from the
actor, never from a request parameter. An unattributable caller sees nothing (fail closed, G-10).

**The system administrator is exempt from this scope.** Scoping every caller to its own organization
scoped `SUPER_ADMIN` too, hiding tenant-owned roles from the tier whose job (R1) is to administer the
whole installation. The exemption is keyed on **holding the `SUPER_ADMIN` role**, not on residing in
the `PLATFORM` organization: membership is what the requirement is about, and a residency rule would be
narrower than R1 and untestable without violating ADR-0032's one-owner invariant.

This was a real regression, shipped by the first version of this change and caught by live
verification rather than by the suite — because every fixture role was platform-wide, the one state in
which the buggy and correct behaviour are identical. `PlatformRoleVisibilityIntegrationTest` pins both
directions: the platform tier sees a tenant role, and a tenant still cannot see another tenant's.

## Consequences

### R2 is PARTIALLY met, not met

Under S2 the seven seeded roles stay shared. Therefore:

| Behaviour | Status |
|---|---|
| An ADMIN edits a role **it created** | isolated — R2 met |
| An ADMIN edits `STAFF`, `STUDENT`, `REVIEWER`, `SUPPORT`, `FINANCE` | **affects every organization** — R2 not met |
| An ADMIN edits `ADMIN` | affects every organization's administrator |
| An ADMIN edits `SUPER_ADMIN` | refused (ADR-0038) |

`OrganizationScopedRoleIntegrationTest.aSharedSeededRoleIsStillEditableAcrossOrganizations` asserts
this residual as **current behaviour**, so that moving to S1 later makes it fail and forces R2's
status to be re-graded. A test that documents a known limitation is more useful than a comment,
because it fails at the moment the limitation stops being true.

### What is now genuinely fixed

- An organization's **own** roles no longer leak into another organization's catalogue.
- The schema can carry ownership, so the S1 backfill is a data change, not a redesign.
- Every `IdentityConstants` lookup is explicit about meaning platform-wide.

### What S1 would add later

Copy the seeded roles per organization at creation time, set their `organization_id`, and flip
`findByCodeAndOrganizationIdIsNull` to a scoped lookup for tenant paths. **No guard logic changes** —
the plumbing built here is what S1 needs.

## Residuals

| Residual | Why it stays open |
|---|---|
| Seeded roles remain shared | the accepted consequence of S2 |
| `permission` is still global | not in D-05's scope; permissions are a platform catalogue by nature |
| `UserService.create` accepts role ids from the client | now bounded by ADR-0044's `checkAssignable`, and a cross-organization id resolves to a role the caller cannot see, which is refused |