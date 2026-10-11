# Smart Competitive Examination Platform
## P6 — Evaluation & Results Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P6 — Evaluation, Results, and Scorecards
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, P4 Blueprint and Assessment Management, P5 Assessment Delivery and Runtime Examination Engine, ExamEngine and CDUI Contract
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

This document converts the Evaluation domain into an implementation-ready engineering specification.

P6 converts a submitted StudentAttempt into an authoritative academic evaluation and, where permitted, a published result.

The lifecycle.

```text
Submitted attempt
      |
      v
Answer key resolution
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
Publication
```

P6 answers:

```text
Which answer key version was applied?
Which response was judged?
How were marks calculated?
How was negative marking applied?
How were bonus, grace, and dropped questions handled?
Was the evaluation reproducible?
Can the evaluation be repeated safely?
Can a result be published exactly once?
Can an authorized correction trigger controlled re-evaluation?
```

---

## 2. Source-of-truth hierarchy

Read these documents before P6 implementation:

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
- This document

The earlier documents already freeze the Evaluation schema, the AnswerKeyDetail contract, the evaluation versioning model, the scoring semantics, and the result publication boundary. This document does not modify those decisions.

---

## 3. Ownership boundary

P6 owns:

```text
AnswerKey
AnswerKeyDetail
Evaluation
Result
Scorecard
Evaluation policies
Scoring calculation
Re-evaluation orchestration
Result publication
Scorecard generation
Evaluation audit evidence
```

P6 does not own:

```text
Question authoring
Assessment authoring
Question snapshot creation
Student response persistence
Runtime timer
Runtime navigation
Attempt start
Autosave
Commerce
Notification delivery
Advanced analytics
```

---

## 4. End-to-end boundary

```text
P4
Published AssessmentVersion
      |
      v
P5
StudentAttempt
AttemptQuestion
AttemptQuestionOption
StudentResponse
      |
      v
P6
AnswerKey
AnswerKeyDetail
Evaluation
Result
Scorecard
      |
      v
P7 and P8
Derived analytics
```

The submitted Attempt is the runtime input to Evaluation.

Evaluation scores the exact snapshot that the student actually attempted, not a newly materialized AssessmentVersion.

---

## 5. Frozen database schema

### 5.1 Table: answer_key

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

Published AnswerKeys are immutable.

### 5.2 Table: answer_key_detail

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

Check: for `NORMAL`, at least one of `correct_option_id` or `correct_answer_text` is present. For `BONUS` or `DROPPED`, both are null.

Indexes.

```text
idx_answer_key_detail_answer_key_id
idx_answer_key_detail_question_version_id
```

Publication requires exactly one detail row per AssessmentQuestion in the bound AssessmentVersion.

### 5.3 Table: evaluation

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

### 5.4 Table: result

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

### 5.5 Table: scorecard

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

## 6. Evaluation state machine

The broad lifecycle is fixed.

```text
StudentAttempt
SUBMITTED
    |
    v
Evaluation
    |
    v
Result
    |
    v
PUBLISHED
```

Evaluation status vocabulary uses the following controlled values.

```text
PENDING
IN_PROGRESS
COMPLETED
FAILED
SUPERSEDED
```

The exact set is enforced by the application. New values are not introduced without an approved amendment.

---

## 7. Result state machine

Result status vocabulary.

```text
CALCULATED
READY_FOR_PUBLICATION
PUBLISHED
SUPERSEDED
```

The lifecycle.

```text
CALCULATED
    |
    v
READY_FOR_PUBLICATION
    |
    v
PUBLISHED
```

Controlled correction.

```text
PUBLISHED
    |
    v
Re-evaluation
    |
    v
New evaluation version
    |
    v
New result in CALCULATED
    |
    v
PUBLISHED
```

The previous Result is marked SUPERSEDED. Historical records remain traceable.

---

## 8. Immutable historical chain

Historical reproducibility requires preserving.

```text
StudentAttempt
      |
      v
Exact AssessmentVersion
      |
      v
Exact AttemptQuestion set
      |
      v
Exact QuestionVersion IDs
      |
      v
Exact AttemptQuestionOption ordering
      |
      v
StudentResponse
      |
      v
AnswerKey version
      |
      v
AnswerKeyDetail
      |
      v
Evaluation
      |
      v
Result
```

Evaluation never uses the latest QuestionVersion, the latest AssessmentVersion, current question marks, current option order, or current DeliveryPolicy.

The runtime snapshot and the AnswerKey version together form the historical evaluation input.

---

## 9. Evaluation input contract

P6 consumes:

```text
StudentAttempt
AttemptQuestion
AttemptQuestionOption
StudentResponse
AssessmentVersion
BlueprintVersion and scoring context
AnswerKey version
AnswerKeyDetail set
```

The input is read-only from P6.

P6 does not mutate AttemptQuestion, AttemptQuestionOption, StudentResponse, AssessmentVersion, or BlueprintVersion.

---

## 10. Evaluation output contract

A successful evaluation produces Evaluation and Result.

A scorecard is a derived presentation artifact.

Official score fields come from the Evaluation calculation.

P6 keeps the calculation deterministic for identical inputs and policy versions.

---

## 11. Question-type extensibility

The platform supports multiple question templates.

The Evaluation engine therefore needs a strategy boundary.

```text
QuestionTemplate
      |
      v
Evaluation strategy
      |
      v
Question evaluation outcome
```

P6 does not contain a giant switch statement that hard-codes every future template into one class.

An extensible evaluator registry is used while preserving the QuestionTemplate contract.

---

## 12. Question evaluation strategy

Recommended application contract.

```java
public interface QuestionEvaluator {
    boolean supports(String templateCode);
    QuestionEvaluationOutcome evaluate(QuestionEvaluationContext context);
}
```

The exact method signatures are implementation design. The key boundary is template-specific response interpretation, separate from global assessment score aggregation.

---

## 13. Objective question baseline

For objective questions, the evaluation flow is conceptually.

```text
Student response
      |
      v
Normalize
      |
      v
Resolve authoritative answer from AnswerKeyDetail
      |
      v
Compare
      |
      v
Correct, incorrect, or unanswered
      |
      v
Apply marks and negative marks
```

The actual answer source is resolved from the approved AnswerKeyDetail contract.

`question_option.is_correct` is an authoring-time convenience and is not consulted at evaluation time.

---

## 14. Unanswered questions

An unanswered question is distinguishable from an incorrect answer, an invalid response, and a not-evaluated question.

Typical objective scoring semantics.

```text
Unanswered -> 0
Incorrect  -> negative marks if configured
Correct    -> marks
```

This remains policy-driven because negative marking is configurable.

---

## 15. Negative marking

AssessmentQuestion stores marks and negative_marks.

These values are authoritative for the specific assessment snapshot.

Evaluation uses AttemptQuestion marks and AttemptQuestion negative_marks, not QuestionVersion default marks, current AssessmentQuestion values, or Blueprint defaults.

Assessment-specific scoring is preserved.

---

## 16. Question-level score formula

For a standard objective question, the conceptual formula is.

```text
Correct    -> + marks
Incorrect  -> - negative_marks
Unanswered -> + 0
```

P6 does not assume every template uses this exact formula.

The evaluator strategy decides the raw outcome. The assessment scoring contract decides how that outcome contributes to the final score.

---

## 17. Minimum score

The frozen schema permits `obtained_marks NUMERIC(8,2)`.

The final score is not clamped at zero. Negative totals are permitted.

This is required for exams with heavy negative marking. The scoring contract explicitly does not impose a floor.

---

## 18. Maximum score

AssessmentVersion stores total_marks.

Evaluation validates that the calculated maximum equals the published assessment total_marks, subject to explicitly approved bonus and grace rules.

If bonus marks can exceed published totals, that behavior is modeled through AnswerKeyDetail overrides and is audited.

---

## 19. Bonus marks

Bonus marks are represented through `answer_key_detail.question_outcome = 'BONUS'`.

Every attempt receives the configured marks for that question regardless of the student's response.

The awarded marks are taken from `answer_key_detail.awarded_marks` when present, otherwise from `attempt_question.marks`.

Bonus is not a separate table or hidden configuration.

---

## 20. Dropped questions

Dropped questions are represented through `answer_key_detail.question_outcome = 'DROPPED'`.

A dropped question contributes 0 marks. The student's response is ignored. The question is excluded from attempted count and accuracy denominators.

---

## 21. Marks override

`answer_key_detail.awarded_marks` and `awarded_negative_marks` are optional overrides.

When present, they supersede `attempt_question.marks` and `attempt_question.negative_marks` for that question.

When null, the attempt snapshot values apply.

This lets a corrected AnswerKey adjust scoring for one question without modifying the immutable attempt snapshot.

---

## 22. Multiple correct answers

Question templates may support response types beyond simple single-choice.

The evaluator distinguishes single-select, multi-select, text and numeric, and other configured template types.

The exact permitted scoring semantics for each template are defined by QuestionTemplate configuration and the Evaluation strategy.

P6 does not invent a universal multi-select scoring formula.

---

## 23. Text and numeric evaluation

For text and numeric question types, the system may require normalization, case rules, whitespace rules, numeric precision, unit handling, and accepted aliases.

Only rules explicitly represented in the frozen QuestionTemplate, content contract, or approved Evaluation contract are enforced.

When no rule exists, evaluation fails to an explicit unsupported-state path rather than silently guessing.

---

## 24. Subjective questions

The source supports multiple question templates but does not specify a human grading workflow.

P6 does not assume subjective or manual evaluation exists in this release.

If a QuestionTemplate requires manual grading, an explicit future contract must define grader identity, rubric, marks awarded, moderation, reconciliation, and publication.

No manual-grading tables are introduced by this document.

---

## 25. Answer key resolution

An evaluation selects an explicit AnswerKey.

The resolution chain.

```text
AssessmentVersion
      |
      v
Published AnswerKey versions
      |
      v
Selected AnswerKey version
      |
      v
Evaluation
```

P6 never silently chooses the latest by created_on, latest by version, or first published unless the approved contract states that exact rule.

---

## 26. Explicit answer key selection

The evaluation request requires an explicit `answerKeyId`.

Preferred operational safety: an explicit AnswerKey reference is used for staff-triggered and internally-triggered evaluation to prevent accidental use of the wrong key.

Defaulting to a "current published key" is not implemented without an approved rule.

---

## 27. Published answer key immutability

Once `is_published = true`, the AnswerKey contents and identity are immutable.

Corrections are represented by a new AnswerKey version, not by an update to the published version.

Historical evaluations retain their exact AnswerKey ID.

---

## 28. Answer key publication

```http
POST /api/v1/answer-keys/{id}/publish
```

Before publication.

```text
Verify assessment version
Verify source assessment is published
Verify answer mapping completeness
Verify question coverage
Verify supported templates
Verify policy consistency
```

After publication.

```text
Immutable
Audited
Available for evaluation
```

---

## 29. Answer key completeness

Before an AnswerKey can be published, the Evaluation implementation verifies that every evaluable AssessmentQuestion is handled according to the approved contract.

Expected validation dimensions.

```text
Question identity
Template support
Answer mapping
Option identity
Single and multi choice semantics
Special scoring rule
Dropped or bonus state if supported
```

A partial answer key is not published unless the business contract explicitly permits partial keys.

---

## 30. Answer key detail API

```http
GET    /api/v1/answer-keys/{id}/details
POST   /api/v1/answer-keys/{id}/details
PUT    /api/v1/answer-keys/{id}/details/{detailId}
DELETE /api/v1/answer-keys/{id}/details/{detailId}
```

Create request.

```json
{
  "questionVersionId": "<uuid>",
  "correctOptionId": "<uuid>",
  "correctAnswerText": null,
  "questionOutcome": "NORMAL",
  "awardedMarks": null,
  "awardedNegativeMarks": null,
  "remarks": null
}
```

Rules.

```text
correctOptionId belongs to the same questionVersionId
For NORMAL, at least one of correctOptionId or correctAnswerText is present
For BONUS or DROPPED, both are null
Mutations blocked after answer_key.is_published = true
```

---

## 31. Evaluation determinism

For the same Attempt, AnswerKey version, AnswerKeyDetail set, scoring configuration, and evaluation policy version, the evaluator produces the same question outcomes, obtained marks, and percentage.

Determinism is required for audit, re-evaluation, incident investigation, and reproducibility.

---

## 32. Rounding policy

The schema supports two decimal places for marks and percentage and four decimal places for percentile.

Rounding policy.

```text
Per-question award: full BigDecimal precision retained
Final obtained_marks: HALF_UP to 2 decimals
Percentage: HALF_UP to 2 decimals
Percentile: HALF_UP to 4 decimals
```

No rounding is performed implicitly by Java floating-point primitives.

---

## 33. Money versus marks arithmetic

Marks are academic numeric values, not currency.

Use `BigDecimal` throughout evaluation.

Do not use `float` or `double` for official scoring calculations.

---

## 34. Percentage calculation

The conceptual formula.

```text
percentage = obtained_marks / published_total_marks * 100
```

Rounded HALF_UP to 2 decimals.

The denominator and any exceptions follow the approved scoring policy. A different denominator is not inferred from the number of answered questions.

---

## 35. Rank calculation

Result contains `rank INTEGER`.

Population: all attempts bound to the same AssessmentVersion whose current (non-superseded) Evaluation has a Result with `result_status = 'PUBLISHED'`.

Formula: standard competition ranking.

```text
Sort scores descending.
Same score -> same rank.
Next distinct score -> rank skips by the number of tied entries.
```

Example.

```text
Score 98 -> rank 1
Score 95 -> rank 2
Score 95 -> rank 2
Score 91 -> rank 4
```

Recomputation trigger: when a Result is published or re-evaluated, ranks for the affected AssessmentVersion are recomputed in a bounded background job. The result.rank column is updated in the same transaction batch.

`rank` remains NULL until the Result is PUBLISHED.

---

## 36. Percentile calculation

Formula.

```text
percentile = (count of candidates with score <= my score) / total candidates * 100
```

Rounded HALF_UP to 4 decimals.

Population: same as rank.

`percentile` remains NULL until the Result is PUBLISHED.

Recomputation trigger: same as rank.

---

## 37. Rank and percentile edge cases

| Scenario | Behaviour |
|---|---|
| Single candidate | rank = 1, percentile = 100.0000 |
| All tied | everyone rank = 1, percentile = 100.0000 |
| No published results | rank and percentile NULL |
| Re-evaluation changes a score | rank and percentile recomputed for the whole AssessmentVersion |

---

## 38. Rank recomputation concurrency

Rank recomputation is serialized per `assessment_version_id` via a row lock on the assessment_version.

Concurrent publication of results in the same assessment version does not produce torn rankings.

---

## 39. Result visibility

| Result status | Student sees | Staff sees | Admin sees |
|---|---|---|---|
| CALCULATED | No | Yes | Yes |
| READY_FOR_PUBLICATION | No | Yes | Yes |
| PUBLISHED | Yes | Yes | Yes |
| SUPERSEDED | No | Yes in history | Yes in history |

Only PUBLISHED results are visible on the student's own result endpoints.

Provisional results are not exposed to students.

---

## 40. Evaluation context

Recommended immutable context object.

```text
EvaluationContext
  attempt
  assessmentVersion
  blueprintVersion and scoring configuration
  snapshot questions
  responses
  answerKey
  answerKeyDetail set
  scoring policy
```

JPA entities are not passed directly into pure calculation components.

---

## 41. Evaluation pipeline

Recommended processing flow.

```text
Load attempt
   |
   v
Verify SUBMITTED
   |
   v
Resolve AnswerKey
   |
   v
Load immutable snapshot
   |
   v
Load responses
   |
   v
Load AnswerKeyDetail set
   |
   v
Validate evaluation preconditions
   |
   v
Evaluate each question
   |
   v
Aggregate score
   |
   v
Calculate percentage
   |
   v
Persist Evaluation
   |
   v
Persist Result
   |
   v
Trigger scorecard generation if requested
```

Rank and percentile are computed in a bounded follow-up step for the affected AssessmentVersion population.

---

## 42. Evaluation preconditions

The evaluator rejects or defers when.

```text
Attempt not SUBMITTED
Answer key not published
Assessment version not published
Answer key detail incomplete
Unsupported question template
Snapshot missing or inconsistent
Response invalid for question
Scoring configuration invalid
```

The error path is explicit and auditable.

---

## 43. Attempt status requirement

Normal official evaluation starts only after `StudentAttempt.attempt_status = SUBMITTED`.

P6 does not evaluate NOT_STARTED or IN_PROGRESS attempts unless a separate approved operational action explicitly handles forced evaluation.

---

## 44. Already evaluated attempt

Because `UNIQUE(attempt_id) WHERE superseded_by_id IS NULL` exists on Evaluation, repeated evaluation does not create a second current official Evaluation row.

The service implements evaluate-once semantics and routes later requests to the existing Evaluation or to the explicit re-evaluation workflow.

---

## 45. Evaluation idempotency

The same evaluation request repeated with the same AnswerKey version does not produce duplicate business effects.

Possible behavior.

```text
First call -> Evaluation CREATED
Repeat call -> existing Evaluation returned
```

Or equivalent deterministic result.

A second Result or Scorecard is not silently generated.

---

## 46. Re-evaluation boundary

```http
POST /api/v1/attempts/{attemptId}/re-evaluate
```

Re-evaluation is a privileged operation.

It identifies the intended AnswerKey, re-runs scoring deterministically, records why re-evaluation occurred, preserves audit evidence, and controls result publication.

---

## 47. Re-evaluation persistence

The frozen schema uses `UNIQUE(attempt_id, version)` and a partial unique index enforcing at most one current Evaluation per attempt.

Re-evaluation creates version N+1 and marks version N superseded.

Schema semantics.

```text
Old evaluation:
  superseded_by_id = new evaluation id
  superseded_on = now()
  superseded_reason = text

New evaluation:
  version = N + 1
  superseded_by_id = NULL
```

The historical evaluation remains queryable.

---

## 48. Re-evaluation request

```http
POST /api/v1/attempts/{attemptId}/re-evaluate
```

Request.

```json
{
  "answerKeyId": "<uuid>",
  "reason": "Corrected answer key v2"
}
```

`answerKeyId` is required.

Missing `answerKeyId` returns 400. Unpublished AnswerKey returns 409. Mismatched AssessmentVersion returns 409.

---

## 49. Re-evaluation response

```json
{
  "success": true,
  "message": "Re-evaluation completed",
  "data": {
    "attemptId": "<uuid>",
    "newEvaluationId": "<uuid>",
    "newResultId": "<uuid>",
    "previousEvaluationId": "<uuid>",
    "newVersion": 2
  }
}
```

---

## 50. Evaluation history API

```http
GET /api/v1/attempts/{attemptId}/evaluations
```

Returns all evaluation versions for the attempt, newest first, including superseded metadata.

Restricted to `EVALUATION_VIEW_HISTORY` permission.

---

## 51. Re-evaluation audit

Every authorized re-evaluation captures through the existing audit boundary.

```text
attemptId
previous AnswerKey ID
new AnswerKey ID
operator
reason
timestamp
outcome
```

A P6-specific audit table is not created.

---

## 52. Answer key correction model

```text
AnswerKey V1 published
       |
       v
Issue detected
       |
       v
AnswerKey V2 created
       |
       v
V2 published
       |
       v
Affected attempts re-evaluated
```

Published V1 remains immutable.

The Result publication consequences follow the approved re-evaluation semantics.

---

## 53. Result creation

Result is created from the persisted Evaluation output.

It contains obtained_marks, percentage, rank, percentile, and result_status.

Result does not independently recalculate marks from QuestionVersion data.

---

## 54. Result publication

```http
POST /api/v1/results/{resultId}/publish
```

Before publication.

```text
Result valid
Evaluation complete
Required fields calculated
Publication permission present
```

After publication.

```text
Result is an official business record
Publication is audited
```

Republishing rules are defined by the state transition policy.

---

## 55. Result immutability

Published Results are treated as immutable business records.

A published Result is not an editable CRUD record.

Corrections flow through controlled re-evaluation and controlled publication.

Generic PUT semantics for official results are not exposed.

---

## 56. Published result and re-evaluation

If a published result is re-evaluated, the system does not silently overwrite the prior official result.

The old evaluation is marked superseded. The old result is marked `superseded_on` and moves to SUPERSEDED status. A new evaluation and result are created.

The transition is audited with the reason and operator.

---

## 57. Scorecard generation

```http
GET  /api/v1/results/{resultId}/scorecard
POST /api/v1/results/{resultId}/scorecard
```

Scorecard generation is a presentation artifact.

Large generation may be asynchronous and can return 202 Accepted with a job reference, consistent with the broader reporting architecture.

---

## 58. Scorecard security

A scorecard is protected by authenticated user, authorization, result ownership or staff permission, and secure file access.

Raw file path, server filesystem path, and private object-store credentials are not exposed.

The scorecard URL and file reference are abstracted behind authorized delivery.

---

## 59. Scorecard content boundary

Scorecards may include student identity, assessment, marks, percentage, rank, percentile, question summary if policy permits, and section score if available.

The exact document contents are configured at the implementation layer without introducing a new database template table.

---

## 60. Scorecard regeneration

A regenerated scorecard for the same Result is idempotent at the business level.

The implementation defines whether the same file is replaced, a new file is generated, or the existing file is reused.

The source schema provides one Scorecard per Result via `UNIQUE(result_id)`. Multiple historical scorecard rows are not supported without a contract amendment.

---

## 61. File storage

Local filesystem in development.

S3-compatible storage in production.

The generated reference is stored in `scorecard.file_path` while ensuring callers cannot retrieve arbitrary paths.

---

## 62. Evaluation module structure

```text
evaluation/
  api/
    AnswerKeyController
    AnswerKeyDetailController
    EvaluationController
    ResultController
    ScorecardController
  application/
    AnswerKeyService
    AnswerKeyDetailService
    AnswerKeyPublicationService
    EvaluationService
    ReEvaluationService
    ResultService
    ResultPublicationService
    ScorecardService
  domain/
    AnswerKey
    AnswerKeyDetail
    Evaluation
    Result
    Scorecard
  strategy/
    QuestionEvaluator
    QuestionEvaluatorRegistry
    template-specific evaluators
  repository/
  dto/
  mapper/
  validation/
  exception/
  worker/
```

---

## 63. Application versus calculation layer

Separate evaluation orchestration from pure scoring calculation.

The calculation layer is free of database and HTTP dependencies.

This enables unit testing, reproducibility, and safe re-evaluation.

---

## 64. Repository boundaries

Evaluation repositories access only Evaluation-owned entities.

To read Delivery data, use application contracts such as SubmittedAttemptProvider, AttemptSnapshotProvider, and StudentResponseProvider.

Delivery JPA repositories are not accessed directly from the Evaluation module.

---

## 65. Delivery to Evaluation contract

P5 hands off attemptId, assessmentVersionId, assessmentScheduleId, attempt mode, submission state, exact snapshot references, and student responses.

Evaluation resolves authoritative persisted data from those references.

P6 does not depend on the frontend submission payload.

---

## 66. No client-calculated score

The student browser may show answered count, unanswered count, and review count.

P6 never trusts client score, client percentage, client rank, or client correctness.

Official scoring is server-side.

---

## 67. Response integrity

P6 verifies that response belongs to attempt, question version belongs to attempt snapshot, response encoding is valid, and no forbidden duplicate semantics exist.

If invalid stored response data exists, Evaluation fails or flags the attempt rather than silently ignoring it.

---

## 68. Incomplete response set

Evaluation supports unanswered questions correctly.

It distinguishes no StudentResponse row from StudentResponse exists with an empty or clear response.

The QuestionTemplate response contract determines whether those states are equivalent.

---

## 69. Attempt question count

Evaluation uses the immutable AttemptQuestion set.

Do not derive evaluable question count from StudentResponse count.

Example.

```text
100 AttemptQuestions
70 StudentResponses
```

means the attempt still contains 100 evaluable questions, with 30 unanswered unless the approved response semantics say otherwise.

---

## 70. Section aggregation

P6 may calculate section-level scoring internally where the Result contract later needs it.

The frozen Result table does not contain section-score columns.

Section score presentation may initially remain a derived view or future analytics rather than new Result columns.

---

## 71. Question outcome model

Recommended internal outcome.

```text
QuestionEvaluationOutcome
  questionVersionId
  status
  responseState
  awardedMarks
  maximumMarks
  negativeMarksApplied
```

This is an application object, not a new database table.

---

## 72. Question outcome states

Possible internal states.

```text
CORRECT
INCORRECT
UNANSWERED
INVALID_RESPONSE
BONUS
DROPPED
UNSUPPORTED
```

These are implementation-level concepts and are not frozen database enums.

Only states approved by the final scoring contract become persisted status values.

---

## 73. Aggregation algorithm

Conceptually.

```text
total = 0

for each AttemptQuestion:
    evaluate response
    determine question outcome
    calculate awarded marks
    total += awarded marks
```

Then.

```text
percentage = total / published total marks * 100
```

Rank and percentile are calculated only when their population and formula are approved, which they are in P6.

---

## 74. BigDecimal rules

Use BigDecimal for marks, negative marks, obtained score, percentage, and percentile.

Never serialize official numeric calculations through binary floating point.

---

## 75. Calculation context immutability

The scoring context is immutable after evaluation starts.

Marks, negative marks, answer key, question mapping, and scoring configuration are not mutated during a calculation.

---

## 76. Parallel question evaluation

Question-level evaluation may be parallelized for large assessments, but the first implementation prefers correctness and bounded resource use.

If parallelized.

```text
Question strategies are pure
Shared state is read-only
BigDecimal accumulation is deterministic
Database calls are not performed per question
```

One database transaction per question is not created.

---

## 77. Batch evaluation

For large assessment populations.

```text
Submitted attempts
      |
      v
Evaluation worker
      |
      v
Bounded batch
      |
      v
Evaluate
      |
      v
Persist
```

Use bounded worker concurrency.

Do not load thousands of complete attempts into heap at once.

---

## 78. Evaluation worker

Recommended background responsibilities.

```text
Consume submitted-attempt evaluation work
Load context
Evaluate
Persist evaluation and result
Emit telemetry
Retry transient failures
```

The worker is idempotent.

---

## 79. Evaluation trigger

The source says Submit Attempt can trigger evaluation.

P6 supports.

```text
Submit
   |
   v
Durable handoff
   |
   v
Evaluation worker
```

The frozen schema does not include a dedicated evaluation-job table.

Therefore P6 uses the existing `background_job` infrastructure without adding an evaluation-job table.

---

## 80. Synchronous versus asynchronous evaluation

The API source allows evaluation to be synchronous or asynchronous depending on assessment size.

Recommended at the architectural level.

```text
Submit -> durable handoff -> async evaluation
```

because it prevents heavy scoring work from elongating the student submission transaction.

The exact queue and job mechanism remains part of the platform infrastructure.

---

## 81. Submission handoff reliability

A submission is not considered successfully evaluated merely because the evaluation worker accepted a request.

Authoritative states.

```text
Attempt SUBMITTED
Evaluation COMPLETED
Result calculated
```

Each is separately observable.

---

## 82. Evaluation failure recovery

Transient failures such as database timeout, temporary dependency issue, or worker crash are retryable.

Permanent failures such as unsupported template, missing answer mapping, corrupt snapshot, or invalid scoring configuration are visible and require controlled remediation.

---

## 83. Retry safety

Retrying evaluation does not create duplicate Evaluation, create duplicate Result, publish twice, or change the AnswerKey.

The frozen unique constraints provide an important database safety boundary.

---

## 84. Transaction boundary

One successful evaluation transaction persists the logically coupled records Evaluation and Result where possible.

The transaction is not held while generating a large PDF, calling an external service, or sending email.

Scorecard generation occurs outside the core scoring transaction.

---

## 85. Result publication transaction

Publishing a Result is a short state transition with authorization, state validation, and publication timestamp and audit.

The score is not recalculated during publication.

---

## 86. Audit events

Important P6 audit events.

```text
AnswerKey created
AnswerKeyDetail created or updated
AnswerKey published
Evaluation started
Evaluation completed
Evaluation failed
Re-evaluation requested
Re-evaluation completed
Result created
Result published
Scorecard generated
```

Use the existing audit subsystem. Audit insertion participates in the business transaction for critical state transitions.

---

## 87. Security boundary

Students may read their own published result and their own scorecard subject to the publication state.

Staff and admins may access broader data only with permissions.

Students never receive answer key, evaluation internals, other students' results, unpublished results, or private audit data.

---

## 88. Answer key API inventory

```http
GET  /api/v1/answer-keys
POST /api/v1/answer-keys
GET  /api/v1/answer-keys/{id}
POST /api/v1/answer-keys/{id}/publish

GET    /api/v1/answer-keys/{id}/details
POST   /api/v1/answer-keys/{id}/details
PUT    /api/v1/answer-keys/{id}/details/{detailId}
DELETE /api/v1/answer-keys/{id}/details/{detailId}
```

---

## 89. Evaluation API inventory

```http
POST /api/v1/attempts/{attemptId}/evaluate
POST /api/v1/attempts/{attemptId}/re-evaluate
GET  /api/v1/attempts/{attemptId}/evaluations
GET  /api/v1/evaluations/{id}
```

---

## 90. Result API inventory

```http
GET  /api/v1/results
GET  /api/v1/results/{id}
GET  /api/v1/attempts/{attemptId}/result
POST /api/v1/results/{resultId}/publish
GET  /api/v1/assessments/{assessmentId}/rankings
```

Filters: assessmentId, assessmentVersionId, studentId, status, from, to.

---

## 91. Scorecard API inventory

```http
GET  /api/v1/results/{resultId}/scorecard
POST /api/v1/results/{resultId}/scorecard
```

---

## 92. Evaluate attempt API

```http
POST /api/v1/attempts/{attemptId}/evaluate
```

Request.

```json
{
  "answerKeyId": "<uuid>"
}
```

Validation.

```text
Attempt exists
Caller authorized
Attempt SUBMITTED
AnswerKey exists and is published
AnswerKey belongs to the attempt's AssessmentVersion
Snapshot valid
```

---

## 93. Re-evaluate attempt API

```http
POST /api/v1/attempts/{attemptId}/re-evaluate
```

Request.

```json
{
  "answerKeyId": "<uuid>",
  "reason": "Corrected answer key v2"
}
```

`answerKeyId` is required. The request does not silently choose a different AnswerKey.

---

## 94. Get evaluation API

```http
GET /api/v1/evaluations/{id}
```

Student-facing access is restricted because Evaluation may contain internal calculation detail not intended for students.

Authorized projections only.

---

## 95. Get result API

```http
GET /api/v1/results/{id}
GET /api/v1/attempts/{attemptId}/result
```

For students, only results permitted by the publication rules are exposed.

Unpublished or internal result state is not leaked.

---

## 96. List results API

```http
GET /api/v1/results
```

Filtering and pagination are mandatory for staff use.

The implementation does not load every result into memory.

---

## 97. Result publication API

```http
POST /api/v1/results/{resultId}/publish
```

The endpoint authenticates, authorizes, validates result state, validates evaluation completeness, transitions publication state, and audits.

It does not recompute scoring.

---

## 98. Scorecard API

GET returns secure scorecard reference or status.

POST validates result, generates or enqueues scorecard, persists scorecard metadata, and returns the result according to synchronous or asynchronous completion.

The implementation prevents duplicate scorecard records through `UNIQUE(result_id)`.

---

## 99. Rankings API

```http
GET /api/v1/assessments/{assessmentId}/rankings
```

Filters: assessmentVersionId, scheduleId.

Response.

```json
{
  "success": true,
  "data": [
    {
      "rank": 1,
      "userId": "<uuid>",
      "displayName": "J*** D***",
      "obtainedMarks": 98.00,
      "percentile": 99.9820
    }
  ],
  "pagination": { "page": 0, "size": 50, "totalElements": 5000, "totalPages": 100 }
}
```

Privacy rules.

| Caller | Name display | User id |
|---|---|---|
| Student | Masked | Hidden |
| Staff or admin | Full | Included |

Public leaderboard requires `rankingsPublic = true` in blueprint configuration.

---

## 100. Error contract

Use the global error envelope.

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": []
}
```

Important P6 business codes.

```text
EVALUATION_NOT_ALLOWED
ATTEMPT_NOT_SUBMITTED
ANSWER_KEY_NOT_FOUND
ANSWER_KEY_NOT_PUBLISHED
ANSWER_KEY_INCOMPLETE
ANSWER_KEY_NOT_FOR_ASSESSMENT
UNSUPPORTED_QUESTION_TYPE
INVALID_RESPONSE_DATA
EVALUATION_ALREADY_EXISTS
RE_EVALUATION_NOT_ALLOWED
RESULT_NOT_FOUND
RESULT_NOT_PUBLISHABLE
SCORECARD_GENERATION_FAILED
SCORING_CONFIGURATION_INVALID
```

Final naming aligns with the platform error catalog.

---

## 101. Result visibility rules

A result moves through calculated, unpublished, and published states.

Students normally see only published results unless the product later explicitly allows provisional results.

---

## 102. Provisional result boundary

Provisional results are not visible to students in the current design.

This is the boundary until a later product decision explicitly changes it.

---

## 103. Result correction

The source mentions controlled result correction but does not define a correction workflow.

P6 does not create a generic `PUT /results/{id}`.

Corrections are represented through re-evaluation with an approved answer key and controlled publication.

---

## 104. Rank recalculation risk

If one student's score changes, rank and percentile may change for other students.

Re-evaluation can have population-wide consequences.

Therefore P6 treats rank and percentile as potentially requiring batch recomputation, publication control, and dependency analysis.

---

## 105. Rank recomputation trigger

Recomputation occurs on result publication and on result re-evaluation.

For large cohorts, rank recomputation runs as a bounded background job scoped to the affected AssessmentVersion.

---

## 106. Percentile recomputation risk

The same as rank. A later evaluated attempt may change the denominator and the population boundary.

Percentile is not treated as a purely local property of one attempt.

---

## 107. Evaluation ordering

For large scheduled assessments, evaluation proceeds asynchronously.

The order of evaluation does not affect per-attempt score.

Rank and percentile finalize at a defined point per AssessmentVersion.

---

## 108. Final score finalization

P6 distinguishes per-attempt calculation complete from cohort ranking and percentile finalized.

The per-attempt Result is persisted independently. The population-level rank and percentile fields are updated when the population finalization runs.

---

## 109. Scoring policy source

Assessment-specific marks and negative marks come from AttemptQuestion.

Additional scoring behavior comes from the approved Blueprint or Assessment configuration, ExamEngine scoring context, and AnswerKeyDetail overrides.

Scoring logic is not hidden inside controller code.

---

## 110. ExamEngine boundary

The ExamEngine defines delivery behavior.

Evaluation does not depend on frontend rendering.

ExamEngine and assessment configuration may expose scoring policy context needed by Evaluation.

The cross-module contract is read-only and version-safe.

---

## 111. QuestionTemplate boundary

Content owns question template, question version, and options.

Evaluation owns response interpretation, comparison to the authoritative answer, and calculation of the outcome.

This separation prevents QuestionBank code from becoming a scoring engine.

---

## 112. Correct answer source

For active student runtime, `question_option.is_correct` is private.

For official evaluation, the evaluator accesses the authoritative answer through `answer_key_detail`.

A student-facing DTO is not reused for scoring.

---

## 113. Answer key versus question option correctness

The source contains `question_option.is_correct` and also defines versioned AnswerKey entities.

P6 uses `answer_key_detail` as the authoritative answer source.

Evaluation never reads `question_option.is_correct`.

---

## 114. Published question immutability

P3 makes published QuestionVersions immutable.

Evaluation safely references the historical QuestionVersion through the snapshot.

QuestionVersion correctness is not mutated to fix an assessment result.

---

## 115. Assessment-specific answer corrections

If one assessment's official answer differs from the underlying QuestionOption correctness, the fix occurs in the AnswerKey layer, not by editing the QuestionVersion used by all assessments.

This is why the AnswerKeyDetail contract is mandatory.

---

## 116. Evaluation detail DTO

Student-safe Result DTO conceptually contains id, attemptId, assessmentVersionId, obtainedMarks, percentage, rank when published, percentile when published, and resultStatus.

Internal calculation metadata is not exposed to students.

---

## 117. Evaluation internals DTO

Authorized staff and evaluation DTO may include evaluationId, attemptId, answerKeyId, evaluatedOn, status, and version history summary.

Detailed per-question outcomes are exposed only through a dedicated approved contract if needed.

---

## 118. Scorecard DTO

Conceptual response.

```text
scorecardId
resultId
status
generatedOn
secureDownloadReference
```

`file_path` is not returned directly.

---

## 119. OpenAPI requirements

OpenAPI describes AnswerKey, AnswerKeyDetail, Evaluation, Result, and Scorecard endpoints, error codes, pagination, publication actions, re-evaluation behavior, and asynchronous scorecard generation.

---

## 120. Validation matrix - AnswerKey

Validate assessment version exists, assessment version published, version number valid, answer mapping complete, template supported, option references valid, no duplicate question mappings, and publication allowed.

---

## 121. Validation matrix - AnswerKeyDetail

Validate answer key exists, question version belongs to the AssessmentVersion, correct option belongs to the same question version, NORMAL outcome has a valid answer, BONUS and DROPPED have null answers, and awarded override values are valid.

---

## 122. Validation matrix - Evaluation

Validate attempt exists, attempt owner and context valid, attempt submitted, snapshot intact, answer key published, answer key detail complete, response data valid, template evaluator available, scoring configuration valid.

---

## 123. Validation matrix - Result

Validate evaluation complete, score calculated, percentage calculated, rank and percentile either valid or intentionally withheld, result status valid, and publication prerequisites satisfied.

---

## 124. Validation matrix - Scorecard

Validate result exists, result is visible or eligible, scorecard generation permitted, secure storage available, and generation does not duplicate the Scorecard record.

---

## 125. Repository projection rules

Use projections for large queries.

Examples.

```text
Result list projection
AnswerKey list projection
Evaluation detail projection
```

Complete object graphs are not loaded unnecessarily.

---

## 126. Evaluation query efficiency

For one attempt, prefer one query for AttemptQuestions, one query for responses, one query for AnswerKeyDetail data, and one query for assessment scoring context.

Or an equivalent bounded set of efficient reads.

N database queries per question are avoided.

---

## 127. Batch result queries

Staff result list supports pagination, assessment filter, version filter, student filter, status filter, and date range.

Database-side filtering and sorting are used.

---

## 128. Index expectations

Existing indexes cover answer_key.assessment_version_id, answer_key_detail.answer_key_id, evaluation.answer_key_id, evaluation.attempt_id, result.rank, result.result_status, and scorecard.result_id.

Additional indexes require evidence from actual query plans and load tests.

---

## 129. Concurrency - Evaluation

Race scenarios.

```text
Two evaluation requests
Evaluate and re-evaluate simultaneously
Evaluate and result publish simultaneously
Duplicate worker delivery
Scorecard generation twice
```

The expected state remains deterministic under concurrency.

---

## 130. Concurrency - Result publication

Two simultaneous publish requests result in one logical publication event.

The second request sees the already-published state or a deterministic conflict, according to the final status contract.

---

## 131. Concurrency - Scorecard generation

Two simultaneous generation requests do not create two Scorecard rows because `UNIQUE(result_id)` exists.

Transaction-safe creation or reuse semantics are used.

---

## 132. Failure injection

P6 tests database timeout during evaluation, worker crash after calculation, worker crash before persistence, answer key publish race, re-evaluation race, scorecard storage failure, PDF generation failure, and result publication retry.

---

## 133. Evaluation reproducibility test

Prepare a fixed test fixture with a published AssessmentVersion, immutable AttemptQuestion set, fixed responses, and a published AnswerKey.

Evaluate twice using the same policy.

Expected: same per-question outcome, same total, same percentage.

This is a release gate.

---

## 134. Negative marking test

Create test cases for all correct, all incorrect, all unanswered, mixed correct and incorrect and unanswered, zero negative marks, non-zero negative marks, question-specific marks, and question-specific negative marks.

Verify exact BigDecimal arithmetic.

---

## 135. Answer key correction test

Create AnswerKey V1 and AnswerKey V2 with a known corrected answer.

Verify V1 remains immutable, V2 is independently identifiable, evaluations reference the exact key used, and re-evaluation behavior follows the approved history policy.

V1 is not modified.

---

## 136. Version reproducibility test

Change QuestionVersion, AssessmentVersion, and Delivery configuration after an attempt is submitted.

Verify that re-reading the attempt and evaluating it still uses the exact historical snapshot and the exact selected AnswerKey.

---

## 137. Result publication test

Verify unpublished result hidden from student, authorized publication succeeds, student can read published result, publication is audited, and duplicate publication is safe.

---

## 138. Rank and percentile test

Test ties, single candidate, multiple schedules, late evaluation, duplicate scores, unpublished results, and cross-organization isolation.

---

## 139. Scorecard test

Verify generate, retrieve, secure access, duplicate generation, storage failure, regeneration behavior, and published result dependency.

---

## 140. Security test - AnswerKey

Students fail to access `GET /answer-keys`, `GET /answer-keys/{id}`, and `POST /answer-keys/{id}/publish` unless their role or permission explicitly grants access.

Answer-key mapping is never returned through student APIs.

---

## 141. Security test - Evaluation

Students do not invoke `POST /attempts/{id}/evaluate` or `POST /attempts/{id}/re-evaluate` unless an explicit product or security rule grants it.

Evaluation endpoints are privileged.

---

## 142. Security test - Result

Student A cannot access Student B result, Student B scorecard, or any unpublished result.

Ownership and publication authorization are enforced.

---

## 143. Organization isolation

All evaluation and result queries are scoped through the organization and security context.

Cross-organization access fails.

UUID obscurity is not authorization.

---

## 144. Audit data protection

Audit records may contain internal operational information.

Students do not see operator notes, internal reasons, private AnswerKey details, or system diagnostics.

---

## 145. Performance model

Primary P6 workloads.

```text
Evaluation throughput
Result listing
Result publication
Scorecard generation
Re-evaluation
```

Evaluation is generally compute and read intensive and can be asynchronous.

---

## 146. Evaluation load test

Use a representative assessment.

```text
5,000 submitted attempts
100 questions each
Mixed question types where supported
Mixed answered and unanswered
Negative marking
```

Measure evaluation throughput, worker latency, DB CPU, DB reads, application CPU, heap, GC, failure rate, and retry rate.

---

## 147. Re-evaluation load test

Simulate a controlled correction affecting a small cohort and a large cohort.

Measure evaluation throughput, result update and publication workload, rank and percentile recomputation impact.

The test does not silently alter published state outside the approved workflow.

---

## 148. Scorecard load test

Test bulk scorecards, single scorecard, duplicate generation requests, and storage latency.

Scorecard generation does not block evaluation transactions.

---

## 149. Background worker capacity

Use bounded concurrency.

Control batch size, worker concurrency, database pool pressure, CPU, memory, and retry backoff.

Final production worker counts are set from measurements.

---

## 150. Caching

Potential cache candidates.

```text
Published assessment metadata
Published AnswerKey metadata
Immutable scoring configuration
```

Cache is not the authoritative source for Evaluation, Result, or publication state.

---

## 151. Observability metrics

Required metrics.

```text
evaluation_requests_total
evaluation_success_total
evaluation_failure_total
evaluation_latency
reevaluation_requests_total
reevaluation_success_total
result_publication_total
result_publication_failure_total
scorecard_generation_total
scorecard_generation_failure_total
scorecard_generation_latency
rank_recomputation_total
rank_recomputation_duration
```

---

## 152. Structured logging

Log requestId, attemptId, evaluationId, answerKeyId, resultId, operation, status, duration, and errorCode.

Never log password, JWT, full answer-key mapping, private student data beyond operational need, or secrets.

---

## 153. Operational dashboards

Minimum P6 dashboard.

```text
Evaluation backlog
Evaluation throughput
Evaluation failures
Re-evaluation activity
Result publication status
Rank recomputation status
Scorecard generation queue
Scorecard failures
DB health
Worker health
```

---

## 154. Alerting

Alert on evaluation failure spike, worker backlog, answer-key validation failures, result publication failures, rank recomputation failures, scorecard generation failure spike, database saturation, and unexpected retry amplification.

---

## 155. Data retention

Evaluation and Result are academic records.

They are not hard-deleted as normal cleanup.

Historical data is archived or retained according to the broader data-retention policy.

---

## 156. Backup and restore

P6 restore verification includes AnswerKeys, AnswerKeyDetails, Evaluations, Results, and Scorecard metadata.

After restore, Evaluation to AnswerKey, Result to Evaluation, and Scorecard to Result relationships remain intact.

---

## 157. Disaster recovery

A recovery test proves that completed evaluations remain readable, published results remain readable, scorecard references remain resolvable, and re-evaluation remains controllable after recovery.

Official records do not depend on in-memory worker state.

---

## 158. Data consistency checks

Periodic operational checks detect Evaluation without valid Attempt, Evaluation without published or valid AnswerKey, Result without Evaluation, Scorecard without Result, and published Result without complete Evaluation.

These checks are read-only validation jobs unless remediation is explicitly authorized.

---

## 159. Orphan prevention

Database foreign keys prevent many orphan relationships.

The application also prevents semantic orphans such as Result with incomplete Evaluation, Scorecard for unpublished or invalid Result, and Evaluation against the wrong AssessmentVersion.

---

## 160. Result publication ordering

Safe order.

```text
AnswerKey published
   |
   v
Evaluation complete
   |
   v
Result calculated
   |
   v
Rank and percentile finalized
   |
   v
Result published
```

Publishing first and calculating later is not allowed.

---

## 161. Evaluation and notifications boundary

P6 may emit an application-level event or trigger a notification workflow after result publication.

P6 does not own email, notification, or announcement delivery.

Use the Communication module later.

---

## 162. Analytics boundary

P6 is the source of official score, percentage, rank, and percentile.

Analytics consumes these values and does not become a second scoring engine.

Analytics may derive accuracy, difficulty, and aggregates from P5 and P6 transactional data.

---

## 163. Pilot scope

P6 pilot scope.

```text
Objective evaluation
Positive marks
Negative marks
AnswerKeyDetail with NORMAL, BONUS, and DROPPED outcomes
Answer key publication
Evaluation
Result
Rank and percentile
Scorecard
Controlled re-evaluation
```

Deferred.

```text
Manual subjective grading
Advanced moderation
AI evaluation
Complex normalization unless explicitly required by pilot
Multi-stage result publication
```

---

## 164. Production readiness blockers closed by design

The design in this document closes the following implementation areas.

```text
AnswerKey mapping persistence via answer_key_detail
Evaluation versioning via evaluation.version and superseded fields
Rank and percentile formula and population
Scoring, rounding, and visibility semantics
Answer key correction via versioned AnswerKey
Re-evaluation without mutating the previous Evaluation
```

---

## 165. No schema invention rule

Do not silently add new tables or columns for P6.

The schema already supports the required behavior through the following tables.

```text
answer_key
answer_key_detail
evaluation
result
scorecard
```

If an implementation requires additional structure, stop and produce a formal amendment.

---

## 166. No API invention rule

Do not silently add new endpoints. The frozen endpoints support the required behavior.

If an implementation requires an additional endpoint, produce a formal amendment.

---

## 167. Evaluation anti-patterns

Reject score calculation in controller, score calculation in frontend, double arithmetic, latest-answer-key lookup, latest-question-version lookup, mutating published AnswerKeys, mutating published Results, duplicate Evaluation rows, duplicate Scorecard rows, N database calls per question, synchronous PDF generation inside the scoring transaction, and cross-module repository access.

---

## 168. P6 implementation task breakdown

```text
P6-01  Create Evaluation module skeleton
P6-02  Map frozen Evaluation entities
P6-03  Map frozen Result and Scorecard entities
P6-04  Implement AnswerKey CRUD
P6-05  Implement AnswerKeyDetail CRUD
P6-06  Implement AnswerKey validation
P6-07  Implement AnswerKey publication
P6-08  Build QuestionEvaluator interface
P6-09  Build evaluator registry
P6-10  Implement objective evaluator
P6-11  Implement negative-marking calculation
P6-12  Implement BigDecimal and rounding policy
P6-13  Implement Evaluation orchestration
P6-14  Implement Evaluation persistence
P6-15  Implement Result creation
P6-16  Implement Result APIs
P6-17  Implement Result publication
P6-18  Implement rank and percentile recomputation
P6-19  Implement re-evaluation
P6-20  Implement scorecard generation
P6-21  Secure scorecard access
P6-22  Implement observability
P6-23  Implement security tests
P6-24  Implement concurrency tests
P6-25  Implement reproducibility tests
P6-26  Implement evaluation load tests
P6-27  Implement scorecard load tests
P6-28  Run pilot end-to-end
P6-29  Produce P6 release evidence
```

Execute one task at a time with checkpoints.

---

## 169. P6-01 to P6-03 acceptance

```text
[ ] Module created
[ ] Entities map to frozen schema
[ ] Repositories use correct indexes
[ ] No cross-module repository access
[ ] No new tables or columns
[ ] CI green
```

---

## 170. P6-04 to P6-07 acceptance

```text
[ ] AnswerKey CRUD
[ ] AnswerKeyDetail CRUD
[ ] AnswerKey version uniqueness
[ ] AnswerKey validation
[ ] AnswerKey publication
[ ] AnswerKeyDetail completeness enforced
[ ] NORMAL requires an answer
[ ] BONUS and DROPPED have null answers
[ ] Published key is immutable
[ ] Security tested
[ ] Audit verified
```

---

## 171. P6-08 to P6-12 acceptance

```text
[ ] QuestionEvaluator interface
[ ] Registry
[ ] Objective evaluator
[ ] Bonus outcome handled
[ ] Dropped outcome handled
[ ] Override marks handled
[ ] Negative marking
[ ] BigDecimal arithmetic
[ ] No floating-point scoring
[ ] Rounding policy
```

---

## 172. P6-13 to P6-18 acceptance

```text
[ ] Evaluation orchestration
[ ] Evaluation persistence
[ ] Version uniqueness enforced
[ ] Partial unique index enforced
[ ] Result creation
[ ] Result APIs
[ ] Result publication
[ ] Rank and percentile recomputation
[ ] Publication audit
[ ] Determinism verified
```

---

## 173. P6-19 to P6-21 acceptance

```text
[ ] Re-evaluation requires explicit answerKeyId
[ ] Re-evaluation creates version N+1
[ ] Previous version superseded
[ ] Previous result marked superseded
[ ] History API works
[ ] Scorecard generation
[ ] Secure scorecard access
[ ] No raw file paths exposed
```

---

## 174. P6-22 to P6-29 acceptance

```text
[ ] Observability
[ ] Security suite passes
[ ] Concurrency suite passes
[ ] Failure injection passes
[ ] Reproducibility verified
[ ] Load tests run
[ ] Pilot end-to-end passes
[ ] Evidence stored
[ ] CI green
```

---

## 175. Recommended implementation prompt

```text
You are implementing P6 Evaluation and Results for the Smart
Competitive Examination Platform.

Read the full specification package before modifying the repository.

P6 owns:
AnswerKey
AnswerKeyDetail
Evaluation
Result
Scorecard
Scoring
Re-evaluation
Result publication
Scorecard generation

NON-NEGOTIABLE:
1.  Do not invent tables.
2.  Do not silently add columns.
3.  Do not silently add endpoints.
4.  Do not mutate published AnswerKeys.
5.  Do not mutate published Results as arbitrary CRUD.
6.  Do not evaluate against latest QuestionVersion.
7.  Do not evaluate against latest AnswerKey unless the caller
    explicitly supplies answerKeyId.
8.  Use the exact submitted Attempt snapshot.
9.  Use BigDecimal for official scoring.
10. Do not use floating-point arithmetic for marks.
11. Do not expose AnswerKey data to students.
12. Do not compute official score in the frontend.
13. Do not create duplicate Evaluation rows.
14. Do not create duplicate Scorecard rows.
15. Do not perform heavy scorecard generation inside the core
    evaluation transaction.
16. Do not access Delivery repositories directly.
17. Use application contracts between modules.
18. Preserve published historical artifacts.
19. Evaluation reads correctness from answer_key_detail.
20. Do not read question_option.is_correct during evaluation.
21. Rank and percentile computed only over published results.
22. Evaluation versioned; re-evaluation creates N+1 and supersedes N.
23. Keep OpenAPI aligned with the frozen contract.
24. Run security, concurrency, reproducibility, failure-injection and
    load tests.
25. Stop and report rather than silently resolving frozen-contract
    gaps.

Task order:
P6-01 through P6-29.

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
```

---

## 176. Code review questions

```text
Which AnswerKey mapping was used?
Is AnswerKeyDetail complete before publication?
Can two evaluation requests race?
What happens on a corrected AnswerKey?
How is historical evaluation reproduced?
What happens after a published result is re-evaluated?
How are ties handled?
How is percentile calculated?
What is the score denominator?
How are negative marks rounded?
Can the score go below zero?
How are unanswered questions handled?
How are unsupported question types handled?
Can a student access answer-key data?
Does scorecard generation block scoring?
Can the same scorecard be generated twice?
Is the partial unique index on current evaluation used?
Does re-evaluation create version N+1 and supersede version N?
```

---

## 177. P6 acceptance - AnswerKey

```text
[ ] CRUD follows frozen API
[ ] Exact AssessmentVersion reference
[ ] Version uniqueness
[ ] Detail completeness
[ ] Publication
[ ] Immutable published key
[ ] Security tested
[ ] Audit verified
```

---

## 178. P6 acceptance - Evaluation

```text
[ ] Submitted attempt required
[ ] Snapshot used
[ ] Exact AnswerKey used
[ ] Question strategies
[ ] Negative marking
[ ] Bonus and dropped outcomes
[ ] Override marks
[ ] BigDecimal arithmetic
[ ] Rounding policy
[ ] Deterministic calculation
[ ] Versioned evaluations
[ ] At most one current evaluation per attempt
[ ] Concurrency safe
[ ] Failure recovery
```

---

## 179. P6 acceptance - Result

```text
[ ] Derived from Evaluation
[ ] Obtained marks
[ ] Percentage
[ ] Rank computed over published results
[ ] Percentile computed over published results
[ ] Visibility rules
[ ] Publication endpoint works
[ ] Publication audited
[ ] Published result immutable by normal CRUD
[ ] Superseded results retained in history
```

---

## 180. P6 acceptance - Scorecard

```text
[ ] Generation
[ ] Secure storage
[ ] Secure retrieval
[ ] Duplicate protection
[ ] Asynchronous path where appropriate
[ ] Storage failure handling
[ ] No raw file paths exposed
```

---

## 181. P6 acceptance - Security

```text
[ ] Student cannot access AnswerKey
[ ] Student cannot evaluate arbitrary attempt
[ ] Student cannot re-evaluate
[ ] Student cannot publish results
[ ] Student sees only own permitted result
[ ] Cross-organization isolation
[ ] Unpublished result protection
[ ] Audit protection
```

---

## 182. P6 acceptance - Performance

```text
[ ] Representative bulk evaluation tested
[ ] Worker throughput measured
[ ] DB load measured
[ ] Heap and GC measured
[ ] Retries measured
[ ] Rank recomputation load measured
[ ] Scorecard load separated
[ ] No N+1 scoring queries
```

---

## 183. P6 acceptance - Historical reproducibility

```text
[ ] Original Attempt snapshot preserved
[ ] Original AnswerKey preserved
[ ] Evaluation references exact AnswerKey
[ ] QuestionVersion changes do not affect past evaluation
[ ] Delivery changes do not affect past evaluation
[ ] Corrected AnswerKey creates a new version
[ ] Re-evaluation creates version N+1
[ ] Previous evaluation retained and queryable
```

---

## 184. P6 release evidence package

Retain migration verification, OpenAPI specification, permission matrix, decision records, AnswerKey validation report, evaluation unit tests, evaluation integration tests, security report, concurrency report, reproducibility report, failure-injection report, bulk evaluation load report, rank recomputation report, scorecard load report, audit verification, known limitations, and rollback plan.

---

## 185. P6 pilot readiness gate

P6 is pilot-ready only when.

```text
[ ] AnswerKeyDetail persistence works
[ ] Objective scoring works
[ ] Negative marking works
[ ] Bonus and dropped outcomes work
[ ] Override marks work
[ ] Deterministic evaluation works
[ ] Evaluation persistence works
[ ] Result persistence works
[ ] Publication works
[ ] Rank and percentile computed
[ ] Scorecard works
[ ] Security tests pass
[ ] Concurrency tests pass
[ ] Historical reproducibility passes
[ ] Re-evaluation history works
[ ] CI green
```

---

## 186. Final engineering principle

> Evaluate the exact attempt that happened, against an explicit immutable AnswerKey, with deterministic arithmetic, and preserve enough historical context to explain the result later.

The authoritative chain.

```text
Submitted StudentAttempt
        |
        v
Immutable attempt snapshot
        |
        v
Explicit AnswerKey version
        |
        v
AnswerKeyDetail set
        |
        v
Deterministic evaluation
        |
        v
Derived result
        |
        v
Controlled publication
        |
        v
Secure scorecard
```

---

## 187. P6 handoff to P7

P6 hands P7 and Analytics the evaluation, result, published result state, obtained marks, percentage, rank, percentile, and question-level outcomes where the approved analytics contract permits.

Analytics remains derived and recalculable.

---

## 188. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P6 — Evaluation and Results.

**Next implementation document:** P7 — Pilot Operations, Scale Validation, and Live Pilot.