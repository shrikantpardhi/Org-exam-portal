# Smart Competitive Examination Platform
## P5 — Assessment Delivery & Runtime Examination Engine Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P5 — Assessment Delivery & Runtime Examination Engine
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, P4 Blueprint and Assessment Management, ExamEngine and CDUI Contract
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**Connection Pooling:** PgBouncer
**API:** REST + JSON, `/api/v1`
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Architects

---

## 1. Purpose

This document converts the Delivery domain into an implementation-ready engineering specification.

P5 owns the live execution boundary:

```text
AssessmentSession
StudentAttempt
AttemptQuestion
AttemptQuestionOption
StudentResponse
DeliveryPolicy
Snapshot preparation
Runtime navigation
Timer enforcement
Autosave
Recovery
Submission
```

P5 does not own:

```text
Blueprint authoring
Assessment authoring
Question authoring
Question publication
Official evaluation
Result publication
Commerce
Notifications
Full administration
```

---

## 2. Source-of-truth hierarchy

Read these documents before P5 implementation:

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
- This document

The earlier documents already freeze the delivery schema, the runtime boundaries, the snapshot-readiness design, and the ExamEngine and CDUI contract. This document does not modify those decisions.

---

## 3. P5 mission

P5 converts a published, immutable AssessmentVersion into a reliable student runtime.

The runtime answers:

```text
Can this student start this scheduled attempt?
What exact questions and options were assigned?
What can the student do right now?
How much time remains?
What response state has been persisted?
Can the student recover after disconnect?
Can the attempt be submitted exactly once?
Can a large concurrent-user burst be absorbed without turning
Start Exam into a database write storm?
```

The central P5 principle.

> Preparation workload and runtime workload remain separate.

The exam snapshot is prepared before runtime start. Start Exam is a lightweight state transition, not a snapshot-generation transaction.

---

## 4. Architectural position

```text
                         P4
              Published AssessmentVersion
                         |
                         v
                AssessmentSchedule
                         |
                         v
               P5 Preparation worker
                         |
                         v
                  StudentAttempt
                         |
                +--------+--------+
                v                 v
        AttemptQuestion     AssessmentSession
                |                 |
                v                 |
      AttemptQuestionOption       |
                |                 |
                +--------+--------+
                         v
                  StudentResponse
                         |
                         v
                     Submit
                         |
                         v
                         P6 Evaluation
```

The frontend is a client of P5.

```text
Next.js assessment runtime
          |
          v
REST / JSON
          |
          v
Delivery API
          |
          v
Delivery application services
          |
          v
PostgreSQL
```

Redis remains cache and support infrastructure only. PostgreSQL remains the transactional source of truth.

---

## 5. Pilot-first scope

P5 is on the pilot-critical path.

Pilot-critical.

```text
EXAM runtime
PRACTICE runtime
Snapshot preparation
Start Exam
Question delivery
Response save with ETag
Navigation
Timer
Session recovery
Submission
Basic runtime telemetry
Concurrent-user performance validation
```

Deferred.

```text
Advanced proctoring
Browser lockdown
Camera and microphone proctoring
AI cheating detection
Live invigilation
Multi-device session orchestration beyond the current contract
Real-time collaboration
Advanced offline exam execution
```

The pilot nevertheless has production-grade correctness at the core state transitions.

---

## 6. Frozen Delivery tables

```text
assessment_session
student_attempt
attempt_question
attempt_question_option
student_response
delivery_policy
```

No new persistent table is required for snapshot preparation.

Snapshot state is tracked on the StudentAttempt record.

---

## 7. Frozen AssessmentSession schema

```sql
assessment_session
------------------
id UUID PK
user_id UUID FK
assessment_schedule_id UUID FK
attempt_id UUID FK
session_status
started_on TIMESTAMPTZ
last_activity_on TIMESTAMPTZ
ended_on TIMESTAMPTZ
```

Indexes.

```text
idx_assessment_session_user_id
idx_assessment_session_schedule_id
idx_assessment_session_session_status
idx_assessment_session_last_activity_on
```

Session statuses.

```text
ACTIVE
EXPIRED
LOGGED_OUT
```

Application rule.

```text
AssessmentSession is a runtime record.
StudentAttempt is the academic record.
```

The two lifecycles are not collapsed into one entity.

---

## 8. Frozen StudentAttempt schema

```sql
student_attempt
---------------
id UUID PK
user_id UUID FK
assessment_version_id UUID FK
assessment_schedule_id UUID FK
attempt_mode VARCHAR(20)
attempt_status
snapshot_status VARCHAR(20)
snapshot_prepared_on TIMESTAMPTZ
started_on TIMESTAMPTZ
submitted_on TIMESTAMPTZ
total_score NUMERIC(8,2)
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Frozen indexes.

```text
idx_student_attempt_user_id
idx_student_attempt_assessment_version_id
idx_student_attempt_assessment_schedule_id
idx_student_attempt_attempt_status
idx_student_attempt_snapshot_status
idx_student_attempt_schedule_snapshot_status
idx_student_attempt_started_on
```

Attempt statuses.

```text
NOT_STARTED
IN_PROGRESS
SUBMITTED
EVALUATED
```

Snapshot statuses.

```text
PENDING
PREPARING
READY
FAILED
```

The attempt state machine and snapshot state machine are intentionally separate.

---

## 9. Frozen AttemptQuestion schema

```sql
attempt_question
----------------
id UUID PK
attempt_id UUID FK
question_version_id UUID FK
display_order INTEGER
section_name VARCHAR(150)
marks NUMERIC(8,2)
negative_marks NUMERIC(8,2)
created_on TIMESTAMPTZ
```

Indexes.

```text
idx_attempt_question_attempt_id
idx_attempt_question_question_version_id
idx_attempt_question_attempt_order
```

Required uniqueness.

```text
UNIQUE(attempt_id, question_version_id)
UNIQUE(attempt_id, display_order)
```

The assigned QuestionVersion, order, section, marks, and negative marks are immutable after preparation.

---

## 10. Frozen AttemptQuestionOption schema

```sql
attempt_question_option
-----------------------
id UUID PK
attempt_question_id UUID FK
question_option_id UUID FK
display_order INTEGER
```

Required uniqueness.

```text
UNIQUE(attempt_question_id, question_option_id)
UNIQUE(attempt_question_id, display_order)
```

The row set is the exact option presentation snapshot.

Student responses never expose the option's server-side `is_correct` value.

---

## 11. Frozen StudentResponse schema

```sql
student_response
----------------
id UUID PK
attempt_id UUID FK
question_version_id UUID FK
response TEXT
time_spent_seconds INTEGER
marked_for_review BOOLEAN
visited BOOLEAN
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Required uniqueness.

```text
UNIQUE(attempt_id, question_version_id)
```

Indexes.

```text
idx_student_response_attempt_id
idx_student_response_question_version_id
```

The response is the current persisted student state for the question.

The `response` field remains TEXT exactly as defined by the frozen schema. The exact transport encoding for each question template remains governed by the Content and QuestionTemplate contract. P5 validates it but does not invent an incompatible response schema.

---

## 12. Frozen DeliveryPolicy schema

```sql
delivery_policy
---------------
id UUID PK
code VARCHAR(50)
name VARCHAR(150)
configuration JSONB
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

Example configuration.

```json
{
  "allowBackNavigation": true,
  "allowSectionSwitch": true,
  "autoSaveIntervalSeconds": 10,
  "allowQuestionReview": true
}
```

The authoritative runtime behavior is represented through the normalized ExamEngine and CDUI contract.

P5 does not interpret arbitrary executable JSON.

---

## 13. Delivery runtime state model

Runtime state.

```text
                 snapshot
                    |
                    v
              NOT_STARTED
                    |
          Start Exam activation
                    |
                    v
                IN_PROGRESS
                 /          \
              submit        expiry
                |              |
                v              v
                SUBMITTED
                    |
               evaluation
                    |
                    v
                EVALUATED
```

Session state runs independently.

```text
ACTIVE
  |
  +-- recover  -> ACTIVE
  +-- logout   -> LOGGED_OUT
  +-- timeout  -> EXPIRED
```

The end of an AssessmentSession does not silently convert to successful attempt submission unless an approved business rule explicitly requires it.

---

## 14. Runtime invariants

The following invariants are mandatory.

```text
1.  A runtime attempt belongs to exactly one user.
2.  A runtime attempt points to one exact AssessmentVersion.
3.  A runtime attempt points to one exact AssessmentSchedule.
4.  attempt_mode is immutable after preparation.
5.  AttemptQuestion is immutable after snapshot preparation.
6.  AttemptQuestionOption is immutable after snapshot preparation.
7.  StudentResponse can only reference a QuestionVersion assigned to the attempt.
8.  StudentResponse cannot be created for a submitted or evaluated attempt.
9.  Correct answers are never returned by student-facing Delivery APIs.
10. The browser clock is not authoritative.
11. Start Exam does not create snapshot rows.
12. Submit is idempotent.
13. Recovery does not reconstruct a snapshot.
14. Published P4 artifacts are never modified by Delivery.
15. Access authorization is checked server-side.
16. Delivery does not call another module's repositories directly.
17. PostgreSQL is authoritative for persisted runtime state.
18. Redis failure cannot corrupt an attempt.
19. Duplicate active attempts are prevented by application rules.
20. All important state transitions are auditable and observable.
21. Stale-write protection uses HTTP conditional requests.
22. System actor identity is used for background processes.
```

---

## 15. Delivery module package boundary

Recommended backend structure.

```text
delivery/
  api/
    DeliveryController
    SessionController
    AttemptController
    ResponseController
    SubmissionController
  application/
    StartAssessmentService
    SessionRecoveryService
    AttemptQueryService
    ResponseSaveService
    SubmissionService
    SnapshotPreparationService
    AttemptEligibilityService
    RuntimeConfigurationService
    DeliveryPolicyService
  domain/
    AssessmentSession
    StudentAttempt
    AttemptQuestion
    AttemptQuestionOption
    StudentResponse
    DeliveryPolicy
  repository/
  dto/
  mapper/
  validation/
  exception/
  engine/
  worker/
```

Cross-module calls use explicit application contracts.

```text
AssessmentContextProvider
QuestionSnapshotProvider
AccessAuthorizationProvider
ExamEngineRegistry
EvaluationTrigger
```

These are contracts, not direct repository access.

---

## 16. P5 to P4 integration contract

Delivery consumes only the published handoff.

```text
AssessmentVersion
BlueprintVersion
AssessmentQuestion
Exact QuestionVersion IDs
QuestionOptions
Section
Marks
Negative marks
Duration
Schedule mode
Engine type
Delivery policy
Engine-compatible configuration
```

Delivery does not:

```text
Edit AssessmentQuestion
Edit AssessmentVersion
Resolve latest QuestionVersion
Change marks
Change negative marks
Change question ordering
Change BlueprintVersion
```

The preparation worker reads the published definition and materializes the runtime snapshot.

---

## 17. P5 to P3 Content contract

The runtime content contract is QuestionVersion, question content, question type, options, assets, and explanation or hint only when runtime policy permits.

Student runtime content uses MARKDOWN_LATEX_V1, supports `\( ... \)`, supports `\[ ... \]`, rejects raw HTML, rejects executable MDX, resolves `[asset: uuid]` through backend authorization, and renders math through the approved KaTeX server-side rendering pipeline.

P5 does not expose asset file paths, server storage paths, QuestionOption.is_correct, private author metadata, review metadata, or answer-key internals.

---

## 18. P5 to P1 security contract

Every runtime endpoint requires JWT authentication, user identity resolution, RBAC and permission enforcement, resource ownership authorization, input validation, and structured security logging where applicable.

Student authorization is based on the authenticated user and target attempt or session, not on a client-provided role.

Frontend route protection is convenience only. Backend authorization is authoritative.

---

## 19. Access and eligibility boundary

Start Exam verifies.

```text
Authenticated user
Schedule exists
Schedule is active and open
Current time is within allowed window
Assessment version is published
User has required access
Eligible attempt exists
Attempt belongs to authenticated user
attempt_mode matches schedule mode
snapshot_status = READY
attempt_status = NOT_STARTED
```

AccessGrant is the authorization source of truth for paid content.

The candidate population mechanism for scheduled mass exam preparation is a P5 design decision addressed during implementation. The chosen mechanism uses existing AccessGrant and operational candidate set. The design is documented before production use.

---

## 20. Snapshot preparation architecture

Snapshot preparation is an asynchronous workload.

```text
Schedule
   |
   v
Preparation trigger
   |
   v
Candidate resolver
   |
   v
StudentAttempt PENDING
   |
   v
Claim -> PREPARING
   |
   v
Read immutable AssessmentVersion
   |
   v
Read exact AssessmentQuestion rows
   |
   v
Read exact QuestionVersions and Options
   |
   v
Materialize AttemptQuestion
   |
   v
Materialize AttemptQuestionOption
   |
   v
Mark snapshot READY
```

No snapshot preparation occurs inside the public Start Exam request.

---

## 21. Snapshot preparation transaction model

Each attempt is prepared atomically.

Preferred unit.

```text
One StudentAttempt
+
All AttemptQuestion rows
+
All AttemptQuestionOption rows
+
READY transition
```

within one database transaction.

Recommended sequence.

```text
1.  Claim StudentAttempt PENDING -> PREPARING using conditional update.
2.  Load exact published AssessmentVersion.
3.  Load ordered AssessmentQuestion rows.
4.  Validate QuestionVersion and Option integrity.
5.  Apply permitted pre-start randomization.
6.  Insert AttemptQuestion rows in deterministic order.
7.  Insert AttemptQuestionOption rows in deterministic order.
8.  Update StudentAttempt:
      snapshot_status = READY
      snapshot_prepared_on = now()
      updated_on = now()
9.  Commit.
```

If any required step fails, the transaction rolls back and snapshot_status becomes FAILED. A retry is idempotent and does not create duplicates.

---

## 22. Snapshot claiming and worker concurrency

Workers use database-safe claiming.

The worker never selects many PENDING rows and independently assumes ownership without a claim protocol.

Preferred pattern.

```sql
UPDATE student_attempt
SET snapshot_status = 'PREPARING',
    updated_on = now()
WHERE id = :attemptId
  AND snapshot_status = 'PENDING';
```

Only the transaction receiving one affected row owns the preparation.

For batches, the implementation may use a bounded claim loop. The final SQL shape is verified against PostgreSQL execution plans and the worker query pattern.

Do not rely on an in-process Java lock for correctness in a multi-instance deployment.

---

## 23. Snapshot retry and recovery

The worker tolerates process crash, pod restart, database transient failure, network failure, partial batch failure, and temporary question-content failure.

State handling.

```text
PENDING    -> eligible for first claim
PREPARING  -> owned by a worker
READY      -> immutable runtime snapshot is usable
FAILED     -> retryable only through an explicit retry policy
```

Stale PREPARING detection uses approved existing timestamps and worker operational metadata rather than silently adding a new column.

`updated_on` may participate in stale detection. The exact timeout is an operational setting and is load-tested.

---

## 24. Snapshot idempotency rules

The following database constraints are part of the correctness model.

```text
UNIQUE(attempt_id, question_version_id)
UNIQUE(attempt_id, display_order)

UNIQUE(attempt_question_id, question_option_id)
UNIQUE(attempt_question_id, display_order)
```

The service treats duplicate-key outcomes during a retry as a signal to inspect existing snapshot state, not as permission to append another copy.

A successful attempt has exactly the expected number of AttemptQuestion rows and expected option rows before `snapshot_status = READY`.

---

## 25. Snapshot determinism

For the same prepared attempt, the runtime always sees the same QuestionVersion IDs, question order, section, marks, negative marks, and option order.

Randomization, if the assessment configuration permits it, occurs only during preparation. No randomization occurs on question GET, response save, recovery, or submit.

Historical reproducibility is therefore preserved by the snapshot itself.

---

## 26. Start Exam - performance-critical path

The Start Exam path is deliberately small.

```text
Authenticate
   |
   v
Authorize
   |
   v
Resolve schedule
   |
   v
Resolve candidate attempt
   |
   v
Read READY snapshot metadata
   |
   v
Resolve ExamEngine
   |
   v
Resolve normalized DeliveryConfiguration
   |
   v
Transition NOT_STARTED -> IN_PROGRESS
   |
   v
Create or activate lightweight AssessmentSession
   |
   v
Return runtime context
```

Start Exam does not insert AttemptQuestion rows, insert AttemptQuestionOption rows, randomize questions, bulk-load the question bank, construct a fresh snapshot, call evaluation, or rebuild the entire assessment definition.

This is the main defense against a thundering-herd failure at exam start.

---

## 27. Start Exam concurrency

For one attempt, concurrent Start requests result in exactly one logical start.

Preferred invariant.

```text
attempt_status = NOT_STARTED
            |
            v conditional transition
attempt_status = IN_PROGRESS
```

Use a conditional update or row-level transaction lock scoped to the StudentAttempt.

Example logical condition.

```text
UPDATE only when:
attempt_status = NOT_STARTED
AND snapshot_status = READY
```

A second request sees the already-started state and returns the established attempt and session context or a deterministic invalid-state response according to the approved idempotency policy.

Never use a global lock on an assessment or schedule.

---

## 28. Start Exam API

```http
POST /api/v1/assessment-schedules/{scheduleId}/start
```

The baseline source response includes.

```json
{
  "success": true,
  "message": "Assessment started",
  "data": {
    "sessionId": "<uuid>",
    "attemptId": "<uuid>",
    "assessmentVersionId": "<uuid>",
    "startedOn": "2026-10-10T10:00:00Z",
    "durationMinutes": 60
  }
}
```

The ExamEngine and CDUI contract additionally requires normalized runtime configuration to be returned.

A complete P5 response may include.

```json
{
  "success": true,
  "message": "Assessment started",
  "data": {
    "sessionId": "<uuid>",
    "attemptId": "<uuid>",
    "assessmentVersionId": "<uuid>",
    "mode": "EXAM",
    "startedOn": "2026-10-10T10:00:00Z",
    "serverTime": "2026-10-10T10:00:01Z",
    "timer": {
      "mode": "GLOBAL",
      "durationSeconds": 3600
    },
    "deliveryConfiguration": {}
  }
}
```

The exact OpenAPI shape is aligned with the ExamEngine and CDUI contract before implementation freeze.

---

## 29. Start Exam - snapshot not ready

If `snapshot_status != READY`, return.

```http
409 Conflict
```

with error code.

```text
PREPARATION_NOT_READY
```

The frontend displays a recoverable preparation state and does not repeatedly hammer Start Exam.

The backend does not fall back to synchronous generation.

---

## 30. Attempt creation

P5 creates the StudentAttempt runtime record as part of preparation, not from the Start Exam request.

An attempt is created with.

```text
attempt_status  = NOT_STARTED
snapshot_status = PENDING
attempt_mode    = schedule.mode
assessment_schedule_id = exact schedule
assessment_version_id  = exact published version
```

The attempt is bound to the authenticated student or candidate before snapshot preparation.

---

## 31. Practice mode

`PRACTICE` and `EXAM` are immutable attempt modes.

Practice may permit flexible navigation, explanation visibility, hint visibility, no enforced duration where configured, and repeated practice according to assessment rules.

The server determines the mode. The frontend never permits `PRACTICE -> EXAM` or `EXAM -> PRACTICE` within the same attempt.

Practice still uses the same snapshot integrity model so that runtime correctness is not a second implementation.

---

## 32. Exam mode

`EXAM` is the strict delivery mode.

The runtime enforces the approved navigation policy, timer policy, submission policy, validation policy, and presentation capability contract.

Any action that violates the normalized policy is rejected by the backend even if the UI hides the control.

---

## 33. ExamEngine resolution

P5 integrates the approved interface.

```java
public interface ExamEngine {
  ExamEngineType type();
  DeliveryConfiguration resolveConfiguration(
      AssessmentContext context,
      StudentAttemptContext attemptContext
  );
  NavigationPolicy navigationPolicy(...);
  TimerPolicy timerPolicy(...);
  SubmissionPolicy submissionPolicy(...);
  ValidationPolicy validationPolicy(...);
}
```

Engine resolution is registry-based.

```text
ExamEngineRegistry
      |
      v
ExamEngineType
      |
      v
Concrete ExamEngine
```

Supported types.

```text
TCS_ION
IBPS
NTA
UPSC
RAILWAY
CUSTOM
```

The frontend does not branch on these names.

---

## 34. Normalized DeliveryConfiguration

The runtime configuration is declarative.

Conceptual groups.

```text
Capabilities
Navigation
Timer
Submission
Validation
Presentation
```

The JSON is an experience and enforcement contract, not executable configuration.

---

## 35. Delivery configuration security

Configuration never contains `renderScript`, `javascript`, `customComponent`, `eval`, `expression`, `databaseQuery`, or `arbitraryUrl`.

The frontend uses a typed Zod schema.

The backend validates the resolved configuration before returning it.

Unsupported configuration fails safely rather than being silently interpreted.

---

## 36. Navigation state machine

Navigation is controlled by the backend policy.

Conceptual modes.

```text
FREE
SEQUENTIAL
SECTION_SEQUENTIAL
CUSTOM
```

A navigation request is validated against attempt status, session status, attempt question membership, current section, target question order, engine navigation policy, and section switch capability.

The backend does not trust a question index sent by the browser without resolving it against the immutable AttemptQuestion snapshot.

---

## 37. Question identity in runtime

The source contract uses `QuestionVersion ID` in the response API.

The delivery design additionally exposes.

```text
attemptQuestionId
questionVersionId
displayOrder
sectionName
marks
negativeMarks
option ordering
```

The backend resolves `attemptId + questionVersionId` to the immutable AttemptQuestion. This prevents a malicious client from selecting a QuestionVersion that was never assigned to the attempt.

---

## 38. Get attempt

```http
GET /api/v1/attempts/{attemptId}
```

Returns only student-safe runtime state.

Recommended logical information.

```text
attemptId
assessmentVersionId
assessment or schedule identifiers where safe
attemptMode
attemptStatus
snapshotStatus
startedOn
submittedOn
serverTime
deadline or remaining timer representation
deliveryConfiguration
progress summary
```

Never returns correct answers, answer key internals, private authoring data, private audit data, other students, payment secrets, or server file paths.

The endpoint verifies attempt ownership or authorized staff permission.

---

## 39. Get attempt questions

```http
GET /api/v1/attempts/{attemptId}/questions
```

The result is built from the prepared snapshot, not from the live AssessmentQuestion table.

Expected runtime data.

```text
attemptQuestionId
questionVersionId
displayOrder
sectionName
marks
negativeMarks
question content
question type
authorized options
authorized assets
```

The backend preserves snapshot order.

The list supports efficient retrieval and does not load the entire QuestionBank.

---

## 40. Get attempt question

```http
GET /api/v1/attempts/{attemptId}/questions/{questionVersionId}
```

The backend verifies attempt belongs to user, attempt is active, and QuestionVersion is assigned to attempt.

The endpoint never resolves the current or latest version of the Question. It resolves the exact QuestionVersion captured by AttemptQuestion.

---

## 41. Question payload protection

Student runtime APIs omit `QuestionOption.is_correct`, answer key, review notes, author identity, question health score, internal taxonomy metadata not required by UI, asset file path, and database identifiers not part of the contract.

Only the information required to render and answer the question is exposed.

---

## 42. Autosave architecture

```http
PUT /api/v1/attempts/{attemptId}/responses/{questionVersionId}
```

Baseline request.

```json
{
  "response": "B",
  "timeSpentSeconds": 32,
  "markedForReview": false,
  "visited": true
}
```

The operation is idempotent.

Autosave is an intentional API interaction with debounce and throttle, request cancellation where safe, bounded retry, offline queue handling for short network interruptions, server acknowledgement, and duplicate protection.

The browser does not assume a response is persisted until the server acknowledges success.

---

## 43. Stale-write protection contract

The save endpoint uses HTTP conditional requests.

GET returns.

```text
ETag: "resp-{updatedOnMillis}-{crc32OfResponse}"
Cache-Control: no-store
```

PUT accepts.

```http
If-Match: "<etag>"
```

Rules.

| Condition | Behaviour |
|---|---|
| If-Match matches current ETag | Accept, increment updated_on, return new ETag |
| If-Match does not match | 412 Precondition Failed with STALE_RESPONSE |
| If-Match: * | Accept only if row exists, otherwise 412 |
| No If-Match | Accept (first save). Server records ETag |

On 412, the client re-fetches the current response, merges local state, and retries with the new ETag.

The important invariant.

> An older delayed request never silently overwrites a newer accepted response.

---

## 44. Response save transaction

Preferred server-side flow.

```text
Authenticate
   |
   v
Authorize attempt
   |
   v
Validate active session and attempt state
   |
   v
Resolve AttemptQuestion
   |
   v
Validate response against question template
   |
   v
Validate If-Match precondition where provided
   |
   v
Normalize response representation
   |
   v
UPSERT student_response
   |
   v
Update session last_activity_on
   |
   v
Commit
```

The persistence operation does not create a new row when one already exists for the same attempt and question.

---

## 45. Response validation

Before persistence, validate attempt belongs to user, attempt_status is IN_PROGRESS, session is valid, question is assigned, question version belongs to snapshot, response conforms to QuestionTemplate, response is within permitted size, timeSpentSeconds is non-negative, markedForReview is permitted, and visited is consistent with runtime state.

A client cannot use response content to select an unassigned option.

The exact option-response encoding remains governed by the question-template content contract.

---

## 46. Time tracking

`student_response.time_spent_seconds` is the frozen persistence field.

The browser may estimate elapsed time for UX, but server-side validation prevents obviously invalid values.

The runtime tracks question timing as a client measurement that is persisted on response saves. The official attempt deadline remains server authoritative.

For analytics, the persisted question time supports derived question time, section time, assessment time, topic time, and student average time.

P5 does not create a new timing event table.

---

## 47. Idempotency model

Idempotency is mandatory for response save, submission, payment callbacks, notification delivery, and background jobs.

For Delivery.

```text
Same logical mutation
      |
      v
Same effective result
      |
      v
No duplicate business effect
```

`X-Request-Id` remains the correlation identifier.

Where an operation requires deduplication beyond the domain unique keys, the exact persistence and short-lived coordination mechanism is selected during implementation.

---

## 48. Session heartbeat and activity

The frozen schema includes `last_activity_on`.

The session updates this timestamp on meaningful runtime activity.

Do not update it on every UI render.

Meaningful activity.

```text
Question load
Response save
Navigation action
Recovery
Submission
```

No heartbeat endpoint is added because the frozen API contract does not include one.

---

## 49. Get active session

```http
GET /api/v1/sessions/active
```

Purpose: reconnect and recovery.

The endpoint returns the currently recoverable session for the authenticated student.

It does not create a new session, create a new attempt, rebuild snapshot, change attempt mode, or restart the timer.

If no recoverable session exists, return the standard not-found or valid-state response.

---

## 50. Get session

```http
GET /api/v1/sessions/{sessionId}
```

Returns runtime-safe state: session status, attempt id, schedule id, started time, last activity, server time, and effective timer state.

Never returns private internal session details that the student does not need.

---

## 51. Session recovery

```http
POST /api/v1/sessions/{sessionId}/recover
```

Recovery means.

```text
Authenticate
   |
   v
Verify session ownership
   |
   v
Verify session is still recoverable
   |
   v
Verify attempt state
   |
   v
Re-resolve normalized DeliveryConfiguration
   |
   v
Read persisted attempt state
   |
   v
Return current runtime context
```

Recovery never reconstructs AttemptQuestion rows. Recovery never resets `started_on`. Recovery never restarts the timer.

---

## 52. Recovery after process restart

The system recovers correctly after API instance restart, frontend refresh, browser crash, temporary network loss, load balancer rebalance, and Redis restart.

The recovery source of truth is PostgreSQL, not React state, Redis-only session state, or browser localStorage alone.

Local UI state may improve UX but is never treated as authoritative.

---

## 53. Redis failure behavior

If Redis is unavailable, core attempt reads continue, response persistence continues, submission continues, and PostgreSQL remains authoritative.

Redis may provide cache, short-lived coordination, rate limiting support, and non-critical optimization, but P5 does not require Redis availability to preserve exam correctness.

---

## 54. Timer authority

The server is authoritative.

At start, the client receives serverTime, startedOn, duration, and effective timer policy.

For a global timer.

```text
deadline = startedOn + effective duration
```

The browser renders a countdown.

The backend recomputes deadline validity on response save, navigation, question load, recovery, submission, and other protected runtime actions.

A browser cannot extend an attempt by changing local clock values.

---

## 55. Timer drift model

Client timer behavior uses serverTime as the authoritative reference, clientClock as a display aid, and periodic server interactions as synchronization opportunities.

The client tolerates tab suspension, sleep and wake, clock changes, CPU throttling, and network latency.

When a server response indicates less time remains, the UI immediately resynchronizes.

---

## 56. Timer expiry

When an important request arrives after the deadline, non-submission state-changing actions are rejected.

For configured auto-submit behavior.

```text
Expire
   |
   v
Server submission path
   |
   v
Finalize attempt once
   |
   v
Mark SUBMITTED
```

Timer expiry is race-safe with manual submission. Only one finalization wins logically.

---

## 57. Global timer

The global timer is fully expressible with the frozen runtime fields: `started_on` and assessment duration.

For the pilot, this is the baseline timer implementation. A global deadline remains stable across refresh and recovery.

---

## 58. Section-wise timer

Section-wise timer semantics require careful persistence design.

The pilot may use only section-timing behavior that can be proven correct from the frozen persistence model. A separate schema and contract amendment is required before enabling behavior that requires new persisted state.

The implementation does not silently add a timer event table.

---

## 59. Navigation and section switching

The normalized configuration controls back navigation, section switching, question review, clear response, and question palette.

The frontend uses these capabilities to render controls.

The backend validates every protected transition.

An exam name such as IBPS or NTA never appears in React navigation logic.

---

## 60. Question palette state

The question palette is derived from persisted runtime state.

```text
NOT_VISITED
VISITED_NOT_ANSWERED
ANSWERED
MARKED_FOR_REVIEW
ANSWERED_AND_MARKED
LOCKED
```

The frozen response model contains enough fields to represent the common states: visited, marked_for_review, and response.

The palette remains a presentation of server-backed state, not an independent source of truth.

---

## 61. Clear response

A clear-response action uses the existing response mutation contract rather than introducing another persistence model.

A clear operation results in a deterministic response state representing no answer, visited according to runtime semantics, and mark-for-review according to current UI state.

---

## 62. Mark for review

The frozen API allows `markedForReview` inside the response payload.

For V1 implementation, PUT response is the preferred path because the source response model already carries the state.

Do not create both paths without a specific need.

---

## 63. Submission API

```http
POST /api/v1/attempts/{attemptId}/submit
```

Baseline request.

```json
{
  "confirmed": true
}
```

Server flow.

```text
Authenticate
   |
   v
Authorize
   |
   v
Validate session
   |
   v
Validate attempt
   |
   v
Validate deadline
   |
   v
Finalize persisted response state
   |
   v
Transition attempt to SUBMITTED
   |
   v
Stamp submitted_on
   |
   v
End or transition session as appropriate
   |
   v
Trigger evaluation
```

Submission is a state transition, not a client-side cleanup operation.

---

## 64. Submission idempotency

Two or more simultaneous submit requests produce one logical submission.

Valid outcomes.

```text
First request transitions IN_PROGRESS -> SUBMITTED
Later identical request returns the same submitted state
```

Invalid outcomes.

```text
Duplicate evaluation triggers
Different submitted timestamps
Resurrection to IN_PROGRESS
Double session end
```

The implementation uses transaction-safe state transition logic.

---

## 65. Submit versus session end

The frozen API contains.

```http
POST /api/v1/sessions/{sessionId}/end
```

and separately.

```http
POST /api/v1/attempts/{attemptId}/submit
```

Therefore.

```text
End session is not the same as Submit attempt
```

Unless a formally approved delivery rule says otherwise.

A support or operator session termination does not accidentally produce an official submission.

---

## 66. Submission race

A submission can race with last autosave, timer expiry, recovery, duplicate submit, and session expiry.

Required outcome.

```text
One authoritative terminal attempt state
```

Recommended transaction rule.

```text
Lock or conditionally update StudentAttempt
Verify IN_PROGRESS
Verify current server time
Finalize only once
```

Any background evaluation trigger uses the resulting submitted attempt ID and an idempotent trigger strategy.

---

## 67. Evaluation handoff

P5 stops at `StudentAttempt = SUBMITTED`.

Then P5 hands off to Evaluation.

Evaluation is the source of official scoring, answer-key application, result, and scorecard.

P5 does not compute official marks as part of Delivery. It may compute operational counts such as answered count, visited count, and review count for runtime UX only.

---

## 68. Submission response

```json
{
  "success": true,
  "message": "Assessment submitted successfully",
  "data": {
    "attemptId": "<uuid>",
    "status": "SUBMITTED",
    "submittedOn": "2026-10-10T11:00:00Z"
  }
}
```

The response does not reveal unofficial score data unless a separate approved result API provides it.

---

## 69. Attempt history

```http
GET /api/v1/attempts
```

Filters: assessmentId, assessmentVersionId, status, from, to.

Students see only their own attempts. Staff visibility is permission controlled.

P5 does not return private attempts outside the caller's authorized organization context.

---

## 70. API inventory - frozen Delivery contract

```http
POST /api/v1/assessment-schedules/{scheduleId}/start

GET  /api/v1/sessions/active
GET  /api/v1/sessions/{sessionId}
POST /api/v1/sessions/{sessionId}/recover
POST /api/v1/sessions/{sessionId}/end

GET  /api/v1/attempts
GET  /api/v1/attempts/{attemptId}
GET  /api/v1/attempts/{attemptId}/questions
GET  /api/v1/attempts/{attemptId}/questions/{questionVersionId}

GET  /api/v1/attempts/{attemptId}/responses/{questionVersionId}
PUT  /api/v1/attempts/{attemptId}/responses/{questionVersionId}
GET  /api/v1/attempts/{attemptId}/responses

POST /api/v1/attempts/{attemptId}/submit

POST /api/v1/assessment-schedules/{scheduleId}/prepare-attempts
```

No new public endpoint is added merely to make the API aesthetically symmetrical.

---

## 71. API response conventions

Success.

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": {}
}
```

Error.

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": []
}
```

Important Delivery error codes.

```text
PREPARATION_NOT_READY
INVALID_STATE
ATTEMPT_NOT_FOUND
ATTEMPT_NOT_OWNED
SESSION_NOT_FOUND
SESSION_EXPIRED
SCHEDULE_NOT_ACTIVE
SCHEDULE_NOT_STARTED
SCHEDULE_CLOSED
ATTEMPT_EXPIRED
QUESTION_NOT_ASSIGNED
INVALID_RESPONSE
SUBMISSION_ALREADY_COMPLETED
DELIVERY_CONFIGURATION_INVALID
STALE_RESPONSE
```

---

## 72. HTTP status mapping

| Situation | Status |
|---|---|
| Valid read, save, start, submit | 200 |
| New runtime record when applicable | 201 |
| Accepted background operation | 202 |
| Validation failure | 400 |
| Missing or invalid JWT | 401 |
| Authenticated but forbidden | 403 |
| Attempt or session not found | 404 |
| State race, preparation not ready, duplicate state | 409 |
| If-Match precondition failed | 412 |
| Valid request but semantic validation fails | 422 |
| Rate limited | 429 |
| Unexpected system failure | 500 |

---

## 73. Frontend runtime architecture

Recommended Next.js structure.

```text
app/
  assessment/
    [scheduleId]/
      page.tsx
    runtime/
      page.tsx

features/assessment/
  api/
  components/
  config/
  hooks/
  models/
  rendering/
  state/
  timer/
  validation/
```

The component tree is configuration driven.

---

## 74. Frontend must not contain

Prohibited.

```text
if examType === 'IBPS'
if assessmentName.includes('NTA')
if exam === 'SSC'
```

Also prohibited: client-side authorization, client-side correct-answer logic, client-side final scoring, client-side deadline authority, snapshot reconstruction, randomization after start, direct database calls, and raw asset path construction.

The frontend is a renderer and interaction client.

---

## 75. Frontend runtime bootstrap

Bootstrap sequence.

```text
1.  Load current user context.
2.  Call active-session endpoint.
3.  If recoverable session exists, offer or resume recovery.
4.  Otherwise navigate to scheduled assessment.
5.  Start only through Start Exam endpoint.
6.  Receive normalized DeliveryConfiguration.
7.  Validate configuration with Zod.
8.  Load attempt, questions, and responses.
9.  Render runtime.
10. Synchronize server time through normal API responses.
```

A configuration validation failure stops unsafe rendering and shows a support-safe error.

---

## 76. Runtime query strategy

Use TanStack Query for server state.

Prefer attempt query, questions query, responses query, and session query.

Avoid making every UI component fetch the same runtime data.

Use cache invalidation only when state changes.

Never cache sensitive answers beyond what the runtime needs.

---

## 77. Runtime mutation strategy

For response saves, use one mutation stream per active attempt.

Avoid dozens of simultaneous writes caused by fast clicking, route transitions, duplicate event handlers, React re-renders, or retry storms.

A response mutation has pending, acknowledged, failed, and retrying states.

UI state remains distinct from persisted state.

---

## 78. Network resilience

The runtime tolerates temporary connectivity issues.

Recommended behavior.

```text
Network lost
   |
   v
Show non-blocking offline indicator
   |
   v
Retain current UI state
   |
   v
Queue safe pending mutations
   |
   v
Reconnect
   |
   v
Retry with bounded backoff
   |
   v
Server acknowledges
   |
   v
Mark persisted
```

Do not queue indefinitely. Do not allow a failed save to be mistaken for a committed response.

---

## 79. Reload resilience

After browser refresh.

```text
GET active session
   |
   v
Recover session
   |
   v
Get attempt
   |
   v
Get responses
   |
   v
Rebuild palette state
   |
   v
Resume at allowed location
```

The UI reconstructs from server state rather than from local-only state.

---

## 80. Multi-tab risk

The pilot explicitly tests same attempt opened in two tabs, simultaneous response mutations, submit in one tab while another is active, and recovery from second tab.

The backend remains authoritative. If multi-tab use is unsupported, the UI may warn the user. The backend still preserves state integrity.

---

## 81. Asset delivery

Question assets are fetched through protected endpoints and services.

The runtime may render `[asset: <uuid>]` through the approved Content rendering pipeline.

The frontend never receives or derives S3 object secrets, local storage paths, server filesystem paths, or internal bucket credentials.

Asset authorization considers the student's access to the assessment and question.

---

## 82. Math rendering

Runtime rendering remains compatible with MARKDOWN_LATEX_V1.

Supported math.

```text
\( ... \)
\[ ... \]
```

Not supported.

```text
$...$
$$...$$
```

Raw HTML and executable MDX are rejected.

KaTeX server-side rendering remains the approved renderer.

---

## 83. Runtime authorization matrix

| Action | Student | Staff | Reviewer | Admin |
|---|---|---|---|---|
| Start own scheduled attempt | Yes | According to permission | According to permission | According to permission |
| Read own attempt | Yes | According to permission | According to permission | According to permission |
| Save own response | Yes | According to permission | According to permission | According to permission |
| Submit own attempt | Yes | According to permission | According to permission | According to permission |
| Read another student's attempt | No | Permission required | Permission required | Permission required |
| Modify published snapshot | No | No | No | No |
| Trigger preparation | No | Permission required | Permission required | Permission required |
| Force operational recovery | No | Permission required | Permission required | Permission required |

The exact permission codes are derived from the Identity permission catalogue and are not invented in Delivery.

---

## 84. Database query rules

P5 repositories filter by attempt ID, filter by authenticated user, use indexes, use projections where practical, avoid N+1 question loading, avoid loading the entire QuestionBank, and avoid cross-module entity graphs.

For question delivery, prefer an explicit query plan that joins the snapshot rows and only the authorized QuestionVersion fields.

Do not expose JPA entities directly from controllers.

---

## 85. Snapshot read query

For `GET /attempts/{attemptId}/questions`, the preferred logical read is.

```text
AttemptQuestion
   JOIN QuestionVersion
   JOIN AttemptQuestionOption
   JOIN QuestionOption
   JOIN authorized assets
```

with ordering by `AttemptQuestion.display_order` and `AttemptQuestionOption.display_order`.

The exact SQL and projection shape is benchmarked.

Avoid loading attempt, then lazy-loading 100 questions, then lazy-loading 400 options, if that produces N+1 behavior.

---

## 86. Response upsert query

The frozen uniqueness constraint allows `UNIQUE(attempt_id, question_version_id)`.

The implementation uses a transaction-safe upsert pattern.

The update modifies only the current response fields.

```text
response
time_spent_seconds
marked_for_review
visited
updated_by
updated_on
status
```

It does not change `attempt_id` or `question_version_id`.

---

## 87. Session update query

Meaningful runtime activity may update `last_activity_on`.

The update remains low-cost.

Do not create a new session on every request. Do not hold long database transactions merely to update activity metadata.

---

## 88. Attempt state transition

Attempt transitions are represented by explicit application actions: Start and Submit. Evaluate is owned by Evaluation.

Avoid generic `PUT /attempts/{id}` for critical state transitions.

The endpoint and action make the business transition obvious in audit and logs.

---

## 89. Session end

```http
POST /api/v1/sessions/{sessionId}/end
```

Use for explicit session termination and support-controlled termination when authorized.

Do not use it as a shortcut for submission, evaluation, or result publication unless an approved policy explicitly defines such coupling.

---

## 90. Session expiry worker

A background cleanup job may expire inactive sessions based on the approved session timeout policy.

It identifies stale ACTIVE sessions, claims and transitions safely, sets ended_on, avoids changing submitted attempt state incorrectly, and emits metrics.

Session expiration does not delete runtime academic data.

---

## 91. Attempt expiry versus session expiry

These are distinct concepts.

Session expiry means the runtime connection or session is no longer active.

Attempt expiry means the examination deadline has passed and the attempt can no longer accept normal mutations.

A session may expire while an attempt remains recoverable until the deadline. An attempt deadline may pass while a session remains technically connected.

The server decides the valid action in both cases.

---

## 92. Deadline calculation

For a global timer.

```text
deadline = started_on + duration
```

All persisted timestamps use TIMESTAMPTZ. All comparisons are server-side.

The frontend receives normalized timer information but never sets the official deadline.

---

## 93. Schedule window versus attempt deadline

These are separate.

```text
Schedule window    -> controls when a start can occur
Attempt duration   -> controls how long a started attempt may run
```

An attempt may start near the end of a schedule window according to the approved business rule.

The exact relationship between schedule end_time and attempt started_on plus duration is defined in the P5 implementation.

---

## 94. Schedule mode enforcement

Schedule mode is `PRACTICE` or `EXAM`.

The worker copies it to `StudentAttempt.attempt_mode`.

The runtime treats the attempt value as immutable.

A client cannot request a different mode in Start Exam.

---

## 95. Delivery policy resolution

Approved resolution chain.

```text
AssessmentVersion
       +
BlueprintVersion configuration
       +
Attempt mode
       +
ExamEngine
       |
       v
DeliveryConfiguration
```

The delivery_policy referenced by the AssessmentVersion provides reusable delivery behaviour.

---

## 96. Configuration versioning

Runtime behavior is historically reproducible.

A started attempt remains governed by the configuration resolved for that attempt. Do not dynamically switch engines or policies mid-attempt because an admin changed a later configuration.

If the system needs stronger persistence of the resolved configuration than the frozen schema currently permits, that becomes a formal contract and schema decision.

---

## 97. Attempt snapshot does not mean content copy

The snapshot copies execution-critical assignment data.

```text
QuestionVersion reference
Order
Section
Marks
Negative marks
Option reference and order
```

It does not require copying the entire question text into new runtime tables.

QuestionVersion is already immutable once published. Therefore the snapshot preserves historical correctness while keeping data duplication bounded.

---

## 98. Publication integrity

Preparation rejects an AssessmentVersion if any required source is no longer valid.

Before preparing.

```text
AssessmentVersion published
BlueprintVersion published and compatible
QuestionVersions published and available
QuestionOption relationships valid
Section counts match
Marks valid
Negative marks valid
Duration valid for EXAM
Engine configuration valid
```

P4 already guarantees publication validity, but Delivery still fails safely rather than assuming authoring data is always correct.

---

## 99. Preparation readiness validation

An attempt is READY only when expected question count equals actual AttemptQuestion count, all expected order positions exist, all AttemptQuestion rows reference correct QuestionVersions, all option snapshots are valid, section assignments are present where required, marks are present, negative marks are present, mode is present, and published source is valid.

READY is a correctness claim, not merely a worker-completed flag.

---

## 100. Preparation failure handling

Failures are visible operationally without adding schema solely for error text.

Use structured logs, metrics, traces, audit events where appropriate, and worker dashboards.

Repeated FAILED attempts are not hidden.

A failed snapshot is not automatically startable.

---

## 101. Preparation scheduling strategy

Preparation finishes before the configured exam window.

Recommended operational lifecycle.

```text
Configured lead time
        |
        v
Identify candidate population
        |
        v
Create PENDING attempts
        |
        v
Worker prepares snapshots
        |
        v
Monitor READY coverage
        |
        v
Retry failures
        |
        v
Freeze preparation
        |
        v
Exam window opens
        |
        v
Students Start
```

The exact lead time is a deployment setting, not a domain constant.

Readiness is visible as an operational metric: READY / ELIGIBLE, FAILED / ELIGIBLE, PENDING / ELIGIBLE.

---

## 102. Preparation capacity

For an assessment with 5,000 students, 100 questions, and average 4 options per question, a fully materialized runtime snapshot can represent approximately.

```text
500,000 AttemptQuestion rows
2,000,000 AttemptQuestionOption rows
2,500,000 runtime snapshot row inserts total
```

The architecture intentionally moves this write workload out of Start Exam.

The calculation is an engineering sizing example, not a guarantee of exact production write volume.

---

## 103. Worker batching

Use bounded worker concurrency.

Potential controls.

```text
Worker concurrency
Batch claim size
JDBC or Hibernate batch size
Transaction timeout
Retry backoff
Database connection limit
PgBouncer configuration
```

Do not hard-code production pool sizes before load testing.

The correct values depend on measured PostgreSQL CPU, I/O, locks, WAL generation, connection usage, and application latency.

---

## 104. PgBouncer boundary

PgBouncer is especially relevant to high-volume response and session traffic.

It helps manage connection pressure. It does not remove PostgreSQL writes, WAL generation, row locking, index maintenance, CPU, or I/O.

Therefore snapshot pre-generation, bounded workers, efficient SQL, PgBouncer, and load testing are treated as one capacity strategy.

---

## 105. Response burst capacity

The autosave path can become the largest sustained write stream.

Design for concurrent students, policy-driven autosave, question navigation, submission burst, and recovery burst.

Do not size for only average traffic.

Load testing simulates correlated student behavior rather than independent random requests.

---

## 106. Start burst capacity

The Start Exam burst is dominated by read attempt, read schedule, validate access, resolve configuration, single attempt state transition, session create or activate, and response serialization.

It is not dominated by bulk INSERT, question randomization, full question bank scans, or cross-module entity loading.

This is the primary acceptance criterion for the runtime-start architecture.

---

## 107. Submission burst capacity

At exam end, many students may submit within seconds.

The path handles conditional state transition, final response consistency, submission timestamp, session termination, and evaluation trigger.

Do not synchronously perform heavy evaluation work in the same transaction as the submission unless benchmark evidence proves it safe.

Prefer a durable, idempotent handoff to Evaluation.

---

## 108. Recovery burst capacity

A deployment restart or network incident can cause many students to reconnect simultaneously.

Recovery remains lightweight.

```text
Read session
Read attempt
Read persisted responses
Resolve configuration
Return state
```

Do not reconstruct snapshots during recovery.

---

## 109. Load test model

The concurrent-user validation includes at least.

```text
Scenario A - Start burst
Scenario B - Steady autosave
Scenario C - Navigation and read burst
Scenario D - Submission burst
Scenario E - Recovery burst
Scenario F - Mixed realistic exam behavior
```

A generic concurrent HTTP-client test is insufficient.

---

## 110. Start burst test

Recommended test.

```text
5,000 prepared StudentAttempts
5,000 authenticated users
Start requests released within a narrow interval
snapshot_status = READY
No preparation activity during test
```

Measure p50, p95, p99, error rate, database CPU, database connections, lock waits, WAL rate, application CPU, GC, and request queue depth.

Acceptance gate.

```text
5,000 concurrent users
Applicable p95 under 100 ms
Greater than 99% submissions
Less than 1% exam failure
```

The exact load profile and hardware are retained with the test evidence.

---

## 111. Autosave burst test

Model 5,000 active attempts with each user saving according to effective autosave policy, randomized interaction timing, occasional duplicate retry, and occasional temporary network failure.

Measure response-save p95 and p99, database write throughput, row-lock contention, deadlocks, connection saturation, and error and retry amplification.

The test demonstrates that retries do not become a retry storm.

---

## 112. Submission burst test

Model 5,000 active attempts reaching terminal submission within a short interval with some requests duplicated, some delayed, and some sessions expired.

Verify one SUBMITTED state, no duplicate evaluation trigger, no lost response mutation beyond the accepted race policy, and no cross-user contamination.

---

## 113. Recovery burst test

Model 5,000 active students with simulated API restart or connection loss, all recovering within a narrow interval.

Verify same attempt, same snapshot, same answers, same timer and deadline, no new attempt, and no duplicate session explosion.

---

## 114. Failure injection

P5 tests failures such as PostgreSQL transient failure, Redis unavailable, one application instance restart, worker crash during PREPARING, worker crash after snapshot rows but before READY, network timeout during response save, duplicate submit, expired token during runtime, and stale tab.

Correctness survives these events without manual database edits.

---

## 115. Snapshot crash case

Critical case.

```text
Worker starts
student_attempt = PREPARING
Transaction opens
Rows inserted
Worker crashes
Transaction rolls back
```

Expected.

```text
No partial committed snapshot
Attempt remains not READY
Retry can prepare safely
```

If rows commit but READY update does not, the implementation has a deterministic reconciliation strategy before allowing the attempt to start.

---

## 116. Database locking strategy

Avoid broad locks.

Prefer row-level lock on one StudentAttempt during Start and Submit, row-level or conditional claim for preparation, short transactions, and indexed predicates.

Avoid locking the entire AssessmentSchedule, locking the entire AssessmentVersion, serializing all students through one application mutex, and long-running transactions during student interaction.

---

## 117. Transaction boundaries

Recommended transaction boundaries.

```text
Create or claim attempt      -> short transaction
Prepare one attempt          -> one bounded transaction
Start one attempt            -> short transaction
Save one response            -> short transaction
Submit one attempt           -> short transaction
Session update               -> short transaction
```

Never hold a transaction while waiting for a remote network call.

---

## 118. Cross-module transaction rule

Do not create a single giant transaction spanning Delivery, Content, Assessment, and Evaluation.

Use application contracts.

P5 reads authoritative source data and persists its own runtime records.

Submission creates a durable handoff to Evaluation rather than requiring synchronous evaluation inside the Delivery transaction.

---

## 119. Cache strategy

Safe candidates for caching.

```text
Resolved delivery configuration where immutable
Published assessment metadata
Published question content for safe read optimization
Lookup values
```

Do not rely on cache as the only source for StudentResponse, StudentAttempt state, AssessmentSession state, or submission state.

Cache invalidation respects publication and version boundaries.

---

## 120. Runtime observability

Required metrics.

```text
delivery_start_requests_total
delivery_start_success_total
delivery_start_conflicts_total
delivery_start_latency
snapshot_preparation_total
snapshot_preparation_ready_total
snapshot_preparation_failed_total
snapshot_preparation_duration
response_save_total
response_save_failure_total
response_save_latency
response_save_conflict_total
session_recovery_total
session_recovery_failure_total
attempt_submission_total
attempt_submission_failure_total
attempt_submission_latency
attempt_expiry_total
```

Dimensions are controlled to avoid cardinality explosions.

---

## 121. Structured logging

Log request_id, user_id where permitted, attempt_id, session_id, assessment_version_id, schedule_id, operation, result, latency, and error code.

Never log password, JWT, reset token, correct answer, full sensitive payment data, unnecessary student response content, or secret asset credentials.

Use correlation IDs end to end.

---

## 122. Audit requirements

Important business transitions are auditable.

```text
Attempt prepared
Attempt started
Attempt submitted
Session ended or expired
Authorized operator action
Preparation retry or failure
```

Use the existing audit boundary. Do not create a Delivery-specific audit table.

Audit insertion participates in the business transaction for important state transitions.

---

## 123. Security threat model

Threats to test.

```text
Attempt ID substitution
Question ID substitution
Cross-student access
Role escalation
Deadline tampering
Mode tampering
Response tampering
Correct-answer discovery
Asset path disclosure
Replay of submit
Replay of save
Duplicate start
Duplicate session
Concurrent mutation
Stale If-Match
```

Every threat has a server-side control or an explicit residual-risk record.

---

## 124. Attempt ID tampering

Request `/attempts/{victimAttemptId}` from another student yields 403 or 404 according to the platform's resource disclosure convention.

Do not reveal whether another student's attempt exists unnecessarily.

---

## 125. Question ID tampering

Request `/attempts/myAttempt/questions/questionNotAssigned` does not return arbitrary QuestionVersion content.

The backend resolves AttemptQuestion and rejects if no assignment exists.

---

## 126. Deadline tampering

The backend ignores or rejects client attempts to alter startedOn, serverTime, deadline, duration, or attemptMode.

The client receives these as server-controlled runtime values.

---

## 127. Correct answer protection

Student runtime APIs never expose QuestionOption.isCorrect, answer_key, expected response, or evaluation internals.

The browser operates only on the question presentation contract.

---

## 128. Submission integrity

At submission.

```text
Server checks deadline
Server checks attempt state
Server checks ownership
Server applies final persistence rules
Server commits terminal state
```

A client-supplied `confirmed = true` is confirmation intent, not authorization to bypass business rules.

---

## 129. Runtime content validation regression

P5 end-to-end tests include plain text question, currency such as `$500`, inline math, block math, formula plus asset, image option, long text, special characters, malformed math, question with no options, single-select, multi-select where supported.

Math delimiters do not confuse currency rendering.

---

## 130. Frontend loading states

Required UI states.

```text
Preparing
Starting
Loading runtime
Saving
Saved
Save failed
Recovering
Offline
Timer warning
Expired
Submitting
Submitted
Configuration error
System error
```

The UI does not expose internal implementation details to the student.

---

## 131. Question navigation UX

The UI supports according to configuration question palette, previous and next, section navigation, mark for review, clear response, progress indicator, and question status.

Controls are disabled or hidden based on capability, but the backend still enforces the policy.

---

## 132. Timer UX

The runtime timer displays remaining time, warns near configured thresholds, resynchronizes after network responses, handles browser sleep and wake, shows expired state, prevents false extension.

The timer component is presentation-only.

---

## 133. Submission UX

Before submission.

```text
Show confirmation according to policy
Show answered and unanswered counts where permitted
Show review-marked count
Allow cancellation if policy permits
```

After successful submission.

```text
Make runtime read-only
Clear active mutation queue
Show authoritative submitted state
Navigate to approved post-submit view
```

Do not re-enable answer editing after successful submission.

---

## 134. Session recovery UX

On reload or network reconnect.

```text
Recover existing session
Restore server-backed state
Show timer immediately
Show persisted responses
Show current or allowed question
```

Do not force the user to start a new attempt if a valid recoverable attempt exists.

---

## 135. Runtime state model in frontend

Separate serverState, uiState, and networkState.

Example.

```text
serverState:
  attemptStatus
  responses
  timer
  sessionStatus

uiState:
  selectedQuestion
  dialogOpen
  localUnsavedResponse

networkState:
  offline
  retrying
  mutationPending
```

Never mix these into one undifferentiated state object.

---

## 136. Test pyramid

P5 requires unit tests, repository tests, PostgreSQL integration tests, API contract tests, security tests, concurrency tests, frontend unit and component tests, end-to-end tests, load tests, and failure-injection tests.

Tests exercise state transitions and race conditions, not only happy-path screens.

---

## 137. Unit test scope

Unit test attempt eligibility, snapshot readiness, mode enforcement, navigation policies, timer calculations, submission policy, delivery configuration resolution, response validation, attempt state transitions, session state transitions, and stale-write behavior.

Pure policy logic is testable without PostgreSQL.

---

## 138. Repository test scope

Verify unique attempt and question, unique attempt and order, unique attempt-question and option, unique attempt-question-option and order, attempt lookup by user, schedule lookup, active session lookup, response upsert, conditional state transitions, and preparation claiming.

Use real PostgreSQL in integration tests.

---

## 139. API test scope

Verify authenticated access, authorization, start, not-ready conflict, attempt questions, single question, save response with ETag, get responses, recover, end session, submit, duplicate submit, invalid question ID, attempt ownership, expired attempt, and stale If-Match handling.

Use the frozen paths exactly.

---

## 140. Concurrency test scope

Include 2 simultaneous starts, 100 simultaneous starts, 5,000 simultaneous starts, 2 simultaneous submits, duplicate response save, submit and save race, recovery and save race, worker claim race, worker retry race.

The expected result is deterministic business state, not merely HTTP success.

---

## 141. End-to-end pilot flow

Minimum.

```text
Publish QuestionVersions
   |
   v
Publish BlueprintVersion
   |
   v
Publish AssessmentVersion
   |
   v
Create EXAM schedule
   |
   v
Prepare candidate attempts
   |
   v
Snapshot READY
   |
   v
Student Start
   |
   v
Load questions
   |
   v
Answer questions
   |
   v
Autosave
   |
   v
Navigate
   |
   v
Reload and recover
   |
   v
Submit
   |
   v
Attempt SUBMITTED
   |
   v
Evaluation handoff observed
```

Run the same lifecycle for PRACTICE.

---

## 142. Version reproducibility end-to-end

Create Question V1, Assessment V1, and Schedule V1.

Prepare Attempt A.

Then publish Question V2 and Assessment V2.

Verify Attempt A still returns QuestionVersion V1, original order, original marks, original negative marks, and original option order.

Delivery never drifts to V2.

---

## 143. Snapshot integrity end-to-end

For a 100-question assessment verify.

```text
AttemptQuestion count = 100
AttemptQuestion display order is unique
QuestionVersion IDs match published AssessmentQuestion set
AttemptQuestionOption ordering matches prepared snapshot
```

Then verify repeated reads return the same ordering.

---

## 144. Practice and exam end-to-end

Verify PRACTICE attempt_mode persists, EXAM attempt_mode persists, client cannot switch mode, practice duration follows policy, exam duration is enforceable, configuration differs only through normalized policy.

Do not introduce exam-name branches to pass the test.

---

## 145. Security end-to-end

Attempt student A to student B attempt, student to correct-answer field, student to unassigned question, student to publish endpoint, student to preparation trigger, and student to another organization resource.

All fail according to the approved security matrix.

---

## 146. Concurrent-user acceptance gate

The pilot gate.

```text
5,000 concurrent users
Applicable p95 under 100 ms
Greater than 99% submissions
Less than 1% exam failure
```

In addition, the team records start burst, autosave burst, submission burst, recovery, database health, application health, and error breakdown.

A passing average latency without concurrency and race evidence is insufficient.

---

## 147. Resource SLO evidence

Retain evidence for API p50, p95, and p99, throughput, HTTP error rate, DB CPU, DB memory, DB I/O, connections, PgBouncer pool stats, lock waits, deadlocks, WAL generation, application CPU, heap, GC, container restarts, and worker queue depth.

The exact infrastructure size is recorded with the load result.

---

## 148. Operational dashboard

Minimum Delivery dashboard.

```text
Snapshot readiness
Active attempts
Active sessions
Start rate
Start latency
Autosave rate
Autosave failures
Recovery rate
Submission rate
Submission failures
Expired attempts
Worker failures
Database saturation
```

---

## 149. Alerts

Alert candidates.

```text
Snapshot READY coverage below threshold
Snapshot FAILED spike
Start latency threshold breached
Response-save failure spike
Submission failure spike
Database connection saturation
Deadlock spike
Redis unavailable
Worker backlog
Application error-rate spike
```

Thresholds are operational settings derived from pilot baselines.

---

## 150. Deployment strategy

Deploy Delivery behind a controlled release.

Required: database migration verified, health checks, readiness checks, rollback plan, versioned deployment, load test evidence, smoke test, monitoring.

No manual production database edits.

---

## 151. Migration rule

P5 uses Flyway.

Do not edit production schema manually, change columns directly in production, create undocumented indexes, or add convenience columns without approval.

Any future change requires a new Flyway migration and architecture review.

---

## 152. Section-wise timer semantics

Section-wise timer semantics require careful persistence design.

The pilot may use only section-timing behavior that can be proven correct from the frozen persistence model.

A separate schema and contract amendment is required before enabling behavior that requires new persisted state.

---

## 153. Schedule end versus attempt deadline

The relationship between schedule end_time and attempt started_on plus duration is defined during implementation.

The chosen policy is documented before production use.

---

## 154. Delivery policy persistence and resolution source

The delivery_policy referenced by the AssessmentVersion provides reusable delivery behaviour.

Resolution chain.

```text
AssessmentVersion.delivery_policy_id
        |
        v
DeliveryPolicy
        |
        v
ExamEngine
        |
        v
DeliveryConfiguration
```

---

## 155. No schema invention rule

An implementation does not add a snapshot_job table, add a response_version column, add a timer_event table, add a section_timer table, add an enrollment table, add a delivery_policy_id column, or add a heartbeat endpoint merely because implementation would feel easier.

First create a decision record and establish that the frozen model cannot safely express the required behavior.

---

## 156. Runtime anti-patterns

Reject snapshot creation during Start, browser-controlled deadlines, frontend authorization, correct answers in payloads, exam-name branching in React, JPA entity exposure, N+1 question loading, global synchronization locks, unbounded retry loops, Redis-only attempt state, synchronous heavy evaluation on submit, cross-module repository access, and long database transactions around network calls.

---

## 157. P5 implementation task breakdown

Execute one task at a time with checkpoints.

```text
P5-01  Create Delivery module skeleton
P5-02  Create runtime domain entities
P5-03  Create repositories and projections
P5-04  Implement DeliveryPolicy contract
P5-05  Implement ExamEngine registry integration
P5-06  Implement DeliveryConfiguration validation
P5-07  Implement candidate eligibility contract
P5-08  Implement attempt creation
P5-09  Implement snapshot claim
P5-10  Implement snapshot preparation
P5-11  Implement snapshot retry and reconciliation
P5-12  Implement snapshot integrity checks
P5-13  Implement Start Exam
P5-14  Implement session create and activate
P5-15  Implement attempt read APIs
P5-16  Implement question read APIs
P5-17  Implement response validation and save with ETag
P5-18  Implement stale-write precondition handling
P5-19  Implement recovery
P5-20  Implement navigation enforcement
P5-21  Implement timer enforcement
P5-22  Implement submission
P5-23  Implement evaluation handoff
P5-24  Implement runtime frontend
P5-25  Implement observability
P5-26  Implement security tests
P5-27  Implement concurrency tests
P5-28  Implement concurrent-user load tests
P5-29  Run pilot end-to-end
P5-30  Produce P5 release evidence
```

Do not hand all 30 tasks to an autonomous implementation as an unbounded request.

---

## 158. P5-01 foundation acceptance

```text
[ ] Delivery module exists
[ ] Boundaries established
[ ] Package layout follows architecture
[ ] No cross-module repository access
[ ] CI green
[ ] OpenAPI baseline preserved
```

---

## 159. P5-02 to P5-06 acceptance

```text
[ ] Entities map to frozen schema
[ ] Repositories use correct indexes
[ ] Delivery policy validated
[ ] ExamEngine registry wired
[ ] DeliveryConfiguration schema validated
[ ] No exam-name UI branches
```

---

## 160. P5-07 to P5-12 acceptance

```text
[ ] Candidate source chosen and documented
[ ] Attempt creation works
[ ] Claim is race-safe
[ ] Snapshot is atomic
[ ] Retries are idempotent
[ ] READY only after integrity validation
[ ] 100-question snapshot passes deterministic verification
```

---

## 161. P5-13 to P5-23 acceptance

```text
[ ] Start does no snapshot inserts
[ ] NOT_STARTED to IN_PROGRESS is race-safe
[ ] Session is created or activated
[ ] Question APIs use snapshot
[ ] Response save is idempotent with ETag
[ ] Stale-write precondition handling is implemented
[ ] Recovery returns same attempt
[ ] Timer is server authoritative
[ ] Navigation enforced server-side
[ ] Submit is idempotent
[ ] Evaluation handoff is durable and idempotent
```

---

## 162. P5-24 frontend acceptance

```text
[ ] Runtime is engine-agnostic
[ ] Configuration is Zod-validated
[ ] Timer renders server-backed time
[ ] Palette state is server-backed
[ ] Autosave status visible
[ ] Failures recover safely
[ ] Reload recovery works
[ ] Submit state is terminal
[ ] No correct answers exposed
[ ] No raw asset paths exposed
[ ] Stale-write precondition handled
```

---

## 163. P5-25 observability acceptance

```text
[ ] Metrics registered
[ ] Structured logs
[ ] Request correlation
[ ] Snapshot dashboard
[ ] Delivery dashboard
[ ] Submission dashboard
[ ] Alerts configured
[ ] No sensitive data in logs
```

---

## 164. P5-26 to P5-30 acceptance

```text
[ ] Security suite passes
[ ] Concurrency suite passes
[ ] Failure-injection suite passes
[ ] Concurrent-user start burst tested
[ ] Concurrent-user autosave load tested
[ ] Concurrent-user submission burst tested
[ ] Concurrent-user recovery tested
[ ] Pilot end-to-end passes
[ ] Evidence stored
[ ] CI green
```

---

## 165. Recommended implementation prompt

```text
You are implementing P5 Assessment Delivery and Runtime Examination
Engine for the Smart Competitive Examination Platform.

Read the full specification package before modifying the repository.

P5 owns:
AssessmentSession
StudentAttempt
AttemptQuestion
AttemptQuestionOption
StudentResponse
DeliveryPolicy
Snapshot preparation
Start Exam
Runtime navigation
Timer
Autosave
Recovery
Submission
Delivery observability

NON-NEGOTIABLE:
1.  Do not invent tables.
2.  Do not silently add columns.
3.  Do not silently add public API endpoints.
4.  Do not modify published P4 artifacts.
5.  Do not resolve latest QuestionVersion dynamically.
6.  Do not generate snapshots from Start Exam.
7.  Do not trust browser timer values.
8.  Do not expose correct answers.
9.  Do not expose private asset paths.
10. Do not put exam-name branching in React.
11. Do not access another module's repository directly.
12. Use application contracts.
13. Use PostgreSQL as runtime source of truth.
14. Redis is cache and coordination only, never authoritative attempt
    state.
15. Start must be lightweight.
16. Submit must be idempotent.
17. Response save must be idempotent with HTTP conditional requests.
18. Recovery must not reconstruct snapshots.
19. AssessmentSession is not StudentAttempt.
20. attempt_mode is immutable.
21. AttemptQuestion and AttemptQuestionOption are immutable after
    preparation.
22. Question ordering and scoring come from the runtime snapshot.
23. Run PostgreSQL integration tests.
24. Run security tests.
25. Run concurrency tests.
26. Run realistic concurrent-user load tests.
27. Do not invent persistence for unresolved section timers, candidate
    registration, or DeliveryPolicy linkage.
28. Keep OpenAPI aligned with the frozen contract.
29. Keep MARKDOWN_LATEX_V1 rendering and secure asset authorization
    intact.
30. Stop and report rather than silently resolving frozen-contract
    gaps.

Implementation order:
P5-01 through P5-30.

For each task:
- Inspect existing code first.
- Implement the smallest correct change.
- Run focused tests.
- Run relevant integration tests.
- Report changed files.
- Report migration impact.
- Report API impact.
- Report unresolved contract issues.
- Stop for review before the next high-risk task.

Before merge:
- Migration verification
- OpenAPI verification
- Security verification
- Snapshot determinism test
- Start Exam no-snapshot-write test
- Response idempotency test with ETag
- Submit idempotency test
- Recovery test
- Timer enforcement test
- Concurrent-user load evidence
- CI green
```

---

## 166. Review questions

Before approving any P5 change, ask.

```text
Did this change add schema?
Did this change add an endpoint?
Did this change modify a published artifact?
Did this change move work into Start Exam?
Did this change trust the browser clock?
Did this change expose correct answers?
Did this change introduce an exam-name branch?
Did this change access another module's repository?
Can the operation be retried safely?
What happens when two requests arrive simultaneously?
What happens when PostgreSQL is temporarily unavailable?
What happens after browser reload?
What happens after worker crash?
What happens at scale?
Can a stale If-Match overwrite a newer response?
```

---

## 167. Code review checklist

```text
[ ] DTO -> Service -> Repository layering
[ ] Transaction boundaries are short
[ ] No N+1 runtime question loading
[ ] Indexes are used
[ ] No broad database locks
[ ] Conditional state transitions
[ ] Snapshot atomicity
[ ] Snapshot readiness integrity
[ ] Response upsert
[ ] Stale-write strategy enforced
[ ] Server timer
[ ] Submit idempotency
[ ] Recovery correctness
[ ] RBAC
[ ] Ownership checks
[ ] Correct answer protection
[ ] Asset authorization
[ ] Logging safe
[ ] Metrics present
[ ] OpenAPI correct
```

---

## 168. P5 release evidence package

Retain database migration output, OpenAPI specification, permission matrix, decision records, unit test report, integration test report, security test report, concurrency test report, end-to-end report, load test report, snapshot integrity evidence, Start Exam query and latency evidence, autosave and write-load evidence, submission evidence, recovery evidence, monitoring dashboard exports, known limitations, and rollback plan.

This evidence is part of the definition of done.

---

## 169. Pilot readiness gate

P5 is pilot-ready only when.

```text
[ ] Published P4 assessment can be prepared
[ ] Eligible student attempts can be identified
[ ] Snapshots reach READY before window
[ ] Start Exam is lightweight
[ ] Concurrent-user start burst has evidence
[ ] Autosave load has evidence
[ ] Submission burst has evidence
[ ] Recovery has evidence
[ ] Timer enforcement is server-side
[ ] Response saves are reliable
[ ] Submission is idempotent
[ ] Stale-write precondition enforced
[ ] Security tests pass
[ ] Runtime frontend is engine-agnostic
[ ] No P5 critical decision record remains unresolved
[ ] CI and CD are green
```

---

## 170. Final engineering principle

> Prepare once, execute many.

```text
Published AssessmentVersion
        |
        v
Scheduled candidate attempts
        |
        v
Async immutable snapshots
        |
        v
Lightweight Start
        |
        v
Server-authoritative runtime
        |
        v
Idempotent autosave with stale-write protection
        |
        v
Recoverable session
        |
        v
Exactly-once logical submission
        |
        v
Evaluation handoff
```

P5 makes runtime execution boring, deterministic, observable, and resilient even when thousands of students behave simultaneously.

---

## 171. P5 handoff to P6

P5 hands P6 a submitted StudentAttempt, submission timestamp, exact AssessmentVersion, exact AssessmentSchedule, attempt mode, immutable AttemptQuestion set, immutable AttemptQuestionOption set, student responses, and runtime timing fields available in the frozen schema.

P6 owns AnswerKey, AnswerKeyDetail, Evaluation, Result, and Scorecard.

P5 does not compute or publish official results.

---

## 172. No surprises rule

An implementation stops and requests architecture review whenever it encounters a requirement that appears to need a new table, a new column, a new public endpoint, a new runtime event store, a new timer persistence model, a new enrollment model, a new DeliveryPolicy foreign key, or a new response versioning field.

The correct response is to identify the gap, document the impact, create or extend a decision record, obtain architecture approval, then implement.

Never hide a product requirement gap inside an implementation shortcut.

---

## 173. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P5 — Assessment Delivery and Runtime Examination Engine.

**Next implementation document:** P6 — Evaluation and Results.