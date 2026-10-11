# Smart Competitive Examination Platform
## P2 — Academic Structure Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P2 — Academic Structure
**Depends on:** P0 Foundation, P1 Identity and Security, ExamEngine and CDUI Contract
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**API:** REST + JSON, `/api/v1`
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Architects

---

## 1. Purpose

This document converts the Academic domain into an implementation-ready engineering specification.

The Academic domain owns:

```text
Exam
Subject
Chapter
Topic
SubTopic
Language
Tag
```

The core hierarchy is:

```text
Exam
 |
 v
Subject
 |
 v
Chapter
 |
 v
Topic
 |
 v
SubTopic
```

Language and Tag are reusable master entities and are deliberately outside the academic hierarchy.

This phase provides the master data required by the Content and Assessment phases.

The implementation preserves the frozen database and API contracts and does not introduce additional academic tables unless a formally approved architecture change requires it.

---

## 2. Source-of-truth hierarchy

Read these documents before P2 implementation:

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
- This document

Frozen Academic model:

```text
exam
subject
chapter
topic
sub_topic
language
tag
```

Frozen relationships:

```text
Exam 1:N Subject
Subject 1:N Chapter
Chapter 1:N Topic
Topic 1:N SubTopic
```

Language is reusable across questions and examination configuration.

Tag is reusable metadata and is not part of the hierarchy.

---

## 3. P2 objective

P2 makes the following possible:

```text
Create exam master
Create subject under exam
Create chapter under subject
Create topic under chapter
Create sub-topic under topic
Manage reusable languages
Manage reusable tags
View the hierarchy
Search and filter master data
Update active master data
Archive master data safely
Audit administrative changes
Serve stable APIs for Content and Assessment
```

P2 is successful only when Academic data can be used directly by the next Content implementation phase without requiring schema redesign.

---

## 4. Pilot-first scope

The product remains single-organization per deployment.

The academic model is intentionally organization-agnostic at the table level because the `exam` table does not contain an `organization_id`.

Therefore:

```text
Current deployment
    |
    v
One organization
    |
    v
Shared academic masters within that deployment
```

Do not add `organization_id` to the Academic tables during P2.

Do not create a multi-tenant academic partitioning model.

Do not introduce tenant-aware routing.

The application still avoids hard-coding a specific institute name or a specific exam list.

Future multi-organization support can be introduced through a formal architecture and schema version.

---

## 5. P2 non-goals

P2 does not implement:

```text
Question
QuestionVersion
QuestionTemplate
Asset
Explanation
Hint
PreviousExamHistory
QuestionTag
Blueprint
Assessment
Delivery
Evaluation
Analytics
Commerce
Communication
Full Administration
AI question generation
Advanced taxonomy graph
Ontology management
Curriculum recommendation engine
Natural-language taxonomy management
```

The Tag master is implemented because it belongs to the Academic domain. The QuestionTag junction remains part of the Content phase.

---

## 6. Architectural position

Academic sits directly after Identity.

```text
Foundation
    |
    v
Identity and security
    |
    v
Academic
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
Analytics
```

Academic is a master-data domain.

It is not an examination runtime domain.

It does not:

```text
Start an attempt
Save student responses
Evaluate answers
Resolve delivery policy
Publish answer keys
```

Those responsibilities belong to later domains.

---

## 7. Academic domain ownership

Academic owns:

```text
Exam
Subject
Chapter
Topic
SubTopic
Language
Tag
```

Academic owns the integrity of the hierarchy.

Academic does not own:

```text
Question content
Question versions
Question options
Assessment questions
Student responses
Score calculations
Commercial access
```

Cross-domain rules use explicit application and service contracts.

Another module does not mutate Academic entities through direct JPA repository access.

---

## 8. Domain relationship rules

### 8.1 Exam to subject

```text
Exam 1:N Subject
```

A Subject belongs to exactly one Exam. A Subject cannot exist without its parent Exam.

### 8.2 Subject to chapter

```text
Subject 1:N Chapter
```

A Chapter belongs to exactly one Subject. A Chapter cannot exist without its parent Subject.

### 8.3 Chapter to topic

```text
Chapter 1:N Topic
```

A Topic belongs to exactly one Chapter. A Topic cannot exist without its parent Chapter.

### 8.4 Topic to subtopic

```text
Topic 1:N SubTopic
```

A SubTopic belongs to exactly one Topic. A SubTopic cannot exist without its parent Topic.

### 8.5 Language

Language is a reusable master.

It may later be referenced by Question, QuestionVersion, and Assessment configuration.

The Academic phase does not create duplicate language records for each exam.

### 8.6 Tag

Tag is generic reusable metadata.

Examples from the conceptual model include:

```text
ALGEBRA
CURRENT_AFFAIRS
HIGH_DIFFICULTY
PYQ
IMPORTANT
```

These are examples, not a closed enumeration.

The tag catalog remains configurable master data.

---

## 9. Frozen database schema

The exact frozen table definitions are implementation constraints.

### 9.1 Table: exam

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

Unique constraint: `UNIQUE(code)`.

The business code is not the primary key. The UUID remains the technical identifier.

### 9.2 Table: subject

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

Unique constraint: `UNIQUE(exam_id, code)`.

Index: `idx_subject_exam_id`.

### 9.3 Table: chapter

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

Unique constraint: `UNIQUE(subject_id, code)`.

Index: `idx_chapter_subject_id`.

### 9.4 Table: topic

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

Unique constraint: `UNIQUE(chapter_id, code)`.

Index: `idx_topic_chapter_id`.

### 9.5 Table: sub_topic

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

Unique constraint: `UNIQUE(topic_id, code)`.

Index: `idx_sub_topic_topic_id`.

### 9.6 Table: language

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

Unique constraint: `UNIQUE(code)`.

### 9.7 Table: tag

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

Unique constraint: `UNIQUE(code)`.

---

## 10. Database rules

P2 preserves the following database rules.

- PostgreSQL
- UUID primary keys
- Singular snake_case tables
- Common audit fields
- Foreign keys
- Unique business codes
- Status lifecycle
- Historical preservation
- Flyway migrations
- Relational modeling

Do not replace relational foreign keys with JSONB.

Do not embed subject IDs inside exam JSON, chapter IDs inside subject JSON, topic IDs inside chapter JSON, sub-topic IDs inside topic JSON, or tag IDs inside tag JSON.

The hierarchy is relational.

---

## 11. Status lifecycle

Academic records use the standard lifecycle.

```text
ACTIVE
INACTIVE
ARCHIVED
```

Typical lifecycle.

```text
Create
  |
  v
ACTIVE
  |
  +-- deactivate -> INACTIVE
  |
  +-- archive    -> ARCHIVED
  |
  +-- reactivate where business rules permit -> ACTIVE
```

A physical hard delete is not exposed as the normal administrative operation.

The API contract uses DELETE to represent archive or deactivation behavior.

---

## 12. Parent-child integrity

The backend validates parent existence before creating child records.

Examples.

```text
Create subject
    |
    v
Exam must exist

Create chapter
    |
    v
Subject must exist

Create topic
    |
    v
Chapter must exist

Create sub-topic
    |
    v
Topic must exist
```

The database foreign key is the final integrity layer. The service layer provides a clear business error before the database constraint is hit whenever possible.

---

## 13. Parent status rules

The service layer prevents creation of a child under a parent that is not eligible for new children.

Required behavior.

```text
ACTIVE parent      -> create child allowed
INACTIVE parent    -> create child rejected unless an explicit business rule allows it
ARCHIVED parent    -> create child rejected
```

This rule is centralized in the Academic service layer.

It is not duplicated independently inside controllers and repositories.

---

## 14. Archive semantics

Archiving a parent creates a hierarchy decision.

The frozen source does not specify a cascading archive algorithm.

P2 implements a safe service rule without silently changing the schema.

```text
Archive parent
    |
    v
Check active descendants
    |
    v
If descendants exist:
    reject with explicit conflict
or
apply an explicitly approved controlled cascade
```

For the initial implementation, prefer: do not silently cascade. Return a business conflict requiring the operator to address descendants first.

This avoids unexpected changes to content taxonomy.

---

## 15. Referential safety for future content

P2 anticipates that later Content records will depend on Academic records.

Example.

```text
Question
   |
   v
Subject
Chapter
Topic
SubTopic
```

An Academic record may already be referenced by content.

Therefore archive over physical delete is the normal lifecycle.

Future Content APIs continue to reference an archived taxonomy node for historical questions where needed.

The Academic phase does not create a new usage table just to manage this.

---

## 16. Business code rules

The frozen schema defines `code` as a business identifier.

Examples.

```text
SSC-CGL
MATH
ALGEBRA
QUADRATIC-EQUATIONS
ENG
PYQ
```

The exact coding convention is not frozen by the source documents.

Implementation uses trim, rejects blank, validates max length, and enforces uniqueness via the database.

The implementation may normalize code representation consistently, but any such normalization rule is documented in code-level validation tests.

Database lengths are not changed. Display names are not used as identifiers.

---

## 17. Name rules

Names should:

```text
Not be blank
Respect maximum schema length
Preserve user-visible meaning
Not be used as foreign keys
```

Do not derive child IDs from names. Do not rely on names remaining unique. Business uniqueness is provided by the frozen code constraints.

---

## 18. Description rules

Descriptions are `TEXT`.

They are optional unless a future business rule explicitly makes them required.

The service safely accepts NULL, empty or blank only if the API contract permits, and normal Unicode text.

Do not introduce an arbitrary maximum description size that conflicts with the schema unless a documented API validation limit is established.

---

## 19. Language rules

Language uses `code VARCHAR(20)` and `name VARCHAR(100)`.

Language code is reusable.

Do not hard-code only ENGLISH, HINDI, or MARATHI as a closed enum in Java.

The database is master-data driven.

The actual catalog may be seeded according to the pilot organization's needs.

---

## 20. Tag rules

Tags use `code VARCHAR(50)`, `name VARCHAR(100)`, and `description TEXT`.

Tags are reusable.

Tags must not be used as substitutes for Subject, Chapter, Topic, or SubTopic.

A taxonomy node describes academic structure. A tag describes reusable metadata.

This distinction remains explicit in the implementation.

---

## 21. Domain entities

Recommended entities:

```text
academic/entity/Exam.java
academic/entity/Subject.java
academic/entity/Chapter.java
academic/entity/Topic.java
academic/entity/SubTopic.java
academic/entity/Language.java
academic/entity/Tag.java
```

Each entity extends the established common audit and base entity pattern from P0 and P1 where the project implementation already provides that abstraction.

A second competing base entity implementation is not created inside Academic.

---

## 22. Entity mapping requirements

Use JPA relationship mappings only where they simplify safe aggregate operations.

Recommended ownership.

```text
Exam
  -> Subject

Subject
  -> Chapter

Chapter
  -> Topic

Topic
  -> SubTopic
```

However, do not automatically model every hierarchy edge with eager-loaded bidirectional collections.

Avoid.

```java
@OneToMany(fetch = FetchType.EAGER)
private List<Subject> subjects;
```

for master-data lists.

Prefer explicit repository queries and controlled fetch strategies. This prevents hierarchy screens from accidentally loading the entire tree.

---

## 23. UUID strategy

All Academic entities use UUID id.

IDs are generated by the platform according to the foundation's UUID policy.

Do not expose sequential database IDs. Do not derive UUIDs from business codes.

---

## 24. Repository layer

Create:

```text
ExamRepository
SubjectRepository
ChapterRepository
TopicRepository
SubTopicRepository
LanguageRepository
TagRepository
```

Repositories extend the project's approved Spring Data JPA repository base.

Repositories contain persistence and query concerns. They do not contain business policy.

---

## 25. Required repository queries

### 25.1 Exam

At minimum:

```text
findById
findAll with pagination
findByCode
existsByCode
findByStatus
search by code and name
```

### 25.2 Subject

At minimum:

```text
findById
findAllByExamId
findAllByExamIdAndStatus
findByExamIdAndCode
existsByExamIdAndCode
searchByExamId
```

Pagination is required for general list APIs. Hierarchy screens must be able to request children of a single parent without loading unrelated data.

### 25.3 Chapter

At minimum:

```text
findById
findAllBySubjectId
findAllBySubjectIdAndStatus
findBySubjectIdAndCode
existsBySubjectIdAndCode
searchBySubjectId
```

### 25.4 Topic

At minimum:

```text
findById
findAllByChapterId
findAllByChapterIdAndStatus
findByChapterIdAndCode
existsByChapterIdAndCode
searchByChapterId
```

### 25.5 SubTopic

At minimum:

```text
findById
findAllByTopicId
findAllByTopicIdAndStatus
findByTopicIdAndCode
existsByTopicIdAndCode
searchByTopicId
```

### 25.6 Language

At minimum:

```text
findById
findAll
findAllByStatus
findByCode
existsByCode
searchByCodeOrName
```

### 25.6 Tag

At minimum:

```text
findById
findAll
findAllByStatus
findByCode
existsByCode
searchByCodeOrName
```

---

## 26. Service layer

Create:

```text
ExamService
SubjectService
ChapterService
TopicService
SubTopicService
LanguageService
TagService
```

An optional `AcademicHierarchyService` may exist only if the implementation needs a dedicated read model or service for tree retrieval.

It is not created merely as a facade that calls other services without business value.

---

## 27. Service responsibilities

### 27.1 ExamService

Owns createExam, getExam, listExams, updateExam, archiveExam, and activate and deactivate where exposed by the approved contract.

It enforces unique code, valid name, valid lifecycle transition, and audit identity.

### 27.2 SubjectService

Owns createSubject, getSubject, listByExam, updateSubject, and archiveSubject.

Validation: Exam exists, Exam is eligible, Code unique within Exam, Name valid.

### 27.3 ChapterService

Owns createChapter, getChapter, listBySubject, updateChapter, and archiveChapter.

Validation: Subject exists, Subject is eligible, Code unique within Subject.

### 27.4 TopicService

Owns createTopic, getTopic, listByChapter, updateTopic, and archiveTopic.

Validation: Chapter exists, Chapter is eligible, Code unique within Chapter.

### 27.5 SubTopicService

Owns createSubTopic, getSubTopic, listByTopic, updateSubTopic, and archiveSubTopic.

Validation: Topic exists, Topic is eligible, Code unique within Topic.

### 27.6 LanguageService

Owns createLanguage, getLanguage, listLanguages, updateLanguage, and archiveLanguage.

It does not know about questions, assessments, or attempts. Those modules consume Language through IDs and contracts.

### 27.7 TagService

Owns createTag, getTag, listTags, updateTag, and archiveTag.

It does not own QuestionTag because QuestionTag belongs to Content.

---

## 28. Transaction boundaries

Create, update, and archive operations for a single Academic entity normally execute inside one database transaction.

Example.

```text
Create subject
    |
    v
Validate parent
    |
    v
Persist Subject
    |
    v
Audit
    |
    v
Commit
```

Do not open a transaction around unrelated module operations.

---

## 29. Audit requirements

Every mutation captures the authenticated actor through the established audit mechanism.

At minimum audit:

```text
CREATE
UPDATE
ARCHIVE or DEACTIVATE
```

Audit record fields come from the frozen `audit_log` model:

```text
user_id
entity_name
entity_id
action
remarks
created_on
```

Do not store passwords or secrets in audit remarks.

Audit insertion participates in the business transaction. Audit failure rolls back the business mutation.

---

## 30. API contract overview

The frozen API contract defines these Academic resource families:

```text
exams
subjects
chapters
topics
sub-topics
languages
tags
```

Base path: `/api/v1`.

All APIs follow the platform standard: REST, JSON, HTTPS, JWT, UUID, DTOs, pagination, filtering, sorting, standard response envelope.

---

## 31. Exam API

```http
GET    /api/v1/exams
GET    /api/v1/exams/{id}
POST   /api/v1/exams
PUT    /api/v1/exams/{id}
DELETE /api/v1/exams/{id}
```

Exam create request.

```json
{
  "code": "SSC-CGL",
  "name": "SSC CGL",
  "description": "Combined Graduate Level"
}
```

Implementation requirements: code required, name required, description optional, unknown fields rejected where global API policy requires it.

---

## 32. Subject API

```http
GET    /api/v1/subjects
GET    /api/v1/subjects/{id}
POST   /api/v1/subjects
PUT    /api/v1/subjects/{id}
DELETE /api/v1/subjects/{id}
```

Required filter: examId.

---

## 33. Chapter API

```http
GET    /api/v1/chapters
GET    /api/v1/chapters/{id}
POST   /api/v1/chapters
PUT    /api/v1/chapters/{id}
DELETE /api/v1/chapters/{id}
```

Required filter: subjectId.

---

## 34. Topic API

```http
GET    /api/v1/topics
GET    /api/v1/topics/{id}
POST   /api/v1/topics
PUT    /api/v1/topics/{id}
DELETE /api/v1/topics/{id}
```

Required filter: chapterId.

---

## 35. SubTopic API

```http
GET    /api/v1/sub-topics
GET    /api/v1/sub-topics/{id}
POST   /api/v1/sub-topics
PUT    /api/v1/sub-topics/{id}
DELETE /api/v1/sub-topics/{id}
```

Required filter: topicId.

---

## 36. Language API

```http
GET    /api/v1/languages
GET    /api/v1/languages/{id}
POST   /api/v1/languages
PUT    /api/v1/languages/{id}
DELETE /api/v1/languages/{id}
```

The API does not turn languages into an enum.

---

## 37. Tag API

```http
GET    /api/v1/tags
GET    /api/v1/tags/{id}
POST   /api/v1/tags
PUT    /api/v1/tags/{id}
DELETE /api/v1/tags/{id}
```

---

## 38. Standard success response

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": {}
}
```

JPA entities are not returned directly.

---

## 39. Standard error response

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": []
}
```

Academic-specific errors map to the platform error categories.

---

## 40. Academic error categories

Typical examples.

```text
VALIDATION_ERROR
NOT_FOUND
CONFLICT
BUSINESS_ERROR
INVALID_STATE
AUTHENTICATION_ERROR
AUTHORIZATION_ERROR
SYSTEM_ERROR
```

Examples.

```text
Exam not found
Subject parent not found
Duplicate subject code within exam
Cannot create topic under archived chapter
Cannot archive exam with active descendants
```

---

## 41. HTTP status mapping

Use the platform standard.

```text
200 OK
201 Created
204 No Content
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity
500 Internal Server Error
```

Recommended Academic mapping.

```text
GET success            -> 200
POST success           -> 201
PUT success            -> 200
DELETE archive success -> 204
Missing entity         -> 404
Unique code conflict   -> 409
Invalid parent state   -> 409
Field validation       -> 400 or 422 according to global policy
Unauthorized           -> 401
Forbidden              -> 403
```

A second error-status convention is not created inside Academic.

---

## 42. Pagination contract

List APIs reuse the platform's common pagination model.

A separate Academic pagination DTO is not created.

At minimum, the implementation supports the project's standard page, size, and sort.

Examples.

```text
GET /api/v1/exams?page=0&size=25
GET /api/v1/subjects?examId=<uuid>&page=0&size=25
GET /api/v1/chapters?subjectId=<uuid>&page=0&size=25
```

---

## 43. Filtering

Allowed filters remain business-relevant.

Examples.

```text
status
search
examId
subjectId
chapterId
topicId
```

Do not expose arbitrary database columns as generic query parameters.

---

## 44. Sorting

Sorting is allow-listed.

Do not translate arbitrary client strings into JPA property paths without validation.

For example, `sort=name,asc` and `sort=createdOn,desc` may be allowed only if those fields are explicitly supported by the resource.

`sort=<arbitrary SQL fragment>` is not allowed.

---

## 45. DTOs

### 45.1 Exam DTOs

```text
ExamCreateRequest
ExamUpdateRequest
ExamResponse
ExamSummaryResponse
ExamListItem
```

The default write DTO does not allow clients to set `createdBy`, `createdOn`, `updatedBy`, or `updatedOn`. These are backend-controlled.

### 45.2 Subject DTOs

```text
SubjectCreateRequest
SubjectUpdateRequest
SubjectResponse
SubjectListItem
```

The parent is represented by `examId`, not a nested client-controlled Exam entity.

### 45.3 Chapter DTOs

```text
ChapterCreateRequest
ChapterUpdateRequest
ChapterResponse
ChapterListItem
```

Parent reference: subjectId.

### 45.4 Topic DTOs

```text
TopicCreateRequest
TopicUpdateRequest
TopicResponse
TopicListItem
```

Parent reference: chapterId.

### 45.5 SubTopic DTOs

```text
SubTopicCreateRequest
SubTopicUpdateRequest
SubTopicResponse
SubTopicListItem
```

Parent reference: topicId.

### 45.6 Language DTOs

```text
LanguageCreateRequest
LanguageUpdateRequest
LanguageResponse
LanguageListItem
```

### 45.7 Tag DTOs

```text
TagCreateRequest
TagUpdateRequest
TagResponse
TagListItem
```

---

## 46. Mapper layer

Use explicit mappers.

```text
ExamMapper
SubjectMapper
ChapterMapper
TopicMapper
SubTopicMapper
LanguageMapper
TagMapper
```

Mapping responsibilities: entity to response DTO, request DTO to creation or update command or entity fields.

Audit implementation details are not exposed unless the API contract explicitly requires them.

---

## 47. Validation layer

Validation has three levels.

```text
API validation
Business validation
Database validation
```

API validation checks required fields, length, format, and nullability.

Business validation checks parent existence, parent eligibility, duplicate business code, and valid lifecycle transition.

Database validation checks primary key, foreign key, unique, and not-null.

All three are required.

---

## 48. Duplicate handling

Two requests may race.

```text
Request A -> code X
Request B -> code X
```

The application provides a friendly pre-check where useful. The PostgreSQL unique constraint remains the final authority.

If the database reports a uniqueness conflict, it is translated to HTTP 409. Do not depend only on application-side `existsBy` checks.

---

## 49. Concurrency rules

Academic master data has lower write concurrency than delivery.

Optimistic concurrency is considered if the common foundation already supports version fields.

The frozen schema does not define a dedicated JPA `@Version` column for Academic records. Do not add a new database version column during P2 unless formally approved.

---

## 50. Idempotency

Academic create APIs are not required to add an idempotency-key table. Do not invent such a table.

For master-data workflows, a duplicate business code returning 409 conflict is acceptable.

Any future general-purpose idempotency mechanism belongs to the shared foundation and decision record process.

---

## 51. Hierarchy read APIs

The frozen API contract defines child-filtered list endpoints.

The implementation may additionally support a UI-friendly hierarchy experience using the existing APIs.

Recommended UI sequence.

```text
Load exams
   |
   v
Select or expand exam
   |
   v
Load subjects by examId
   |
   v
Select or expand subject
   |
   v
Load chapters by subjectId
   |
   v
Select or expand chapter
   |
   v
Load topics by chapterId
   |
   v
Select or expand topic
   |
   v
Load sub-topics by topicId
```

A new hierarchy tree endpoint is not added unless the existing contract proves insufficient. This keeps P2 aligned with the frozen API surface.

---

## 52. N+1 prevention

Do not implement the hierarchy page by recursively fetching JPA collections from each entity.

Avoid:

```text
Exam -> subjects -> chapters -> topics -> subtopics
```

as one uncontrolled object graph.

The preferred model is parent ID followed by explicit child query. This makes the cost visible and predictable.

---

## 53. Backend package structure

```text
academic/
  controller/
    ExamController.java
    SubjectController.java
    ChapterController.java
    TopicController.java
    SubTopicController.java
    LanguageController.java
    TagController.java
  service/
    ExamService.java
    SubjectService.java
    ChapterService.java
    TopicService.java
    SubTopicService.java
    LanguageService.java
    TagService.java
  repository/
    ExamRepository.java
    SubjectRepository.java
    ChapterRepository.java
    TopicRepository.java
    SubTopicRepository.java
    LanguageRepository.java
    TagRepository.java
  entity/
    Exam.java
    Subject.java
    Chapter.java
    Topic.java
    SubTopic.java
    Language.java
    Tag.java
  dto/
    exam/
    subject/
    chapter/
    topic/
    subtopic/
    language/
    tag/
  mapper/
  validation/
  exception/
  util/
```

Only create packages that contain real implementation code.

---

## 54. Controller rules

Controllers:

```text
Authenticate
Authorize
Validate request
Delegate
Return response
```

Controllers do not perform business workflows, write repositories directly, construct JPA graphs, or apply hierarchy policies.

---

## 55. Authorization

Academic APIs are protected by Identity RBAC.

Permission checks occur on the backend.

Typical conceptual permissions include:

```text
EXAM_VIEW
EXAM_CREATE
EXAM_UPDATE
EXAM_ARCHIVE

SUBJECT_VIEW
SUBJECT_CREATE
SUBJECT_UPDATE
SUBJECT_ARCHIVE

CHAPTER_VIEW
CHAPTER_CREATE
CHAPTER_UPDATE
CHAPTER_ARCHIVE

TOPIC_VIEW
TOPIC_CREATE
TOPIC_UPDATE
TOPIC_ARCHIVE

SUB_TOPIC_VIEW
SUB_TOPIC_CREATE
SUB_TOPIC_UPDATE
SUB_TOPIC_ARCHIVE

LANGUAGE_VIEW
LANGUAGE_CREATE
LANGUAGE_UPDATE
LANGUAGE_ARCHIVE

TAG_VIEW
TAG_CREATE
TAG_UPDATE
TAG_ARCHIVE
```

These permission names are implementation candidates. The frozen permission entity is configuration-driven. The final permission catalog aligns with the Identity role and permission configuration.

---

## 56. Student access

Students do not need unrestricted write access to Academic master APIs.

Student-facing APIs normally consume only the Academic identifiers and data needed by question delivery, assessment presentation, filters, and analytics.

Students are not allowed to create Exam, Subject, Chapter, Topic, SubTopic, Language, or Tag.

---

## 57. Staff access

Staff access is permission-based.

A generic STAFF role does not automatically receive all Academic write permissions. Permission assignment is managed by Identity RBAC.

---

## 58. Frontend scope

P2 frontend work is an operational master-data workspace, not a full administration portal.

Pilot-first screens:

```text
Academic overview
Exam management
Subject management
Chapter management
Topic management
SubTopic management
Language management
Tag management
```

---

## 59. Academic overview screen

Purpose: give staff a clear view of the hierarchy.

Recommended layout.

```text
Exams
  |
  v
Subjects
  |
  v
Chapters
  |
  v
Topics
  |
  v
SubTopics
```

Use expandable panels or tree behavior. Avoid rendering every descendant on initial page load.

---

## 60. Exam management UI

Required capabilities: list, search, create, edit, archive or deactivate, view details, and view child subjects.

Suggested columns: code, name, status, updated on, actions.

---

## 61. Subject management UI

Context should show Exam followed by Subjects.

Required: list, filter by exam, search, create, edit, archive.

Do not force staff to type an arbitrary Exam ID. Use a controlled parent selector populated by the backend.

---

## 62. Chapter management UI

Context.

```text
Exam
  |
  v
Subject
  |
  v
Chapters
```

Required: filter by subject, search, create, edit, archive.

---

## 63. Topic management UI

Context.

```text
Exam
  |
  v
Subject
  |
  v
Chapter
  |
  v
Topics
```

Required: filter by chapter, search, create, edit, archive.

---

## 64. SubTopic management UI

Context.

```text
Exam
  |
  v
Subject
  |
  v
Chapter
  |
  v
Topic
  |
  v
SubTopics
```

Required: filter by topic, search, create, edit, archive.

---

## 65. Language management UI

Required: list, search, create, edit, archive.

Suggested columns: code, name, status, updated on.

---

## 66. Tag management UI

Required: list, search, create, edit, archive.

Suggested columns: code, name, description, status, updated on.

---

## 67. Frontend state architecture

Server state: TanStack Query.

Form state: React Hook Form.

Validation: Zod.

Local presentation state: React state and hooks.

Do not put the entire Academic hierarchy into a global state store.

---

## 68. Query key standards

Use stable domain-oriented query keys.

Examples.

```text
["exams", filters]
["exam", examId]
["subjects", { examId, ...filters }]
["subject", subjectId]
["chapters", { subjectId, ...filters }]
["chapter", chapterId]
["topics", { chapterId, ...filters }]
["topic", topicId]
["subTopics", { topicId, ...filters }]
["languages", filters]
["tags", filters]
```

Stable hierarchical cache keys matter more than exact naming.

---

## 69. Cache invalidation

After create, update, or archive of a subject:

```text
["subjects", { examId }]
["subject", subjectId]
```

and any affected overview query.

Do not manually mutate many caches unless there is a clear performance reason. Use TanStack Query invalidation first.

---

## 70. Redis cache

The platform baseline permits Redis for master-like data such as exam masters and similar read-heavy master data.

Academic may use Redis selectively.

Recommended approach: cache only read-heavy, stable masters.

Potential candidates:

```text
languages
tags
exam summaries
```

Do not cache blindly. Do not cache student responses, attempts, payments, or sessions.

---

## 71. Redis failure behavior

Academic APIs continue to work if Redis is unavailable.

Required behavior.

```text
Redis available   -> cache may be used
Redis unavailable -> fall back to PostgreSQL
```

Redis is an optimization, not the source of truth.

---

## 72. Cache invalidation rules

When Academic data mutates:

```text
Database transaction succeeds
    |
    v
Invalidate or update applicable cache
```

Do not return success if the database transaction failed.

Cache failure does not roll back an already successful master-data mutation unless the project's shared cache consistency policy explicitly requires it.

---

## 73. OpenAPI

Document every Academic endpoint.

Each operation specifies summary, authentication, authorization, path parameters, query parameters, request body, validation constraints, success response, and error responses.

No undocumented controller route is merged.

---

## 74. Security rules

Academic is an authenticated operational API.

Requirements.

```text
JWT required
RBAC required
DTO input validation
Database constraint enforcement
Audit logging
No sensitive data exposure
```

The frontend permission check is only a UX aid. The backend remains authoritative.

---

## 75. Authorization failure handling

Examples.

```text
Missing or invalid JWT -> 401
Authenticated but no permission -> 403
```

404 is not used as a generic replacement for authorization failures unless a deliberate security policy in Identity defines resource hiding.

---

## 76. API input security

Reject or safely handle oversized input, unexpected fields, malformed UUID, invalid pagination, invalid sort field, invalid status, blank code, and blank name.

Raw SQL never receives client-controlled fragments. Use parameterized JPA queries or approved query APIs.

---

## 77. Unicode

Academic master data supports Unicode.

Examples of possible data.

```text
Mathematics
Marathi
Hindi
Gujarati
Bengali
```

Names are not restricted to ASCII. The database remains UTF-8.

---

## 78. Language and name rendering

Language records are data. The frontend renders the stored name. Do not translate language records by adding hard-coded frontend maps.

Localization of the administrative UI is a separate concern.

---

## 79. Audit UI

For P2 scope, an independent audit screen is optional.

At minimum, mutations produce audit records.

The final audit explorer remains part of Administration.

An `academic_audit` table is not created.

---

## 80. Logging

Structured logs include requestId, userId where permitted, resource type, resource ID, action, result, and latency.

Logs do not include JWT, password, secrets, or authorization headers.

---

## 81. Metrics

P2 metrics are minimal and operationally useful.

Recommended.

```text
academic_api_requests_total
academic_api_request_duration
academic_api_errors_total
academic_db_constraint_conflicts_total
academic_cache_hits
academic_cache_misses
```

Metric naming follows the P0 observability convention.

Do not build a separate Academic analytics database.

---

## 82. Health checks

Academic does not need a separate health endpoint. Use platform-level health.

Dependency health: application, PostgreSQL, Redis where configured.

The domain remains operational if optional Redis caching is unavailable.

---

## 83. Seed data strategy

P2 supports controlled master-data seeding.

Use Flyway for deterministic technical seed data where appropriate.

Do not hard-code a giant permanent academic catalog into Java application startup.

For pilot organization data, seed or admin import is explicit.

---

## 84. Seed data safety

Seed scripts are idempotent.

Prefer "insert if absent by business code" or migration-owned immutable seed changes.

Do not re-create UUIDs differently on every environment if the seed is intended to be referenced by stable downstream fixtures.

Where seed identifiers are not required by the application, data may be generated once during migration.

---

## 85. Academic master import

Bulk import is not a required P2 feature.

However, the data model remains compatible with later Content and import workflows.

A bulk-import table is not introduced in P2. A future import pipeline can use application services in batches.

---

## 86. API sorting and search performance

For pilot scale, PostgreSQL is authoritative.

The implementation validates real queries with EXPLAIN and EXPLAIN ANALYZE before adding indexes.

Speculative indexes that are not supported by actual query patterns are not added.

The frozen schema already specifies the required parent foreign-key indexes.

---

## 87. Index requirements

Must exist.

```text
uk_exam_code

uk_subject_exam_id_code
idx_subject_exam_id

uk_chapter_subject_id_code
idx_chapter_subject_id

uk_topic_chapter_id_code
idx_topic_chapter_id

uk_sub_topic_topic_id_code
idx_sub_topic_topic_id

uk_language_code
uk_tag_code
```

Exact physical index names follow the frozen naming standard.

---

## 88. Foreign key requirements

Required.

```text
subject.exam_id         -> exam.id
chapter.subject_id      -> subject.id
topic.chapter_id        -> chapter.id
sub_topic.topic_id      -> topic.id
```

The database rejects orphan children.

---

## 89. Delete strategy

Do not implement `DELETE FROM exam` as normal application behavior.

The HTTP DELETE endpoint represents archive or deactivation semantics as already defined by the API contract.

The platform retains historical taxonomy information.

---

## 90. Cross-module boundary

### 90.1 Content

Content consumes Academic records.

Expected future relationships.

```text
Question
  -> Exam
  -> Subject
  -> Chapter
  -> Topic
  -> SubTopic

QuestionVersion
  -> Language
```

This future relationship does not justify moving Question into Academic.

### 90.2 Assessment

Assessment consumes Exam, Subject, Language, and other Academic metadata for blueprint construction.

Assessment does not become the owner of these master records.

### 90.3 Analytics

Analytics derives metrics by taxonomy.

Examples.

```text
Accuracy by subject
Accuracy by chapter
Accuracy by topic
Time by topic
```

Analytics queries and consumes Academic identifiers. Academic itself does not store analytical aggregates.

---

## 91. No circular dependency

Academic does not depend on Content, Assessment, Delivery, Evaluation, Analytics, Commerce, or Communication for core CRUD.

It may expose application contracts consumed by those domains.

---

## 92. Aggregate root rules

Academic aggregate roots.

```text
Exam
Subject
Chapter
Topic
SubTopic
Language
Tag
```

Each aggregate has a narrow transactional boundary.

Do not load an entire academic tree into a single transaction merely to edit one leaf.

---

## 92.1 Example create flow

Exam.

```text
POST /api/v1/exams
        |
        v
JWT authentication
        |
        v
Permission check
        |
        v
DTO validation
        |
        v
ExamService.create
        |
        v
Duplicate-code check
        |
        v
Persist
        |
        v
Audit
        |
        v
Cache invalidation if applicable
        |
        v
201 Created
```

Subject.

```text
POST /api/v1/subjects
        |
        v
JWT authentication
        |
        v
Permission check
        |
        v
DTO validation
        |
        v
Load Exam
        |
        v
Validate Exam lifecycle
        |
        v
Check scoped code uniqueness
        |
        v
Persist Subject
        |
        v
Audit
        |
        v
201 Created
```

Exam archive.

```text
DELETE /api/v1/exams/{id}
        |
        v
Authenticate
        |
        v
Authorize
        |
        v
Load Exam
        |
        v
Check descendant safety
        |
        v
Transition status
        |
        v
Audit
        |
        v
Invalidate cache
        |
        v
204 No Content
```

If active descendants make archiving unsafe, return 409 CONFLICT with a business error.

---

## 93. Error example

Duplicate subject.

```json
{
  "success": false,
  "message": "Subject code already exists for this exam",
  "errors": [
    {
      "code": "CONFLICT",
      "field": "code"
    }
  ]
}
```

The exact global error-item structure follows P0 and P1 shared API error conventions.

---

## 94. Frontend route structure

Suggested routes.

```text
/academic
/academic/exams
/academic/exams/[id]
/academic/subjects
/academic/chapters
/academic/topics
/academic/sub-topics
/academic/languages
/academic/tags
```

Exact routing follows the existing Next.js application layout. A separate frontend application is not created for Academic.

---

## 95. Frontend components

Suggested reusable components.

```text
AcademicBreadcrumbs
AcademicHierarchy
AcademicTreeNode
AcademicStatusBadge
AcademicSearchBar
AcademicTable
AcademicPagination
AcademicCreateDialog
AcademicEditDialog
AcademicArchiveDialog
ParentSelector
CodeField
DescriptionField
```

Do not create separate copies of the same table or dialog implementation for every master if one generic component can safely support them.

---

## 96. Form architecture

Use React Hook Form plus Zod.

Each resource has a dedicated schema.

Examples.

```text
examCreateSchema
examUpdateSchema
subjectCreateSchema
subjectUpdateSchema
```

Frontend validation improves UX. Backend validation remains authoritative.

---

## 97. Mutation UX

For create, update, and archive:

```text
Submit
  |
  v
Disable duplicate submission
  |
  v
API call
  |
  v
Show success or failure
  |
  v
Invalidate affected queries
```

UI mutation completion is not proof of backend persistence.

---

## 98. Confirmation UX

Archive or deactivate requires explicit user confirmation when performed from a destructive UI action.

The UI displays parent and child context when relevant.

Example: archive exam "SSC-CGL". This makes the exam unavailable for new child creation.

Do not claim that children are deleted unless the backend actually performs such a transition.

---

## 99. Empty states

Every list screen handles no records, no search results, parent has no children, archived only, API error, and loading.

Examples.

```text
No subjects configured for this exam.
No topics found for this chapter.
```

---

## 100. Loading states

Use skeleton, spinner, and disabled submit where appropriate.

Do not block the entire Academic workspace for a single child-list request.

---

## 101. Error UX

Map backend categories to useful messages.

Examples.

```text
NOT_FOUND           -> record no longer exists
CONFLICT            -> code already exists or hierarchy prevents operation
AUTHORIZATION_ERROR -> user is not allowed to perform this action
VALIDATION_ERROR    -> fix highlighted fields
```

Never hide a server error behind a misleading success state.

---

## 102. Accessibility

Academic operational UI supports keyboard navigation, visible focus, labels, semantic buttons, accessible dialogs, table headers, and screen-reader-friendly status text.

The platform design system remains the shared UI foundation.

---

## 103. Responsive behavior

P2 is web-first.

Support desktop and tablet.

Mobile remains usable but does not require a specialized mobile application. Do not create a second rendering architecture for Academic.

---

## 104. Backend unit tests

At minimum test ExamService, SubjectService, ChapterService, TopicService, SubTopicService, LanguageService, and TagService.

Cases: create success, duplicate code, missing parent, inactive parent, archived parent, update success, archive success, invalid transition.

---

## 105. Repository tests

Test unique lookup, parent filtering, status filtering, search, pagination, and sorting.

Use integration tests against PostgreSQL for query correctness where repository behavior matters.

Do not rely exclusively on mocks for database constraints.

---

## 106. Database integration tests

Verify primary key constraints, foreign key constraints, unique exam code, unique subject code within exam, unique chapter code within subject, unique topic code within chapter, unique sub-topic code within topic, unique language code, and unique tag code.

Also verify that orphan insertion fails.

---

## 107. API tests

For each endpoint verify 200, 201, 204, 400, 401, 403, 404, and 409 where applicable.

Test malformed UUID, missing required field, too-long code, duplicate business code, invalid parent, and unauthorized access.

---

## 108. RBAC tests

Verify view permission allows GET, create permission allows POST, update permission allows PUT, archive permission allows DELETE, and missing permission returns 403.

The exact permissions are taken from the configured Identity permission catalog.

---

## 109. Audit tests

For successful mutations, verify audit record created, correct acting user, correct entity, correct entity ID, and correct action.

For failed mutations, no false-success audit event is created.

The exact audit transaction semantics follow P0 and P1.

---

## 110. Frontend unit tests

Test form validation, parent selector behavior, archive confirmation, table rendering, empty states, server error mapping, permission-based action visibility, and query invalidation.

---

## 111. Frontend end-to-end tests

Minimum happy path.

```text
Login as staff or admin
    |
    v
Open Academic
    |
    v
Create Exam
    |
    v
Create Subject
    |
    v
Create Chapter
    |
    v
Create Topic
    |
    v
Create SubTopic
    |
    v
Create Language
    |
    v
Create Tag
    |
    v
Edit records
    |
    v
Archive a safe record
```

---

## 112. End-to-end negative paths

Include duplicate exam code, duplicate subject code within same exam, same subject code under different exams, missing parent, archived parent, forbidden user, invalid input, refresh page after mutation, and back or forward navigation.

---

## 113. Performance test scope

Academic CRUD is not the main concurrent-user hotspot.

The primary concurrent-user load risk remains exam start, autosave, submission, and recovery.

P2 ensures Academic APIs remain healthy under realistic operational load.

Performance acceptance includes paged list, search, child lookup, and hierarchy navigation.

---

## 114. Query performance goal

For normal master-data usage, queries are lightweight and index-supported.

Measure list exams, list subjects by exam, list chapters by subject, list topics by chapter, list subtopics by topic, list languages, and list tags.

Use real staging data before declaring performance complete.

---

## 115. Observability acceptance

P2 is complete only when engineers can answer which Academic endpoint failed, who invoked it, which resource was affected, what was the error, and how long it took, without inspecting application source manually.

---

## 116. Operational failure behavior

If PostgreSQL is unavailable, Academic read and write operations fail safely, the global readiness reflects the dependency issue, and no false success response is returned.

If Redis is unavailable, Academic requests continue via PostgreSQL if Redis is configured only as a cache.

---

## 117. No hidden background jobs

P2 does not require a mandatory academic worker.

Do not introduce asynchronous workers for simple create, update, and archive operations.

Use background jobs only where later requirements actually demand them.

---

## 118. Implementation boundaries

The implementation may modify entities, repositories, services, DTOs, mappers, validation, controllers, frontend screens, tests, and OpenAPI annotations.

The implementation does not change the frozen schema, add academic tables, change API paths, invent a new architecture, add microservices, introduce GraphQL, replace PostgreSQL, replace Redis, or change the frontend stack without explicit architecture approval.

---

## 119. Task strategy

P2 is implemented as small reviewable tasks.

Recommended branch.

```text
feature/p2-academic
```

Smaller feature branches are also acceptable.

```text
feature/p2-academic-exam
feature/p2-academic-taxonomy
feature/p2-academic-reference-masters
```

No agent pushes directly to main.

---

## 120. P2 task list

### P2-01 Module skeleton

Create the academic package with controller, service, repository, entity, dto, mapper, validation, and exception subpackages.

Acceptance: application starts, no unused empty architecture, no cross-domain repository access.

### P2-02 Schema verification

Verify the active migrations contain the exact tables exam, subject, chapter, topic, sub_topic, language, and tag with the frozen schema.

If these tables do not yet exist in the implementation repository, create the exact Flyway migration.

Do not alter columns or add fields beyond the frozen schema.

Acceptance: migration applies cleanly, rollback strategy documented where the project's Flyway policy requires it, constraints present, indexes present.

### P2-03 Exam entity, repository, service

Implement complete Exam CRUD and archive flow.

Acceptance: create, read, list, update, archive, validation, unique code, audit, tests.

### P2-04 Subject entity, repository, service

Implement exam parent validation, scoped uniqueness, CRUD and archive, pagination, tests.

### P2-05 Chapter entity, repository, service

Implement subject parent validation, scoped uniqueness, CRUD and archive, tests.

### P2-06 Topic entity, repository, service

Implement chapter parent validation, scoped uniqueness, CRUD and archive, tests.

### P2-07 SubTopic entity, repository, service

Implement topic parent validation, scoped uniqueness, CRUD and archive, tests.

### P2-08 Language master

Implement CRUD and archive, unique code, search and list, tests.

### P2-09 Tag master

Implement CRUD and archive, unique code, search and list, tests.

### P2-10 REST controllers

Implement all frozen endpoints.

Acceptance: OpenAPI generated, standard response, standard error, pagination, filtering, authorization.

### P2-11 Authorization

Connect endpoints to Identity RBAC.

Acceptance: view, create, update, archive permissions enforced, student cannot mutate master data.

### P2-12 Audit integration

Verify mutations emit audit records.

Acceptance: create audited, update audited, archive audited.

### P2-13 Frontend shell

Implement routes: academic, exams, subjects, chapters, topics, sub-topics, languages, tags.

### P2-14 Hierarchical navigation UI

Implement exam to subject to chapter to topic to sub-topic with lazy child loading.

Do not recursively preload the entire tree.

### P2-15 Forms and mutation UI

Implement create, edit, archive, validation, server error handling, and permission-aware actions using React Hook Form, Zod, TanStack Query, and shadcn/ui.

### P2-16 Integration and end-to-end suite

Implement complete Academic workflow tests.

### P2-17 Performance and observability verification

Measure list, search, child lookup, and mutation. Verify logs and metrics.

### P2-18 Code review and exit gate

Run unit tests, integration tests, API tests, end-to-end tests, security tests, migration verification, OpenAPI verification, lint, format, and build. Then perform human review.

---

## 121. Recommended task order

```text
P2-01
   |
   v
P2-02
   |
   v
P2-03
   |
   v
P2-04
   |
   v
P2-05
   |
   v
P2-06
   |
   v
P2-07
   |
   v
P2-08
   |
   v
P2-09
   |
   v
P2-10
   |
   v
P2-11
   |
   v
P2-12
   |
   v
P2-13
   |
   v
P2-14
   |
   v
P2-15
   |
   v
P2-16
   |
   v
P2-17
   |
   v
P2-18
```

Some tasks may run in parallel after database contracts and shared DTO, error, and security utilities are ready.

---

## 122. Recommended implementation prompt

```text
You are implementing P2 Academic Structure for the Smart Competitive
Examination Platform.

Read the full specification package before modifying the repository.

Academic owns exactly:

Exam
Subject
Chapter
Topic
SubTopic
Language
Tag

Hierarchy:

Exam -> Subject -> Chapter -> Topic -> SubTopic

Language and Tag are reusable masters.

NON-NEGOTIABLE RULES:

1.  Do not invent database tables.
2.  Do not add organization_id to Academic tables.
3.  Do not convert Academic masters into Java enums.
4.  Do not change API paths defined in the frozen API contract.
5.  Do not expose JPA entities directly through REST.
6.  Do not allow frontend authorization to become the security boundary.
7.  Do not bypass RBAC.
8.  Do not physically delete Academic master data as a normal application operation.
9.  Use Flyway for all schema changes.
10. Use UUID primary keys.
11. Preserve all frozen columns, constraints and indexes.
12. Use DTO -> Service -> Repository architecture.
13. Keep business rules in services.
14. Keep persistence concerns in repositories.
15. Use explicit parent validation.
16. Do not eagerly load the entire hierarchy.
17. Do not introduce a second global state solution in the frontend.
18. Use TanStack Query for server state.
19. Use React Hook Form and Zod for forms.
20. Add tests for every mutation and parent-child rule.
21. Add OpenAPI documentation.
22. Produce small reviewable commits.
23. Never modify main directly.
24. If a requirement appears to conflict with a frozen artifact, stop
    and report the conflict instead of silently changing architecture.
25. Do not add speculative abstraction unless it removes real duplication.

IMPLEMENT:

Backend: entities, repositories, services, DTOs, mappers,
validation, controllers, authorization, audit integration, tests,
OpenAPI.

Frontend: Academic navigation, Exam management, Subject management,
Chapter management, Topic management, SubTopic management, Language
management, Tag management, search, filter, pagination, create, edit,
archive dialogs, permission-aware actions, loading, empty and error
states, TanStack Query invalidation.

DATABASE: verify exact Academic schema, verify FK constraints, verify
unique constraints, verify required indexes, create Flyway migration
only if the repository does not already contain the frozen schema.

QUALITY: no N+1 hierarchy loading, no SQL injection, no sensitive data
logging, no direct repository calls from controllers, no business rules
in React, backend remains authoritative, Redis failure must not break
core Academic persistence if Redis is only used as cache.

Before finishing:
1.  Run all tests.
2.  Run formatting and linting.
3.  Run build.
4.  Verify Flyway migration.
5.  Verify OpenAPI.
6.  Verify RBAC.
7.  Verify audit events.
8.  Verify duplicate-code conflicts.
9.  Verify parent-state rules.
10. Provide a concise implementation report with files changed,
    migration status, APIs implemented, tests added, known limitations,
    and any frozen-contract conflicts.
```

---

## 123. Definition of done

A task is not complete because code compiles.

It is complete when implementation exists, tests exist, security exists, OpenAPI exists, migration is verified, audit is verified, frontend integration is verified where applicable, and CI passes.

---

## 124. Backend acceptance checklist

```text
[ ] Exam CRUD implemented
[ ] Subject CRUD implemented
[ ] Chapter CRUD implemented
[ ] Topic CRUD implemented
[ ] SubTopic CRUD implemented
[ ] Language CRUD implemented
[ ] Tag CRUD implemented
[ ] Parent validation implemented
[ ] Parent lifecycle validation implemented
[ ] Scoped unique code validation implemented
[ ] Database constraints verified
[ ] Database indexes verified
[ ] Standard response envelope used
[ ] Standard error envelope used
[ ] Pagination implemented
[ ] Filtering implemented
[ ] Sorting implemented
[ ] OpenAPI complete
[ ] JWT required
[ ] RBAC enforced
[ ] Audit events recorded
[ ] No sensitive data logged
[ ] Unit tests passing
[ ] Integration tests passing
[ ] API tests passing
```

---

## 125. Frontend acceptance checklist

```text
[ ] Academic workspace loads
[ ] Exams page works
[ ] Subjects page works
[ ] Chapters page works
[ ] Topics page works
[ ] SubTopics page works
[ ] Languages page works
[ ] Tags page works
[ ] Parent filters work
[ ] Search works
[ ] Pagination works
[ ] Create works
[ ] Edit works
[ ] Archive confirmation works
[ ] Server validation displayed
[ ] Permission-based action visibility works
[ ] Loading states work
[ ] Empty states work
[ ] Error states work
[ ] Query invalidation works
[ ] Keyboard navigation works
[ ] Responsive layout works
[ ] End-to-end tests pass
```

---

## 126. Database acceptance checklist

```text
[ ] exam exists
[ ] subject exists
[ ] chapter exists
[ ] topic exists
[ ] sub_topic exists
[ ] language exists
[ ] tag exists
[ ] exam.code unique
[ ] subject.(exam_id, code) unique
[ ] chapter.(subject_id, code) unique
[ ] topic.(chapter_id, code) unique
[ ] sub_topic.(topic_id, code) unique
[ ] language.code unique
[ ] tag.code unique
[ ] all parent foreign keys exist
[ ] indexes exist
[ ] audit columns exist
[ ] status lifecycle supported
[ ] Flyway owns schema
```

---

## 127. Security acceptance checklist

```text
[ ] Authenticated access
[ ] RBAC
[ ] Backend authorization
[ ] Student cannot mutate masters
[ ] Malformed IDs rejected
[ ] Invalid input rejected
[ ] Arbitrary sort field blocked
[ ] SQL injection impossible through query layer
[ ] Secrets absent from logs
[ ] Audit actor captured
```

---

## 128. Integration acceptance checklist

The real P2 exit is not isolated Academic CRUD.

Verify future compatibility.

```text
Academic
   |
   v
Question authoring can select taxonomy
   |
   v
Assessment authoring can select exam and subject
   |
   v
Delivery can reference immutable assessment data
   |
   v
Analytics can aggregate by taxonomy
```

P2 does not implement these future domains prematurely. It only leaves clean IDs and stable APIs.

---

## 129. P2 exit gate

P2 is ready to hand off to Content when:

```text
Academic schema verified
        +
All Academic APIs implemented
        +
RBAC implemented
        +
Audit implemented
        +
Frontend master-data workspace usable
        +
Parent-child integrity tested
        +
Archive behavior tested
        +
OpenAPI complete
        +
CI green
        +
End-to-end green
```

The strongest acceptance test is:

```text
Create a complete academic hierarchy
        |
        v
Exam
        |
        v
Subject
        |
        v
Chapter
        |
        v
Topic
        |
        v
SubTopic
        |
        v
Create Language
        |
        v
Create Tag
        |
        v
Consume these IDs from the next Content phase
```

---

## 130. P2 exit evidence

The implementation retains test reports, migration result, OpenAPI output, CI run, end-to-end result, sample API responses, and sample audit records.

No screenshots are required if automated evidence is already available.

---

## 131. Code review questions

The reviewer asks:

```text
Does the implementation match the frozen schema exactly?

Can a child be created without a valid parent?

Can a child be created under an archived parent?

Can two child records use the same code under one parent?

Can the same code be reused under a different parent?

Does archive accidentally delete historical structure?

Are controllers free of business logic?

Are repositories free of business rules?

Is RBAC enforced on the backend?

Does frontend code contain exam-specific business logic?

Does hierarchy rendering cause N+1 queries?

Does Redis failure break persistence?

Are audit events correct?

Are APIs documented?

Are tests checking database constraints, not just service mocks?
```

---

## 132. Common failure modes

### Failure 1 - Hard-coded exam list

Bad.

```java
enum ExamType {
    SSC,
    UPSC,
    NEET
}
```

Do not do this for the Academic exam catalog. The source model treats Exams as master data.

### Failure 2 - Flat taxonomy

Bad: subject_name, chapter_name, topic_name, sub_topic_name all copied directly into Question.

The source model explicitly defines Exam, Subject, Chapter, Topic, SubTopic.

### Failure 3 - JSON hierarchy

Bad: subjects, chapters, topics, or subtopics stored inside an exam.configuration column.

The frozen model is relational.

### Failure 4 - Cascading physical deletion

Bad: delete exam, then delete subjects, then delete chapters, then delete topics.

Historical records are not casually deleted.

### Failure 5 - Eager-loading the entire tree

Bad: `exam.getSubjects().forEach(subject -> ...)` when each child eagerly loads all descendants.

### Failure 6 - Academic owns questions

Bad: AcademicQuestionService, or placing Question entities inside Academic.

Question is owned by Content.

### Failure 7 - Tag becomes taxonomy

Bad: using Tag as Chapter.

Tags are reusable metadata.

### Failure 8 - Language enum

Bad.

```java
enum Language {
    ENGLISH,
    HINDI
}
```

Language is a reusable database master.

### Failure 9 - Frontend-only permission control

Bad: `if (user.role === "ADMIN") show Edit` and assuming that hides the API.

The backend authorizes.

### Failure 10 - API contract drift

Bad: adding `GET /api/v1/academic/examHierarchy` without checking the frozen contract.

Prefer the existing endpoint surface.

---

## 133. Future extension boundaries

Future changes may introduce academic versioning, multi-organization academic ownership, advanced taxonomy relationships, subject aliases, localization metadata, taxonomy import and export, academic change history UI, and bulk master-data operations.

None belong in P2 unless explicitly approved.

---

## 134. No new tables rule

P2 starts and ends with these seven Academic tables.

```text
exam
subject
chapter
topic
sub_topic
language
tag
```

No additional table is created to represent ExamHierarchy, AcademicTree, SubjectMapping, LanguageMapping, TagMapping, AcademicAudit, or AcademicSearch.

The existing platform mechanisms already provide audit_log, relational foreign keys, REST APIs, and query filtering.

---

## 135. No new API surface rule

The implementation uses the frozen API routes for exams, subjects, chapters, topics, sub-topics, languages, and tags before creating additional composite endpoints.

A new endpoint requires proof that the frozen contract cannot support the requirement and goes through contract and version review.

---

## 136. Content handoff contract

When P2 exits, the Content phase can perform the following actions by stable UUID.

```text
Select Exam
Select Subject
Select Chapter
Select Topic
Select SubTopic
Select Language
Select Tag
```

Content does not require direct access to Academic JPA entities.

---

## 137. Assessment handoff contract

When P2 exits, Assessment can use Exam ID, Subject ID, Language ID, and other Academic master identifiers without changing the schema.

---

## 138. Analytics handoff contract

When P2 exits, Analytics can use Subject ID, Chapter ID, Topic ID, and SubTopic ID as grouping dimensions.

The Academic module does not compute those analytics.

---

## 139. Final engineering principle

The Academic implementation follows the same platform principle established by the architecture.

> The backend owns business truth; the database protects hierarchy integrity; the frontend delivers the master-data experience; and each downstream domain consumes Academic identifiers through explicit contracts.

P2 remains simple.

Its job is not to solve Content, Assessment, Delivery, or Analytics.

Its job is to provide a trustworthy academic foundation for them.

---

## 140. Final deliverables

The P2 implementation produces the following.

Backend: Academic entities, repositories, services, DTOs, mappers, validation, controllers, OpenAPI, authorization, audit integration, tests.

Database: exact Academic Flyway migration, constraints, indexes, seed strategy.

Frontend: Academic workspace, master-data screens, hierarchy navigation, forms, search, filter, pagination, archive flows, RBAC-aware UI.

Quality: unit tests, integration tests, API tests, end-to-end tests, CI evidence.

---

## 141. Document status

**Status:** IMPLEMENTATION-READY.

**Schema change required:** No, unless the repository is missing the already-frozen Academic migration.

**New tables required:** No.

**New core APIs required:** No.

**Primary risk:** Hierarchy lifecycle and downstream referential safety.

**Secondary risk:** Accidental eager-loading and N+1 in hierarchy screens.

**Primary acceptance artifact:** A complete executable Academic master-data workflow with backend authorization, database integrity, audit, and end-to-end coverage.

---

## 142. Handoff

After P2 is accepted, the next implementation document is the Content and Question Bank phase.

That phase consumes the Academic identifiers and implements Question, QuestionVersion, QuestionTemplate, QuestionOption, Asset, Explanation, Hint, PreviousExamHistory, and QuestionTag while preserving the content rendering contract and the pilot-first execution strategy.