# P6 — Evaluation, Results, and Scorecards: Testing Notes

Phase specification: `specs/15-P6-Evaluation-V1.5.md`.

## Scope verified

- `answer_key` / `answer_key_detail` CRUD, completeness, publication, and immutability.
- Objective evaluation for option-based, numeric, and short-text templates.
- Bonus, dropped, and marks/negative-marks override handling.
- `BigDecimal` scoring with the HALF_UP rounding policy; the total is not clamped at zero.
- Evaluation versioning (`UNIQUE(attempt_id, version)`) and the partial unique index
  (`UNIQUE(attempt_id) WHERE superseded_by_id IS NULL`).
- Controlled re-evaluation (version N+1 supersedes version N; the previous result is superseded).
- Rank and percentile recomputation over the published population, including ties.
- Result publication, visibility, and the student/staff access boundary.
- Scorecard generation, idempotency, and secure reference.

## Unit tests

| Test | What it proves |
|---|---|
| `evaluation/strategy/OptionBasedQuestionEvaluatorTest` | correct/incorrect/unanswered/unknown-option, bonus, dropped, override, zero negative marks |
| `evaluation/strategy/NumericAndTextEvaluatorTest` | scale-insensitive numeric compare, invalid response, invalid key answer, text normalization |
| `evaluation/scoring/EvaluationCalculatorTest` | aggregation, HALF_UP rounding, percentage, unclamped negative totals, determinism, zero-total rejection, invalid-response failure |
| `evaluation/scoring/RoundingPolicyTest` | 2-decimal marks/percentage and 4-decimal percentile HALF_UP |
| `evaluation/dto/EvaluationLeakageTest` | student projections carry no answer-key or storage-path fields |

Run: `cd backend && ./mvnw test -Dtest="*EvaluatorTest,EvaluationCalculatorTest,RoundingPolicyTest,EvaluationLeakageTest"`.

## Integration tests

Base: `integration/evaluation/AbstractEvaluationIntegrationTest` (extends the P5 delivery harness,
so attempts are created, started, answered, and submitted through the real endpoints).

| Test | What it proves |
|---|---|
| `EvaluationMigrationIntegrationTest` | tables, uniqueness/outcome/status constraints, indexes, partial unique index predicate, permission seed |
| `EvaluationApiIntegrationTest` | answer-key CRUD, publication completeness, immutability after publish, mixed scoring, bonus/dropped, override, evaluate-once, rejection paths |
| `ResultVisibilityIntegrationTest` | unpublished results hidden from students, publication visibility, rank 1 / percentile 100 for a single candidate, idempotent publication, scorecard requirement + idempotency + no path leak |
| `RankPercentileIntegrationTest` | competition ranking with ties (1, 1, 3, 4) and percentiles (100, 100, 50, 25) over the published population |
| `ReEvaluationIntegrationTest` | version N+1, supersession of version N and its result, history ordering, determinism across versions |
| `EvaluationSecurityIntegrationTest` | students denied answer-key, evaluation, re-evaluation, publication, and staff listing; cross-student result isolation; staff access |

Run: `cd backend && ./mvnw test -Dtest="*Evaluation*IntegrationTest,ResultVisibilityIntegrationTest,RankPercentileIntegrationTest,ReEvaluationIntegrationTest"`.

## Manual/curl smoke

1. Publish an assessment version and open a schedule (P4/P5).
2. `POST /api/v1/answer-keys` then `POST /api/v1/answer-keys/{id}/details` for every question, then
   `POST /api/v1/answer-keys/{id}/publish`.
3. Start, answer, and submit an attempt as the candidate (P5).
4. `POST /api/v1/attempts/{attemptId}/evaluate` with the explicit `answerKeyId`.
5. `POST /api/v1/results/{resultId}/publish`, then `GET /api/v1/attempts/{attemptId}/result`.
6. `POST /api/v1/results/{resultId}/scorecard`, then `GET .../scorecard`.
7. `POST /api/v1/attempts/{attemptId}/re-evaluate` with a corrected key; `GET
   /api/v1/attempts/{attemptId}/evaluations` shows both versions.

## Not exercised on the development host

- 5,000-attempt evaluation throughput and rank-recomputation load (no staging data/host) — GAP-076.
- Concurrent publication of many results in the same assessment version (covered in-process by the
  advisory row lock, not by a staged multi-node burst).
- Scorecard PDF generation and byte delivery (the pilot writes an HTML artifact and returns a secure
  reference) — GAP-075.
