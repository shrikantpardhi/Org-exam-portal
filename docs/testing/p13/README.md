# P13 — Broader Reporting & Operational Reporting: Testing Notes

Phase specification: `specs/22-P13-Reporting-V1.5.md`.

## Scope verified

- The report catalog registry and its filter schemas.
- Request validation, authorization, and object scoping.
- Synchronous and asynchronous execution through `background_job`.
- CSV and PDF rendering, including CSV formula-injection mitigation.
- Protected artifact download, ownership, and signed tokens.
- Report history from `background_job`, with no reporting table.

## Unit tests

| Test | What it proves |
|---|---|
| `P13RendererTest.csvNeutralizesFormulaLikePrefixes` | `=`, `+`, `-`, `@` cells are neutralized (§20) |
| `P13RendererTest.csvQuotesEmbeddedDelimitersQuotesAndLineBreaks` | RFC 4180 quoting; line-break normalization prevents record forgery |
| `P13RendererTest.csvHasDeterministicHeaderAndRows` | stable column order and a UTF-8 BOM |
| `P13RendererTest.pdfIsAStructurallyCompleteDocument` | valid `%PDF-1.4` … `%%EOF`, catalog/pages/font, xref, trailer, page numbering |
| `P13RendererTest.pdfPaginatesAndNumbersEveryPage` | multi-page output numbers every page |
| `P13RendererTest.pdfEscapesDelimitersAndDropsUnrepresentableCharacters` | `(`, `)`, `\` escaped; non-representable glyphs replaced |
| `P13RendererTest.pdfRendersAnEmptyResultSetWithAMessage` | an empty result is a successful report (§17) |

Run: `cd backend && ./mvnw test -Dtest="P13RendererTest"`.

## Integration tests

| Test | What it proves |
|---|---|
| `P13MigrationIntegrationTest.noReportingTableWasCreated` | **no reporting table exists** (§8, §25) |
| `P13MigrationIntegrationTest.reportHistoryIsCarriedByBackgroundJob` | history reuses the job table's `job_type`/`initiated_by`/`parameters`/`result_reference` (§30) |
| `P13MigrationIntegrationTest.reportPermissionsAreSeededOnTheExpectedRoles` | ADMIN/STAFF/FINANCE scope; **students hold no report permission** and FINANCE has no executive scope (§76, §79) |
| `P13ReportingIntegrationTest.catalogAdvertisesOnlyPermittedReportTypes` | the catalog is permission-filtered and advertises unimplemented types with `enabled=false` (§11, §74) |
| `P13ReportingIntegrationTest.requestingAnUnpermittedReportIsRefused` | a student cannot request an executive report (403) |
| `P13ReportingIntegrationTest.requestValidationRejectsBadInputBeforeExecution` | unknown type, unsupported filter key (including a `sql` key), unordered range, oversized interval |
| `P13ReportingIntegrationTest.unimplementedReportFailsWithAControlledError` | a registered-but-unbacked type is refused, never an empty file |
| `P13ReportingIntegrationTest.synchronousReportReturnsRowsAndDefinedMetrics` | **report reconciliation**: rows come from the owning source; every executive KPI carries its definition (§70) |
| `P13ReportingIntegrationTest.hostileFilterValuesAreTreatedAsData` | **no arbitrary SQL**: a hostile filter returns 0 rows and leaves the audit trail unchanged (§7.3, §106) |
| `P13ReportingIntegrationTest.asynchronousReportLifecycleQueuesRunsAndDownloads` | **async job lifecycle**: `REPORT_GENERATION` row QUEUED → worker → COMPLETED → artifact download |
| `P13ReportingIntegrationTest.reportHistoryIsServedFromBackgroundJob` | history exposes the job from the job table, not a reporting table (§29) |
| `P13ReportingIntegrationTest.protectedDownloadRefusesAnotherUserAndTamperedTokens` | **protected download**: another principal is refused; a forged token is refused (§23, §27, §81) |
| `P13ReportingIntegrationTest.downloadReportsAMissingArtifactForAnUnfinishedJob` | an unfinished job yields a controlled 404, never an internal path (§28) |
| `P13ReportingIntegrationTest.aClaimedJobIsNotProcessedTwice` | the claim is race-safe; a second pass does not re-run the job |

Run: `cd backend && ./mvnw test -Dtest="P13*"`.

## Manual/curl smoke

```bash
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/reports/catalog
curl -X POST -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"reportType":"OPERATIONS_SYSTEM_HEALTH","filters":{},"format":"PDF"}' $BASE/api/v1/reports
curl -X POST -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"reportType":"OPERATIONS_AUDIT_ACTIVITY","filters":{"from":"2026-10-01T00:00:00Z","to":"2026-10-15T00:00:00Z"},"format":"CSV"}' \
     $BASE/api/v1/reports
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/reports/jobs/<jobId>
curl -H "Authorization: Bearer <admin>" -o report.csv $BASE/api/v1/reports/jobs/<jobId>/download
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/reports/history
```

## Implemented reports

| Report type | Category | Source | Mode |
|---|---|---|---|
| `OPERATIONS_USER_ACTIVITY` | Operations | Identity `AdminUserDirectory` (contact masked) | SYNC |
| `OPERATIONS_AUDIT_ACTIVITY` | Operations | Administration audit trail | ASYNC |
| `OPERATIONS_SYSTEM_HEALTH` | Operations | Administration health aggregation | SYNC |
| `EXECUTIVE_KPI_SUMMARY` | Executive | Identity + Administration | SYNC |

## Not implemented

Four families are registered with `enabled=false` and refuse with `REPORT_NOT_AVAILABLE`: Student,
Assessment, Content and Commerce (GAP-141). Also deferred: retention enforcement (GAP-143), job
cancellation (GAP-144), request-level idempotency (GAP-145), catalog caching (GAP-146), richer PDF
presentation (GAP-147), the reporting frontend (GAP-148), student self-service authorization
(GAP-142), and the staged performance and replica evidence (GAP-150).
