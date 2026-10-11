# Smart Competitive Examination Platform
## P7 — Scale Validation & Live Pilot Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P7 — Pilot Operations, Scale Validation, and Live Pilot
**Depends on:** P0 through P6 Implementation Specifications, ExamEngine and CDUI Contract
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Connection Pooling:** PgBouncer
**Deployment:** Docker, Nginx, CI/CD
**Observability:** Prometheus, Grafana, Loki
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Operations, Architects

---

## 1. Purpose

This document converts the pilot execution strategy into an implementation-ready operational specification.

P7 is not a second implementation of the analytics subsystem.

P7 consumes the telemetry and operational controls established in P6 and uses them to validate scale, reliability, runtime correctness, operational readiness, pilot execution, and evidence retention.

P7 exists to make the platform safe to operate for a real pilot cohort, not to expand the feature set.

---

## 2. Source-of-truth hierarchy

Read these documents before P7 execution:

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
- This document

The earlier documents already freeze the runtime architecture, the delivery snapshot model, the evaluation contract, and the performance acceptance targets. This document does not modify those decisions.

---

## 3. P7 mission

P7 proves with evidence that the platform can operate a real pilot workload safely.

The mission.

```text
Build confidence with evidence.
Do not declare readiness from code review alone.
```

P7 must demonstrate:

```text
Concurrent-user behavior
Start-burst resilience
Autosave resilience
Submission-burst resilience
Recovery resilience
Runtime correctness
Evaluation completion
Security isolation
Monitoring visibility
Operator readiness
Rollback capability
Incident response readiness
```

---

## 4. P7 success principle

> The pilot is an evidence exercise, not a feature-development phase.

P7 minimizes new product functionality.

The default question during P7 is:

> Can the frozen system reliably deliver the pilot?

not:

> What new feature can be added before launch?

---

## 5. P7 scope

In scope.

```text
Load-test environment
Production-like environment validation
Concurrent-user scale validation
Start burst
Autosave burst
Submission burst
Recovery burst
Failure injection
Database capacity validation
PgBouncer validation
Observability validation
Security validation
Pilot dry runs
Pilot launch readiness
Live pilot execution
Post-pilot review
```

Out of scope.

```text
New commerce flows
Full analytics
Full administration
Advanced proctoring
AI features
New mobile applications
New exam engines unless required to fix a validated pilot gap
```

---

## 6. Frozen pilot path

The complete pilot path.

```text
Identity
   |
   v
Content
   |
   v
Assessment
   |
   v
Delivery
   |
   v
Evaluation
   |
   v
Minimal pilot operations and telemetry
   |
   v
Scale validation
   |
   v
Live pilot
```

A defect discovered in an earlier layer is fixed at its source rather than patched only in P7 operations.

---

## 7. Pilot scope boundary

The pilot may begin with one organization.

The architecture remains organization-aware.

```text
Organization
    |
    v
Users
    |
    v
Content and assessments
    |
    v
Attempts and results
```

Pilot-specific hard-coded organization identifiers are not committed into business logic.

---

## 8. Pilot access model

The first pilot uses the controlled AccessGrant onboarding model.

```text
Pilot user
   |
   v
AccessGrant
   |
   v
Assessment authorization
   |
   v
Scheduled assessment
```

Commercial checkout is deferred.

Routine onboarding uses a version-controlled mechanism, not unrestricted production SQL.

---

## 9. Pilot readiness gate

A pilot does not start until all critical gates are green.

```text
Identity
Content
Assessment
Delivery
Evaluation
Telemetry
Security
Backup
Rollback
Load test
Operational runbook
Dry run
```

A single critical unresolved failure is a no-go condition for the affected pilot scope.

---

## 10. Performance baseline

The frozen architecture requires evidence for.

```text
5,000 concurrent users
99.9% availability target
Under 100 ms p95 for applicable API requests
Greater than 99% successful submissions
Less than 1% exam failure rate
```

These are acceptance gates, not guarantees.

The test report identifies:

```text
Workload
Infrastructure
Software version
Database size
Data volume
Test duration
Test profile
Results
Known limitations
```

---

## 11. P7 does not change the data model

P7 does not add new tables solely for pilot measurement.

The frozen analytics and telemetry architecture is used.

New performance tooling may write to external metrics and logging systems, test tooling stores, or CI artifacts without changing transactional schema.

---

## 12. Production-like environment

Scale validation uses a production-like environment.

At minimum.

```text
Next.js deployment
Spring Boot deployment
Nginx
PgBouncer
PostgreSQL
Redis
Background workers
Prometheus
Grafana
Loki
Object and file storage equivalent
```

The closer the environment is to pilot production, the more meaningful the load result.

---

## 13. Environment separation

Maintain.

```text
Local
Test
Staging and load
Production
```

No load test points at the live production database.

No pilot dry run shares uncontrolled state with production.

---

## 14. Release candidate

A P7 release candidate is immutable and identifiable by.

```text
Application version
Frontend version
Database migration version
Container image digest
Configuration revision
```

The exact values are retained with pilot evidence.

---

## 15. Freeze before load test

Before the primary scale test:

```text
Freeze application code
Freeze database migration set
Freeze test data model
Freeze load profile
Freeze core runtime policies
```

Only critical blockers change after the freeze.

If a code change is made, the relevant performance suite is rerun.

---

## 16. Production configuration review

Validate.

```text
JWT configuration
CORS
HTTPS
Database pool
PgBouncer
Redis
Worker concurrency
Timeouts
Retry policies
Logging
Metrics
File storage
DNS
TLS
Secret management
```

Secrets are not included in source control or test reports.

---

## 17. PgBouncer validation

PgBouncer is part of the pilot production path.

Validate.

```text
Application compatibility
Transaction boundaries
Pool sizing
Pool exhaustion
Connection reuse
Failure behavior
Restart behavior
Monitoring
```

P7 demonstrates that adding PgBouncer does not alter application correctness.

---

## 18. PostgreSQL capacity validation

Measure.

```text
CPU
Memory
I/O
Connections
Locks
WAL
Checkpoint behavior
Autovacuum
Query latency
Transaction rate
```

Capacity is derived from actual load-test evidence rather than theoretical connection counts alone.

---

## 19. Redis validation

Redis is cache and coordination infrastructure.

Test.

```text
Normal operation
Cache misses
Redis restart
Redis unavailable
Recovery after Redis returns
```

Core exam correctness survives Redis unavailability because PostgreSQL is authoritative.

---

## 20. Background worker validation

Validate snapshot preparation workers, evaluation workers, scorecard generation workers where used, cleanup workers, retry behavior, worker restart, and worker backlog.

Worker concurrency remains bounded.

---

## 21. Test data strategy

Create a deterministic synthetic dataset representing.

```text
1 organization
5,000 students
1 published assessment version
100 questions
Representative options
1 or more schedules
Prepared attempts
Responses
Answer key and answer key detail
Evaluation fixtures
```

The exact question count may vary for other workloads. The main load test retains the 100-question reference scenario.

---

## 22. Question content test corpus

The pilot corpus includes representative content.

```text
Plain text
Long text
Currency
Inline math
Block math
Images and assets
Single select
Multi select if supported
Questions with no options
Special characters
```

This ensures performance tests do not accidentally validate only trivial content.

---

## 23. Prepared attempt dataset

For the primary concurrent-user start test.

```text
5,000 authenticated users
5,000 eligible attempts
5,000 READY snapshots
0 snapshot generation during start test
```

This is mandatory because Start Exam validates the prepared state rather than performing snapshot generation.

---

## 24. Load-test tooling contract

The test harness supports.

```text
Scheduled release of users
Authentication
Start
Question retrieval
Response saves with ETag
Navigation
Recovery
Submission
Assertions
Latency collection
Error classification
Correlation ids
```

The tool may be external to the product codebase.

---

## 25. Load-test profiles

At minimum.

```text
PROFILE-01 Start burst
PROFILE-02 Steady exam
PROFILE-03 Autosave burst
PROFILE-04 Submission burst
PROFILE-05 Recovery burst
PROFILE-06 Mixed realistic exam
PROFILE-07 Failure injection
```

All profiles are versioned with the test evidence.

---

## 26. Start burst workload

Release 5,000 starts within a narrow interval while snapshot_status is READY.

Measure start p50, p95, p99, error rate, database locks, connection utilization, and application saturation.

---

## 27. Start burst correctness assertions

For every successful start:

```text
Attempt = correct student
AssessmentVersion = correct
Schedule = correct
Attempt mode = correct
Session exists
Attempt becomes IN_PROGRESS
Snapshot row counts unchanged
```

Critical assertion.

```text
Start burst produces zero new AttemptQuestion rows.
Start burst produces zero new AttemptQuestionOption rows.
```

---

## 28. Steady exam workload

Simulate students behaving over a representative exam window.

```text
Question reads
Navigation
Response saves
Review marking
Clear response
Occasional idle time
Recovery events
Eventual submission
```

This catches issues that synthetic endpoint benchmarks miss.

---

## 29. Autosave burst workload

At the effective autosave policy, simulate.

```text
5,000 active attempts
Periodic response updates
Duplicate retry
Temporary network errors
Stale If-Match precondition retries
```

Measure save p95 and p99, write throughput, error rate, retry amplification, and database pressure.

---

## 30. Submission burst workload

Simulate.

```text
5,000 submissions
Short time window
Duplicate submit requests
Late requests
Some timer-expired attempts
```

Assert no duplicate logical submission, no attempt resurrection, and no duplicate evaluation.

---

## 31. Recovery burst workload

Simulate 5,000 active users, connection loss or service disruption, reconnect, recover, and continue.

Assert same attempt, same snapshot, same answers, same timer, no duplicate attempt.

---

## 32. Mixed realistic workload

Use a mixed distribution.

```text
Question read
Navigation
Answer
Mark for review
Clear
Idle
Recover
Submit
```

The mix represents observed pilot behavior where available. If observed pilot data does not yet exist, documented synthetic assumptions are used and labeled as such.

---

## 33. Ramp test

Do not jump immediately to the target.

Recommended progression.

```text
100
500
1,000
2,000
3,000
4,000
5,000
```

At each step inspect latency, errors, database health, connection pressure, and application saturation.

Stop and diagnose rather than blindly escalating after a severe failure.

---

## 34. Soak test

Run a sustained workload to expose memory leak, connection leak, worker backlog, WAL accumulation, autovacuum pressure, cache instability, log growth, and container restart.

The exact duration is long enough to show steady-state behavior and is recorded with the evidence.

---

## 35. Spike test

Simulate sudden traffic changes.

```text
0 to 5,000 starts
5,000 active to mass submission
0 active to recovery storm
```

Spike tests target transient behavior, not only average throughput.

---

## 36. Failure injection philosophy

Inject failures one at a time before combining them.

Examples.

```text
Redis unavailable
One API instance restart
Worker restart
PgBouncer restart
Database connection interruption
Object storage latency
Network latency
```

Multiple failures are not combined until single-failure behavior is understood.

---

## 37. Failure injection - Redis

Expected.

```text
Core response reads and writes continue
Attempts remain correct
Submission continues
```

Allowed impact.

```text
Cache misses
Higher read load
```

Not allowed.

```text
Lost responses
Incorrect authorization
Attempt corruption
```

---

## 38. Failure injection - Application instance

During a multi-instance deployment, terminate one API instance.

Expected.

```text
Requests reroute
Sessions remain recoverable
No in-memory state dependency
```

---

## 39. Failure injection - Worker crash

Crash a snapshot worker during preparation.

Expected.

```text
No unsafe READY attempt
Retry path available
Duplicate rows prevented
```

Repeat for evaluation worker.

---

## 40. Failure injection - PgBouncer

Test controlled restart and failure.

Verify application retry behavior, connection recovery, transaction integrity, and no silent data loss.

---

## 41. Failure injection - PostgreSQL

A controlled transient database outage produces bounded request failures, bounded retries, no infinite retry loops, and no silently accepted-but-unpersisted mutation.

After recovery, transaction state is verified.

---

## 42. Failure injection - Storage

Scorecard generation remains separate from core evaluation.

A storage failure does not roll back an otherwise valid evaluation unless the explicit contract requires scorecard atomicity.

---

## 43. Correctness over availability

For official academic state.

```text
Correctness is preferred over cosmetic availability
```

The system prefers a clear safe failure over:

```text
Duplicated response
Wrong attempt
Wrong score
Wrong user
Extended timer
```

---

## 44. Latency measurement rules

Measure latency at client, Nginx, application, and database.

Whenever possible correlate with the same `X-Request-Id`.

P95 alone is insufficient for diagnosing long tails. Retain p50, p95, and p99.

---

## 45. Applicable API latency gate

The architecture baseline requires under 100 ms for 95% of applicable API requests.

The load report identifies which APIs are included and which are excluded because they are asynchronous or intentionally long-running.

---

## 46. Error budget interpretation

The platform baseline requires greater than 99% successful submissions and less than 1% exam failure rate.

These are defined before the final test as measurable counters.

Do not redefine an unsuccessful submission after seeing poor results.

---

## 47. Submission success definition

A successful submission means the client receives authoritative submission acknowledgment and the attempt is durably SUBMITTED.

A client-side toast or HTTP connection close alone is not success evidence.

---

## 48. Exam failure definition

An exam failure covers materially harmful cases such as cannot start, cannot recover, responses cannot be persisted, timer invalid, submission cannot complete, wrong question snapshot, cross-user data exposure, and wrong attempt association.

The final operational definition is frozen before test execution.

---

## 49. Data integrity gate

After load tests verify attempt count, snapshot count, option snapshot count, response count, submission count, evaluation count, and result count.

Then compare to expected totals.

Latency success without data-integrity success is a failed test.

---

## 50. Snapshot integrity gate

For every prepared attempt in the reference scenario.

```text
Expected question count
Expected option count
Unique order
Exact QuestionVersion references
Exact section
Exact marks
Exact negative marks
```

must match the source AssessmentVersion.

---

## 51. Response integrity gate

For completed attempts verify.

```text
One logical response per assigned question
No response for an unassigned question
Correct attempt ownership
Valid response representation
Time values within accepted rules
Review flags preserved
```

---

## 52. Evaluation integrity gate

Verify.

```text
Only submitted attempts are evaluated
Correct AnswerKey reference
Deterministic score
Negative marking correct
Bonus and dropped handled
Unanswered behavior correct
No duplicate Evaluation
```

---

## 53. Result integrity gate

Verify.

```text
Evaluation to Result
Exact obtained marks
Percentage
Rank and percentile according to frozen rules
Publication state
Student visibility
```

---

## 54. Historical reproducibility gate

Take one submitted attempt and mutate later versions of Question, Assessment, and Delivery configuration.

Re-evaluate according to the approved workflow.

The original attempt still resolves through its historical snapshot and explicit AnswerKey reference.

---

## 55. Security load validation

During load tests verify no cross-user data, no cross-organization access, no correct answers, no raw asset paths, and no privileged evaluation calls from students.

Security correctness is validated under concurrency, not only in unit tests.

---

## 56. Token expiry behavior

Simulate token expiration during question read, response save, recovery, and submit.

The system rejects unauthorized calls, avoids corrupting state, and allows normal re-authentication and recovery according to the P1 policy.

---

## 57. Rate-limit behavior

Where rate limiting is enabled, verify it does not incorrectly classify normal synchronized exam traffic as abuse.

Also test malicious burst, duplicate submit, and retry storm.

The rate limiter fails safely without becoming the source of exam failure.

---

## 58. Monitoring validation

P7 demonstrates that operators can identify start surge, save failure surge, submission failure, snapshot failure, worker backlog, database saturation, connection saturation, and application crash within the agreed operational response window.

---

## 59. Dashboard validation

The minimum dashboard presents.

```text
Active attempts
Active sessions
Start throughput
Start latency
Autosave rate
Autosave failures
Submission rate
Submission failures
Evaluation backlog
Worker failures
Database health
```

---

## 60. Log validation

Logs contain enough correlation to troubleshoot request, attempt, session, assessment, operation, and outcome.

Logs do not expose credentials, JWTs, answer keys, unnecessary student response content, or secrets.

---

## 61. Alert validation

Fire test alerts for submission failures, database saturation, worker backlog, snapshot failure spike, and API error spike.

Verify alert, operator awareness, diagnostic path, resolution, and closure.

---

## 62. Backup validation

Before live pilot, prove restore for users, assessment definitions, attempts, responses, evaluations, and results.

A backup that exists but has never been restore-tested is not an operational readiness proof.

---

## 63. Restore procedure

Document who can restore, where, from which backup, how integrity is verified, how application writes are controlled, and how service is reopened.

Do not rely on memory or tribal knowledge.

---

## 64. Rollback strategy

Every live pilot deployment needs previous application version, database compatibility assessment, rollback trigger, operator ownership, and communication path.

Database rollback respects Flyway migration rules.

Not every schema migration is safely reversible.

---

## 65. Blue-green or rolling deployment

Use the deployment strategy supported by the infrastructure, but validate old and new application compatibility, database migration compatibility, session continuity, response saves during rollout, and submission during rollout.

The deployment method itself does not create an exam outage.

---

## 66. Pre-pilot smoke test

Immediately after release candidate deployment.

```text
Login
Access
Assessment launch
Start practice
Save response
Recover
Submit
Evaluate
Read result
Generate scorecard
```

Run this before any broad load test.

---

## 67. Pilot dry run one

Run a small realistic cohort.

Verify student flow, operator flow, telemetry, support workflow, recovery, submission, evaluation, and result publication.

Record every issue, even if manually resolved.

---

## 68. Pilot dry run two

Repeat after corrections.

Include network interruption, browser refresh, duplicate submit, support intervention, and worker restart.

No critical defect remains unexplained before live pilot.

---

## 69. Full dress rehearsal

Run the entire launch procedure from candidate onboarding through result publication using production-like data volume and timings.

Treat this as a real exam day.

---

## 70. Operator roles

Use the existing roles and permissions model.

Pilot operations assign clear ownership for platform operations, content, assessment schedule, candidate access, support, evaluation, result publication, and incident response.

A generic admin credential is not shared.

---

## 71. Pre-exam runbook

Before the exam.

```text
Verify release version
Verify database health
Verify worker health
Verify snapshot READY coverage
Verify schedule
Verify AccessGrants
Verify candidate count
Verify dashboards
Verify alerting
Verify backups
Verify support coverage
```

---

## 72. Snapshot readiness gate

Before exam start check.

```text
READY / ELIGIBLE
FAILED / ELIGIBLE
PENDING / ELIGIBLE
PREPARING / ELIGIBLE
```

No attempt is startable without `snapshot_status = READY`.

The pre-exam readiness check is an operational gate.

---

## 73. Snapshot failure escalation

If snapshot failures exist, classify failure, retry if safe, inspect source assessment and content, and verify expected counts.

Snapshot rows are not manually inserted to bypass the preparation system.

---

## 74. Candidate access verification

Before the exam verify candidate exists, candidate is active, AccessGrant is valid, schedule is active, and attempt is prepared.

The authorization model remains unchanged.

---

## 75. Schedule verification

Validate exact AssessmentVersion, mode, start_time, end_time, status, and published assessment.

A schedule is not edited casually immediately before launch.

---

## 76. Exam-day start procedure

At the opening.

```text
1. Confirm readiness dashboard.
2. Confirm infrastructure health.
3. Open candidate access.
4. Observe start rate.
5. Watch p95 and p99.
6. Watch database connections.
7. Watch locks and errors.
8. Do not interfere unless thresholds breach.
```

The operator avoids unnecessary administrative actions during the burst.

---

## 77. Exam-day runtime procedure

During runtime monitor start, active sessions, autosave, API errors, database health, worker health, and support incidents.

Heavy reporting queries are not run against the primary exam database during the active window.

---

## 78. Exam-day submission procedure

Near end of exam watch submission rate, error rate, database write pressure, and evaluation backlog.

Do not trigger bulk administrative jobs that compete with the submission burst.

---

## 79. Post-exam procedure

After the exam, confirm all expected submissions, classify failed submissions, confirm evaluation queue, monitor evaluation, and verify result generation.

Keep the evidence package intact.

---

## 80. Incident severity model

```text
SEV-1  Exam-wide or security-critical failure
SEV-2  Major cohort impact
SEV-3  Limited user impact; workaround exists
SEV-4  Cosmetic or non-blocking
```

The exact escalation contacts are operational data and belong in the runbook.

---

## 81. SEV-1 response

Examples.

```text
Global start failure
Global submission failure
Cross-user data exposure
Timer corruption affecting cohort
Database integrity issue
```

Immediate actions.

```text
Protect data integrity
Freeze risky changes
Assemble incident owner
Assess rollback or failover
Communicate to pilot stakeholders
```

---

## 82. SEV-2 response

Examples.

```text
Large cohort cannot save
Large recovery failure
High worker backlog
Scorecard generation impacting core result access
```

Use bounded mitigation, traffic control where appropriate, operator support, and root-cause isolation.

---

## 83. Incident evidence

For each incident retain timeline, request IDs, attempt IDs where required, metrics, logs, deployment version, operator actions, root cause, impact, resolution, and follow-up action.

---

## 84. Communication during incident

Use predefined communication channels.

The platform does not rely on individual developer availability.

Only authoritative operational owners communicate state externally.

---

## 85. No emergency code without review

During the pilot, direct production hotfixes are minimized.

Preferred.

```text
Reproduce
Patch in branch
Test
Review
Deploy
```

Only pre-approved emergency procedures may bypass the normal path, and the action is recorded.

---

## 86. Database emergency rule

Never fix an incident by ad hoc SQL that changes academic truth unless a documented incident procedure explicitly authorizes it.

The normal rule remains Flyway owns schema, application owns business transitions, and audit records operational actions.

---

## 87. Support workflow

Support can answer.

```text
Can the student log in?
Does the user have access?
Is the schedule open?
Is the attempt prepared?
Is the session active or recoverable?
Was the response acknowledged?
Was the attempt submitted?
Is evaluation complete?
Is the result published?
```

Support tooling uses authorized read APIs.

---

## 88. Support must not

Support must not modify answer keys during a live exam, change marks on a published assessment, edit StudentResponse directly in the database, restart a timer manually, create duplicate attempts, or publish a result without authorization.

---

## 89. Pilot support script - Cannot start

For a student unable to start.

```text
Verify identity
Verify AccessGrant
Verify schedule
Verify attempt
Verify snapshot READY
Verify session and attempt state
Check error code
```

Do not immediately create a second attempt.

---

## 90. Pilot support - Save failure

For response save problems.

```text
Check browser and network
Check API status
Check attempt status
Check response save metrics
Check database health
Check retry state
Check ETag precondition behavior
```

The operator does not tell a student that a response was saved unless server evidence confirms it.

---

## 91. Pilot support - Recovery

For recovery failure.

```text
Verify active or recoverable session
Verify attempt state
Verify deadline
Verify token
Verify database state
```

Never restart an attempt just because the frontend cannot recover.

---

## 92. Pilot support - Submission

For submission issues.

```text
Check attempt state
Check deadline
Check submission endpoint
Check duplicate submit behavior
Check database persistence
```

If the attempt is already SUBMITTED, treat it as authoritative.

---

## 93. Exam completion reconciliation

After the exam reconcile eligible candidates, prepared attempts, started attempts, submitted attempts, failed attempts, evaluated attempts, and published results.

Every discrepancy needs a known explanation.

---

## 94. Count reconciliation example

For a target population.

```text
Eligible   = 5,000
Prepared   = 5,000
Started    = X
Submitted  = Y
Evaluated  = Z
Published  = W
```

Actual values are measured.

Do not infer success from frontend page views.

---

## 95. Response reconciliation

For submitted attempts verify response counts, valid response ownership, and no impossible question IDs.

Inspect outliers before publishing high-stakes results.

---

## 96. Evaluation reconciliation

Before result publication verify all intended submitted attempts evaluated, failed evaluations classified, AnswerKey consistent, and no duplicate evaluations.

If rank and percentile population is not finalized, those fields are not published.

---

## 97. Result reconciliation

Before publication verify result count, evaluation count, score ranges, unexpected nulls, and publication status.

Outlier detection flags negative, zero, or maximum anomalies, impossible percentages, and missing results.

Any rule used for anomaly detection is documented rather than treated as proof of fraud.

---

## 98. Scorecard reconciliation

If scorecards are generated, verify result count, scorecard count, generation failures, and secure retrieval.

Scorecard failure is distinguished from result failure.

---

## 99. Pilot KPI set

The pilot KPI set remains small and decision-oriented.

```text
Concurrent users
Start success
Start latency
Response-save success
Submission success
Exam failure rate
Recovery success
Evaluation completion
Result publication completion
Critical incidents
```

Full product analytics remains deferred.

---

## 100. Student experience KPI

Measure operationally start wait, save failures, recovery failures, submission failures, and unexpected disconnects.

Do not rely only on post-exam satisfaction to identify runtime correctness defects.

---

## 101. Operational KPI thresholds

Use the frozen gates as mandatory.

```text
p95 applicable API under 100 ms
Submission success greater than 99%
Exam failure less than 1%
Availability target 99.9%
```

Additional thresholds can be set from observed pilot baseline.

---

## 102. Pilot cohort selection

The pilot cohort is large enough to exercise the system.

The exact candidate count may be less than the full concurrent-user target for a first live session, but the system has passed the required scale validation before the production-like pilot proceeds to its approved scale.

---

## 103. Pilot sequencing

Recommended live sequence.

```text
Small controlled pilot
   |
   v
Pilot review
   |
   v
Larger pilot
   |
   v
Full approved cohort
```

A small successful run is not proof of concurrent-user readiness without the dedicated load-test evidence.

---

## 104. Pilot exit criteria

A live pilot is complete when exam delivered, submissions reconciled, evaluations completed, results handled, incidents documented, telemetry reviewed, and postmortem completed.

---

## 105. Post-pilot review

Review what worked, what failed, what surprised the team, capacity headroom, operator workload, student-impacting issues, security issues, and data-integrity issues.

Convert findings into bug, decision record, process change, or deferred feature rather than an informal list only.

---

## 106. Capacity headroom

Passing at exactly the threshold does not prove comfortable operation.

The post-pilot review documents observed peak CPU, peak database connections, peak WAL, peak worker queue, peak latency, peak error rate, and available headroom.

---

## 107. Database headroom

Check whether PostgreSQL approached CPU saturation, connection exhaustion, lock contention, I/O saturation, or WAL pressure.

If so, capacity remediation occurs before increasing pilot traffic.

---

## 108. Application headroom

Check CPU, heap, GC, request queues, thread pools, and container restarts.

Do not simply add instances without understanding the bottleneck.

---

## 109. Network headroom

Check bandwidth, TLS termination, load balancer, Nginx, application network, and database network.

The student payload size is also measured for question-heavy assessments.

---

## 110. Payload optimization

The assessment runtime avoids unnecessarily large payloads.

Prefer only needed fields, efficient JSON, and compressed HTTP where enabled.

Do not optimize by exposing less information than the UI requires for correct operation.

---

## 111. Caching validation

If immutable assessment and question data is cached, verify cache hit, cache miss, invalid cache entry, publication boundary, and version isolation.

Never serve stale content that violates historical or published-version semantics.

---

## 112. CDUI validation at scale

At scale verify the frontend remains engine-agnostic.

Test multiple normalized configurations.

```text
Navigation variants
Timer variants
Calculator capability
Section switching
Review behavior
Presentation variations
```

The React code responds to configuration rather than exam names.

---

## 113. ExamEngine regression matrix

At minimum test TCS_ION, IBPS, NTA, UPSC, RAILWAY, and CUSTOM configurations where the pilot's approved engine implementations are available.

The test verifies common runtime behavior plus valid policy differences.

---

## 114. No engine-specific production patch

A pilot defect does not automatically result in `if engine == X` inside common delivery or React code.

First ask whether the behavior is representable in normalized policy.

This preserves the architecture rule.

---

## 115. Versioned configuration test

Prepare two assessment versions with different configurations.

Verify Schedule A returns configuration A, Schedule B returns configuration B, and student A cannot receive B because a frontend route was reused.

---

## 116. Release compatibility test

Before deployment test database migration, old sessions, new application, new sessions, response saves, submission, and evaluation.

If old and new compatibility is not guaranteed, the deployment procedure accounts for that explicitly.

---

## 117. Security regression at release

Rerun authentication, authorization, attempt ownership, answer-key protection, cross-organization isolation, asset authorization, and score and result visibility.

Security regression is not skipped because code changes appear to be performance-only.

---

## 118. Secrets and configuration validation

Verify production configuration sources.

No secret appears in repository, Docker image, frontend bundle, logs, load-test artifact, OpenAPI examples, or support screenshots.

---

## 119. Certificate and HTTPS validation

Before pilot verify TLS active, certificate valid, HTTP redirect policy, secure cookies where used, CORS, and security headers.

---

## 120. Browser compatibility smoke test

The pilot is web-first.

Validate supported desktop browser combinations defined by the product baseline.

Test login, exam start, question render, math, asset, autosave, timer, submit, and recovery.

---

## 121. Network variability test

Simulate high latency, packet loss, brief disconnect, and reconnect.

Verify responses do not silently disappear, timer remains server-authoritative, recovery works, and retry stays bounded.

---

## 122. Browser refresh test

During question, answer entry, section switch, and timer countdown refresh the page.

Expected.

```text
Same attempt
Same snapshot
Persisted response recovered
Timer not reset
```

---

## 123. Browser sleep and wake test

Put the browser or tab to sleep where practical.

On resume verify server state remains authoritative, timer corrects, and queued mutations are handled safely.

---

## 124. Concurrent user race test

Run duplicate start, save plus submit, submit plus recovery, and duplicate submit at scale.

The goal is business correctness under race, not just HTTP-level success.

---

## 125. Pilot data isolation test

Ensure data for organization A cannot appear in organization B, even if UUIDs and request paths are guessed.

---

## 126. Audit verification

After dry run and pilot, verify audit evidence for attempt start, submission, evaluation, result publication, and operator actions.

Audit is complete enough to investigate disputed events.

---

## 127. Disputed attempt procedure

Define a procedure for student claims that an answer disappeared, timer issue, submission failed, or result is wrong.

The investigation uses attempt, responses, session, audit, logs, and evaluation rather than assumptions.

---

## 128. Disputed result procedure

For a result dispute, identify Result, Evaluation, AnswerKey, Attempt, snapshot, and review scoring calculation.

Do not edit production score rows manually.

---

## 129. Answer key change during pilot

A published AnswerKey is not changed casually after evaluation begins.

Any correction follows the new key, controlled re-evaluation, audit, and result publication policy defined by P6.

---

## 130. Question change during pilot

A published QuestionVersion used by an active AssessmentVersion is not changed.

Create a new version and preserve historical links.

---

## 131. Assessment change during pilot

A published AssessmentVersion is not modified during a live schedule.

A new AssessmentVersion is required for substantive changes.

---

## 132. Pilot schedule change

Schedule changes immediately before launch are tightly controlled.

If changed, revalidate candidate eligibility, revalidate attempt preparation, revalidate start window semantics, and rerun the relevant operational gate.

---

## 133. Pilot cancellation

Define how to handle a canceled schedule.

At minimum document candidate communication owner, attempt state policy, snapshot disposal and retention, result impact, and audit.

Direct database deletion is not used as a shortcut.

---

## 134. Pilot pause

A pause mechanism is not frozen as a universal Delivery capability.

A global pause feature is not invented during P7 merely for operations.

Use only the approved ExamEngine and CDUI policy if such behavior is already supported.

---

## 135. Go and no-go meeting

The final go or no-go review inspects load-test evidence, security evidence, backup and restore evidence, dry-run evidence, telemetry, open critical defects, runbook, rollback, and support coverage.

The decision is documented.

---

## 136. Open critical defects

Classify defects before launch.

```text
BLOCKER
HIGH
MEDIUM
LOW
```

Any blocker that threatens data integrity, submission, security, timer correctness, or cross-user isolation is a no-go until resolved or the affected functionality is explicitly removed from the pilot scope.

---

## 137. Change control during pilot

Any production change during a live pilot records why, owner, risk, version, approval, rollback, and outcome.

This avoids losing causality during incidents.

---

## 138. Post-change validation

After any emergency or scheduled production change, health, login, start, save, recovery, and submit are smoke-tested before declaring the system stable again.

---

## 139. Operational cost tracking

P7 records application instances, database size, database utilization, PgBouncer resources, worker resources, monitoring resources, storage, and network.

This creates a factual baseline for later infrastructure planning.

---

## 140. Pilot cost versus performance

Do not optimize solely for lowest infrastructure cost.

Record cost, latency, headroom, failure rate, and operator burden.

A cheaper system with insufficient headroom is not treated as equivalent to a validated pilot system.

---

## 141. Scale test report structure

Every major load test report contains objective, environment, software version, data set, workload, ramp, duration, metrics, results, errors, database evidence, application evidence, conclusion, and limitations.

---

## 142. Load-test reproducibility

The test is reproducible using same code version, same data seed, same workload definition, and same environment definition.

Randomized behavior uses a recorded seed where applicable.

---

## 143. Performance regression gate

After any optimization, rerun baseline, compare p50, p95, p99, compare error rates, compare database impact, and compare CPU and memory.

Do not accept a faster endpoint that introduces data-integrity defects.

---

## 144. Soak-test exit criteria

A soak test is complete when there is no unbounded memory growth, no connection leak, no runaway queue, no sustained increasing latency, and no unexplained error growth.

The exact numerical thresholds are tied to the measured baseline.

---

## 145. Scale-test exit criteria

P7 scale validation is complete when the agreed representative test demonstrates 5,000 concurrent-user support, start burst within applicable latency target, submission success greater than 99%, exam failure less than 1%, no critical data corruption, no critical security defect, and recovery works.

---

## 146. Live pilot exit criteria

The live pilot is operationally successful when the approved cohort completed, submissions reconciled, evaluation completed, results handled, no unresolved critical incident, evidence archived, and post-pilot review completed.

---

## 147. Pilot evidence archive

Archive release artifacts, load tests, monitoring exports, incident reports, backup and restore test, security results, data reconciliation, pilot metrics, operator checklist, go and no-go record, and post-pilot review.

This evidence becomes part of the platform's operational history.

---

## 148. Post-pilot architecture review

Review whether the pilot revealed genuine architectural limits.

Possible outcomes are no architecture change, performance tuning, decision record amendment, schema amendment, API amendment, or future feature backlog.

Architectural changes are not created simply because an operator preferred a different workflow.

---

## 149. Post-pilot deferred scope review

Only after pilot evidence should the team reprioritize Full Analytics, Commerce, Communication, Full Administration, and AI capabilities.

The pilot is not used as an excuse to partially build every deferred module.

---

## 150. Pilot lessons to backlog

Every significant lesson is classified as bug, performance issue, security issue, operational issue, UX issue, missing contract, or future feature.

Then assign an owner and priority.

---

## 151. Agent role in P7

Automation may assist with load-test scripts, data generation, test analysis, log classification, dashboard queries, reconciliation reports, runbook checks, and regression tests.

Humans remain responsible for go and no-go, security approval, production change approval, incident command, and result publication decisions.

---

## 152. Agent guardrails

Automation does not autonomously modify production academic data, publish results, change AnswerKeys, change published assessments, disable security controls, increase production traffic, or execute arbitrary production SQL.

Agent output is reviewed at the appropriate operational boundary.

---

## 153. Scale-test task breakdown

```text
P7-01  Build load-test data
P7-02  Build test environment
P7-03  Validate release candidate
P7-04  Run 100-user baseline
P7-05  Run 500-user baseline
P7-06  Run 1,000-user test
P7-07  Run 2,000-user test
P7-08  Run 3,000-user test
P7-09  Run 4,000-user test
P7-10  Run 5,000-user test
P7-11  Run autosave burst
P7-12  Run submission burst
P7-13  Run recovery burst
P7-14  Run soak test
P7-15  Run failure injection
P7-16  Verify data integrity
P7-17  Verify security
P7-18  Validate dashboards and alerts
P7-19  Full dress rehearsal
P7-20  Live pilot
P7-21  Reconcile results
P7-22  Archive evidence
P7-23  Post-pilot review
P7-24  Prepare next-phase recommendation
```

---

## 154. Recommended execution prompt

```text
You are executing P7 Scale Validation and Live Pilot for the Smart
Competitive Examination Platform.

Read the full specification package before changing the environment.

P7 owns:
Scale validation
Operational readiness
Pilot dry runs
Live pilot execution
Evidence collection
Reconciliation
Post-pilot review

NON-NEGOTIABLE:
1.  Do not invent business features during the load test.
2.  Do not add tables for test telemetry.
3.  Do not change the frozen API just to simplify load generation.
4.  Do not generate runtime snapshots from Start.
5.  Do not bypass AccessGrant.
6.  Do not expose correct answers.
7.  Do not trust client timing.
8.  Do not alter published assessments or questions.
9.  Do not alter published AnswerKeys.
10. Do not modify official results with ad hoc SQL.
11. Do not hard-code exam names into runtime behavior.
12. Do not treat Redis as authoritative.
13. Do not declare success from average latency alone.
14. Verify start burst, autosave, submission and recovery separately.
15. Verify business-state correctness after load.
16. Retain reproducible test evidence.
17. Stop and escalate on data-integrity or security failures.
18. Humans own go and no-go and production approvals.
19. Verify stale-write protection under concurrent autosave.
20. Verify rank and percentile recomputation after result publication.

For each test:
- Record release version.
- Record environment.
- Record data seed.
- Record workload.
- Run focused validation.
- Capture metrics.
- Capture errors.
- Verify database integrity.
- Report findings.
```

---

## 155. Review questions

```text
Did the test use prepared snapshots?
Did Start create zero snapshot child rows?
Were 5,000 users actually concurrent?
Were submissions measured durably?
Was exam failure defined before the test?
Was p95 measured on applicable APIs?
Was p99 retained?
Was PostgreSQL health observed?
Was PgBouncer observed?
Was recovery tested?
Was data reconciled after the test?
Were security checks run under concurrency?
Was any manual production data mutation performed?
Can the load test be reproduced?
Were stale-write preconditions exercised?
Was rank and percentile recomputation validated?
```

---

## 156. Operational checklist - T minus seven days

```text
[ ] Release candidate identified
[ ] Environment stable
[ ] Database backup verified
[ ] Restore test complete
[ ] Load test evidence complete
[ ] Security regression complete
[ ] Runbook reviewed
[ ] Operator roles assigned
[ ] Support coverage assigned
[ ] Incident contacts confirmed
[ ] Candidate list frozen
[ ] Assessment version frozen
```

---

## 157. Operational checklist - T minus one day

```text
[ ] Candidate AccessGrants verified
[ ] Schedule verified
[ ] Attempts prepared
[ ] Snapshots READY
[ ] Worker health green
[ ] Database health green
[ ] PgBouncer healthy
[ ] Redis healthy
[ ] Dashboards visible
[ ] Alerts tested
[ ] Backup current
[ ] Support team ready
```

---

## 158. Operational checklist - T minus thirty minutes

```text
[ ] No active deployment
[ ] No schema migration running
[ ] Snapshot coverage confirmed
[ ] Candidate count confirmed
[ ] Infrastructure capacity confirmed
[ ] Incident owner online
[ ] Operator dashboard open
[ ] Application smoke test green
```

---

## 159. Operational checklist - T plus thirty minutes

```text
[ ] Starts stable
[ ] Save errors acceptable
[ ] Active sessions expected
[ ] Database connections healthy
[ ] Worker queues healthy
[ ] No security incident
```

Do not declare the exam healthy solely because students reached the first question.

---

## 160. Operational checklist - Submission window

```text
[ ] Submission rate monitored
[ ] p95 and p99 monitored
[ ] Database write pressure monitored
[ ] Error rate monitored
[ ] Duplicate-submit behavior monitored
[ ] Evaluation queue monitored
```

---

## 161. Operational checklist - T plus one hour

```text
[ ] Submission reconciliation started
[ ] Evaluation progressing
[ ] Rank and percentile recomputation progressing
[ ] Critical incidents classified
[ ] Backups retained
[ ] Evidence collection running
```

---

## 162. Reconciliation checklist

```text
[ ] Eligible users
[ ] Prepared attempts
[ ] Started attempts
[ ] Submitted attempts
[ ] Failed submissions
[ ] Evaluations
[ ] Results
[ ] Published results
[ ] Scorecards
[ ] Incident count
```

---

## 163. Final acceptance matrix

| Area | Required evidence |
|---|---|
| Identity | Login and security tests |
| Access | AccessGrant validation |
| Assessment | Published exact version |
| Delivery | Start, recovery, autosave tests |
| Snapshot | READY and deterministic |
| Performance | Concurrent-user evidence |
| Submission | Greater than 99% success |
| Failure | Less than 1% exam failure |
| API latency | Under 100 ms p95 for applicable APIs |
| Database | Capacity, lock, and WAL evidence |
| Security | Regression and concurrency checks |
| Recovery | Successful recovery evidence |
| Evaluation | Reconciliation |
| Results | Publication reconciliation |
| Operations | Runbook and dashboards |
| Backup | Restore evidence |
| Rollback | Tested and approved plan |

---

## 164. Non-functional exit gate

The frozen platform baseline requires.

```text
99.9% availability target
5,000 concurrent users
Under 100 ms p95 applicable API latency
Greater than 99% successful submissions
Less than 1% exam failure rate
```

P7 reports measured results rather than stating that the requirement is simply satisfied.

---

## 165. Security exit gate

```text
[ ] Authentication secure
[ ] Authorization enforced
[ ] No cross-user access
[ ] No cross-organization access
[ ] No correct-answer leakage
[ ] No asset path leakage
[ ] Privileged APIs protected
[ ] Audit verified
[ ] Secrets protected
[ ] Stale-write protection enforced
```

---

## 166. Data integrity exit gate

```text
[ ] Snapshot integrity
[ ] Response integrity
[ ] Submission integrity
[ ] Evaluation integrity
[ ] Result integrity
[ ] Publication integrity
[ ] Rank and percentile integrity
[ ] No unexplained count mismatch
```

---

## 167. Operations exit gate

```text
[ ] Runbook
[ ] Dashboards
[ ] Alerts
[ ] On-call ownership
[ ] Support procedure
[ ] Incident procedure
[ ] Backup
[ ] Restore
[ ] Rollback
```

---

## 168. Pilot exit gate

```text
[ ] Approved live cohort completed
[ ] Submission reconciliation complete
[ ] Evaluation reconciliation complete
[ ] Result handling complete
[ ] Critical incidents resolved or formally accepted
[ ] Evidence archived
[ ] Post-pilot review complete
```

---

## 169. Architecture decision gate

After the pilot, make an evidence-based decision.

```text
Continue pilot
Expand pilot
Tune infrastructure
Change architecture
Amend API
Amend schema
Prioritize deferred module
```

No architecture change is made solely because it feels theoretically cleaner.

---

## 170. Handoff to post-pilot

P7 produces validated scale profile, capacity baseline, known limits, pilot incident record, student experience findings, operational workload, security findings, reliability findings, cost baseline, architecture decisions, and next-phase priorities.

---

## 171. Deferred modules handoff

Post-pilot prioritization may evaluate Full Analytics, Commerce, Communication, Full Administration, and the AI roadmap.

Their implementation remains subject to evidence from the live pilot.

---

## 172. Final engineering principle

> Do not call the platform production-ready because it works for one user. Call it pilot-ready only after the system survives the workload, failure modes, and operational procedures that the pilot will actually impose.

The validation chain.

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
Ramp from small cohort to concurrent-user target
 |
 v
Failure injection
 |
 v
Data reconciliation
 |
 v
Dress rehearsal
 |
 v
Live pilot
 |
 v
Evidence
 |
 v
Post-pilot decision
```

---

## 173. No surprises rule

During P7, do not solve an observed scale problem by silently changing schema, API, business rules, snapshot model, answer-key model, attempt model, or ExamEngine contract without documenting the change and rerunning the affected acceptance gates.

---

## 174. Release evidence package

Retain load-test scripts, data seed and configuration, release versions, infrastructure configuration, metrics exports, logs, database performance evidence, failure-injection evidence, security evidence, backup and restore evidence, dry-run evidence, pilot runbook, incident reports, reconciliation report, go and no-go decision, and post-pilot review.

---

## 175. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P7 — Scale Validation and Live Pilot.

**Next implementation document:** P8 — Full Analytics and Insights.