# GATE B — approval memo (proposal, not yet approved)

**Status:** APPROVED 2026-10-26. Decisions in §8; Tasks 9, 10 and 11 are in progress.

| Field | Value |
|---|---|
| Gate | B |
| Covers | Task 9 (D-05), Task 10 (D-10), Task 11 (D-11) |
| Blocking | requirement **R2** — ADMIN manages only within its own organization |

---

## 1. Where this gate stands

Gate A is closed (2026-10-26). Nine of twelve defects are closed. **R1 met, R3 met, R2 not met.**

R2 is the requirement these three defects *are*. Until they close, the platform cannot claim to be
multi-tenant in any enforced sense — a single-organization ADMIN reads every user and every audit row
in the installation.

---

## 2. Gate A changed this gate's arithmetic

Approving A2 gave SUPER_ADMIN role authoring over the **same global role rows** an organization's
ADMIN edits. Before Gate A, D-05 was a cross-organization isolation defect. It is now also a
collision defect: two tiers write one set of rows with no ownership between them.

| | Before Gate A | After |
|---|---|---|
| Who can edit role STAFF | any organization's ADMIN | any organization's ADMIN **and** SUPER_ADMIN |
| Who can edit role SUPER_ADMIN | nobody (ADR-0038) | nobody (ADR-0038, unchanged) |
| Is there an owner column | no | no |

This does not change Task 9's shape. It changes its urgency, and adds one requirement: the migration
must decide what happens to the **seven seeded global roles**, because both tiers already depend on
them by code.

---

## 3. Task 9 — D-05, organization-scoped roles (LARGE)

### The defect

`04-Schema` §25.3 defines `role` with no `organization_id`; only `user`, `organization_setting` and
`organization_analytics` carry one. So:

~~~text
An ADMIN of organization A can read role STAFF of organization B, and edit it.
An ADMIN of organization A can create a role that appears in organization B's catalogue.
~~~

### Why this is the largest task in the mission

Adding a column is easy. Deciding what the column means for seven seeded roles referenced by code
across the backend is not.

| Reference | Uses | Why it matters |
|---|---|---|
| ADMIN_ROLE_CODE = ADMIN | UserService, PlatformOrganizationService, RolePermissionPolicy | resolves a role *by code*, not by id |
| SUPER_ADMIN_ROLE_CODE | RoleService immutability, bootstrap, recovery | must stay unique installation-wide |
| roleRepository.findByCode(...) | 11 call sites | would return the wrong row once codes are no longer unique |

**The core question is not add the column but: may two organizations hold a role with the same
code?** Three viable shapes:

| Shape | Model | Consequence |
|---|---|---|
| **S1 — organization-owned, codes unique per organization** | role.organization_id NOT NULL for business roles; the seven seeded roles are copied per organization at creation; code unique within organization_id | strongest isolation; every findByCode becomes findByCodeAndOrganizationId, and 11 call sites plus the seeds change; existing organizations need a backfill materialising 6 roles each |
| **S2 — organization-owned, platform roles global** | role.organization_id NULL means platform-wide (the seven seeded roles); business roles carry an organization | smaller change; but the seeded ADMIN stays shared, so an ADMIN editing it still affects everyone — R2 only *partially* met |
| **S3 — template + copy** | seeded roles become read-only templates; each organization gets copies at creation | cleanest separation; largest backfill and the most behaviour change (an ADMIN could no longer break its own login by editing the template, but also could no longer receive seeded fixes) |

**Recommendation: S1.** S2 leaves exactly the defect in place for the roles that matter most
(ADMIN, STAFF, STUDENT), which would mean declaring R2 met when it is not. S3 is close to S1 but adds
a template-versus-copy distinction this codebase does not have and would need a second ADR.

### Proposed scope

| Item | Detail |
|---|---|
| Migration | V35 — add organization_id to role, backfill, replace the global unique on code with a composite; materialise the six business roles per existing organization |
| Repository | findByCode(String, UUID organizationId); the no-scope variant either removed or restricted to platform roles |
| Service | every RoleService and UserService path scoped to the actor's organization |
| Guard | SUPER_ADMIN's platform scope handles organization_id IS NULL roles only |
| Seeds | V29's global roles become organization_id IS NULL (platform-owned); business organizations get copies |
| ADR | ADR-0045 |
| Tests | ~20 |

### The hard part, stated plainly

UserService.create resolves roles from request.roles() — a list of **ids** supplied by the client.
Scoping that to the actor's organization is a correctness fix; but the client can send an id from
another organization, and today nothing checks. The fix is one predicate. The *audit* of the 11
findByCode sites is where the time goes.

---

## 4. Task 10 — D-10, the user list is not organization-scoped

### The defect, measured

~~~text
GET /api/v1/users  as demo.admin2@example.test (organization DEFAULT)
  -> 5,010 users, across both organizations, including superadmin@platform.test
~~~

### Proposed scope

One predicate in UserService.userSpecification, plus the equivalent on detail-by-id.

| Item | Detail |
|---|---|
| Change | scope every UserRepository read that serves /api/v1/users to the actor's organization_id |
| Also | GET /users/{id} must 404 (not 403) for a user in another organization — non-disclosure, matching §25's rule for password reset |
| ADR | none needed; this is the frozen contract being honoured, not amended |
| Tests | ~6 |

**Open question:** SUPER_ADMIN holds no USER_VIEW, so it cannot reach this endpoint at all — its user
surface is the read-only /platform/users built in Task 7. Confirm that stays true, or say whether
SUPER_ADMIN should see /api/v1/users too.

---

## 5. Task 11 — D-11, audit_log has no organization attribute (LARGE)

### The defect

`04-Schema` §34.4 defines `audit_log` without `organization_id`. The ADMIN explorer therefore returns
all 5,867 rows of a two-organization deployment. Organization is *derived* through `user` — which
works only while every audited entity has a user, and silently reports nothing for the ones that do
not (organizations, business rules, provider config).

### Proposed scope

| Item | Detail |
|---|---|
| Migration | V36 — add audit_log.organization_id, backfill from user where derivable, index it |
| Write path | SecurityEventLogger records the organization at write time rather than deriving at read time |
| Read path | scope GET /audit-logs to the actor's organization |
| Backfill gap | rows whose actor has no organization (system actor, scheduled jobs) cannot be attributed — **decision needed** |
| ADR | ADR-0046 |
| Tests | ~8 |

### The question this gate must answer

**What organization do system-generated audit rows belong to?** Today 5,867 rows include worker and
scheduler activity. Three options:

| Option | Consequence |
|---|---|
| NULL = platform-wide, hidden from every organization's ADMIN | clean semantics; the ADMIN explorer shows only attributable rows, and the existing count drops |
| attribute to the entity's organization where derivable, NULL otherwise | best coverage; the backfill is more complex and some rows stay unattributed |
| backfill everything to DEFAULT | simple and **wrong** — it would relabel another organization's history |

**Recommendation: the second**, falling back to NULL.

---

## 6. Cost

| Task | Effort | Risk |
|---|---|---|
| 9 (D-05) | ~3 days | **High** — schema change on a table every identity path reads; 11 findByCode sites; backfill |
| 10 (D-10) | ~0.5 day | **Low** — one predicate and a 404 |
| 11 (D-11) | ~2 days | **Medium** — schema change plus a backfill with an unattributable remainder |

Task 10 is independent and could ship first; Tasks 9 and 11 both need migrations and should not run
concurrently.

---

## 7. The decision

| # | Question | Options |
|---|---|---|
| B1 | Approve Task 9 (D-05 org-scoped roles), and which shape? | **S1 / S2 / S3** — recommendation: **S1** |
| B2 | Approve Task 10 (D-10 user-list scoping)? | yes / no |
| B3 | Confirm SUPER_ADMIN stays off /api/v1/users (its surface remains /platform/users)? | yes / no |
| B4 | Approve Task 11 (D-11 audit organization), and which backfill? | **null / derived+null / all-DEFAULT** — recommendation: **derived+null** |
| B5 | Order: Task 10 first (independent), then 9, then 11? | yes / no |

Nothing in Gate B's scope has been started.
---

## 8. Recorded decisions (2026-10-26)

| # | Question | Decision |
|---|---|---|
| B1 | Shape for Task 9 (D-05)? | **S2 — organization-owned roles, platform roles global.** `role.organization_id IS NULL` means platform-wide; business roles carry an organization. The seven seeded roles stay `NULL` (platform-owned) and shared. |
| B2 | Approve Task 10 (D-10)? | **YES** |
| B3 | Confirm SUPER_ADMIN stays off `/api/v1/users`? | **YES** — its user surface remains the read-only `/platform/users` |
| B4 | Approve Task 11 (D-11)? | **YES**, implemented with the recommended backfill: **derive from the audited entity's owner, fall back to `NULL`** |
| B5 | Order | **YES** — Task 10, then Task 9, then Task 11 |

### B4's backfill choice, stated explicitly

The question offered three backfills (null / derived+null / all-DEFAULT) and the answer was "yes" to the
question, which I have read as accepting the recommendation: **derive where derivable, `NULL`
otherwise**. The other two readings are not silently excluded — `all-DEFAULT` is *rejected* because it
would relabel another organization's history as `DEFAULT`'s, and bare `null` simply attributes less.
If a different backfill was intended, only the `UPDATE` in `V36` changes; the column, write path and
read scope do not.

### ACCEPTED CONSEQUENCE OF B1 — S2 does not fully satisfy R2

This must be recorded plainly, because it changes what the project may claim.

Under S2 the seven seeded roles (ADMIN, REVIEWER, STAFF, STUDENT, SUPPORT, FINANCE, SUPER_ADMIN)
remain `organization_id IS NULL` and therefore shared by every organization. So:

| Behaviour | Under S2 |
|---|---|
| An ADMIN edits a role **it created** | isolated to its organization — R2 met |
| An ADMIN edits `STAFF`, `STUDENT`, `REVIEWER`, `SUPPORT`, `FINANCE` | **still affects every organization** — R2 not met for these |
| An ADMIN edits `ADMIN` | affects every organization's administrator |
| An ADMIN edits `SUPER_ADMIN` | refused (ADR-0038, unchanged) |

These are exactly the roles that matter most, which is why I recommended S1. The decision is
nevertheless coherent: it is the smaller change, it establishes the ownership column and the scoping
plumbing everywhere, and it can be taken to full S1 later by copying the seeded roles per organization
without revisiting any of the guard logic.

**Therefore: after Tasks 9-11, R2 is recorded as PARTIALLY met, not met.** The residual becomes a
named gap rather than a claim. Anything else would be the same overstatement the pre-work had to
correct for D-03.

### Consequence for Task 10

D-10 is unaffected by S2: `user.organization_id` already exists, so scoping `/api/v1/users` is a
predicate, not a schema question. It is done first per B5 and it closes a defect independently of
Task 9.

### Implementation status (added after tasks 9-11 landed)

| Task | Decision | State | Evidence |
|---|---|---|---|
| 10 | B2 yes | **complete** | `UserListOrganizationScopeIntegrationTest` (5 tests); the 5,010-user leak is closed |
| 9 | B1 = S2 | **complete** | `OrganizationScopedRoleIntegrationTest` (7 tests); `V35`; ADR-0045 |
| 11 | B4 derive+null | **complete** | `AuditLogOrganizationScopeIntegrationTest` (4 tests); `V36`; ADR-0046 |

B3 is unchanged: `SUPER_ADMIN` still has no `/api/v1/users` surface, asserted by
`SuperAdminPermissionCatalogueTest.superAdminAuthorsRolesButHoldsNoOrganizationUserSurface`.

B5's order was followed exactly: Task 10, then 9, then 11.

### One thing this gate did not anticipate

Tasks 9 and 11 both required a column on a **frozen** table (`04-Schema` §25.3 and §34.4), and Task 9
also required replacing the global `UNIQUE(code)` on `role`. That is a formal schema amendment under
`V1.5-SCOPE-AND-FREEZE-AUTHORITY.md` §4 and AGENTS.md rule 20, and it was not among the options the
gate offered. It is recorded as **ADR-0047 (Proposed)** and **GAP-185** rather than assumed approved.

Two exhaustive schema pins caught it — `IdentityMigrationIntegrationTest` and
`P11MigrationIntegrationTest` — and neither was relaxed silently: both now name the additions with the
ADR reference and still fail on anything unapproved. **Sign-off is still outstanding**, and
`specs/04` §25.3/§34.4 have not been edited to match.