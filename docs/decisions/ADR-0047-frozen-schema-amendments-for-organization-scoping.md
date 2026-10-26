# ADR-0047 — frozen-schema amendments for organization scoping

- **Status:** Proposed (2026-10-26) — **requires product/architecture sign-off**
- **Closes:** the schema half of D-05 and D-11
- **Amends:** `04-Database-Schema-V1.5.md` §25.3 (`role`), §34.4 (`audit_log`)
- **Related:** ADR-0045, ADR-0046
- **Migrations:** `V35`, `V36`

## Why this ADR exists

D-05 and D-11 cannot be closed without changing two frozen tables. `V1.5-SCOPE-AND-FREEZE-AUTHORITY.md`
§4 says a specification change must not *silently* introduce a new persistent structure, and AGENTS.md
rule 20 says to stop and report rather than resolve a frozen-contract gap quietly.

Two exhaustive schema pins in the test suite caught exactly that. They are recorded here rather than
silently relaxed, because a pin that is edited without a decision is indistinguishable from a pin that
was never there.

## Amendment 1 — role.organization_id

**Frozen text (§25.3):** ten columns, and `Constraint: UNIQUE(code)`.

**Amendment:** add nullable `organization_id UUID FK -> organization(id)`, and replace `UNIQUE(code)`
with two partial unique indexes.

| Item | Frozen | Now |
|---|---|---|
| Columns | 10 | 11 |
| Uniqueness | `UNIQUE(code)` global | `UNIQUE(code) WHERE organization_id IS NULL` plus `UNIQUE(organization_id, code) WHERE organization_id IS NOT NULL` |
| Indexes | none declared | `+ ix_role_organization_id` |

**Why the constraint had to change rather than be kept:** the global unique makes per-organization role
codes impossible — two organizations could never both own a `MANAGER` role. Keeping it would have left
D-05 half-fixed in a way that only shows up in production.

**Why partial indexes:** PostgreSQL treats NULLs as distinct, so a plain `UNIQUE(organization_id, code)`
would not constrain the platform-wide rows at all.

**Caught by:** `IdentityMigrationIntegrationTest.requiredIndexesAndUniqueConstraintsAreInstalled`
(`uk_role_code should exist` -> 0). The pin now names the two replacements and remains exhaustive.

## Amendment 2 — audit_log.organization_id

**Frozen text (§34.4):** seven columns, four indexes.

**Amendment:** add nullable `organization_id UUID FK -> organization(id)` and
`ix_audit_log_organization_id`.

**Caught by:** `P11MigrationIntegrationTest.auditLogHasExactlyTheFrozenColumns`
(`unexpected: [organization_id]`). The pin now includes it with the ADR reference in a comment.

### What this amendment does NOT do

§11.3 of `20-P11` forbids payload columns and the pin guards that. The new column is an **ownership
discriminator**, not payload: it holds a UUID that identifies a tenant, not request content. No
`request_id`, `old_values`, `new_values`, `ip_address` or `payload` column was added, and the pin still
fails if one appears.

## Nothing else moved

| Check | Result |
|---|---|
| New tables | none |
| New API families | none |
| New domain entities | none |
| Removed columns | none |
| Removed indexes/constraints | 1 (`uk_role_code`), replaced by 2 documented partial uniques |
| Endpoints whose request or response shape changed | none |

Both amendments are additive-nullable, so existing rows and existing queries remain valid.

## The decision this needs from the product owner

| Question | Options | Consequence |
|---|---|---|
| Accept the two amendments as a v1.5 schema revision? | **accept** | D-05/D-11 close; `04-Schema` §25.3/§34.4 need a matching edit |
| | reject | D-05 and D-11 stay open; V35/V36 are reverted and the two pins restored |
| | accept for `audit_log` only | D-11 closes; D-05 reverts, and role scoping needs a different design |

**My recommendation: accept both, and edit `04-Schema` §25.3 and §34.4 to match.** The columns are
nullable and additive, no existing behaviour depends on their absence, and without them two defects
cannot be closed at all. The alternatives are not cheaper — they are the same change deferred, or the
defect kept.

## If this is rejected

Revert order matters: drop `V36`, then `V35`; restore `uk_role_code`; restore the two pins; revert
`RoleService`, `RoleRepository` (restore the unscoped `findByCode`), the 13 call sites, `AuditTrail`,
`SecurityEventLogger`, `AuditQueryService`, and the controllers. D-05 and D-11 then return to **Open**
and R2 stays as it was — not met.