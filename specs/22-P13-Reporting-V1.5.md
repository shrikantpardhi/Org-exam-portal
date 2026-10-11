# Smart Competitive Examination Platform
## P13 — Broader Reporting & Operational Reporting Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P13 — Broader Reporting & Operational Reporting
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, P4 Blueprint and Assessment Management, P5 Assessment Delivery and Runtime Examination Engine, P6 Evaluation and Results, P7 Scale Validation and Live Pilot, P8 Full Analytics and Insights, P9 Commerce and Access Management, P10 Communication and Notification, P11 Full Administration, P12 Production Hardening and Production Readiness
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**API:** REST + JSON, `/api/v1`
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Operations, Architects

---

## 1. Purpose

This document converts the broader reporting capability into an implementation-ready engineering specification.

P13 extends the platform from analytics dashboards into broader business and operational reporting.

The API contract already defines a Reports API, asynchronous large-report generation, report job status, and report history.

P13 implements reporting as an application capability and output layer rather than as a new business domain.

The central rule.

```text
Transactional domains own business truth
          |
          v
Analytics owns approved derived aggregates
          |
          v
Reporting reads controlled projections
          |
          v
Report artifact is generated asynchronously where required
          |
          v
Protected artifact is delivered to authorized caller
```

P13 does not create a new report table merely to make the implementation convenient.

---

## 2. Source-of-truth hierarchy

Read these documents before P13 implementation:

- Master PRD
- Domain Model
- Technical Design Document
- Database Standards and Schema
- API Contract
- Architecture Freeze
- Development Roadmap
- ExamEngine and CDUI Contract
- P0 through P12 Implementation Specifications
- This document

The earlier documents already freeze the report contract, the asynchronous job model, and the protected file delivery mechanism. This document does not modify those decisions.

---

## 3. Source-bound requirements

The roadmap states.

```text
Operations Platform: Admin, Communication, Reporting
```

The API contract defines these report endpoints.

```http
POST /api/v1/reports
GET  /api/v1/reports/jobs/{jobId}
GET  /api/v1/reports/history
```

The API contract states that reports are business outputs rather than database entities and names these report categories.

```text
Student
Assessment
Content
Commerce
Operations
Executive
```

These source commitments are carried forward unchanged.

---

## 4. Purpose and scope

P13 provides implementation-ready behavior for:

```text
Report catalog management in code and configuration
Report request validation
Synchronous small-result reporting where safe
Asynchronous large-report generation
Protected report artifact delivery
Report status tracking
Report history behavior
Student reports
Assessment reports
Content reports
Commerce reports
Operations reports
Executive reports
Permissions and organization scoping
Data quality and reconciliation
Report performance and load testing
Operational monitoring
```

---

## 5. Non-goals

P13 does not introduce.

```text
report_definition table
report_history table
report_schedule table
custom_dashboard table
custom_metric table
saved_filter table
report_builder table
warehouse schema
search index
arbitrary SQL endpoint
BI semantic warehouse
```

P13 does not convert the platform into an unrestricted BI system.

P13 does not make reporting a bypass around module authorization.

P13 does not permit report consumers to execute arbitrary SQL.

P13 does not expose internal JPA entities as a report contract.

P13 does not make analytics aggregates the sole source for every report. A report may read transactional projections or approved analytics aggregates depending on metric semantics.

---

## 6. Position in the delivery roadmap

The operational sequence.

```text
P8 Analytics
      |
      v
P9 Commerce
      |
      v
P10 Communication
      |
      v
P11 Administration
      |
      v
P12 Hardening and Production Readiness
      |
      v
P13 Broader Reporting and Operational Reporting
```

P13 is post-hardening reporting expansion.

The objective is to expose trustworthy read-oriented outputs across domains. It does not reopen validated transactional architecture.

---

## 7. Reporting principles

### 7.1 Domain ownership

Each metric remains owned by the domain that creates its source truth.

```text
Delivery        -> attempts, responses, snapshot facts
Evaluation      -> evaluations, results, scorecards
Commerce        -> purchases, payments, access grants
Communication   -> notifications, templates, announcements
Administration  -> settings, rules, lookups, audit
Analytics       -> approved derived aggregates
```

Reporting composes these sources. It does not own them.

### 7.2 Immutable business artifacts

Published assessments, answer keys, evaluations, and published results remain authoritative according to their existing contracts.

A report reflects those authoritative states at report-generation time.

### 7.3 Read-only reporting

Reports do not mutate business state.

A report never repairs a result, changes a payment, publishes an assessment, changes an answer key, activates a user, or modifies a notification.

### 7.4 Backend authority

All report filters, authorization, field selection, and object scope are validated by the backend.

### 7.5 Async large work

Large reports execute through background workers.

### 7.6 Bounded queries

Every report has bounded filter semantics, pagination or chunking where applicable, and an explicit data volume strategy.

### 7.7 Reproducibility

A report request is traceable to reportType, filters, format, requesting principal, request time, execution status, and artifact reference when available.

The source model does not freeze a durable schema for this metadata. Where durable history is required, the implementation uses the general-purpose `background_job` table.

---

## 8. Reporting architecture

Recommended architecture.

```text
                 Report API
                     |
                     v
            Report application service
                     |
          +----------+----------+
          v                     v
 Report authorization      Report request validator
          |                     |
          +----------+----------+
                     v
               Report registry
                     |
        +------------+------------+
        v                         v
Small or bounded query     Async report job
        |                         |
        v                         v
 Report projector            Report worker
        |                         |
        +------------+------------+
                     v
                Report renderer
                     |
                     v
             Protected file store
                     |
                     v
              Authorized download
```

The report registry is an application-level catalog, not a new database entity.

---

## 9. Backend module placement

Use a dedicated reporting package and module inside the modular monolith.

```text
reporting/
  api/
  application/
  catalog/
  domain/
  projection/
  renderer/
  storage/
  authorization/
  validation/
  worker/
  mapper/
  dto/
  exception/
```

Reporting code depends on domain application contracts and read-side projections.

Reporting does not depend directly on another module's internal repositories unless the module boundary explicitly permits that read contract.

---

## 10. Cross-module read boundary

Preferred pattern.

```text
ReportingService
      |
      v
AssessmentReportingDataProvider
      |
      v
Assessment-owned projection and query contract
```

Avoid.

```text
ReportingService
      |
      v
AssessmentJpaRepository
```

when that repository is an internal implementation detail of the owning module.

This keeps reporting decoupled from persistence layout.

---

## 11. Report registry

A report registry is required in application code and configuration.

Each report entry defines at minimum.

```text
reportType
category
displayName
description
supportedFormats
permission
filterSchema
executionMode
projectionProvider
renderer
```

Example.

```text
reportType      = ASSESSMENT_ATTEMPTS
category        = ASSESSMENT
displayName     = Assessment Attempts
permission      = REPORT_ASSESSMENT_ATTEMPTS_READ
executionMode   = ASYNC_PREFERRED
```

The exact permission namespace follows the project's established Identity convention.

---

## 12. Report type naming

Use stable machine-readable identifiers.

Suggested initial identifiers.

```text
STUDENT_PROFILE
STUDENT_ATTEMPTS
STUDENT_PERFORMANCE
STUDENT_TOPIC_PERFORMANCE
ASSESSMENT_SUMMARY
ASSESSMENT_ATTEMPTS
ASSESSMENT_PERFORMANCE
ASSESSMENT_QUESTION_ANALYSIS
ASSESSMENT_SCHEDULE_ACTIVITY
CONTENT_QUESTION_INVENTORY
CONTENT_VERSION_STATUS
CONTENT_TAG_COVERAGE
COMMERCE_PURCHASES
COMMERCE_PAYMENTS
COMMERCE_ACCESS_GRANTS
COMMERCE_REVENUE_SUMMARY
OPERATIONS_USER_ACTIVITY
OPERATIONS_AUDIT_ACTIVITY
OPERATIONS_COMMUNICATION_STATUS
OPERATIONS_SYSTEM_HEALTH
EXECUTIVE_KPI_SUMMARY
EXECUTIVE_ORGANIZATION_PERFORMANCE
```

These are report catalog candidates. Their exact availability depends on the source data and implemented permissions.

No new database enumeration table is required.

---

## 13. Report category contract

The category names remain.

```text
Student
Assessment
Content
Commerce
Operations
Executive
```

Additional top-level categories are not added without an API versioning decision.

---

## 14. Report request API

```http
POST /api/v1/reports
```

Request shape.

```json
{
  "reportType": "ASSESSMENT_ATTEMPTS",
  "filters": {
    "assessmentId": "<uuid>",
    "from": "2026-10-01",
    "to": "2026-10-31"
  },
  "format": "PDF"
}
```

P13 preserves this top-level shape.

Additional request fields require API contract amendment.

---

## 15. Request validation

Validation confirms reportType exists, reportType is enabled, caller has permission, filters match report schema, UUIDs are valid, date range is valid, range does not exceed configured limit, format is supported, and requested objects belong to authorized organization and scope.

Invalid requests fail before expensive query execution.

---

## 16. Date range validation

Every time-bounded report defines.

```text
from inclusive or exclusive semantics
to inclusive or exclusive semantics
maximum allowed interval
timezone boundary
```

The platform source does not freeze one universal reporting timezone.

The initial implementation uses the documented operational timezone choice and exposes the semantics in report documentation.

Timezone is not inferred from the browser.

---

## 17. Empty results

An empty result set is not a system failure.

The report completes successfully with `rowCount = 0` where row count is part of the implementation result model.

The rendering layer produces a clear "no matching data" representation instead of a broken artifact.

---

## 18. Report format strategy

The source contract demonstrates `format = PDF`.

P13 supports formats only when explicitly implemented and tested.

Initial safe implementation.

```text
PDF
CSV
```

Adding another format requires implementation and contract testing.

Spreadsheet XLSX support is not claimed until a tested renderer exists.

---

## 19. PDF rendering

PDF reports have title, report period, applied filters, generated timestamp, generated by or scoped principal where permitted, summary metrics, data tables, page numbers, and organization branding only where authorized.

Sensitive internal ids are not exposed unless the report specifically needs them.

---

## 20. CSV rendering

CSV is suitable for machine analysis.

CSV generation streams or chunks large datasets, escapes commas and quotes, normalizes line breaks, avoids formula injection hazards, uses deterministic column ordering, and addresses spreadsheet formula injection for fields beginning with formula-like prefixes.

---

## 21. Large file handling

Large report artifacts are not held fully in application memory when avoidable.

Preferred flow.

```text
Query stream or chunk
      |
      v
Renderer stream or chunk
      |
      v
Object storage multipart or stream upload
      |
      v
Artifact reference
```

---

## 22. Protected download

The API contract uses a protected download URL concept.

The implementation never returns filesystem path, raw bucket credentials, or permanent public object URL.

Download authorization is enforced independently of the report generation request.

---

## 23. Download security

A download token or signed URL implementation has short lifetime, scope to exact artifact, authorization verification, optional one-time behavior if supported, and redaction of storage internals.

The exact mechanism reuses the platform's existing protected file and artifact mechanism.

An unapproved file-access API is not created merely for reports.

---

## 24. Asynchronous report contract

For large reports the API contract defines 202 Accepted with a response such as the following.

```json
{
  "success": true,
  "message": "Report generation started",
  "data": {
    "jobId": "<uuid>",
    "status": "QUEUED"
  }
}
```

---

## 25. Async state model

Use the existing `background_job` lifecycle.

Minimum report state semantics.

```text
QUEUED
RUNNING
COMPLETED
COMPLETED_WITH_ERRORS
FAILED
CANCELLED (only if job infrastructure already supports cancellation)
```

New persistence is not invented just to model these states.

---

## 26. Idempotent report jobs

Repeated report requests with the same idempotency key do not create duplicate work where the API contract requires idempotency.

At minimum.

```text
Same idempotency key
Same authenticated principal
Same request fingerprint
-> one logical report job and result
```

Different requested formats or filters produce different work identities.

---

## 27. Report job ownership

A report job has a security scope tied to the requesting principal and organization context.

A student does not retrieve another user's report job by substituting a UUID.

---

## 28. Report job status API

```http
GET /api/v1/reports/jobs/{jobId}
```

Response exposes only safe fields.

```text
jobId
status
Safe progress if supported
Safe error code if failed
downloadUrl when authorized and completed
```

Stack traces and infrastructure identifiers are not exposed.

---

## 29. Report history API

```http
GET /api/v1/reports/history
```

The API contract names report name, generated by, generated date, status, and download.

The implementation provides these fields from the general-purpose `background_job` table.

---

## 30. Report history source

Report history is served from the general-purpose `background_job` table where `job_type = 'REPORT_GENERATION'`.

The `initiated_by` column is used for ownership scoping.

The `parameters` column stores the sanitized request fingerprint.

The `result_reference` column stores the opaque storage reference for the artifact.

---

## 31. Report history retention

Retention is defined for job metadata and artifact.

The frozen schema does not fix retention duration.

P13 uses a documented operational default.

```text
Completed report jobs retained 90 days
Failed report jobs retained 30 days
Artifacts retained in tandem with job metadata
```

The values are configurable through `BusinessRule`.

---

## 32. Report naming

Generated artifact names are deterministic and safe.

Example.

```text
assessment-attempts-2026-10-01-2026-10-31.pdf
```

Raw user-provided strings are never used as storage paths.

---

## 33. Report projection pattern

Each report has a projection contract.

Example.

```java
interface AssessmentAttemptsReportProvider {
    Stream<AssessmentAttemptRow> stream(ReportCriteria criteria);
}
```

Projection DTOs contain only fields required by the report.

---

## 34. No entity serialization

Forbidden.

```java
return repository.findAll();
```

from controller code.

Reports map to explicit DTO and projection structures.

---

## 35. Report query policy

Every report documents primary source, joins, filters, grouping, ordering, pagination or chunking, expected cardinality, and index dependency.

This becomes part of the implementation review evidence.

---

## 36. Primary source selection

Use transactional tables when the report needs authoritative current business facts.

Use analytics tables when a report needs approved derived metrics and the analytics contract already represents the required grain.

Not all reports are forced through Analytics merely for convenience.

---

## 37. Student reports

Student reports are scoped to the authenticated student or to authorized staff and admin organization scope.

Candidate reports.

```text
Student Profile
Student Attempts
Student Performance
Student Topic Performance
```

---

## 38. Student profile report

Possible content.

```text
Student identity fields permitted by role
Account status
Registration and basic profile data allowed by contract
```

Do not include password hash, login session secrets, payment provider secrets, or internal authorization data.

---

## 39. Student attempts report

Candidate fields.

```text
Assessment
Attempt identifier where permitted
Mode
Start and finish state
Date and time
Score and result fields where published and authorized
```

The exact fields come from Delivery and Evaluation contracts.

Hidden answer-key details are not exposed to students.

---

## 40. Student performance report

The preferred current source is the frozen StudentAnalytics aggregate where its fields satisfy the report.

Frozen fields.

```text
assessments_attempted
average_score
accuracy
average_time
last_calculated_on
```

Additional metrics require a separate approved source or bounded query.

---

## 41. Student topic performance report

Topic-level reporting may use TopicAnalytics where the required scope exists.

The report preserves taxonomy mapping uncertainty.

If a source record cannot be safely mapped to a topic, it is represented as unmapped rather than guessed.

---

## 42. Assessment reports

Assessment reports form the core operational reporting set.

Candidate initial reports.

```text
Assessment Summary
Assessment Attempts
Assessment Performance
Assessment Question Analysis
Assessment Schedule Activity
```

---

## 43. Assessment summary

Possible measures.

```text
Total attempts
Average score
Highest score
Lowest score
Assessment identity and status
```

The first four metrics align with frozen AssessmentAnalytics fields.

AssessmentAnalytics is used for current aggregate values where the semantics match.

---

## 44. Assessment attempts

The source API example explicitly uses `ASSESSMENT_ATTEMPTS`.

Typical filters.

```text
assessmentId
from
to
```

Additional filters are supported only after catalog definition and authorization rules are established.

---

## 45. Assessment performance

Possible measures.

```text
Attempt count
Score distribution summary
Completion and submission state where source supports it
Published-result coverage where applicable
```

Distributions are not fabricated if the source query is not defined.

---

## 46. Assessment question analysis

Question-level reporting may combine QuestionAnalytics, Evaluation outcomes, and AssessmentQuestion placement.

Potential fields.

```text
Question reference
Attempt count
Correct attempts
Incorrect attempts
Average time
Difficulty index
```

These mirror frozen QuestionAnalytics fields.

---

## 47. Assessment schedule activity

Schedule reports describe schedule identity, mode, start and end window, status, and attempt counts where linked through the approved Delivery relationship.

Business meaning is not inferred from raw timestamps without the schedule semantics established by Assessment.

---

## 48. Section-level reports

A section report is possible only where the existing Assessment and Delivery data provide a stable section grain.

The current frozen analytics schema does not contain a dedicated section_analytics table.

P13 uses bounded derived queries or existing report projections rather than creating a new analytics table.

---

## 49. Content reports

Content reports focus on inventory and operational quality.

Candidate reports.

```text
Question Inventory
Question Version Status
Tag Coverage
Asset Coverage
Previous Exam Metadata Coverage
```

---

## 50. Question inventory

Possible dimensions.

```text
Exam
Subject
Chapter
Topic
Sub-topic
Status
Question template
Language
```

Only relationships that exist in the frozen Academic and Content model are used.

---

## 51. Question version status

Report the lifecycle state of question versions without exposing internal drafts to unauthorized students.

Staff, reviewer, and admin roles receive different scopes.

---

## 52. Tag coverage

Use the existing QuestionTag relationship.

Useful measures.

```text
Questions tagged
Questions untagged
Questions by tag and category
```

The exact classification follows the existing Tag model.

---

## 53. Asset coverage

Use Asset relationships attached to content where the source supports them.

Candidate quality views.

```text
Questions with assets
Questions without assets
Asset type distribution
```

No quality conclusion is inferred solely from asset presence.

---

## 54. Previous exam history coverage

Use PreviousExamHistory records where present.

The report exposes coverage and completeness measures but does not imply correctness of historical metadata unless verified by content operations.

---

## 55. Commerce reports

Candidate commerce reports.

```text
Purchases
Payments
Access Grants
Subscription Summary
Revenue Summary
Coupon Activity
Refund Activity
```

Commerce authorization remains owned by AccessGrant and Commerce.

---

## 56. Purchase report

Use Purchase data.

Possible dimensions.

```text
Purchase status
Date
Goods and access package reference
Student and customer reference when authorized
```

Payment secrets are not exposed.

---

## 57. Payment report

Use PaymentRecord data and owning Commerce contracts.

Possible measures.

```text
Payment count
Status distribution
Provider reference where role permits
Amount where contract permits
```

Provider-sensitive values are masked.

---

## 58. Access grant report

AccessGrant is the authorization source of truth.

A report may show grant state but does not treat Purchase as the direct authorization source.

Diagnostic relationship.

```text
Purchase
  |
  v
Payment
  |
  v
AccessGrant
```

---

## 59. Subscription report

Use SubscriptionPlan and subscription-related business contracts already frozen.

A customer-level recurring billing history model is not invented if not present in the database.

---

## 60. Revenue summary

Revenue reporting semantics distinguish at least.

```text
Gross transaction amounts
Successful payments
Refunds
Net recognized operational amount
```

The source does not freeze an accounting definition for revenue.

Therefore the report uses an explicitly approved business definition rather than silently equating payment total with accounting revenue.

The platform default accounting definition.

```text
Revenue = Sum of Purchase.amount
          for Purchase where payment_status = SUCCESS
          minus Sum of Purchase.amount
          for Purchase where payment_status = REFUNDED
```

---

## 61. Coupon activity

Use Coupon and Purchase relationships where available.

Report coupon usage count, status, validity window, and associated business objects.

Coupon secrets and unrestricted administrative internals are not exposed to students.

---

## 62. Refund activity

Refund status is sourced from the Commerce-owned payment and refund contract.

Communication failures do not change refund truth.

---

## 63. Operations reports

Operations reports are designed for staff, support, and admin operational use.

Candidate reports.

```text
User Activity
Audit Activity
Communication Status
System Health
Report Job Activity
```

The exact availability depends on source observability and frozen APIs.

---

## 64. User activity report

Possible measures.

```text
Logins
Active users
Recent account activity
Session outcomes
```

Use LoginSession and Identity contracts.

Logged-in-once is not equated with active user unless the organization's active-user definition is documented.

---

## 65. Audit activity report

AuditLog is immutable append-only operational evidence.

Use `GET /api/v1/audit-logs` for authorized audit reads.

Reporting may summarize audit activity but does not mutate or rewrite audit records.

---

## 66. Communication status report

P10 defined operational communication metrics.

```text
Notification throughput
Email throughput
Success and failure
Retry backlog
Provider latency
Oldest queued job
Template errors
Announcement fan-out
```

These are surfaced through existing metrics and job infrastructure.

The frozen database does not define a delivery-history table.

---

## 67. System health report

System health consumes the existing observability stack.

The architecture already includes Prometheus, Grafana, and Loki.

P13 does not create a health-history table simply to render a report.

---

## 68. Report job activity

Where the `background_job` infrastructure exposes safe job metadata, operations reporting may show queued, running, completed, failed, latency, and backlog.

This uses operational telemetry and job state rather than a new business table.

---

## 69. Executive reports

Executive reporting is a controlled roll-up of authoritative metrics.

Candidate outputs.

```text
Executive KPI Summary
Organization Performance
Commercial Snapshot
Assessment Delivery Snapshot
```

Executive reports are read-only and scoped to authorized leadership and admin permissions.

---

## 70. Executive KPI principles

Every executive metric declares.

```text
Metric name
Definition
Time period
Source
Calculation
Population
Last refresh time
```

This prevents visually attractive but semantically ambiguous dashboards.

---

## 71. Organization performance

Frozen OrganizationAnalytics provides.

```text
total_students
total_assessments
total_attempts
active_users
last_calculated_on
```

These form a current organization-level executive summary where their definitions are accepted by the business.

---

## 72. Cross-organization boundary

The pilot model does not require cross-organization benchmarking.

P13 keeps executive reporting within authorized organization scope unless a future multi-tenant or global-admin contract explicitly expands it.

---

## 73. Student privacy

Student-level reports require stronger data minimization than aggregate reports.

Students see only their own data.

Staff, support, and admin visibility is permission-driven.

---

## 74. Report authorization

Recommended authorization flow.

```text
JWT
 |
 v
Permission
 |
 v
Organization scope
 |
 v
Resource scope
 |
 v
Report filter validation
 |
 v
Execution
```

Authorization is evaluated server-side.

---

## 75. Permission examples

Candidate granular permissions.

```text
report.student.read
report.assessment.read
report.content.read
report.commerce.read
report.operations.read
report.executive.read
report.history.read
report.generate
```

These are candidate permission identifiers and map to the project's exact Identity conventions before seeding.

---

## 76. Student self-service rule

A student request is automatically scoped.

```text
caller.userId == requested student identity
```

unless a different explicit report contract exists.

A student cannot filter a student report by another user id merely by changing a query parameter.

---

## 77. Staff scope

Staff access is constrained to the organization and explicit permissions.

`if STAFF then all reports` is not implemented.

---

## 78. Support scope

Support may require operational visibility while still being restricted from financial, answer-key, or sensitive content fields that are unrelated to support duties.

The report field set is role-aware where necessary.

---

## 79. Finance scope

Finance users receive commerce-specific report access without automatic access to assessment-content internals.

---

## 80. Admin scope

Administrators may have broad organizational reporting permissions. Reporting still honors organization boundaries and explicit permission checks.

---

## 81. IDOR protection

Every report endpoint is tested for object substitution.

Examples.

```text
Replace assessmentId
Replace studentId
Replace jobId
Replace resultId
Replace organization-scoped object id
```

The server rejects access outside the caller's scope.

---

## 82. Sensitive fields

Never include password hash, JWT secrets or tokens, payment provider secrets, raw provider credentials, private object-store credentials, server filesystem paths, or internal stack traces.

Payment and student PII fields are limited to the report contract.

---

## 83. Report filters

Each report catalog entry declares its filter schema in code.

Typical reusable filter classes.

```text
organizationId (server-derived, not client-trusted)
studentId
assessmentId
examId
subjectId
topicId
status
from
to
mode
```

Not every report supports every filter.

---

## 84. Filter composition

Unsupported combinations fail validation rather than being silently ignored.

Example.

```text
ASSESSMENT_ATTEMPTS with assessmentId and date range = valid
ASSESSMENT_ATTEMPTS with studentId if catalog does not support it = validation error
```

---

## 85. Sort contract

Every tabular report defines deterministic ordering.

`ORDER BY random()` or unspecified row order is avoided.

---

## 86. Pagination and chunking

Interactive previews are paginated.

Async exports use chunked iteration.

The full result set is not loaded into a Java collection for large reports.

---

## 87. Database index policy

Every performance-critical report query is reviewed using `EXPLAIN` and `EXPLAIN ANALYZE` where appropriate.

Indexes are justified by report filters and workload evidence.

No blind index creation.

---

## 88. Analytics reuse rule

Use analytics aggregates when they already represent the required metric grain.

Example.

```text
AssessmentAnalytics
   |
   v
Assessment Summary report
```

Millions of StudentResponse rows are not scanned merely to reconstruct an already approved current aggregate.

---

## 89. Transactional read rule

Use transactional projections when the report requires detail-level authoritative facts.

Example.

```text
AssessmentAttempts report
   |
   v
Delivery and Evaluation read projection
```

---

## 90. Mixed source report

A single report may combine transactional detail and analytics summary only if the metric definitions are explicitly aligned.

The report labels or documents the refresh semantics when one component is derived and another is current transactional data.

---

## 91. Freshness disclosure

Reports based on analytics expose `lastCalculatedOn` or an equivalent safe freshness indicator where the underlying aggregate provides it.

Stale derived data is not presented as real-time transactional truth.

---

## 92. Report consistency window

For a multi-section report, source data may change during generation.

P13 documents whether the report is point-in-time, best-effort current, or transaction-consistent.

The current project does not freeze a universal reporting snapshot mechanism.

Point-in-time consistency is not claimed unless the implementation actually provides it.

---

## 93. Long report strategy

For potentially large data sets.

```text
Validate
 |
 v
Queue job
 |
 v
Worker acquires read scope
 |
 v
Stream and chunk source
 |
 v
Render
 |
 v
Upload artifact
 |
 v
Mark complete
```

---

## 94. Report worker isolation

Reporting work does not starve active examination delivery workers.

Prefer separate worker concurrency controls for exam-critical jobs, report jobs, communication jobs, and analytics jobs.

Use evidence-driven limits.

---

## 95. Database load isolation

Large report queries are scheduled and shaped to avoid destabilizing live examination traffic.

Where a read replica is considered, that is a separate infrastructure decision record.

---

## 96. Caching

Cache small, stable metadata where beneficial.

```text
Report catalog
Lookup values
Organization-safe labels
```

Sensitive, fast-changing student or payment data is not cached without a clear invalidation policy.

---

## 97. Redis boundary

Redis remains cache and infrastructure support.

It is not the source of truth for report data.

---

## 98. Report error model

Use controlled application errors such as.

```text
REPORT_NOT_FOUND
REPORT_NOT_SUPPORTED
REPORT_FILTER_INVALID
REPORT_FORBIDDEN
REPORT_TOO_LARGE
REPORT_GENERATION_FAILED
REPORT_ARTIFACT_UNAVAILABLE
REPORT_HISTORY_UNAVAILABLE
```

Exact error-code naming follows the common API exception contract.

---

## 99. Error responses

Do not return SQL exception message, filesystem exception details, provider credentials, or stack trace.

Return a safe error envelope with `X-Request-Id` for support diagnostics.

---

## 100. Report timeout policy

Interactive queries need bounded timeouts.

When execution exceeds the synchronous threshold, stop the interactive request, queue an asynchronous job, and return 202.

The exact threshold is performance-test-driven.

---

## 101. Report cancellation

Cancellation is optional and depends on the existing `background_job` infrastructure.

A new cancel endpoint is not invented unless separately added to the API contract.

---

## 102. Report retries

Retry only transient failures.

Examples.

```text
Worker restart
Transient storage failure
Network timeout
```

Deterministic errors such as malformed filters are not blindly retried.

---

## 103. Partial artifact handling

If rendering fails after partial artifact creation, mark the job failed, remove or quarantine the incomplete artifact, and avoid advertising the download.

The business source data remains unchanged.

---

## 104. Duplicate artifact risk

A retry does not make multiple user-visible artifacts for one logical job unless the job contract explicitly allows it.

Safe job identity and idempotent artifact handling are used.

---

## 105. Report generation security

Filters are treated as untrusted input.

Validate UUIDs, strings, enums, dates, ranges, sort fields, and format.

Raw filters are never interpolated into SQL.

---

## 106. SQL safety

Forbidden.

```text
GET /api/v1/query?sql=...
POST /api/v1/reports { "sql": "..." }
```

Only registered report types map to controlled query implementations.

---

## 107. Report catalog extensibility

New report creation requires code review and test evidence.

Expected change path.

```text
Add report catalog entry
 |
 v
Add filter schema
 |
 v
Add projection provider
 |
 v
Add renderer mapping if needed
 |
 v
Add authorization
 |
 v
Add tests
 |
 v
Add performance evidence
```

---

## 108. No custom report builder

A UI where users dynamically select arbitrary tables and columns is outside the current contract.

P13 uses curated report types.

---

## 109. Student dashboard reports

The student-facing portal may provide self-service report artifacts for own attempts, own performance, own topic performance, and own result and scorecard views.

Organization-wide reports are not exposed to students.

---

## 110. Staff reporting workspace

Staff UI provides report category, report name, description, filters, format, generate, job status, and history when authorized.

---

## 111. Admin reporting workspace

Admin UI may add organization scope, report permissions, broader filters, operations reports, and executive summaries.

The common Reports API is used.

---

## 112. Report generation UX

User flow.

```text
Select report
 |
 v
Enter filters
 |
 v
Validate
 |
 v
Generate
 |
 v
If small: receive result
If large: receive jobId
 |
 v
Observe status
 |
 v
Download securely
```

---

## 113. Loading state

The user is not left with an indefinite spinner.

For async jobs show queued, running, completed, and failed with clear recovery action on failure.

---

## 114. Accessibility

Report generation UI supports keyboard navigation, visible labels, form errors, focus management, and screen-reader accessible progress states.

---

## 115. Browser compatibility

Use the same supported browser matrix established by the production-hardening plan.

Report generation does not rely on browser-specific rendering for backend-generated artifacts.

---

## 116. Internationalization

Report titles and labels use the existing localization conventions where present.

Data values remain correctly encoded, especially for Indic languages.

---

## 117. PDF language support

PDF generation uses fonts and rendering rules that support the project's required languages.

A Latin-only font stack is not assumed to be sufficient.

---

## 118. CSV encoding

Use UTF-8.

Test Indian-language content in import and export workflows where relevant.

---

## 119. Report timezone display

Display timestamps using the approved user, organization, and deployment timezone policy.

UTC date filters and local date labels are not silently mixed.

---

## 120. Day boundary semantics

For reports grouped by day, document whether a day means UTC day, organization timezone day, or deployment timezone day.

The platform currently does not freeze an organization timezone field.

Default: UTC day with explicit filter boundary documentation.

---

## 121. Report metadata footer

Where practical, artifacts include generated at, report type, period, filter summary, and freshness note for derived metrics.

Internal storage paths are not exposed.

---

## 122. Report branding

Organization branding comes from approved organization configuration.

Arbitrary logo URLs or HTML supplied by the report request are not trusted.

---

## 123. HTML injection in reports

User-generated content appearing in reports is escaped or sanitized before rendering HTML or PDF.

Question rich text, announcements, names, and remarks are untrusted content from a rendering-security perspective.

---

## 124. Formula injection in CSV

Any cell beginning with a spreadsheet formula operator is safely escaped according to the CSV export security policy.

This is especially relevant for user names, remarks, question text fragments, and coupon labels.

---

## 125. PII minimization

Every report definition documents which fields are PII.

Only fields needed for the report purpose and caller role are included.

---

## 126. Audit of report generation

Generating privileged reports creates the appropriate audit event through the existing audit mechanism.

A reporting-specific audit table is not added.

---

## 127. Audit of downloads

If the existing protected artifact mechanism supports download auditing, use it for privileged report downloads.

Otherwise record only through approved existing audit capabilities.

A report-download table is not added without contract amendment.

---

## 128. Report data freshness

Reports clearly distinguish live transactional fields, current aggregates, operational metrics, and historical snapshots.

---

## 129. Analytics freshness

When using StudentAnalytics, AssessmentAnalytics, QuestionAnalytics, TopicAnalytics, or OrganizationAnalytics, include their `last_calculated_on` equivalent when useful and safe.

---

## 130. Data quality checks

Before publishing an artifact, validate report output for basic integrity.

```text
Negative counts
Impossible percentages
Invalid dates
Null required fields
Score bound violations
Currency formatting errors
```

---

## 131. Cross-source reconciliation

For critical reports compare selected report totals with source totals.

Examples.

```text
AssessmentAttempts report count vs qualifying attempt source count
Commerce payment report total vs successful payment source total
Organization summary vs OrganizationAnalytics
```

Discrepancies are investigated rather than silently adjusted.

---

## 132. Score and result integrity

Assessment result reports derive from Evaluation and Result contracts.

Scores are not recalculated inside Reporting.

The Reporting module never implements its own scoring engine.

---

## 133. Answer key security

Reports for students never expose private answer-key internals or unpublished answer mappings.

Staff access follows the existing evaluation permissions.

---

## 134. Published result boundary

A student-facing performance report uses published and authorized result state where publication is required by the existing Result contract.

Result publication rules are not bypassed.

---

## 135. Assessment immutability

A report may reference historical assessment versions, but it does not rewrite published assessment data.

---

## 136. Delivery integrity

Attempt-level reports respect the immutable snapshot model from Delivery.

The report does not reconstruct the question set from the current mutable assessment version.

---

## 137. Snapshot-aware reporting

For an attempt report.

```text
Attempt
 |
 v
AttemptQuestion
 |
 v
AttemptQuestionOption
 |
 v
StudentResponse and Evaluation
```

This preserves what the student actually received.

---

## 138. Performance report metrics

Use deterministic decimal formatting.

Floating-point arithmetic is not introduced in financial or scoring calculations where exact BigDecimal semantics are required.

---

## 139. Commerce precision

Monetary report calculations respect the Commerce contract's currency and precision rules.

Currency is not inferred from browser locale.

---

## 140. Report security boundary

The report itself is a protected artifact and is treated as sensitive data.

A report link is not an authorization substitute.

---

## 141. Signed URL expiration

Where signed URLs are used, expiry is short and operationally sufficient for legitimate downloads.

The exact expiry is configuration-driven and tested.

---

## 142. Report link reuse

A previously generated artifact may be reusable when still authorized and within retention.

The implementation re-checks access rather than treating the original creator's authorization as permanently valid.

---

## 143. Revoked user access

If a user loses permission after report generation, the existing report artifact does not automatically remain downloadable without policy validation.

Authorization at download time is required.

---

## 144. Deleted and archived objects

Report filters against archived and inactive objects follow the owning module's state semantics.

Reporting does not silently reinterpret archival as deletion.

---

## 145. Report availability by lifecycle

A report type may be disabled while dependencies are incomplete.

Catalog state is controlled by deploy-time configuration or code, not a hidden database record.

---

## 146. Feature flag boundary

A report feature flag may control UI exposure or catalog availability.

It is not used to bypass authorization.

---

## 147. API versioning

All reporting endpoints remain under `/api/v1`.

Breaking report contract changes require a new API version or an approved compatibility strategy.

---

## 148. Request correlation

Every report request propagates `X-Request-Id` into worker logs and audit context where supported.

---

## 149. Structured logging

Report logs include timestamp, requestId, jobId where applicable, reportType, category, userId where permitted, organization scope, duration, row count where safe, status, and error code.

Complete report payloads are not logged by default.

---

## 150. Logging redaction

Never log raw payment secrets, passwords, JWTs, private storage URLs, full sensitive student data unnecessarily, or full question bank dumps.

---

## 151. Reporting metrics

Required operational metrics include report_generation_total, report_generation_success_total, report_generation_failure_total, report_generation_duration, report_rows_generated, report_queue_depth, report_queue_oldest_age, report_download_total, report_download_failure_total, and report_query_duration.

Exact metric naming follows project observability conventions.

---

## 152. Report alerts

Candidate alerts.

```text
Report queue backlog
Report job failure spike
Report duration regression
Artifact storage failure
Report query timeout spike
Report download failure spike
```

---

## 153. Worker health

Health monitoring shows whether report workers are available and processing work.

Raw worker internals are not exposed through student APIs.

---

## 154. Storage health

Report artifact generation depends on file and object storage.

A storage outage produces controlled report failure or degradation without modifying transactional business state.

---

## 155. Database failure

If the database is unavailable, report generation fails safely, no false success is returned, and no fabricated report is produced.

Cached metadata is never presented as authoritative report data when source truth is unavailable.

---

## 156. Redis failure

Redis is not a report data source.

A Redis outage does not corrupt report truth.

Cache-dependent acceleration may degrade to PostgreSQL where safe.

---

## 157. Worker retry failure

After retry policy is exhausted, mark failure, record a safe diagnostic code, retain request correlation, and notify operational monitoring.

---

## 158. Provider failure

Reporting does not depend directly on payment, email, or other providers.

If a report includes provider data, it reads the persisted domain state rather than calling the provider on demand unless a future contract explicitly requires live provider reconciliation.

---

## 159. Report generation during live exams

Large reporting activity does not destabilize live exam delivery.

Operational policy.

```text
Exam delivery has higher runtime priority than large report rebuild or export.
```

---

## 160. Peak load scheduling

High-cardinality reports avoid scheduled execution during known exam start and submission peaks when operations can control timing.

---

## 161. Report load test dataset

Load testing includes a large student population, large assessment history, large attempt volume, large question bank, large payment history, and large audit history.

Production-like cardinality is used, not toy datasets.

---

## 162. Interactive report SLO

For each synchronous report, define a tested target for p50, p95, and p99.

The target is consistent with the applicable API performance target and workload evidence.

---

## 163. Async report SLO

Measure queue wait time, execution time, artifact upload time, and total time to downloadable artifact.

Only server query time is not optimized.

---

## 164. Report concurrency test

Test concurrent generation of small, medium, and large reports and verify that one expensive report does not monopolize all worker capacity.

---

## 165. Database protection test

During report load, observe database CPU, database I/O, connection pool utilization, PgBouncer pools, lock waits, and slow query volume.

---

## 166. PgBouncer boundary

PgBouncer remains in the production request path.

Report workloads respect the production pooling model and evidence-based pool sizing.

---

## 167. Connection management

Report workers release database resources promptly.

Streaming queries are closed deterministically even on renderer or storage failure.

---

## 168. Memory protection

Large reports are tested for heap growth, GC pressure, OOM risk, large byte-array allocation, and PDF renderer memory spikes.

---

## 169. CSV streaming strategy

CSV is the preferred first large-data export path because it can stream rows efficiently.

PDF has a tested page and chunk strategy for large tables.

---

## 170. PDF row pagination

PDF tables paginate automatically.

A single extremely wide table does not produce clipped or unreadable output.

---

## 171. Column selection

Each report has a fixed approved column set.

Arbitrary database-column selection through the public API is not allowed.

---

## 172. Report schema evolution

If a report adds or removes columns, update the catalog contract, update the renderer, update tests, update the sample artifact, and update documentation.

Silent semantic changes to executive reports are avoided.

---

## 173. Report definition versioning

The source does not define a persisted report-definition version.

P13 versions report definitions in code and release metadata if reproducibility requires it, without creating a new database table.

---

## 174. Report calculation version

Where a metric's calculation changes materially, record the calculation-version identifier in the generated artifact metadata or safe operational logs where supported.

A changed metric definition is not presented as historically identical to an older one.

---

## 175. Executive KPI definition registry

Executive reports require explicit metric definitions.

Recommended code-level registry metadata.

```text
metricCode
definition
source
grain
formula
freshness
```

No new database table is required.

---

## 176. KPI calculation ownership

Reporting calls an analytics calculator or owning domain projection rather than duplicating business formulas.

---

## 177. Average calculations

For metrics such as average score and time, use exact approved semantics.

Pre-averaged aggregates are not averaged unless mathematically valid for the intended weighting.

---

## 178. Percentage calculations

Accuracy and percentage metrics respect the existing analytics bounds.

```text
0 <= value <= 100
```

---

## 179. Score bounds

Score-related outputs remain consistent with assessment total marks, negative marking semantics, and bonus and grace semantics.

Reporting does not recalculate scoring rules.

---

## 180. Difficulty index

If QuestionAnalytics difficulty_index is reported, use the Analytics-defined field and meaning.

It is not reinterpreted as a universal psychometric score.

---

## 181. Active user metric

OrganizationAnalytics contains `active_users` but the source does not freeze a universal active-user definition.

Executive reporting documents the adopted definition before presenting it as a headline KPI.

The P8 definition applies.

```text
A user is active if the user has at least one StudentAttempt
with started_on within the last 30 days.
```

---

## 182. Progress metric

The source does not freeze a single canonical student progress formula.

A progress KPI is not invented for executive reporting without an explicit definition.

---

## 183. Trend reporting

Trend reporting is possible only when the source can support it without fabricating history.

Current aggregate tables are not time series.

If a trend requires historical snapshots, a future persistence decision is needed.

---

## 184. No silent time series table

Do not create student_analytics_history, assessment_analytics_history, or kpi_history without a formal schema amendment.

---

## 185. Report export history

The `/reports/history` contract shows only history that the existing `background_job` infrastructure can truthfully retain.

History is not manufactured from client-side browser state.

---

## 186. Browser download security

The frontend receives either a protected URL or a controlled download response.

Storage credentials are not exposed.

---

## 187. Frontend API layer

Use a dedicated reportingService, reportingHooks, report schemas, and report UI components.

Report-specific API calls are not placed throughout pages.

---

## 188. Frontend report catalog

The frontend renders the report catalog from backend-safe metadata where practical rather than duplicating authorization truth in hard-coded UI rules.

Frontend visibility is UX, not security.

---

## 189. Frontend filter forms

Use React Hook Form and Zod consistent with the project frontend baseline.

The backend performs authoritative validation again.

---

## 190. TanStack Query

Use TanStack Query for catalog reads, job status polling, and history reads.

Use backoff and stop polling on terminal status.

---

## 191. Polling strategy

Poll report jobs at a bounded interval.

Stop polling when the status is COMPLETED, COMPLETED_WITH_ERRORS, FAILED, or CANCELLED.

Aggressive polling for thousands of users is avoided.

---

## 192. Job status caching

A job status response may be briefly cached on the client, but completion and download authorization remain server-controlled.

---

## 193. Report history pagination

`GET /api/v1/reports/history` uses the project's standard pagination contract.

Unbounded history is not returned to the browser.

---

## 194. History search filters

The history API is not extended with unapproved arbitrary fields.

Potential filters such as date, status, and reportType require API contract validation before exposure.

---

## 195. Common API envelope

All report endpoints use the common JSON response envelope established by the project.

A report-specific response envelope is not introduced.

---

## 196. HTTP status contract

Minimum expected statuses.

```text
200 successful read
202 async job accepted
400 invalid request
401 unauthenticated
403 unauthorized
404 unknown resource or job
409 conflict where applicable
429 rate limit where applicable
500 or 503 controlled server or dependency failures
```

---

## 197. Rate limiting

Report generation is rate-limited more aggressively than lightweight reads because it consumes significant resources.

Exact limits are evidence-driven.

---

## 198. Abuse protection

Reject requests that attempt extreme date windows, unbounded exports, repeated expensive generation, or cross-organization access.

---

## 199. Report quotas

Organization and report quotas are not frozen in the current schema.

If quotas are required, they are documented as configuration and business-rule decisions before persistence is added.

---

## 200. Concurrent job limits

Per-principal and per-organization concurrent job limits may be used operationally.

The enforcement mechanism reuses existing job infrastructure or approved configuration.

---

## 201. Duplicate request protection

Use the project-wide idempotency mechanism for report requests where enabled.

The same request does not create unlimited duplicate expensive jobs.

---

## 202. Report authorization during queueing

Authorization is checked at submission time.

For sensitive reports, the worker also verifies that the job remains valid and within scope at execution time.

---

## 203. Report authorization during download

Download requests independently enforce current authorization.

---

## 204. Organization scope in worker

Organization scope is never inferred from a client-supplied filter alone.

The trusted organization context is persisted or passed through the job contract.

---

## 205. Multi-tenant future compatibility

Even though the deployment is single-organization, report code passes organization context explicitly.

A singleton organization is not hard-coded in report queries.

---

## 206. Global admin boundary

Cross-organization reporting is outside the pilot requirement.

A future global admin has an explicit permission and data-scope contract.

---

## 207. Report data provider contracts

Recommended interfaces.

```java
StudentReportDataProvider
AssessmentReportDataProvider
ContentReportDataProvider
CommerceReportDataProvider
OperationsReportDataProvider
ExecutiveReportDataProvider
```

Each returns report-specific projections.

---

## 208. Renderer contracts

Recommended interfaces.

```java
ReportRenderer<T>
PdfReportRenderer<T>
CsvReportRenderer<T>
```

The renderer does not know business persistence details.

---

## 209. Report orchestration

The application service coordinates validation, authorization, execution mode selection, job submission, projection retrieval, rendering, storage, and response mapping.

---

## 210. Report execution mode

A report definition indicates SYNC_ALLOWED, ASYNC_REQUIRED, or ASYNC_PREFERRED.

The final execution decision may also consider actual cardinality.

---

## 211. Automatic async promotion

Even for a report type that can be synchronous in principle, a large requested range switches to asynchronous execution.

The user contract returns 202 and jobId.

---

## 212. Preflight estimation

Where practical, the report engine estimates result size before execution using bounded count or projection queries.

The estimate does not itself become an unbounded expensive query.

---

## 213. Report row limits

Every report type defines a safe maximum row threshold for synchronous execution.

The threshold is load-test driven.

---

## 214. Data export limits

Async exports also have safe maximums to protect the platform.

Requests beyond the supported range fail clearly rather than timing out after hours.

---

## 215. Report query timeout

Database statement timeouts are appropriate for report workload and do not compromise live exam workload.

---

## 216. Report query isolation

Use the least disruptive isolation level that preserves the report's documented semantics.

Repeatable-read or point-in-time consistency is not claimed unless deliberately configured and tested.

---

## 217. Lock avoidance

Reporting reads avoid taking locks on hot transactional paths.

No report query issues business-state UPDATE statements.

---

## 218. Read replica consideration

A read replica may be evaluated only from load evidence.

If introduced, it requires infrastructure documentation, lag semantics, failover behavior, and operational validation.

---

## 219. Warehouse consideration

Data warehouse adoption is a future capability.

BigQuery, Snowflake, Redshift, and ClickHouse are not added merely because an executive report is difficult to query.

Evidence justifies a future architecture decision.

---

## 220. Search engine boundary

Reporting does not justify Elasticsearch or OpenSearch.

PostgreSQL projections and queries are used unless a separately approved architecture decision changes the search topology.

---

## 221. Materialized view boundary

Materialized views are not part of the frozen reporting schema.

They are considered only when performance evidence demonstrates that approved aggregate tables and bounded queries cannot meet requirements.

---

## 222. Operational dashboard versus report artifact

A dashboard can use live metrics and bounded API reads.

A report artifact is generated output intended for download, share, and archive under authorization.

Every dashboard panel is not forced through PDF generation.

---

## 223. Executive dashboard

Executive dashboards may reuse OrganizationAnalytics, AssessmentAnalytics, Commerce aggregates, and operational metrics subject to freshness disclosure.

---

## 224. Operational dashboard

Operations dashboards prioritize system health, report backlog, communication backlog, failed jobs, audit activity, and assessment operational volume.

---

## 225. Report catalog UI categories

Use the same top-level categories.

```text
Student
Assessment
Content
Commerce
Operations
Executive
```

---

## 226. Report description standard

Every report definition describes purpose, intended audience, source, freshness, filters, and known limitations.

---

## 227. Report data dictionary

Maintain a code and documentation-level data dictionary with field, business name, technical source, calculation, and PII classification.

This is version controlled with report definitions.

---

## 228. Report sample artifacts

Each important report has a representative sample artifact in test assets.

Samples contain synthetic or anonymized data only.

---

## 229. Golden file testing

PDF and CSV rendering uses golden-file or structural snapshot testing where practical.

Validate header, columns, row values, page count where deterministic, and encoding.

---

## 230. PDF visual QA

At least one representative PDF per major report family undergoes visual validation.

Check clipping, overflow, page breaks, font rendering, language support, and headers and footers.

---

## 231. CSV structural QA

Validate column count, header ordering, escaping, UTF-8 encoding, newline handling, and formula injection mitigation.

---

## 232. API contract tests

Automated tests verify `POST /api/v1/reports`, `GET /api/v1/reports/jobs/{jobId}`, and `GET /api/v1/reports/history` including authorized and unauthorized scenarios.

---

## 233. Role matrix tests

Test report access for ADMIN, REVIEWER, STAFF, STUDENT, SUPPORT, and FINANCE using actual seeded permissions rather than role-name assumptions.

---

## 234. Object authorization tests

For each report family test same-organization allowed case, other-organization denied case, other-student denied case, and unpublished data denied case where the underlying contract supports those cases.

---

## 235. Filter validation tests

Test bad UUID, invalid date, from greater than to, unsupported filter, unknown reportType, unsupported format, and excessive range.

---

## 236. Async job tests

Test queued, running, completed, completed_with_errors, failed, retry, worker restart, and storage failure.

---

## 237. Idempotency tests

Test repeated submissions with same idempotency key, same request fingerprint, network retry, and client retry after timeout.

Verify no duplicate logical output.

---

## 238. Download security tests

Test valid authorized download, expired URL, wrong user, wrong organization, revoked permission, and missing artifact.

---

## 239. Data reconciliation tests

For critical reports, compare generated totals to source queries.

Automate representative reconciliation assertions.

---

## 240. Performance regression tests

Every high-volume report has a baseline.

Track query duration, worker duration, artifact generation duration, total end-to-end duration, database utilization, heap, and connections.

---

## 241. Concurrency regression tests

Run reporting alongside exam start, response autosave, submission, result generation, payment callback, and communication workers.

The reporting suite passes only if core exam behavior remains within agreed thresholds.

---

## 242. Failure injection - Database

Simulate database interruption during report queueing, report query, report render, and report history read.

Verify controlled failure and no false success.

---

## 243. Failure injection - Worker

Kill and restart the report worker during execution.

Verify job recovery behavior and artifact integrity.

---

## 244. Failure injection - Object storage

Simulate artifact-storage outage.

Expected: job fails or retries, no broken success state, no leaked path.

---

## 245. Failure injection - Redis

Verify reporting remains correct because Redis is not authoritative data storage.

---

## 246. Failure injection - Network

Simulate request timeout after the server accepted a job.

Client retry is handled through the idempotency strategy rather than creating uncontrolled duplicates.

---

## 247. Report incident runbook

Basic workflow.

```text
Identify report type
 |
 v
Inspect job status
 |
 v
Check report worker health
 |
 v
Check query latency
 |
 v
Check database and PgBouncer
 |
 v
Check object storage
 |
 v
Inspect sanitized logs
 |
 v
Retry only if transient
 |
 v
Reconcile output
```

---

## 248. Broken artifact runbook

```text
Mark artifact unavailable
 |
 v
Identify source job
 |
 v
Check renderer logs
 |
 v
Check storage
 |
 v
Regenerate using same authorized inputs
 |
 v
Validate artifact
 |
 v
Expose download only after successful validation
```

---

## 249. Slow report runbook

```text
Identify reportType
 |
 v
Check filters and range
 |
 v
Check query plan
 |
 v
Check database load
 |
 v
Check worker contention
 |
 v
Compare against baseline
 |
 v
Apply evidence-backed optimization
```

Indexes or replicas are not added immediately without analysis.

---

## 250. Report data mismatch runbook

```text
Freeze interpretation
 |
 v
Identify report definition and version
 |
 v
Recompute from source
 |
 v
Compare source, projection, and renderer
 |
 v
Identify semantic or data-quality drift
 |
 v
Correct owning domain or analytics issue
```

Reporting does not patch transactional truth.

---

## 251. Production deployment strategy

P13 deploys behind controlled feature exposure.

Sequence.

```text
Deploy backend
 |
 v
Deploy report catalog disabled or limited
 |
 v
Validate dependencies
 |
 v
Enable low-risk reports
 |
 v
Observe
 |
 v
Enable broader reports
 |
 v
Enable executive reports after metric validation
```

---

## 252. Low-risk first set

A reasonable first operational set.

```text
Assessment Summary
Assessment Attempts
Student Performance
Question Inventory
Basic Commerce Payments
Audit Activity
```

Actual enablement depends on validation and permissions.

---

## 253. High-risk reports

Higher validation requirements.

```text
Executive KPI Summary
Revenue Summary
Cross-domain summaries
Very large CSV and PDF exports
Reports combining transactional and derived freshness
```

---

## 254. Executive validation gate

No executive KPI is enabled until definition is approved, source is confirmed, population is documented, calculation is validated, freshness is disclosed, and reconciliation passes.

---

## 255. Commerce validation gate

Commerce reports require amount precision validation, refund semantics validation, payment status reconciliation, and AccessGrant consistency checks.

---

## 256. Assessment validation gate

Assessment reports require attempt count reconciliation, score consistency, published-result boundary validation, and snapshot correctness for attempt detail.

---

## 257. Content validation gate

Content reports require version consistency, taxonomy joins verified, asset relationship verified, and tag relationship verified.

---

## 258. Student validation gate

Student reports require ownership checks, PII minimization, result publication checks, and analytics freshness disclosure.

---

## 259. Operations validation gate

Operations reports require safe observability exposure, no secret leakage, clear metric semantics, and resource-scope enforcement.

---

## 260. Report documentation

Each released report has a concise internal contract containing report type, category, audience, permission, filters, source, calculation, freshness, formats, execution mode, limits, and known gaps.

---

## 261. Report catalog review

Before production release, review the complete registry for duplicate report types, ambiguous names, missing permissions, missing tests, missing source ownership, missing freshness definition, and missing performance evidence.

---

## 262. API documentation

OpenAPI describes request body, accepted formats, 202 response, job status response, error responses, and authorization requirements.

---

## 263. Example request

```http
POST /api/v1/reports
Content-Type: application/json
X-Request-Id: <uuid>
Idempotency-Key: <uuid>

{
  "reportType": "ASSESSMENT_ATTEMPTS",
  "filters": {
    "assessmentId": "<uuid>",
    "from": "2026-10-01",
    "to": "2026-10-31"
  },
  "format": "PDF"
}
```

---

## 264. Example 202 response

```json
{
  "success": true,
  "message": "Report generation started",
  "data": {
    "jobId": "<uuid>",
    "status": "QUEUED"
  }
}
```

---

## 265. Example job status

```json
{
  "success": true,
  "message": "Report job status retrieved",
  "data": {
    "jobId": "<uuid>",
    "status": "COMPLETED",
    "downloadUrl": "<protected-download>"
  }
}
```

---

## 266. Example failure response

```json
{
  "success": false,
  "message": "Report generation failed",
  "data": null,
  "error": {
    "code": "REPORT_GENERATION_FAILED"
  }
}
```

The exact error envelope remains aligned with the common API contract.

---

## 267. Sample backend flow

```text
POST /reports
   |
   v
ReportController
   |
   v
ReportApplicationService
   |
   v
Permission check
   |
   v
ReportRegistry lookup
   |
   v
Filter validation
   |
   v
Scope resolution
   |
   v
Execution-mode decision
   |
   +-- Synchronous -> projection -> renderer -> response
   |
   +-- Asynchronous -> job submission -> 202
```

---

## 268. Sample worker flow

```text
Job worker
   |
   v
Load trusted job context
   |
   v
Revalidate report definition
   |
   v
Resolve provider
   |
   v
Stream data
   |
   v
Render
   |
   v
Store artifact
   |
   v
Finalize job state
   |
   v
Emit metrics
```

---

## 269. No business mutation rule

A reporting transaction never writes to assessment, assessment_version, student_attempt, student_response, evaluation, result, purchase, payment_record, access_grant, notification, or announcement except for approved audit and job infrastructure mechanisms that are not business-domain mutation.

---

## 270. Report security review checklist

```text
[ ] Authentication
[ ] Permission
[ ] Organization scope
[ ] Object scope
[ ] PII review
[ ] Secret review
[ ] SQL safety
[ ] XSS and HTML rendering safety
[ ] CSV formula injection
[ ] Artifact access
[ ] URL expiry
[ ] Logging redaction
[ ] Rate limiting
```

---

## 271. Data quality review checklist

```text
[ ] Source identified
[ ] Calculation documented
[ ] Freshness identified
[ ] Row counts reconciled
[ ] Aggregate totals reconciled
[ ] Null handling checked
[ ] Date semantics checked
[ ] Timezone checked
[ ] Score and currency semantics checked
```

---

## 272. Performance review checklist

```text
[ ] Query plan reviewed
[ ] Indexes justified
[ ] Sample cardinality representative
[ ] p50 measured
[ ] p95 measured
[ ] p99 measured
[ ] Database load measured
[ ] Memory measured
[ ] Worker concurrency measured
[ ] Live-exam coexistence tested
```

---

## 273. Artifact review checklist

```text
[ ] File opens
[ ] Format valid
[ ] Encoding valid
[ ] Headers correct
[ ] Filters shown
[ ] Rows correct
[ ] Page breaks correct
[ ] PII reviewed
[ ] Branding safe
[ ] No server path exposed
```

---

## 274. Report history review checklist

```text
[ ] History source identified
[ ] Retention documented
[ ] Authorization checked
[ ] Pagination implemented
[ ] Download secured
[ ] Expired artifact behavior defined
```

---

## 275. Report API rate-limit review

Test single-user burst, organization burst, many small reports, few large reports, and retries after timeout.

Measure rejected versus accepted requests and worker backlog.

---

## 276. Report queue fairness

Ensure one organization or report type cannot monopolize shared worker capacity.

Mechanisms may include concurrency classes, per-organization limits, priority queues, and fair scheduling only where supported by the existing job infrastructure.

---

## 277. Report priority

Exam-critical asynchronous jobs retain priority over non-critical exports where infrastructure supports prioritization.

If no priority mechanism exists, isolate worker pools rather than inventing a new business table.

---

## 278. Report storage naming safety

Storage keys are generated by the server.

Do not use path traversal, absolute filesystem path, or raw user filename.

---

## 279. Storage metadata

Store only safe metadata required by the existing artifact mechanism.

Sensitive report contents are not persisted in application logs or metadata.

---

## 280. Report encryption

Use the existing production object and file storage security model.

If encryption at rest is required, it is handled by approved storage and deployment configuration, not by inventing report-specific secrets.

---

## 281. Backup consideration

Report artifacts are generated outputs and may have a different recovery priority than transactional database truth.

Document whether artifacts are recoverable and re-creatable rather than assuming they are primary business data.

---

## 282. Regeneration strategy

A report is normally re-generatable from authoritative source data when the artifact is missing and the source remains available.

This is preferable to treating every generated file as unique business truth.

---

## 283. Reconciliation after restore

After database restore or application rollback, verify report catalog version, job state behavior, artifact accessibility, and source data alignment.

A report claiming data that no longer exists in restored source state is not exposed.

---

## 284. Deployment compatibility

A new report renderer is backward compatible with existing APIs unless the contract intentionally changes.

---

## 285. Migration boundary

P13 requires no database migration by default.

If implementation discovers that the current system cannot satisfy a frozen API requirement without persistence, the explicit amendment gate is used rather than creating an ad hoc migration.

---

## 286. Flyway rule

Any formally approved reporting schema amendment uses Flyway and goes through the same migration review process as every other database change.

---

## 287. Observability dashboard

Create a Reporting dashboard using existing observability tooling.

Panels include report requests per second, async queue depth, success rate, failure rate, p95 generation time, artifact failures, and database load during report peaks.

---

## 288. SLO evidence

Each report family retains evidence of workload, infrastructure, release version, data size, query profile, render format, result, and limitations.

---

## 289. Regression after optimization

Any optimization that changes SQL, indexing, projection logic, aggregation formula, renderer, worker concurrency, or storage behavior requires the affected report test and performance suite to be rerun.

---

## 290. Report security regression

Security suite is rerun after changes to report filters, authorization, job ids, artifact access, storage adapters, or PII projection.

---

## 291. Report catalog release process

Recommended workflow.

```text
Draft definition
 |
 v
Code review
 |
 v
Unit tests
 |
 v
Integration tests
 |
 v
Security review
 |
 v
Performance review
 |
 v
Sample artifact review
 |
 v
Staged enablement
```

---

## 292. P13 implementation task breakdown

```text
P13-01  Reporting module skeleton
P13-02  Report registry
P13-03  Request and filter validation
P13-04  Authorization
P13-05  Synchronous execution
P13-06  Asynchronous execution
P13-07  Job status
P13-08  History
P13-09  Artifact storage
P13-10  PDF renderer
P13-11  CSV renderer
P13-12  Student reports
P13-13  Assessment reports
P13-14  Content reports
P13-15  Commerce reports
P13-16  Operations reports
P13-17  Executive reports
P13-18  Observability
P13-19  Security testing
P13-20  Performance testing
P13-21  Reconciliation
P13-22  Staged rollout
```

Execute one task at a time with checkpoints.

---

## 293. P13-01 to P13-04 acceptance

```text
[ ] Module skeleton
[ ] Report registry
[ ] Request and filter validation
[ ] Authorization with permission and organization scope
[ ] No new tables
[ ] No direct cross-module repository access
[ ] CI green
```

---

## 294. P13-05 to P13-09 acceptance

```text
[ ] Synchronous execution path
[ ] Asynchronous execution path via background_job
[ ] Job status endpoint
[ ] History endpoint backed by background_job
[ ] Artifact storage via existing file mechanism
[ ] Protected download
[ ] Idempotency
[ ] Duplicate artifact prevention
```

---

## 295. P13-10 to P13-11 acceptance

```text
[ ] PDF renderer
[ ] CSV renderer
[ ] CSV formula injection mitigation
[ ] HTML and PDF injection controls
[ ] Language support where required
[ ] Golden-file test evidence
```

---

## 296. P13-12 to P13-17 acceptance

```text
[ ] Student reports reconciled
[ ] Assessment reports reconciled
[ ] Content reports reconciled
[ ] Commerce reports reconciled
[ ] Operations reports validated
[ ] Executive reports require explicit KPI definitions
[ ] Freshness disclosed
[ ] Timezone semantics documented
```

---

## 297. P13-18 to P13-22 acceptance

```text
[ ] Observability metrics
[ ] Observability dashboard
[ ] Alerts configured
[ ] Security tests
[ ] Concurrency tests
[ ] Failure injection tests
[ ] Performance baseline
[ ] Reconciliation tests
[ ] Runbooks published
[ ] OpenAPI updated
[ ] Staged rollout complete
[ ] No unresolved critical security or data-integrity defect
```

---

## 298. Recommended implementation prompt

```text
You are implementing P13 Broader Reporting and Operational Reporting for
the Smart Competitive Examination Platform.

Read the full specification package before modifying the repository.

P13 owns:
Report catalog
Report execution
Report rendering
Report artifact delivery
Report job status
Report history
Report observability

NON-NEGOTIABLE:
1.  Reports are a read-oriented application capability, not a new
    business domain.
2.  Do not invent reporting tables for convenience.
3.  Preserve POST /api/v1/reports,
    GET /api/v1/reports/jobs/{jobId}, and
    GET /api/v1/reports/history.
4.  Reuse the existing background_job infrastructure for async
    reports.
5.  Do not add reporting schema without a formal amendment.
6.  Backend authorization is authoritative.
7.  Student reports are self-scoped.
8.  Staff, support, finance, and admin access is permission-driven.
9.  Organization scope is enforced server-side.
10. No arbitrary SQL.
11. No entity dumping from controllers.
12. Use explicit projection contracts.
13. Large exports are asynchronous.
14. Artifact downloads are protected.
15. Do not expose paths, credentials, tokens, or provider secrets.
16. Do not recalculate scores inside Reporting.
17. Do not change commerce, assessment, evaluation, or communication
    truth from Reporting.
18. Use Analytics aggregates when their grain and definition match the
    report.
19. Do not invent historical time-series semantics.
20. Executive and financial metrics require explicit definitions.
21. Use UTF-8-safe CSV and multilingual-safe PDF rendering.
22. Mitigate CSV formula injection and HTML and PDF content injection.
23. Report workloads must not starve live examination delivery.
24. Validate report totals against source truth.
25. Use production-like performance evidence.
26. Preserve X-Request-Id and common API conventions.
27. Use idempotency where report requests are configured for
    idempotency.
28. Maintain immutable published artifacts and immutable audit
    evidence.
29. Any material change after validation requires rerunning the
    affected security, performance, and regression suite.
30. Stop and report rather than silently resolving frozen-contract
    gaps.

Task order:
P13-01 through P13-22.

For each task:
- Inspect current repository first.
- Implement the smallest correct change.
- Run focused tests.
- Run PostgreSQL integration tests.
- Report changed files.
- Report API impact.
- Report schema impact.
- Report unresolved contract issues.
- Stop before the next high-risk task.

Before merge:
- Unit tests
- Integration tests
- Security tests
- Concurrency tests
- Failure injection
- Performance baseline
- Reconciliation
- Migration verification
- OpenAPI verification
- Audit verification
- CI green
```

---

## 299. Review questions

```text
Which report definition was used?
Is the source of truth documented?
Is the freshness disclosed?
Is the authorization enforced at generation and download?
Is the report history truthfully retained?
Can a large export destabilize live exams?
Can a report leak secrets or PII?
Is CSV formula injection mitigated?
Is PDF content injection mitigated?
Is the execution mode correctly chosen?
Is the report generation idempotent?
Is the artifact protected?
Is the frozen schema unchanged?
Is the frozen API unchanged?
```

---

## 300. Final engineering principle

> Compute reports from authoritative sources, protect artifacts, disclose freshness, and never allow reporting to become a hidden second database of truth.

The reporting flow.

```text
Authoritative transactional data
            |
            v
Controlled projection
            |
            v
Renderer
            |
            v
Protected artifact
            |
            v
Authorized download
```

---

## 301. P13 handoff

P13 completes the broader reporting capability by turning the report API and Operations Platform intent into an implementation discipline centered on controlled projections, asynchronous execution, protected artifacts, strong authorization, reconciliation, and production-grade operations.

The platform remains intentionally narrower than a general BI system.

---

## 302. Next expansion path

The next expansion path after P13 can build on reliable reporting data for future Phase 2 capabilities.

```text
Advanced reporting
Recommendation systems
Natural language search
Data warehouse integration
AI admin assistant
```

These are not part of P13.

---

## 303. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P13 — Broader Reporting and Operational Reporting.

**Next document:** V1.5 Implementation Status.