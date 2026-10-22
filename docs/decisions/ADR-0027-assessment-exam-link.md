# ADR-0027 — The assessment `examId` filter: how an assessment is linked to an exam

- **Status:** Accepted
- **Phase:** P4 — Assessment & Blueprint
- **Spec:** `specs/13-P4-Assessment-V1.5.md` §45, §46, §64, §95; `specs/04-Database-Schema-V1.5.md` §27.2, §28.1,
  §28.4, §28.5, §28.7, §28.8, §28.10
- **Closes:** GAP-041

## Context

`13-P4 §64` and `§95` both name the assessment list filters: **status, examId, blueprintId, search**. The
frozen `assessment` table (`04-Schema §28.4`) has no exam column:

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

Because no path was approved, `GET /api/v1/assessments?examId=…` was implemented as a controlled **400
`UNSUPPORTED_FILTER`** rather than silently ignored. The filter is required by the contract
(`§95`: "Additional filters require explicit contract approval", which implies the four named ones are
mandatory), so the 400 is a contract gap, not a feature.

### What the frozen schema actually contains

An exhaustive check of every `exam_id` column in the schema (`V4`, `V6`, `V9` … `V26`) finds it on
exactly three tables, none of them owned by Assessment:

| Table | Owner | `exam_id` |
|---|---|---|
| `subject` | Academic | FK → `exam` |
| `question` | Content | FK → `exam` |
| `previous_exam_history` | Content | FK → `exam` |

and **nowhere** in `assessment`, `assessment_version`, `assessment_question`, `assessment_schedule`,
`assessment_collection`, `collection_assessment`, `blueprint` or `blueprint_version`.

The Assessment module does, however, hold the exam link itself: the selection rule that scopes a
version's question pool carries `examId` in its `rule_configuration` JSONB
(`04-Schema §28.7`, `§28.8`; `13-P4 §45`). That field is not decoration — it is what the platform
acts on: `QuestionSelectionService` passes it to the Content contract as
`ContentCatalog.SelectionCriteria.examId`, and Content selects the eligible published question
versions by `question.exam_id` (`13-P4 §46`).

## The options, and the impact of each

### Option 1 — Remove the filter

`GET /api/v1/assessments` would silently ignore `examId`, or the parameter would be dropped from the
contract.

- **Impact:** the API no longer matches `13-P4 §64` and `§95`. A caller that passes `examId` gets a
  *complete* assessment list back rather than a filtered one — a silent wrong answer, which is worse
  than the current explicit 400. It also edits a frozen API contract.
- **Rejected:** it changes the frozen contract to hide a required filter.

### Option 2 — Add an `exam_id` column to `assessment`

- **Impact:** a schema amendment to a frozen table (`04-Schema §28.4`), which requires a formal
  amendment with an approval gate before any implementation code (`AGENTS.md` rule 3;
  `06-Architecture-Freeze §19`). It would also introduce a **second source of truth**: the stored
  column could contradict the selection rules that actually scope the version. Worse, a single
  column cannot even represent the truth — a version may draw from several exams (one rule per
  section, `UNIQUE(assessment_version_id, section_name)`), or from none (a rule scoped by
  `subjectId`/`tagIds` only, `13-P4 §45`). The column would be a lossy summary of data the platform
  already stores.
- **Rejected:** a schema amendment plus a contradictory, lossy duplicate of existing data.

### Option 3 — Resolve the link through the blueprint

`assessment` → `assessment_version` → `blueprint_version` → `blueprint`.

- **Impact:** none, because **this is not a path.** The `blueprint` table (`04-Schema §28.1`) has no
  exam column, and the blueprint configuration shape (`§28.3`; `BlueprintConfiguration`) has no exam
  field — it carries sections, navigation, timer, submission, validation, presentation, calculator
  and language. There is nothing to resolve.
- **Rejected:** not implementable; the option rests on a false premise.

### Option 4 — Resolve the link through the assessment-collection path

`assessment` → `collection_assessment` → `assessment_collection`.

- **Impact:** none, because **this is not a path either.** `assessment_collection`
  (`04-Schema §28.10`) has no exam column. The collection is the *commerce* bundle
  (`18-P9 §3.3`: Assessment → AssessmentCollection → AccessPackage → SubscriptionPlan); it carries
  no academic taxonomy.
- **Rejected:** not implementable; the option rests on a false premise.

### Option 5 — Resolve the link through the selection-rule provenance

`assessment` → `assessment_version` → `assessment_selection_rule`, matching
`rule_configuration->>'examId'`.

- **Impact:** the filter returns the assessments that were **authored against** an exam — the
  assessments whose versions carry a selection rule scoped to that exam. This is the exam link the
  platform actually records and acts on, so it is the link the filter is asking about.
- **Cost:** the exam id lives in a JSONB column, so it is not directly indexable. The read is
  therefore two bounded steps rather than one joined query: an indexed scan of
  `assessment_selection_rule` joined to `assessment_version` (`idx_assessment_selection_rule_version`,
  `idx_assessment_version_assessment_id`), and then the resulting assessment id set restricts the
  existing paginated query. The id set is bounded by the number of assessments authored for one
  exam, and the page query keeps its own pagination.
- **Rejected for a different reason than the others: none.** It is the only option that is both
  implementable and faithful to the frozen data.

## The chosen option

> **Resolve the link through the selection-rule provenance**: an assessment matches `examId=X` when one
> of its assessment versions carries a selection rule whose `rule_configuration` names `X`.

### Reasoning

1. **It is the link the platform actually records.** `13-P4 §45` defines the selection rule as the
   scope of a version's question pool, and `§46` materializes exactly that pool. The `examId` in the
   rule is what Content filters `question.exam_id` by. Filtering on the same field answers the same
   question the authoring flow asks.
2. **It is provenance, and the spec says so.** `04-Schema §28.8`: "Once materialized into
   `assessment_question` rows, the rule is provenance and is not re-executed at runtime." The rule is
   the authoritative *record* of where a version's content came from; the materialized rows are the
   outcome of acting on it.
3. **It needs no schema amendment.** No table, no column, no migration, no new endpoint, and no new
   cross-module contract: every table on the path is Assessment-owned, and the exam id is only ever
   an opaque uuid inside Assessment's own JSONB. The Academic module is not touched at all.
4. **It cannot create a second source of truth.** Nothing is stored; the filter reads the same field
   the platform uses.
5. **It is stable.** An assessment that has been authored against an exam but not yet materialized
   still matches, and the answer does not change when a version is re-materialized.

### Alternatives that were considered and are not the choice

- **The materialized-content path** (`assessment_question` → `question_version` → `question.exam_id`)
  is the only *materialized* route to an exam, and it would also catch a version whose rule is scoped
  by `subjectId` alone. It was rejected because `question` is owned by **Content**: answering it from
  Assessment would mean either reaching across a module boundary or adding a Content contract, and
  it would make the filter depend on materialization state — an assessment that declares exam X but
  has not been materialized would not match. The selection rule is the better-defined relationship.
- **Adding an `exam_id` column** — see Option 2 above.

### What the rule deliberately does not cover, and the operator mitigation

| Residual | Why | Mitigation |
|---|---|---|
| A version whose rule is scoped by `subjectId`/`tagIds` only (no `examId`) does not appear under any exam filter, even though its questions belong to an exam | The rule never declared an exam; the platform has no declared link to filter on | The rule may declare `examId` explicitly; the Content module remains the way to find questions by exam |
| A version may name a different exam per section rule | One rule per section (`UNIQUE(assessment_version_id, section_name)`) | An assessment matches if **any** of its versions' rules names the exam |
| The exam id is stored as JSONB text, so it is not indexable | The frozen rule configuration shape stores it as a scalar uuid string | The read is an indexed join plus a JSONB extraction over the rules of the matching versions only. A GIN/expression index would remove the extraction cost, but that is DDL and needs its own amendment |

## Consequences

- The filter named by `13-P4 §64` and `§95` works, and the API no longer answers a documented filter
  with a 400.
- Nothing was stored and nothing was duplicated: the filter reads the field the platform already
  acts on.
- An `examId` that matches no rule returns an empty page, not an error — the same shape as any other
  filter that finds nothing.

## Implementation and evidence

| Concern | Where |
|---|---|
| The exam-scoped assessment ids | `AssessmentRepository.findAssessmentIdsBySelectionRuleExamId` |
| The filter | `AssessmentService.list` / `AssessmentService.specification` |
| The contract | `13-P4 §64`, `§95` — unchanged |
| The evidence | `backend/src/test/java/com/company/exam/integration/assessment/AssessmentExamFilterIntegrationTest.java` |

Full backend suite after this change: **388 tests, 0 failures**.

## Related

- `docs/known-gaps.md` — GAP-041 (closed by this record); GAP-056 (Delivery permission codes).
- ADR-0017 — P4 decisions; ADR-0026 — the paid-content entitlement gate (the other closed P4/P9
  contract gap).
