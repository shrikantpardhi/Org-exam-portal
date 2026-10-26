# ADR-0039 — Reserved permission set

- **Status:** Accepted
- **Phase:** Permission model hardening (D-03)
- **Spec:** `specs/20-P11-Administration-V1.5.md` §17; `specs/10-P1-Identity-V1.5.md` §52, §65
- **Amends:** ADR-0032 ("granted to `SUPER_ADMIN` only" — now enforced, not merely stated)
- **Closes:** D-03
- **Migration:** `V33__reserved_permission_set.sql`

## Context

Two frozen statements say platform permissions belong to the installation owner alone:

- `10-P1` §65 — `ADMIN_ROLE_ASSIGN` is listed among "reserved-but-not-granted admin permissions".
- ADR-0032 — "The platform permission catalogue (`PLATFORM_*`) is granted to `SUPER_ADMIN` only."

Neither was enforced. `RoleService.replacePermissions` validated only that the supplied permission
ids **exist**. Reproduced live: an ADMIN placed `PLATFORM_ORGANIZATION_VIEW` on STAFF and received
200 (D-03).

The catalogue read path made this worse than the brief assumed: an earlier draft of the D-03 record
claimed the ADMIN could not *discover* the reserved codes. That was a single-page artefact of
`size=100` against a 163-code catalogue sorted by code. Scanning every page shows all eight
`PLATFORM_*` codes and `ADMIN_ROLE_ASSIGN`. The ADMIN can discover **and** assign them.

## Decision

The reserved set is declared as **data**, in the `RESERVED_PERMISSIONS` business rule:

~~~json
{
  "prefixes": ["PLATFORM_"],
  "codes": ["UNBOUNDED_PERMISSION_ASSIGN"],
  "bypassPermission": "UNBOUNDED_PERMISSION_ASSIGN"
}
~~~

and enforced by `RolePermissionPolicy.checkReserved`, called from `RoleService.replacePermissions`.
A reserved code may be placed only by a caller holding `UNBOUNDED_PERMISSION_ASSIGN`; everyone else
gets `403 RESERVED_PERMISSION_DENIED`.

Five properties are deliberate:

1. **Data, not code** (ADR-0023 decision 6). A future `PLATFORM_*` permission is covered by the
   prefix rule without a code change and without a list to keep in sync. A future non-prefixed
   reserved code is one row edit, not a release.
2. **Fail closed** (G-10). A missing, non-ACTIVE, or unreadable rule makes *every* permission
   reserved and leaves the bypass held by nobody, so `PUT /roles/{id}/permissions` refuses
   everything. The unsafe alternative — treating an unreadable rule as "nothing is reserved" — would
   turn a deleted row into a privilege escalation.
3. **The bypass is granted to nobody** in `V33`, following the `ADMIN_ROLE_ASSIGN` precedent
   ("reserved-but-not-granted"). The capability is defined, named, and testable, but no seeded
   principal holds it. A legitimate change is a migration — reviewable, ordered, replayed.
4. **Checked before delegation, after immutability.** Precedence is fixed in ADR-0038.
5. **The refusal is a distinct code.** `RESERVED_PERMISSION_DENIED` (structural: nobody may place
   this without the bypass) is not `PERMISSION_NOT_HELD` (authority: *you* do not hold this). A test
   asserts both in one place so a regression in either is visible.

## Why not an enum or a constant

A Java constant would satisfy §17 today and fail the next time a permission ships. ADR-0023
decision 6 already rejected code-as-policy for this module; the reserved set is exactly the kind of
policy that decision was written for.

## Why `UNBOUNDED_PERMISSION_ASSIGN` rather than reusing `SUPER_ADMIN`

Reusing the role would make the permission surface depend on role membership, which `10-P1` §9
explicitly forbids ("Do not encode business authorization as `if role == ADMIN` ... prefer permission
checks"). A named permission is grantable, revocable, auditable, and testable in isolation.

## Consequences

- `PUT /roles/{id}/permissions` returns `403 RESERVED_PERMISSION_DENIED` for `PLATFORM_*` and
  `UNBOUNDED_PERMISSION_ASSIGN` to every caller without the bypass.
- A mixed request (allowed + reserved) is refused as a whole and writes nothing: the check runs
  before any `role_permission` row is touched.
- `V33` adds one permission (163 → 164) and one business rule.
- `ReservedPermissionIntegrationTest` (5 tests) covers the refusal, the mixed-request atomicity, the
  ungranted bypass, the satisfiable bypass, and the rule's data declaration.
- The `RESERVED_PERMISSIONS` rule is editable through `PUT /business-rules/{id}`, which is
  ADMIN-reachable. That is acceptable **only** because widening the rule narrows what may be
  assigned; shrinking it widens — so the rule row itself is a privileged write and is recorded in the
  Gate B memo as a residual to re-examine.

## Rollback

Delete the `RESERVED_PERMISSIONS` row. `RolePermissionPolicy` then fails closed and refuses **all**
permission assignment — an intentionally unusable state, so a rollback must also remove the
`checkReserved` call site. This asymmetry is the point of fail-closed and is recorded here so an
operator is not surprised by it.