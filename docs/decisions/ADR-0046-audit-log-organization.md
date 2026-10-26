# ADR-0046 — the audit trail carries an organization

- **Status:** Accepted (2026-10-26), on Gate B approval
- **Closes:** D-11
- **Related:** ADR-0045 (organization-owned roles)

## Context

`04-Schema` §34.4 defines `audit_log` without an `organization_id`. The ADMIN audit explorer therefore
returned all 5,867 rows of a two-organization deployment to any caller holding `AUDIT_LOG_VIEW`.

Organization was *derived* at read time by joining through `user`. That has two faults:

1. It fails for entities that have no user — organizations, business rules, provider configuration,
   scheduled jobs. Those rows were silently unattributable rather than correctly attributed.
2. It reports the **actor's** organization, which is not always the same as the **subject's**. An
   organization-scoped row written by the system actor was attributed to nobody.

## Decision

`V36` adds `audit_log.organization_id` (nullable, FK to `organization`), `SecurityEventLogger`
records it at write time, and `AuditQueryService` filters on it.

### Resolution order at write time

| # | Condition | Attributed to |
|---|---|---|
| 1 | the audited entity is an `Organization` | that organization |
| 2 | the actor is a user | the actor's organization |
| 3 | otherwise | `NULL` — platform-wide |

`NULL` is a real answer, not a failure. It is the correct attribution for activity belonging to no
tenant, and those rows are shown to no organization's ADMIN.

### Why write-time and not read-time

Read-time derivation needs a join whose meaning depends on the entity type, which is why it produced
the two faults above. Write-time attribution is a single fact recorded once, by the code path that
already knows the answer, and it is indexable.

### The backfill

Derive where derivable, `NULL` otherwise — in derivability order, each step touching only rows still
unattributed:

| # | Rule |
|---|---|
| 1 | the row's actor has an organization |
| 2 | the audited entity is an `Organization` |
| 3 | the audited entity is a `User` |
| 4 | the audited entity is a `Role` — its `organization_id` after V35 (platform-wide roles stay `NULL`) |

**`all-DEFAULT` was rejected.** It is simpler and it is wrong: it would relabel another
organization's history as `DEFAULT`'s, producing rows that look correct and are not. An unattributed
row is honest; a misattributed one is not.

## Consequences

- **The ADMIN explorer's row count drops.** That is the fix, not a regression. The rows that
  disappear were another organization's, or the platform's.
- **System activity is invisible to tenants.** Correct, and it means an ADMIN investigating a
  suspected cross-organization action cannot see platform rows. That is the platform tier's surface.
- **`AuditTrail.Entry` gained a field.** It is an internal boundary, not a wire contract; no endpoint
  changed shape.
- **The scope is fail-closed.** An unattributable caller sees nothing rather than the installation's
  trail (G-10).

## Residuals

| Residual | Why it stays open |
|---|---|
| Historical rows whose actor has no organization stay `NULL` | cannot be attributed without inventing an answer |
| A row can only carry one organization | an action spanning two organizations is attributed to the actor's; the frozen schema has no join table for this |