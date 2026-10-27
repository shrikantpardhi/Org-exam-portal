# ADR-0053 — Platform administrator management endpoints

- **Status:** Proposed
- **Date:** 2026-10-26
- **Amends:** `specs/05-API-Contract-V1.5.md` §72 (new §72.7-§72.10)
- **Related:** ADR-0032, ADR-0042, ADR-0052

## Context

`specs/05` §72.5 defines exactly one administrator endpoint:

```http
POST /api/v1/platform/organizations/{id}/admins
```

Creation only. Consequences:

- An organization's administrator list cannot be read. `/api/v1/platform/users` lists platform users
  with their organization, but not *per organization*, not filtered to administrators, and not
  paginated per organization. The organization detail screen can only show `adminCount`.
- An administrator's roles cannot be corrected. A wrong role assignment needs a database change.
- An administrator cannot be removed. The ownership creation establishes is permanent.

A typo in the invitation email therefore leaves an organization with an administrator nobody can
correct or replace. That is a real operational trap, not a completeness argument.

## Decision

Add three endpoints under `/api/v1/platform/organizations/{orgId}/admins`:

| Method | Path | Permission | Behaviour |
|---|---|---|---|
| GET | (collection) | `PLATFORM_ADMIN_VIEW` | Paginated, org-scoped, filterable by `search` (and by `status` since ADR-0054) |
| PATCH | `/{userId}/roles` | `PLATFORM_ADMIN_ROLE_UPDATE` | Replace the non-ADMIN roles |
| DELETE | `/{userId}` | `PLATFORM_ADMIN_DEACTIVATE` | ~~Deactivate the account~~ **Archives it, reversibly (ADR-0054)** |

Two new permissions (`V41`), granted to `SUPER_ADMIN` only.

**Extended by ADR-0054** (2026-10-27): `PATCH /{id}/primary-admin` under
`PLATFORM_ADMIN_PRIMARY_ASSIGN` (`V43`), and `POST /{userId}/restore` under
`PLATFORM_ADMIN_DEACTIVATE`. `DELETE` now writes `ARCHIVED` instead of `INACTIVE`; see the amendment
at the end of this record.

### Deliberate limits

- **`PATCH` cannot add or remove ADMIN.** Administrator membership is governed by the cap and the
  primary-admin rule; a second path to it would be a second, unaudited way to reach a privileged state.
  It also refuses `SUPER_ADMIN` outright — a platform-tier capability must never be grantable from an
  organization screen.
- **`DELETE` deactivates, never deletes.** The account owns audit history and may own content;
  `specs/10-P1` retains user rows. It refuses the primary administrator (409) and refuses the last
  active administrator (409). **Amended by ADR-0054:** the retirement state is now `ARCHIVED` rather
  than `INACTIVE`, and it is reversible via `POST /{userId}/restore`; both refusals are retained.
- ~~**No `make-primary` endpoint.**~~ **Superseded by ADR-0054.** Governance was specified as "the
  platform owner decides, audited", so `PATCH /{id}/primary-admin` now exists under
  `PLATFORM_ADMIN_PRIMARY_ASSIGN`. GAP-197 is closed. The original caution was right about the missing
  input, not the difficulty.
- Cross-organization targets return **404**, not 403: a 403 would confirm the id exists elsewhere.

## Amendment (2026-10-27) — ADR-0054

The administrator surface became a full lifecycle:

1. **`DELETE` archives reversibly.** Status becomes `ARCHIVED`, sessions are expired and the permission
   cache evicted, but the `user_role` row is deliberately left `ACTIVE` so restore needs no snapshot.
   Archived administrators stay listable with `?status=ARCHIVED` — visible as archived, as requested.
2. **`POST /{userId}/restore`** restores the account and its ADMIN assignment; extra roles are left as
   they were and remain editable through `PATCH /{userId}/roles`.
3. **`PATCH /{id}/primary-admin`** transfers primacy under its own, narrower permission.
4. **`PATCH /{userId}/roles` now calls `RolePermissionPolicy.checkAssignable`.** Without it this
   endpoint was a privilege-escalation path (D-08 Rule A) that the equivalent organization-side path
   already closed — the sharpest defect ADR-0053 left behind.
5. The administrator cap was raised from 1 to 5 (`V42`), without which none of the above was reachable.

## Consequences

- The platform owner can now see and correct an organization's administrators, which is what makes
  a designated primary (ADR-0052) practically assignable.
- `SuperAdminPermissionCatalogueTest` grows by two codes — the pinned-set test is the forcing function
  that makes this visible in review.
- The new `GET` is additive; existing clients are unaffected.
