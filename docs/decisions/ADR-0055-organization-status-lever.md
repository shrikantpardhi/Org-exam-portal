# ADR-0055 — Organization status is a platform-controlled, enforced lever

- **Status:** Proposed
- **Date:** 2026-10-27
- **Supersedes:** none
- **Amends:** `specs/05-API-Contract-V1.5.md` §72 (adds §72.16)
- **Closes:** GAP-201 (no way to change an organization's status from any surface)

## Context

Two facts existed in the codebase before this record, and neither was visible from the UI:

1. **`organization.status` already gates authentication.** `AuthenticationService` refuses a login
   whose organization is not `ACTIVE` (`:78-79`). Suspending a tenant was therefore *already possible*
   in the data model and *already enforced* by the security layer — there was simply no way to do it.
2. **`PLATFORM_ORGANIZATION_UPDATE` was a dead grant.** `V29` seeded it and granted it to
   `SUPER_ADMIN` (`:42`, `:55`), the permission catalogue lists it, and **no endpoint consumed it**.
   The platform advertised an authority it did not offer.

A status of `INACTIVE` was reachable only by editing the database by hand — the worst of both worlds:
a real, enforced capability with no governed path to invoke it and no audit trail when it happened.

Simultaneously, the platform screens had accumulated redundant prose: every page restated in a
`description` what the breadcrumb trail directly above already said, and `PlatformPageHeader` rendered
a `Platform` eyebrow that duplicated the breadcrumb root on every single screen. That is a separate
concern, but it is the reason the status control needed a home that was not another paragraph of text.

## Decision

### 1. Organization status becomes an explicit, audited platform action

`PATCH /api/v1/platform/organizations/{id}/status` (`specs/05` §72.16), permission
`PLATFORM_ORGANIZATION_UPDATE`. This is the endpoint that gives the existing seeded grant its
purpose; no new permission is invented.

### 2. Only `ACTIVE` and `INACTIVE` are accepted

`ARCHIVED` is refused (`400`). The database check constraint permits it (`V2:45`), but the login gate
collapses every non-`ACTIVE` value to the same outcome, so `ARCHIVED` would introduce a third word
for the second behaviour. Two states an operator can reason about are worth more than three that
differ only in name. If a genuinely different lifecycle state is ever needed — for example a
read-only retention state — it should arrive with behaviour that distinguishes it.

### 3. The consequence is stated wherever the action is offered

`INACTIVE` locks out **every user of the tenant**, not just its administrators. The confirmation dialog
says so in plain words before applying. A control with a security consequence that does not announce
it is worse than no control, because it looks harmless.

### 4. `PLATFORM` is refused

`409`/`404` for the installation's own tenant. `requireBusinessOrganization` already refuses `PLATFORM`
for reads; the same guard applies to a write. Suspending `PLATFORM` would lock out every operator,
including the caller mid-request, with no in-product path back.

### 5. Re-applying the current status does not write an audit row

The call returns `200` but emits nothing. An audit trail exists to record what happened; recording a
change that did not happen is the one failure a trail must not have. A genuine change emits
`ORGANIZATION_STATUS_CHANGE` with the previous and new value.

### 6. Header prose is reduced, and the breadcrumb carries location instead

`PlatformPageHeader` loses its `Platform` eyebrow, and per-page `description` strings that restated the
breadcrumb are removed. Descriptions that carry information a breadcrumb cannot — provider secret
masking, seed catalogue read-only status — are kept and shortened.

Breadcrumbs now render an entity's **name** rather than its raw identifier. A UUID in a breadcrumb is
technically accurate and operationally useless: `11111111 1111 1111 1111 111111111111` tells an
operator nothing, while `Default Organization` tells them where they are. The path stays the source of
the trail — an optional label map substitutes display text only, so routing and links are unaffected.

### 7. Short edits use dialogs, large ones use pages

The administrator roles editor moved from an inline panel into a `FormDialog` built on the existing
`ConfirmDialog` accessibility contract (`role="dialog"`, `aria-modal`, Escape, focus management). The
applied rule: **three fields or fewer and no file upload -> dialog; otherwise -> page.** Zero-field
confirmations stay `ConfirmDialog`.

This also reinforces a guarantee established by ADR-0054's UI work: the role editor must never be
rendered inside a TanStack grid cell. Rendering inputs in a cell recreated the nodes on every
keystroke, dropping focus and swallowing input — a defect found by test, not by reading. A dialog
keeps it outside the table by construction.

## Consequences

**Positive.** A real, already-enforced capability becomes governed and audited. The permission
catalogue stops advertising an authority the API does not offer. Tenant suspension no longer requires
database access. Screens state their location once instead of three times, and an operator reading a
breadcrumb sees a name they recognize.

**Negative.** `INACTIVE` is a blunt instrument: it locks out every user, including administrators, with
no grace period and no per-user exception. That is acceptable for a suspension lever but must not be
mistaken for a licensing or billing control. A future read-only or grace-period state would need the
behaviour to justify the extra state, per decision 2.

**Neutral.** The system screen no longer renders git commit and build date. Those remain on the DTO —
removing them would change a frozen response shape for a display preference. Service status now lists
four components instead of two, because `PlatformInfoService` was filtering out `APPLICATION`,
`BACKGROUND_WORKERS` and the cache fallback that the health service had already computed: the screen
showed two green dots while the application's own database access sat unreported.

## Alternatives considered

- **Offer `ARCHIVED` as a third state.** Rejected per decision 2 — no distinct behaviour to justify it.
- **Forbid suspending an organization that still has administrators.** Rejected: it would make the
  lever unusable precisely when a tenant must be switched off, and would let an administrator block
  the platform owner by existing.
- **Derive the status from the presence of administrators / users.** Rejected: status is an operator
  judgement about the tenant, not an inference from its contents.
- **Add a new `PLATFORM_ORGANIZATION_STATUS` permission.** Rejected: `PLATFORM_ORGANIZATION_UPDATE`
  already names exactly this authority and was already granted. Minting a second code would leave the
  first permanently dead.
- **Rewrite breadcrumbs to be data-driven instead of path-derived.** Rejected as disproportionate and
  riskier: the path is a correct and testable source of the trail, and only *display text* needed to
  change. A label override achieves the requirement without changing how the trail is computed.
- **Keep the roles editor as a panel.** Rejected per request 3 and because the dialog is a strictly
  better home for a short form on a data-dense screen.

## Verification

`PlatformOrganizationStatusIntegrationTest` (8 tests): suspend and reinstate; **a user of a suspended
organization cannot log in (401) and regains access when it is reinstated** — the assertion that proves
the lever is real; idempotent re-set writes no audit row; `PLATFORM` refused; `ARCHIVED` refused with
400; missing status 400; an organization ADMIN gets 403 for its own tenant; unknown organization 404.

Frontend: breadcrumb renders the organization name rather than the UUID and still links; the roles
editor opens and closes as a dialog while still submitting exactly the ticked codes; the status dialog
warns only when moving away from `ACTIVE`; the system screen renders neither git commit nor build date.

## Related

- ADR-0054 — reversible administrator archive (whose dialog/panel lesson is reused here)
- ADR-0051 — cross-tenant 404-not-403 rule
- `specs/05-API-Contract-V1.5.md` §72.4, §72.16
- `specs/10-P1-Identity-V1.5.md` (organization status gates login)
- GAP-201 (closed)
