# ADR-0017: P4 blueprint and assessment decisions

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P4 — Blueprint and Assessment Management
- **Frozen source:** `specs/13-P4-Assessment-V1.5.md`, `specs/04-Database-Schema-V1.5.md §28`,
  `specs/05-API-Contract-V1.5.md §36-§43`, `specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md`

## Context

P4 turns reusable published content into a deterministic, immutable, schedulable examination
definition. The frozen schema and API contract leave several implementation choices open.

## Decisions

**1. delivery_policy prerequisite.** `assessment_version.delivery_policy_id` FKs to `delivery_policy`,
which is owned by the Delivery domain and not yet implemented. Migration `V8` creates the frozen
`delivery_policy` table (exact columns, `04-Schema §29.6`) so the frozen FK holds; P4 only reads it
through the `DeliveryPolicyCatalog` contract. Recorded as GAP-040.

**2. Exact QuestionVersion binding.** `assessment_question.question_version_id` references an exact
`question_version` and is `UNIQUE(assessment_version_id, question_version_id)`. Latest-version
resolution never occurs; historical reproducibility depends on the exact id (`§24, §25`).

**3. Deterministic, idempotent materialization.** Rule materialization orders candidates by difficulty
(EASY → MEDIUM → HARD) then question version id ascending, excludes versions already used in the
version, and persists `target_count` rows. Rules with `materialized_on` set are skipped on re-run, so
materialization is idempotent; the same pool and rules always produce the same set (`§46-§48`). The
spec's "least-recently-used in prior assessments" tie-break is approximated by id ascending for the
pilot (GAP-047).

**4. Publication gate.** Publication is an explicit validating action; it is blocked while the
assessment is incomplete (count/section mismatch, unpublished questions, missing engine type,
missing delivery policy, invalid duration) and recomputes totals atomically (`§53, §75, §84`).

**5. Engine type immutability.** The frozen contract exposes no assessment-version mutation endpoint,
so engine type and delivery policy cannot be changed through the API after creation. Immutability is
additionally enforced in `AssessmentVersionService.update`, which rejects any change to a published
version, and verified at the service boundary (GAP-043).

**6. Typed blueprint configuration.** Blueprint JSONB is parsed into a typed record
(`BlueprintConfiguration`) using the platform ObjectMapper, which rejects unknown fields — so
`javascript`, `renderScript`, or `component` cannot be smuggled in and no per-section table is added
(`§9-§12`).

**7. Manual, rule-based, and hybrid authoring** are all expressed as concrete `AssessmentQuestion`
rows: manual = add-question API; rule-based = rule + materialize; hybrid = both. Delivery never
re-runs authoring rules (`§31-§34`).

**8. Schedule and collection.** A schedule binds a published AssessmentVersion to a window and never
follows a later version; `mode` is fixed at creation. "Close" maps to `INACTIVE` (the standard
lifecycle) because no second persistent status field is created (GAP-044). Collections group logical
assessments (`§67-§77`).

**9. No runtime execution.** P4 contains no Start Exam, no snapshot creation, and no StudentAttempt
or AttemptQuestion. Delivery prepares snapshots asynchronously in P5 (`§128-§130`).

**10. Permissions.** The `BLUEPRINT_*`/`ASSESSMENT_*` codes from `§145` are seeded as implementation
candidates and granted by role (ADMIN all; STAFF authoring; REVIEWER read-only) (GAP-048).

## Consequences

- The frozen schema and API surface are preserved; only the Delivery prerequisite table is added
  early, and it is documented.
- Published BlueprintVersions and AssessmentVersions are immutable, deterministic, and reproducible.
- P5 can consume a published AssessmentVersion without modifying P4-owned records.
