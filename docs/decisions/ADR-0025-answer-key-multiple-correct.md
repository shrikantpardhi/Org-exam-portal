# ADR-0025 — Answer key: multiple correct options for MULTIPLE_CHOICE

- **Status:** Accepted
- **Phase:** P6 — Evaluation, Results, and Scorecards
- **Spec:** `specs/15-P6-Evaluation-V1.5.md` (§5.2, §22), `specs/04-Database-Schema-V1.5.md` (§30.2)
- **Amendment to:** ADR-0019 (P6 evaluation decisions); closes GAP-072
- **Supersedes:** none (additive)

## Problem

`answer_key_detail` stores a single `correct_option_id` (and a single `correct_answer_text`). A
`MULTIPLE_CHOICE` question whose authoritative answer contains more than one correct option cannot
be represented, so such a question can only be keyed against one option. This is recorded as
GAP-072 and is the only reason `MULTIPLE_CHOICE` multi-correct questions are not evaluable today.

## Proposed schema change

Add one new child table, `answer_key_detail_option`, and no change to any other table:

```sql
CREATE TABLE answer_key_detail_option (
    id                    UUID NOT NULL,
    answer_key_detail_id  UUID NOT NULL,
    question_option_id    UUID NOT NULL,
    CONSTRAINT pk_answer_key_detail_option PRIMARY KEY (id),
    CONSTRAINT uk_answer_key_detail_option_pair UNIQUE (answer_key_detail_id, question_option_id),
    CONSTRAINT fk_answer_key_detail_option_detail FOREIGN KEY (answer_key_detail_id)
        REFERENCES answer_key_detail (id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_key_detail_option_option FOREIGN KEY (question_option_id)
        REFERENCES question_option (id)
);

CREATE INDEX idx_answer_key_detail_option_detail_id ON answer_key_detail_option (answer_key_detail_id);
CREATE INDEX idx_answer_key_detail_option_option_id ON answer_key_detail_option (question_option_id);
```

- `answer_key_detail.correct_option_id` is retained for single-correct and free-text keys; it remains
  the source when no child rows exist, so single-correct behaviour is unchanged.
- When one or more child rows exist, the child set is the authoritative correct option set.
- The child table is additive; no column of `answer_key_detail` is removed or altered.

## Impact on the API

The existing `AnswerKeyDetail` endpoints are extended (no new endpoint):

- `AnswerKeyDetailRequest` gains `correctOptionIds: UUID[]` (the correct option set for multi-correct
  questions). `correctOptionId` and `correctAnswerText` remain.
- `AnswerKeyDetailResponse` gains the same `correctOptionIds: UUID[]`.
- Validation: for `NORMAL`, at least one of `correctOptionId`, a non-blank `correctAnswerText`, or a
  non-empty `correctOptionIds` is required; every id in `correctOptionIds` must belong to the same
  `questionVersionId`. `BONUS`/`DROPPED` require all three to be null/empty.

## Impact on evaluation

- The evaluator resolves the authoritative correct set from the child rows when present, otherwise
  from `correct_option_id` (single) / `correct_answer_text` (free text).
- `OptionBasedQuestionEvaluator` compares the student's selected option set against the authoritative
  set:
  - empty selection -> UNANSWERED (0 marks)
  - exact set match -> CORRECT (full marks)
  - any other non-empty selection -> INCORRECT (negative marks applied)
  - a selection naming an unknown option -> INVALID_RESPONSE (fails evaluation)
- Scoring policy is all-or-nothing by default: a partially correct multi-select response scores 0
  (incorrect), not a proportional mark. This default is documented here and enforced by the
  evaluator; no proportional/partial-credit formula is introduced (consistent with `§22`, which
  forbids inventing a universal multi-select formula).

## Migration number

`V26__create_answer_key_detail_option.sql` (next free number after `V25`).

## Rollback strategy

- Database: `DROP TABLE answer_key_detail_option;` (additive change; no other table or column is
  touched, so rollback restores the prior schema exactly). Any multi-correct keys created after
  migration are lost on rollback.
- Application: revert the entity/repository/service/DTO/mapper/evaluator changes; `correct_option_id`
  remains the single-correct source, so previously stored single-correct keys keep working.
- No data migration of existing rows is required because the child table starts empty and
  single-correct keys continue to use `correct_option_id`.

## Approval

Approved — Stage 2 implemented (migration V26, entity, repository, service, API, evaluator, tests).
See `docs/known-gaps.md` (GAP-072, Resolved) and `docs/implementation-status.md`.