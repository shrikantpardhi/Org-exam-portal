# Smart Competitive Examination Platform
## P12 — Production Hardening & Production Readiness Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P12 — Production Hardening & Production Readiness
**Depends on:** P0 through P11 Implementation Specifications, ExamEngine and CDUI Contract
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Connection Pooling:** PgBouncer
**Deployment:** Docker, Nginx, controlled CI/CD
**Observability:** Prometheus, Grafana, Loki
**API:** REST + JSON, `/api/v1`
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Operations, Architects

---

## 1. Purpose

This document defines the Production Hardening and Production Readiness phase.

Hardening begins after functional development and the post-pilot modules are available.

The phase temporarily freezes new features and focuses only on correctness, security, performance, reliability, UX, and operational readiness.

This document turns those requirements into implementation, validation, release, and evidence gates.

---

## 2. Source-of-truth hierarchy

Read these documents before P12 execution:

- Master PRD
- Domain Model
- Technical Design Document
- Database Standards and Schema
- API Contract
- Architecture Freeze
- Development Roadmap
- ExamEngine and CDUI Contract
- P0 Foundation Implementation Specification
- P1 Identity and Security Implementation Specification
- P2 Academic Structure Implementation Specification
- P3 Content and Question Bank Implementation Specification
- P4 Blueprint and Assessment Management Implementation Specification
- P5 Assessment Delivery and Runtime Examination Engine Implementation Specification
- P6 Evaluation and Results Implementation Specification
- P7 Scale Validation and Live Pilot Implementation Specification
- P8 Full Analytics and Insights Implementation Specification
- P9 Commerce and Access Management Implementation Specification
- P10 Communication and Notification Implementation Specification
- P11 Full Administration Implementation Specification
- This document

The earlier documents already freeze the runtime architecture, the delivery model, the evaluation contract, the commerce and communication contracts, the administration contract, and the performance acceptance targets. This document does not modify those decisions.

---

## 3. Phase position

Current sequence.

```text
Pilot evidence
  |
  v
Fix and stabilize
  |
  v
Full analytics
  |
  v
Commerce
  |
  v
Communication
  |
  v
Full administration
  |
  v
Hardening
  |
  v
Production
```

Hardening is the last stabilization phase before production release.

---

## 4. Hardening principle

After functional development.

```text
Freeze new features temporarily
Fix defects
Validate existing behavior
Measure actual performance
Validate security
Test failure modes
Validate operational procedures
Retain release evidence
```

Hardening is not another feature-development sprint.

---

## 5. No new feature rule

During the primary hardening window, new features are prohibited unless a production-blocking defect or explicit release decision requires them.

Any material feature addition restarts the relevant regression, performance, and security validation.

---

## 6. Scope

In scope.

```text
Correctness
Security
Performance
Reliability
UX hardening
Database performance
API regression
Assessment integrity
Commerce integrity
Communication resilience
Backup and restore
Deployment
Rollback
Monitoring
Logging
Alerts
Incident readiness
Support procedures
Release governance
Staging
Smoke tests
Production gates
```

Out of scope.

```text
New business domains
New AI features
Major architecture replacement
Speculative microservices
New search platform without evidence
New schema merely for monitoring
Uncontrolled infrastructure changes
```

---

## 7. Frozen architecture

The production architecture remains the modular monolith.

```text
Next.js
Spring Boot
PostgreSQL
Redis
PgBouncer
Background workers
Nginx
Prometheus
Grafana
Loki
Object and file storage
```

Do not replace this stack during hardening unless a critical, evidence-backed architecture decision is formally approved.

---

## 8. Release quality objective

The goal is not to prove that every request is always fast.

The goal is to produce measured evidence that the release candidate behaves correctly and reliably under the workload and failure modes expected in production.

---

## 9. Frozen performance gates

The platform baseline requires evidence for.

```text
5,000 concurrent users
99.9% availability target
Under 100 ms p95 for applicable API requests
Greater than 99% successful submissions
Less than 1% exam failure rate
```

These are acceptance gates, not guarantees.

---

## 10. Evidence requirement

Every release gate retains evidence identifying.

```text
Software version
Frontend version
Database migration version
Infrastructure and configuration
Workload
Data volume
Test duration
Test profile
Result
Known limitations
Operator and date
```

A gate is not marked passed based on verbal confirmation alone.

---

## 11. Release candidate identity

A hardening release candidate is uniquely identifiable by.

```text
Application version
Frontend version
Database migration version
Container image digest
Configuration revision
```

These identifiers are retained with the release evidence.

---

## 12. Code freeze

Before the primary final validation.

```text
Freeze application code
Freeze migration set
Freeze test data model
Freeze load profile
Freeze core runtime policies
```

After the freeze, only critical release-blocking changes enter the candidate.

---

## 13. Change after freeze

If code, schema, configuration, runtime policy, worker concurrency, or infrastructure changes after the primary validation.

```text
Assess impact
Rerun the relevant tests
Update evidence
Repeat the release gate if required
```

Stale performance and security evidence is not reused after a material change.

---

## 14. Security hardening scope

Validate.

```text
Authentication
Authorization
JWT expiration
Session invalidation
Password hashing
Password reset
Input validation
SQL injection resistance
XSS protection
CSRF strategy
CORS
File upload security
Access control
Audit logging
Sensitive-data masking
Error-message safety
```

---

## 15. Authentication hardening

Verify invalid credentials fail safely, expired credentials fail, malformed JWTs fail, expired JWTs fail, refresh behavior follows the P1 contract, logout behavior is correct, session lifecycle is correct, password reset does not expose tokens, and system actor login is rejected.

Authentication secrets are never logged or returned.

---

## 16. Authorization hardening

Every protected operation enforces authentication, permission, object scope, and business state.

Frontend route protection is not sufficient.

---

## 17. IDOR hardening

Test ID substitution across users, roles, permissions, questions, assessments, schedules, attempts, responses, results, purchases, payments, access grants, notifications, announcements, templates, and audit logs.

Expected behavior follows the platform's 403 or 404 object-concealment convention.

---

## 18. Privilege escalation hardening

Test student to privileged operation, support to admin, finance to admin, staff to admin, self-role escalation, role-permission escalation, hidden endpoint access, and bulk-operation privilege abuse.

A role label is not a substitute for explicit permission enforcement.

---

## 19. Mass assignment hardening

No controller binds arbitrary request JSON directly to JPA entities.

Use explicit DTOs and server-owned fields.

Protect id, status, created_by, created_on, updated_by, updated_on, ownership identifiers, publication flags, payment state, and result state.

---

## 20. Input validation

Validate server-side.

```text
Type
Length
Range
Enum
UUID syntax
Pagination
Sorting
Filters
Nested configuration
Date ranges
State transitions
```

Frontend validation is supplementary only.

---

## 21. SQL injection hardening

All queries use parameter binding or approved ORM query mechanisms.

Never interpolate search strings, filters, sort fields, table names, operation names, or SQL fragments.

Global search uses explicit resource handlers.

---

## 22. XSS hardening

Validate stored and rendered content in announcements, notification text, question content, explanations, hints, email templates, admin configuration views, lookup names, and audit remarks.

Follow the established content-sanitization rules for question content.

---

## 23. File upload hardening

Validate size, media and file type, filename, encoding, structure, and content where applicable.

Prevent path traversal, arbitrary filesystem writes, executable upload, unbounded upload, and unsafe archive extraction.

Use managed storage services.

---

## 24. CORS hardening

Production CORS is explicit.

Do not use allow-all origins, allow-all credentials, or wildcard production configuration.

Align with the P1 security foundation and actual frontend origin.

---

## 25. CSRF strategy

Confirm that the authentication and session architecture is consistent with the P1 CSRF strategy.

Do not disable CSRF protections simply because the client is a web application using JSON APIs.

---

## 26. Security headers

Validate production security headers established by P0, P1, and Nginx, including the project's approved content security policy, frame protection, content-type protection, referrer policy, and transport security.

A contradictory header policy is not introduced in hardening.

---

## 27. HTTPS

Production requires HTTPS.

Validate valid certificate, correct hostname, redirect from HTTP where intended, no mixed content, and secure transport between browser and platform entrypoint.

---

## 28. Secret management

Secrets stay outside source control.

Examples.

```text
Database credentials
JWT signing secret
Redis credentials
Email provider secret
Payment provider secret
Object-storage credentials
```

Use deployment secret and configuration mechanisms.

---

## 29. Sensitive log review

Perform a release-wide review of logs to ensure they do not contain passwords, JWTs, reset tokens, payment secrets, provider credentials, card data, sensitive personal data unnecessarily, or private answer-key content in student delivery logs.

---

## 30. Error message hardening

API and UI errors are useful but safe.

Do not expose stack traces, SQL statements, database credentials, provider secrets, filesystem paths, or internal class names.

Use request ids for support correlation.

---

## 31. Audit verification

Important operations are audited.

```text
Publication
Role and permission changes
Configuration changes
Access grants and revokes
Refunds
Bulk operations
Imports
Other critical business actions
```

AuditLog remains immutable.

---

## 32. Audit immutability

Verify INSERT is allowed, UPDATE is prohibited, and DELETE is prohibited.

No generic audit mutation APIs.

No admin SQL console.

---

## 33. Audit completeness

A critical workflow does not silently succeed without required audit evidence.

The transactional relationship between business mutation and audit persistence follows the approved P11 audit strategy.

---

## 34. Security regression gate

Security hardening is complete only when authentication tests pass, authorization tests pass, IDOR tests pass, privilege escalation tests pass, injection tests pass, XSS tests pass, upload tests pass, secret scan passes, audit integrity passes, and no critical unresolved security finding remains.

---

## 35. Performance hardening scope

The roadmap requires testing login, dashboard, question search, assessment loading, question navigation, response save, submission, result generation, analytics, and report generation.

Special focus.

```text
Concurrent assessment start
Concurrent response save
Concurrent submission
Concurrent result generation
```

---

## 36. Performance measurement rules

Capture.

```text
p50
p95
p99
Throughput
Error rate
Database latency
Database CPU
Database connections
PgBouncer waiting clients
JVM memory and CPU
Redis usage
Worker utilization
```

Aggregate averages alone are insufficient.

---

## 37. Login performance

Measure normal login, failed login, concurrent login burst, refresh-token workload, and logout.

Watch authentication query latency, password hashing CPU cost, database connection utilization, and rate limiter behavior.

---

## 38. Dashboard performance

Admin and student dashboards do not trigger uncontrolled sequential API fan-out.

Measure number of requests, critical path latency, slow dependencies, cache behavior, and browser rendering impact.

---

## 39. Question search performance

Measure question search against realistic content volume.

Validate filters, pagination, sorting, search terms, and complex combinations.

Unbounded scans are avoided.

---

## 40. Assessment loading performance

Measure assessment detail, version loading, preview, student runtime question loading, and schedule checks.

Published assessment definitions are read consistently.

---

## 41. Assessment start performance

The architecture requires Start Exam to avoid synchronous snapshot creation.

Hardening verifies snapshot is READY before start, start does not create large AttemptQuestion and AttemptQuestionOption batches, start transaction remains lightweight, and the concurrent-user warm start remains within the measured target.

---

## 42. Snapshot performance

Snapshot preparation is asynchronous.

Measure eligible attempt rate, preparation throughput, READY percentage, FAILED percentage, preparation duration, lead-time safety margin, worker utilization, and database insert throughput.

---

## 43. Autosave performance

Measure realistic concurrent response-save traffic.

Capture requests per second, p50, p95, and p99, database CPU, lock waits, PgBouncer queueing, retry rate, stale-write precondition handling, and success rate.

---

## 44. Submission performance

Submission is a critical correctness path.

Measure concurrent submissions, duplicate submissions, transaction latency, successful submission percentage, failure percentage, and downstream evaluation queueing.

---

## 45. Result generation performance

Measure evaluation throughput, concurrent result generation, queue depth, worker utilization, result persistence latency, rank and percentile recomputation latency, and scorecard generation latency where applicable.

---

## 46. Analytics performance

P8 analytics is derived and recalculable.

Measure incremental refresh, full rebuild, dashboard read latency, heavy topic and question queries, and database load.

Transactional delivery capacity is not sacrificed for analytics jobs.

---

## 47. Report performance

Large reports are asynchronous.

Measure queueing, generation duration, database impact, file-generation duration, download access, and failure and retry behavior.

---

## 48. Database performance review

Review foreign-key indexes, search indexes, composite indexes, query plans, slow queries, pagination, large-table scans, analytics queries, lock contention, WAL and checkpoint behavior, and autovacuum.

Indexes are not added blindly.

---

## 49. Index justification

Every major index added during hardening identifies query or use case, expected benefit, size and storage impact, write overhead, and observed query plan.

An index is not created because a column exists.

---

## 50. PostgreSQL capacity

Measure CPU, memory, I/O, connections, transaction rate, lock waits, WAL volume, checkpoint behavior, autovacuum, slow queries, and storage growth.

Capacity decisions come from load evidence.

---

## 51. PgBouncer validation

PgBouncer is part of the production request path.

Validate application compatibility, transaction boundaries, pool sizing, pool exhaustion, connection reuse, restart behavior, monitoring, and failure behavior.

---

## 52. Redis validation

Redis is cache and coordination infrastructure.

Test normal operation, cache miss, restart, unavailable, recovery, and invalidation.

Core business correctness survives Redis failure when PostgreSQL fallback exists.

---

## 53. Worker capacity

Validate bounded worker concurrency for snapshot preparation, evaluation, analytics, reports, email and notifications, cleanup, imports, and bulk operations.

Admin workloads do not starve exam-critical workers.

---

## 54. Worker backlog

Monitor queue depth, processing rate, failure rate, retry backlog, oldest job age, and worker availability.

Alert when backlog growth threatens operational windows.

---

## 55. Mixed workload testing

Run representative admin, communication, commerce, and analytics background workloads together with exam traffic.

Measure impact on start exam, autosave, submission, evaluation, PostgreSQL, PgBouncer, Redis, and workers.

---

## 56. Concurrent-user test

Primary load validation.

```text
5,000 concurrent users
Realistic student workload
Production-like infrastructure
Frozen release candidate
Realistic data volume
```

Retain complete evidence.

---

## 57. Scaling curve

Run staged tests.

```text
100
500
1,000
2,500
5,000
```

Use the curve to detect nonlinear latency, connection saturation, lock contention, worker bottlenecks, and infrastructure limits.

---

## 58. Stress test beyond target

A stress test may exceed the concurrent-user acceptance population to identify failure shape.

The objective is not a production requirement but understanding saturation point, graceful degradation, recovery, and bottleneck.

---

## 59. Availability target

The platform baseline is a 99.9% availability target.

Hardening validates architecture and recovery against that target. It does not claim that any finite test proves annual availability.

---

## 60. Availability evidence

Retain uptime monitoring, deployment downtime, dependency outage tests, restart tests, recovery times, and health-check behavior.

---

## 61. Failure injection strategy

Inject failures in a production-like environment.

```text
PostgreSQL restart
Redis restart
PgBouncer restart
Worker restart
Provider outage
Network interruption
Application restart
Invalid configuration
Storage failure where safe
```

Fault injection is not performed against live production unless explicitly approved.

---

## 62. PostgreSQL failure injection

Verify requests fail safely, no false success, business transactions do not partially acknowledge, connection recovery works, and application health changes accurately.

---

## 63. Redis failure injection

Verify cached reads fall back, authorization correctness remains intact, application reports degraded infrastructure state, and cache rebuild works after recovery.

---

## 64. PgBouncer failure injection

Verify connection failures surface safely, application reconnects, no transactional corruption, and recovery after PgBouncer returns.

---

## 65. Worker failure injection

Restart and kill workers during snapshot preparation, evaluation, analytics, report generation, and notification delivery.

Verify retry and recovery behavior of the common `background_job` framework.

---

## 66. Network failure injection

Simulate client disconnect, transient API network loss, delayed response, duplicate retry, and provider timeout.

Assessment delivery preserves the P5 recovery and idempotency guarantees.

---

## 67. Application restart during exam

Verify in-progress attempt remains authoritative, responses remain durable, session recovery works, timer remains server-authoritative, and no snapshot reconstruction occurs.

---

## 68. Application restart during evaluation

Verify evaluation jobs recover according to the common `background_job` framework, deterministic evaluation remains intact, and no duplicate business result is created.

---

## 69. Payment failure injection

From P9.

```text
Provider timeout
Callback delay
Duplicate callback
Invalid callback
Amount mismatch
Refund failure
```

Verify commerce truth remains authoritative.

---

## 70. Communication failure injection

From P10.

```text
Email provider timeout
Provider 5xx
Rate limit
Missing template
Worker restart
```

Verify business transactions are not rolled back because communication failed.

---

## 71. Backup strategy

Production has operational backups.

Validate backup creation, retention, storage, access control, encryption where applicable, monitoring, and restore procedure.

---

## 72. Restore testing

A backup is not considered operational until restoration has been demonstrated.

Restore to an isolated environment and verify schema, data, constraints, indexes, and critical business flows.

---

## 73. Point-in-time recovery

The source requires backup and restore readiness but does not define a specific PostgreSQL point-in-time recovery architecture.

PITR capability is not claimed unless it is actually configured and tested.

---

## 74. Backup evidence

Retain backup timestamp, version and configuration, storage location, restore test result, duration, and known limitations.

---

## 75. Restore objectives

The source does not freeze numerical RPO and RTO values.

Define them operationally before production.

Do not invent contractual recovery times merely to complete a checklist.

---

## 76. Database migration hardening

All schema changes use Flyway.

Before production.

```text
Run complete migration chain
Test from representative baseline
Validate constraints
Validate indexes
Verify backward compatibility
```

---

## 77. Expand and contract strategy

For incompatible changes use expand, deploy compatible application, migrate and backfill, switch usage, and contract.

Do not rely on reversing migrations as the default rollback mechanism.

---

## 78. Migration rollback principle

Application rollback generally does not require destructive database rollback.

Prefer forward-compatible migrations.

---

## 79. Migration failure test

Test migration from current staging baseline, failure recovery, partially applied migration handling according to Flyway semantics, and application startup after migration.

---

## 80. Data integrity regression

Validate foreign keys, unique constraints, status values, published immutability, attempt snapshot integrity, response uniqueness, purchase and payment relationships, AccessGrant validity, analytics recalculability, and audit immutability.

---

## 81. Assessment integrity gate

Assessment hardening proves exact published AssessmentVersion, exact QuestionVersions, deterministic AttemptQuestion snapshot, deterministic option order, server-authoritative timer, response persistence, submission correctness, and exact AnswerKey usage.

---

## 82. Snapshot correctness gate

Verify Start Exam requires snapshot READY, start performs no large snapshot insert, recovery reuses existing snapshot, and snapshot remains immutable during attempt.

---

## 83. Response correctness gate

Verify response save, update, retry, stale-write policy, duplicate save, question ownership, response retrieval, and recovery.

---

## 84. Submission correctness gate

Verify one effective submission, duplicate submission safe, timeout behavior, network retry, state transition, and evaluation queueing.

---

## 85. Evaluation correctness gate

Verify deterministic scoring, negative marking, question-specific marks, bonus and dropped outcomes, override marks, BigDecimal arithmetic, answer-key selection, publication, and re-evaluation.

---

## 86. Result correctness gate

Verify result derived from evaluation, published result immutability, scorecard consistency, visibility policy, rank and percentile recomputation, and reconciliation.

---

## 87. Commerce integrity gate

Verify server-calculated amount, successful callback idempotency, duplicate callback protection, payment and reference validation, AccessGrant authorization, refund semantics, and no student access from failed payment.

---

## 88. Communication integrity gate

Verify business event does not mutate originating domain, notification ownership, email provider abstraction, bounded retry, and no false business failure on email failure.

---

## 89. Analytics integrity gate

Verify derived metrics remain read-only, rebuild produces consistent values, source transaction counts reconcile, and analytics refresh cannot corrupt transactional data.

---

## 90. Administration integrity gate

Verify admin uses owning domain services, no direct cross-domain repository access, settings, rules, and lookup constraints hold, AuditLog remains immutable, and privileged operations are permission-controlled.

---

## 91. API regression strategy

Every major API is validated for success, validation failure, authentication failure, authorization failure, not found, conflict, invalid state, duplicate request, pagination, filtering, and sorting.

---

## 92. HTTP contract regression

Expected statuses remain.

```text
200
201
202
204
400
401
403
404
409
412
422
429
500
503
```

Status codes are not repurposed without a contract change.

---

## 93. Response envelope regression

Success remains.

```json
{
  "success": true,
  "message": "...",
  "data": {}
}
```

Error remains.

```json
{
  "success": false,
  "message": "...",
  "errors": []
}
```

---

## 94. Request id regression

Every API request continues to support `X-Request-Id`.

Use it to correlate API, logs, and operational investigations where the supporting infrastructure provides it.

---

## 95. Idempotency regression

Re-test idempotency for login and refresh where relevant, response save, submission, evaluation, purchase, payment callback, refund, access grant, notification delivery, imports, and bulk jobs.

---

## 96. Rate-limit regression

Validate that rate limits protect authentication, payment callbacks where applicable, expensive search, bulk and import operations, and coupon validation without breaking legitimate exam delivery traffic.

---

## 97. Frontend UX hardening

Validate loading, empty, error, recovery, responsive behavior, permission-aware actions, confirmation for destructive operations, and clear status messaging.

---

## 98. Student UX hardening

Critical student paths.

```text
Login
Assessment discovery
Instructions
Start
Question navigation
Autosave feedback
Recovery
Submission
Result
Analytics
```

---

## 99. Exam runtime UX

Validate timer, section navigation, question palette, mark for review, clear response, save state, network warning, recovery, and submission confirmation.

The frontend timer is presentation. Server time is authoritative.

---

## 100. Error recovery UX

When an API fails, do not lose local UI state unnecessarily, show a safe error, retry where appropriate, avoid duplicate mutation, and preserve session and recovery.

---

## 101. Admin UX hardening

Validate dashboard, tables, filters, pagination, permission-gated actions, settings, rules, lookups, audit, bulk and import, support, and health.

---

## 102. Responsive UX

Admin is desktop-oriented. Important views remain usable on smaller screens.

Student-facing examination UI is tested against the supported browser and device baseline.

---

## 103. Accessibility hardening

Validate keyboard navigation, accessible labels, focus, error announcements, table semantics, dialogs, read and unread semantics, and form validation messages.

---

## 104. Browser compatibility

Run regression across the platform's supported browser baseline.

A browser-specific workaround that breaks another supported client is not added without documenting the tradeoff.

---

## 105. Operational readiness scope

Production readiness includes monitoring, logs, alerts, backup, restore, rollback, health checks, on-call ownership, support procedure, incident procedure, and release procedure.

---

## 106. Monitoring stack

Production monitoring uses Prometheus, Grafana, Loki, application metrics, health endpoints, and infrastructure telemetry.

A separate monitoring database is not created merely for admin.

---

## 107. Application metrics

Monitor request count, latency, error rate, JVM CPU and memory, thread and connection saturation, active exam sessions, response-save failures, submissions, and session recovery.

---

## 108. Database metrics

Monitor CPU, connections, query latency, locks, storage, I/O, WAL and checkpoints, autovacuum, and slow queries.

---

## 109. PgBouncer metrics

Monitor active clients, waiting clients, pool utilization, saturation, connection errors, and restart and recovery.

---

## 110. Redis metrics

Monitor availability, memory, hit and miss, latency, evictions if configured, and restart and recovery.

---

## 111. Worker metrics

Monitor queue depth, active jobs, duration, failures, retries, oldest job age, and worker availability.

---

## 112. Assessment metrics

Minimum operational metrics.

```text
Assessment start success
Snapshot readiness
Snapshot preparation duration
Response-save success
Response-save latency
Submission success
Submission latency
Exam failure rate
Session recovery success
```

---

## 113. Alerting baseline

Alerts exist for critical API error spikes, high p95 latency, submission failure spike, exam failure rate, snapshot backlog, worker outage, database saturation, PgBouncer saturation, Redis outage, backup failure, and restore test failure.

---

## 114. Alert quality

Alerts identify condition, environment, severity, affected subsystem, and basic diagnostic reference.

Noisy alerts that fire on every transient event are avoided.

---

## 115. On-call ownership

Before production, assign operational ownership for application, database, infrastructure, exam delivery, evaluation, commerce, and communication.

The exact team roster is outside the source documents. Ownership is nevertheless explicit operationally.

---

## 116. Runbook requirement

Critical incidents require documented runbooks.

At minimum.

```text
Exam start issue
Autosave issue
Submission issue
Database outage
Redis outage
Worker backlog
Payment issue
Email issue
Deployment rollback
Backup restore
```

---

## 117. Incident procedure

The production process defines detect, assess, contain, communicate, recover, reconcile, close, and post-incident review.

---

## 118. Incident classification

Use operational severity based on student impact, examination integrity, financial impact, data integrity, security impact, and system availability.

Unsupported database incident entities are not created merely to classify incidents.

---

## 119. Exam-day incident priority

Exam integrity takes precedence over convenience.

Critical conditions.

```text
Start failure
Snapshot incomplete
Widespread response-save failure
Submission failure
Timer correctness failure
Incorrect scoring
```

---

## 120. Exam-day support workflow

On exam-day.

```text
Monitor
  |
  v
Detect
  |
  v
Identify affected population
  |
  v
Protect data and integrity
  |
  v
Apply approved recovery
  |
  v
Reconcile
  |
  v
Record incident evidence
```

Direct database edits are not improvised.

---

## 121. Snapshot backlog runbook

If estimated snapshot completion exceeds the preparation window, inspect eligible population, inspect worker throughput, inspect database capacity, inspect failures, scale approved workers if safe, re-run failed preparation, and escalate before the exam window.

---

## 122. Submission failure runbook

If submissions fail, inspect API and database latency, inspect connection pools, inspect duplicate requests, inspect attempt state, verify no data loss, protect retry behavior, and monitor successful recovery.

---

## 123. Recovery runbook

For session recovery.

```text
Authenticate
Identify active session
Retrieve authoritative attempt state
Retrieve authoritative snapshot
Retrieve responses
Return normalized delivery configuration
Never reconstruct from mutable current assessment content
```

---

## 124. Evaluation incident runbook

If evaluation is delayed, inspect queue, inspect worker health, inspect failed jobs, inspect database, verify deterministic evaluation, retry approved jobs, and reconcile counts.

---

## 125. Result incident runbook

If results appear inconsistent, identify attempt, identify AnswerKey, inspect Evaluation, inspect Result publication, use P6 re-evaluation and reconciliation, and do not edit score directly.

---

## 126. Payment incident runbook

For payment issues, inspect Purchase, inspect PaymentRecord, inspect provider reference, inspect callback state, reconcile, and do not manually mark success without verified evidence.

---

## 127. Communication incident runbook

For email and notification failure, inspect job backlog, inspect provider health, inspect template, inspect retry, protect business state, and recover communication independently.

---

## 128. Deployment runbook

Release sequence.

```text
Backup
  |
  v
Database migration if required
  |
  v
Application deployment
  |
  v
Health checks
  |
  v
Smoke tests
  |
  v
Controlled traffic and release
  |
  v
Monitor
```

---

## 129. Rollback runbook

If release fails, confirm rollback condition, stop further rollout, deploy previous stable application image, preserve database compatibility, rerun smoke tests, observe critical paths, and record incident.

---

## 130. Rollback database rule

Flyway migrations are not blindly reversed.

Prefer forward-compatible schema and application rollback compatibility.

---

## 131. Deployment safety

Production deployment is made from a controlled release commit or tag, immutable artifact, known migration set, reviewed configuration, and approved release candidate.

---

## 132. CI pipeline

Recommended.

```text
Commit
  |
  v
Checkout
  |
  v
Dependencies
  |
  v
Compile
  |
  v
Unit tests
  |
  v
Static analysis
  |
  v
Frontend build
  |
  v
Backend build
  |
  v
Integration tests
  |
  v
Docker build
  |
  v
Security checks
  |
  v
Artifact
```

---

## 133. CI quality gates

Fail the build on compile failure, test failure, migration failure, frontend build failure, critical static-analysis failure, critical security finding, and API contract failure.

---

## 134. Branch protection

main requires successful CI, review approval, passing tests, and clean merge state.

Production deployments use controlled release commits or tags.

---

## 135. Environment strategy

Maintain local, development, test, staging and load, and production.

No load test points at live production data.

No dry run shares uncontrolled mutable state with production.

---

## 136. Local environment

Purpose: development and debugging.

Typical: Docker Compose, PostgreSQL, Redis, PgBouncer, local file storage, mock and fake external providers.

---

## 137. Development environment

Purpose: feature integration, early QA, developer collaboration.

Not treated as production evidence.

---

## 138. Test environment

Purpose: automated regression, integration, end-to-end, and security tests.

---

## 139. Staging and load environment

Closely resembles production.

```text
Next.js
Spring Boot
Nginx
PgBouncer
PostgreSQL
Redis
Workers
Prometheus
Grafana
Loki
Object and file storage equivalent
```

---

## 140. Production environment

Provides HTTPS, backups, monitoring, logging, alerts, controlled deployment, rollback capability, migration strategy, and secured infrastructure.

---

## 141. Configuration review

Before production validate JWT, CORS, HTTPS, database pool, PgBouncer, Redis, worker concurrency, timeouts, retries, logs, metrics, file storage, DNS and TLS, and secrets.

---

## 142. Configuration drift

Production configuration is version-controlled or otherwise traceable.

Unexpected drift is investigated before release.

---

## 143. Feature flags during hardening

Existing feature flags may be used to contain risky capabilities, roll out safely, and disable non-critical subsystems.

A feature flag is not a substitute for fixing a correctness bug.

---

## 144. Disable strategy

If a non-core subsystem is failing, disable the affected feature if possible, preserve core exam delivery, preserve financial correctness, and preserve security controls.

---

## 145. Release candidate smoke test

After staging deployment verify health, login, student assessment discovery, start, response save, recovery, submission, evaluation, result, admin login, commerce path, and communication path.

---

## 146. Smoke test principle

Smoke tests prove basic operational viability.

They do not replace full regression, security, load, restore, and failure-injection testing.

---

## 147. Critical release scenario - Free practice

Student login, open free assessment, start, answer, submit, evaluate, result, analytics.

---

## 148. Critical release scenario - Paid assessment

Student login, select package, purchase, payment, AccessGrant, assessment, attempt, submit, result.

---

## 149. Critical release scenario - Staff assessment creation

Staff login, create question, review, approve, publish, create blueprint, create assessment, publish, schedule.

---

## 150. Critical release scenario - Assessment recovery

Student start, answer, browser failure, login and recovery, continue, submit.

---

## 151. Critical release scenario - Duplicate payment callback

Payment success, callback one, access granted, callback two, no duplicate access.

---

## 152. Critical release scenario - Admin governance

Admin login, modify business rule, permission check, validation, save, audit, consumer observes intended configuration.

---

## 153. Critical release scenario - Bulk and import

Admin launches approved bulk or import, 202, job, process, partial failure handling, audit, safe retry.

---

## 154. Critical release scenario - Failure recovery

Simulate Redis outage and degraded state, worker restart and job recovery, database restart and application recovery.

Verify no business data corruption.

---

## 155. Performance baseline report

The hardening report contains workload, infrastructure, software release, data volume, duration, test profile, p50, p95, p99, throughput, errors, database metrics, PgBouncer metrics, Redis metrics, worker metrics, and known limitations.

---

## 156. Concurrent-user acceptance

The concurrent-user scenario is accepted only when evidence shows the workload actually reached target, a production-like stack was used, metrics were retained, failure thresholds were measured, and no critical integrity issue occurred.

---

## 157. Submission acceptance

Target: greater than 99% successful submissions.

Report total submissions, successes, failures, duplicate and retry behavior, affected conditions, and evidence.

---

## 158. Exam failure acceptance

Target: less than 1% exam failure rate.

Define exactly how the rate was measured for the tested population.

Do not silently change the denominator after the test.

---

## 159. API latency acceptance

Target: under 100 ms p95 for applicable API requests.

Report endpoint population separately.

Do not average provider or network latency into local API metrics without distinguishing it.

---

## 160. Availability acceptance

Target: 99.9% availability.

Use monitoring evidence and tested recovery characteristics.

A load test alone cannot prove annual availability.

---

## 161. Data reconciliation

Before release compare created attempts, submissions, evaluations, results, payment states, grants, and notification outcomes where measurable.

No unexplained count mismatch for critical transactional flows.

---

## 162. Reconciliation principle

For critical systems, source transaction to derived operation to count and state reconciliation.

Derived and read models never silently become authoritative.

---

## 163. Assessment reconciliation

Reconcile scheduled population, prepared snapshots, started attempts, submitted attempts, evaluated attempts, and published results.

---

## 164. Evaluation reconciliation

Reconcile submitted attempts, evaluation count, result count, publication count, failures, and retries.

---

## 165. Commerce reconciliation

Reconcile purchases, PaymentRecords, successful payments, AccessGrants, refunds, and mismatches.

Use P9 rules for repair.

---

## 166. Analytics reconciliation

Reconcile key analytics aggregates against transactional source counts.

P8 analytics is derived and recalculable.

---

## 167. Audit reconciliation

Verify important administrative actions have expected AuditLog entries.

Not every read operation has an audit row unless the specific policy requires it.

---

## 168. Dress rehearsal

Run a realistic production-like rehearsal including deployment, migration, seed and configuration validation, student flow, admin flow, commerce, communication, failure injection, backup and restore validation, and rollback demonstration.

---

## 169. Dress rehearsal data

Use synthetic or approved non-production data.

Uncontrolled production personal or financial data is not used for rehearsal.

---

## 170. Dress rehearsal evidence

Retain timeline, release version, operator actions, test results, incidents, recoveries, and unresolved observations.

---

## 171. Security review evidence

Retain automated security tests, dependency and security scan results, manual penetration test findings where conducted, secrets scan, audit verification, and remediation status.

---

## 172. Dependency security

Review production dependencies for known critical vulnerabilities, unsupported versions, transitive vulnerabilities, and runtime compatibility.

Major framework upgrades are not blindly performed during final hardening without regression testing.

---

## 173. Framework upgrade rule

A late major framework upgrade is a hardening risk.

If required, isolate, test fully, rerun performance, rerun security, and update evidence.

---

## 174. Browser security review

Validate CSP, cookies and session behavior, HTTPS, mixed content, unsafe inline execution, download handling, and file upload UX.

---

## 175. API security review

Review all privileged endpoints, state-changing actions, object ownership, pagination, error leakage, rate limiting, and idempotency.

---

## 176. Database security review

Review least-privilege database user, network exposure, credential storage, TLS where configured, backups, dangerous extensions and features, and audit and logging boundaries.

---

## 177. Operational data access

Production operators use controlled application and runbook paths.

Direct production SQL is exceptional, documented, and not the normal operational repair mechanism.

---

## 178. Production SQL restriction

Production SQL editing is not relied upon to repair attempts, responses, results, payments, grants, audit, or published assessment data.

Use domain services and reconciliation.

---

## 179. Object storage hardening

Validate authorization before file access, generated and safe object names, protected downloads, no raw file paths in APIs, failed upload cleanup, and appropriate retention.

---

## 180. Asset authorization

Question assets remain protected by backend authorization.

Never expose asset file path, raw storage credentials, or unprotected object-store URLs when the contract forbids them.

---

## 181. Database storage monitoring

Monitor table growth, index growth, free storage, WAL, backup size, audit growth, and notification growth.

New storage tables are not added solely to simplify monitoring.

---

## 182. Table growth priorities

Large operational tables deserve periodic review.

```text
student_response
attempt_question
attempt_question_option
audit_log
notification
purchase and payment
analytics tables
background_job
```

Use evidence before partitioning or archiving.

---

## 183. Retention policy

The source does not freeze a universal retention duration for all tables.

Do not invent retention values as product guarantees.

Where retention is needed, define it by domain and validate legal and business implications.

---

## 184. Log retention

Logging retention is an operational configuration, not a frozen product database field.

Ensure enough retention for troubleshooting, pilot and production incident analysis, and audit correlation without creating unbounded storage growth.

---

## 185. Monitoring retention

Metrics retention is sufficient to compare release, load test, incidents, and capacity.

The exact retention period is infrastructure-specific.

---

## 186. Release artifact retention

Retain release evidence and immutable build identifiers for the period required by the organization's release governance.

Secrets are not stored in evidence artifacts.

---

## 187. Release approval

Production release requires explicit approval after regression, security, performance, migration, staging, smoke, backup and restore, rollback, and operational readiness.

---

## 188. Go and no-go criteria

No-go if any of the following is unresolved for the release.

```text
Critical assessment-integrity failure
Critical authorization bypass
Unexplained financial inconsistency
Unsafe migration
Failed rollback
Backup or restore failure for required scope
Materially missed core performance gate
Uncontrolled exam failure rate
```

---

## 189. Conditional acceptance

A non-critical limitation may be accepted only when impact is documented, affected scope is known, workaround exists where needed, owner is assigned, and release authority accepts it.

Critical integrity or security defects are not waived with a known-issue label.

---

## 190. Release evidence package

Create one release evidence package containing release identifiers, test reports, security findings, performance report, migration result, backup and restore evidence, smoke result, incident and runbook readiness, known limitations, and approval.

---

## 191. Release checklist - Application

```text
[ ] Critical features complete
[ ] No critical defects
[ ] No unresolved release-blocking defects
[ ] Error handling verified
[ ] Configuration externalized
[ ] Observability active
```

---

## 192. Release checklist - Database

```text
[ ] Migrations tested
[ ] Constraints verified
[ ] Indexes reviewed
[ ] Backup verified
[ ] Restore verified
[ ] Storage capacity reviewed
```

---

## 193. Release checklist - Security

```text
[ ] HTTPS
[ ] JWT and session security
[ ] RBAC
[ ] Authorization
[ ] Input validation
[ ] XSS
[ ] SQL injection
[ ] Upload security
[ ] Secrets protected
[ ] Audit verified
```

---

## 194. Release checklist - Assessment

```text
[ ] Start
[ ] Snapshot
[ ] Autosave with ETag
[ ] Recovery
[ ] Timer
[ ] Navigation
[ ] Submission
[ ] Evaluation
[ ] Result
[ ] Publication
[ ] Rank and percentile recomputation
```

---

## 195. Release checklist - Commerce

```text
[ ] Purchase
[ ] Payment success
[ ] Payment failure
[ ] Callback
[ ] Callback idempotency
[ ] Refund
[ ] AccessGrant
[ ] Authorization
```

---

## 196. Release checklist - Communication

```text
[ ] Notification center
[ ] Templates
[ ] Announcements
[ ] Asynchronous delivery
[ ] Retry
[ ] Provider failure handling
```

---

## 197. Release checklist - Administration

```text
[ ] Settings
[ ] Business rules
[ ] Lookups
[ ] Audit
[ ] Users
[ ] Support
[ ] Bulk and import
[ ] Search
[ ] Health
```

---

## 198. Release checklist - Operations

```text
[ ] Monitoring
[ ] Logs
[ ] Alerts
[ ] Health checks
[ ] Backup
[ ] Restore
[ ] Rollback
[ ] Incident procedure
[ ] Support procedure
[ ] On-call ownership
```

---

## 199. Production smoke suite

Immediately after production deployment verify application health, database connectivity, Redis connectivity, PgBouncer path, login, student assessment read, Start Exam safe path, response save with ETag, submission, admin access, commerce read, and communication read.

---

## 200. Production monitoring window

After deployment, actively monitor API errors, p95, database health, PgBouncer, Redis, workers, exam starts, autosave, submissions, evaluation, commerce, and communication.

The monitoring window is operational policy and is explicitly assigned.

---

## 201. Deployment freeze after release

For the initial production deployment, avoid unrelated configuration changes, unrelated migrations, and feature work.

Allow the release to stabilize before subsequent changes.

---

## 202. Production rollback trigger

Rollback is considered when a critical exam-integrity defect occurs, widespread authentication failure occurs, severe submission failure occurs, data corruption occurs, security bypass occurs, unsafe migration occurs, or release-caused systemic failure occurs.

---

## 203. Rollback safety

Before rollback protect evidence, stop unsafe traffic or actions where possible, confirm previous version compatibility, preserve database state, execute rollback, run smoke tests, and reconcile critical data.

---

## 204. Post-rollback reconciliation

After rollback check attempts, responses, submissions, evaluations, results, payments and grants, audit, and jobs.

No unexplained state divergence remains.

---

## 205. Post-release defect triage

Classify defects.

```text
Release blocker
Urgent production fix
Normal bug
Deferred enhancement
```

Feature development is not reopened inside a stability incident unless necessary.

---

## 206. Incident postmortem

For critical production incidents, record what happened, impact, detection, timeline, root cause, contributing factors, response, recovery, reconciliation, and corrective actions.

---

## 207. Post-pilot findings handoff

P7 produces validated scale profile, capacity baseline, known limits, incident record, student experience findings, operational workload, security findings, reliability findings, cost baseline, architecture decisions, and next-phase priorities.

P12 uses these findings as release evidence and input.

---

## 208. Cost baseline

Production readiness reviews compute, PostgreSQL, Redis, PgBouncer and infrastructure, storage, network, monitoring and logging, provider costs, and backup costs.

The source does not freeze a cost budget. Actual observed or capacity-based cost is reported.

---

## 209. Capacity headroom

Production is not sized exactly at the observed peak.

Use load evidence to establish operational headroom.

The exact headroom percentage is not frozen by the source and is not invented as a contractual value.

---

## 210. Connection capacity

Database and application pool sizes are load-test-driven.

Pool sizes are not set based only on theoretical concurrent users.

---

## 211. Worker headroom

Worker concurrency preserves capacity for snapshot preparation, evaluation, communication, analytics, reports, imports and bulk.

Non-critical jobs do not consume all capacity.

---

## 212. API timeout review

Review all outbound and internal timeouts for database, Redis, storage, email, payment, and HTTP clients.

Timeouts prevent thread and connection exhaustion while allowing legitimate operations.

---

## 213. Retry review

Review all retries for response save, submission, payment, email, jobs, and reconciliation.

Retries are bounded and idempotency-aware.

---

## 214. Retry storm prevention

Use exponential backoff, jitter, max attempts, rate limits, and queue backpressure.

Multiple layers do not retry the same request uncontrollably.

---

## 215. Circuit-breaker decision

The source does not freeze a circuit-breaker implementation.

Circuit breakers are not claimed to exist unless implemented.

Add them only when evidence shows they are needed.

---

## 216. Dependency isolation

Slow external systems do not consume all application resources.

Examples: payment provider, email provider, storage.

Use bounded client concurrency and timeouts according to the selected provider contracts.

---

## 217. Database lock review

For critical workflows inspect response save, submission, evaluation, callback processing, refunds, grant operations, and configuration writes.

Measure lock waits under concurrency.

---

## 218. Transaction boundaries

Transactions remain aligned with domain ownership.

Giant admin transactions spanning unrelated domains are not created.

---

## 219. Long transaction review

Identify transactions with large fan-out, external network calls, long-running queries, report generation, or communication sends.

Move long work out of request transactions.

---

## 220. Async boundary review

Background jobs own snapshots, analytics, reports, imports, bulk operations, and external communication.

Core interactive requests remain bounded.

---

## 221. Data consistency after async job

For every important job define input, state transition, success, retry, terminal failure, and reconciliation.

Worker completion alone is not treated as business truth.

---

## 222. Health check review

Health checks distinguish healthy, degraded, unavailable, and unknown.

Healthy is not reported when telemetry is missing.

---

## 223. Health check timeout

Health checks do not hang indefinitely.

External checks require bounded timeouts.

---

## 224. Security monitoring

Monitor failed logins, authorization denials, privilege changes, suspicious bulk and import attempts, sensitive endpoint errors, and security scan findings.

---

## 225. Audit monitoring

Monitor audit insert failures, audit query failures, and sudden privileged-change spikes.

A critical audit failure is visible operationally.

---

## 226. Financial monitoring

Monitor payment success and failure, pending payments, callback duplicates, refund failures, grant inconsistencies, and reconciliation mismatch.

Use P9 transaction truth.

---

## 227. Exam integrity monitoring

Monitor start success, snapshot readiness, response saves, submissions, recovery, evaluation, and result reconciliation.

This is more important than cosmetic dashboard metrics.

---

## 228. Communication monitoring

Monitor notification creation, email sends, failures, retries, queue depth, and provider health.

Business state remains independent.

---

## 229. Analytics monitoring

Monitor refresh failures, calculation duration, stale timestamps, query latency, and rebuild outcomes.

Transactional source data is not altered to repair analytics.

---

## 230. Admin monitoring

Monitor admin API errors, search latency, import and bulk failures, audit query latency, configuration update errors, and privileged operation rate.

---

## 231. Release metrics snapshot

Capture a metrics snapshot immediately before and after production release so release-caused regressions can be identified.

---

## 232. Regression comparison

Compare previous stable release to release candidate for p95, error rate, submission success, exam failure, database load, and worker backlog.

Releases are not compared using different workload profiles without explaining the difference.

---

## 233. Data volume for performance tests

Load tests use realistic question-bank size, assessments, users, attempts, responses, purchases, analytics, and audit volume.

Tiny datasets do not provide meaningful production evidence.

---

## 234. Test data reproducibility

Load data generation is deterministic enough to reproduce major performance findings.

Record dataset version, generator version, and scale.

---

## 235. Performance environment parity

The closer staging and load infrastructure is to production, the more meaningful the result.

Differences are documented.

---

## 236. Release environment parity

Staging is close enough to production to validate migrations, configuration, networking, PgBouncer, Redis, workers, storage, and observability.

---

## 237. Production configuration dry run

Validate final configuration in staging before production for values, types, secrets injection, feature flags, endpoints, timeouts, CORS, and storage.

---

## 238. Secret injection test

Verify secrets arrive through secure deployment configuration and are not committed, printed, returned in health responses, or logged during startup.

---

## 239. Artifact immutability

Production deploys the exact reviewed container artifact.

An untracked image is not rebuilt between approval and production.

---

## 240. Image digest verification

Record and verify container image digests for production components.

The release evidence identifies exact deployed images.

---

## 241. Dependency locking

Use the repository's standard dependency locking and management approach so release builds remain reproducible.

---

## 242. Frontend artifact verification

Record the frontend build and version corresponding to the backend release candidate.

Frontend and backend contract mismatch is avoided.

---

## 243. API and frontend compatibility

Before production verify frontend points to correct API, API responses match frontend expectations, auth handling matches, error envelope matches, and feature flags match.

---

## 244. Database and application compatibility

Validate that application can start on migrated schema, previous compatible version can be rolled back where required, and new fields and structures do not break old code when expand and contract is used.

---

## 245. Release ordering

For a schema and application change.

```text
Backup
  |
  v
Migration
  |
  v
Application deployment
  |
  v
Health
  |
  v
Smoke
```

For compatible expand and contract releases, follow the approved sequence.

---

## 246. Health gate after deploy

Release is not declared successful solely because the process is running.

Check application health, database, Redis, PgBouncer, workers, and critical APIs.

---

## 247. Smoke gate after deploy

Critical flows pass after production deploy.

Failure of an exam-critical smoke path is a release blocker.

---

## 248. Monitoring gate after deploy

Observe start, response save, submission, evaluation, auth, database, worker, and error rate for the defined operational window.

---

## 249. Production incident communication

The incident procedure defines internal communication, stakeholder communication, student-facing messaging where needed, escalation, and resolution message.

Sensitive communications are not improvised during an incident.

---

## 250. Student-facing incident messaging

Messages are accurate, concise, non-speculative, and action-oriented.

Internal technical details are not exposed.

---

## 251. Examination integrity communication

If an incident affects exam integrity, preserve evidence, identify affected scope, communicate only verified facts, and follow approved recovery and reconciliation procedure.

Attempt data is not silently altered.

---

## 252. Backup restore runbook test

Run the actual documented restore procedure with an operator who did not author it, where practical.

This tests whether the runbook is operationally usable.

---

## 253. Rollback runbook test

Run a controlled rollback rehearsal in staging and load environment.

Verify previous artifact, compatibility, health, smoke, and reconciliation.

---

## 254. Incident runbook test

Walk through major incident scenarios for database outage, snapshot backlog, submission failure, payment mismatch, email outage, and worker outage.

Document gaps found during rehearsal.

---

## 255. Operational knowledge transfer

Before production, developers, QA, operations, support, finance, and content and assessment operators understand their operational responsibilities.

The exact training format is organizational, not frozen in the product schema.

---

## 256. Production access review

Review who can access production application, PostgreSQL, Redis, PgBouncer, observability, object storage, deployment system, and secrets.

Use least privilege.

---

## 257. Production database access

Limit direct database access to approved operational roles.

Application ownership remains the primary production mutation path.

---

## 258. Production observability access

Ensure support and operations can see required dashboards without gaining unnecessary database or secret access.

---

## 259. Deployment access

Production deployment permissions are limited and auditable.

Arbitrary developers do not deploy unreviewed artifacts.

---

## 260. Secret access review

Secret access is restricted to the minimum operational identities required.

Shared plaintext secrets are not used in documentation.

---

## 261. Configuration change review

Before production review configuration, verify ownership, verify change record, verify audit and traceability, and verify rollback path.

---

## 262. Operational cost of monitoring

Monitoring and logging do not consume enough resources to threaten core exam delivery.

Tune telemetry volume using measured workloads.

---

## 263. Log volume protection

Avoid verbose request bodies, repetitive health logs, full provider payloads, and high-cardinality PII labels.

Use structured fields.

---

## 264. Metrics cardinality

Avoid labels containing email, mobile, user id, query strings, payment references, and free-form remarks.

Use bounded dimensions.

---

## 265. Trace sampling

Tracing strategy balances diagnostic value with production overhead.

All requests are not assumed to be sampled at 100 percent without evidence.

---

## 266. Monitoring failure mode

If the monitoring backend is unavailable, the application does not fail solely because telemetry cannot be delivered, critical business flows remain independent, and the operational blind spot is alerted through available mechanisms.

---

## 267. Health endpoint failure mode

If a non-critical health sub-check fails, represent degraded or unknown accurately.

The entire system is not made to appear healthy.

---

## 268. Release documentation

Each production release includes release version, changes since prior release, migration notes, configuration changes, known limitations, operational impact, and rollback procedure.

---

## 269. Change classification

Use roadmap categories.

```text
Category A  Bug fix
Category B  Minor enhancement
Category C  Contract change
Category D  Architecture change
```

During hardening, avoid Category B, C, and D changes unless release governance explicitly approves them.

---

## 270. Category A during hardening

Bug fixes proceed when they correct existing behaviour, do not alter frozen architecture, and have regression tests.

Relevant evidence is rerun if the fix touches measured paths.

---

## 271. Category C during hardening

API, schema, and workflow changes require architecture review and invalidate affected release evidence.

Contract changes are not merged into a release candidate casually.

---

## 272. Category D during hardening

Framework, database, service-boundary, and deployment changes require explicit architecture approval.

A production release generally avoids such changes during final hardening unless critical.

---

## 273. Documentation freeze

Final release documentation matches the deployed release.

Code is not deployed whose source, API, database, and runbook documentation are materially stale.

---

## 274. API documentation freeze

OpenAPI matches actual endpoints, request and response schemas, statuses, and authentication requirements.

---

## 275. Database documentation freeze

Database documentation matches actual migrations, indexes, constraints, and the frozen table model.

Undocumented production tables are not left behind.

---

## 276. Operational runbook freeze

Runbooks match actual deployment commands, actual dashboards, actual infrastructure, and actual recovery procedures.

---

## 277. Release review board inputs

A release review receives functional status, security status, performance evidence, operational readiness, data reconciliation, known limitations, and rollback confidence.

---

## 278. No-go examples

```text
Start Exam creates large synchronous snapshots
Response-save correctness not proven
Submission success below threshold without accepted explanation
Student can access another user's result
Payment success can create duplicate grants
Backup cannot be restored
Migration cannot be safely deployed
Rollback is untested
```

---

## 279. Production readiness matrix

| Area | Gate |
|---|---|
| Product | Critical journeys pass |
| Assessment | Exact version and snapshot integrity |
| Performance | Concurrent-user evidence |
| Submission | Greater than 99% success |
| Failure | Less than 1% exam failure |
| API | Under 100 ms p95 applicable target |
| Availability | 99.9% target strategy and evidence |
| Security | No critical unresolved issue |
| Database | Migration, index, and restore evidence |
| Operations | Dashboards, alerts, runbooks |
| Release | Rollback tested |

---

## 280. P12 implementation task breakdown

```text
P12-01  Hardening freeze
P12-02  Security regression
P12-03  API regression
P12-04  Database performance
P12-05  Concurrent-user scale validation
P12-06  Concurrency regression
P12-07  Failure injection
P12-08  Backup validation
P12-09  Restore rehearsal
P12-10  Migration rehearsal
P12-11  Rollback rehearsal
P12-12  Monitoring and alert validation
P12-13  UX regression
P12-14  Production configuration review
P12-15  Dress rehearsal
P12-16  Release evidence package
P12-17  Production smoke test
P12-18  Post-release monitoring
```

Execute one task at a time with checkpoints.

---

## 281. P12-01 hardening freeze acceptance

```text
[ ] Feature freeze announced
[ ] Release candidate tagged
[ ] Migrations frozen
[ ] Load profile frozen
[ ] Test dataset versioned
[ ] Configuration revision identified
```

---

## 282. P12-02 security acceptance

```text
[ ] Authentication
[ ] Authorization
[ ] IDOR
[ ] Privilege escalation
[ ] Mass assignment
[ ] SQL injection
[ ] XSS
[ ] Upload security
[ ] CORS
[ ] CSRF strategy
[ ] Secrets
[ ] Audit
```

---

## 283. P12-03 API regression acceptance

```text
[ ] Statuses
[ ] Envelopes
[ ] Validation
[ ] Auth
[ ] Authorization
[ ] Conflict
[ ] Invalid state
[ ] Precondition handling
[ ] Pagination
[ ] Filtering
[ ] Idempotency
[ ] Rate limits
```

---

## 284. P12-04 database acceptance

```text
[ ] Query plans
[ ] Indexes
[ ] Locks
[ ] Connection capacity
[ ] WAL
[ ] Autovacuum
[ ] Backup
[ ] Restore
[ ] Migration
```

---

## 285. P12-05 load acceptance

```text
[ ] Concurrent users
[ ] Production-like environment
[ ] Realistic data
[ ] Warm start
[ ] Autosave
[ ] Submission
[ ] Recovery
[ ] Result generation
[ ] Metrics retained
```

---

## 286. P12-06 concurrency acceptance

```text
[ ] Start Exam
[ ] Response save
[ ] Duplicate response save
[ ] Submission
[ ] Duplicate submission
[ ] Evaluation
[ ] Payment callback
[ ] Refund
[ ] Role and permission changes
[ ] Stale-write precondition handling
```

---

## 287. P12-07 failure injection acceptance

```text
[ ] PostgreSQL
[ ] Redis
[ ] PgBouncer
[ ] Worker
[ ] Network
[ ] Provider
[ ] Application restart
[ ] Job retry
```

---

## 288. P12-08 backup acceptance

```text
[ ] Automated backup
[ ] Protected storage
[ ] Successful backup
[ ] Failure alert
[ ] Evidence
```

---

## 289. P12-09 restore acceptance

```text
[ ] Restore rehearsal
[ ] Schema valid
[ ] Constraints valid
[ ] Critical records present
[ ] Core journey tested
[ ] Evidence retained
```

---

## 290. P12-10 migration acceptance

```text
[ ] Full chain tested
[ ] Staging rehearsal
[ ] Compatibility validated
[ ] Failure path tested
[ ] Production order documented
```

---

## 291. P12-11 rollback acceptance

```text
[ ] Previous artifact identified
[ ] Compatibility validated
[ ] Rollback performed in staging
[ ] Smoke passed
[ ] Reconciliation passed
```

---

## 292. P12-12 observability acceptance

```text
[ ] Metrics
[ ] Logs
[ ] Dashboards
[ ] Alerts
[ ] Health
[ ] Worker visibility
[ ] Database visibility
[ ] PgBouncer visibility
[ ] Redis visibility
```

---

## 293. P12-13 UX acceptance

```text
[ ] Critical student journey
[ ] Runtime exam UI
[ ] Recovery UX
[ ] Results UX
[ ] Admin UX
[ ] Error states
[ ] Accessibility
[ ] Responsive behavior
```

---

## 294. P12-14 configuration acceptance

```text
[ ] Environment configuration reviewed
[ ] Secrets injected safely
[ ] Feature flags reviewed
[ ] CORS
[ ] HTTPS
[ ] Timeouts
[ ] Retries
[ ] Storage
[ ] Worker concurrency
```

---

## 295. P12-15 dress rehearsal acceptance

```text
[ ] Release deployment
[ ] Migrations
[ ] Smoke
[ ] Critical flows
[ ] Failure injection
[ ] Backup and restore
[ ] Rollback
[ ] Evidence
```

---

## 296. P12-16 evidence package acceptance

```text
[ ] All gate reports
[ ] Release identifiers
[ ] Known limitations
[ ] Approvals
[ ] Remediation status
[ ] Runbooks
```

---

## 297. P12-17 production smoke acceptance

```text
[ ] Health
[ ] Auth
[ ] Student flow
[ ] Assessment
[ ] Response save
[ ] Submission
[ ] Admin
[ ] Commerce
[ ] Communication
```

---

## 298. P12-18 post-release acceptance

```text
[ ] Monitoring active
[ ] Critical flows observed
[ ] No release-blocking incident
[ ] Data reconciliation
[ ] Incident record updated where needed
```

---

## 299. Recommended execution prompt

```text
You are executing P12 Production Hardening and Production Readiness for
the Smart Competitive Examination Platform.

Read the full specification package before changing anything.

P12 is a feature-freeze phase.
Focus only on correctness, security, performance, reliability, UX, and
operational readiness.

NON-NEGOTIABLE:
1.  Preserve the modular monolith.
2.  Do not invent schema or APIs for convenience.
3.  Do not reopen validated delivery architecture.
4.  Start Exam must not generate large snapshots synchronously.
5.  Backend remains authoritative.
6.  Redis is not source of truth.
7.  AccessGrant remains the commerce authorization source of truth.
8.  Published artifacts remain immutable.
9.  Flyway owns schema changes.
10. Important mutations remain permission-controlled and audited.
11. Large operations remain asynchronous.
12. Use production-like load testing.
13. Retain evidence for concurrent users, 99.9% availability,
    under 100 ms p95 applicable API target, greater than 99%
    submissions, and less than 1% exam failure.
14. A material change after performance or security validation requires
    rerunning the affected suite.
15. Do not use stale evidence for a changed release.
16. Do not patch production business data directly as a normal
    operational procedure.
17. Backup, restore, and rollback must be tested.
18. A critical assessment-integrity, security, or data-corruption
    defect is a no-go.
19. Stale-write precondition handling is verified under concurrency.
20. Rank and percentile recomputation is verified after result
    publication.

Implementation sequence:
Freeze
  |
  v
Inspect
  |
  v
Security regression
  |
  v
Functional regression
  |
  v
Performance
  |
  v
Failure injection
  |
  v
Backup and restore
  |
  v
Migration rehearsal
  |
  v
Rollback rehearsal
  |
  v
Staging and dress rehearsal
  |
  v
Release evidence
  |
  v
Production deployment
  |
  v
Smoke
  |
  v
Active monitoring
  |
  v
Reconciliation
```

---

## 300. Final exit criteria

P12 is complete only when feature freeze is completed, release candidate is uniquely identified, all critical regression suites pass, security gates pass, concurrent-user evidence is retained, performance targets are measured, submission target is measured, exam failure target is measured, database capacity is reviewed, backups are verified, restore is verified, migration is rehearsed, rollback is rehearsed, monitoring and alerts are verified, runbooks are approved, dress rehearsal is complete, production smoke suite is defined, release evidence package is complete, and explicit go and no-go approval is recorded.

---

## 301. Final engineering principle

The production release is the result of evidence, not assumption.

The platform is considered ready only when the following form a consistent operational chain.

```text
Code
  |
  v
Automated tests
  |
  v
Production-like deployment
  |
  v
Scale validation
  |
  v
Failure injection
  |
  v
Reconciliation
  |
  v
Dress rehearsal
  |
  v
Release approval
  |
  v
Production smoke
  |
  v
Active monitoring
```

---

## 302. Next roadmap

After hardening and production release, future priorities may include broader reporting, production expansion, additional operational improvements, and the advanced AI roadmap.

The source does not authorize these as hardening work.

Advanced AI remains a separate future roadmap.

---

## 303. Decision and evidence register

| Decision or evidence | Required before production |
|---|---|
| Feature freeze | Yes |
| Release candidate identity | Yes |
| Concurrent-user evidence | Yes |
| Under 100 ms p95 applicable API evidence | Yes |
| Greater than 99% submission evidence | Yes |
| Less than 1% exam failure evidence | Yes |
| 99.9% availability readiness evidence | Yes |
| Security regression | Yes |
| Database capacity review | Yes |
| Backup | Yes |
| Restore | Yes |
| Migration rehearsal | Yes |
| Rollback rehearsal | Yes |
| Monitoring and alerts | Yes |
| Incident and runbooks | Yes |
| Dress rehearsal | Yes |
| Production smoke | Yes |
| Explicit go and no-go | Yes |

---

## 304. Non-negotiable invariants

```text
1. No feature expansion during final hardening without release governance.
2. Backend owns business truth.
3. Assessment correctness outranks convenience.
4. Start Exam does not synchronously generate a large attempt snapshot.
5. Snapshot is prepared asynchronously before exam start.
6. Server time is authoritative for exam timing.
7. Published question, blueprint, assessment, and answer-key artifacts
   are immutable.
8. AccessGrant is the paid-access authorization source of truth.
9. Financial transactions remain Commerce-owned.
10. Analytics remain derived and recalculable.
11. AuditLog remains immutable.
12. Redis remains cache and coordination infrastructure, not
    transactional truth.
13. Large operations remain asynchronous.
14. Direct production SQL mutation is not the normal repair path.
15. A material release change invalidates affected evidence until
    retested.
16. Critical assessment-integrity, security, or data-corruption
    defects are release blockers.
```

---

## 305. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P12 — Production Hardening and Production Readiness.

**Next implementation document:** P13 — Broader Reporting and Operational Reporting.