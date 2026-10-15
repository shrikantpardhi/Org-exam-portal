# ADR-0024 — P13 Reporting: read-only composition without new tables

- **Status:** Accepted
- **Phase:** P13 — Broader Reporting & Operational Reporting
- **Spec:** `specs/22-P13-Reporting-V1.5.md`
- **Preserves:** the frozen schema (no reporting table), the `background_job` contract, and every
  owning module's authority over its own data.

## Context

Reporting composes facts owned by other modules and renders them as artifacts. The specification
forbids a reporting persistence model, requires reports to be read-only, and freezes the request shape.
This ADR records how those constraints were met.

## Decisions

1. **No new tables.** Report history is served from the general-purpose `background_job` table where
   `job_type = 'REPORT_GENERATION'`: `initiated_by` scopes ownership, `parameters` holds the sanitized
   request fingerprint, and `result_reference` holds the opaque artifact key (`§25, §30`). The report
   catalog is application code — a Java enum, not a table (`§8, §12`). `V24` seeds permissions only;
   a migration test asserts that no `report`, `report_job`, `report_history`, `report_artifact`,
   `report_schedule`, `report_definition`, `report_catalog` or `export_job` table exists.

2. **Reports are read-only.** No provider writes anything. A test proves a hostile filter value leaves
   the audit trail unchanged (`§7.3`).

3. **No arbitrary SQL.** The request body accepts `reportType`, `filters`, `format` — there is no
   field that accepts SQL, a table name, or a column expression (`§106`). Only registered types map to
   controlled providers, and every filter key is validated against the report's declared filter schema
   before execution (`§15, §105`). Filter values are bound as data; a hostile value returns an empty
   result rather than being interpolated.

4. **Cross-module reads go through owning contracts.** Reporting depends on
   `AdminUserDirectory` (Identity) and a new Administration-owned `ReportingAdministrationCatalog`; it
   never touches another module's repository (`§10`). Providers map to explicit columns and rows — no
   business entity is serialized into a report (`§34`).

5. **Sync for small, async for large.** The catalog declares an execution mode per type; a
   `SYNC` report runs in the request, an `ASYNC_PREFERRED` report is queued as a `background_job` and
   rendered by the worker (`§7.5, §24, §93`).

6. **Worker isolation.** The report worker has its own batch size (default 5) and its own slower
   schedule (60 s), separate from the delivery, evaluation, analytics and communication schedules, so
   reporting cannot starve an exam-critical worker (`§94`).

7. **A shared transactional job-state service.** The `background_job` claim/complete/fail/reschedule
   methods are `@Modifying` queries and therefore need a transaction. Calling them directly from a
   worker loop fails with `TransactionRequiredException` — the same defect first hit in P10. Rather
   than add a second copy, this phase introduced `common.job.JobStateService` and moved the three P10
   communication workers onto it, deleting `CommunicationJobService`.

8. **CSV formula-injection mitigation.** Any field beginning with `=`, `+`, `-`, `@`, tab or carriage
   return is prefixed with a single quote, embedded line breaks are normalized so a field cannot forge
   a record, and quoting follows RFC 4180 (`§20`).

9. **PDF without a dependency.** The renderer emits a valid PDF 1.4 document directly — object graph,
   content streams, xref, trailer — with title, period, applied filters, generation metadata, summary
   metrics, tables and page numbers. Tests assert the structure, pagination, delimiter escaping, and
   that non-representable glyphs are replaced rather than emitted raw. The platform gains no PDF
   library (`§19`).

10. **Protected download.** The artifact is written under a per-job directory with a sanitized,
    deterministic name derived from the report type and format — never from a raw user string
    (`§32`). Download requires either ownership of the job (or an operational read permission) or a
    short-lived HMAC-signed token scoped to the exact artifact, and the response carries
    `Cache-Control: no-store`. No filesystem path, storage key, or credential is ever returned
    (`§22, §23`).

11. **Executive KPIs carry definitions.** Every executive metric is emitted with its explicit
    definition and source, and the PDF prints the definition beside the value, because a KPI without a
    definition is semantically ambiguous (`§70`).

12. **A student gets no report permission.** P1 froze the invariant that a student's permission set is
    empty, so this phase removed the student report grant that the first draft of `V24` added — an
    existing P1 test caught it. Student self-service reporting therefore needs an explicit
    authorization decision when those reports are implemented (recorded as a gap), rather than being
    resolved by inventing a permission.

## Boundary of what was implemented

Implemented: P13-01…P13-11, P13-14 (content inventory is registered), P13-16 (operations),
P13-17 (executive), P13-18, P13-19, P13-21, and the catalog registration of every `§12` identifier.

Four report families — Student, Assessment, Content and Commerce — are registered in the catalog but
**disabled** (`enabled=false`), because their owning modules do not yet expose a read contract at the
required grain. Requesting one fails with a controlled `REPORT_NOT_AVAILABLE`, never a silently empty
artifact. They are listed in `docs/known-gaps.md` (GAP-141).

Deferred with gaps: report retention enforcement, cancellation, request-level idempotency, catalog
caching, the reporting frontend, and the staged performance evidence.
