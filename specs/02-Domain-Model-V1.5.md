# Smart Competitive Examination Platform
## Domain Model & Entity Relationship Design

**Version:** 1.5
**Status:** FROZEN
**Architecture:** Modular Monolith
**Database:** PostgreSQL
**Primary Key:** UUID
**Organization Scope:** Single Organization
**Audience:** Backend Engineers, Database Engineers, Architects, QA, Product

---

## 1. Purpose

This document defines the domain model and entity relationships for the platform.

It converts product requirements into a structured business domain model implemented with Java, Spring Boot, Spring Data JPA, Hibernate, and PostgreSQL.

The document defines business domains, entities, entity ownership, relationships, cardinality, versioning, transactional boundaries, cross-domain references, business rules, entity lifecycle, data ownership, and ERD-level structure.

The objective is to prevent the database from becoming a collection of unrelated tables. The domain model stays aligned with the business architecture.

---

## 2. Domain architecture

The platform comprises ten business domains plus supporting infrastructure.

| Domain | Purpose |
|---|---|
| Identity | Users, roles, permissions, sessions |
| Academic | Exam hierarchy and master data |
| Content | Question bank and versioning |
| Assessment | Blueprint and assessment construction |
| Delivery | Runtime examination |
| Evaluation | Scoring, results, scorecards |
| Analytics | Derived performance data |
| Commerce | Pricing, purchases, access |
| Communication | Notifications and announcements |
| Administration | Configuration and audit |
| Infrastructure | Asynchronous jobs |

Each domain owns its business logic. A domain may reference another domain using identifiers, but it does not directly manipulate another domain's internal entities.

---

## 3. Domain ownership principle

Every business entity belongs to exactly one domain.

| Entity | Owning domain |
|---|---|
| Question | Content |
| Assessment | Assessment |
| StudentAttempt | Delivery |
| Result | Evaluation |
| Purchase | Commerce |

The owning domain is responsible for entity lifecycle, business validation, state transitions, persistence, domain rules, and service operations.

Other domains communicate through services or well-defined application interfaces.

---

## 4. Base entity

All major business entities inherit from a conceptual base entity.

| Field | Type | Required | Description |
|---|---|---|---|
| id | UUID | Yes | Primary identifier |
| status | Enum | Yes | ACTIVE, INACTIVE, ARCHIVED |
| created_by | UUID | Yes | User who created the record |
| created_on | TIMESTAMPTZ | Yes | Creation timestamp |
| updated_by | UUID | Yes | Last user who updated the record |
| updated_on | TIMESTAMPTZ | Yes | Last update timestamp |

Status semantics:

- ACTIVE: usable in active workflows
- INACTIVE: exists but disabled
- ARCHIVED: historical, no longer operational, retained for audit

---

## 5. Versioned entity extension

Certain entities require immutable versions.

- Question
- Blueprint
- Assessment
- AnswerKey

Conceptual version structure.

| Field | Description |
|---|---|
| version | Sequential version number |
| is_published | Publication flag |
| published_by | User who published |
| published_on | Publication timestamp |

A versioned entity must never overwrite a published definition. A correction creates a new version.

---

## 6. Domain 1 - Identity

### 6.1 Purpose

Manages organization, users, roles, permissions, and login sessions.

### 6.2 Entities

| Entity | Purpose |
|---|---|
| Organization | The operating organization |
| User | Every authenticated platform user |
| Role | Logical security role |
| Permission | Individual capability |
| UserRole | User-to-role junction |
| RolePermission | Role-to-permission junction |
| LoginSession | Authenticated session record |

### 6.3 Relationships

```text
Organization 1:N User

User M:N Role through UserRole
Role M:N Permission through RolePermission

User 1:N LoginSession
User 1:N AuditLog
User 1:N Notification
```

### 6.4 Organization

Represents the organization operating the platform.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| code | VARCHAR(50) | Unique business code |
| name | VARCHAR(150) | Display name |
| description | TEXT | Optional |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

The platform runs for one organization at a time. Organization boundaries remain explicit so that future expansion is not blocked.

### 6.5 User

Represents every authenticated platform user.

A user can hold multiple roles: Admin, Reviewer, Staff, Student, Support, Finance.

A user is not permanently tied to a single business function. Role assignment determines what the user can do.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| organization_id | UUID | FK to organization |
| first_name | VARCHAR(100) | |
| last_name | VARCHAR(100) | |
| email | VARCHAR(255) | Unique |
| mobile | VARCHAR(20) | Unique |
| password | VARCHAR(255) | Secure hash only |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

### 6.6 Role

Defines a logical security role.

Initial roles: ADMIN, REVIEWER, STAFF, STUDENT, SUPPORT, FINANCE.

The exact permission set is managed through RolePermission. Role codes are unique.

### 6.7 Permission

Defines an individual capability.

Examples: USER_VIEW, USER_CREATE, QUESTION_CREATE, QUESTION_REVIEW, QUESTION_PUBLISH, ASSESSMENT_CREATE, ASSESSMENT_PUBLISH, REPORT_VIEW, PAYMENT_VIEW.

The permission catalog is controlled master data, not a hard-coded enum. Permission codes are unique.

### 6.8 UserRole and RolePermission

Junction entities.

| Junction | Unique constraint |
|---|---|
| UserRole | UNIQUE(user_id, role_id) |
| RolePermission | UNIQUE(role_id, permission_id) |

A permission is effective only when all of the following hold.

```text
user.status            = ACTIVE
role.status            = ACTIVE
permission.status      = ACTIVE
user_role.status       = ACTIVE
role_permission.status = ACTIVE
```

### 6.9 LoginSession

Represents an authenticated login session.

States: ACTIVE, EXPIRED, LOGGED_OUT.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK to user |
| login_time | TIMESTAMPTZ | |
| logout_time | TIMESTAMPTZ | Nullable |
| session_status | Enum | ACTIVE, EXPIRED, LOGGED_OUT |
| ip_address | VARCHAR(100) | |
| device_info | TEXT | |

Relationship.

```text
User 1:N LoginSession
```

### 6.10 System actor

A well-known system user represents background and scheduled operations.

| Property | Value |
|---|---|
| UUID | 00000000-0000-0000-0000-000000000001 |
| Email | system@platform.internal |

Login is blocked for this identity. All background jobs, scheduled tasks, and data migrations use the system actor. Audit attribution reflects this.

---

## 7. Domain 2 - Academic

### 7.1 Purpose

Defines the examination knowledge hierarchy.

### 7.2 Entities

| Entity | Purpose |
|---|---|
| Exam | A competitive examination family |
| Subject | Subject within an exam |
| Chapter | Chapter within a subject |
| Topic | Topic within a chapter |
| SubTopic | Sub-topic within a topic |
| Language | Reusable language master |
| Tag | Reusable metadata tag |

### 7.3 Core hierarchy

```text
Exam
 └── Subject
      └── Chapter
           └── Topic
                └── SubTopic
```

### 7.4 Rules

- Exams are master data. Do not hard-code exam families as enums.
- Languages are reusable across questions and configuration.
- Tags are reusable metadata, not part of the hierarchy.
- Academic masters are not physically deleted when referenced by transactional or historical records. Use lifecycle states.

---

## 8. Domain 3 - Content

### 8.1 Purpose

Manages the complete question lifecycle.

### 8.2 Entities

| Entity | Purpose |
|---|---|
| Question | Logical question identity |
| QuestionVersion | Exact content at a version |
| QuestionTemplate | Structural template for a question type |
| QuestionOption | Answer option for a version |
| Asset | Image, diagram, or file attached to a version |
| Explanation | Explanation for a version |
| Hint | Optional hint for a version |
| PreviousExamHistory | Historical occurrence of a question |
| QuestionTag | Question-to-tag junction |

### 8.3 Relationships

```text
QuestionTemplate 1:N Question
Question 1:N QuestionVersion
QuestionVersion 1:N QuestionOption
QuestionVersion 1:N Asset
QuestionVersion 1:1 Explanation
QuestionVersion 1:0..1 Hint
Question 1:N PreviousExamHistory
Question M:N Tag through QuestionTag
```

### 8.4 Question

The logical identity of a question.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| template_id | UUID | FK to question_template |
| exam_id | UUID | FK to exam |
| subject_id | UUID | FK to subject |
| chapter_id | UUID | FK to chapter, nullable |
| topic_id | UUID | FK to topic, nullable |
| sub_topic_id | UUID | FK to sub_topic, nullable |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Taxonomy lives on the logical Question. Language lives on a specific version.

### 8.5 QuestionVersion

The exact content of a question at a version.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| question_id | UUID | FK to question |
| version | INTEGER | Sequential |
| title | VARCHAR(255) | |
| content | TEXT | Canonical content |
| marks | NUMERIC(8,2) | |
| negative_marks | NUMERIC(8,2) | |
| difficulty | VARCHAR(20) | EASY, MEDIUM, HARD |
| language_id | UUID | FK to language |
| is_published | BOOLEAN | |
| published_by | UUID | Nullable |
| published_on | TIMESTAMPTZ | Nullable |
| review_status | VARCHAR(20) | Workflow state |
| submitted_for_review_by | UUID | Nullable |
| submitted_for_review_on | TIMESTAMPTZ | Nullable |
| reviewed_by | UUID | Nullable |
| reviewed_on | TIMESTAMPTZ | Nullable |
| review_remarks | TEXT | Nullable |

Unique: UNIQUE(question_id, version).

Review state machine.

```text
DRAFT
  |
  |-- submit-review --> IN_REVIEW
  |                       |
  |                       |-- approve --> APPROVED
  |                       |                  |
  |                       |                  |-- publish --> PUBLISHED
  |                       |
  |                       |-- reject  --> REJECTED
  |                                          |
  |-- edit <---------------------------------+
```

A published version is immutable. Corrections create a new version.

### 8.6 QuestionTemplate

Defines the structure and behavior of a question type.

Configuration is stored as JSONB and supports flags such as the following.

```json
{ "requires_latex_parsing": true }
```

The template catalog remains configurable.

### 8.7 QuestionOption

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| question_version_id | UUID | FK to question_version |
| option_key | VARCHAR(20) | A, B, C, D, etc. |
| content | TEXT | Canonical content |
| display_order | INTEGER | |
| is_correct | BOOLEAN | Server-side only |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(question_version_id, option_key) and UNIQUE(question_version_id, display_order).

Correctness is server-side. It is never exposed to a student during an active attempt.

### 8.8 Asset

An asset belongs to a specific question_version.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| question_version_id | UUID | FK to question_version |
| file_name | VARCHAR(255) | Original filename |
| file_path | TEXT | Storage reference |
| file_type | VARCHAR(100) | MIME type |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

The file_path is a storage implementation detail. It is never exposed to the student as an authorization primitive.

### 8.9 Explanation and Hint

Associated with a specific version.

```text
QuestionVersion 1:1  Explanation
QuestionVersion 1:0..1 Hint
```

Both use the canonical content contract.

### 8.10 PreviousExamHistory

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| question_id | UUID | FK to question |
| exam_id | UUID | FK to exam |
| year | INTEGER | |
| shift | VARCHAR(50) | |
| source | VARCHAR(100) | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Multiple rows per Question. Historical occurrences are not compressed into a single column.

### 8.11 QuestionTag

Many-to-many junction between Question and Tag.

Unique: UNIQUE(question_id, tag_id).

Tag remains owned by Academic.

---

## 9. Domain 4 - Assessment

### 9.1 Purpose

The core examination construction domain.

### 9.2 Entities

| Entity | Purpose |
|---|---|
| Blueprint | Logical examination pattern |
| BlueprintVersion | Exact published pattern |
| Assessment | Logical assessment identity |
| AssessmentVersion | Exact published examination definition |
| AssessmentQuestion | Assessment-to-QuestionVersion binding |
| AssessmentSchedule | Delivery window and mode |
| AssessmentCollection | Group of assessments |
| CollectionAssessment | Collection-to-Assessment junction |
| AssessmentSelectionRule | Rule for rule-based or hybrid selection |

### 9.3 Relationships

```text
Blueprint 1:N BlueprintVersion
BlueprintVersion 1:N AssessmentVersion
Assessment 1:N AssessmentVersion
AssessmentVersion M:N QuestionVersion through AssessmentQuestion
AssessmentVersion 1:N AssessmentSchedule
AssessmentVersion 1:N AssessmentSelectionRule
AssessmentCollection M:N Assessment through CollectionAssessment
```

### 9.4 Blueprint

Defines the examination structure: sections, subjects, distribution, marks, negative marking, time limits, section timing, navigation, languages, calculator, scoring.

### 9.5 BlueprintVersion

Blueprint versioning. A published BlueprintVersion is immutable.

The configuration is JSONB and supports sections, navigation, timer, submission, validation, presentation, calculator, and language.

Do not add new tables for individual configuration concerns.

### 9.6 Assessment

Logical examination identity. Example: SSC CGL Mock Test 01.

### 9.7 AssessmentVersion

The exact assessment delivered.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| assessment_id | UUID | FK to assessment |
| blueprint_version_id | UUID | FK to blueprint_version |
| version | INTEGER | Sequential |
| total_questions | INTEGER | |
| total_marks | NUMERIC(8,2) | |
| duration_minutes | INTEGER | Nullable for practice |
| engine_type | VARCHAR(20) | TCS_ION, IBPS, NTA, UPSC, RAILWAY, CUSTOM |
| engine_configuration_version | INTEGER | Nullable |
| delivery_policy_id | UUID | FK to delivery_policy |
| is_published | BOOLEAN | |
| published_by | UUID | |
| published_on | TIMESTAMPTZ | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(assessment_id, version).

Publication requires engine_type and delivery_policy_id.

### 9.8 Engine type

Supported engine identifiers: TCS_ION, IBPS, NTA, UPSC, RAILWAY, CUSTOM.

Engine type is immutable after publication. A change requires a new AssessmentVersion.

### 9.9 AssessmentQuestion

Binds an AssessmentVersion to exact QuestionVersions.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| assessment_version_id | UUID | FK to assessment_version |
| question_version_id | UUID | FK to question_version |
| display_order | INTEGER | |
| section_name | VARCHAR(150) | |
| marks | NUMERIC(8,2) | |
| negative_marks | NUMERIC(8,2) | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Assessment-specific scoring values are authoritative for that assessment.

### 9.10 AssessmentSelectionRule

Rules for rule-based and hybrid question selection.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| assessment_version_id | UUID | FK to assessment_version |
| section_name | VARCHAR(150) | |
| target_count | INTEGER | Must be greater than 0 |
| rule_configuration | JSONB | Filters |
| display_order | INTEGER | |
| materialized_on | TIMESTAMPTZ | Nullable |
| materialized_question_count | INTEGER | Nullable |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(assessment_version_id, section_name).

Rule configuration filters by taxonomy, difficulty, tags, language, and published-only status. Once materialized into AssessmentQuestion rows, the rule is provenance and is not re-executed at runtime.

Rule configuration schema.

```json
{
  "templateCode": "SINGLE_CHOICE",
  "examId": "uuid",
  "subjectId": "uuid",
  "chapterId": "uuid",
  "topicId": "uuid",
  "difficulty": ["EASY", "MEDIUM"],
  "tagIds": ["uuid"],
  "languageId": "uuid",
  "excludeQuestionIds": ["uuid"],
  "excludeQuestionVersionIds": ["uuid"],
  "publishedOnly": true
}
```

### 9.11 AssessmentSchedule

Controls when an AssessmentVersion is available.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| assessment_version_id | UUID | FK to assessment_version |
| mode | VARCHAR(20) | PRACTICE, EXAM |
| start_time | TIMESTAMPTZ | |
| end_time | TIMESTAMPTZ | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

A schedule does not modify the underlying AssessmentVersion.

### 9.12 AssessmentCollection

Groups assessments.

```text
AssessmentCollection M:N Assessment through CollectionAssessment
```

---

## 10. Domain 5 - Delivery

### 10.1 Purpose

Manages the runtime examination.

### 10.2 Entities

| Entity | Purpose |
|---|---|
| AssessmentSession | Runtime session state |
| StudentAttempt | Permanent academic attempt |
| AttemptQuestion | Immutable question snapshot for an attempt |
| AttemptQuestionOption | Immutable option ordering snapshot |
| StudentResponse | Student's persisted answer |
| DeliveryPolicy | Reusable delivery behaviour |

### 10.3 Relationships

```text
AssessmentSchedule 1:N AssessmentSession
User 1:N AssessmentSession
AssessmentSession 1:1 StudentAttempt
AssessmentSchedule 1:N StudentAttempt
StudentAttempt 1:N AttemptQuestion
AttemptQuestion 1:N AttemptQuestionOption
StudentAttempt 1:N StudentResponse
```

### 10.4 AssessmentSession

Runtime state.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK to user |
| assessment_schedule_id | UUID | FK to assessment_schedule |
| attempt_id | UUID | FK to student_attempt |
| session_status | Enum | ACTIVE, EXPIRED, LOGGED_OUT |
| started_on | TIMESTAMPTZ | |
| last_activity_on | TIMESTAMPTZ | |
| ended_on | TIMESTAMPTZ | Nullable |

### 10.5 StudentAttempt

Permanent academic record.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK to user |
| assessment_version_id | UUID | FK to assessment_version |
| assessment_schedule_id | UUID | FK to assessment_schedule |
| attempt_mode | VARCHAR(20) | PRACTICE, EXAM |
| attempt_status | Enum | NOT_STARTED, IN_PROGRESS, SUBMITTED, EVALUATED |
| snapshot_status | Enum | PENDING, PREPARING, READY, FAILED |
| snapshot_prepared_on | TIMESTAMPTZ | Nullable |
| started_on | TIMESTAMPTZ | Nullable |
| submitted_on | TIMESTAMPTZ | Nullable |
| total_score | NUMERIC(8,2) | Nullable |
| audit fields | | Standard base fields |

The attempt state machine and snapshot state machine are separate.

A student may enter the exam only when all of the following hold.

```text
attempt_status  = NOT_STARTED
snapshot_status = READY
access          = VALID
schedule        = OPEN
```

### 10.6 AttemptQuestion

Immutable snapshot of the question assigned to a specific attempt.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| attempt_id | UUID | FK to student_attempt |
| question_version_id | UUID | FK to question_version |
| display_order | INTEGER | |
| section_name | VARCHAR(150) | |
| marks | NUMERIC(8,2) | |
| negative_marks | NUMERIC(8,2) | |
| created_on | TIMESTAMPTZ | |

Unique: UNIQUE(attempt_id, question_version_id) and UNIQUE(attempt_id, display_order).

Once the snapshot is READY, AttemptQuestion rows are immutable. Runtime randomization does not mutate them.

### 10.7 AttemptQuestionOption

Preserves exact option ordering presented to the student.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| attempt_question_id | UUID | FK to attempt_question |
| question_option_id | UUID | FK to question_option |
| display_order | INTEGER | |

Unique: UNIQUE(attempt_question_id, question_option_id) and UNIQUE(attempt_question_id, display_order).

### 10.8 StudentResponse

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| attempt_id | UUID | FK to student_attempt |
| question_version_id | UUID | FK to question_version |
| response | TEXT | Encoded answer |
| time_spent_seconds | INTEGER | |
| marked_for_review | BOOLEAN | |
| visited | BOOLEAN | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(attempt_id, question_version_id).

Stale-write protection uses HTTP conditional requests at the API boundary. An older delayed request does not overwrite a newer accepted response.

### 10.9 DeliveryPolicy

Reusable delivery behaviour.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| code | VARCHAR(50) | Unique |
| name | VARCHAR(150) | |
| configuration | JSONB | Declarative |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Configuration is declarative. It never contains executable code.

---

## 11. Domain 6 - Evaluation

### 11.1 Purpose

Converts student attempts into official results.

### 11.2 Entities

| Entity | Purpose |
|---|---|
| AnswerKey | Versioned answer key |
| AnswerKeyDetail | Per-question answer mapping |
| Evaluation | Versioned scoring record |
| Result | Official result |
| Scorecard | Student-facing result document |

### 11.3 Relationships

```text
AssessmentVersion 1:N AnswerKey
AnswerKey 1:N AnswerKeyDetail
StudentAttempt 1:N Evaluation
AnswerKey 1:N Evaluation
Evaluation 1:1 Result
Result 1:1 Scorecard
```

### 11.4 AnswerKey

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| assessment_version_id | UUID | FK to assessment_version |
| version | INTEGER | Sequential |
| is_published | BOOLEAN | |
| published_by | UUID | |
| published_on | TIMESTAMPTZ | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(assessment_version_id, version).

Published AnswerKeys are immutable. Corrections create a new version.

### 11.5 AnswerKeyDetail

Per-question answer mapping.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| answer_key_id | UUID | FK to answer_key |
| question_version_id | UUID | FK to question_version |
| correct_option_id | UUID | Nullable |
| correct_answer_text | TEXT | Nullable |
| question_outcome | VARCHAR(20) | NORMAL, BONUS, DROPPED |
| awarded_marks | NUMERIC(8,2) | Nullable override |
| awarded_negative_marks | NUMERIC(8,2) | Nullable override |
| remarks | TEXT | Nullable |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(answer_key_id, question_version_id).

Publication requires exactly one detail row per AssessmentQuestion in the bound AssessmentVersion.

Outcome semantics.

| Outcome | Meaning | Scoring effect |
|---|---|---|
| NORMAL | Standard scoring | Correct gives +marks, wrong gives -negative, unanswered gives 0 |
| BONUS | All candidates receive full marks | +marks awarded to every attempt |
| DROPPED | Question excluded from scoring | 0 marks, response ignored |

Evaluation reads correctness only from this table. It never reads question_option.is_correct.

### 11.6 Evaluation

Versioned.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| attempt_id | UUID | FK to student_attempt |
| answer_key_id | UUID | FK to answer_key |
| version | INTEGER | Sequential |
| superseded_by_id | UUID | Nullable self-reference |
| superseded_on | TIMESTAMPTZ | Nullable |
| superseded_reason | TEXT | Nullable |
| evaluated_on | TIMESTAMPTZ | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(attempt_id, version).

A partial unique index enforces at most one current evaluation per attempt.

```text
UNIQUE(attempt_id) WHERE superseded_by_id IS NULL
```

Re-evaluation creates version N+1 and marks version N superseded.

### 11.7 Result

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| evaluation_id | UUID | FK to evaluation |
| obtained_marks | NUMERIC(8,2) | |
| percentage | NUMERIC(8,2) | |
| rank | INTEGER | Nullable |
| percentile | NUMERIC(8,4) | Nullable |
| result_status | Enum | CALCULATED, READY_FOR_PUBLICATION, PUBLISHED, SUPERSEDED |
| superseded_on | TIMESTAMPTZ | Nullable |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(evaluation_id).

Result is derived from Evaluation. It is not edited as an arbitrary record.

Scoring semantics.

- Minimum score: no floor, negative totals are permitted
- Rounding: HALF_UP for obtained_marks and percentage
- Per-question precision: full BigDecimal retained throughout the calculation
- AnswerKey selection on evaluate or re-evaluate: explicit answerKeyId required
- Result visibility: only PUBLISHED is visible to students

Rank uses standard competition ranking over published results within the same AssessmentVersion.

Percentile.

```text
percentile = (count of candidates with score <= my score) / total * 100
```

Both fields are NULL until the Result is PUBLISHED. Recomputation occurs on result publication and re-evaluation.

### 11.8 Scorecard

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| result_id | UUID | FK to result |
| generated_on | TIMESTAMPTZ | |
| file_path | TEXT | Storage reference |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(result_id).

---

## 12. Domain 7 - Analytics

### 12.1 Purpose

Derived performance information.

### 12.2 Entities

| Entity | Grain |
|---|---|
| StudentAnalytics | One row per user |
| AssessmentAnalytics | One row per assessment |
| QuestionAnalytics | One row per question |
| TopicAnalytics | One row per topic |
| OrganizationAnalytics | One row per organization |

### 12.3 Principle

Analytics is derived data. It is not the source of truth.

The source of truth remains StudentAttempt, StudentResponse, Evaluation, and Result.

Analytics is recalculable from transactional data.

Analytics tables are current aggregates. They are not time-series stores. Historical trends are derived from transactional timestamps or through dedicated read queries.

### 12.4 StudentAnalytics

| Field | Type |
|---|---|
| id | UUID |
| user_id | UUID |
| assessments_attempted | INTEGER |
| average_score | NUMERIC(8,2) |
| accuracy | NUMERIC(8,2) |
| average_time | NUMERIC(10,2) |
| last_calculated_on | TIMESTAMPTZ |

Unique: UNIQUE(user_id).

### 12.5 AssessmentAnalytics

| Field | Type |
|---|---|
| id | UUID |
| assessment_id | UUID |
| total_attempts | INTEGER |
| average_score | NUMERIC(8,2) |
| highest_score | NUMERIC(8,2) |
| lowest_score | NUMERIC(8,2) |
| last_calculated_on | TIMESTAMPTZ |

Unique: UNIQUE(assessment_id).

### 12.6 QuestionAnalytics

| Field | Type |
|---|---|
| id | UUID |
| question_id | UUID |
| total_attempts | INTEGER |
| correct_attempts | INTEGER |
| incorrect_attempts | INTEGER |
| average_time | NUMERIC(10,2) |
| difficulty_index | NUMERIC(8,4) |
| last_calculated_on | TIMESTAMPTZ |

Unique: UNIQUE(question_id).

### 12.7 TopicAnalytics

| Field | Type |
|---|---|
| id | UUID |
| topic_id | UUID |
| total_attempts | INTEGER |
| average_score | NUMERIC(8,2) |
| accuracy | NUMERIC(8,2) |
| last_calculated_on | TIMESTAMPTZ |

Unique: UNIQUE(topic_id).

### 12.8 OrganizationAnalytics

| Field | Type |
|---|---|
| id | UUID |
| organization_id | UUID |
| total_students | INTEGER |
| total_assessments | INTEGER |
| total_attempts | INTEGER |
| active_users | INTEGER |
| last_calculated_on | TIMESTAMPTZ |

Unique: UNIQUE(organization_id).

---

## 13. Domain 8 - Commerce

### 13.1 Purpose

Controls what is sold, pricing, purchases, payments, access, and coupons.

### 13.2 Entities

| Entity | Purpose |
|---|---|
| AccessPackage | Entitlement bundle |
| AccessPackageCollection | Package-to-collection junction |
| SubscriptionPlan | Commercial offering |
| Purchase | Commercial event |
| PaymentRecord | Payment transaction |
| AccessGrant | Authorization source of truth |
| Coupon | Discount mechanism |

### 13.3 Separation of concerns

The following concepts are not merged.

| Concept | Role |
|---|---|
| SubscriptionPlan | What is being sold and its pricing |
| AccessPackage | What the customer receives |
| AccessGrant | What the user is currently authorized to access |
| Purchase | The commercial event |
| PaymentRecord | The payment transaction |

### 13.4 Commerce flow

```text
Assessment
    |
    v
AssessmentCollection
    |
    v
AccessPackage
    |
    v
SubscriptionPlan
    |
    v
Purchase
    |
    v
PaymentRecord
    |
    v
AccessGrant
```

### 13.5 AccessGrant

The authorization source of truth for paid content.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK to user |
| access_package_id | UUID | FK to access_package |
| start_date | TIMESTAMPTZ | |
| end_date | TIMESTAMPTZ | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

A valid grant is evaluated using all of the following.

```text
status     == ACTIVE
start_date <= now
end_date   >= now
```

### 13.6 AccessPackageCollection

Maps packages to collections.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| access_package_id | UUID | FK to access_package |
| assessment_collection_id | UUID | FK to assessment_collection |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(access_package_id, assessment_collection_id).

### 13.7 Payment provider boundary

The payment gateway is isolated behind an interface. Provider-specific code lives in adapters. Provider-specific data does not enter the domain schema.

### 13.8 Freemium

Free assessments are supported. A free assessment does not require a paid purchase. The AccessGrant-based check applies to paid content only.

---

## 14. Domain 9 - Communication

### 14.1 Purpose

Informational user communication.

### 14.2 Entities

| Entity | Purpose |
|---|---|
| Notification | In-app notification for a user |
| EmailTemplate | Reusable email content |
| Announcement | Organization-level message |

### 14.3 Notification

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK to user |
| title | VARCHAR(255) | |
| message | TEXT | |
| notification_status | Enum | UNREAD, READ |
| created_on | TIMESTAMPTZ | |

### 14.4 EmailTemplate

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| code | VARCHAR(50) | Unique |
| name | VARCHAR(150) | |
| subject | VARCHAR(255) | |
| body | TEXT | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(code).

### 14.5 Announcement

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| title | VARCHAR(255) | |
| description | TEXT | |
| start_date | TIMESTAMPTZ | |
| end_date | TIMESTAMPTZ | |
| created_by | UUID | FK to user |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

### 14.6 Communication architecture

Business modules do not directly implement email delivery.

```text
Business event
    |
    v
Notification service
    |
    v
Notification record
    |
    v
Delivery queue
    |
    v
Email or in-app
    |
    v
Delivery status
```

Long-running and external delivery is asynchronous.

---

## 15. Domain 10 - Administration

### 15.1 Purpose

Platform configuration and audit.

### 15.2 Entities

| Entity | Purpose |
|---|---|
| OrganizationSetting | Organization-level configuration |
| BusinessRule | Configurable business behaviour |
| Lookup | Reusable master values |
| AuditLog | Immutable audit record |

### 15.3 OrganizationSetting

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| organization_id | UUID | FK to organization |
| configuration | JSONB | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(organization_id).

### 15.4 BusinessRule

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| code | VARCHAR(100) | Unique |
| configuration | JSONB | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(code).

Rules are data, not executable code. Arbitrary expressions, scripts, and method invocation are prohibited.

### 15.5 Lookup

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| category | VARCHAR(100) | |
| code | VARCHAR(100) | |
| name | VARCHAR(150) | |
| display_order | INTEGER | |
| status | Enum | Lifecycle |
| audit fields | | Standard base fields |

Unique: UNIQUE(category, code).

### 15.6 AuditLog

Immutable.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK to user |
| entity_name | VARCHAR(150) | |
| entity_id | UUID | |
| action | VARCHAR(100) | |
| remarks | TEXT | |
| created_on | TIMESTAMPTZ | |

Audit is transactional. Audit insertion participates in the business transaction. Failure rolls back the business mutation.

Background operations use the system actor.

Audit records are insert-only. Update and delete are prohibited.

---

## 16. Domain 11 - Infrastructure

### 16.1 BackgroundJob

General-purpose asynchronous job record.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| job_type | VARCHAR(50) | See below |
| reference_type | VARCHAR(50) | Nullable |
| reference_id | UUID | Nullable |
| initiated_by | UUID | FK to user |
| status | Enum | QUEUED, RUNNING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, CANCELLED |
| parameters | JSONB | Nullable |
| result_reference | TEXT | Nullable |
| progress_current | INTEGER | Nullable |
| progress_total | INTEGER | Nullable |
| error_code | VARCHAR(100) | Nullable |
| error_message | TEXT | Nullable |
| queued_on | TIMESTAMPTZ | |
| started_on | TIMESTAMPTZ | Nullable |
| completed_on | TIMESTAMPTZ | Nullable |
| attempt_count | INTEGER | Default 0 |
| last_attempt_on | TIMESTAMPTZ | Nullable |

Job types.

| Code | Purpose |
|---|---|
| REPORT_GENERATION | Report artifact generation |
| IMPORT | CSV or bulk import |
| BULK_OPERATION | Bulk user or content actions |
| ANALYTICS_REFRESH | Analytics recalculation |
| SCORECARD_GENERATION | Scorecard PDF generation |

Snapshot preparation is not stored here. Snapshot state lives on student_attempt per the delivery design.

Workers claim jobs via conditional update.

```sql
UPDATE background_job
SET status = 'RUNNING', started_on = now()
WHERE id = :id AND status = 'QUEUED';
```

Only the transaction that updates one row owns the job.

---

## 17. Complete domain map

```text
Identity
  Organization
  User
  UserRole
  Role
  RolePermission
  Permission
  LoginSession

Academic
  Exam to Subject to Chapter to Topic to SubTopic
  Language
  Tag

Content
  QuestionTemplate to Question to QuestionVersion
  QuestionVersion to QuestionOption
  QuestionVersion to Asset
  QuestionVersion to Explanation
  QuestionVersion to Hint
  Question to PreviousExamHistory
  Question to Tag through QuestionTag

Assessment
  Blueprint to BlueprintVersion
  Assessment to AssessmentVersion
  AssessmentVersion to AssessmentQuestion to QuestionVersion
  AssessmentVersion to AssessmentSchedule
  AssessmentVersion to AssessmentSelectionRule
  AssessmentCollection to Assessment through CollectionAssessment

Delivery
  AssessmentSchedule to AssessmentSession to StudentAttempt
  StudentAttempt to AttemptQuestion to AttemptQuestionOption
  StudentAttempt to StudentResponse
  DeliveryPolicy to AssessmentVersion

Evaluation
  AssessmentVersion to AnswerKey to AnswerKeyDetail
  StudentAttempt to Evaluation to Result to Scorecard

Analytics
  User to StudentAnalytics
  Assessment to AssessmentAnalytics
  Question to QuestionAnalytics
  Topic to TopicAnalytics
  Organization to OrganizationAnalytics

Commerce
  AssessmentCollection to AccessPackage to SubscriptionPlan
  User to Purchase to PaymentRecord
  User to AccessGrant to AccessPackage
  Coupon to Purchase

Communication
  User to Notification
  EmailTemplate
  Announcement

Administration
  Organization to OrganizationSetting
  BusinessRule
  Lookup
  AuditLog

Infrastructure
  BackgroundJob
```

---

## 18. Core end-to-end flow

```text
Organization
    |
    v
Academic structure
    |
    v
Question bank
    |
    v
Question version
    |
    v
Blueprint
    |
    v
Blueprint version
    |
    v
Assessment
    |
    v
Assessment version
    |
    v
Assessment schedule
    |
    v
Student access
    |
    v
Snapshot preparation (async)
    |
    v
Student attempt
    |
    v
Student responses
    |
    v
Answer key and answer key detail
    |
    v
Evaluation
    |
    v
Result
    |
    v
Scorecard
    |
    v
Analytics
```

---

## 19. Lifecycles

### 19.1 Question lifecycle

```text
Question created
    |
    v
Draft version
    |
    v
Submit review
    |
    v
Approve
    |
    v
Publish version
    |
    v
Used in assessment
    |
    v
Student attempts
    |
    v
Analytics
```

Published versions are immutable.

### 19.2 Assessment lifecycle

```text
Assessment created
    |
    v
Draft version
    |
    v
Validation
    |
    v
Preview
    |
    v
Published
    |
    v
Scheduled
    |
    v
Available
    |
    v
Closed
```

### 19.3 Student attempt lifecycle

```text
NOT_STARTED
    |
    v
IN_PROGRESS
    |
    v
SUBMITTED
    |
    v
EVALUATED
```

Session state runs independently.

```text
ACTIVE
  |-- recover --> ACTIVE
  |-- end     --> LOGGED_OUT
  |-- timeout --> EXPIRED
```

### 19.4 Result lifecycle

```text
Evaluation
    |
    v
Result calculated
    |
    v
PUBLISHED
```

A published result is not silently overwritten. Corrections use controlled re-evaluation.

### 19.5 Payment lifecycle

```text
Purchase
    |
    v
PENDING
    |-- FAILED
    |-- SUCCESS
           |
           v
       AccessGrant
           |
           v
       Active access
```

Refund.

```text
SUCCESS
    |
    v
REFUNDED
```

### 19.6 Access authorization flow

```text
Student
    |
    v
Authentication
    |
    v
Authorization
    |
    v
Assessment free?
    |-- Yes -> Allow
    |-- No
          |
          v
       AccessGrant check
          |-- Valid grant -> Allow
          |-- No access   -> Deny
```

AccessGrant is the authorization source of truth for paid content.

---

## 20. Cross-domain rules

Identity may be referenced by all domains through created_by, updated_by, user_id.

Academic provides exam hierarchy references.

Content owns questions and versions. Assessment references exact question_version_id.

Assessment owns examination definitions. Delivery references assessment_version_id and assessment_schedule_id.

Delivery owns runtime attempts and responses. Evaluation consumes Delivery data.

Evaluation owns official scoring and results. Analytics consumes Evaluation and Delivery data.

Commerce owns commercial authorization. Student access is determined through Commerce services and AccessGrant. Commerce is not queried from Delivery's payment records directly.

Communication reacts to business events. It does not own business state.

Administration stores configuration and audit. It does not bypass domain services.

---

## 21. Immutable references

The following references point to versions where exact historical behaviour matters.

| Reference | Points to |
|---|---|
| AssessmentQuestion | QuestionVersion |
| AssessmentVersion | BlueprintVersion |
| Evaluation | AnswerKey |
| StudentAttempt | AssessmentVersion |
| AttemptQuestion | QuestionVersion |
| AnswerKeyDetail | QuestionVersion |

This ensures historical attempts remain reproducible.

---

## 22. Historical integrity

The platform must be able to answer, at any future time.

- What exactly did the student take?
- How was the student's score calculated?

The reproducible chain.

```text
Published QuestionVersion
      +
Published AssessmentVersion
      +
Published AnswerKey
      +
AnswerKeyDetail
      +
StudentResponse
      =
Reproducible historical result
```

---

## 23. Analytics principle

Analytics tables are projections.

```text
Transactional data
      |
      v
Background analytics processing
      |
      v
Analytics tables
      |
      v
Dashboards and reports
```

If analytics becomes inconsistent, it is recalculated from transactional data.

---

## 24. Time analytics

The delivery domain captures time_spent_seconds at the response level.

This enables question time, section time, assessment time, topic time, average student time. Section time is derived from response-level data. No redundant transactional section-time records are stored.

---

## 25. Negative marking

Negative marking is configurable at multiple levels.

| Level | Where |
|---|---|
| Assessment level | Assessment configuration |
| Question level | Question metadata |
| Question-specific negative marks | AssessmentQuestion |
| Answer key override | AnswerKeyDetail |

An exam may have negative marking while individual questions are exempt.

---

## 26. Previous-year questions

The same logical question can occur multiple times.

```text
Question Q500

PreviousExamHistory
-------------------
SSC | 2019 | Shift 1
SSC | 2021 | Shift 2
SSC | 2024 | Shift 1
```

Previous-year information is a one-to-many historical relationship, not a single year column.

---

## 27. Tags versus academic hierarchy

These concepts remain separate.

Academic hierarchy.

```text
Exam to Subject to Chapter to Topic to SubTopic
```

Tags.

```text
Question to Tag
```

Academic hierarchy represents structured curriculum. Tags represent reusable metadata.

---

## 28. Collection vs package vs plan

| Concept | Role |
|---|---|
| AssessmentCollection | Groups assessments |
| AccessPackage | Defines what is unlocked |
| SubscriptionPlan | Defines what the customer buys |
| Purchase | Records the commercial transaction |
| AccessGrant | Records actual authorization |

This separation allows pricing and entitlement models to evolve without redesigning assessment structure.

---

## 29. Session vs attempt

| Concept | Role |
|---|---|
| AssessmentSession | Runtime state |
| StudentAttempt | Permanent academic record |

A session can expire, reconnect, or recover while the attempt remains the permanent academic entity.

---

## 30. Question vs question version

| Concept | Role |
|---|---|
| Question | Logical identity |
| QuestionVersion | Exact content used by assessment |

This protects historical examination integrity.

---

## 31. Assessment vs assessment version

| Concept | Role |
|---|---|
| Assessment | Logical assessment |
| AssessmentVersion | Exact published examination definition |

---

## 32. Blueprint vs blueprint version

| Concept | Role |
|---|---|
| Blueprint | Logical examination pattern |
| BlueprintVersion | Exact published pattern |

---

## 33. Answer key versioning

An AnswerKey is versioned because a published answer may require correction.

```text
AnswerKey V1 published
    |
    v
Issue detected
    |
    v
AnswerKey V2 published
    |
    v
Controlled re-evaluation
```

Evaluation records the exact AnswerKey version applied.

---

## 34. Entity ownership matrix

| Domain | Entity |
|---|---|
| Identity | Organization, User, Role, Permission, UserRole, RolePermission, LoginSession |
| Academic | Exam, Subject, Chapter, Topic, SubTopic, Language, Tag |
| Content | Question, QuestionVersion, QuestionTemplate, QuestionOption, Asset, Explanation, Hint, PreviousExamHistory, QuestionTag |
| Assessment | Blueprint, BlueprintVersion, Assessment, AssessmentVersion, AssessmentQuestion, AssessmentSchedule, AssessmentCollection, CollectionAssessment, AssessmentSelectionRule |
| Delivery | AssessmentSession, StudentAttempt, AttemptQuestion, AttemptQuestionOption, StudentResponse, DeliveryPolicy |
| Evaluation | AnswerKey, AnswerKeyDetail, Evaluation, Result, Scorecard |
| Analytics | StudentAnalytics, AssessmentAnalytics, QuestionAnalytics, TopicAnalytics, OrganizationAnalytics |
| Commerce | AccessPackage, AccessPackageCollection, SubscriptionPlan, Purchase, PaymentRecord, AccessGrant, Coupon |
| Communication | Notification, EmailTemplate, Announcement |
| Administration | OrganizationSetting, BusinessRule, Lookup, AuditLog |
| Infrastructure | BackgroundJob |

---

## 35. Relationship summary

| Parent | Relationship | Child |
|---|---|---|
| Organization | 1:N | User |
| User | M:N | Role |
| Role | M:N | Permission |
| User | 1:N | LoginSession |
| Exam | 1:N | Subject |
| Subject | 1:N | Chapter |
| Chapter | 1:N | Topic |
| Topic | 1:N | SubTopic |
| QuestionTemplate | 1:N | Question |
| Question | 1:N | QuestionVersion |
| QuestionVersion | 1:N | QuestionOption |
| QuestionVersion | 1:N | Asset |
| QuestionVersion | 1:1 | Explanation |
| QuestionVersion | 1:0..1 | Hint |
| Question | 1:N | PreviousExamHistory |
| Question | M:N | Tag |
| Blueprint | 1:N | BlueprintVersion |
| Assessment | 1:N | AssessmentVersion |
| BlueprintVersion | 1:N | AssessmentVersion |
| AssessmentVersion | M:N | QuestionVersion |
| AssessmentVersion | 1:N | AssessmentSchedule |
| AssessmentVersion | 1:N | AssessmentSelectionRule |
| AssessmentCollection | M:N | Assessment |
| AssessmentSchedule | 1:N | AssessmentSession |
| User | 1:N | AssessmentSession |
| AssessmentSchedule | 1:N | StudentAttempt |
| AssessmentSession | 1:1 | StudentAttempt |
| StudentAttempt | 1:N | AttemptQuestion |
| AttemptQuestion | 1:N | AttemptQuestionOption |
| StudentAttempt | 1:N | StudentResponse |
| AssessmentVersion | 1:N | AnswerKey |
| AnswerKey | 1:N | AnswerKeyDetail |
| StudentAttempt | 1:N | Evaluation |
| AnswerKey | 1:N | Evaluation |
| Evaluation | 1:1 | Result |
| Result | 1:1 | Scorecard |
| User | 1:1 | StudentAnalytics |
| Assessment | 1:1 | AssessmentAnalytics |
| Question | 1:1 | QuestionAnalytics |
| Topic | 1:1 | TopicAnalytics |
| Organization | 1:1 | OrganizationAnalytics |
| AccessPackage | 1:N | SubscriptionPlan |
| AccessPackage | M:N | AssessmentCollection |
| User | 1:N | Purchase |
| SubscriptionPlan | 1:N | Purchase |
| Purchase | 1:1 | PaymentRecord |
| User | 1:N | AccessGrant |
| AccessPackage | 1:N | AccessGrant |
| Coupon | 1:N | Purchase |
| User | 1:N | Notification |
| Organization | 1:1 | OrganizationSetting |
| User | 1:N | AuditLog |
| User | 1:N | BackgroundJob |

---

## 36. Domain boundary rules

### Rule 1 - No direct cross-domain repository access

```text
AssessmentService  -> Content application contract
                   NOT ContentRepository
```

### Rule 2 - No duplicated business ownership

Assessment does not maintain a second question master. Question belongs to Content.

### Rule 3 - Use IDs across domain boundaries

Prefer question_version_id, user_id, assessment_version_id, access_package_id over embedding another domain's full object graph.

### Rule 4 - Keep transactional data relational

Core transactional information uses normal relational columns and tables.

### Rule 5 - Use JSONB selectively

JSONB is appropriate for the following.

- Blueprint configuration
- Delivery policy configuration
- Business rule configuration
- Organization settings
- Selection rule configuration

It is not a replacement for the relational model.

---

## 37. Aggregate boundaries

Aggregate roots.

- Organization
- User
- Exam
- Question
- Blueprint
- Assessment
- AssessmentSchedule
- StudentAttempt
- AnswerKey
- Result
- AccessPackage
- Purchase
- Notification
- OrganizationSetting
- BackgroundJob
- AssessmentSelectionRule

An aggregate root owns changes to its internal data.

---

## 38. Suggested transaction boundaries

### Question

```text
Question
QuestionVersion
Explanation
Hint
```

### Assessment publication

Validate the following.

```text
AssessmentVersion
BlueprintVersion
AssessmentQuestion
QuestionVersion
```

### Attempt submission

```text
StudentAttempt
StudentResponse
submission state
```

### Evaluation

```text
Evaluation
Result
```

### Purchase

```text
Purchase
PaymentRecord
AccessGrant
```

with payment provider integration and idempotency controls.

### Re-evaluation

```text
New Evaluation (version N+1)
New Result
Supersede version N
Audit
```

### Report generation

```text
background_job insert
async worker
artifact storage
job status transition
```

---

## 39. Data ownership summary

| Data | Owner |
|---|---|
| User identity | Identity |
| Exam structure | Academic |
| Question content | Content |
| Exam pattern | Assessment / Blueprint |
| Runtime attempt | Delivery |
| Official score | Evaluation |
| Performance projections | Analytics |
| Commercial transaction | Commerce |
| Communication | Communication |
| Platform configuration | Administration |
| Async job state | Infrastructure |

---

## 40. Design decisions

1. Single-organization product scope
2. UUID primary keys
3. Common base entity
4. Standard status lifecycle
5. Versioning for Question, Blueprint, Assessment, AnswerKey, Evaluation
6. Published versions are immutable
7. Exam to Subject to Chapter to Topic to SubTopic hierarchy
8. Question template supports multiple formats
9. Previous exam history supports multiple occurrences
10. Question tags are many-to-many
11. Assessment references exact QuestionVersion
12. Assessment schedule is separate from Assessment
13. Session and attempt are separate concepts
14. Student response is tied to QuestionVersion
15. Evaluation is tied to an AnswerKey version
16. Evaluation is versioned to support controlled re-evaluation
17. Result is separate from Evaluation
18. Scorecard is separate from Result
19. Analytics is derived and recalculable
20. AccessGrant is the authorization source of truth
21. Subscription plan is separated from access package
22. Purchase is separated from payment record
23. Free assessments are supported
24. Bundled assessments are supported
25. Communication is separated from business modules
26. Audit logs are immutable and transactional
27. JSONB is used selectively for configuration
28. Cross-domain references use IDs and service boundaries
29. Snapshot preparation is asynchronous and outside Start Exam
30. Stale-write protection uses HTTP conditional requests
31. System actor represents background and scheduled operations
32. General-purpose background job table serves reports, imports, bulk operations, and scorecards

---

## 41. Exclusions from the domain model

The following are not part of the current model.

- Multi-tenant SaaS architecture
- Advanced proctoring entities
- Biometric identity
- Device trust management
- SSO
- 2FA-specific domain model
- Complex marketing campaign entities
- Social or community entities
- Chat or messaging entities
- AI-specific persistent domain entities
- Advanced recommendation-engine entities
- Complex report-builder entities

These are added through future domain-model versions without complicating the current baseline.

---

## 42. Implementation principle

The database represents the business model, not the UI.

A student dashboard is a UI concept. It is not a database entity.

It derives information from StudentAttempt, Result, Analytics, AccessGrant, and Assessment.

An admin dashboard does not require an AdminDashboard table.

---

## 43. Final domain architecture

```text
                    IDENTITY
                        |
                        v
                    ACADEMIC
                        |
                        v
                     CONTENT
                        |
                        v
                    ASSESSMENT
                        |
                        v
                     DELIVERY
                        |
                        v
                    EVALUATION
                        |
                        v
                     ANALYTICS

COMMERCE        ------> ACCESS AND ENTITLEMENT
COMMUNICATION   ------> EVENTS AND NOTIFICATIONS
ADMINISTRATION  ------> CONFIGURATION AND AUDIT
INFRASTRUCTURE  ------> ASYNC JOBS AND STORAGE
```

Domains remain logically separated inside the modular monolith.

---

## 44. Entity-to-table mapping

| Entity | Table |
|---|---|
| Organization | organization |
| User | user |
| Role | role |
| Permission | permission |
| UserRole | user_role |
| RolePermission | role_permission |
| LoginSession | login_session |
| Exam | exam |
| Subject | subject |
| Chapter | chapter |
| Topic | topic |
| SubTopic | sub_topic |
| Language | language |
| Tag | tag |
| Question | question |
| QuestionVersion | question_version |
| QuestionTemplate | question_template |
| QuestionOption | question_option |
| Asset | asset |
| Explanation | explanation |
| Hint | hint |
| PreviousExamHistory | previous_exam_history |
| QuestionTag | question_tag |
| Blueprint | blueprint |
| BlueprintVersion | blueprint_version |
| Assessment | assessment |
| AssessmentVersion | assessment_version |
| AssessmentQuestion | assessment_question |
| AssessmentSchedule | assessment_schedule |
| AssessmentCollection | assessment_collection |
| CollectionAssessment | collection_assessment |
| AssessmentSelectionRule | assessment_selection_rule |
| AssessmentSession | assessment_session |
| StudentAttempt | student_attempt |
| AttemptQuestion | attempt_question |
| AttemptQuestionOption | attempt_question_option |
| StudentResponse | student_response |
| DeliveryPolicy | delivery_policy |
| AnswerKey | answer_key |
| AnswerKeyDetail | answer_key_detail |
| Evaluation | evaluation |
| Result | result |
| Scorecard | scorecard |
| StudentAnalytics | student_analytics |
| AssessmentAnalytics | assessment_analytics |
| QuestionAnalytics | question_analytics |
| TopicAnalytics | topic_analytics |
| OrganizationAnalytics | organization_analytics |
| AccessPackage | access_package |
| AccessPackageCollection | access_package_collection |
| SubscriptionPlan | subscription_plan |
| Purchase | purchase |
| PaymentRecord | payment_record |
| AccessGrant | access_grant |
| Coupon | coupon |
| Notification | notification |
| EmailTemplate | email_template |
| Announcement | announcement |
| OrganizationSetting | organization_setting |
| BusinessRule | business_rule |
| Lookup | lookup |
| AuditLog | audit_log |
| BackgroundJob | background_job |

---

## 45. Document status

Status: FROZEN domain baseline.

This document is the domain-model reference for database schema, JPA entity design, repository design, service boundaries, API design, Flyway migrations, analytics design, and test data design.

Any structural change to these domains or their major relationships is treated as an architecture change and reflected in a versioned update.