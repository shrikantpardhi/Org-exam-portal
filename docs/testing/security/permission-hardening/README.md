# Permission model hardening — pre-work record

Mission: close twelve permission defects against requirements R1 (SUPER_ADMIN administers the
system), R2 (ADMIN manages only its own organization), R3 (no user modifies their own permissions).

**Status: complete — all twelve defects closed across tasks 1-11. R2 is PARTIALLY met (see below).**

| Phase | State |
|---|---|
| Section 3 pre-work | complete — 12 defects reproduced, decision matrix approved |
| Tasks 1-6 | **complete** — D-01, D-02, D-03, D-04, D-07, D-09, D-12 closed; 29 tests; 2 migrations; 4 ADRs |
| Gate A | **approved 2026-10-26** — see `gate-a-memo.md` §7 |
| Tasks 7-8 | **complete** — D-06, D-08 closed; 15 tests; `V34`; ADR-0042, ADR-0044 |
| Gate B | **approved 2026-10-26** — see `gate-b-memo.md` §8 |
| Tasks 9-11 | **complete** — D-05, D-10, D-11 closed; 18 tests; `V35`, `V36`; ADR-0045, ADR-0046, ADR-0047 |

Requirement status: **R1 met**, **R3 met**, **R2 partially met**.

**One regression was introduced and then fixed.** The first version of Task 9 scoped `SUPER_ADMIN` to its
own organization, so it could not see tenant-owned roles — a regression against **R1**. The suite passed
with it in place, because every fixture role was platform-wide; live verification found it by creating a
tenant role and comparing the two catalogues. Fixed by `RoleService.isSystemAdministrator` and pinned by
`PlatformRoleVisibilityIntegrationTest` (GAP-186, now Closed). Recorded because "tests were green" and
"the requirement was met" were not the same statement here.

R2 is **not** recorded as met, and the distinction is deliberate. D-10 and D-11 are genuinely closed:
an ADMIN no longer sees another organization's users or audit trail. D-05 is closed in the schema and
partially in behaviour — Gate B chose shape **S2**, so the seven seeded roles stay platform-wide and an
ADMIN editing `STAFF` still changes what every organization sees. See GAP-184, ADR-0045, and the test
`OrganizationScopedRoleIntegrationTest.aSharedSeededRoleIsStillEditableAcrossOrganizations`, which
asserts that residual so that closing it forces this paragraph to be rewritten.

## Index

| Defect | Severity | Real? | Record | Fix class | Gate |
|---|---|---|---|---|---|
| D-01 | CRITICAL | Yes | [D-01](./D-01/reproduction.md) | Config | — |
| D-02 | CRITICAL | Yes | [D-02](./D-02/reproduction.md) | Guard + ADR | — |
| D-03 | CRITICAL | Yes | [D-03](./D-03/reproduction.md) | Guard + ADR + seed | — |
| D-04 | CRITICAL | Yes | [D-04](./D-04/reproduction.md) | Guard + ADR (via Task 4) | — |
| D-05 | CRITICAL | Yes | [D-05](./D-05/reproduction.md) | Schema + ADR | **B** |
| D-06 | HIGH | Yes | [D-06](./D-06/reproduction.md) | Endpoint + ADR | **A** |
| D-07 | HIGH | Yes | [D-07](./D-07/reproduction.md) | Guard + ADR | — |
| D-08 | HIGH | Yes | [D-08](./D-08/reproduction.md) | ADR + 2 guards | **A** |
| D-09 | MEDIUM (root cause) | Yes | [D-09](./D-09/reproduction.md) | Guard + ADR | — |
| D-10 | MEDIUM | Yes | [D-10](./D-10/reproduction.md) | Service + test | **B** |
| D-11 | MEDIUM | Yes | [D-11](./D-11/reproduction.md) | Schema + ADR | **B** |
| D-12 | HIGH | Yes | [D-12](./D-12/reproduction.md) | Test only | — |

All twelve defects are real. **None is already mitigated.** No defect needed to be closed as
"not-a-defect".

[Decision matrix](./decision-matrix.md) — approved 2026-10-26.

[Task records](./tasks/) — what changed for each of tasks 1-6, with the change set and test list.

[Live re-verification](./live-reverification.md) — every defect re-executed against the running stack
after the fix; the measured status codes are recorded side by side with the pre-fix ones.

[Gate A memo](./gate-a-memo.md) — approved; §7 records the four answers and their consequences.

[Gate B memo](./gate-b-memo.md) — approved; §8 records the five answers and their consequences.

[ADR-0047](../../../decisions/ADR-0047-frozen-schema-amendments-for-organization-scoping.md) — **Proposed**:
`V35` and `V36` amend two frozen tables, and `specs/04` has not been edited to match (GAP-185).

## Numbers that frame the risk

| Measure | Value |
|---|---|
| Roles | 7, global |
| Permissions | 163, global |
| SUPER_ADMIN permissions | 8, all `PLATFORM_*` |
| ADMIN permissions | 144 |
| Users | 5,010 across 2 organizations (5,009 `DEFAULT`, 1 `PLATFORM`) |
| Audit rows visible to one organization's ADMIN | 5,867 (the whole deployment's) |
| Migrations applied | 36 (`V1`–`V36`); next free `V37` |
| Next free ADR | `ADR-0048` |

## Two live-stack incidents during reproduction

Reproduction is not read-only. Two of the twelve defects, when executed, changed deployment state
and had to be repaired. Both repairs are recorded in the defect files; neither is hidden.

| Defect | Damage | Repair | Residual |
|---|---|---|---|
| D-02 | `SUPER_ADMIN` lost 5 of 8 permissions | `UPDATE role_permission ... status='ACTIVE'` for `PLATFORM_%` (8 rows). Verified: `GET /platform/organizations` → 200 | none |
| D-07 | `ADMIN` role set `INACTIVE`; the reproducing administrator locked itself out | `UPDATE role SET status='ACTIVE'` for `ADMIN`,`STAFF`; then the `authz:perms` cache entry had to be deleted by hand because the negative result had been cached for its 5-minute TTL | **This is itself evidence for D-07**: data repair was not sufficient; recovery required a cache intervention. |

## What the pre-work phase did NOT do

Kept for the record: during pre-work, no fix code, migration, ADR, spec edit or test change was made —
only the thirteen documents under this directory. All fixes landed later, in tasks 1-11, and
`docs/known-gaps.md`, `docs/implementation-status.md` and `docs/PROJECT-SUMMARY.md` were updated with
each task's code rather than in advance.

## Recommendation

Approve Tasks 1–6 (no gate). They close every CRITICAL defect except D-05, need no schema change,
and are each independently testable.

Gate A (Tasks 7–8) and Gate B (Tasks 9–11) are separate decisions, each with its own memo. Gate A was
approved on 2026-10-26 and its memo now carries the answers in §7; Gate B's memo exists and is
**unapproved**.

The memos are written but never self-approved: a gate is a decision for a human, and the record is
that the decision was asked for, not that it was assumed.