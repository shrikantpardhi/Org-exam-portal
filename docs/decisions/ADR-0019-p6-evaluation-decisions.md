# ADR-0019 — P6 Evaluation: scoring, versioning, and publication decisions

- **Status:** Accepted
- **Phase:** P6 — Evaluation, Results, and Scorecards
- **Spec:** `specs/15-P6-Evaluation-V1.5.md`
- **Supersedes:** none
- **Preserves:** the frozen Evaluation schema, the `answer_key_detail` correctness contract, and the
  frozen API surface.

## Context

P6 converts a submitted `StudentAttempt` into an authoritative evaluation, a derived result, and a
published scorecard. The frozen schema (`answer_key`, `answer_key_detail`, `evaluation`, `result`,
`scorecard`) and the API contract already fix the tables, columns, uniqueness rules, and endpoints.
This ADR records the implementation decisions the specification left to the implementation layer. No
frozen contract is changed.

## Decisions

1. **Correctness source.** Evaluation reads correctness only from `answer_key_detail`
   (`specs/15-P6 §112, §113`). `question_option.is_correct` is never read during evaluation; the
   option identity needed to interpret a response is taken from the student-safe `ContentCatalog`
   runtime projection, which does not carry correctness.

2. **Response interpretation.** The runtime client persists the selected option key as the response
   (`frontend .../exam-runner.tsx`). Option-based templates (`SINGLE_CHOICE`, `MULTIPLE_CHOICE`,
   `TRUE_FALSE`) therefore match the normalized response against the authoritative option's key or
   its option id. A response that names no known option of the question is an explicit
   `INVALID_RESPONSE` and fails the evaluation rather than being silently scored as incorrect
   (`§67, §72`). `INTEGER`/`DECIMAL` compare numerically with `BigDecimal.compareTo`; the only
   `FILL_IN_THE_BLANK` normalization is trimmed, whitespace-collapsed, case-insensitive equality
   (`§22, §23`). No other normalization is invented.

3. **Marks policy.** Marks and negative marks come from the immutable `attempt_question` snapshot,
   superseded by `answer_key_detail.awarded_marks` / `awarded_negative_marks` when present (`§15,
   §21`). `BONUS` awards the configured marks regardless of the response; `DROPPED` contributes zero
   and ignores the response (`§19, §20`). The total is never clamped at zero (`§17`).

4. **Rounding.** Every official number is a `BigDecimal`; no binary floating point is used (`§33,
   §74`). Per-question awards keep full precision; `obtained_marks` and `percentage` are HALF_UP to
   2 decimals and `percentile` is HALF_UP to 4 decimals (`§32`). `percentage = obtained_marks /
   published_total_marks * 100`; the denominator is never inferred from the answered count (`§34`).

5. **Evaluate-once.** A first evaluation inserts an `evaluation` row with `version = 1` and a
   `CALCULATED` result. A repeated request returns the existing current evaluation instead of
   creating a second row; a genuine concurrent race is resolved by the partial unique index
   `UNIQUE(attempt_id) WHERE superseded_by_id IS NULL` and surfaced as a 409 conflict (`§44, §45,
   §129`).

6. **Re-evaluation.** Re-evaluation requires an explicit, published `answerKeyId` and creates
   version N+1 while superseding version N (`superseded_by_id`, `superseded_on`,
   `superseded_reason`, status `SUPERSEDED`) and superseding the previous result (`§46-§52, §56`).
   Because the partial unique index permits only one current row, the previous version is first
   released from the current slot (a transient self-reference that is overwritten before commit),
   then version N+1 is inserted, then the supersede pointer is finalised. The exact historical chain
   remains queryable through `GET /attempts/{id}/evaluations`.

7. **Rank and percentile.** The population is the current (non-superseded) evaluation of every
   attempt bound to the same assessment version with a `PUBLISHED` result (`§35, §36`). Rank uses
   standard competition ranking; `percentile = count(score <= mine) / total * 100`, HALF_UP to 4
   decimals. Recomputation runs on publication and on re-evaluation (`§105`). It is serialized by a
   pessimistic row lock over the evaluation-owned answer keys of the version, which avoids reaching
   into the Assessment-owned `assessment_version` table while still serializing per version (`§38`).

8. **Publication.** Publication is a state transition with authorization, state validation, and
   audit; the score is never recalculated (`§54, §85`). A result is only published from `CALCULATED`
   or `READY_FOR_PUBLICATION`; republishing is idempotent. Ranks are recomputed in the same
   transaction batch so a published result always carries a rank (`§160`).

9. **Scorecard.** Generation is a presentation artifact written outside the scoring transaction
   (`§57, §84`). It requires a published result, is business-idempotent per result via
   `UNIQUE(result_id)` (`§60`), and stores only a private storage reference. Callers receive a
   secure reference, never a filesystem path (`§58`).

10. **Module boundaries.** Evaluation never accesses Delivery, Assessment, Content, or Identity
    repositories directly (`§64`). It consumes narrow application contracts: `AttemptEvaluationCatalog`
    (Delivery), `AssessmentDefinitionCatalog` (Assessment plumbing context), `ContentCatalog`
    (Content), and `UserDirectory` (Identity display names). The evaluation input is read-only;
    P6 does not mutate the attempt snapshot, responses, or the attempt lifecycle.

11. **Asynchronous handoff.** The durable `background_job` handoff is implemented as an
    `EvaluationWorker`, but it requires an explicit `answerKeyId` in the job parameters because
    `§26` forbids defaulting to a "current published key". The P5 submission handoff carries no answer
    key, so the worker is disabled by default and jobs without a key are failed with
    `ANSWER_KEY_REQUIRED` (see `docs/known-gaps.md` GAP-071). The synchronous, explicit
    `POST /attempts/{id}/evaluate` is the supported staff path.

## Consequences

- Evaluation is deterministic and reproducible for identical inputs and policy (`§31`); the
  reproducibility, negative-marking, override, bonus, dropped, re-evaluation, and rank/percentile
  behaviours are covered by unit and integration tests.
- The multi-correct `MULTIPLE_CHOICE` case is not representable in the frozen `answer_key_detail`
  schema (one `correct_option_id`), recorded as GAP-072.
- Durable audit persistence remains deferred to Administration (GAP-001); P6 emits structured
  `SECURITY` events in the business transaction.
- Load/performance evidence (5,000-attempt throughput) is not produced on the development host;
  recorded as GAP-076.

## Contract gaps discovered

Recorded in `docs/known-gaps.md` (GAP-070 … GAP-082). None was silently resolved; none changes a
frozen table, column, or endpoint.
