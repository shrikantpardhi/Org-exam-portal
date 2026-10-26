# GATE A — approval memo (proposal, not yet approved)

**Status:** ✅ **APPROVED 2026-10-26.** Decisions recorded in §7; Tasks 7 and 8 are in progress.

| Field | Value |
|---|---|
| Gate | A |
| Reached | 2026-10-26, after tasks 1-6 |
| Covers | Task 7 (D-06), Task 8 (D-08) |
| Blocking | nothing yet — this is a scope approval, not an incident |

---

## 1. What is already true when this gate is reached

| Requirement | Status |
|---|---|
| R1 — SUPER_ADMIN administers organizations, admins, roles, permissions | **Met** for the read surface (D-01). Authoring is this gate's question (§3). |
| R2 — ADMIN manages only within its own organization | **Not met** — Gate B. |
| R3 — no user modifies their own permissions | **Met** for the direct paths (D-02, D-07). The indirect path is Task 8 (§4). |

Seven of twelve defects closed. 29 tests added. Backend suite green. No existing test modified or
deleted.

---

## 2. Task 7 — D-06, SUPER_ADMIN recovery for a locked-out ADMIN

### What the defect actually is

This is **not a contract violation**. `specs/10-P1-Identity-V1.5.md` §63 says, explicitly:

> Emergency path: ... recovery is a documented direct database procedure operated by a DBA under
> change control, **not a UI feature**.

Task 7 therefore **amends a deliberate policy decision**. That is legitimate, but it must be argued as
new scope rather than presented as remediation, and the ADR must record that the earlier decision was
conscious.

### What changed since that decision was written

ADR-0032 introduced `MAX_ADMINS_PER_ORG = 1` (`V29`). Under the frozen model's original multi-admin
reading, a locked-out ADMIN could be replaced by a second ADMIN. With the cap at 1, there is no
replacement path either: `POST /platform/organizations/{id}/admins` refuses when an ADMIN already
exists (`AdminCapExceededException`).

~~~text
POST /platform/organizations/{orgId}/admins/{userId}/reset-password   -> 404
POST /platform/organizations/{orgId}/admins/{userId}/unlock-sessions   -> 404
GET  /platform/users                                                  -> 404
~~~

So the current recovery procedure is: a DBA session against production, and — as D-07's reproduction
demonstrated — a Redis key deletion on top of the SQL repair, because a negative authorization result
is cached for its 5-minute TTL.

### Proposed scope

Two endpoints, both `SUPER_ADMIN`-only, both audited, both organization-scoped:

| Endpoint | Effect | Refusal cases |
|---|---|---|
| `POST /api/v1/platform/organizations/{orgId}/admins/{userId}/reset-password` | issues a single-use reset token through the existing `PasswordResetDelivery`; the ADMIN must change the password on next login | target not in `{orgId}` → 404; target is not an ADMIN → 422; target is `SUPER_ADMIN` → 409 `SUPER_ADMIN_IMMUTABLE` |
| `POST /api/v1/platform/organizations/{orgId}/admins/{userId}/unlock-sessions` | revokes every active login session for the target and evicts its authorization cache | same three |

**Permission:** one new code, `PLATFORM_ADMIN_RECOVER`, granted to `SUPER_ADMIN` in the same migration.
**Migration:** `V34`, seed only.
**ADR:** ADR-0042, which must state that it supersedes the §63 "not a UI feature" sentence and why.

### What I propose NOT to build

- **No password disclosure.** The endpoint issues a reset token; it never returns or sets a password.
  A `SUPER_ADMIN` who can read an ADMIN's password is a bigger hole than the one being closed.
- **No role granting.** Recovery must not become "SUPER_ADMIN can make anyone an ADMIN"; that is
  D-04's escalation with a different actor.
- **No `GET /platform/users`.** Listing cross-org users would re-open D-10 at the platform tier. The
  organization's admin list already exists through `GET /platform/organizations/{id}`.

### Residuals this does NOT close

| Residual | Why |
|---|---|
| A `SUPER_ADMIN` who is themselves locked out still needs a DBA | `SUPER_ADMIN` is the recovery principal; there is nobody above it. This remains a documented DBA procedure. |
| The reset token still needs a delivery channel | `ADR-0034` wires `PasswordResetDelivery` to `Communication.EmailGateway`. If the ADMIN's mailbox is the compromised asset, the DBA path is still required. |
| The cache-eviction asymmetry D-07 exposed remains | D-06's unlock endpoint evicts explicitly, but any *other* out-of-band repair still needs a manual eviction. Mitigated for this path, not eliminated generally. |

---

## 3. The question this gate must answer first: may SUPER_ADMIN author roles?

D-01's fix (`V32`) granted `SUPER_ADMIN` the two catalogue **read** permissions and deliberately
withheld `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN`. `SuperAdminPermissionCatalogueTest`
asserts their absence.

Requirement **R1** says `SUPER_ADMIN` "manages organizations, admins, roles, permissions". The word
*manages* is ambiguous between:

| Reading | Consequence |
|---|---|
| **A — full authoring.** `SUPER_ADMIN` gets `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN`, and the `/platform/*` surface gains role authoring | satisfies the plain reading of R1; but the platform tier would then be able to edit every organization's roles — and, with D-05 unfixed, the **same global role rows** an ADMIN edits. It would also make `RESERVED_PERMISSION_DENIED` unreachable for `SUPER_ADMIN`, since a `SUPER_ADMIN` holding `UNBOUNDED_PERMISSION_ASSIGN` would bypass it |
| **B — read + recovery only.** `SUPER_ADMIN` keeps the catalogue reads and gains recovery, but role authoring stays with each organization's `ADMIN` | keeps the platform tier a *supervisor*, not a co-administrator; keeps the reserved set meaningful; defers the cross-tier question to Gate B, where D-05 makes org-scoped roles real |

**I recommend B**, and Task 7's scope above assumes it. Under A, `V32` plus a new `V34` would need to
grant four more permissions, `SuperAdminPermissionCatalogueTest` would be edited, and the reserved set
would need a carve-out — all of which is cleaner **after** Gate B gives roles an organization.

This is a decision this memo cannot make for you.

---

## 4. Task 8 — D-08, two-step role escalation

### The reproduction that succeeded

~~~text
POST /roles  {"code":"ELEV_TEST", ...}                              -> 201
PUT  /roles/<ELEV_TEST>/permissions  {ADMIN_ROLE_ASSIGN}             -> 200
~~~

The actor held `ROLE_CREATE` and `ROLE_PERMISSION_ASSIGN` but **not** `ADMIN_ROLE_ASSIGN`.

**Note:** ADR-0040's bounded delegation, shipped in Task 4, **already blocks the second call** —
`ADMIN_ROLE_ASSIGN` is a code the actor does not hold, so it is refused with `PERMISSION_NOT_HELD`.
Task 8's step 1 is therefore closed as a side effect.

### What is still open

| Rule | Gap |
|---|---|
| **Rule A** | The escalation can still be *added after* creation by a caller who holds the code, then assigned by a third party who does not. Bounded delegation stops the *grant*; it does not stop a role from existing in an escalated state and being assigned by someone who cannot see that it is escalated. Closure: refuse assignment of a role whose permission set contains a code the **assigner** cannot see or hold. |
| **Rule B** | `POST /roles` has no permission input at all (`CreateRoleRequest` is `code`, `name`, `description`), so the mission's assumed "create with permissions" path does not exist. Rule B must therefore guard the **create-then-assign sequence**, or the DTO must change — and a DTO change is a `05-API-Contract §24` amendment needing an ADR. |
| **The two-account step** | Not executed during reproduction: it needs a second holder of `USER_UPDATE`, which `MAX_ADMINS_PER_ORG = 1` does not permit on this deployment. It is asserted, not performed. |

### Proposed scope for Task 8

- `RoleService.assignToUser` path (via `UserService.replaceRoles`) refuses a role whose effective
  permission set contains a code the assigning actor lacks — `403 ROLE_ESCALATION_DETECTED`, kept
  distinct from `PERMISSION_NOT_HELD` because the refusal is about the *role*, not the request body.
- No DTO change. `CreateRoleRequest` stays as-is; the create-then-assign sequence is what is guarded.
- ADR-0044 records the letter-vs-intent reading of §63 and the Rule A/Rule B split.

---

## 5. Cost

| Task | Effort | Risk |
|---|---|---|
| 7 (D-06) | ~1 day | **Low** — new routes, new permission, no existing behaviour changes |
| 8 (D-08) | ~0.5 day | **Medium** — touches `UserService.replaceRoles`, which `AdminCapIntegrationTest` and `RoleImmutabilityIntegrationTest` already guard |

---

## 6. The decision

| # | Question | Options |
|---|---|---|
| A1 | Approve Task 7 (D-06 recovery endpoints, `V34`, ADR-0042)? | yes / no |
| A2 | **Role authoring for `SUPER_ADMIN`: reading A (full) or B (read + recovery)?** | **A / B** — recommendation: **B** |
| A3 | Approve Task 8 (D-08 Rule A, ADR-0044)? | yes / no |
| A4 | Confirm `GET /platform/users` stays unbuilt (D-06 closes without it)? | yes / no |

Nothing in Gate A's scope has been started. Awaiting the answers above.
---

## 7. Recorded decisions (2026-10-26)

| # | Question | Decision |
|---|---|---|
| A1 | Approve Task 7 (D-06 recovery endpoints, `V34`, ADR-0042)? | **YES** |
| A2 | Role authoring for `SUPER_ADMIN` | **Reading A — full authoring.** `SUPER_ADMIN` gains `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN`. The mechanism is **create, then assign**: `CreateRoleRequest` keeps `code`/`name`/`description` and permissions are set by a second `PUT /roles/{id}/permissions`. No DTO change, no `05-API-Contract §24` amendment for creation. |
| A2b | Should `SUPER_ADMIN` hold `UNBOUNDED_PERMISSION_ASSIGN`? | **YES.** Without it, bounded delegation (ADR-0040) would confine `SUPER_ADMIN` to the codes it holds, so authoring could not build any role carrying `PLATFORM_*` or `ADMIN_ROLE_ASSIGN` — a capability that cannot perform its own purpose. Recorded consequence: `RESERVED_PERMISSION_DENIED` is unreachable for `SUPER_ADMIN` (it is reachable for every other principal, which is what the guard is for). |
| A3 | Approve Task 8 (D-08 Rule A, ADR-0044)? | **YES** |
| A4 | `GET /platform/users` | **BUILD IT.** Paginated cross-org list guarded by a new `PLATFORM_ADMIN_VIEW`. Shape: `id`, `firstName`, `lastName`, `email`, `status`, `organizationId`, `roles`; filters `search`, `status`, `organizationId`; audited. Returns **all** organizations, including `PLATFORM`. |

### Consequences of A2 that the approval should be read as accepting

1. **`V32`'s narrower grant is superseded.** `SuperAdminPermissionCatalogueTest` (Task 6) pinned
   exactly 10 codes and asserted the absence of `ROLE_CREATE`, `ROLE_UPDATE`,
   `ROLE_PERMISSION_ASSIGN`, `ADMIN_ROLE_ASSIGN` and `UNBOUNDED_PERMISSION_ASSIGN`. Task 7 edits that
   test in the same change set. This is the intended forcing function documented in the Task 6
   record, not a weakening.
2. **D-05's weight increases.** With authoring granted and `role` still global, `SUPER_ADMIN` and
   every organization's `ADMIN` now edit the *same role rows*. That is Gate B's problem and it is now
   more urgent, not less — recorded in the Gate B memo's inputs.
3. **The reserved set becomes a platform-only control.** It still refuses `PLATFORM_*` placement for
   `ADMIN` (D-03 stays closed), but `SUPER_ADMIN` bypasses it by design.
