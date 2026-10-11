# Smart Competitive Examination Platform
## P3 — Content & Question Bank Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P3 — Content / Question Bank
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, ExamEngine and CDUI Contract
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**API:** REST + JSON, `/api/v1`
**Content Contract:** MARKDOWN_LATEX_V1
**Math Renderer:** KaTeX server-side rendering
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Architects

---

## 1. Purpose

This document converts the Content domain into an implementation-ready engineering specification.

Content owns the reusable question repository.

Content entities:

```text
QuestionTemplate
Question
QuestionVersion
QuestionOption
Asset
Explanation
Hint
PreviousExamHistory
QuestionTag
```

The core lifecycle is:

```text
Question template
       |
       v
Question
       |
       v
Question version
       |
       v
Options, assets, explanation, hint
       |
       v
Previous exam history, tags
       |
       v
Review
       |
       v
Approval
       |
       v
Publication
```

A published QuestionVersion is immutable and remains historically reproducible.

---

## 2. Source-of-truth hierarchy

Read these documents before P3 implementation:

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
- This document

The earlier documents already freeze the Content schema, the content representation contract, the review workflow, and the delivery boundary. This document does not modify those decisions.

---

## 3. P3 objective

P3 makes the following possible:

```text
Create logical question
Create question version
Maintain version history
Create and edit options
Attach assets
Maintain explanation
Maintain hint
Map previous exam occurrences
Associate reusable tags
Associate taxonomy
Validate content
Preview content
Submit version for review
Approve or reject
Publish exact QuestionVersion
Search and filter question bank
Supply stable content data to Assessment
Supply safe content to Delivery
```

The module is the source of truth for question content. It is not the source of truth for student attempts or scoring.

---

## 4. Pilot-first scope

P3 is on the pilot-critical path:

```text
Identity
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
```

Commerce, Communication, full Administration, and advanced analytics remain deferred unless required to unblock the pilot.

---

## 5. P3 non-goals

P3 does not implement:

```text
Blueprint
Assessment
AssessmentSchedule
AssessmentSession
StudentAttempt
StudentResponse
Evaluation
Result
Scorecard
Commerce
Payment
Subscription
Notification
Advanced proctoring
AI-generated persistent question entities
Recommendation engine
Full report builder
Search-engine infrastructure
Microservices
```

The bulk-import foundation may be prepared, but a large asynchronous import platform is not part of the first pilot unless separately approved.

---

## 6. Content domain ownership

Content owns:

```text
QuestionTemplate
Question
QuestionVersion
QuestionOption
Asset
Explanation
Hint
PreviousExamHistory
QuestionTag
```

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

Content consumes Academic IDs and contracts. It does not directly own Academic persistence.

---

## 7. Domain relationships

The frozen conceptual model.

```text
QuestionTemplate
       |
       v
Question
       |
       v
QuestionVersion
       |
       +-- QuestionOption
       |
       +-- Asset
       |
       +-- Explanation
       |
       +-- Hint

Question
   |
   +-- PreviousExamHistory
   |
   +-- QuestionTag -> Tag
```

This structure keeps logical question identity separate from the exact published content.

---

## 8. Question versus QuestionVersion

Question is the logical identity.

QuestionVersion is the exact content at a version.

Example.

```text
Question Q100
  |
  +-- Version 1
  +-- Version 2
  +-- Version 3
```

A published assessment references an exact QuestionVersion. This is the historical reproducibility boundary.

---

## 9. Published immutability

Once a QuestionVersion is published, the following are immutable for that published version.

```text
Content
Title
Marks
Negative marks
Difficulty
Options
Assets
Explanation
Hint
Language
Taxonomy validation outcome
```

Correction flow.

```text
Published V3
    |
    v
Create V4
    |
    v
Edit V4
    |
    v
Review
    |
    v
Approve
    |
    v
Publish V4
```

Do not overwrite V3.

---

## 10. Frozen database schema

### 10.1 Table: question_template

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

The template catalog is configurable master data.

The `configuration` field supports flags such as the following.

```json
{ "requires_latex_parsing": true }
```

### 10.2 Table: question

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

Foreign keys.

```text
template_id  -> question_template(id)
exam_id      -> exam(id)
subject_id   -> subject(id)
chapter_id   -> chapter(id)
topic_id     -> topic(id)
sub_topic_id -> sub_topic(id)
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

Taxonomy lives on the logical Question. The actual versioned content lives on QuestionVersion.

### 10.3 Table: question_version

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

### 10.4 Table: question_option

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

Index.

```text
idx_question_option_question_version_id
```

Correctness is server-side evaluation data. It is not exposed in student delivery responses.

### 10.5 Table: asset

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

Index.

```text
idx_asset_question_version_id
```

### 10.6 Table: explanation

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

Index.

```text
idx_explanation_question_version_id
```

### 10.7 Table: hint

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

Index.

```text
idx_hint_question_version_id
```

### 10.8 Table: previous_exam_history

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

Multiple rows per Question.

### 10.9 Table: question_tag

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

---

## 11. Canonical content contract

The following fields use the frozen `MARKDOWN_LATEX_V1` representation.

```text
question_version.content
question_option.content
explanation.content
hint.content
```

Rendered HTML is derived output, not another source of truth.

---

## 12. Accepted markdown and LaTeX syntax

Accepted mathematical delimiters.

Inline.

```text
\( ... \)
```

Block.

```text
\[
...
\]
```

Examples.

```text
Solve for \(x\) when \(2x+5=15\).
```

And.

```text
\[
x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
\]
```

`$...$` and `$$...$$` are not supported math delimiters.

Literal currency such as the following remains ordinary text.

```text
If an item costs $500 and tax is $50...
```

---

## 13. Raw HTML rejection

Raw HTML is not part of the canonical content format.

Reject content containing examples such as the following.

```html
<p>Question</p>
<strong>x</strong>
<div>...</div>
<script>...</script>
<img src="...">
<span onclick="...">...</span>
```

Validation occurs before persistence.

Do not attempt to reproduce full Markdown grammar with a PostgreSQL check constraint.

---

## 14. Executable MDX rejection

Executable MDX is not part of the content contract.

The renderer uses a controlled Markdown AST and an explicit allowlist of supported syntax.

Never evaluate author-controlled JavaScript or arbitrary React components from question content.

---

## 15. Asset shortcode contract

Canonical asset syntax.

```text
[asset: <uuid>]
```

The UUID resolves to an asset belonging to the same question_version.

The relationship check.

```text
asset.id
  |
  v
asset.question_version_id
  ==
content question_version_id
```

The browser never receives a raw `asset.file_path` as an authorization primitive.

---

## 16. Asset rendering pipeline

Approved pipeline.

```text
Canonical content
      |
      v
Controlled Markdown AST
      |
      v
LaTeX delimiter handling
      |
      v
KaTeX server-side rendering
      |
      v
Asset shortcode resolution
      |
      v
Sanitization
      |
      v
Safe HTML
```

The pipeline uses KaTeX server-side rendering rather than client-side MathJax.

---

## 17. Question template configuration

`question_template.configuration` supports.

```json
{
  "requires_latex_parsing": true
}
```

Default.

```json
{
  "requires_latex_parsing": false
}
```

The API exposes this as `requiresLatexParsing`.

The flag is a rendering hint, not a security authority.

---

## 18. Question template purpose

QuestionTemplate defines the structure and behavior of a question type.

Examples.

```text
Single choice
Multiple choice
Integer
Decimal
Fill in the blank
Match the following
Comprehension
Numerical
True or false
```

The exact catalog remains configurable. These examples are not turned into a hard-coded exhaustive Java enum unless a separate requirement explicitly freezes such a catalog.

---

## 19. Template design rule

A template defines.

```text
Content expectations
Response model
Option behavior
Required fields
Validation rules
Rendering hints
Evaluation behavior contract
```

It does not embed arbitrary executable frontend code.

The backend owns validation. The student-facing renderer consumes a safe normalized representation.

---

## 20. Question lifecycle

```text
Draft
  |
  v
Review
  |
  v
Approved
  |
  v
Published
```

The persistent review state uses `question_version.review_status`. The `is_published` flag remains the authoritative publication flag.

---

## 21. Review state machine

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

Illegal transitions return `409 INVALID_STATE`.

Self-review is blocked: `submitted_for_review_by` and `reviewed_by` must differ when the same user holds both permissions.

---

## 22. Taxonomy mapping

The `question` table carries the taxonomy foreign keys.

```text
exam_id
subject_id
chapter_id
topic_id
sub_topic_id
```

The `question_version` table carries the language foreign key.

```text
language_id
```

Publication of a `question_version` requires.

```text
Parent question.exam_id NOT NULL
Parent question.subject_id NOT NULL
This version's language_id NOT NULL
```

Chapter, topic, and sub-topic remain optional because not every exam uses the full hierarchy.

---

## 23. Question creation mapping

The frozen create request.

```json
{
  "templateId": "<uuid>",
  "examId": "<uuid>",
  "subjectId": "<uuid>",
  "chapterId": "<uuid>",
  "topicId": "<uuid>",
  "title": "Example question",
  "content": "What is ...?",
  "marks": 2,
  "negativeMarks": 0,
  "difficulty": "MEDIUM",
  "languageId": "<uuid>"
}
```

The service treats Question creation as a business operation that creates Question plus an initial QuestionVersion atomically.

The service does not duplicate content columns into the question table.

---

## 24. Question version creation

```http
POST /api/v1/questions/{id}/versions
```

The service creates a new immutable-by-publication QuestionVersion with next version number, title, canonical content, marks, negative marks, difficulty, and language_id.

Options, assets, explanation, and hint are managed separately.

The new version remains unpublished until it passes its quality gates and the approved workflow.

---

## 25. Version numbering

Within one Question, version numbers are unique.

```text
version = 1
version = 2
version = 3
```

Do not rely only on `SELECT MAX(version) + 1` without transaction and concurrency protection.

A concurrent version-creation race is ultimately protected by `UNIQUE(question_id, version)`. The service may retry version allocation if the repository standard supports it.

---

## 26. Difficulty

The frozen field is `difficulty VARCHAR(20)`.

Values.

```text
EASY
MEDIUM
HARD
```

The value is controlled by application and master-data rules rather than arbitrary free text.

Do not silently create a new difficulty table in P3.

---

## 27. Marks and negative marks

Frozen types.

```text
marks NUMERIC(8,2)
negative_marks NUMERIC(8,2)
```

Validation enforces the product's established scoring policy.

P3 does not duplicate assessment-level scoring rules. QuestionVersion values are content metadata and defaults. AssessmentQuestion later carries assessment-specific scoring.

---

## 28. Option model

QuestionOption belongs to QuestionVersion.

Required fields.

```text
optionKey
content
displayOrder
isCorrect
status
```

An option's `displayOrder` is the presentation order.

`optionKey` is a stable question-local identifier such as A, B, C, D, or another configured key.

---

## 29. Option integrity

For a QuestionVersion.

```text
optionKey must be unique
displayOrder must be unique
```

Final integrity.

```text
UNIQUE(question_version_id, option_key)
UNIQUE(question_version_id, display_order)
```

Question-type validation decides how many options are required and how correctness is represented.

---

## 30. Option content

Option content uses the same `MARKDOWN_LATEX_V1` contract as QuestionVersion content.

Therefore options may contain text, inline mathematics, block mathematics, and approved asset shortcodes, subject to the same validation and sanitization rules.

---

## 31. Correct answer protection

`is_correct` is authoring and evaluation data.

Allowed in staff authoring APIs, review APIs, and evaluation internals.

Prohibited in active student delivery payloads.

Do not serialize the entity directly from JPA in the student API.

---

## 32. Single choice validation

A single choice template validates, according to template configuration, options present, sufficient option count, exactly one correct option, no duplicate option keys, and no duplicate display orders.

These rules are not global to every template.

---

## 33. Multiple choice validation

A multiple choice template validates options present, multiple correct options supported, at least one correct option, and no duplicate option keys.

The exact minimum and maximum option count is template configuration, not hard-coded into the core Question entity.

---

## 34. Integer and decimal validation

Templates such as Integer or Decimal may not require multiple-choice options.

Their answer and value representation is defined by the template contract rather than by adding columns to QuestionOption.

This is why the platform uses QuestionTemplate as a behavior abstraction.

---

## 35. Fill in the blank

Fill-in-the-blank behavior remains template-defined.

Do not invent a universal `expected_answer` column unless the active template contract explicitly requires it. The frozen schema does not show such a column.

---

## 36. Match the following

Match-the-following may require multiple paired relationships.

The frozen source does not define a dedicated match-pair table.

Therefore P3 does not invent one.

The template may remain unsupported for publication until a concrete persistent content and response contract is formally defined, or its configuration can legitimately express the required structure without violating relational rules.

This is an explicit implementation decision point, not a hidden schema addition.

---

## 37. Comprehension

Comprehension is mentioned as a possible template.

A comprehension template may conceptually contain a passage plus related questions.

However, the frozen domain model does not define a dedicated comprehension passage table or parent-child question relation.

P3 implements only the template behavior that fits the currently frozen content model. Any richer parent-passage structure requires explicit schema and domain review.

---

## 38. Question options API

```http
GET    /api/v1/questions/{questionId}/versions/{versionId}/options
POST   /api/v1/questions/{questionId}/versions/{versionId}/options
PUT    /api/v1/questions/{questionId}/versions/{versionId}/options/{optionId}
DELETE /api/v1/questions/{questionId}/versions/{versionId}/options/{optionId}
```

Published-version immutability applies.

Create request.

```json
{
  "optionKey": "A",
  "content": "Option text",
  "displayOrder": 1,
  "isCorrect": false
}
```

`isCorrect` is accepted only by authorized content and reviewer APIs. It is never returned by student delivery APIs.

---

## 39. Explanation API

```http
GET  /api/v1/question-versions/{versionId}/explanation
POST /api/v1/question-versions/{versionId}/explanation
PUT  /api/v1/question-versions/{versionId}/explanation
```

Content uses `MARKDOWN_LATEX_V1`.

Publication rules apply.

The domain model describes QuestionVersion to Explanation as one to one conceptually. The exact database constraint is verified against the active migration baseline.

---

## 40. Hint API

```http
GET  /api/v1/question-versions/{versionId}/hint
POST /api/v1/question-versions/{versionId}/hint
PUT  /api/v1/question-versions/{versionId}/hint
```

Hint is optional.

Content uses `MARKDOWN_LATEX_V1`.

Publication immutability applies once the parent QuestionVersion is published.

---

## 41. Asset upload API

```http
POST   /api/v1/question-versions/{versionId}/assets
GET    /api/v1/question-versions/{versionId}/assets
DELETE /api/v1/question-versions/{versionId}/assets/{assetId}
```

Upload is multipart.

The service.

```text
Authenticate
Authorize
Validate version state
Validate file type and size
Store file or object
Persist metadata
Audit
```

The exact object-storage provider is not hard-coded.

---

## 42. Asset security

Never expose `asset.file_path` as a client-controlled resource location.

Use a backend-controlled asset access mechanism.

For delivery.

```text
Student
  |
  v
Authorized QuestionVersion
  |
  v
Asset lookup
  |
  v
Ownership validation
  |
  v
Protected asset response or reference
```

Archived or missing assets never resolve to arbitrary filesystem paths.

---

## 43. Asset version integrity

An asset belongs to exactly one QuestionVersion through `asset.question_version_id`.

The content validator rejects cross-version references.

```text
QuestionVersion A
  references
Asset owned by QuestionVersion B
```

returns `ASSET_VERSION_MISMATCH`.

This protects historical reproducibility.

---

## 44. Previous exam history API

```http
GET    /api/v1/questions/{questionId}/previous-exams
POST   /api/v1/questions/{questionId}/previous-exams
PUT    /api/v1/questions/{questionId}/previous-exams/{historyId}
DELETE /api/v1/questions/{questionId}/previous-exams/{historyId}
```

A Question can map to multiple historical occurrences.

---

## 45. Previous exam history semantics

Example.

```text
Question Q100

SSC | 2019 | Shift 1
SSC | 2021 | Shift 2
SSC | 2025 | Shift 1
```

Do not compress historical occurrences into a single `previous_year` value.

---

## 46. Previous exam year validation

`year INTEGER`.

Validation ensures a plausible year according to product policy. The frozen source does not define the exact allowable range. A hard-coded range such as 1900 to 2100 is not silently assumed without a documented business reason.

---

## 47. Previous exam shift

`shift VARCHAR(50)`.

Shift is optional configurable metadata. It is not made an enum in the core domain unless the exam framework explicitly requires it. Different exam families may represent shift information differently.

---

## 48. Previous exam source

`source VARCHAR(100)` identifies where the historical mapping came from.

Examples.

```text
Official
Faculty
Imported
```

These are examples only. A closed enum is not introduced without a requirement.

---

## 49. Tag association

QuestionTag is a Content-owned junction using the Academic Tag master.

The relationship.

```text
Question
  |
  v
QuestionTag
  |
  v
Tag
```

Tag remains reusable metadata and is not part of the academic hierarchy.

---

## 50. Tag mutation rules

P3 exposes QuestionTag association through the Question authoring workflow.

It does not mutate Tag records through a QuestionTag request.

Correct separation.

```text
TagService                   -> manages Tag
QuestionService              -> manages QuestionTag association
```

A second tag catalog inside Content is not created.

---

## 51. Question search

Frozen Question list filters.

```text
status
templateId
difficulty
examId
subjectId
chapterId
topicId
tagId
languageId
isPublished
reviewStatus
search
```

The search API does not advertise a filter as operationally supported unless the backend has a verified authoritative mapping.

---

## 52. Search implementation

Search uses PostgreSQL-supported filtering and search.

Possible fields.

```text
Question id
Question version title
Canonical content
Template
Difficulty
Publication state
Review state
```

No Elasticsearch or OpenSearch is introduced.

Projections are used where practical to prevent loading large object graphs.

---

## 53. Content list projections

Question list pages do not fetch all assets, all explanations, all hints, all options, or full version histories unless the screen explicitly needs them.

Use DTO projections or controlled queries for list screens.

---

## 54. Question detail read model

Question detail provides.

```text
Logical question
Template summary
Available versions
Selected version details
Options
Asset summaries
Explanation summary
Hint summary
Taxonomy
Historical exam mappings
Tag associations
```

The API response is designed around the user workflow, not the JPA entity graph.

---

## 55. Version history

Question version history shows version, published state, review state, created date, published date where present, difficulty, marks, and negative marks.

Editing of a published version is not allowed. A new version is created for corrections.

---

## 56. Preview contract

The authoring UI needs preview support.

Preview uses the same canonical rendering rules as delivery.

```text
MARKDOWN_LATEX_V1
KaTeX server-side rendering
Asset shortcode resolution
Sanitized output
```

A permissive editor preview and a restrictive delivery renderer are not built separately.

---

## 57. Preview security

Preview is still a security boundary.

Author-controlled content never executes JavaScript, event handlers, raw HTML, or arbitrary React components.

Preview output remains within the same allowlisted renderer.

---

## 58. Content validation pipeline

Recommended order.

```text
Request DTO validation
        |
        v
Content size validation
        |
        v
Raw HTML detection
        |
        v
Markdown parse
        |
        v
LaTeX delimiter validation
        |
        v
Asset shortcode parsing
        |
        v
Asset ownership verification
        |
        v
Template-specific validation
        |
        v
Business quality validation
        |
        v
Persist
```

A later step does not assume an earlier step succeeded unless that result is explicitly represented.

---

## 59. Content validation error codes

Use structured error codes.

```text
INVALID_CONTENT_FORMAT
RAW_HTML_NOT_ALLOWED
INVALID_MATH_DELIMITER
UNSUPPORTED_MATH_DELIMITER
INVALID_ASSET_SHORTCODE
ASSET_NOT_FOUND
ASSET_VERSION_MISMATCH
CONTENT_TOO_LARGE
INVALID_MARKDOWN
```

These are resource and content errors, not frontend-only validation strings.

---

## 60. Content size

Content size is bounded to protect database, API, server-side rendering, KaTeX rendering, and browser memory.

The exact maximum size is not frozen. The implementation uses an explicitly configured technical limit rather than silently inventing a business limit.

The limit is load-tested with realistic mathematical content.

---

## 61. Math rendering safety

KaTeX is configured safely.

The renderer tokenizes approved delimiters, renders through KaTeX server-side rendering, avoids arbitrary code execution, and sanitizes generated output.

The implementation preserves a stable renderer version for historical reproducibility where required.

---

## 62. Renderer cache

Rendered output may be cached by immutable content identity.

```text
questionVersionId
+
contentFormat
+
rendererVersion
```

Because published QuestionVersions are immutable, this cache can be safely reused.

Rendered HTML is not treated as the transactional source of truth.

---

## 63. Redis boundary for content

Redis is optional cache infrastructure.

Reasonable cache candidates.

```text
Published QuestionVersion read representations
Question template configuration
Language and tag master lookups
```

Do not cache draft authoring state as authoritative data, payment data, student response state, or attempt state.

If Redis is unavailable, Content reads and writes continue from PostgreSQL where the cache is optional.

---

## 64. Question publication

```http
POST /api/v1/questions/{id}/versions/{versionId}/publish
```

Publication is a business action.

It is not equivalent to a generic PUT with an `isPublished` flag.

The service executes a controlled validation and transition operation.

---

## 65. Publication preconditions

At minimum:

```text
Question text exists
Valid question type exists
Required options exist
Correct answer exists where applicable
Marks exist
Negative marking is valid
Exam mapping exists
Subject mapping exists
Language is set
Content format is valid
Version is APPROVED
```

The publication gate remains blocked if any required condition cannot be satisfied from the frozen model.

---

## 66. Publication transaction

A publication transaction.

```text
Load QuestionVersion
Load relevant template configuration
Validate canonical content
Validate options
Validate assets
Validate explanation and hint relationships as applicable
Validate required metadata and mapping
Validate review state
Set publication fields
Audit
Commit
```

Publication does not occur before validation.

---

## 67. Post-publication immutability

After publication.

```text
PUT version content -> rejected
PUT option -> rejected
DELETE option -> rejected
Asset removal -> rejected where it would mutate the published version
Explanation update -> rejected
Hint update -> rejected
```

Use a business INVALID_STATE or CONFLICT response according to global API conventions.

---

## 68. Question master status

`question.status` uses the platform's standard lifecycle.

The semantic relationship between `question.status`, `question_version.is_published`, and `review_status` is documented by the Content service.

One flag is not used to infer another without an explicit rule.

---

## 69. Template update rules

QuestionTemplate is master and configuration data.

Changing template configuration can affect future authoring and rendering.

Because a Question references a template, template changes are reviewed for compatibility with existing questions.

A template configuration update does not retroactively reinterpret already-published QuestionVersions without an approved compatibility strategy.

---

## 70. Template immutability decision

P3 defines whether the active template implementation treats configuration changes as safe backward-compatible updates, as a reason to create a new template code, or as another controlled mechanism.

QuestionTemplate versioning tables are not added unless formally approved.

---

## 71. Backend module structure

```text
content/
  controller/
    QuestionTemplateController.java
    QuestionController.java
    QuestionVersionController.java
    QuestionOptionController.java
    AssetController.java
    ExplanationController.java
    HintController.java
    PreviousExamHistoryController.java
  service/
    QuestionTemplateService.java
    QuestionService.java
    QuestionVersionService.java
    QuestionOptionService.java
    AssetService.java
    ExplanationService.java
    HintService.java
    PreviousExamHistoryService.java
    QuestionPublicationService.java
  repository/
  entity/
  dto/
  mapper/
  validation/
  rendering/
  exception/
  util/
```

Only create classes and packages that contain real behavior.

---

## 72. Rendering package boundary

Content rendering concerns are kept in a dedicated Content rendering component rather than mixed into controllers.

Conceptual interfaces.

```java
interface ContentValidator
interface MarkdownLatexRenderer
interface AssetReferenceResolver
interface ContentSanitizer
```

These are implementation candidates, not mandatory public contracts.

The component does not contain ExamEngine logic.

---

## 73. Question service responsibilities

QuestionService owns logical-question operations.

```text
Create question
Get question
List questions
Update draft-level question metadata
Archive or deactivate where applicable
Load version summaries
Associate tags
```

It does not evaluate answers and does not start assessments.

---

## 74. QuestionVersion service responsibilities

QuestionVersionService owns.

```text
Create version
Get version
List versions
Validate draft
Prepare version data
```

Publication is isolated in a business action service so publication is not hidden inside a generic update method.

---

## 75. QuestionPublicationService

Responsibilities.

```text
Load exact version
Check not already published
Validate workflow state
Validate template
Validate content
Validate options
Validate required assets
Validate metadata and mapping
Perform publication transition
Audit
```

This service is a critical correctness boundary.

---

## 76. QuestionOption service

Responsibilities.

```text
List options
Create option
Update option
Remove or archive option where permitted
Validate option state against template
```

It checks that the option belongs to the requested QuestionVersion. It also checks publication state before mutation.

---

## 77. Asset service

Responsibilities.

```text
Upload asset
Validate file
Persist asset metadata
List assets
Archive or remove asset where allowed
Authorize asset access
```

Object storage and database metadata operations have a recoverable failure strategy. Orphan metadata or files are not left without an operational cleanup approach.

---

## 78. Explanation and hint services

Explanation and Hint mutations are version-aware.

Both enforce questionVersion exists, questionVersion belongs to the requested scope, questionVersion is mutable, and the content contract is valid.

Published version immutability applies.

---

## 79. PreviousExamHistory service

Responsibilities.

```text
List
Create
Update
Archive or remove according to approved delete semantics
Validate question
Validate referenced exam
```

The source schema uses `exam_id` and `question_id` relationships.

---

## 80. QuestionTag service boundary

QuestionTag association is a Content operation.

Tag itself remains Academic master data.

The service calls an explicit Academic contract such as `verifyTagExists(tagId)` rather than accessing TagRepository directly from Content.

---

## 81. Cross-module repository rule

Forbidden.

```text
QuestionService
   |
   v
SubjectRepository
```

Preferred.

```text
QuestionService
   |
   v
Academic application contract
   |
   v
Verify and get taxonomy data
```

This preserves modular-monolith boundaries and future extractability.

---

## 82. Academic contract usage

Potential Content needs.

```text
Verify exam
Verify subject
Verify chapter
Verify topic
Verify sub-topic
Verify language
Verify tag
```

Use only the contracts actually required by the resolved mapping design. A giant AcademicFacade that exposes every entity is not created merely because it is convenient.

---

## 83. DTO standards

Recommended DTO categories.

```text
QuestionTemplateCreateRequest
QuestionTemplateUpdateRequest
QuestionTemplateResponse

QuestionCreateRequest
QuestionUpdateRequest
QuestionResponse
QuestionListItem

QuestionVersionCreateRequest
QuestionVersionResponse
QuestionVersionListItem

QuestionOptionCreateRequest
QuestionOptionUpdateRequest
QuestionOptionResponse

AssetResponse
ExplanationRequest
ExplanationResponse
HintRequest
HintResponse

PreviousExamHistoryCreateRequest
PreviousExamHistoryUpdateRequest
PreviousExamHistoryResponse
```

Write DTOs do not expose server-controlled audit values.

---

## 84. Question create request mapping

```json
{
  "templateId": "<uuid>",
  "examId": "<uuid>",
  "subjectId": "<uuid>",
  "title": "Example question",
  "content": "What is \\(x\\)?",
  "marks": 2,
  "negativeMarks": 0,
  "difficulty": "MEDIUM",
  "languageId": "<uuid>"
}
```

The backend converts the request into Question plus initial QuestionVersion rather than duplicating content into the Question table.

---

## 85. Question update rules

```http
PUT /api/v1/questions/{id}
```

Only mutable draft-level data may be updated. If a change affects exact versioned content, a new QuestionVersion is created. Published history is not overwritten.

---

## 86. Version create request

Conceptually.

```json
{
  "title": "Updated question",
  "content": "...",
  "marks": 2,
  "negativeMarks": 0,
  "difficulty": "MEDIUM",
  "languageId": "<uuid>"
}
```

Options, assets, explanation, and hint are maintained through their dedicated endpoints.

---

## 87. Question template API

```http
GET  /api/v1/question-templates
GET  /api/v1/question-templates/{id}
POST /api/v1/question-templates
PUT  /api/v1/question-templates/{id}
```

The template API defines supported question formats.

---

## 88. Question API

```http
GET  /api/v1/questions
GET  /api/v1/questions/{id}
POST /api/v1/questions
PUT  /api/v1/questions/{id}

GET  /api/v1/questions/{id}/versions
POST /api/v1/questions/{id}/versions
GET  /api/v1/questions/{id}/versions/{versionId}
POST /api/v1/questions/{id}/versions/{versionId}/publish
POST /api/v1/questions/{id}/versions/{versionId}/submit-review
POST /api/v1/questions/{id}/versions/{versionId}/approve
POST /api/v1/questions/{id}/versions/{versionId}/reject
```

Alternative path names are not added without contract review.

---

## 89. Review submission API

```http
POST /api/v1/questions/{id}/versions/{versionId}/submit-review
```

The service rejects submission when version missing, version already published, content invalid, template invalid, or required fields missing.

---

## 90. Approve API

```http
POST /api/v1/questions/{id}/versions/{versionId}/approve
```

Request.

```json
{
  "remarks": "Verified content and options."
}
```

Only a user with `QUESTION_REVIEW` permission may approve. Self-review is blocked.

---

## 91. Reject API

```http
POST /api/v1/questions/{id}/versions/{versionId}/reject
```

Request.

```json
{
  "remarks": "Option D duplicate of C. Please fix."
}
```

Only a user with `QUESTION_REVIEW` permission may reject.

---

## 92. Content response safety

Authoring responses may include `isCorrect`, asset metadata appropriate to staff, publication details, and workflow details.

Student delivery responses do not include `isCorrect`, correct option, answer key, private asset path, internal scoring logic, or private audit fields.

The authoring DTO is not shared with the student delivery API.

---

## 93. API pagination

Question list APIs support page, size, and sort and the frozen filters.

Unsupported filters return a clear validation error rather than silently being ignored.

---

## 94. API sorting allowlist

Only known safe sort fields are allowed.

Example candidates.

```text
createdOn
updatedOn
difficulty
version
```

User-provided strings are not turned into arbitrary database order clauses.

---

## 95. HTTP status mapping

Follow the common platform contract.

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
Duplicate version -> 409
Published version mutation -> 409 or INVALID_STATE
Missing asset -> 404
Validation failure -> 400 or 422
No permission -> 403
```

---

## 96. Idempotency boundary

Content authoring mutations do not require a new idempotency table.

For upload and publication, the implementation prevents accidental duplicate logical operations through state checks and existing uniqueness constraints.

A global idempotency schema is not invented inside Content.

---

## 97. Transaction for create question

```text
Validate template
   |
   v
Validate taxonomy
   |
   v
Create Question
   |
   v
Create initial QuestionVersion
   |
   v
Audit
   |
   v
Commit
```

If the operation exposes initial options or related metadata, they are included atomically where appropriate and where the API contract defines them.

---

## 98. Transaction for create version

```text
Load Question
   |
   v
Allocate next version
   |
   v
Validate content
   |
   v
Persist QuestionVersion
   |
   v
Persist related draft data where part of one command
   |
   v
Audit
   |
   v
Commit
```

Files may require separate object-storage processing.

---

## 99. Transaction for publish

Publication is atomic from the database perspective.

At commit.

```text
QuestionVersion.is_published = true
published_by = actor
published_on = server time
review_status = PUBLISHED
```

Related publication audit state remains consistent. If a required dependency fails, the version remains unpublished.

---

## 100. Asset transaction strategy

Object storage is outside PostgreSQL.

Use a recoverable pattern.

```text
Validate upload
    |
    v
Store object
    |
    v
Persist metadata
    |
    v
Commit
```

If database persistence fails after object upload, an orphan cleanup mechanism exists.

Success is not returned for an asset that cannot be resolved from persisted metadata.

---

## 101. Question draft editing UX

The authoring editor makes the distinction clear.

```text
Draft
Review
Approved
Rejected
Published
```

The UI uses server state. It does not infer publication status from local form state.

---

## 102. Question editor core fields

The editor supports.

```text
Template
Exam, subject, chapter, topic, subtopic
Language
Title
Content
Marks
Negative marks
Difficulty
```

---

## 103. Question editor options

For option-based templates.

```text
Option key
Option content
Display order
Correct answer selection
```

The UI validates obvious errors before submission but relies on the backend for authoritative template validation.

---

## 104. Question editor math

The editor provides safe authoring affordances for inline `\(...\)` and block `\[...\]`.

Users are not taught to rely on `$...$` or `$$...$$`.

Currency content such as `$500` remains valid ordinary text.

---

## 105. Question editor assets

The editor provides upload, list, preview, and remove.

Inserting a canonical reference uses `[asset: <uuid>]` rather than storing filesystem paths in content.

---

## 106. Question editor explanation

Explanation is associated with the exact QuestionVersion.

The UI prevents editing an explanation independently of the selected version. Published-version explanation mutation is blocked.

---

## 107. Question editor hint

Hint is optional and version-bound.

The UI shows whether the selected version already contains a hint.

Hint content is not leaked into active examination payloads when the delivery policy says hints are not available.

---

## 108. Previous exam mapping UI

Provide Exam, Year, Shift, Source.

The UI uses Academic Exam masters. Staff are not allowed to create an ad-hoc free-text exam name where the contract expects `exam_id`.

---

## 109. Tag selection UI

Use the reusable Academic Tag catalog.

Support search, multi-select, and remove.

Tags are not created automatically from arbitrary content text in the first implementation.

---

## 110. Version history UI

Display version number, created date, review state, published state, marks, negative marks, and difficulty.

Editing a historical published version routes the user toward creating a new version.

---

## 111. Review queue UX

A reviewer needs questions awaiting review, selected version, content preview, options, assets, explanation, hint, metadata, validation issues.

The review UI does not invent a separate persisted review entity.

---

## 112. Approval UX

Until the review state is authoritative, the UI does not display a persisted Approved state if the backend cannot represent it.

The final workflow comes from the approved backend design.

---

## 113. Frontend route structure

Suggested.

```text
/questions
/questions/new
/questions/[id]
/questions/[id]/versions/[versionId]
/question-templates
/question-templates/[id]
```

Exact routes follow the established application layout.

---

## 114. Question list UI

Required: search, filters, pagination, sorting, status, template, difficulty, review state, publication state.

Taxonomy filters are enabled when the authoritative mapping exists.

---

## 115. Question table columns

Suggested columns: Question, Template, Difficulty, Version, Review, Publication, Updated on, Actions.

Full question content is not placed in every table row.

---

## 116. Bulk operation boundary

The roadmap mentions a bulk import foundation.

P3 prepares reusable import validation services and interfaces without building a massive import platform.

The first implementation may support CSV or Excel parsing abstraction, row validation, error reporting, dry run, and batch persistence contract only when this directly supports the pilot content onboarding.

---

## 117. Bulk import safety

Bulk import does not insert raw records without applying the same service validation used by interactive authoring.

Bulk import enforces template validity, canonical content validity, option integrity, metadata integrity, and duplicate rules. It does not bypass publication controls.

---

## 118. Import publication rule

Imported questions do not become published merely because import succeeds.

Correct sequence.

```text
Import
 |
 v
Draft
 |
 v
Review
 |
 v
Approval
 |
 v
Publication
```

Unless an explicitly approved trusted-import workflow exists.

---

## 119. Question bank health

The frozen PRD includes question quality and health concepts.

P3 may expose technical validation indicators such as content valid, options valid, assets valid, metadata complete, and ready for review.

A quantitative health score is not frozen in the current schema and is not persisted without a later requirement.

---

## 120. Question quality gate matrix

| Gate | Requirement |
|---|---|
| Text | Question text exists |
| Template | Valid question type exists |
| Options | Required options exist where the template needs them |
| Correct answer | Exists where applicable |
| Marks | Valid |
| Negative marks | Valid |
| Exam mapping | Present |
| Subject mapping | Present |
| Language | Present |
| Review state | APPROVED |
| Content format | MARKDOWN_LATEX_V1 |
| Assets | Shortcode references valid |

---

## 121. Template-driven validation architecture

Avoid one giant switch statement.

```text
QuestionTemplate
      |
      v
QuestionTypeValidator
      |
      v
QuestionTypeRendererMetadata
      |
      v
QuestionTypeEvaluationContract
```

The exact interface is implementation-level design and remains simpler than a micro-framework.

---

## 122. Template registry

A registry maps database template codes to backend strategy implementations.

Example conceptual.

```java
Map<String, QuestionTypeDefinition>
```

Each definition may provide validation, answer model, option model behavior, and rendering hints.

Database-stored class names or arbitrary code do not control strategy loading.

---

## 123. Unknown template handling

If a Question references a template code that has no active implementation, authoring is blocked from publication and delivery never receives unsupported content.

A controlled business error is returned.

---

## 124. Template configuration validation

Template JSONB configuration is parsed into a typed internal structure.

Do not scatter raw JSON lookups across services.

Example.

```java
QuestionTemplateConfiguration
```

may expose `requiresLatexParsing` and other approved configuration values.

---

## 125. Content format metadata

A successful version or content response may expose.

```json
{
  "contentFormat": "MARKDOWN_LATEX_V1"
}
```

The client does not choose the content contract. The server owns the format.

---

## 126. Rendering contract and delivery

The Delivery and CDUI contract defines the QuestionViewModel.

```ts
interface QuestionViewModel {
  attemptQuestionId: string;
  questionVersionId: string;
  displayOrder: number;
  sectionName?: string;
  marks: number;
  negativeMarks: number;
  requiresLatexParsing: boolean;
  contentFormat: "MARKDOWN_LATEX_V1";
  content: string;
  options: OptionViewModel[];
}
```

Content supplies valid canonical content and template metadata. Delivery decides what the student may see.

---

## 127. Correct answer leakage test

Contract tests prove that student-facing question payloads do not contain `isCorrect`, `correctOptionId`, or `answerKey`, even if the underlying entity includes them.

---

## 128. Content API authorization

Typical permission families configured through Identity RBAC.

Conceptual candidates.

```text
QUESTION_TEMPLATE_VIEW
QUESTION_TEMPLATE_CREATE
QUESTION_TEMPLATE_UPDATE

QUESTION_VIEW
QUESTION_CREATE
QUESTION_UPDATE
QUESTION_SUBMIT_REVIEW
QUESTION_REVIEW
QUESTION_PUBLISH
QUESTION_ARCHIVE

QUESTION_OPTION_CREATE
QUESTION_OPTION_UPDATE
QUESTION_OPTION_ARCHIVE

QUESTION_ASSET_CREATE
QUESTION_ASSET_VIEW
QUESTION_ASSET_REMOVE

QUESTION_EXPLANATION_CREATE
QUESTION_EXPLANATION_UPDATE

QUESTION_HINT_CREATE
QUESTION_HINT_UPDATE

QUESTION_PREVIOUS_EXAM_VIEW
QUESTION_PREVIOUS_EXAM_MANAGE
```

These names are candidates, not a new frozen enum.

---

## 129. Staff versus reviewer

A generic STAFF role is not automatically equivalent to REVIEWER.

Permission resolution controls authoring, review, approval, and publication.

Do not hard-code `role == REVIEWER` as the sole business rule.

---

## 130. Student content access

Students receive only content required for practice, active exam, or post-exam review where policy permits.

Student access to explanations and hints depends on Assessment and Delivery policy. Content itself does not bypass those policies.

---

## 131. Caching published questions

Published QuestionVersion data is attractive for caching because it is immutable.

Safe key components.

```text
questionVersionId
contentFormat
rendererVersion
```

Mutable draft state is not cached as a source of truth.

---

## 132. Database query optimization

Use projection queries, pagination, proper indexes, batch fetching, and explicit parent filters.

Do not load the entire Question object graph for list screens.

---

## 133. Potential N+1 sources

Watch for Question to versions, Version to options, Version to assets, Question to tags, and Question to previous history.

A list query does not accidentally issue dozens or hundreds of SQL statements.

---

## 134. Repository tests

Use real PostgreSQL integration tests for version uniqueness, option uniqueness, foreign keys, publication state reads, parent-child queries, pagination, and filtering.

Mocks alone cannot validate database behavior.

---

## 135. Service unit tests

Test create Question, create version, increment version, duplicate version conflict, publish valid, publish invalid, update published rejected, option validation, asset version mismatch, previous history validation, tag validation, review-state transitions, self-review block.

---

## 136. Content parser tests

Required regression tests.

```text
Ordinary text
Currency $500
Inline LaTeX
Block LaTeX
$...$ rejected as math
$$...$$ rejected
Raw HTML rejected
Script tags rejected
Malformed Markdown
Malformed LaTeX delimiters
Invalid asset shortcode
Missing asset
Cross-version asset reference
```

---

## 137. Template tests

For each implemented template.

```text
Valid minimum payload
Invalid payload
Missing required option
Multiple correct where prohibited
No correct answer where prohibited
Invalid answer structure
Rendering metadata
```

Contract tests are added before enabling a template in the pilot.

---

## 138. Asset tests

Test upload valid image, upload unsupported type, oversized file, missing version, published version mutation, cross-version reference, archive or remove behavior, and protected access.

Do not test only the happy path.

---

## 139. Review and publication tests

Test draft to review request, review request repeat, publish before approval, publish invalid content, publish already published, edit after publish, new version after publish.

The persistent representation follows the final review decision.

---

## 140. Frontend unit tests

Test question form validation, template selection, option editor, math entry, asset insert, tag selection, previous exam mapping, version switcher, review actions, publication button guards, and API error rendering.

---

## 141. Frontend end-to-end authoring journey

Minimum.

```text
Login
 |
 v
Open Question Bank
 |
 v
Choose template
 |
 v
Create question
 |
 v
Save draft
 |
 v
Add options
 |
 v
Add asset
 |
 v
Add explanation
 |
 v
Add hint
 |
 v
Save
 |
 v
Submit review
 |
 v
Approve
 |
 v
Publish
```

---

## 142. Frontend end-to-end versioning journey

```text
Create V1
 |
 v
Edit draft
 |
 v
Publish V1
 |
 v
Attempt edit V1
 |
 v
Expect rejection
 |
 v
Create V2
 |
 v
Edit V2
```

V1 remains unchanged.

---

## 143. Frontend end-to-end rendering journey

Create test content containing normal text, `$500` currency, inline math, block math, asset shortcode, and multiple options.

Verify preview and delivery-compatible rendering are consistent.

---

## 144. API security tests

Verify missing JWT, invalid JWT, expired or revoked session, wrong permission, student access, staff access, reviewer access, and publication permission.

The backend, not the frontend, enforces the boundary.

---

## 145. Audit requirements

Mutations that should be audited include create Question, create or update Version, submit review, approve, reject, publish, create or update or remove option, asset upload and remove, explanation update, hint update, previous history mutation, and tag association mutation.

The exact action strings follow the project audit convention.

---

## 146. Logging requirements

Structured logs include requestId, userId where safe, resource type, resource id, operation, duration, outcome, and error code.

Never log password, JWT, private asset paths, sensitive tokens, or full authoring content on normal request logs.

---

## 147. Metrics

Useful module metrics.

```text
content_api_requests_total
content_api_request_duration
content_validation_failures_total
question_publications_total
question_publication_failures_total
content_asset_upload_failures_total
content_render_failures_total
```

A separate analytics table is not created solely for these counters.

---

## 148. OpenAPI requirements

Every endpoint documents authentication, authorization, parameters, request body, validation, success responses, error responses, and multipart behavior.

Content APIs are part of the same OpenAPI document established by P0.

---

## 149. Operational alerts

During pilot, useful alerts include publication failure spike, asset upload failure spike, content renderer failure spike, and unexpected database constraint conflict spike.

Use platform monitoring rather than a Content-specific alerting framework.

---

## 150. P3 task list

```text
P3-01  Content module skeleton
P3-02  Freeze verification and migration check
P3-03  QuestionTemplate
P3-04  Question
P3-05  QuestionVersion
P3-06  QuestionOption
P3-07  Content validator
P3-08  Markdown and LaTeX renderer
P3-09  Asset service
P3-10  Explanation and Hint
P3-11  PreviousExamHistory
P3-12  QuestionTag association
P3-13  Search and filter API
P3-14  Review workflow
P3-15  Publication service
P3-16  Authoring frontend
P3-17  Review frontend
P3-18  Preview and rendering integration
P3-19  Security and audit
P3-20  Tests
P3-21  Pilot content seed and import
P3-22  Performance and observability
P3-23  Code review and exit gate
```

---

## 151. P3-01 module skeleton acceptance

```text
[ ] content package created
[ ] no cross-domain repository access
[ ] interfaces and strategies only where needed
[ ] application starts
[ ] tests run
```

---

## 152. P3-02 schema verification acceptance

Verify exactly.

```text
question_template
question
question_version
question_option
asset
explanation
hint
previous_exam_history
question_tag
```

No extra table is permitted without explicit schema approval.

Verify all required indexes and unique constraints against the active repository migration baseline.

---

## 153. P3-03 QuestionTemplate acceptance

```text
[ ] list
[ ] get
[ ] create
[ ] update
[ ] validation
[ ] RBAC
[ ] audit
[ ] tests
[ ] configuration parsing
```

---

## 154. P3-04 Question acceptance

```text
[ ] logical Question persistence
[ ] initial Version creation mapping
[ ] template validation
[ ] taxonomy validation
[ ] list
[ ] get
[ ] update draft
[ ] archive or deactivate if applicable
[ ] filters
[ ] RBAC
[ ] tests
```

---

## 155. P3-05 QuestionVersion acceptance

```text
[ ] version creation
[ ] version uniqueness
[ ] version history
[ ] canonical content validation
[ ] immutable publication boundary
[ ] API responses
[ ] tests
```

---

## 156. P3-06 QuestionOption acceptance

```text
[ ] CRUD for allowed version states
[ ] unique option key
[ ] unique display order
[ ] content validation
[ ] template-specific validation
[ ] isCorrect protected from student API
[ ] tests
```

---

## 157. P3-07 Content validator acceptance

```text
[ ] MARKDOWN_LATEX_V1 enforced
[ ] raw HTML rejected
[ ] $...$ rejected as math
[ ] $$...$$ rejected
[ ] malformed delimiters rejected
[ ] Markdown AST controlled
[ ] asset shortcode validated
[ ] content size bounded
[ ] structured error codes returned
```

---

## 158. P3-08 Renderer acceptance

```text
[ ] KaTeX SSR
[ ] approved delimiters
[ ] currency regression passes
[ ] asset shortcode resolution
[ ] sanitization
[ ] no executable MDX
[ ] stable renderer configuration
[ ] renderer tests
```

---

## 159. P3-09 Asset acceptance

```text
[ ] multipart upload
[ ] file validation
[ ] metadata persistence
[ ] ownership validation
[ ] protected retrieval
[ ] published-version rules
[ ] orphan handling
```

---

## 160. P3-10 Explanation and Hint acceptance

```text
[ ] version-bound
[ ] canonical content validation
[ ] publication immutability
[ ] get, create, update
[ ] optional hint behavior
[ ] tests
```

---

## 161. P3-11 Previous history acceptance

```text
[ ] multiple occurrences
[ ] exam reference validation
[ ] year
[ ] shift
[ ] source
[ ] list, create, update, archive or remove
[ ] tests
```

---

## 162. P3-12 Tags acceptance

```text
[ ] tag existence through Academic contract
[ ] association CRUD
[ ] duplicate association blocked
[ ] published-version rules as applicable
[ ] tests
```

---

## 163. P3-13 Search acceptance

```text
[ ] pagination
[ ] safe sorting
[ ] search
[ ] status
[ ] template
[ ] difficulty
[ ] review state
[ ] publication
[ ] taxonomy filters
[ ] language filter
```

---

## 164. P3-14 Review acceptance

```text
[ ] DRAFT to IN_REVIEW
[ ] IN_REVIEW to APPROVED
[ ] IN_REVIEW to REJECTED
[ ] REJECTED to DRAFT
[ ] self-review blocked
[ ] illegal transitions rejected server-side
```

---

## 165. P3-15 Publication acceptance

Publication fails when template invalid, content invalid, options invalid, assets invalid, missing required data, unresolved mapping, unapproved review state, or already published.

On success, QuestionVersion is immutable and audit is generated.

---

## 166. P3-16 Authoring frontend acceptance

```text
[ ] question list
[ ] create and edit form
[ ] template selector
[ ] taxonomy selector
[ ] language selector
[ ] options
[ ] math content
[ ] assets
[ ] explanation
[ ] hint
[ ] tags
[ ] previous exam history
[ ] version history
[ ] API errors
[ ] permissions
```

---

## 167. P3-17 Review frontend acceptance

```text
[ ] review queue
[ ] version preview
[ ] validation issues
[ ] submit-review action
[ ] approve action
[ ] reject action
[ ] publication action once prerequisites pass
```

---

## 168. P3-18 Preview acceptance

Preview uses the same canonical renderer semantics as delivery. Editor preview and delivery renderer do not have materially different parsing rules.

---

## 169. P3-19 Security and audit acceptance

```text
[ ] JWT
[ ] RBAC
[ ] backend authorization
[ ] audit
[ ] no secrets in logs
[ ] no correct-answer leakage
[ ] protected assets
[ ] system actor used for background imports
```

---

## 170. P3-20 Test acceptance

```text
[ ] unit
[ ] integration
[ ] API
[ ] security
[ ] end-to-end
[ ] rendering regression
[ ] database constraint tests
```

---

## 171. P3-21 pilot content provisioning

The pilot needs enough trusted content to validate question authoring, review, publication, assessment creation, delivery rendering, and evaluation.

Use controlled fixtures and imports.

Do not manually patch production rows through ad-hoc SQL.

---

## 172. P3-22 performance acceptance

Measure question list p50, p95, and p99, question detail p50, p95, and p99, question version creation, publication latency, asset upload latency, preview server-side rendering, and KaTeX rendering.

Content is not the primary concurrent-user runtime hotspot, but its published read path is efficient enough not to become a delivery bottleneck.

---

## 173. P3-23 exit gate

P3 exits only when.

```text
Question bank works
        +
Published QuestionVersions are immutable
        +
Canonical rendering works
        +
Assets are secure
        +
Review and publication flow is authoritative
        +
Search works
        +
RBAC works
        +
Audit works
        +
Tests pass
        +
CI passes
```

---

## 174. Recommended implementation prompt

```text
You are implementing P3 Content and Question Bank for the Smart
Competitive Examination Platform.

Read the full specification package before modifying the repository.

Content owns:
QuestionTemplate
Question
QuestionVersion
QuestionOption
Asset
Explanation
Hint
PreviousExamHistory
QuestionTag

NON-NEGOTIABLE:
1.  Do not invent database tables.
2.  Do not silently add taxonomy columns.
3.  Do not change API paths.
4.  Use Flyway for approved schema changes.
5.  Published QuestionVersion is immutable.
6.  Never overwrite published content.
7.  Use MARKDOWN_LATEX_V1.
8.  Accept only \( ... \) and \[ ... \] as math delimiters.
9.  Reject $...$ and $$...$$ as math delimiters.
10. Reject raw HTML.
11. Reject executable MDX.
12. Validate [asset: uuid] references against the same QuestionVersion.
13. Never expose isCorrect in student APIs.
14. Never expose raw asset.file_path to students.
15. Use DTOs, services, repositories, mappers and validation.
16. Do not access Academic repositories directly from Content.
17. Use explicit module contracts for Academic lookups.
18. Use TanStack Query for frontend server state.
19. Use React Hook Form and Zod for authoring forms.
20. Use KaTeX SSR for canonical rendering.
21. Do not create exam-name-specific React branches.
22. Do not introduce microservices.
23. Do not add Elasticsearch or OpenSearch.
24. Do not add an AI database model.
25. Test database constraints using PostgreSQL integration tests.
26. Test rendering regressions including $500 currency.
27. Run formatting, tests, build and CI checks.
28. Report any frozen-contract conflict rather than silently resolving it.

Implementation order:
P3-01 through P3-23.

Before merging:
- List changed files.
- State migration status.
- List implemented endpoints.
- List permissions.
- List tests.
- State review workflow decisions.
- State unresolved blockers.
- Prove published version immutability.
- Prove no correct-answer leakage.
- Prove content security.
```

---

## 175. Code review questions

Reviewers ask.

```text
Does the schema exactly match the active baseline?

Are published versions truly immutable?

Can concurrent version creation violate uniqueness?

Can an option be attached to another QuestionVersion?

Can an asset shortcode cross version boundaries?

Can raw HTML pass the validator?

Can $500 trigger the math parser?

Can executable MDX execute?

Can a student receive isCorrect?

Can a student learn the raw asset path?

Does QuestionService directly access Academic repositories?

Are template strategies configurable without arbitrary code execution?

Does preview use the same rendering contract as delivery?

Are unsupported templates prevented from publication?

Are taxonomy filters backed by real authoritative persistence?

Is review state authoritative?
```

---

## 176. Known design decisions summary

Confirmed by the frozen baseline.

```text
Question and QuestionVersion separation
QuestionOption normalized table
QuestionTemplate with JSONB configuration
Assets per QuestionVersion
Explanation and Hint per version
PreviousExamHistory with multiple occurrences
QuestionTag many-to-many
Published QuestionVersion immutability
MARKDOWN_LATEX_V1
KaTeX server-side rendering
Asset shortcode authorization
Question APIs
Review submission, approval, rejection, and publish endpoints
Taxonomy on the logical Question
Language on the QuestionVersion
Review state on the QuestionVersion
```

These are the implementation constraints.

---

## 177. P3 handoff to Assessment

Once P3 exits, Assessment can consume published QuestionVersion IDs, template information, valid question content, valid options, assessment-safe marks defaults, negative marks defaults, reusable tags and metadata, and historical question information.

Assessment does not modify Content-owned QuestionVersion records. It creates its own assessment-specific references and scoring according to the frozen Assessment schema.

---

## 178. P3 handoff to Delivery

Delivery consumes only published, authorized content.

The delivery path resolves QuestionVersion, QuestionOption, required assets, contentFormat, and requiresLatexParsing.

It does not expose isCorrect, answer key, review state internals, private audit fields, or raw storage paths.

---

## 179. P3 handoff to Evaluation

Evaluation consumes answer semantics through its own answer-key contract.

Content owns authoring truth. Evaluation owns official scoring. Scoring-result persistence is not embedded inside the Content module.

---

## 180. Final engineering principle

The Content module makes one thing dependable.

> A QuestionVersion is a safe, exact, reviewable, and historically reproducible representation of examination content that downstream Assessment, Delivery, and Evaluation modules consume without reinterpreting or mutating it.

---

## 181. Final status

**Status:** IMPLEMENTATION-READY.

**New tables required:** No. `question_option` is already part of the schema.

**Primary risks:**

```text
Content rendering safety
Published immutability
Template-specific persistence gaps
Asset synchronization
Review workflow enforcement
```

**Next implementation document:** P4 — Blueprint and Assessment Management.