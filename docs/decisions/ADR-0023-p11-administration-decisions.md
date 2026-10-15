# ADR-0023 — P11 Administration: control-plane decisions

- **Status:** Accepted
- **Phase:** P11 — Full Administration
- **Spec:** `specs/20-P11-Administration-V1.5.md`
- **Preserves:** the frozen four-table administration schema, the frozen API contract, and each owning
  module's authority over its own data.

## Context

Administration is a control plane, not a business domain. It owns exactly four tables and reaches
every other capability through the owning module's service. This ADR records the decisions the
specification left open and the exact boundary of what was implemented.

## Decisions

1. **Exactly four tables.** `organization_setting`, `business_rule`, `lookup`, `audit_log` — no
   support-ticket, incident, import-history, bulk-operation, search-index, health-history, or
   configuration-version table (`§7`). A migration test asserts that none of those tables exists.

2. **No god mode.** ADMIN is not a bypass. Every administration endpoint resolves an explicit
   permission through `AuthorizationService` (`§6`). The permission catalogue is seeded as
   implementation candidates (GAP-116). `ADMIN_ROLE_ASSIGN` is deliberately *not* granted to ADMIN:
   P1 froze the invariant that assigning ADMIN requires a permission the ADMIN role does not hold, and
   the first draft of the seed violated it — two P1 tests caught it and the grant was removed.

3. **Durable audit through a shared boundary, not a second mechanism.** Every module already recorded
   mutations through `SecurityEventLogger`. P11 adds the immutable `audit_log` table and a
   `common.audit.AuditTrail` interface; Administration implements it and the logger records through it
   when present. This closes the long-standing audit gap (GAP-001) for *all* phases at once, with no
   module depending on Administration and no duplicated audit path.

4. **Audit is atomic with the business mutation.** `AuditTrailService.record` uses
   `Propagation.REQUIRED`, so it joins the caller's transaction: the audit row and the mutation commit
   or roll back together (`§11.11`). The trail is insert-only — the entity is mapped `@Immutable`, the
   repository has no update or delete method, and no update/delete endpoint exists (`§11.2`).

5. **Configuration is validated against a registry, not merely parsed.** `organization_setting` and
   `business_rule` accept recognized keys only, with declared types, defaults, mutability and security
   classification, plus cross-field rules (`§8.4, §8.6`). A syntactically valid document is not
   accepted as a valid configuration.

6. **Rules are data, never code.** Keys suggesting script/SQL/expression/eval/class/method/exec/shell/
   file/path/url/network constructs are rejected, and the serialized configuration is size-bounded
   (`§9.3`).

7. **No cross-domain repository access.** Administration calls contracts: `ConfigurationCatalog` and
   `BusinessRuleResolver` are exposed for consumers; `AdminUserDirectory` is the contract
   Administration calls for Identity; the support notification action goes through Communication's
   `NotificationService` (`§82, §83`). Support masking is a presentation rule in Administration, and
   Identity stays the only writer of the `user` table.

8. **Concurrency without a version column.** The frozen schema has no optimistic-lock column, so
   organization-setting updates are serialized server-side with a pessimistic row lock (`§8.7`).

9. **Global search is explicit and permission-filtered.** Handlers are registered per resource from
   the frozen allow-list; a resource whose permission the caller lacks is omitted from both the
   searched-resource list and the results — never filtered after the fact (`§39, §40, §86`). Query
   parameters are never mapped into table names.

10. **Health never infers HEALTHY from missing telemetry.** Independent checks run in parallel with
    bounded timeouts; optional external components report UNKNOWN, so an unconfigured deployment is
    DEGRADED rather than falsely healthy (`§58-§61`). Only status, safe latency, and a message are
    returned.

## Boundary of what was implemented

Implemented: P11-01…P11-05, P11-14, P11-15 (user view + ad-hoc notification), P11-18 (framework plus
the USER and AUDIT_LOG handlers), P11-19, P11-20.

Deferred with recorded gaps: the cross-domain administration surfaces (P11-06…P11-13, which remain
reachable through the existing P1/P3/P4/P5/P6/P9/P10 APIs), imports (P11-16), bulk operations
(P11-17), the remaining search handlers, the administration frontend, and the load and release tasks
(P11-21, P11-22).

## Consequences

- Audit evidence is now durable and searchable, and it participates in the business transaction.
- `docs/known-gaps.md` GAP-001 is closed; GAP-116…GAP-125 record what remains.
