# Task 11 / D-11 — the audit trail carries an organization

**Status:** complete (Gate B approved 2026-10-26)

| Field | Value |
|---|---|
| Defect | D-11 |
| Fix class | Schema + write path + read scope + ADR |
| ADR | ADR-0046 |
| Migration | `V36__audit_log_organization.sql` |
| Tests | 4 in `AuditLogOrganizationScopeIntegrationTest` |

## The defect

`audit_log` had no `organization_id`, so the ADMIN explorer returned all 5,867 rows of a
two-organization deployment. Organization was derived through `user` at read time, which reported
nothing for entities with no user (organizations, business rules, providers, scheduled jobs).

## Change set

| File | Change |
|---|---|
| `V36__audit_log_organization.sql` | **new** — column, FK, index, 4-step backfill |
| `administration/entity/AuditLog.java` | `organizationId` |
| `common/audit/AuditTrail.java` | `Entry` gains `organizationId` |
| `common/audit/SecurityEventLogger.java` | resolves the organization at write time via a small lookup interface |
| `identity/service/UserOrganizationLookupService.java` | **new** — implements that interface |
| `administration/service/AuditQueryService.java` | scopes by the actor's organization, fail-closed |
| `administration/controller/AuditLogController.java` | passes the principal |
| `docs/decisions/ADR-0046-audit-log-organization.md` | **new** |

## Why a lookup interface instead of a repository dependency

`SecurityEventLogger` lives in `common` and must not depend on Identity. Identity implements the small
`UserOrganizationLookup` interface, so the dependency points inward and modules without Identity still
construct the logger. The dependency is `@Autowired(required = false)` so plain unit tests that do
`new SecurityEventLogger()` continue to work.

## The backfill, and the option I rejected

Derive where derivable, `NULL` otherwise: actor's organization → organization entity → user entity →
role owner. `all-DEFAULT` was rejected because it would relabel another organization's history as
`DEFAULT`'s — rows that look correct and are not.

## A test that would have passed for the wrong reason

The first version asserted that audit rows exist for users the fixture created. They do not: the base
test class writes users **directly through the repository**, which emits no audit row. Two of the
three tests failed against an empty expectation.

The fix was to drive the real `SecurityEventLogger` rather than to weaken the assertion. This is the
second time in this mission that a test's setup rather than its assertion was the defect (Task 7's
PLATFORM fixture was the first).

## Tests

| Test | Asserts |
|---|---|
| `theAuditExplorerReturnsOnlyTheCallersOrganization` | another organization's row is absent |
| `theAuditWritePathRecordsTheOrganization` | the column is written, not derived |
| `anOrganizationScopedEntityIsAttributedToThatOrganizationEvenWithNoActor` | rule 1 of the order — the case read-time derivation could never cover |
| `platformWideRowsAreNotShownToATenant` | `NULL` rows belong to the platform, not to any tenant |

## Residual

Historical rows whose actor has no organization stay `NULL` and are invisible to tenants. That is
correct: they cannot be attributed without inventing an answer.