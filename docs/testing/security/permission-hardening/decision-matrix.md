# Permission Model Hardening — decision matrix

**Mission date:** 2026-10-26  
**Baseline commit:** `807a277` (working tree carries 119 uncommitted paths from the preceding missions)  
**Environment used for reproduction:** local Docker stack through the edge, `http://localhost:8081`, PostgreSQL 16, migrations applied through `V31`.  
**Next free Flyway version:** `V32`. **Next free ADR number:** `ADR-0038`.

This matrix is the artifact to review **before** approving any task. Every row was produced by
executing the reproduction recorded under `./<defect-id>/reproduction.md`; no row is inferred from
reading code alone.

---

## 1. The matrix

| # | Defect | Frozen section violated | Current behaviour (observed) | Target behaviour | Fix class | ADR? | Migration? | Test coverage to add | Risk to existing tests |
|---|---|---|---|---|---|---|---|---|---|
| D-01 | SUPER_ADMIN lacks `ROLE_VIEW` / `PERMISSION_VIEW` | `10-P1` §68 matrix; `super-admin-flow.md` §12–13; ADR-0032 Consequences | `GET /roles` 403, `GET /permissions` 403 for SUPER_ADMIN | Both 200; set = 8 `PLATFORM_*` **+** `ROLE_VIEW` **+** `PERMISSION_VIEW` | Config | **No** — one-sentence ADR-0032 clarification | **Yes** — `V32` grants 2 rows | `SuperAdminPermissionCatalogueTest` (Task 6) pins the set; plus a direct 200/403 test in Task 1 | **None.** No existing test asserts SUPER_ADMIN's role/permission API access. |
| D-02 | ADMIN can rewrite the SUPER_ADMIN role's permissions | `20-P11` §18 ("SUPER_ADMIN cannot be demoted"); ADR-0032 (non-demotable) | `PUT /roles/{superAdminId}/permissions` → **200**, 5 of 8 codes deactivated live | 409 `ROLE_IMMUTABLE` from any caller, incl. SUPER_ADMIN | Guard + ADR | **Yes** — ADR-0038 | No | 6 integration tests (ADMIN 409, SUPER_ADMIN 409, status-change 409, STAFF edit still 200, out-of-band-deactivated row still refused, paired with `SUPER_ADMIN_IMMUTABLE` user test) | **None.** `IdentityRoleApiIntegrationTest` never touches the SUPER_ADMIN role. |
| D-03 | ADMIN can assign `PLATFORM_*` to any role | `20-P11` §17; ADR-0032 ("granted to SUPER_ADMIN only") | `PUT /roles/{staffId}/permissions` with `PLATFORM_ORGANIZATION_VIEW` → **200** | 403 `RESERVED_PERMISSION_DENIED` unless caller holds the bypass code | Guard + ADR + seed | **Yes** — ADR-0039 | **Yes** — `V32` seeds the `RESERVED_PERMISSIONS` business rule + bypass permission | 5 integration tests, incl. fail-closed when the rule is absent | **None.** No test assigns a `PLATFORM_*` code through `PUT /roles/{id}/permissions`. |
| D-04 | ADMIN can assign `ADMIN_ROLE_ASSIGN` | `10-P1` §65; ADR-0014; ADR-0023 dec. 2 | `PUT /roles/{staffId}/permissions` with `ADMIN_ROLE_ASSIGN` → **200** | 403 `PERMISSION_NOT_HELD` (Task 4's rule, not Task 3's) | Guard + ADR (closed by Task 4) | **Yes** — ADR-0040 | No | 1 dedicated test asserting the *code* is `PERMISSION_NOT_HELD`, not `RESERVED_PERMISSION_DENIED` | **None.** |
| D-05 | Roles are global; no `organization_id` | **`04-Schema` §25.3 has no such column** — the contract is missing, not broken | `role` carries no org attribute; 7 roles shared by 5,010 users in 2 orgs; `RoleSummary` DTO has no org member | `role.organization_id` set; `GET/POST/PUT /roles` org-scoped; cross-org → 404 | **Schema + ADR (LARGE)** | **Yes** — ADR-0041 | **Yes** — expand (`V32`) + contract (`V33`) pair | 6 integration tests (list isolation, 404 on both PUTs, SUPER_ADMIN sees PLATFORM roles only, Task 2 still fires) | **High.** Every role test that assumes a global catalogue needs review; each rewrite recorded in ADR-0041's "Changed test assertions" table. |
| D-06 | No SUPER_ADMIN recovery for a locked-out ADMIN | `10-P1` §63 emergency path is **explicitly out of API scope** — this is a *policy* gap created by `MAX_ADMINS_PER_ORG = 1`, not a contract violation | `.../reset-password` 404; `.../unlock-sessions` 404; `GET /platform/users` 404 | Two new endpoints under §72.10, audited, `PLATFORM_ADMIN_RECOVER` | Endpoint + ADR | **Yes** — ADR-0042 | **Yes** — `V33` seeds `PLATFORM_ADMIN_RECOVER` | 6 integration tests | **None** (new routes). |
| D-07 | Self-permission rule ignores role STATUS | `10-P1` §63 (intent) | `PUT /roles/{ownRoleId} {"status":"INACTIVE"}` → **200**; caller locked out; undo → 403 | 403 `SELF_PERMISSION_MODIFICATION` on any non-cosmetic field of a role the caller actively holds | Guard + ADR | **Yes** — ADR-0043 | No | 4 integration tests (status blocked, name allowed, non-member unaffected, INACTIVE membership carries no rights) | **Low.** No existing test deactivates the caller's own role. |
| D-08 | Two-step escalation via a created role | `10-P1` §63 (letter: self-assignment only; intent: no escalation) | `POST /roles` → 201, then `PUT .../permissions` with `ADMIN_ROLE_ASSIGN` → 200 on a role the attacker created | 403 `ROLE_ESCALATION_DETECTED`; and `POST /roles` cannot pre-load escalated codes | ADR + 2 guards | **Yes** — ADR-0044 | No | 6 integration tests | **Low.** No test creates a role and then escalates it. |
| D-09 | No bounded delegation | `10-P1` §52 ("no unrestricted permission-mutation API"); ADR-0023 dec. 2 | Caller granted 2 codes it does not hold, both → 200 | 403 `PERMISSION_NOT_HELD` unless caller holds `UNBOUNDED_PERMISSION_ASSIGN` | Guard + ADR | **Yes** — ADR-0040 (shared with D-04) | **Yes** — `V32` seeds `UNBOUNDED_PERMISSION_ASSIGN` | 4 integration tests incl. a mid-request cache-reduction test | **Medium.** Any existing test where the acting admin grants a code it does not hold will now 403. `IdentityRoleApiIntegrationTest:101-110` already expects 403 but for a different code — must be re-checked for precedence. |
| D-10 | User list has no organization scope | `10-P1` §151; ADR-0033; ADR-0037 | `GET /users` → **5,010 users** across both organizations; PLATFORM user readable by a DEFAULT-org ADMIN | Caller sees only their own organization's users; cross-org `GET /users/{id}` → 404 | Service + test | **No** if it confirms intent (it is a behaviour change → **Yes**, ADR-0045) | No | 3 integration tests + 1 residual gap entry (SUPER_ADMIN has no cross-org user view) | **Medium-high.** ~40 identity integration tests create users in the fixture org; each must still pass. The fixture must keep the caller and target in the same org. |
| D-11 | `audit_log` has no `organization_id` | `04-Schema` §34.4 (no such column); `admin-flow.md` §15 promise | 5,867 rows returned to one org's ADMIN; organization is a join through `user` | Column added, nullable; audit read scoped to the caller's org; SYSTEM_ACTOR rows NULL | **Schema + ADR (LARGE)** | **Yes** — ADR-0046 | **Yes** — expand + backfill, contract later | 3 integration tests | **Medium.** `P11AdministrationIntegrationTest` and audit assertions read the shared trail; scoping may hide rows they create with a differently-scoped principal. |
| D-12 | SUPER_ADMIN set not pinned | ADR-0032 Consequences; ADR-0030 precedent | 0 test references to `PLATFORM_*` in `integration/security/` | `SuperAdminPermissionCatalogueTest` pins codes, roles, and absences | Test only | **No** | No | 3 assertions (exact set, only-role, forbidden absences) | **None.** |

---

## 2. Correction to the brief's premises

Three statements in the mission brief do not match the repository. They are recorded here because
the matrix is what a reviewer approves, and an approved matrix built on a wrong premise is worse
than no matrix.

### 2.1 D-10's stated mitigation does not exist

> "cross-org visibility is only mitigated by `SUPER_ADMIN_IMMUTABLE` for the `SUPER_ADMIN` user; nothing else"

`guardSuperAdminImmutability` blocks **archive** and **role replacement** for the SUPER_ADMIN user.
It is not a visibility control. `GET /users` and `GET /users/{id}` return PLATFORM-organization
users to a DEFAULT-organization ADMIN with no guard at all (reproduction: D-10). The defect is
larger than described: it is not "an implicit scope that should be made explicit", it is **no scope**.

### 2.2 D-06 is not a contract violation

`10-P1` §63 states that ADMIN recovery is "a documented direct database procedure operated by a DBA
under change control, **not a UI feature**". Task 7 therefore does not close a violation — it
amends a deliberate policy decision. The ADR must argue the policy change, and the brief's framing
("close the lockout path") should be understood as new scope, not as remediation.

### 2.3 D-05's fix is larger than "add a column"

Adding `role.organization_id` does not by itself satisfy R2, because `permission` is also global and
`role_permission` links the two. The matrix scopes Task 9 to the `role` column plus API scoping, as
the brief specifies, and records the remaining `permission` globality as a residual in the Gate B
memo.

---

## 3. Grouping, and what each gate buys

| Group | Defects | Closes | Effort | Gate |
|---|---|---|---|---|
| In-scope A (Tasks 1–6) | D-01, D-02, D-03, D-04, D-07, D-09, D-12 | Every CRITICAL defect except D-05 | ~2.5 days | none |
| In-scope B (Tasks 7–8) | D-06, D-08 | Both HIGH defects outside the schema | ~1.5 days | **Gate A** |
| Deferred (Tasks 9–11) | D-05, D-10, D-11 | R2 at the schema level | ~4–5 days | **Gate B** |

After Tasks 1–6 only, requirements R1 and R3 are met and **R2 is not**: D-05, D-10 and D-11 remain,
and D-10 makes R2 observably false on a live deployment — an organization ADMIN still reads another
organization's users. That is the single strongest argument for Gate B.

---

## 4. Ordering constraints (why the task order is not arbitrary)

```text
T1 (grants)  ->  T6 (pins T1's result)          T6 must run after every grant task
T2 (immutable) -> T3 (reserved)                 T2 must win over T3's bypass; the guard order is
                                                immutability FIRST, then reserved, then bounded
T3 (reserved) -> T4 (bounded)                   T4's test must distinguish its code from T3's
T4 (bounded)  -> T8 (Rule B)                    Rule B reuses T4's containment test
T5 (status)   independent of T2-T4, but shares the same transaction boundary
T7, T8        after Gate A
T9, T10, T11  after Gate B; T10 is not schema-gated and could be pulled forward if Gate B defers
```

Guard precedence, fixed once and asserted in tests: **immutability (409) → reserved (403
`RESERVED_PERMISSION_DENIED`) → bounded delegation (403 `PERMISSION_NOT_HELD`)**. A test asserts each
code individually, so a reordering regression fails loudly.

---

## 5. Sign-off

| Question | Answer required |
|---|---|
| Is the matrix accurate as reproduced? | yes / corrections |
| Approve Tasks 1–6 (no gate)? | yes / no |
| Approve the guard precedence in §4? | yes / no |
| Approve the correction to D-10 in §2.1 (fix = add scope, not pin existing scope)? | yes / no |
| Approve the reframing of D-06 in §2.2 (policy amendment, not remediation)? | yes / no |

Nothing is implemented until this matrix is approved.