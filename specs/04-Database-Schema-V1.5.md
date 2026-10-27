# Smart Competitive Examination Platform
## Database Standards & Complete Schema

**Version:** 1.5
**Status:** FROZEN
**Database:** PostgreSQL
**ORM:** Hibernate / Spring Data JPA
**Migration:** Flyway
**Primary Key:** UUID
**Organization Scope:** Single Organization per Deployment
**Table Count:** 63

---

## 1. Purpose

This document defines the database standards and complete relational schema for the examination platform.

It is the implementation reference for PostgreSQL database creation, Flyway migrations, JPA entity mapping, repository design, foreign-key relationships, index creation, constraints, versioning, audit fields, status management, JSONB configuration, and data integrity.

The database is the transactional source of truth for the platform.

---

## 2. Database design principles

1. PostgreSQL is the primary transactional database.
2. UUID is the primary identifier.
3. Tables use singular `snake_case` names.
4. Every major business entity uses common audit fields.
5. Foreign keys enforce relationships.
6. Unique constraints protect business identifiers.
7. Indexes support actual query patterns.
8. Published examination content is immutable.
9. Versioned entities use explicit business versions.
10. Status is used for lifecycle management.
11. Historical records are not casually deleted.
12. JSONB is used only for genuinely configurable structures.
13. Core transactional data remains relational.
14. Flyway owns schema changes.
15. Production databases are not modified manually.
16. Database constraints are the final integrity boundary.

---

## 3. PostgreSQL configuration

Recommended baseline.

```text
Database:   PostgreSQL
Encoding:   UTF-8
Timezone:   UTC
Primary key: UUID
```

Timestamps representing real points in time use `TIMESTAMP WITH TIME ZONE`.

---

## 4. UUID strategy

All major entities use UUID identifiers.

UUIDs provide globally unique identifiers, safer distributed generation, no sequential business information, and easier future service extraction.

The UUID is an internal identifier. Human-facing codes use a separate `code` field where required.

---

## 5. Entity identifier vs business code

Business codes are not primary keys.

```text
id   = UUID
code = "SSC-CGL"
```

The UUID is the technical identity. The code is the business identifier.

---

## 6. Base entity standard

Major business tables use.

```text
id
status
created_by
created_on
updated_by
updated_on
```

---

## 7. Base entity field standards

| Field | PostgreSQL type | Required | Purpose |
|---|---|---|---|
| id | UUID | Yes | Primary key |
| status | VARCHAR or enum | Yes | Lifecycle state |
| created_by | UUID | Yes | Creator |
| created_on | TIMESTAMPTZ | Yes | Creation time |
| updated_by | UUID | Yes | Last updater |
| updated_on | TIMESTAMPTZ | Yes | Last update time |

System-generated records may use a controlled system identity where required.

---

## 8. Status standard

Standard entity status.

```text
ACTIVE
INACTIVE
ARCHIVED
```

ACTIVE: available for normal use.

INACTIVE: temporarily disabled.

ARCHIVED: historical and no longer operational.

---

## 9. Operational status enums

Session.

```text
ACTIVE
EXPIRED
LOGGED_OUT
```

Assessment.

```text
DRAFT
PUBLISHED
SCHEDULED
CLOSED
```

Attempt.

```text
NOT_STARTED
IN_PROGRESS
SUBMITTED
EVALUATED
```

Snapshot.

```text
PENDING
PREPARING
READY
FAILED
```

Review.

```text
DRAFT
IN_REVIEW
APPROVED
REJECTED
PUBLISHED
```

Result.

```text
CALCULATED
READY_FOR_PUBLICATION
PUBLISHED
SUPERSEDED
```

Payment.

```text
PENDING
SUCCESS
FAILED
REFUNDED
```

Notification.

```text
UNREAD
READ
```

Question outcome.

```text
NORMAL
BONUS
DROPPED
```

Background job.

```text
QUEUED
RUNNING
COMPLETED
COMPLETED_WITH_ERRORS
FAILED
CANCELLED
```

Engine type.

```text
TCS_ION
IBPS
NTA
UPSC
RAILWAY
CUSTOM
```

Delivery mode.

```text
PRACTICE
EXAM
```

---

## 10. Enum implementation strategy

Business status values are represented as controlled values.

Recommended Java implementation.

```java
public enum Status {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
```

The database representation may use PostgreSQL enum types or controlled `VARCHAR` values with check constraints. The choice remains consistent across the project.

Do not allow arbitrary free-text status values.

---

## 11. Naming standards

Tables: singular snake_case.

```text
organization
user
question
question_version
student_attempt
assessment_schedule
access_grant
background_job
```

---

## 12. Column naming

Columns use snake_case.

```text
first_name
last_name
created_on
updated_on
assessment_version_id
time_spent_seconds
```

---

## 13. Primary key naming

Every table.

```text
id UUID
```

Constraint: `pk_<table>`.

---

## 14. Foreign key naming

Foreign keys end with `_id`.

```text
organization_id
user_id
question_id
assessment_version_id
```

Constraint naming: `fk_<table>_<reference>`.

---

## 15. Unique constraint naming

Format: `uk_<table>_<column>`.

Composite: `uk_<table>_<column1>_<column2>`.

Example: `uk_user_email`, `uk_user_role_user_id_role_id`.

---

## 16. Index naming

Format: `idx_<table>_<column>`.

Composite: `idx_<table>_<column1>_<column2>`.

Example: `idx_student_attempt_user_id`, `idx_student_response_attempt_id_question_version_id`.

---

## 17. Display order

Where ordering matters.

```text
display_order INTEGER
```

Used for assessment questions, sections, ordered collections, navigation items, lookup values, and other explicitly ordered records.

Ordering is ascending. Gaps are allowed.

```text
1
2
5
10
```

is valid. The system does not require contiguous numbering.

---

## 18. Numeric standards

Marks and monetary values use `NUMERIC`, not floating-point types.

```text
marks NUMERIC(8,2)
negative_marks NUMERIC(8,2)
price NUMERIC(10,2)
percentage NUMERIC(8,2)
percentile NUMERIC(8,4)
```

Floating-point values are not used for financial amounts or official scores.

---

## 19. Text standards

Typical lengths.

```text
code        VARCHAR(50)
name        VARCHAR(150) to VARCHAR(200)
email       VARCHAR(255)
mobile      VARCHAR(20)
description TEXT
content     TEXT
```

Exact lengths may be adjusted where justified.

---

## 20. Boolean standards

Use `BOOLEAN`.

Examples: `is_published`, `marked_for_review`, `visited`, `is_correct`.

Default values are explicit where appropriate.

---

## 21. Date and time standards

Use timestamp types for created_on, updated_on, published_on, start_time, end_time, started_on, submitted_on, login_time, logout_time.

All persisted timestamps use a consistent timezone strategy.

---

## 22. JSONB standards

JSONB is allowed for flexible configuration.

Examples.

```text
blueprint_version.configuration
delivery_policy.configuration
organization_setting.configuration
business_rule.configuration
assessment_selection_rule.rule_configuration
background_job.parameters
```

JSONB is not used for core transactional relationships that need foreign keys, joins, constraints, reporting, or independent lifecycle management.

---

## 23. JSONB rule

Good use.

```json
{
  "navigation": {
    "allowBack": true
  },
  "calculator": {
    "enabled": false
  }
}
```

Bad use.

```json
{
  "studentId": "...",
  "questionId": "...",
  "score": 4,
  "paymentId": "..."
}
```

Transactional relationships remain relational.

---

## 24. Complete table inventory

### Identity

```text
organization
user
role
permission
user_role
role_permission
login_session
```

### Academic

```text
exam
subject
chapter
topic
sub_topic
language
tag
```

### Content

```text
question
question_version
question_template
question_option
asset
explanation
hint
previous_exam_history
question_tag
```

### Assessment

```text
blueprint
blueprint_version
assessment
assessment_version
assessment_question
assessment_schedule
assessment_collection
collection_assessment
assessment_selection_rule
```

### Delivery

```text
assessment_session
student_attempt
attempt_question
attempt_question_option
student_response
delivery_policy
```

### Evaluation

```text
answer_key
answer_key_detail
evaluation
result
scorecard
```

### Analytics

```text
student_analytics
assessment_analytics
question_analytics
topic_analytics
organization_analytics
```

### Commerce

```text
access_package
access_package_collection
subscription_plan
purchase
payment_record
access_grant
coupon
```

### Communication

```text
notification
email_template
announcement
```

### Administration

```text
organization_setting
business_rule
lookup
audit_log
```

### Infrastructure

```text
background_job
```

Total: 63 tables.

---

## 25. Identity schema

### 25.1 Table: organization

```sql
organization
------------
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

Constraints.

```text
PK(id)
UNIQUE(code)
NOT NULL(code)
NOT NULL(name)
NOT NULL(status)
```

Indexes.

```text
pk_organization
uk_organization_code
```

> **Status semantics (ADR-0055).** `organization.status` is not a label. `AuthenticationService` refuses
> a login whose organization is not `ACTIVE`, so a non-`ACTIVE` organization suspends **every** user in
> the tenant. The check constraint permits `ACTIVE`, `INACTIVE` and `ARCHIVED`, but the platform API
> (§72.16) accepts only the first two: the login gate collapses every non-`ACTIVE` value to the same
> outcome, so `ARCHIVED` would add vocabulary without adding behaviour. No schema change accompanies
> this note.

### 25.1a Amendment — organization profile and primary administrator (ADR-0051, ADR-0052)

> **Frozen-contract amendment.** The columns below are additions to the frozen table above. They are
> additive and every one of them is NULLable, so an existing row stays valid with no backfill.
> Recorded in ADR-0051 (profile) and ADR-0052 (primary administrator) and applied by
> `V37`/`V38`/`V39`.

```sql
organization (added columns)
---------------------------
address_line1        VARCHAR(200)   -- V37, ADR-0051
address_line2        VARCHAR(200)   -- V37
city                 VARCHAR(100)   -- V37
state_region         VARCHAR(100)   -- V37
postal_code          VARCHAR(20)    -- V37
country              VARCHAR(100)   -- V37
contact_email        VARCHAR(254)   -- V37
contact_phone        VARCHAR(32)    -- V37
website_url          VARCHAR(500)   -- V37
logo_url             VARCHAR(500)   -- V37
primary_admin_user_id UUID          -- V39, ADR-0052
```

Additional constraints.

```text
FK(primary_admin_user_id) -> user(id) ON DELETE SET NULL
```

Additional indexes.

```text
idx_organization_primary_admin (primary_admin_user_id) WHERE primary_admin_user_id IS NOT NULL
```

`primary_admin_user_id` records which administrator may change other users' roles in the
organization. It is a column on `organization` rather than a flag on `user_role` so that "at most one
primary per organization" is a schema property instead of something every writer must remember.

`NULL` means no primary is designated, and role editing is then refused for the whole organization
(fail-closed, ADR-0052). Existing rows are deliberately **not** backfilled: inferring the primary from
`created_on` would hand the authority to whoever happened to be oldest, which is precisely the
escalation the column exists to prevent. The state is recoverable because creating an organization's
first administrator designates it.

No index is added for the profile columns: none is a lookup or sort key, and `organization` is a small
table read by primary key.

#### 25.1a.1 Amendment — archiving retains the role assignment (ADR-0054)

ADR-0054 makes retiring an organization administrator an **archive** (`user.status = 'ARCHIVED'`) that
can be reversed, rather than a one-way deactivation. No schema change accompanies it; the point of this
note is an intentional invariant that a future reader could otherwise mistake for corruption:

```text
user.status          = 'ARCHIVED'    -- account archived
user_role.status     = 'ACTIVE'      -- assignment deliberately retained
organization.primary_admin_user_id   -- unchanged; an archived admin cannot be the primary
```

An archived account holding an ACTIVE `ADMIN` assignment is **correct by design**:

- Restore is then a one-field write instead of a reconstruction, so no archive-history table is needed
  (frozen rule 1 forbids inventing one for convenience).
- Access is still revoked, because `PermissionRepository.findEffectivePermissionCodes` filters
  `user.status = 'ACTIVE'`; an archived account resolves to no permissions. Session expiry and
  permission-cache eviction are additionally required at archive time because authorization never
  re-reads account status per request.
- The administrator cap and the per-organization `adminCount` both exclude `ARCHIVED` accounts
  (`UserRoleRepository.countActiveAssignmentsByRoleCodeInOrganization`,
  `findActiveUserIdsByRoleCodeInOrganization`), so a retained assignment occupies no slot and archive
  genuinely frees capacity for a replacement.

`INACTIVE` is **not** excluded from those counts. An account deactivated by other means still occupies
its slot; that is a state an operator must resolve, not capacity that can be silently reclaimed.

`V42` (ADR-0054) raises the seeded `business_rule` value `MAX_ADMINS_PER_ORG` from `1` to `5`. It is a
data `UPDATE` of an existing row, not a schema change, and not a new rule (`business_rule` has
`UNIQUE(code)`).

### 25.2 Table: user

```sql
user
----
id UUID PK
organization_id UUID FK
first_name VARCHAR(100)
last_name VARCHAR(100)
email VARCHAR(255)
mobile VARCHAR(20)
password VARCHAR(255)
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

The `password` column stores only the password hash. Never plaintext.

Constraints.

```text
PK(id)
FK(organization_id)
UNIQUE(email)
UNIQUE(mobile)
NOT NULL(organization_id)
NOT NULL(email)
NOT NULL(password)
NOT NULL(status)
```

Indexes.

```text
idx_user_organization_id
uk_user_email
uk_user_mobile
```

### 25.3 Table: role

```sql
role
----
id UUID PK
code VARCHAR(50)
name VARCHAR(100)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Constraint: `UNIQUE(code)`.

### 25.4 Table: permission

```sql
permission
----------
id UUID PK
code VARCHAR(100)
name VARCHAR(150)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Constraint: `UNIQUE(code)`.

### 25.5 Table: user_role

```sql
user_role
---------
id UUID PK
user_id UUID FK
role_id UUID FK
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(user_id, role_id)`.

Indexes.

```text
idx_user_role_user_id
idx_user_role_role_id
```

### 25.6 Table: role_permission

```sql
role_permission
---------------
id UUID PK
role_id UUID FK
permission_id UUID FK
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(role_id, permission_id)`.

Indexes.

```text
idx_role_permission_role_id
idx_role_permission_permission_id
```

### 25.7 Table: login_session

```sql
login_session
-------------
id UUID PK
user_id UUID FK
login_time TIMESTAMPTZ
logout_time TIMESTAMPTZ
session_status
ip_address VARCHAR(100)
device_info TEXT
```

Indexes.

```text
idx_login_session_user_id
idx_login_session_login_time
idx_login_session_session_status
```

### 25.8 System actor

The database contains one well-known system user for background operations.

```text
id:    00000000-0000-0000-0000-000000000001
email: system@platform.internal
```

Login is blocked for this identity.

---

## 26. Academic schema

### 26.1 Table: exam

```sql
exam
----
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

### 26.2 Table: subject

```sql
subject
-------
id UUID PK
exam_id UUID FK
code VARCHAR(50)
name VARCHAR(150)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(exam_id, code)`.

Index: `idx_subject_exam_id`.

### 26.3 Table: chapter

```sql
chapter
-------
id UUID PK
subject_id UUID FK
code VARCHAR(50)
name VARCHAR(150)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(subject_id, code)`.

Index: `idx_chapter_subject_id`.

### 26.4 Table: topic

```sql
topic
-----
id UUID PK
chapter_id UUID FK
code VARCHAR(50)
name VARCHAR(150)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(chapter_id, code)`.

Index: `idx_topic_chapter_id`.

### 26.5 Table: sub_topic

```sql
sub_topic
---------
id UUID PK
topic_id UUID FK
code VARCHAR(50)
name VARCHAR(150)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(topic_id, code)`.

Index: `idx_sub_topic_topic_id`.

### 26.6 Table: language

```sql
language
--------
id UUID PK
code VARCHAR(20)
name VARCHAR(100)
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 26.7 Table: tag

```sql
tag
---
id UUID PK
code VARCHAR(50)
name VARCHAR(100)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 26.8 Academic relationship

```text
exam
 |
 +-- subject
       |
       +-- chapter
             |
             +-- topic
                   |
                   +-- sub_topic
```

Language and Tag are reusable master entities.

---

## 27. Content schema

### 27.1 Table: question_template

```sql
question_template
-----------------
id UUID PK
code VARCHAR(50)
name VARCHAR(150)
description TEXT
configuration JSONB
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

The `configuration` field supports flags such as `requires_latex_parsing`.

### 27.2 Table: question

```sql
question
--------
id UUID PK
template_id UUID FK
exam_id UUID FK
subject_id UUID FK
chapter_id UUID FK
topic_id UUID FK
sub_topic_id UUID FK
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Taxonomy lives on the logical question.

Constraints.

```text
FK(template_id)   -> question_template(id)
FK(exam_id)       -> exam(id)
FK(subject_id)    -> subject(id)
FK(chapter_id)    -> chapter(id)
FK(topic_id)      -> topic(id)
FK(sub_topic_id)  -> sub_topic(id)
```

Indexes.

```text
idx_question_template_id
idx_question_exam_id
idx_question_subject_id
idx_question_chapter_id
idx_question_topic_id
idx_question_sub_topic_id
```

### 27.3 Table: question_version

```sql
question_version
----------------
id UUID PK
question_id UUID FK
version INTEGER
title VARCHAR(255)
content TEXT
marks NUMERIC(8,2)
negative_marks NUMERIC(8,2)
difficulty VARCHAR(20)
language_id UUID FK
is_published BOOLEAN
published_by UUID
published_on TIMESTAMPTZ
review_status VARCHAR(20)
submitted_for_review_by UUID
submitted_for_review_on TIMESTAMPTZ
reviewed_by UUID
reviewed_on TIMESTAMPTZ
review_remarks TEXT
```

Unique: `UNIQUE(question_id, version)`.

Check: `review_status IN ('DRAFT','IN_REVIEW','APPROVED','REJECTED','PUBLISHED')`.

Indexes.

```text
idx_question_version_question_id
idx_question_version_language_id
idx_question_version_review_status
```

### 27.4 Table: question_option

```sql
question_option
---------------
id UUID PK
question_version_id UUID FK
option_key VARCHAR(20)
content TEXT
display_order INTEGER
is_correct BOOLEAN
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique.

```text
UNIQUE(question_version_id, option_key)
UNIQUE(question_version_id, display_order)
```

Index: `idx_question_option_question_version_id`.

Correctness is server-side data and is not exposed to a student during active delivery.

### 27.5 Table: asset

```sql
asset
-----
id UUID PK
question_version_id UUID FK
file_name VARCHAR(255)
file_path TEXT
file_type VARCHAR(100)
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Index: `idx_asset_question_version_id`.

### 27.6 Table: explanation

```sql
explanation
-----------
id UUID PK
question_version_id UUID FK
content TEXT
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Index: `idx_explanation_question_version_id`.

### 27.7 Table: hint

```sql
hint
----
id UUID PK
question_version_id UUID FK
content TEXT
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Index: `idx_hint_question_version_id`.

### 27.8 Table: previous_exam_history

```sql
previous_exam_history
---------------------
id UUID PK
question_id UUID FK
exam_id UUID FK
year INTEGER
shift VARCHAR(50)
source VARCHAR(100)
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Indexes.

```text
idx_previous_exam_history_question_id
idx_previous_exam_history_exam_id
```

Multiple rows are allowed for the same question.

### 27.9 Table: question_tag

```sql
question_tag
------------
id UUID PK
question_id UUID FK
tag_id UUID FK
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(question_id, tag_id)`.

Indexes.

```text
idx_question_tag_question_id
idx_question_tag_tag_id
```

### 27.10 Question version rules

1. Version number is unique within a Question.
2. Version starts from 1.
3. Published versions are immutable.
4. Updating published content creates a new version.
5. Assets belong to the specific version.
6. Explanation belongs to the specific version.
7. Hint belongs to the specific version.
8. Language belongs to the specific version.
9. Review state follows the DRAFT to PUBLISHED machine.
10. Taxonomy is validated at publication: parent question must have exam_id and subject_id; the version must have language_id.

---

## 28. Assessment schema

### 28.1 Table: blueprint

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

### 28.2 Table: blueprint_version

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

### 28.3 Blueprint configuration shape

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

This is illustrative. The exact configuration contract is defined by the Assessment and Blueprint API.

### 28.4 Table: assessment

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

### 28.5 Table: assessment_version

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

### 28.6 Table: assessment_question

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

The current model may enforce `UNIQUE(assessment_version_id, question_version_id)` when a QuestionVersion appears only once within an AssessmentVersion.

### 28.7 Table: assessment_selection_rule

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

### 28.8 Rule configuration shape

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

Once materialized into `assessment_question` rows, the rule is provenance and is not re-executed at runtime.

### 28.9 Table: assessment_schedule

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

### 28.10 Table: assessment_collection

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

### 28.11 Table: collection_assessment

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

## 29. Delivery schema

### 29.1 Table: assessment_session

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

### 29.2 Table: student_attempt

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

Indexes.

```text
idx_student_attempt_user_id
idx_student_attempt_assessment_version_id
idx_student_attempt_assessment_schedule_id
idx_student_attempt_attempt_status
idx_student_attempt_snapshot_status
idx_student_attempt_schedule_snapshot_status
idx_student_attempt_started_on
```

The attempt state machine and snapshot state machine are separate.

### 29.3 Table: attempt_question

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

Unique.

```text
UNIQUE(attempt_id, question_version_id)
UNIQUE(attempt_id, display_order)
```

Indexes.

```text
idx_attempt_question_attempt_id
idx_attempt_question_question_version_id
idx_attempt_question_attempt_order
```

Rows are immutable after snapshot READY.

### 29.4 Table: attempt_question_option

```sql
attempt_question_option
-----------------------
id UUID PK
attempt_question_id UUID FK
question_option_id UUID FK
display_order INTEGER
```

Unique.

```text
UNIQUE(attempt_question_id, question_option_id)
UNIQUE(attempt_question_id, display_order)
```

Indexes.

```text
idx_attempt_question_option_attempt_question_id
idx_attempt_question_option_question_option_id
```

Preserves the exact option ordering presented to the student.

### 29.5 Table: student_response

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

Unique: `UNIQUE(attempt_id, question_version_id)`.

Indexes.

```text
idx_student_response_attempt_id
idx_student_response_question_version_id
```

Stale-write protection uses HTTP conditional requests at the API boundary.

### 29.6 Table: delivery_policy

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

Configuration example.

```json
{
  "allowBackNavigation": true,
  "allowSectionSwitch": true,
  "autoSaveIntervalSeconds": 10,
  "allowQuestionReview": true
}
```

---

## 30. Evaluation schema

### 30.1 Table: answer_key

```sql
answer_key
----------
id UUID PK
assessment_version_id UUID FK
version INTEGER
status
is_published BOOLEAN
published_by UUID
published_on TIMESTAMPTZ
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(assessment_version_id, version)`.

Index: `idx_answer_key_assessment_version_id`.

### 30.2 Table: answer_key_detail

```sql
answer_key_detail
-----------------
id UUID PK
answer_key_id UUID FK
question_version_id UUID FK
correct_option_id UUID FK
correct_answer_text TEXT
question_outcome VARCHAR(20)
awarded_marks NUMERIC(8,2)
awarded_negative_marks NUMERIC(8,2)
remarks TEXT
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(answer_key_id, question_version_id)`.

Check: `question_outcome IN ('NORMAL','BONUS','DROPPED')`.

Check: when `question_outcome = 'NORMAL'`, one of `correct_option_id` or `correct_answer_text` is present. When `question_outcome` is `BONUS` or `DROPPED`, both are null.

Indexes.

```text
idx_answer_key_detail_answer_key_id
idx_answer_key_detail_question_version_id
```

### 30.3 Table: evaluation

```sql
evaluation
----------
id UUID PK
attempt_id UUID FK
answer_key_id UUID FK
version INTEGER
superseded_by_id UUID FK
superseded_on TIMESTAMPTZ
superseded_reason TEXT
evaluated_on TIMESTAMPTZ
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(attempt_id, version)`.

Partial unique index: `UNIQUE(attempt_id) WHERE superseded_by_id IS NULL`.

Indexes.

```text
idx_evaluation_attempt_id
idx_evaluation_answer_key_id
idx_evaluation_current_lookup
```

### 30.4 Table: result

```sql
result
------
id UUID PK
evaluation_id UUID FK
obtained_marks NUMERIC(8,2)
percentage NUMERIC(8,2)
rank INTEGER
percentile NUMERIC(8,4)
result_status
superseded_on TIMESTAMPTZ
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(evaluation_id)`.

Indexes.

```text
idx_result_rank
idx_result_result_status
```

### 30.5 Table: scorecard

```sql
scorecard
---------
id UUID PK
result_id UUID FK
generated_on TIMESTAMPTZ
file_path TEXT
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(result_id)`.

Index: `idx_scorecard_result_id`.

---

## 31. Analytics schema

### 31.1 Table: student_analytics

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

### 31.2 Table: assessment_analytics

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

### 31.3 Table: question_analytics

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

### 31.4 Table: topic_analytics

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

### 31.5 Table: organization_analytics

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

### 31.6 Analytics rules

Analytics tables are derived, read-heavy, recalculable, background-updated, and non-authoritative.

Transactional records remain the source of truth.

---

## 32. Commerce schema

### 32.1 Table: access_package

```sql
access_package
--------------
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

### 32.2 Table: access_package_collection

```sql
access_package_collection
-------------------------
id UUID PK
access_package_id UUID FK
assessment_collection_id UUID FK
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(access_package_id, assessment_collection_id)`.

Indexes.

```text
idx_access_package_collection_access_package_id
idx_access_package_collection_assessment_collection_id
```

### 32.3 Table: subscription_plan

```sql
subscription_plan
-----------------
id UUID PK
access_package_id UUID FK
code VARCHAR(50)
name VARCHAR(150)
duration_days INTEGER
price NUMERIC(10,2)
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

Index: `idx_subscription_plan_access_package_id`.

### 32.4 Table: purchase

```sql
purchase
--------
id UUID PK
user_id UUID FK
subscription_plan_id UUID FK
coupon_id UUID FK NULL
purchase_date TIMESTAMPTZ
amount NUMERIC(10,2)
payment_status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Indexes.

```text
idx_purchase_user_id
idx_purchase_subscription_plan_id
idx_purchase_coupon_id
idx_purchase_purchase_date
idx_purchase_payment_status
```

### 32.5 Table: payment_record

```sql
payment_record
--------------
id UUID PK
purchase_id UUID FK
payment_reference VARCHAR(255)
payment_method VARCHAR(100)
payment_status
payment_date TIMESTAMPTZ
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(purchase_id)`.

A uniqueness constraint on `payment_reference` is applied when the chosen provider guarantees globally unique references.

### 32.6 Table: access_grant

```sql
access_grant
------------
id UUID PK
user_id UUID FK
access_package_id UUID FK
start_date TIMESTAMPTZ
end_date TIMESTAMPTZ
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Indexes.

```text
idx_access_grant_user_id
idx_access_grant_access_package_id
idx_access_grant_start_date
idx_access_grant_end_date
idx_access_grant_status
```

AccessGrant is the authorization source of truth for paid content.

### 32.7 Table: coupon

```sql
coupon
------
id UUID PK
code VARCHAR(50)
name VARCHAR(150)
discount_value NUMERIC(10,2)
valid_from TIMESTAMPTZ
valid_to TIMESTAMPTZ
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

---

## 33. Communication schema

### 33.1 Table: notification

```sql
notification
------------
id UUID PK
user_id UUID FK
title VARCHAR(255)
message TEXT
notification_status
created_on TIMESTAMPTZ
```

Indexes.

```text
idx_notification_user_id
idx_notification_notification_status
```

### 33.2 Table: email_template

```sql
email_template
--------------
id UUID PK
code VARCHAR(50)
name VARCHAR(150)
subject VARCHAR(255)
body TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 33.3 Table: announcement

```sql
announcement
------------
id UUID PK
title VARCHAR(255)
description TEXT
start_date TIMESTAMPTZ
end_date TIMESTAMPTZ
created_by UUID FK
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Indexes.

```text
idx_announcement_created_by
idx_announcement_start_date
idx_announcement_end_date
idx_announcement_status
```

---

## 34. Administration schema

### 34.1 Table: organization_setting

```sql
organization_setting
--------------------
id UUID PK
organization_id UUID FK
configuration JSONB
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(organization_id)`.

### 34.2 Table: business_rule

```sql
business_rule
-------------
id UUID PK
code VARCHAR(100)
configuration JSONB
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 34.3 Table: lookup

```sql
lookup
------
id UUID PK
category VARCHAR(100)
code VARCHAR(100)
name VARCHAR(150)
display_order INTEGER
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(category, code)`.

Indexes.

```text
idx_lookup_category
idx_lookup_category_display_order
```

### 34.4 Table: audit_log

```sql
audit_log
---------
id UUID PK
user_id UUID FK
entity_name VARCHAR(150)
entity_id UUID
action VARCHAR(100)
remarks TEXT
created_on TIMESTAMPTZ
```

Indexes.

```text
idx_audit_log_user_id
idx_audit_log_entity_name
idx_audit_log_entity_id
idx_audit_log_created_on
```

Audit logs are immutable. Insert is allowed. Update and delete are prohibited.

---

## 35. Infrastructure schema

### 35.1 Table: background_job

```sql
background_job
--------------
id UUID PK
job_type VARCHAR(50)
reference_type VARCHAR(50)
reference_id UUID
initiated_by UUID FK
status VARCHAR(20)
parameters JSONB
result_reference TEXT
progress_current INTEGER
progress_total INTEGER
error_code VARCHAR(100)
error_message TEXT
queued_on TIMESTAMPTZ
started_on TIMESTAMPTZ
completed_on TIMESTAMPTZ
attempt_count INTEGER
last_attempt_on TIMESTAMPTZ
```

Check: `status IN ('QUEUED','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','CANCELLED')`.

Indexes.

```text
idx_background_job_job_type
idx_background_job_initiated_by
idx_background_job_status
idx_background_job_queued_on
idx_background_job_reference
```

Workers claim jobs via conditional update.

```sql
UPDATE background_job
SET status = 'RUNNING', started_on = now()
WHERE id = :id AND status = 'QUEUED';
```

Only the transaction that updates one row owns the job.

Snapshot preparation is not stored here. Snapshot state lives on `student_attempt`.

---

## 36. Complete relationship map

```text
organization
    |
    +--< user
    |       |
    |       +--< login_session
    |       +--< user_role >---- role
    |       |                       |
    |       |                       +--< role_permission >---- permission
    |       |
    |       +--< student_attempt
    |       +--< assessment_session
    |       +--< purchase
    |       +--< access_grant
    |       +--< notification
    |       +--< audit_log
    |       +--< background_job
    |
    +-- organization_setting
    |
    +-- organization_analytics

exam
    |
    +--< subject
             |
             +--< chapter
                      |
                      +--< topic
                               |
                               +--< sub_topic

question_template
    |
    +--< question
             |
             +--< question_version
             |       |
             |       +--< question_option
             |       +--< asset
             |       +--< explanation
             |       +--< hint
             |
             +--< previous_exam_history >---- exam
             |
             +--< question_tag >---- tag

blueprint
    |
    +--< blueprint_version
                |
                +--< assessment_version
                          |
                          +--< assessment_question >---- question_version
                          |
                          +--< assessment_schedule
                          |
                          +--< assessment_selection_rule
                          |
                          +--< answer_key
                                    |
                                    +--< answer_key_detail >---- question_version

assessment
    |
    +--< assessment_version
    |
    +--< collection_assessment >---- assessment_collection

assessment_schedule
    |
    +--< assessment_session
                |
                +-- student_attempt
                          |
                          +--< attempt_question
                          |         |
                          |         +--< attempt_question_option
                          |
                          +--< student_response
                          |
                          +--< evaluation (versioned)
                                    |
                                    +-- result
                                            |
                                            +-- scorecard

assessment
    |
    +-- assessment_analytics

question
    |
    +-- question_analytics

topic
    |
    +-- topic_analytics

access_package
    |
    +--< subscription_plan
    |           |
    |           +--< purchase
    |                     |
    |                     +-- payment_record
    |
    +--< access_grant
    |
    +--< access_package_collection >---- assessment_collection

coupon
    |
    +--< purchase

user
    |
    +--< notification
    +--< audit_log
    +--< background_job

email_template
announcement
```

---

## 37. Foreign key reference summary

| Child table | Foreign key | Parent |
|---|---|---|
| user | organization_id | organization |
| user_role | user_id | user |
| user_role | role_id | role |
| role_permission | role_id | role |
| role_permission | permission_id | permission |
| login_session | user_id | user |
| subject | exam_id | exam |
| chapter | subject_id | subject |
| topic | chapter_id | chapter |
| sub_topic | topic_id | topic |
| question | template_id | question_template |
| question | exam_id | exam |
| question | subject_id | subject |
| question | chapter_id | chapter |
| question | topic_id | topic |
| question | sub_topic_id | sub_topic |
| question_version | question_id | question |
| question_version | language_id | language |
| question_option | question_version_id | question_version |
| asset | question_version_id | question_version |
| explanation | question_version_id | question_version |
| hint | question_version_id | question_version |
| previous_exam_history | question_id | question |
| previous_exam_history | exam_id | exam |
| question_tag | question_id | question |
| question_tag | tag_id | tag |
| blueprint_version | blueprint_id | blueprint |
| assessment_version | assessment_id | assessment |
| assessment_version | blueprint_version_id | blueprint_version |
| assessment_version | delivery_policy_id | delivery_policy |
| assessment_question | assessment_version_id | assessment_version |
| assessment_question | question_version_id | question_version |
| assessment_selection_rule | assessment_version_id | assessment_version |
| assessment_schedule | assessment_version_id | assessment_version |
| collection_assessment | collection_id | assessment_collection |
| collection_assessment | assessment_id | assessment |
| assessment_session | user_id | user |
| assessment_session | assessment_schedule_id | assessment_schedule |
| assessment_session | attempt_id | student_attempt |
| student_attempt | user_id | user |
| student_attempt | assessment_version_id | assessment_version |
| student_attempt | assessment_schedule_id | assessment_schedule |
| attempt_question | attempt_id | student_attempt |
| attempt_question | question_version_id | question_version |
| attempt_question_option | attempt_question_id | attempt_question |
| attempt_question_option | question_option_id | question_option |
| student_response | attempt_id | student_attempt |
| student_response | question_version_id | question_version |
| answer_key | assessment_version_id | assessment_version |
| answer_key_detail | answer_key_id | answer_key |
| answer_key_detail | question_version_id | question_version |
| answer_key_detail | correct_option_id | question_option |
| evaluation | attempt_id | student_attempt |
| evaluation | answer_key_id | answer_key |
| evaluation | superseded_by_id | evaluation |
| result | evaluation_id | evaluation |
| scorecard | result_id | result |
| student_analytics | user_id | user |
| assessment_analytics | assessment_id | assessment |
| question_analytics | question_id | question |
| topic_analytics | topic_id | topic |
| organization_analytics | organization_id | organization |
| access_package_collection | access_package_id | access_package |
| access_package_collection | assessment_collection_id | assessment_collection |
| subscription_plan | access_package_id | access_package |
| purchase | user_id | user |
| purchase | subscription_plan_id | subscription_plan |
| purchase | coupon_id | coupon |
| payment_record | purchase_id | purchase |
| access_grant | user_id | user |
| access_grant | access_package_id | access_package |
| notification | user_id | user |
| announcement | created_by | user |
| organization_setting | organization_id | organization |
| audit_log | user_id | user |
| background_job | initiated_by | user |

---

## 38. Cascading delete policy

Hard cascading deletes are used carefully.

For historical examination data.

```text
Do NOT cascade delete
```

Examples.

```text
Question
Assessment
StudentAttempt
Result
Payment
AuditLog
BackgroundJob
```

Historical records remain consistent. Prefer ARCHIVED over physical deletion.

---

## 39. Referential integrity

The database prevents orphan records.

A `student_response` cannot reference a nonexistent `student_attempt`.

An `assessment_question` cannot reference a nonexistent `question_version`.

An `answer_key_detail` cannot reference a nonexistent `answer_key` or `question_version`.

---

## 40. Versioning constraints

For versioned entities.

```text
UNIQUE(parent_id, version)
```

Examples.

```text
UNIQUE(question_id, version)
UNIQUE(blueprint_id, version)
UNIQUE(assessment_id, version)
UNIQUE(assessment_version_id, version)
UNIQUE(attempt_id, version)          -- evaluation
```

---

## 41. Publication constraints

The application enforces `is_published = true` only after required validation. A database constraint alone cannot fully validate complex publication rules.

Application business validation and database integrity work together.

---

## 42. Assessment publication validation

Before publishing.

```text
AssessmentVersion
      |
      +-- BlueprintVersion exists
      +-- BlueprintVersion published and valid
      +-- Questions exist
      +-- QuestionVersions valid and published
      +-- Question count valid
      +-- Section counts valid
      +-- Marks valid
      +-- Negative marking valid
      +-- Duration valid
      +-- engine_type present
      +-- delivery_policy_id present
      +-- Engine type supported by registry
      +-- Required configuration valid
```

Only after validation: `is_published = true`.

---

## 43. Question publication validation

Before publishing.

```text
Question
   |
   +-- Template valid
   +-- Content exists
   +-- Canonical content format valid
   +-- Raw HTML absent
   +-- Required metadata exists
   +-- Marks valid
   +-- Required assets and data valid
   +-- question.exam_id present
   +-- question.subject_id present
   +-- question_version.language_id present
   +-- review_status = APPROVED
   +-- Submitted reviewer and approver differ where the same user holds both permissions
```

---

## 44. Answer key publication validation

Before publication.

```text
AnswerKey
   |
   +-- AssessmentVersion valid and published
   +-- Exactly one answer_key_detail per AssessmentQuestion
   +-- NORMAL rows have valid correct_option_id or correct_answer_text
   +-- correct_option_id belongs to the same question_version_id
   +-- BONUS and DROPPED rows have no correct answer
   +-- Scoring configuration valid
```

---

## 45. Result integrity

Result is traceable.

```text
Result
 |
 +-- Evaluation
      |
      +-- StudentAttempt
      |     |
      |     +-- AssessmentVersion
      |     +-- AttemptQuestion set
      |
      +-- AnswerKey
            |
            +-- AnswerKeyDetail
```

This provides the historical chain required to reproduce the score.

---

## 46. Data lineage

```text
Student result
    |
    v
Evaluation
    |
    +-- AnswerKey version
    |       |
    |       +-- AnswerKeyDetail set
    |
    +-- StudentAttempt
            |
            +-- AssessmentVersion
            |       |
            |       +-- BlueprintVersion
            |       +-- AssessmentQuestion
            |       +-- AssessmentSelectionRule
            |
            +-- AttemptQuestion
                    |
                    +-- QuestionVersion
                            |
                            +-- Question
```

This is one of the most important data-integrity chains in the system.

---

## 47. Financial data lineage

```text
Purchase
   |
   +-- SubscriptionPlan
   |        |
   |        +-- AccessPackage
   |                 |
   |                 +-- AccessPackageCollection
   |                          |
   |                          +-- AssessmentCollection
   |
   +-- Coupon
   |
   +-- PaymentRecord
            |
            v
       Payment status
            |
            v
        AccessGrant
```

The platform can explain how a user's access was obtained.

---

## 48. Access decision

A paid assessment access check.

```text
User
 |
 v
AccessGrant
 |
 +-- Active?
 +-- Within start date?
 +-- Within end date?
 +-- Package contains required content?
       |
       v
     ALLOW / DENY
```

Free assessments may use a separate free-access rule.

---

## 49. Database transaction boundaries

### User creation

```text
user
+
role assignment
```

### Question publication

```text
question_version state
+
audit
```

### Assessment publication

```text
assessment_version
+
publication state
+
audit
```

### Attempt submission

```text
attempt
+
responses
+
submission state
```

### Evaluation

```text
evaluation
+
result
```

### Re-evaluation

```text
new evaluation version
+
new result
+
supersede previous evaluation
+
audit
```

### Purchase completion

```text
purchase
+
payment
+
access grant
```

### Report generation

```text
background_job insert
+
async worker
+
artifact storage
+
job status transition
```

---

## 50. Database performance rules

Avoid `SELECT *` for large APIs. Prefer explicit columns.

Avoid unbounded queries. Use pagination.

Avoid repeated database lookups for static configuration. Use Redis where appropriate.

---

## 51. Query optimization

For frequently accessed data use indexes, projection queries, pagination, batch fetching, and query optimization.

Do not add indexes without considering read performance, write overhead, storage, and query pattern.

---

## 52. Large tables

Expected growth areas.

```text
student_response
student_attempt
attempt_question
attempt_question_option
assessment_session
audit_log
notification
previous_exam_history
background_job
```

These tables are monitored for growth. Future partitioning may be introduced if actual volume requires it.

Partitioning is not required by default.

---

## 53. Audit log growth

Audit logs can grow rapidly. The system supports future operational retention and archival without affecting transactional tables.

Do not prematurely partition unless actual scale requires it.

---

## 54. Student response growth

`student_response` may become one of the largest tables.

Primary operational access pattern.

```text
attempt_id
+
question_version_id
```

Therefore `UNIQUE(attempt_id, question_version_id)` is important.

---

## 55. Assessment attempt query pattern

```sql
SELECT ...
FROM student_attempt
WHERE user_id = ?
AND assessment_version_id = ?
ORDER BY started_on DESC;
```

Therefore.

```text
idx_student_attempt_user_id
idx_student_attempt_assessment_version_id
```

are required initially. Composite indexes may be added after observing real query patterns.

---

## 56. Audit search pattern

Typical filters.

```text
user_id
entity_name
entity_id
created_on
```

These are indexed appropriately.

---

## 57. Migration structure

Recommended Flyway grouping.

```text
db/migration/

V1__create_base_schema.sql
V2__create_identity_tables.sql
V3__create_academic_tables.sql
V4__create_content_tables.sql
V5__create_assessment_tables.sql
V6__create_delivery_tables.sql
V7__create_evaluation_tables.sql
V8__create_analytics_tables.sql
V9__create_commerce_tables.sql
V10__create_communication_tables.sql
V11__create_administration_tables.sql
V12__create_indexes.sql
V13__insert_initial_master_data.sql

V100__answer_key_detail.sql
V101__evaluation_versioning.sql
V102__question_taxonomy.sql
V103__question_version_review_state.sql
V104__assessment_selection_rule.sql
V105__assessment_engine.sql
V106__background_job.sql
V107__seed_system_actor.sql
```

Actual migration numbering may differ.

---

## 58. Migration rules

Never edit an already-applied production migration.

Existing migration stays. New migration is added.

```text
V15__add_new_column.sql
```

---

## 59. Seed data

Some system master data may be inserted through Flyway.

Examples: roles, permissions, system lookup values, question templates, supported languages, default delivery policy, system actor.

Seed data is idempotent or protected by appropriate uniqueness rules.

---

## 60. Role seed data

Initial role concepts.

```text
ADMIN
REVIEWER
STAFF
STUDENT
SUPPORT
FINANCE
```

The exact permission assignments are maintained through role-permission configuration.

---

## 61. Question template seed data

Initial templates may include the formats required by the first release. The template architecture allows future templates.

---

## 62. Organization scope

The platform is designed for one organization at a time.

The database does not include multi-tenant isolation architecture. `organization_id` remains where the domain model requires organizational ownership.

This provides a path for future expansion without making the current baseline unnecessarily complex.

---

## 63. Soft delete rules

Use `status` rather than physical deletion when historical references exist.

Example.

```text
Question
   |
   v
ARCHIVED
```

rather than `DELETE FROM question`.

---

## 64. When physical delete is allowed

Physical deletion is considered only for records that have no historical significance, are not referenced, are not audit records, are not financial records, and are not required for regulatory or business retention.

Even then, deletion is a controlled business operation.

---

## 65. Database security

Database access uses an application DB user with only required permissions.

The PostgreSQL superuser is not used from the application.

Credentials come from secure configuration.

---

## 66. Database backup

At minimum: regular automated backup, retention policy, and restore testing.

Backups are stored separately from the primary database environment.

---

## 67. Database monitoring

Monitor connection count, connection pool, query latency, slow queries, locks, disk usage, transaction volume, index health, and database availability.

---

## 68. Connection pool

The application uses a managed connection pool. Pool size is configured based on database capacity, application instances, expected concurrency, and query duration.

Do not use a very large pool blindly.

In production the application connects through PgBouncer.

---

## 69. Database timeouts

Appropriate timeouts exist for connection acquisition, database query, transaction, and external database calls.

Long-running operations generally move to background processing.

---

## 70. Data consistency principle

For transactional operations, atomicity, consistency, isolation, and durability are preserved.

Examples: attempt submission, payment completion, result publication, access grant, re-evaluation.

---

## 71. Schema review checklist

Before creating each table.

```text
[ ] Correct domain owner
[ ] Correct primary key
[ ] Correct foreign keys
[ ] Required audit fields
[ ] Correct status
[ ] Correct data types
[ ] Unique constraints
[ ] Indexes
[ ] Delete strategy
[ ] Versioning requirement
[ ] Historical requirement
```

---

## 72. Entity-to-table mapping

| Domain entity | Table |
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

## 73. Frozen database rules

1. PostgreSQL.
2. UUID primary keys.
3. Singular snake_case tables.
4. Common audit fields.
5. ACTIVE, INACTIVE, ARCHIVED standard lifecycle.
6. Domain-specific status values.
7. Question versioning.
8. Blueprint versioning.
9. Assessment versioning.
10. Answer key versioning.
11. Published versions immutable.
12. Foreign keys for relationships.
13. Unique business codes.
14. Display order where ordering matters.
15. JSONB for flexible configuration only.
16. Relational storage for transactional data.
17. Flyway migrations.
18. No manual production schema changes.
19. Analytics is derived.
20. Audit log is immutable and transactional.
21. Historical examination data is not casually deleted.
22. AccessGrant is the paid-access authorization source of truth.
23. Financial amounts use NUMERIC.
24. Student responses use one record per attempt and question version.
25. Previous exam history supports multiple occurrences.
26. Assessment schedules are separate from assessments.
27. Sessions are separate from attempts.
28. Evaluation is versioned to support controlled re-evaluation.
29. Answer key detail stores per-question correctness.
30. Attempt questions and options are immutable after snapshot READY.
31. Background jobs are claimed by conditional state transition.
32. System actor represents background and scheduled operations.

---

## 74. Final database architecture

```text
                    +------------------+
                    |    PostgreSQL    |
                    +--------+---------+
                             |
        +--------------------+---------------------+
        |                    |                     |
        v                    v                     v
   Identity              Academic               Content
        |                    |                     |
        +--------------------+---------------------+
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
                          Analytics

        Commerce -------------> Access
        Communication --------> Notifications
        Administration -------> Configuration and audit
        Infrastructure -------> Async jobs
```

---

## 75. Final data integrity chain

The most important academic data chain.

```text
Question
   |
   v
QuestionVersion
   |
   v
AssessmentQuestion
   |
   v
AssessmentVersion
   |
   v
AssessmentSchedule
   |
   v
StudentAttempt
   |
   v
AttemptQuestion + AttemptQuestionOption
   |
   v
StudentResponse
   |
   v
AnswerKey + AnswerKeyDetail
   |
   v
Evaluation
   |
   v
Result
   |
   v
Scorecard
```

The system preserves this chain throughout the lifecycle.

---

## 76. Final technical principle

The database is relational where relationships and transactions matter, versioned where historical reproducibility matters, configurable where business behaviour legitimately varies, and simple everywhere else.

The schema supports the product without turning the database into a generic configuration engine.

---

## 77. Document status

**Status:** FROZEN database baseline.

This document is the database implementation reference for PostgreSQL schema, Flyway migrations, JPA entities, repository queries, constraints, indexes, audit implementation, versioning, data integrity, and database testing.