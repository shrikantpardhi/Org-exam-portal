    # Smart Competitive Examination Platform
## P8 — Full Analytics & Insights Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P8 — Full Analytics & Insights
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, P4 Blueprint and Assessment Management, P5 Assessment Delivery and Runtime Examination Engine, P6 Evaluation and Results, P7 Scale Validation and Live Pilot
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**API:** REST + JSON, `/api/v1`
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Architects

---

## 1. Purpose

This document converts the Full Analytics domain into an implementation-ready engineering specification.

P8 transforms transactional examination data into actionable insights for students, assessments, questions, topics, and the organization.

The authoritative chain remains.

```text
StudentAttempt
StudentResponse
Evaluation
Result
    |
    v
Analytics
```

P8 answers:

```text
How is the student performing?
Where are the weak areas?
Which topics are improving?
Which assessments are difficult?
Which questions are too easy or too hard or problematic?
How much time is being spent?
How is an organization performing?
What patterns are visible across attempts?
```

---

## 2. Source-of-truth hierarchy

Read these documents before P8 implementation:

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
- This document

The earlier documents already freeze the analytics tables, the source-of-truth principle, and the recalculation model. This document does not modify those decisions.

---

## 3. Analytics principle

> Analytics explains transactional history; it does not replace it.

Therefore.

```text
Transactional data
       |
       v
Aggregation and derivation
       |
       v
Analytics tables
       |
       v
Read APIs
       |
       v
Dashboards
```

If an analytics row is lost or corrupted, the platform can rebuild it from the authoritative source data.

---

## 4. P8 ownership

P8 owns:

```text
Analytics calculation
Analytics projections
Aggregation jobs
Student insights
Assessment insights
Question analytics
Topic analytics
Organization analytics
Dashboard query models
Analytics refresh orchestration
Analytics validation
```

P8 does not own:

```text
Attempts
Responses
Evaluation
Official result
Scorecard
Assessment authoring
Delivery
Commerce
Communication
```

---

## 5. Frozen analytics tables

The frozen schema contains exactly five analytics tables.

```text
student_analytics
assessment_analytics
question_analytics
topic_analytics
organization_analytics
```

No dedicated event table, fact table, analytics dimension table, time-series database, data warehouse, or search index is frozen.

P8 does not introduce any of those merely because they are analytically convenient.

---

## 6. Frozen schemas

### 6.1 Table: student_analytics

```sql
student_analytics
-----------------
id UUID PK
user_id UUID FK
assessments_attempted INTEGER
average_score NUMERIC(8,2)
accuracy NUMERIC(8,2)
average_time NUMERIC(10,2)
last_calculated_on TIMESTAMPTZ
```

Unique: `UNIQUE(user_id)`.

Index: `idx_student_analytics_user_id`.

### 6.2 Table: assessment_analytics

```sql
assessment_analytics
--------------------
id UUID PK
assessment_id UUID FK
total_attempts INTEGER
average_score NUMERIC(8,2)
highest_score NUMERIC(8,2)
lowest_score NUMERIC(8,2)
last_calculated_on TIMESTAMPTZ
```

Unique: `UNIQUE(assessment_id)`.

### 6.3 Table: question_analytics

```sql
question_analytics
------------------
id UUID PK
question_id UUID FK
total_attempts INTEGER
correct_attempts INTEGER
incorrect_attempts INTEGER
average_time NUMERIC(10,2)
difficulty_index NUMERIC(8,4)
last_calculated_on TIMESTAMPTZ
```

Unique: `UNIQUE(question_id)`.

### 6.4 Table: topic_analytics

```sql
topic_analytics
---------------
id UUID PK
topic_id UUID FK
total_attempts INTEGER
average_score NUMERIC(8,2)
accuracy NUMERIC(8,2)
last_calculated_on TIMESTAMPTZ
```

Unique: `UNIQUE(topic_id)`.

### 6.5 Table: organization_analytics

```sql
organization_analytics
----------------------
id UUID PK
organization_id UUID FK
total_students INTEGER
total_assessments INTEGER
total_attempts INTEGER
active_users INTEGER
last_calculated_on TIMESTAMPTZ
```

Unique: `UNIQUE(organization_id)`.

---

## 7. Current analytics limitation

The frozen tables are intentionally compact.

They are sufficient for current aggregate dashboards but do not directly persist:

```text
Per-assessment-per-student history
Per-topic-per-attempt history
Per-question-per-attempt history
Daily, monthly, or trend buckets
Section analytics rows
Assessment-version analytics
Schedule analytics
Cohort analytics
Historical aggregate snapshots
```

P8 distinguishes between:

```text
Supported directly by frozen tables
Derived dynamically from transactional data
Future analytics model requiring schema amendment
```

---

## 8. Analytics grain rule

Every metric has an explicit grain.

Examples.

```text
StudentAnalytics       -> one current aggregate row per user
AssessmentAnalytics    -> one current aggregate row per assessment
QuestionAnalytics      -> one current aggregate row per question
TopicAnalytics         -> one current aggregate row per topic
OrganizationAnalytics  -> one current aggregate row per organization
```

An aggregate row is not silently interpreted as historical time-series data.

---

## 9. Current versus historical analytics

The frozen tables contain `last_calculated_on` but no metric-effective timestamp history.

P8 treats them as current aggregate projections unless a dedicated read query derives historical trends directly from transactional timestamps.

A request for durable historical analytics snapshots requires a formal analytics schema amendment.

---

## 10. Recalculability contract

Every stored analytics row has a deterministic rebuild process.

Conceptually.

```text
Delete or replace aggregate
        |
        v
Read source transactional data
        |
        v
Recalculate
        |
        v
Upsert analytics row
```

The production implementation may use incremental updates for efficiency, but a full rebuild path exists.

---

## 11. Incremental versus full rebuild

P8 supports both.

```text
Incremental refresh   -> optimized for normal operation
Full recalculation    -> correction, backfill, incident recovery,
                         algorithm changes, migration, validation
```

---

## 12. Analytics source tables

Primary sources.

```text
user
assessment
assessment_version
assessment_question
assessment_schedule
student_attempt
attempt_question
attempt_question_option
student_response
question
question_version
exam
subject
chapter
topic
sub_topic
evaluation
result
```

Question-level correctness depends on Evaluation and AnswerKeyDetail truth, not on frontend state.

---

## 13. Analytics source priority

When information conflicts, use.

```text
Result and Evaluation  -> official score and correctness
StudentAttempt and Response -> runtime interaction
AttemptQuestion        -> exact question assignment and scoring
AssessmentVersion      -> assessment definition
Academic masters       -> topic hierarchy
```

Analytics does not infer official facts from UI telemetry.

---

## 14. Student analytics objective

Student analytics provides attempt activity, average score, accuracy, average time, progress, section performance, topic performance, weak areas, strength areas, and question efficiency.

Only metrics supported by the current source model are persisted in StudentAnalytics.

---

## 15. Student attempt count

Frozen field: `assessments_attempted`.

This counts qualifying StudentAttempts according to an explicit inclusion rule.

The inclusion rule is documented and includes the following in scope.

```text
Included   -> EVALUATED attempts with a current Evaluation
Included   -> Published-result attempts where required by product
Excluded   -> NOT_STARTED attempts
Excluded   -> IN_PROGRESS attempts
Excluded   -> SUBMITTED but not yet evaluated attempts
```

The rule is centralized in the analytics service rather than duplicated across dashboards.

---

## 16. Student average score

Frozen field: `average_score`.

The population includes evaluated attempts whose current Result is on record, subject to the student metric population rule.

The metric is not mixed silently between practice and exam unless the product explicitly approves that combination.

---

## 17. Student accuracy

Frozen field: `accuracy`.

Formula.

```text
accuracy = correct_count / (correct_count + incorrect_count) * 100
```

Unanswered, dropped, and unsupported questions are excluded from the denominator.

The result is bounded to [0, 100].

---

## 18. Student average time

Frozen field: `average_time`.

Source data: `student_response.time_spent_seconds`.

The metric includes only answered questions unless the product explicitly approves a broader population.

Extreme values caused by known defects are excluded via documented and reproducible rules.

---

## 19. Student progress

Progress tracking is derived from transactional history through read queries or a future analytics projection.

A new progress column is not added silently.

Progress is derived from a documented formula such as completed qualifying assessments or attempted versus target.

---

## 20. Weak areas

Weak areas are derived from topic accuracy, question outcomes, time efficiency, and assessment performance.

The exact threshold is configurable at the application layer and documented.

Weak-area detection does not turn a chosen threshold into an unexplained official classification.

---

## 21. Strength areas

Strength areas may be exposed as factual metrics such as highest accuracy topics, fastest reliable topics, or strongest assessment sections without turning a chosen threshold into an official classification.

---

## 22. Assessment analytics objective

Frozen AssessmentAnalytics supports total attempts, average score, highest score, and lowest score.

Additional metrics such as completion rate, average time, question difficulty, and section performance are supported dynamically where the frozen aggregate storage does not directly represent them.

---

## 23. Assessment attempt count

Frozen field: `total_attempts`.

The same attempt-inclusion policy is applied as in student analytics.

---

## 24. Assessment completion rate

Completion rate is calculated dynamically from submitted or evaluable attempts divided by started or eligible population only after the exact denominator is approved.

No new column is added silently.

---

## 25. Assessment average score

Frozen field: `average_score`.

The value is computed from official Evaluation and Result marks, not from client-calculated scores or raw response heuristics.

---

## 26. Assessment highest score

Frozen field: `highest_score`.

Derived from qualifying evaluated attempts only.

The inclusion rule matches the average-score population unless a documented product policy states otherwise.

---

## 27. Assessment lowest score

Frozen field: `lowest_score`.

The calculation excludes invalid and incomplete evaluations.

Whether a negative final score is possible depends on the P6 scoring contract.

---

## 28. Question analytics objective

Frozen QuestionAnalytics supports total_attempts, correct_attempts, incorrect_attempts, average_time, and difficulty_index.

The product roadmap also mentions correct percentage, incorrect percentage, and skipped percentage. These are derived from the stored counts rather than added as redundant columns.

---

## 29. Question attempt population

Question analytics counts actual evaluated QuestionVersion occurrences from attempt snapshots.

Question bank views, preview views, and editor views are not counted as examination attempts.

---

## 30. Question identity mapping

Frozen QuestionAnalytics references `question_id`, while Evaluation operates on QuestionVersion.

P8 aggregates versioned question activity back to the parent Question where appropriate.

A published Question may have multiple versions across historical assessments.

---

## 31. Question correct count

`correct_attempts` derives from official evaluation outcomes.

`question_option.is_correct` alone is not treated as proof of a student outcome.

---

## 32. Question incorrect count

`incorrect_attempts` distinguishes incorrect from unanswered, invalid, unsupported, and bonus or dropped.

Only outcomes defined as incorrect increment `incorrect_attempts`.

---

## 33. Question skipped count

The roadmap calls for skipped percentage. The frozen table has no skipped count.

Skipped status is derived from AttemptQuestion population minus qualifying answered responses according to the approved QuestionTemplate response semantics.

---

## 34. Question average time

Use `student_response.time_spent_seconds` with the approved time population.

Browser navigation timestamps are not interpreted as official time unless they are backed by the frozen persisted response data.

---

## 35. Difficulty index

Frozen schema contains `difficulty_index NUMERIC(8,4)`.

The formula is fixed and documented as follows.

```text
difficulty_index = 1 - (correct_attempts / total_attempts)
```

Where `total_attempts` counts evaluated attempts that produced a correct or incorrect outcome.

Unanswered questions are excluded from the denominator to prevent empty responses from inflating difficulty.

The value is bounded to [0, 1]. Higher means harder.

Any future change to the formula is a versioned analytics decision, not a silent redefinition.

---

## 36. Topic analytics objective

Frozen TopicAnalytics supports total_attempts, average_score, and accuracy.

Progress and weak areas are derived in read models where the frozen schema does not directly represent them.

---

## 37. Topic population

Topic analytics maps evaluated QuestionVersions through the Question taxonomy.

The taxonomy mapping is authoritative through the Question table and Academic masters.

---

## 38. Topic accuracy

Topic accuracy is calculated from question outcomes associated with the topic.

The exact denominator excludes unanswered, unsupported, and non-evaluable questions according to the final template and scoring contract.

---

## 39. Topic average score

`average_score` represents the average obtained marks for questions or attempts associated with the topic.

The unit is documented as marks.

A normalized percentage is derived separately where required.

---

## 40. Organization analytics objective

Frozen OrganizationAnalytics supports total_students, total_assessments, total_attempts, and active_users.

Revenue analytics remains Commerce-owned and is not part of P8.

---

## 41. Organization student count

`total_students` counts active users with the Student role according to Identity role and permission data.

Archived users, inactive users, and users without the Student role are excluded.

---

## 42. Organization assessment count

`total_assessments` counts qualifying Assessment records.

Draft assessments are excluded. Archived assessments are excluded from the active count.

The grain is the Assessment, not the AssessmentVersion.

---

## 43. Organization attempt count

`total_attempts` uses the approved attempt-inclusion policy and avoids double counting retries.

---

## 44. Active user definition

`active_users` uses the following documented definition.

```text
A user is active if the user has at least one StudentAttempt
with started_on within the last 30 days.
```

The exact window is configurable and stored as part of the analytics configuration rather than hard-coded.

---

## 45. Section analytics

Section analytics is derived as a dynamic read aggregation from AssessmentQuestion.section_name, AttemptQuestion.section_name, StudentResponse.time_spent_seconds, and evaluation outcomes.

No dedicated section analytics table is introduced unless a future schema amendment explicitly approves one.

---

## 46. Section score

Section score is derived from AttemptQuestion marks and evaluation outcomes for a given attempt or assessment.

The exact API shape and persistent model follow the frozen schema.

---

## 47. Section accuracy

Section accuracy aggregates question outcomes by section_name while preserving assessment version and attempt context.

A global section name is not guaranteed unique across exams.

---

## 48. Section time

Section time is derived from question-level timing and section assignment.

It is not equivalent to an independently persisted section timer.

---

## 49. Time analytics boundary

Three distinct concepts remain separate.

```text
Client display time
Persisted question time
Server authoritative attempt deadline
```

Analytics uses persisted timing fields for historical metrics.

---

## 50. Student dashboard scope

The student dashboard exposes attempt count, average score, accuracy, average time, recent assessments, section performance, topic performance, progress, and weak areas.

Only published and authorized result data is used for official performance metrics.

---

## 51. Student dashboard freshness

Analytics may be slightly delayed because it is derived.

The UI displays the analytics refresh timestamp using `last_calculated_on` where relevant.

---

## 52. Student dashboard empty state

For a student with no qualifying evaluated attempts, the UI shows a clear empty state rather than zero percent accuracy or zero average score unless those values are explicitly defined as meaningful.

---

## 53. Assessment dashboard scope

Assessment dashboard includes total attempts, average score, highest score, lowest score, completion rate, average time, section analysis, and question difficulty.

Some metrics are derived dynamically because the frozen table is intentionally compact.

---

## 54. Question dashboard scope

Question dashboard includes total attempts, correct percentage, incorrect percentage, skipped percentage, average response time, and difficulty indicator.

Question identity is parent Question-level unless a user explicitly filters to a version.

---

## 55. Topic dashboard scope

Topic dashboard includes attempt count, average score, accuracy, progress, and weak areas.

The topic hierarchy comes from the Academic domain.

---

## 56. Organization dashboard scope

Organization dashboard includes students, assessments, attempts, active users, assessment participation, and performance aggregates.

Revenue widgets remain Commerce-owned and deferred.

---

## 57. Analytics API strategy

The analytics API is read-oriented and uses a compact set of endpoints.

Proposed endpoints.

```http
GET /api/v1/analytics/student
GET /api/v1/analytics/student/assessments
GET /api/v1/analytics/student/topics
GET /api/v1/analytics/assessments/{assessmentId}
GET /api/v1/analytics/assessments/{assessmentId}/sections
GET /api/v1/analytics/assessments/{assessmentId}/questions
GET /api/v1/analytics/questions/{questionId}
GET /api/v1/analytics/topics/{topicId}
GET /api/v1/analytics/organization
```

These are the frozen endpoints. Additional variants require explicit contract review.

---

## 58. Read-only analytics API

Analytics APIs are read-oriented.

They do not expose:

```http
PUT /analytics/{id}
POST /analytics/{id}/recalculate
DELETE /analytics/{id}
```

to normal students.

Recalculation is a controlled operational function, not public student CRUD.

---

## 59. Analytics filters

Supported filters.

```text
assessmentId
assessmentVersionId
date range
topicId
subjectId
examId
```

Each filter maps to actual source relationships.

Filters for fields that do not exist in the current model are not added.

---

## 60. Pagination

Large analytics lists are paginated.

Examples.

```text
Question analytics
Assessment analytics
Student result analytics
Topic lists
```

Unbounded aggregates are not loaded into browser memory.

---

## 61. Sorting

Sorting may include score, accuracy, time, attempt count, and difficulty.

Only sorts backed by efficient database queries or bounded computed datasets are exposed.

---

## 62. Caching analytics

Analytics is a strong cache candidate.

Safe cached values.

```text
Current aggregate analytics
Dashboard summaries
Published-result-derived metrics
```

Cache keys include relevant scope.

```text
Organization
User
Assessment
Topic
Version where applicable
```

---

## 63. Cache invalidation

When a new Evaluation or Result becomes authoritative, relevant analytics cache may become stale.

P8 prefers asynchronous refresh rather than making result publication wait for every dashboard aggregate.

---

## 64. Analytics refresh model

Recommended.

```text
Evaluation or result completion
        |
        v
Analytics refresh signal
        |
        v
Bounded background aggregation
        |
        v
Upsert analytics
        |
        v
Cache invalidation
```

The event and queue mechanism reuses existing infrastructure and does not require a new analytics event table.

---

## 65. Full rebuild job

Provide an operator-controlled full rebuild path.

Conceptually.

```text
Rebuild student analytics
Rebuild assessment analytics
Rebuild question analytics
Rebuild topic analytics
Rebuild organization analytics
```

The job is resumable and bounded where practical.

---

## 66. Incremental refresh job

Incremental refresh uses changed transactional records.

Avoid recalculating every organization after one student's result.

Scope work to the affected student, assessment, questions, topics, and organization.

---

## 67. Recalculation isolation

Analytics jobs do not lock large portions of transactional data for long durations.

Use read-only source queries, bounded transactions, and incremental upserts.

---

## 68. Analytics consistency model

Analytics is eventually consistent.

The UI tolerates result just published and analytics not yet refreshed.

Analytics is not claimed to be transactionally identical at every instant.

---

## 69. Freshness indicator

Where useful, expose `lastCalculatedOn` and possibly a status such as CURRENT, REFRESHING, or STALE.

The exact status field is not frozen in the database and remains an API or view concept.

---

## 70. Stale data threshold

The source does not define a freshness SLA.

P8 records an operational target.

```text
Student dashboard within configured minutes
Assessment dashboard within configured minutes
```

The numerical SLA is set from actual workload and product expectations.

---

## 71. Privacy boundary

Analytics does not expose more individual-level detail than the caller is authorized to see.

Students see their own metrics.

Staff see authorized organization and assessment metrics.

Admins see authorized broader metrics.

---

## 72. Aggregation privacy

Organization dashboards prefer aggregates.

Individual student performance is not returned inside a broad organization summary unless the caller has the required permission and the endpoint explicitly requests it.

---

## 73. Cross-organization isolation

Every analytics query is organization-scoped.

Aggregate tables themselves do not provide isolation. Application authorization is enforced.

---

## 74. Academic taxonomy dependency

Topic analytics depends on reliable mapping from Question to Exam, Subject, Chapter, Topic, and SubTopic.

The taxonomy mapping is authoritative in P3. Analytics does not guess taxonomy from names.

---

## 75. Question version aggregation

Question analytics aggregates historical outcomes carefully.

A version-level correction does not rewrite historical outcomes belonging to an older immutable version.

---

## 76. Historical question comparability

A parent Question may represent multiple versions.

Question-level metrics document whether they aggregate all historical versions, the current published version, or a selected version.

The frozen schema only supports parent-level aggregate storage.

---

## 77. Assessment version aggregation

AssessmentAnalytics references `assessment_id`, not `assessment_version_id`.

Version-specific analytics is derived as a dynamic read model unless the schema is formally amended.

---

## 78. Schedule analytics

Schedule-specific dashboards are derived from assessment_schedule, assessment_session, and student_attempt through authorized queries.

ScheduleAnalytics is not added without an architecture decision.

---

## 79. Practice versus exam analytics

Because StudentAttempt contains immutable `attempt_mode`, analytics can filter by PRACTICE and EXAM where the query contract supports it.

Practice and exam metrics are not combined silently when the distinction materially changes interpretation.

---

## 80. Exam framework analytics

The platform supports multiple ExamEngine types.

Analytics does not rely on hard-coded exam names.

Where engine-level filtering is needed, it comes from `assessment_version.engine_type`.

---

## 81. Performance trend

A trend requires a time axis.

The frozen StudentAnalytics table does not store a time series.

A trend is derived dynamically from StudentAttempt timestamps and Result state until a formal trend schema is approved.

---

## 82. Daily and weekly trend

Daily and weekly tables are not created without formal schema review.

P8 uses SQL aggregation over transactional timestamps for small and controlled ranges where performance permits.

---

## 83. Trend performance boundary

If dynamic trend queries become too expensive, document query latency, source row count, frequency, and cache hit rate, then propose an evidence-based materialization strategy.

---

## 84. Recommendation boundary

AI-driven personalized study recommendations and performance insights remain future work.

P8 Full Analytics exposes factual insights such as topic accuracy, question time, and recent score trend but does not implement an AI recommendation engine.

---

## 85. Rule-based insight model

Safe deterministic insights include accuracy below configured threshold, time above configured threshold, recent score below personal baseline, and topic attempts with insufficient sample size.

Thresholds are explicit and configurable.

---

## 86. Sample size guardrails

Strong statements are not made from tiny samples.

A topic accuracy metric from a single answered question is not treated as robust.

The analytics API may expose a sample size as a view-level value without adding it to frozen tables.

---

## 87. Question health signals

Question analytics supports factual signals such as high incorrect rate, high skip rate, unusually long response time, and very low attempt count.

These are presented as metrics, not automatically labeled as defective questions.

---

## 88. Difficulty versus quality

High difficulty is not equated with bad question. The frozen QuestionAnalytics table provides only a difficulty indicator, not a quality verdict.

---

## 89. Assessment difficulty

The source mentions question difficulty, not overall assessment difficulty.

P8 does not invent a new assessment difficulty field.

---

## 90. Data completeness

Analytics calculations distinguish no data, zero, and not applicable.

Null or absence is not turned into zero without explicit semantics.

---

## 91. Analytics calculation versioning

Because formulas may evolve, the application configuration and code carry a traceable analytics calculation version.

The source does not freeze a database column for this. Historical formula provenance requires an explicit schema amendment if needed later.

---

## 92. Formula changes

When a metric formula changes, incremental refresh may produce mixed populations.

Therefore the platform supports full recalculation after a material formula change.

---

## 93. Backfill strategy

When new transactional data becomes available, identify affected grain, recalculate, upsert, and validate counts.

Individual aggregate rows are not manually patched.

---

## 94. Analytics job scheduling

Background jobs may include near-real-time incremental refresh, scheduled aggregate refresh, full nightly rebuild, and manual rebuild.

The exact cadence is an operational decision.

---

## 95. Job idempotency

A repeated analytics job produces the same current aggregate state.

Counters are not incremented blindly during retries. Recompute and upsert semantics are preferred.

---

## 96. Analytics upsert

For each frozen row, `UNIQUE(entity_id)` supports upsert semantics.

Conceptual flow.

```text
Calculate aggregate
    |
    v
INSERT ... ON CONFLICT
    |
    v
Replace current values
```

---

## 97. Avoid increment-only counters

Blindly incrementing counters such as total_attempts = total_attempts + 1 is avoided without reconciliation and rebuild support.

Corrections, retries, re-evaluations, and data repair can otherwise permanently drift the aggregate.

---

## 98. Student analytics refresh example

Conceptually.

```text
1. Identify qualifying attempts.
2. Aggregate official scores.
3. Aggregate qualifying question outcomes.
4. Aggregate response time.
5. Upsert StudentAnalytics.
6. Mark last_calculated_on.
```

---

## 99. Assessment analytics refresh example

Conceptually.

```text
1. Identify qualifying attempts for the Assessment.
2. Aggregate official Result scores.
3. Calculate count, min, max, and average.
4. Upsert AssessmentAnalytics.
5. Mark last_calculated_on.
```

---

## 100. Question analytics refresh example

Conceptually.

```text
1. Identify evaluated snapshot occurrences.
2. Map QuestionVersion to Question.
3. Classify outcomes from Evaluation.
4. Aggregate count and time.
5. Calculate difficulty_index.
6. Upsert QuestionAnalytics.
```

---

## 101. Topic analytics refresh example

Conceptually.

```text
1. Identify evaluated question outcomes.
2. Resolve topic taxonomy.
3. Aggregate attempts, accuracy, and score.
4. Upsert TopicAnalytics.
```

If taxonomy is incomplete, the affected data is reported as unmapped rather than guessed.

---

## 102. Organization analytics refresh example

Conceptually.

```text
1. Count qualifying students.
2. Count qualifying assessments.
3. Count qualifying attempts.
4. Calculate active-user metric.
5. Upsert OrganizationAnalytics.
```

---

## 103. Refresh dependency ordering

Recommended refresh order.

```text
Question and topic
      |
      v
Assessment and student
      |
      v
Organization
```

This is an operational optimization, not a transactional dependency.

---

## 104. Analytics repository design

Use projection queries such as StudentAnalyticsProjection, AssessmentAnalyticsProjection, QuestionAnalyticsProjection, TopicAnalyticsProjection, and OrganizationAnalyticsProjection.

JPA entities are not exposed directly.

---

## 105. Analytics service design

Recommended services.

```text
StudentAnalyticsService
AssessmentAnalyticsService
QuestionAnalyticsService
TopicAnalyticsService
OrganizationAnalyticsService
AnalyticsRefreshService
AnalyticsRebuildService
AnalyticsQueryService
```

---

## 106. Analytics worker design

Recommended worker responsibilities.

```text
Consume refresh request
Determine affected grains
Run bounded aggregation
Upsert current rows
Invalidate cache
Emit metrics
```

Use the existing background-job infrastructure.

---

## 107. API security

Analytics endpoints use JWT, RBAC, organization scope, and resource ownership.

Student analytics is scoped to the authenticated student.

---

## 108. Staff analytics authorization

Staff access is limited to the organization and permissions assigned in Identity.

`if staff then all analytics` is not an authorization design.

Authorization remains permission-driven.

---

## 109. Admin analytics authorization

Admin visibility respects organization scope unless the platform later introduces a controlled multi-organization or global-admin contract.

---

## 110. Export boundary

Full reporting and export is a separate roadmap concern.

P8 does not introduce arbitrary CSV export, custom report builder, or saved analytics report definitions unless separately prioritized.

---

## 111. Dashboard query performance

Analytics dashboards are fast enough for user-facing read traffic.

Use precomputed current aggregates, bounded dynamic queries, indexes, cache, and pagination.

Do not scan millions of StudentResponse rows for every dashboard open when an equivalent current aggregate exists.

---

## 112. Dynamic versus stored metric decision

For each metric ask:

```text
Can frozen aggregate storage represent it?
Can it be derived cheaply?
Does it need historical trend data?
Does it need a new grain?
```

Choose frozen table or bounded dynamic query before proposing new schema.

---

## 113. Analytics data consistency checks

Periodic validation compares the analytics aggregate against source recomputation for sampled or full populations as operationally appropriate.

---

## 114. Drift detection

Flag negative counts, count impossible relative to source, average outside source range, and accuracy outside [0, 100].

These are data-quality checks, not user-facing judgments.

---

## 115. Accuracy bounds

Accuracy and percentage metrics obey 0 <= value <= 100 subject to any explicitly approved normalization scheme.

---

## 116. Time bounds

Average time is non-negative and is checked for impossible outliers caused by client corruption or implementation defects.

---

## 117. Score bounds

Average, highest, and lowest score remain consistent with assessment total marks, approved negative-score semantics, and approved bonus and grace semantics.

---

## 118. Analytics security logging

Log analytics endpoint, caller, scope, duration, and status.

Full student result payloads are not logged unnecessarily.

---

## 119. Analytics metrics

Required operational metrics.

```text
analytics_refresh_total
analytics_refresh_success_total
analytics_refresh_failure_total
analytics_refresh_duration
analytics_rebuild_total
analytics_query_latency
analytics_cache_hit
analytics_cache_miss
analytics_data_quality_failures
```

---

## 120. Analytics alerts

Alert candidates.

```text
Refresh backlog
Rebuild failure
Query latency spike
Data-quality failure spike
Cache failure
Unexpected metric drift
```

---

## 121. Analytics load test

Test large student population, large assessment history, large response population, dashboard concurrency, question analytics list, topic analytics list, and organization dashboard.

Measure p50, p95, p99, database CPU, database I/O, cache behavior, and application heap.

---

## 122. Analytics rebuild load test

Use a representative dataset and measure rebuild duration, database load, worker throughput, locking behavior, and application memory.

A rebuild does not destabilize active examination runtime workloads.

---

## 123. Analytics runtime isolation

Analytics does not run heavy scans during live high-stakes exam traffic.

Operational rule.

```text
Live exam workload has priority over analytics rebuild.
```

Large rebuilds are scheduled outside active examination windows where possible.

---

## 124. Read replica consideration

The current architecture does not freeze a read replica.

If analytics load requires one, that is an infrastructure architecture decision, not a silent data topology change.

---

## 125. Warehouse consideration

The current architecture does not freeze a warehouse.

BigQuery, Snowflake, Redshift, and ClickHouse are not introduced merely for P8 convenience.

Actual scale evidence is required first.

---

## 126. Search engine boundary

The architecture avoids Elasticsearch and OpenSearch.

P8 uses PostgreSQL queries and projections unless a future architecture decision changes the search topology.

---

## 127. Materialized view consideration

PostgreSQL materialized views are not part of the frozen analytics schema.

They are considered only when performance evidence demonstrates that approved aggregate tables and bounded queries cannot meet requirements.

---

## 128. Organization-level comparison

Organization comparison across multiple organizations is not a pilot requirement.

P8 provides current organization analytics within the authorized organization.

Cross-organization benchmarking requires a future product and permission decision.

---

## 129. Student peer comparison

Peer comparison is not frozen.

Student-versus-peer ranking or percentile dashboards are not exposed merely because Result contains rank and percentile.

Use only published and authorized metrics under the existing P6 result contract.

---

## 130. Leaderboard boundary

A leaderboard feature is not the same as aggregate analytics.

P8 does not build a new leaderboard subsystem.

Rankings are served through the P6 rankings API as a descriptive view over published results.

---

## 131. Question ranking

Sorting questions by difficulty, incorrect rate, or time is an analytics view.

It is not a product verdict on content quality.

---

## 132. Topic ranking

Topics may be sorted by factual measures such as accuracy, average score, attempt count, and time.

A sorted topic list is not labeled as an evaluative ranking of what students should study without a documented recommendation rule.

---

## 133. Progress calculation

A safe progress metric is completed qualifying assessments or attempted versus target.

The canonical progress formula is documented before being presented as the primary progress number.

---

## 134. Trend minimum sample

Trend charts indicate sample size where practical.

Strong conclusions are not drawn from very sparse data.

---

## 135. Date and time semantics

Use TIMESTAMPTZ from transactional source data.

For UI display, convert to the user's locale and timezone.

Aggregation boundaries such as day, week, and month define timezone explicitly.

---

## 136. Timezone source

The source does not freeze an organization timezone or student timezone field.

Daily analytics uses the deployment or organization timezone only after that operational choice is documented.

Timezone is not inferred from browser locale as an official business rule.

---

## 137. Analytics API example - Student

```json
{
  "success": true,
  "message": "Student analytics retrieved",
  "data": {
    "assessmentsAttempted": 24,
    "averageScore": 62.40,
    "accuracy": 71.25,
    "averageTime": 48.20,
    "lastCalculatedOn": "2026-10-07T12:00:00Z"
  }
}
```

This reflects frozen StudentAnalytics fields only. Additional dashboard sections are returned by separate read models.

---

## 138. Analytics API example - Assessment

```json
{
  "success": true,
  "message": "Assessment analytics retrieved",
  "data": {
    "assessmentId": "<uuid>",
    "totalAttempts": 5000,
    "averageScore": 58.20,
    "highestScore": 98.00,
    "lowestScore": -4.00,
    "lastCalculatedOn": "2026-10-07T12:00:00Z"
  }
}
```

---

## 139. Analytics API example - Question

```json
{
  "success": true,
  "message": "Question analytics retrieved",
  "data": {
    "questionId": "<uuid>",
    "totalAttempts": 4872,
    "correctAttempts": 2931,
    "incorrectAttempts": 1512,
    "averageTime": 41.20,
    "difficultyIndex": 0.3980,
    "lastCalculatedOn": "2026-10-07T12:00:00Z"
  }
}
```

The difficulty index uses the frozen formula.

---

## 140. Analytics API example - Topic

```json
{
  "success": true,
  "message": "Topic analytics retrieved",
  "data": {
    "topicId": "<uuid>",
    "totalAttempts": 42000,
    "averageScore": 61.40,
    "accuracy": 73.20,
    "lastCalculatedOn": "2026-10-07T12:00:00Z"
  }
}
```

---

## 141. Analytics API example - Organization

```json
{
  "success": true,
  "message": "Organization analytics retrieved",
  "data": {
    "totalStudents": 5200,
    "totalAssessments": 42,
    "totalAttempts": 31800,
    "activeUsers": 4100,
    "lastCalculatedOn": "2026-10-07T12:00:00Z"
  }
}
```

---

## 142. Frontend analytics architecture

```text
app/
  analytics/
    student/
    assessments/
    questions/
    topics/
    organization/

components/
  analytics/
    MetricCard
    TrendChart
    SectionAnalysis
    TopicAnalysis
    QuestionAnalysis
    ProgressView

lib/
  analytics-api
  analytics-schemas

hooks/
  useStudentAnalytics
  useAssessmentAnalytics
  useTopicAnalytics
  useQuestionAnalytics
  useOrganizationAnalytics
```

---

## 143. Frontend analytics state

Keep server analytics data separate from filter UI state, chart display state, and selected date range.

Use TanStack Query for server data.

---

## 144. Analytics chart rules

Charts show metric name, unit, time or population scope, last refresh, empty state, loading, and error.

A percentage is not displayed without indicating what it measures.

---

## 145. Analytics accessibility

Charts have accessible equivalents such as tabular or summary representation.

Analytics is not dependent on visual interpretation alone.

---

## 146. Analytics empty states

Examples.

```text
No evaluated attempts yet.
No topic mapping available.
No assessment data for this period.
Analytics is refreshing.
```

Misleading zeros are avoided.

---

## 147. Analytics error states

If analytics refresh fails, the UI shows last known value, refresh timestamp, and a non-blocking stale indicator where safe.

Stale data is not silently replaced with zeros.

---

## 148. Analytics permissions in UI

Controls are hidden or disabled according to permission.

Backend authorization remains authoritative. Access is not inferred from route visibility alone.

---

## 149. P8 implementation task breakdown

```text
P8-01  Analytics module skeleton
P8-02  Frozen entity mapping
P8-03  Source query contracts
P8-04  Student analytics calculation
P8-05  Assessment analytics calculation
P8-06  Question analytics calculation
P8-07  Topic analytics calculation
P8-08  Organization analytics calculation
P8-09  Section dynamic analytics
P8-10  Dashboard read models
P8-11  Analytics API implementation
P8-12  Cache strategy
P8-13  Incremental refresh worker
P8-14  Full rebuild worker
P8-15  Data-quality checks
P8-16  Student dashboard
P8-17  Assessment dashboard
P8-18  Question analytics UI
P8-19  Topic analytics UI
P8-20  Organization analytics UI
P8-21  Security tests
P8-22  Repository and integration tests
P8-23  API tests
P8-24  Analytics correctness tests
P8-25  Load tests
P8-26  Rebuild tests
P8-27  Pilot-data backfill
P8-28  Documentation
P8-29  Release evidence
```

Execute one task at a time with checkpoints.

---

## 150. P8-01 to P8-03 acceptance

```text
[ ] Module exists
[ ] Frozen entity mapping complete
[ ] Source query contracts defined
[ ] No cross-module repository access
[ ] No new tables
[ ] Application starts
```

---

## 151. P8-04 to P8-08 acceptance

```text
[ ] Student analytics calculation
[ ] Assessment analytics calculation
[ ] Question analytics calculation
[ ] Topic analytics calculation
[ ] Organization analytics calculation
[ ] Difficulty index formula matches frozen definition
[ ] Recalculation is idempotent
[ ] Upsert semantics verified
```

---

## 152. P8-09 to P8-12 acceptance

```text
[ ] Section analytics via dynamic read
[ ] Dashboard read models
[ ] Analytics APIs implemented
[ ] Cache strategy implemented
[ ] Cache key contains required scope
[ ] Cache invalidation on refresh
```

---

## 153. P8-13 to P8-15 acceptance

```text
[ ] Incremental refresh worker
[ ] Full rebuild worker
[ ] Data-quality checks
[ ] Drift detection
[ ] Metrics exposed
```

---

## 154. P8-16 to P8-20 acceptance

```text
[ ] Student dashboard
[ ] Assessment dashboard
[ ] Question analytics UI
[ ] Topic analytics UI
[ ] Organization analytics UI
[ ] Empty states
[ ] Loading states
[ ] Error states
[ ] Freshness indicator
```

---

## 155. P8-21 to P8-29 acceptance

```text
[ ] Security tests
[ ] Repository and integration tests
[ ] API tests
[ ] Analytics correctness tests
[ ] Load tests
[ ] Rebuild tests
[ ] Pilot-data backfill
[ ] Documentation updated
[ ] Release evidence retained
[ ] CI green
```

---

## 156. Recommended implementation prompt

```text
You are implementing P8 Full Analytics and Insights for the Smart
Competitive Examination Platform.

Read the full specification package before modifying the repository.

P8 owns:
Derived analytics
Read models
Aggregate calculations
Refresh and rebuild workers
Dashboards
Analytics APIs
Data-quality checks

NON-NEGOTIABLE:
1.  Do not invent tables.
2.  Do not silently add columns.
3.  Do not silently add public APIs.
4.  Analytics is derived and recalculable.
5.  PostgreSQL transactional data remains authoritative.
6.  Do not use analytics as a scoring engine.
7.  Use Evaluation and Result for official score and correctness.
8.  Respect QuestionVersion and AssessmentVersion history.
9.  Do not guess taxonomy when source mapping is unresolved.
10. Use the frozen difficulty index formula.
11. Do not treat practice and exam as identical if mode matters.
12. Do not expose cross-organization data.
13. Student analytics are scoped to the authenticated student.
14. Use bounded SQL and projections.
15. Avoid N+1 analytics queries.
16. Do not introduce a warehouse or search engine unless formally
    approved.
17. Build both incremental refresh and a full rebuild path.
18. Make analytics refresh idempotent.
19. Do not block exam runtime with heavy analytics rebuilds.
20. Preserve last_calculated_on.
21. Do not silently convert missing data into zero.
22. Report conflicts instead of silently resolving them.

Task order:
P8-01 through P8-29.

For every task:
- Inspect current repository first.
- Inspect frozen upstream APIs and schema.
- Implement the smallest correct change.
- Run focused tests.
- Run PostgreSQL integration tests.
- Report schema and API impact.
- Report unresolved issues.
- Stop before the next high-risk task.
```

---

## 157. Review questions

```text
What is the source of this metric?
What is its population?
What is its denominator?
What is its grain?
Can the aggregate be rebuilt?
What happens after a re-evaluation?
What happens for an old QuestionVersion?
What happens for practice versus exam?
What timezone is used?
How is a missing value represented?
Can a student see another student's data?
Can a rebuild overload PostgreSQL?
Does a cache failure affect correctness?
Is the metric formula explicitly approved?
```

---

## 158. Final engineering principle

> Compute insights from authoritative history, make every metric explainable and rebuildable, and keep analytics from becoming a hidden second database of truth.

The intended flow.

```text
Attempt, Response, Evaluation, Result
            |
            v
       Reconciliation
            |
            v
      Analytics jobs
            |
            v
 Current aggregate projections
            |
            v
        Read APIs
            |
            v
       Dashboards
```

---

## 159. P8 handoff

P8 produces the derived analytics layer required for later product capabilities.

Future consumers may include Commerce-aware dashboards, Communication targeting, advanced reporting, personalized recommendations, AI insights, and admin analytics.

Those consumers do not bypass the authoritative transactional model.

---

## 160. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P8 — Full Analytics and Insights.

**Next implementation document:** P9 — Commerce and Access Management.