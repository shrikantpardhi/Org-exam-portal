# P4 Acceptance Evidence

- **Phase:** P4 — Blueprint and Assessment Management
- **Date:** 2026-10-10
- **Frozen source:** `specs/13-P4-Assessment-V1.5.md §174-§207`

Commands were run and their results recorded, not claimed.

## 1. Verification command set

| Command | Result |
|---|---|
| `cd backend && ./mvnw clean verify` | PASS — 165 tests, 0 failures, 0 errors, BUILD SUCCESS |
| `cd frontend && npm run lint` | PASS — 0 errors |
| `npm run type-check` | PASS |
| `npm test` | PASS — 47 tests, 16 files |
| `npm run build` | PASS — `/blueprints`, `/assessments`, `/assessments/[id]` built |

Assessment backend tests: migration (5) + API/verifications (6) = 11.

## 2. The three requested verifications

**Rule materialization is deterministic and idempotent** — `AssessmentApiIntegrationTest.ruleMaterializationIsDeterministicAndIdempotent`:
- Two assessment versions with the same rule configuration over the same published pool materialize
  the **same** question-version set (id-ascending tie-break).
- Re-running materialization leaves the set **unchanged** (rules with `materialized_on` are skipped).

**Publication blocks incomplete assessments** — `AssessmentApiIntegrationTest`:
- `publicationIsBlockedWhenTheAssessmentIsIncomplete` → `/validate` returns `valid=false` with
  `QUESTION_COUNT_MISMATCH`; `/publish` returns **422**.
- `publicationIsBlockedWithoutAnEngineType` → `valid=false`, `/publish` **422**.

**Engine type is immutable after publication** — `AssessmentApiIntegrationTest.engineTypeIsImmutableAfterPublication`:
- After publish, `AssessmentVersionService.update(...)` with a new engine type throws
  `InvalidStateException`; the version still reports `CUSTOM`. (The frozen contract exposes no
  version-mutation endpoint, so the guard is at the service boundary.)

## 3. Golden path and historical reproducibility (P4-200, P4-202)

`theGoldenPathPublishesADeterministicAssessment`: publish a blueprint version → create an assessment
version (engine type `CUSTOM`, delivery policy) → materialize rules → validate (`valid=true`) →
preview → publish (`published=true`, `totalQuestions=2`). The published version still binds the exact
`blueprintVersionId`; re-materialization on the published version returns **409**.

## 4. Acceptance checklists

`§175-§196`: module skeleton; schema verification (9 tables + delivery_policy, constraints, indexes);
blueprint CRUD and versions; typed configuration; blueprint validation and publication; assessment
CRUD; assessment versions; assessment questions; selection rules and materialization; validation;
preview; publication; scheduling; collections; frontend; security and audit — implemented and covered.

## 5. Known limitations

See [`docs/known-gaps.md`](../../known-gaps.md). P4-relevant: delivery_policy created ahead of P5
(GAP-040), `examId` assessment filter unsupported (GAP-041), shortage detail conveyed in the error
message (GAP-042), engine immutability has no API mutation path (GAP-043), schedule close maps to
`INACTIVE` (GAP-044), migration numbering V8/V9/V10 (GAP-045), no Start Exam/snapshot by design
(GAP-046), LRU tie-break approximated (GAP-047), permission names as candidates (GAP-048), the lean
P4 frontend / missing builders (GAP-049), no archive endpoints (GAP-050), blueprint `language` ids not
verified (GAP-051), blueprint publication not required before assessment publication (GAP-052), P4
performance not measured (GAP-053), no assessment Redis cache (GAP-054). CI is still absent
(GAP-008); local verification is green.
