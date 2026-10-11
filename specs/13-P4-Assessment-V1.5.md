# Smart Competitive Examination Platform
## P4 — Blueprint & Assessment Management Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P4 — Blueprint & Assessment Management
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, ExamEngine and CDUI Contract
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

This document converts the Assessment domain into an implementation-ready engineering specification.

P4 turns reusable published question content into a deterministic assessment definition.

The domain supports:

```text
Blueprint authoring
Blueprint versioning
Blueprint validation
Blueprint publication
Assessment authoring
Assessment versioning
Question selection
Section assignment
Assessment-specific marks
Assessment-specific negative marks
Question ordering
Rule-based and hybrid selection
Assessment validation
Assessment preview
Assessment publication
Assessment scheduling
Assessment collections
Exam engine selection
```

P4 does not own:

```text
Student attempts
Runtime sessions
Student responses
Snapshot preparation
Server-side timer enforcement
Runtime navigation
Evaluation
Results
Payments
Notifications
```

---

## 2. Source-of-truth hierarchy

Read these documents before P4 implementation:

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
- This document

Frozen P4 entities:

```text
Blueprint
BlueprintVersion
Assessment
AssessmentVersion
AssessmentQuestion
AssessmentSchedule
AssessmentCollection
CollectionAssessment
AssessmentSelectionRule
```

The frozen schema includes `assessment_schedule.mode` with values:

```text
PRACTICE
EXAM
```

Published BlueprintVersions and AssessmentVersions are immutable. AssessmentQuestion points to exact QuestionVersions. Schedules point to a specific AssessmentVersion. Delivery performs runtime snapshot preparation rather than Start Exam.

---

## 3. Architectural boundary

```text
Academic
   |
   v
Content
   |
   v
Blueprint and Assessment
   |
   v
Delivery
   |
   v
Evaluation
```

Blueprint answers: what should this examination contain?

AssessmentVersion answers: what exact published examination definition will Delivery execute?

Delivery answers: how is this definition executed for a specific student?

Evaluation answers: what is the official result?

---

## 4. Pilot-first scope

P4 is part of the pilot-critical path.

Required for pilot:

```text
Blueprint
BlueprintVersion
Assessment
AssessmentVersion
AssessmentQuestion
Assessment validation
Assessment preview
Assessment publication
AssessmentSchedule
Basic AssessmentCollection
AssessmentSelectionRule
Exam engine selection
```

Not pulled forward:

```text
Commerce
Communication
Full administration
Advanced analytics
Advanced reporting
AI capabilities
```

Existing deferred schema remains untouched.

---

## 5. Frozen database schema

### 5.1 Table: blueprint

```sql
blueprint
---------
id UUID PK
code VARCHAR(50)
name VARCHAR(150)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 5.2 Table: blueprint_version

```sql
blueprint_version
-----------------
id UUID PK
blueprint_id UUID FK
version INTEGER
configuration JSONB
is_published BOOLEAN
published_by UUID
published_on TIMESTAMPTZ
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(blueprint_id, version)`.

Index: `idx_blueprint_version_blueprint_id`.

### 5.3 Table: assessment

```sql
assessment
----------
id UUID PK
code VARCHAR(50)
name VARCHAR(200)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 5.4 Table: assessment_version

```sql
assessment_version
------------------
id UUID PK
assessment_id UUID FK
blueprint_version_id UUID FK
version INTEGER
total_questions INTEGER
total_marks NUMERIC(8,2)
duration_minutes INTEGER
engine_type VARCHAR(20)
engine_configuration_version INTEGER
delivery_policy_id UUID FK
is_published BOOLEAN
published_by UUID
published_on TIMESTAMPTZ
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(assessment_id, version)`.

Check: `engine_type IS NULL OR engine_type IN ('TCS_ION','IBPS','NTA','UPSC','RAILWAY','CUSTOM')`.

Indexes.

```text
idx_assessment_version_assessment_id
idx_assessment_version_blueprint_version_id
idx_assessment_version_engine_type
idx_assessment_version_delivery_policy_id
```

### 5.5 Table: assessment_question

```sql
assessment_question
-------------------
id UUID PK
assessment_version_id UUID FK
question_version_id UUID FK
display_order INTEGER
section_name VARCHAR(150)
marks NUMERIC(8,2)
negative_marks NUMERIC(8,2)
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Indexes.

```text
idx_assessment_question_assessment_version_id
idx_assessment_question_question_version_id
```

The model may enforce `UNIQUE(assessment_version_id, question_version_id)` when a QuestionVersion appears only once within an AssessmentVersion.

### 5.6 Table: assessment_selection_rule

```sql
assessment_selection_rule
-------------------------
id UUID PK
assessment_version_id UUID FK
section_name VARCHAR(150)
target_count INTEGER
rule_configuration JSONB
display_order INTEGER
materialized_on TIMESTAMPTZ
materialized_question_count INTEGER
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(assessment_version_id, section_name)`.

Check: `target_count > 0`.

Index: `idx_assessment_selection_rule_version`.

### 5.7 Table: assessment_schedule

```sql
assessment_schedule
-------------------
id UUID PK
assessment_version_id UUID FK
mode VARCHAR(20)
start_time TIMESTAMPTZ
end_time TIMESTAMPTZ
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Indexes.

```text
idx_assessment_schedule_assessment_version_id
idx_assessment_schedule_start_time
idx_assessment_schedule_status
```

Mode: PRACTICE or EXAM.

### 5.8 Table: assessment_collection

```sql
assessment_collection
---------------------
id UUID PK
code VARCHAR(50)
name VARCHAR(200)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 5.9 Table: collection_assessment

```sql
collection_assessment
---------------------
id UUID PK
collection_id UUID FK
assessment_id UUID FK
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(collection_id, assessment_id)`.

Indexes.

```text
idx_collection_assessment_collection_id
idx_collection_assessment_assessment_id
```

---

## 6. Domain relationships

```text
Blueprint
   |
   v
BlueprintVersion

Assessment
   |
   v
AssessmentVersion
   |
   +-- N:1 BlueprintVersion
   |
   +-- 1:N AssessmentQuestion
   |          |
   |          v
   |     QuestionVersion
   |
   +-- 1:N AssessmentSchedule
   |
   +-- 1:N AssessmentSelectionRule

AssessmentCollection
   |
   v
M:N Assessment through CollectionAssessment
```

---

## 7. Core principle

> Blueprint defines the intended assessment structure; AssessmentVersion binds that structure to exact published QuestionVersions; Delivery executes the immutable result.

This separation is non-negotiable.

---

## 8. Blueprint configuration

The frozen schema uses `configuration JSONB`.

Illustrative shape.

```json
{
  "sections": [
    {
      "name": "General Intelligence",
      "displayOrder": 1,
      "questionCount": 25,
      "marks": 50,
      "durationMinutes": 20
    }
  ],
  "navigation": {
    "allowSectionSwitch": true
  },
  "calculator": {
    "enabled": false
  }
}
```

This example is illustrative, not a complete frozen JSON schema.

P4 defines a typed internal configuration model before production publication.

---

## 9. Typed configuration model

Recommended internal representation.

```java
record BlueprintConfiguration(
    List<BlueprintSection> sections,
    NavigationConfiguration navigation,
    TimerConfiguration timer,
    SubmissionConfiguration submission,
    ValidationConfiguration validation,
    PresentationConfiguration presentation,
    CalculatorConfiguration calculator,
    LanguageConfiguration language
) {}
```

These types are implementation-level structures. Database tables for each configuration section are not added.

---

## 10. Configuration must be declarative

Forbidden.

```json
{
  "javascript": "...",
  "renderScript": "...",
  "component": "SomeReactComponent"
}
```

Configuration is data. It is never executable code.

This preserves the ExamEngine and CDUI boundary.

---

## 11. Configuration parsing pipeline

```text
JSONB
  |
  v
Typed deserialization
  |
  v
Field validation
  |
  v
Cross-field validation
  |
  v
Business validation
  |
  v
Publish validation
```

Malformed JSON never reaches business logic as an untyped map if a typed contract has been established.

---

## 12. Section model

A section contains at minimum the concepts shown by the source.

```text
name
displayOrder
questionCount
marks
durationMinutes
```

The implementation may expose more fields only if the approved configuration contract requires them.

---

## 13. Section name

Section name is a user-visible label.

Examples.

```text
Reasoning
Quantitative Aptitude
Physics
Chemistry
General Intelligence
```

These are examples. Do not hard-code a fixed section catalog.

---

## 14. Section order

`displayOrder` is ascending.

Platform ordering permits gaps. Example: 1, 2, 5, 10.

For a published AssessmentVersion, actual AssessmentQuestion order remains deterministic.

---

## 15. Section question count

Normal sections have `questionCount > 0`.

A draft can temporarily be incomplete. Publication enforces final consistency.

---

## 16. Section marks

Section marks are part of blueprint structure.

The implementation reconciles configured section marks with materialized AssessmentQuestion marks according to the approved scoring contract.

Client-calculated totals are not trusted.

---

## 17. Section duration

`durationMinutes` controls intended section timing when section-wise timing is configured.

The exact runtime semantics are resolved through the ExamEngine and CDUI. P4 validates structural consistency. Delivery enforces runtime timing.

---

## 18. Global duration

AssessmentVersion stores `duration_minutes`.

Publication validates compatibility between blueprint configuration, AssessmentVersion duration, and ExamEngine timer semantics.

The frontend does not duplicate timer authority.

---

## 19. Navigation configuration

Blueprint may contain navigation intent such as the following.

```json
{
  "navigation": {
    "allowSectionSwitch": true
  }
}
```

The normalized runtime contract is later produced by the ExamEngine.

P4 does not enforce runtime navigation.

---

## 20. Calculator configuration

Blueprint may contain.

```json
{
  "calculator": {
    "enabled": false
  }
}
```

The frontend later receives this as a normalized capability. Raw blueprint JSON is not sent directly to the student UI as the authority.

---

## 21. Language configuration

The platform supports reusable Language masters.

Blueprint language configuration references the approved Language contract.

Do not hard-code ENGLISH, HINDI, or MARATHI as an exhaustive enum.

The exact JSON shape is frozen before publication.

---

## 22. Blueprint version immutability

Published BlueprintVersion is immutable.

Allowed.

```text
V1 Draft
  |
  v
V1 Published
```

Correction.

```text
V1 Published
  |
  v
Create V2
  |
  v
Edit V2
  |
  v
Validate
  |
  v
Publish V2
```

V1 is never edited after publication.

---

## 23. Assessment version immutability

Published AssessmentVersion is immutable.

Do not change:

```text
BlueprintVersion reference
QuestionVersions
Order
Section
Marks
Negative marks
Duration
Engine type
Delivery policy
```

after publication.

---

## 24. AssessmentQuestion as historical binding

AssessmentQuestion is the bridge.

```text
AssessmentVersion
+
exact QuestionVersion
```

It freezes assessment-specific values.

```text
Order
Section
Marks
Negative marks
```

This prevents QuestionVersion defaults from being mistaken for the actual scoring configuration of a specific examination.

---

## 25. Exact QuestionVersion rule

Published AssessmentVersion references `question_version_id`, not `question_id`, and not `latest version`.

Historical reproducibility depends on the exact version ID.

---

## 26. Content drift protection

Example.

```text
Question Q100

V3 = Published
V4 = Draft
```

If Assessment V1 uses Q100 V3, creating V4 cannot alter Assessment V1.

---

## 27. Blueprint drift protection

Example.

```text
Blueprint V1 = Published
Blueprint V2 = Draft
```

An AssessmentVersion referencing V1 remains bound to V1.

---

## 28. Assessment lifecycle

The logical Assessment lifecycle is distinct from version publication.

Operational concepts.

```text
DRAFT
PUBLISHED
SCHEDULED
CLOSED
```

The exact status set follows the active API and database baseline. Do not create new persistent statuses solely for UI labels.

---

## 29. AssessmentVersion draft

Draft versions can be incomplete.

Examples.

```text
Missing questions
Wrong counts
Wrong totals
Missing engine type
```

This is acceptable during authoring. Publication rejects invalid versions.

---

## 30. Assessment creation modes

Manual, rule-based, hybrid.

These are authoring modes. Once an AssessmentVersion is published, the final result is concrete.

---

## 31. Manual creation mode

```text
Published QuestionVersion pool
       |
       v
Staff selects questions
       |
       v
AssessmentQuestion rows
       |
       v
Section assignment
       |
       v
Scoring
       |
       v
Order
       |
       v
Validation
       |
       v
Publication
```

---

## 32. Rule-based creation mode

```text
Selection rules
       |
       v
Eligible QuestionVersions
       |
       v
Selection algorithm
       |
       v
Materialized AssessmentQuestion rows
       |
       v
Validation
       |
       v
Publication
```

Delivery never reruns authoring selection rules.

---

## 33. Hybrid creation mode

```text
Manual questions
+
Rule-selected questions
        |
        v
Combined concrete AssessmentQuestion set
        |
        v
Validation
        |
        v
Publication
```

---

## 34. Deterministic publication

A published AssessmentVersion is deterministic.

For rule-based and hybrid builds.

```text
Published version
     |
     v
Fixed AssessmentQuestion set
     |
     v
Fixed order
```

Do not depend on current Question Bank state after publication.

---

## 35. Question selection eligibility

Only eligible published QuestionVersions may be selected for publication.

Minimum:

```text
QuestionVersion exists
QuestionVersion published
QuestionVersion status eligible
Template supported
Required content valid
Taxonomy compatible with the section
Language compatible
```

---

## 36. No draft questions in published assessment

Forbidden.

```text
AssessmentVersion published
    +
QuestionVersion draft
```

Publication must fail.

---

## 37. Question count validation

Required.

```text
Actual AssessmentQuestion count
=
Sum of section question counts
```

Example.

```text
Required = 100
Actual = 98
```

must fail with a structured mismatch error.

---

## 38. Section count validation

For each blueprint section.

```text
Actual questions in section
=
Configured section questionCount
```

Drafts may temporarily violate this. Published versions may not.

---

## 39. Assessment total questions

AssessmentVersion stores `total_questions`.

The server calculates the authoritative value from materialized AssessmentQuestion rows.

The frontend is not trusted.

---

## 40. Assessment total marks

AssessmentVersion stores `total_marks`.

The server validates it against the actual AssessmentQuestion scoring configuration.

---

## 41. Negative marking validation

AssessmentQuestion stores `negative_marks`.

Typical.

```text
0
0.25
0.50
1.00
```

The product supports configurable negative marking. P4 does not calculate student scores.

---

## 42. Marks validation

AssessmentQuestion `marks NUMERIC(8,2)` is non-negative under the normal model.

Exact decimal arithmetic is preserved.

---

## 43. Question ordering

AssessmentQuestion `display_order` defines authoring and published order.

Reorder endpoint.

```http
PUT /api/v1/assessment-versions/{versionId}/questions/order
```

Deterministic ordering is required before publication.

---

## 44. Section assignment

Every AssessmentQuestion intended for publication maps to a valid blueprint section.

Reject section names not present in the blueprint.

Do not allow arbitrary orphan section labels.

---

## 45. Assessment selection rule configuration

Rule configuration filters by taxonomy, difficulty, tags, language, and published-only status.

Example.

```json
{
  "templateCode": "SINGLE_CHOICE",
  "examId": "<uuid>",
  "subjectId": "<uuid>",
  "chapterId": "<uuid>",
  "topicId": "<uuid>",
  "difficulty": ["EASY", "MEDIUM"],
  "tagIds": ["<uuid>"],
  "languageId": "<uuid>",
  "excludeQuestionIds": ["<uuid>"],
  "excludeQuestionVersionIds": ["<uuid>"],
  "publishedOnly": true
}
```

At least one of `examId`, `subjectId`, `topicId`, or `tagIds` must be present so a rule does not accidentally target the entire question bank.

---

## 46. Materialization algorithm

```text
1. Load all eligible published QuestionVersions matching filters.
2. Exclude any QuestionVersion already used in this AssessmentVersion.
3. Apply deterministic ordering:
   a. Difficulty: EASY, then MEDIUM, then HARD
   b. Within a difficulty bucket: least-recently-used in prior assessments
   c. Tie-break by QuestionVersion.id ascending
4. Take exactly target_count items.
5. If insufficient, fail with QUESTION_POOL_SHORTAGE including the deficit.
6. Persist AssessmentQuestion rows.
7. Record materialized_on and materialized_question_count on the rule.
```

---

## 47. Determinism guarantee

For the same rule configuration, the same published QuestionVersion pool, and the same prior-use state, materialization produces the same result.

Use a deterministic ordering seed derived from `assessment_version_id` and `section_name` to keep tie-breaks stable across retries.

---

## 48. Idempotent materialization

Re-running materialization on an AssessmentVersion whose rules have already been materialized returns the same question set.

Rules with `materialized_on IS NOT NULL` are skipped on re-run.

---

## 49. Rule shortage response

```json
{
  "success": false,
  "message": "Question pool shortage for section: Physics",
  "errors": [
    {
      "code": "QUESTION_POOL_SHORTAGE",
      "sectionName": "Physics",
      "requested": 30,
      "available": 24,
      "deficit": 6
    }
  ]
}
```

---

## 50. Engine persistence

`assessment_version` carries.

```text
engine_type
engine_configuration_version
delivery_policy_id
```

Supported engine identifiers.

```text
TCS_ION
IBPS
NTA
UPSC
RAILWAY
CUSTOM
```

Publication requires both `engine_type` and `delivery_policy_id`.

Engine type is immutable after publication. A change requires a new AssessmentVersion.

Existing published assessment versions that predate the field are backfilled with `engine_type = 'CUSTOM'`.

---

## 51. ExamEngine resolution flow

```text
AssessmentVersion.engine_type
        |
        v
ExamEngineRegistry.resolve(type)
        |
        v
ExamEngine implementation
        |
        v
resolveConfiguration(context, attempt)
        |
        v
DeliveryConfiguration
        |
        v
Runtime
```

---

## 52. Blueprint publication API

```http
POST /api/v1/blueprints/{id}/versions/{versionId}/publish
```

Publication:

```text
Validate
 |
 v
Set is_published
 |
 v
Set published_by
 |
 v
Set published_on
 |
 v
Audit
 |
 v
Commit
```

---

## 53. Assessment publication API

```http
POST /api/v1/assessment-versions/{versionId}/publish
```

Server validates blueprint, questions, question versions, marks, negative marks, duration, engine type, delivery policy, and required configuration before publication.

---

## 54. Assessment validation API

```http
POST /api/v1/assessment-versions/{versionId}/validate
```

Response.

```json
{
  "success": true,
  "message": "Assessment validation completed",
  "data": {
    "valid": false,
    "errors": [
      {
        "code": "QUESTION_COUNT_MISMATCH",
        "message": "Assessment requires 100 questions but only 98 are configured"
      }
    ]
  }
}
```

---

## 55. Assessment preview API

```http
GET /api/v1/assessment-versions/{versionId}/preview
```

Preview does not create runtime rows.

Never create AssessmentSession, StudentAttempt, AttemptQuestion, AttemptQuestionOption, or StudentResponse for preview.

---

## 56. Preview requirements

Preview represents assessment, version, blueprint, sections, question order, question content, options, marks, negative marks, duration, and language configuration according to the user's permission.

---

## 57. Preview rendering

Preview uses the P3 rendering contract.

```text
MARKDOWN_LATEX_V1
KaTeX server-side rendering
Safe Markdown
Asset shortcode resolution
Sanitization
```

A permissive preview renderer is not built separately.

---

## 58. AssessmentQuestion APIs

```http
POST   /api/v1/assessment-versions/{versionId}/questions
PUT    /api/v1/assessment-versions/{versionId}/questions/{assessmentQuestionId}
DELETE /api/v1/assessment-versions/{versionId}/questions/{assessmentQuestionId}
PUT    /api/v1/assessment-versions/{versionId}/questions/order
```

Mutation is permitted only while the AssessmentVersion is mutable.

---

## 59. Add question request

```json
{
  "questionVersionId": "<uuid>",
  "displayOrder": 1,
  "sectionName": "General Intelligence",
  "marks": 2,
  "negativeMarks": 0.5
}
```

Server validates every field.

---

## 60. Update question request

Allowed only while AssessmentVersion is draft or mutable.

Server verifies that the specified AssessmentQuestion belongs to the supplied AssessmentVersion.

---

## 61. Remove question request

Removal is not allowed after publication.

Historical published versions are not successfully mutated.

---

## 62. Reorder request

```json
{
  "questionOrders": [
    { "assessmentQuestionId": "<uuid>", "displayOrder": 1 },
    { "assessmentQuestionId": "<uuid>", "displayOrder": 2 }
  ]
}
```

Validate that IDs belong to the version, that there are no duplicate IDs, and that there are no duplicate orders.

Prefer complete ordering for deterministic authoring.

---

## 63. AssessmentSelectionRule API

```http
GET    /api/v1/assessment-versions/{versionId}/rules
POST   /api/v1/assessment-versions/{versionId}/rules
PUT    /api/v1/assessment-versions/{versionId}/rules/{ruleId}
DELETE /api/v1/assessment-versions/{versionId}/rules/{ruleId}
POST   /api/v1/assessment-versions/{versionId}/rules/materialize
```

Create request.

```json
{
  "sectionName": "Physics",
  "targetCount": 30,
  "displayOrder": 1,
  "ruleConfiguration": {
    "templateCode": "SINGLE_CHOICE",
    "examId": "<uuid>",
    "subjectId": "<uuid>",
    "difficulty": ["EASY", "MEDIUM"],
    "publishedOnly": true
  }
}
```

Materialize request.

```json
{
  "mode": "REPLACE_UNMATERIALIZED"
}
```

---

## 64. Assessment API

```http
GET  /api/v1/assessments
GET  /api/v1/assessments/{id}
POST /api/v1/assessments
PUT  /api/v1/assessments/{id}
```

Assessment list filters: status, examId, blueprintId, search.

---

## 65. Blueprint API

```http
GET  /api/v1/blueprints
GET  /api/v1/blueprints/{id}
POST /api/v1/blueprints
PUT  /api/v1/blueprints/{id}

POST /api/v1/blueprints/{id}/versions
GET  /api/v1/blueprints/{id}/versions
GET  /api/v1/blueprints/{id}/versions/{versionId}
POST /api/v1/blueprints/{id}/versions/{versionId}/publish
```

---

## 66. AssessmentVersion API

```http
GET  /api/v1/assessments/{id}/versions
POST /api/v1/assessments/{id}/versions
GET  /api/v1/assessments/{id}/versions/{versionId}
```

Create does not imply publication.

Create request.

```json
{
  "blueprintVersionId": "<uuid>",
  "durationMinutes": 60,
  "engineType": "CUSTOM",
  "deliveryPolicyId": "<uuid>"
}
```

---

## 67. Schedule API

```http
GET  /api/v1/assessment-schedules
POST /api/v1/assessment-schedules
PUT  /api/v1/assessment-schedules/{id}
POST /api/v1/assessment-schedules/{id}/close
```

Filters: assessmentVersionId, status, from, to.

Create request.

```json
{
  "assessmentVersionId": "<uuid>",
  "mode": "EXAM",
  "startTime": "2026-10-10T10:00:00Z",
  "endTime": "2026-10-10T12:00:00Z"
}
```

Schedule preconditions.

```text
AssessmentVersion exists
AssessmentVersion published
startTime present
endTime present
startTime < endTime
mode valid
```

---

## 68. Schedule update

Business rules determine whether a live schedule can be modified.

P4 centralizes the policy in AssessmentScheduleService. It is not duplicated across controller and frontend.

---

## 69. Schedule close

```http
POST /api/v1/assessment-schedules/{id}/close
```

Close is an explicit business action. Closing does not unpublish the AssessmentVersion.

---

## 70. Schedule historical binding

Schedule references `assessment_version_id`. A schedule never automatically follows a later version. A new version requires a new schedule.

---

## 71. Schedule temporal state

The database status is not necessarily the same as computed runtime state.

Runtime UI may derive UPCOMING, OPEN, or CLOSED from status, start_time, and end_time.

A second persistent status field is not created without approval.

---

## 72. Schedule mode

Mode is immutable for the intended schedule context.

```text
PRACTICE
EXAM
```

The browser does not switch an existing schedule's mode during an active attempt.

---

## 73. Practice handoff

P4 produces `mode = PRACTICE`.

Delivery and ExamEngine later interpret navigation, timer, submission, hints, and explanations according to the normalized policy.

---

## 74. Exam handoff

P4 produces `mode = EXAM`.

Delivery later enforces the official rules.

P4 does not contain runtime enforcement logic.

---

## 75. ExamEngine compatibility validation

Before publishing AssessmentVersion, validate that the assessment configuration can be interpreted by the configured ExamEngine.

```text
AssessmentVersion
      |
      v
ExamEngineRegistry
      |
      v
Engine
      |
      v
Compatibility validation
      |
      v
Publish
```

---

## 76. Assessment collection API

```http
GET  /api/v1/assessment-collections
GET  /api/v1/assessment-collections/{id}
POST /api/v1/assessment-collections
PUT  /api/v1/assessment-collections/{id}
```

### 76.1 Add assessment to collection

```http
POST /api/v1/assessment-collections/{collectionId}/assessments
```

### 76.2 Remove assessment from collection

```http
DELETE /api/v1/assessment-collections/{collectionId}/assessments/{assessmentId}
```

---

## 77. Collection membership

Collection groups logical Assessments.

It does not publish, schedule, start, or evaluate. Assessments remain independently managed.

Association is at the logical Assessment level, not the AssessmentVersion level unless the schema is formally changed.

---

## 78. Backend package structure

```text
assessment/
  controller/
  service/
  repository/
  entity/
  dto/
  mapper/
  validation/
  exception/
  selection/
  blueprint/
  runtime/
  publication/
```

Suggested classes.

```text
BlueprintService
BlueprintVersionService
BlueprintValidationService
BlueprintPublicationService

AssessmentService
AssessmentVersionService
AssessmentQuestionService
AssessmentValidationService
AssessmentPublicationService

AssessmentScheduleService
AssessmentCollectionService
CollectionAssessmentService

QuestionSelectionService
AssessmentSelectionService
```

Only create classes and packages that contain real behavior.

---

## 79. Selection strategies

If the implementation uses strategies.

```text
ManualQuestionSelectionStrategy
RuleBasedQuestionSelectionStrategy
HybridQuestionSelectionStrategy
```

Keep them narrow. Do not build an unnecessary generic framework.

---

## 80. Repository layer

Repositories.

```text
BlueprintRepository
BlueprintVersionRepository
AssessmentRepository
AssessmentVersionRepository
AssessmentQuestionRepository
AssessmentSelectionRuleRepository
AssessmentScheduleRepository
AssessmentCollectionRepository
CollectionAssessmentRepository
```

Repositories contain persistence and query behavior only.

---

## 81. Cross-module repository rule

Forbidden.

```text
AssessmentService
    |
    v
QuestionRepository
```

Preferred.

```text
AssessmentService
    |
    v
Content application contract
    |
    v
QuestionVersion summary
```

This preserves modular boundaries.

---

## 82. Content contract needed by P4

Potential contract operations.

```text
verifyPublishedQuestionVersion
getQuestionVersionSummary
validateTemplateCompatibility
getOptionSummariesIfRequired
getContentMetadataForPreview
```

Content JPA entities are not imported directly.

---

## 83. Academic contract needed by P4

Potential operations.

```text
verifyBlueprintAcademicContext
verifySubject
verifyExam
verifyLanguage
```

Use explicit module contracts.

---

## 83.1 Delivery policy contract needed by P4

`delivery_policy` is owned by the Delivery domain.

P4 reads delivery policy existence and metadata through the Delivery application contract rather than directly manipulating the Delivery repository.

---

## 84. AssessmentValidationService

Responsibilities.

```text
Blueprint validation
Question eligibility
Question count
Section counts
Question ordering
Marks
Negative marks
Total marks
Duration
Engine compatibility
Delivery policy presence
Selection rule materialization status
```

No persistence mutation unless explicitly required.

---

## 85. BlueprintValidationService

Responsibilities.

```text
Configuration parsing
Section validation
Question count validation
Marks validation
Timing validation
Navigation validation
Calculator validation
Language validation
```

---

## 86. Publication service

Publication is explicit.

```text
POST /publish
     |
     v
PublicationService
```

Publication is not hidden inside a generic PUT.

---

## 87. DTO standards

Recommended.

```text
BlueprintCreateRequest
BlueprintUpdateRequest
BlueprintResponse
BlueprintListItem

BlueprintVersionCreateRequest
BlueprintVersionResponse
BlueprintVersionListItem
BlueprintValidationResponse

AssessmentCreateRequest
AssessmentUpdateRequest
AssessmentResponse
AssessmentListItem

AssessmentVersionCreateRequest
AssessmentVersionResponse

AssessmentQuestionCreateRequest
AssessmentQuestionUpdateRequest
AssessmentQuestionResponse
ReorderAssessmentQuestionsRequest

AssessmentSelectionRuleCreateRequest
AssessmentSelectionRuleUpdateRequest
AssessmentSelectionRuleResponse
MaterializeSelectionRulesRequest

AssessmentValidationResponse

AssessmentScheduleCreateRequest
AssessmentScheduleUpdateRequest
AssessmentScheduleResponse

AssessmentCollectionCreateRequest
AssessmentCollectionUpdateRequest
AssessmentCollectionResponse
```

---

## 88. DTO rule

Do not expose createdBy, createdOn, updatedBy, or updatedOn as client-controlled write fields.

Backend owns audit data.

---

## 89. API response envelope

Use the frozen standard.

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": {}
}
```

---

## 90. Error envelope

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": []
}
```

---

## 91. Common error classes

```text
VALIDATION_ERROR
NOT_FOUND
CONFLICT
BUSINESS_ERROR
INVALID_STATE
AUTHORIZATION_ERROR
AUTHENTICATION_ERROR
SYSTEM_ERROR
```

---

## 92. Assessment-specific error codes

```text
BLUEPRINT_NOT_FOUND
BLUEPRINT_VERSION_NOT_FOUND
BLUEPRINT_INVALID
BLUEPRINT_NOT_PUBLISHED

ASSESSMENT_NOT_FOUND
ASSESSMENT_VERSION_NOT_FOUND
ASSESSMENT_VERSION_ALREADY_PUBLISHED
ASSESSMENT_VERSION_INVALID

QUESTION_VERSION_NOT_FOUND
QUESTION_VERSION_NOT_PUBLISHED
QUESTION_VERSION_ALREADY_SELECTED
QUESTION_SECTION_INVALID
QUESTION_COUNT_MISMATCH
SECTION_COUNT_MISMATCH
TOTAL_MARKS_MISMATCH
DURATION_INVALID
ENGINE_CONFIGURATION_INVALID
UNSUPPORTED_EXAM_ENGINE
DELIVERY_POLICY_NOT_FOUND

SCHEDULE_NOT_FOUND
SCHEDULE_WINDOW_INVALID
SCHEDULE_MODE_INVALID

COLLECTION_NOT_FOUND
COLLECTION_ASSESSMENT_DUPLICATE

QUESTION_POOL_SHORTAGE
RULE_ALREADY_MATERIALIZED
```

These are implementation error identifiers, not a new database enum.

---

## 93. HTTP status mapping

Use the platform standard.

```text
200
201
204
400
401
403
404
409
412
422
500
```

Examples.

```text
Missing resource -> 404
Duplicate -> 409
Invalid configuration -> 400 or 422
Published version mutation -> 409
Forbidden -> 403
Precondition failed -> 412
```

---

## 94. Pagination

List APIs use `page`, `size`, and `sort`.

A separate pagination contract is not created for Assessment.

---

## 95. Filtering

Assessment filters: status, examId, blueprintId, search.

Schedule filters: assessmentVersionId, status, from, to.

Additional filters require explicit contract approval.

---

## 96. Sorting

Use an allowlist of safe sortable properties.

Arbitrary user strings are not turned into SQL or JPA order expressions.

---

## 97. Frontend architecture

Use Next.js, TypeScript, Tailwind, shadcn/ui, TanStack Query, React Hook Form, Zod, and Axios.

Server state: TanStack Query.

Form state: React Hook Form.

Validation: Zod.

---

## 98. Frontend routes

Suggested.

```text
/blueprints
/blueprints/[id]
/blueprints/[id]/versions/[versionId]

/assessments
/assessments/[id]
/assessments/[id]/versions/[versionId]
/assessments/[id]/versions/[versionId]/builder
/assessments/[id]/versions/[versionId]/preview

/schedules

/collections
/collections/[id]
```

---

## 99. Blueprint builder

Required areas.

```text
Blueprint header
Version
Status

Sections
  name
  order
  question count
  marks
  duration

Navigation
Timer
Calculator
Language

Validation
Publish
```

---

## 100. Assessment builder

Recommended workflow.

```text
Assessment
   |
   v
AssessmentVersion
   |
   v
BlueprintVersion
   |
   v
Selection mode
   |
   v
Questions
   |
   v
Sections
   |
   v
Scoring
   |
   v
Order
   |
   v
Validate
   |
   v
Preview
   |
   v
Publish
```

---

## 101. Manual picker

Use server-side search, filter, pagination, and sorting.

Never download the whole Question Bank into the browser.

---

## 102. Rule builder

Expose only filters backed by authoritative P3 data.

Potential dimensions.

```text
Difficulty
Template
Tag
Taxonomy
Language
```

Exact availability depends on P3 contract resolution.

---

## 103. Hybrid builder

UI shows manual count, rule count, target count, selected count, and remaining count.

The server ultimately materializes the concrete AssessmentQuestion set.

---

## 104. Section assignment UI

Use a controlled list derived from BlueprintVersion.

Users do not type arbitrary section names into a published AssessmentVersion.

---

## 105. Scoring UI

Make it clear that QuestionVersion default and AssessmentQuestion actual scoring may differ.

This prevents operator confusion.

---

## 106. Validation UI

Group errors.

```text
Blueprint
Questions
Sections
Scoring
Timing
Engine
Publication
```

Example.

```text
2 questions missing
1 unpublished question
1 section has 24 of 25 questions
```

---

## 107. Preview UI

Preview resembles the assessment experience but remains authoring-only.

Preview does not create a runtime session.

---

## 108. Publication confirmation

Show assessment, version, blueprint version, question count, total marks, duration, and sections.

Enable Publish only when server validation is successful.

---

## 109. Schedule UI

Fields.

```text
AssessmentVersion
Mode
Start time
End time
Status
```

The backend validates final values.

---

## 110. Collection UI

Required: list, create, get, update, add assessment, remove assessment, and archive.

Collection membership is managed through CollectionAssessment.

---

## 111. Transaction for blueprint create

```text
Authenticate
 |
 v
Authorize
 |
 v
Validate
 |
 v
Unique code
 |
 v
Persist
 |
 v
Audit
 |
 v
Commit
```

---

## 112. Transaction for blueprint version create

```text
Load Blueprint
 |
 v
Validate parent
 |
 v
Allocate next version
 |
 v
Parse configuration
 |
 v
Validate configuration
 |
 v
Persist
 |
 v
Audit
 |
 v
Commit
```

---

## 113. Transaction for blueprint version publish

```text
Load version
 |
 v
Ensure draft
 |
 v
Validate configuration
 |
 v
Set publication data
 |
 v
Audit
 |
 v
Commit
```

---

## 114. Transaction for assessment create

```text
Validate unique code
 |
 v
Persist Assessment
 |
 v
Audit
 |
 v
Commit
```

---

## 115. Transaction for assessment version create

```text
Load Assessment
 |
 v
Load BlueprintVersion
 |
 v
Validate blueprint eligibility
 |
 v
Validate engine type and delivery policy
 |
 v
Allocate next version
 |
 v
Persist draft
 |
 v
Audit
 |
 v
Commit
```

---

## 116. Transaction for add assessment question

```text
Load AssessmentVersion
 |
 v
Ensure mutable
 |
 v
Load QuestionVersion summary
 |
 v
Verify published and eligible
 |
 v
Validate section
 |
 v
Validate scoring
 |
 v
Validate order
 |
 v
Persist AssessmentQuestion
 |
 v
Audit
 |
 v
Commit
```

---

## 117. Transaction for reorder

```text
Load version
 |
 v
Ensure mutable
 |
 v
Load all specified AssessmentQuestions
 |
 v
Validate complete order
 |
 v
Update
 |
 v
Audit
 |
 v
Commit
```

A partially reordered assessment is never exposed.

---

## 118. Transaction for materialize selection rules

```text
Load AssessmentVersion
 |
 v
Ensure mutable
 |
 v
Load pending rules
 |
 v
For each rule:
     Resolve eligible pool
     Apply deterministic selection
     Persist AssessmentQuestion rows
     Update rule materialization metadata
 |
 v
Audit
 |
 v
Commit
```

Long-running materialization may be moved to a background job.

---

## 119. Transaction for publish assessment version

```text
Load version
 |
 v
Validate BlueprintVersion
 |
 v
Validate question count
 |
 v
Validate section counts
 |
 v
Validate published QuestionVersions
 |
 v
Validate marks
 |
 v
Validate negative marks
 |
 v
Validate total marks
 |
 v
Validate duration
 |
 v
Validate engine type
 |
 v
Validate delivery policy
 |
 v
Validate engine compatibility
 |
 v
Publish
 |
 v
Audit
 |
 v
Commit
```

---

## 120. Transaction for create schedule

```text
Load AssessmentVersion
 |
 v
Ensure published
 |
 v
Validate window
 |
 v
Validate mode
 |
 v
Persist schedule
 |
 v
Audit
 |
 v
Commit
```

---

## 121. Transaction for close schedule

```text
Load schedule
 |
 v
Validate close transition
 |
 v
Close
 |
 v
Audit
 |
 v
Commit
```

---

## 122. Concurrency for version creation

Concurrent requests may both attempt `MAX(version) + 1`.

Database unique constraints remain the final guard.

```text
UNIQUE(blueprint_id, version)
UNIQUE(assessment_id, version)
```

The service may safely retry after conflict if the shared transaction pattern supports it.

---

## 123. Concurrency for question duplication

If the active schema includes `UNIQUE(assessment_version_id, question_version_id)`, the database protects against duplicate QuestionVersions.

If not, service-level duplicate detection is still implemented and the limitation documented.

---

## 124. Concurrency for publication

Publication is a state transition.

A second simultaneous publication does not create a second publication event or state.

Use transaction and state validation.

---

## 125. Concurrency for schedule update

Live schedule updates need a centralized state policy.

The backend re-evaluates state from persisted timestamps and status. Client-side Open or Closed labels are not authoritative.

---

## 126. Historical safety

Do not physically delete published BlueprintVersion, AssessmentVersion, or AssessmentQuestion.

Historical examination data remains reproducible.

---

## 127. Archive semantics

When logical artifacts need retirement, prefer `INACTIVE` or `ARCHIVED` over hard delete.

---

## 128. No delivery data in P4

P4 does not create AssessmentSession, StudentAttempt, AttemptQuestion, AttemptQuestionOption, or StudentResponse during authoring.

---

## 129. Snapshot boundary

Delivery later prepares StudentAttempt, AttemptQuestion, and AttemptQuestionOption from the immutable AssessmentVersion, AssessmentQuestion, QuestionVersion, and QuestionOption.

P4 leaves this data consistent and stable.

---

## 130. Start Exam boundary

Start Exam does not select questions, run authoring rules, or create snapshot child rows.

That work belongs to asynchronous preparation.

---

## 131. Randomization boundary

If Delivery supports randomization of question order or option order, it happens during snapshot preparation, not by changing published AssessmentQuestion rows.

---

## 132. Assessment collections

Collection groups logical Assessments.

It does not publish, schedule, start, or evaluate.

Assessments remain independently managed.

---

## 133. Collection membership

Association is between AssessmentCollection and Assessment through CollectionAssessment.

The logical Assessment reference is not replaced with a version ID unless the schema is formally changed.

---

## 134. Frontend API client functions

```text
listBlueprints
getBlueprint
createBlueprint
updateBlueprint

listBlueprintVersions
createBlueprintVersion
getBlueprintVersion
publishBlueprintVersion

listAssessments
getAssessment
createAssessment
updateAssessment

listAssessmentVersions
createAssessmentVersion
getAssessmentVersion

addAssessmentQuestion
updateAssessmentQuestion
removeAssessmentQuestion
reorderAssessmentQuestions

listAssessmentSelectionRules
createAssessmentSelectionRule
updateAssessmentSelectionRule
deleteAssessmentSelectionRule
materializeAssessmentSelectionRules

validateAssessmentVersion
previewAssessmentVersion
publishAssessmentVersion

listSchedules
createSchedule
updateSchedule
closeSchedule

listCollections
getCollection
createCollection
updateCollection
addCollectionAssessment
removeCollectionAssessment
```

---

## 135. Query keys

Examples.

```text
["blueprints", filters]
["blueprint", id]
["blueprintVersions", blueprintId]
["blueprintVersion", blueprintId, versionId]

["assessments", filters]
["assessment", id]
["assessmentVersions", assessmentId]
["assessmentVersion", assessmentId, versionId]

["assessmentQuestions", assessmentVersionId]
["assessmentSelectionRules", assessmentVersionId]

["assessmentSchedules", filters]

["assessmentCollections", filters]
["assessmentCollection", collectionId]
```

---

## 136. Cache invalidation

After blueprint version publish, invalidate affected blueprint and version queries.

After assessment question add, update, remove, or reorder, invalidate assessmentVersion, assessmentQuestions, and validation result.

After assessment version publish, invalidate relevant assessment, version, and schedule queries.

---

## 137. Redis boundary

Redis may cache published BlueprintVersion read representation, published AssessmentVersion summary, and QuestionTemplate configuration only where safe.

Redis is not the source of truth.

---

## 138. Redis failure

If Redis is unavailable, PostgreSQL remains authoritative.

Core authoring and persistence continue. No false success.

---

## 139. Search strategy

Search uses PostgreSQL-supported search.

No Elasticsearch or OpenSearch is required for P4.

Use pagination, projections, parameterized queries, and query-plan review.

---

## 140. N+1 prevention

Avoid AssessmentVersion to AssessmentQuestions to QuestionVersion to Options to Assets as one uncontrolled object graph.

Use explicit queries and projections.

---

## 141. Question selection query

Rule-based selection is database-driven.

Do not select all questions then filter in Java memory when the dataset is large.

Prefer database predicates, batching, and pagination where appropriate.

---

## 142. Validation query strategy

For a 100-question assessment, batch-load the relevant Content summaries.

Do not issue one query per question if a batch query can safely provide the same information.

---

## 143. Preview query strategy

Preview loads only the selected AssessmentVersion and required question data.

Do not load all assessment versions, all blueprint versions, or all assessments.

---

## 144. Security

Every P4 API requires JWT, RBAC, and state and resource validation.

Frontend permission checks are UX only.

---

## 145. Permission candidates

```text
BLUEPRINT_VIEW
BLUEPRINT_CREATE
BLUEPRINT_UPDATE
BLUEPRINT_PUBLISH
BLUEPRINT_ARCHIVE

ASSESSMENT_VIEW
ASSESSMENT_CREATE
ASSESSMENT_UPDATE

ASSESSMENT_VERSION_VIEW
ASSESSMENT_VERSION_CREATE
ASSESSMENT_VERSION_UPDATE
ASSESSMENT_VERSION_VALIDATE
ASSESSMENT_VERSION_PUBLISH

ASSESSMENT_QUESTION_MANAGE
ASSESSMENT_SELECTION_RULE_MANAGE

ASSESSMENT_SCHEDULE_VIEW
ASSESSMENT_SCHEDULE_CREATE
ASSESSMENT_SCHEDULE_UPDATE
ASSESSMENT_SCHEDULE_CLOSE

ASSESSMENT_COLLECTION_VIEW
ASSESSMENT_COLLECTION_MANAGE
```

These are configured through Identity RBAC.

---

## 146. Student authorization boundary

Students do not successfully invoke P4 authoring mutations. Student runtime access is through Delivery.

---

## 147. Publication authorization

A user who can create and edit drafts does not automatically gain publish unless that permission is assigned.

---

## 148. Audit

Audit mutations.

```text
Create or update Blueprint
Create or update BlueprintVersion
Publish BlueprintVersion
Create or update Assessment
Create or update AssessmentVersion
Add, update, remove AssessmentQuestion
Reorder questions
Materialize selection rules
Create or update or delete AssessmentSelectionRule
Publish AssessmentVersion
Create or update or close Schedule
Create or update Collection
Add or remove CollectionAssessment
```

Use `audit_log`. Do not create P4-specific audit tables.

---

## 149. Structured logging

Log requestId, userId where safe, resource type, resource id, operation, latency, outcome, and error code.

Do not log secrets or full sensitive payloads.

---

## 150. Metrics

Recommended.

```text
blueprint_api_requests_total
blueprint_validation_failures_total
blueprint_publish_total

assessment_api_requests_total
assessment_validation_failures_total
assessment_publish_total

assessment_question_mutations_total
assessment_selection_rule_materializations_total

assessment_schedule_create_total
assessment_schedule_close_total
```

Follow the platform's Prometheus conventions.

---

## 151. OpenAPI

Every P4 endpoint describes HTTP method, path, parameters, request schema, response schema, authentication, authorization, and errors.

No undocumented business endpoint is merged.

---

## 152. Unit tests for blueprint

Test valid configuration, invalid sections, duplicate section order, invalid count, invalid marks, invalid duration, invalid navigation, invalid calculator, and invalid language configuration.

---

## 153. Unit tests for assessment

Test create, update draft, version creation, duplicate version, add question, duplicate question, bad section, unpublished question, invalid scoring, reorder, validate, and publish.

---

## 154. Unit tests for selection rules

Test rule schema validation, materialization determinism across retries, shortage response, duplicate prevention, hybrid count validation, and idempotent re-materialization.

---

## 155. Unit tests for schedule

Test valid schedule, start equals end, end before start, unpublished version, invalid mode, update, close, and repeat close.

---

## 156. Unit tests for collection

Test create, duplicate code, add assessment, duplicate association, remove assessment, and archive.

---

## 157. Repository integration tests

Use PostgreSQL.

Verify unique codes, unique versions, foreign keys, assessment question queries, selection rule queries, schedule filters, collection associations, and ordering.

---

## 158. API integration tests

Test 200, 201, 204, 400, 401, 403, 404, 409, and 422 as applicable.

---

## 159. Security regression tests

Test student creates blueprint, student publishes assessment, staff without publish permission publishes, draft version scheduled, unpublished question published through assessment, published assessment reordered, invalid IDs, unsafe sort field, and malformed configuration.

All must fail safely.

---

## 160. Publication regression tests

Test invalid blueprint, missing questions, unpublished question, duplicate question, bad section, wrong totals, bad duration, unsupported engine, missing delivery policy, selection rule shortage, and already published.

---

## 161. Historical regression test

Create Blueprint V1 Published, Assessment V1 Published, Schedule V1.

Then create Blueprint V2, Assessment V2.

Verify V1 remains unchanged.

---

## 162. Frontend unit tests

At minimum test BlueprintForm, BlueprintSectionEditor, BlueprintConfigurationEditor, AssessmentForm, QuestionPicker, RuleBuilder, HybridBuilder, AssessmentQuestionTable, OrderEditor, ValidationSummary, PublishDialog, ScheduleForm, and CollectionEditor.

---

## 163. End-to-end blueprint journey

```text
Login
 |
 v
Open Blueprints
 |
 v
Create Blueprint
 |
 v
Create version
 |
 v
Configure sections
 |
 v
Save
 |
 v
Validate
 |
 v
Publish
 |
 v
Attempt edit published
 |
 v
Verify rejected
```

---

## 164. End-to-end manual assessment journey

```text
Create Assessment
 |
 v
Create AssessmentVersion
 |
 v
Select BlueprintVersion
 |
 v
Manual mode
 |
 v
Select published questions
 |
 v
Assign sections
 |
 v
Assign marks
 |
 v
Reorder
 |
 v
Validate
 |
 v
Preview
 |
 v
Publish
```

---

## 165. End-to-end rule-based journey

```text
Create AssessmentVersion
 |
 v
Rule-based mode
 |
 v
Define rules
 |
 v
Server selects questions
 |
 v
Materialize
 |
 v
Validate
 |
 v
Preview
 |
 v
Publish
```

---

## 166. End-to-end hybrid journey

```text
Manual questions
 |
 v
Rule selection
 |
 v
Combine
 |
 v
Validate count
 |
 v
Preview
```

---

## 167. End-to-end schedule journey

```text
Published AssessmentVersion
 |
 v
Create Schedule
 |
 v
PRACTICE or EXAM
 |
 v
Set time window
 |
 v
Save
 |
 v
Close
```

---

## 168. End-to-end immutability journey

After publishing, attempt to edit question membership, edit order, edit section, edit marks, edit negative marks, and edit BlueprintVersion reference.

All must fail.

---

## 169. Rendering regression

Preview an assessment containing normal text, currency `$500`, inline LaTeX, block LaTeX, image asset, and multiple options.

Expected.

```text
Currency remains ordinary text
Approved math renders
Unsafe HTML blocked
Asset resolves securely
```

---

## 170. Performance — assessment validation

Measure with 25, 100, and 250 questions.

Track p50, p95, p99, query count, database time, and application CPU.

---

## 171. Performance — question selection

For realistic staging volumes of 10,000 and 50,000 questions where available, measure selection latency, memory, database scan behavior, and duplicate detection.

---

## 172. Performance — preview

Measure API time, server-side rendering time, HTML size, question count, and asset count.

The runtime under 100 ms target is primarily a Delivery concern. It is not falsely applied to every authoring API.

---

## 173. Observability acceptance

Engineers determine which assessment API failed, which resource was affected, which user performed it, what validation failed, and how long it took using logs and metrics.

---

## 174. P4 task list

```text
P4-01  Assessment module skeleton
P4-02  Frozen schema verification
P4-03  Blueprint logical CRUD
P4-04  BlueprintVersion
P4-05  Typed Blueprint configuration
P4-06  Blueprint validation
P4-07  Blueprint publication
P4-08  Assessment logical CRUD
P4-09  AssessmentVersion
P4-10  AssessmentQuestion CRUD
P4-11  AssessmentQuestion ordering
P4-12  Question selection service
P4-13  Manual selection
P4-14  Rule-based selection
P4-15  Hybrid selection
P4-16  Assessment validation
P4-17  Assessment preview
P4-18  Assessment publication
P4-19  Scheduling
P4-20  Collections
P4-21  Frontend builders
P4-22  Security, audit, testing, performance
P4-23  Delivery handoff and exit gate
```

---

## 175. P4-01 acceptance

```text
[ ] Module exists
[ ] Package boundaries correct
[ ] No direct Content repository access
[ ] No direct Delivery repository access
[ ] Application starts
```

---

## 176. P4-02 acceptance

```text
[ ] All P4 tables verified
[ ] Primary keys verified
[ ] Foreign keys verified
[ ] Unique constraints verified
[ ] Indexes verified
[ ] Mode verified
[ ] Engine columns verified
```

---

## 177. P4-03 acceptance

```text
[ ] Blueprint list
[ ] Get
[ ] Create
[ ] Update
[ ] Validation
[ ] RBAC
[ ] Audit
[ ] Tests
```

---

## 178. P4-04 acceptance

```text
[ ] Create version
[ ] Version numbering
[ ] List versions
[ ] Get version
[ ] Draft mutation
[ ] Publication boundary
[ ] History
```

---

## 179. P4-05 acceptance

```text
[ ] Typed configuration parser
[ ] Schema validation
[ ] Section validation
[ ] Timer validation
[ ] Navigation validation
[ ] Calculator validation
[ ] Language validation
[ ] No executable config
```

---

## 180. P4-06 acceptance

```text
[ ] Total question validation
[ ] Total mark validation
[ ] Duration
[ ] Configuration consistency
```

---

## 181. P4-07 acceptance

```text
[ ] Publication action
[ ] Validation before publication
[ ] Audit
[ ] Immutable afterward
```

---

## 182. P4-08 acceptance

```text
[ ] Assessment list
[ ] Get
[ ] Create
[ ] Update
[ ] Filters
[ ] Audit
```

---

## 183. P4-09 acceptance

```text
[ ] Create version
[ ] Blueprint reference
[ ] Engine type
[ ] Delivery policy
[ ] Version numbering
[ ] Totals
[ ] Duration
[ ] Publication
[ ] History
```

---

## 184. P4-10 acceptance

```text
[ ] Add
[ ] Update
[ ] Remove
[ ] Published QuestionVersion only
[ ] Section validation
[ ] Scoring validation
[ ] Duplicate protection
```

---

## 185. P4-11 acceptance

```text
[ ] Reorder
[ ] Full and validated ordering
[ ] Duplicate order blocked
[ ] Wrong-version IDs blocked
[ ] Immutable after publication
```

---

## 186. P4-12 acceptance

```text
[ ] Published-only eligibility
[ ] Database filtering
[ ] Batching
[ ] Duplicate prevention
[ ] Materialization
[ ] Deterministic ordering
```

---

## 187. P4-13 acceptance

```text
[ ] Manual picker
[ ] Sections
[ ] Scoring
[ ] Order
[ ] Validation
```

---

## 188. P4-14 acceptance

```text
[ ] Rule contract
[ ] Eligible question query
[ ] Target count
[ ] Shortage diagnostics
[ ] Deterministic materialization
[ ] Idempotent retry
```

---

## 189. P4-15 acceptance

```text
[ ] Manual selection
[ ] Rule selection
[ ] Target
[ ] Duplicate protection
[ ] Materialization
```

---

## 190. P4-16 acceptance

```text
[ ] Blueprint valid
[ ] Question count
[ ] Section counts
[ ] Published questions
[ ] Marks
[ ] Negative marks
[ ] Total marks
[ ] Duration
[ ] Engine type and delivery policy
[ ] Engine compatibility
```

---

## 191. P4-17 acceptance

```text
[ ] No runtime rows
[ ] Exact order
[ ] Sections
[ ] Scoring display
[ ] Content rendering
[ ] Secure assets
```

---

## 192. P4-18 acceptance

```text
[ ] Publish endpoint
[ ] Server validation
[ ] Audit
[ ] Immutable
```

---

## 193. P4-19 acceptance

```text
[ ] List
[ ] Filters
[ ] Create
[ ] Update
[ ] Close
[ ] Published version required
[ ] Valid time window
[ ] Mode
[ ] Audit
```

---

## 194. P4-20 acceptance

```text
[ ] List
[ ] Create
[ ] Get
[ ] Update
[ ] Add assessment
[ ] Duplicate association protection
[ ] Remove
[ ] Archive
```

---

## 195. P4-21 acceptance

```text
[ ] Blueprint workspace
[ ] Version editor
[ ] Assessment workspace
[ ] Builder
[ ] Question picker
[ ] Rule builder
[ ] Validation UI
[ ] Preview
[ ] Publication
[ ] Schedule
[ ] Collections
[ ] Permission-aware actions
```

---

## 196. P4-22 acceptance

```text
[ ] Unit tests
[ ] Integration tests
[ ] API tests
[ ] Security tests
[ ] End-to-end
[ ] Rendering regression
[ ] Performance baseline
[ ] CI green
[ ] OpenAPI verified
[ ] Audit verified
```

---

## 197. P4-23 delivery handoff

P5 can consume published AssessmentVersion, published BlueprintVersion, AssessmentQuestion set, exact QuestionVersion IDs, section, marks, negative marks, duration, schedule mode, engine type, delivery policy, and engine-compatible configuration without modifying P4-owned records.

---

## 198. Recommended implementation prompt

```text
You are implementing P4 Blueprint and Assessment Management for the
Smart Competitive Examination Platform.

Read the full specification package before modifying the repository.

P4 owns:
Blueprint
BlueprintVersion
Assessment
AssessmentVersion
AssessmentQuestion
AssessmentSelectionRule
AssessmentSchedule
AssessmentCollection
CollectionAssessment

NON-NEGOTIABLE:
1.  Do not invent database tables.
2.  Do not silently add columns.
3.  Do not modify frozen API paths.
4.  Published BlueprintVersions are immutable.
5.  Published AssessmentVersions are immutable.
6.  AssessmentQuestion references exact QuestionVersion.
7.  Never resolve latest QuestionVersion dynamically.
8.  Never create runtime snapshot rows from P4 authoring APIs.
9.  Manual, rule-based, and hybrid authoring must materialize concrete
    AssessmentQuestion rows before publication.
10. Delivery owns StudentAttempt, AttemptQuestion, AttemptQuestionOption,
    AssessmentSession, StudentResponse.
11. Delivery Start Exam must not perform snapshot generation.
12. Use DTO -> Service -> Repository.
13. Use module application contracts instead of cross-module repository
    access.
14. Backend remains authoritative.
15. No exam-name branching in React.
16. No executable JSON or configuration.
17. PostgreSQL is source of truth.
18. Redis is cache only.
19. Use PostgreSQL filtering and projections instead of loading full
    question banks.
20. Do not introduce Elasticsearch or OpenSearch.
21. Use MARKDOWN_LATEX_V1-compatible preview rendering.
22. Protect correct answers and private data from student contexts.
23. Use JWT, RBAC, and audit.
24. If a frozen contract is inconsistent with implementation, report
    the conflict; do not silently redesign it.
25. Selection rule materialization is deterministic and idempotent.

Task order:
P4-01 through P4-23.

Before merge:
- Run unit tests
- Run PostgreSQL integration tests
- Run API tests
- Run security tests
- Run end-to-end
- Run rendering regression
- Verify migrations
- Verify OpenAPI
- Verify audit
- Verify publication immutability
- Verify exact QuestionVersion references
- Verify question, section, and mark validation
- Verify engine type and delivery policy enforcement
- Verify schedule validation
- Verify selection rule materialization
- Report unresolved contract issues
```

---

## 199. Code review questions

Reviewers verify.

```text
Does the implementation match the active schema exactly?

Can a published BlueprintVersion change?

Can a published AssessmentVersion change?

Can a draft QuestionVersion enter a published assessment?

Can a question silently resolve to latest content?

Can section counts mismatch and still publish?

Can total marks mismatch and still publish?

Can Schedule point to an unpublished version?

Can frontend state bypass backend validation?

Can P4 access QuestionRepository directly?

Can P4 access object storage directly?

Can authoring configuration execute code?

Can preview leak answer keys?

Can an AssessmentVersion be changed after Delivery preparation starts?

Can rule materialization be non-deterministic?

Can rule materialization be non-idempotent?

Can the engine type change after publication?
```

---

## 200. Golden path

```text
Login
 |
 v
Academic masters available
 |
 v
Published QuestionVersions available
 |
 v
Create Blueprint
 |
 v
Create BlueprintVersion
 |
 v
Configure sections, timing, navigation, calculator, language
 |
 v
Validate Blueprint
 |
 v
Publish BlueprintVersion
 |
 v
Create Assessment
 |
 v
Create AssessmentVersion
 |
 v
Select BlueprintVersion
 |
 v
Configure engine type and delivery policy
 |
 v
Choose Manual, Rule-Based, or Hybrid
 |
 v
Materialize AssessmentQuestions
 |
 v
Assign sections
 |
 v
Set marks and negative marks
 |
 v
Reorder
 |
 v
Validate
 |
 v
Preview
 |
 v
Publish AssessmentVersion
 |
 v
Create PRACTICE or EXAM Schedule
 |
 v
Delivery prepares snapshots asynchronously
```

---

## 201. Failure cases

```text
Blueprint invalid                          -> Publish blocked
QuestionVersion unpublished                -> AssessmentVersion publish blocked
98 of 100 questions                        -> QUESTION_COUNT_MISMATCH
Section has wrong count                    -> SECTION_COUNT_MISMATCH
Wrong total marks                          -> TOTAL_MARKS_MISMATCH
Bad duration                               -> DURATION_INVALID
Missing engine type                        -> publication blocked
Missing delivery policy                    -> publication blocked
Unsupported engine                         -> publication blocked
Selection rule shortage                    -> QUESTION_POOL_SHORTAGE
Published AssessmentVersion mutation       -> INVALID_STATE
Published BlueprintVersion mutation        -> INVALID_STATE
Invalid schedule range                     -> schedule rejected
Schedule references unpublished version    -> schedule rejected
```

---

## 202. Historical reproducibility test

Create Question V1 Published, Blueprint V1 Published, Assessment V1 Published, and Schedule V1.

Then create Question V2, Blueprint V2, and Assessment V2.

Verify.

```text
Schedule V1 still points to Assessment V1
Assessment V1 still points to Blueprint V1
Assessment V1 still points to exact QuestionVersion V1
```

---

## 203. Delivery compatibility test

Without creating student runtime data, verify that Delivery can resolve.

```text
AssessmentVersion
BlueprintVersion
AssessmentQuestion
QuestionVersion
QuestionOption
Mode
Duration
Sections
Marks
Negative marks
Engine type
Delivery policy
ExamEngine context
```

This is the P4 and P5 integration boundary.

---

## 204. Exit criteria — product

```text
[ ] Blueprints can be authored
[ ] BlueprintVersions can be published
[ ] Assessments can be authored
[ ] AssessmentVersions can be created
[ ] Questions can be selected manually and via rules
[ ] Selection rules materialize deterministically
[ ] Sections can be assigned
[ ] Scoring can be configured
[ ] Ordering works
[ ] Validation works
[ ] Preview works
[ ] Publication works
[ ] Schedules work
[ ] Collections work
[ ] Engine type and delivery policy enforced
```

---

## 205. Exit criteria — architecture

```text
[ ] No runtime snapshot creation
[ ] No Start Exam logic
[ ] No direct Content repository access
[ ] No direct Delivery repository access
[ ] No direct asset storage access
[ ] ExamEngine boundary preserved
[ ] CDUI boundary preserved
[ ] Immutable publication boundary preserved
[ ] Deterministic selection rule materialization preserved
```

---

## 206. Exit criteria — security

```text
[ ] JWT
[ ] RBAC
[ ] Backend authorization
[ ] Publish permission
[ ] Schedule permission
[ ] Student denied authoring
[ ] Secure preview
[ ] Audit
```

---

## 207. Exit criteria — quality

```text
[ ] Migration verified
[ ] OpenAPI complete
[ ] Unit tests pass
[ ] Integration tests pass
[ ] API tests pass
[ ] End-to-end passes
[ ] Rendering regression passes
[ ] Performance baseline recorded
[ ] CI green
```

---

## 208. Final engineering principle

> P4 transforms reusable published content into a deterministic, immutable, schedulable examination definition without taking ownership of runtime execution or official evaluation.

The essential separation.

```text
Authoring
    |
    v
Published AssessmentVersion
    |
    v
Schedule
    |
    v
Async Delivery snapshot preparation
    |
    v
Runtime exam
    |
    v
Evaluation
```

---

## 209. Document status

**Status:** IMPLEMENTATION-READY.

**New tables required:** No.

**Primary risks:**

```text
Blueprint JSON contract
ExamEngine source
Rule-based persistence
Collection version semantics
Schedule overlap semantics
Cross-module version and reference integrity
```

**Next implementation document:** P5 — Assessment Delivery and Runtime Examination Engine.