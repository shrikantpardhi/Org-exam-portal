# P2 Acceptance Evidence

- **Phase:** P2 — Academic Structure
- **Date:** 2026-10-10
- **Frozen source:** `specs/11-P2-Academic-V1.5.md §123-§131`

Commands were run and their results recorded, not claimed.

## 1. Verification command set

| Command | Result |
|---|---|
| `cd backend && ./mvnw clean verify` | PASS — 124 tests, 0 failures, 0 errors, BUILD SUCCESS |
| `cd frontend && npm run lint` | PASS — 0 errors, 0 warnings |
| `npm run type-check` | PASS |
| `npm test` | PASS — 38 tests, 13 files |
| `npm run build` | PASS — academic routes `/academic`, `/academic/{exams,subjects,chapters,topics,sub-topics,languages,tags}` built |
| Migration validation | PASS — `AcademicMigrationIntegrationTest` |
| OpenAPI | PASS — every Academic operation annotated (`@Operation`, path/query/body via DTOs) |
| End-to-end | PASS — `AcademicApiIntegrationTest` (full hierarchy) + frontend hierarchy/form tests |

Academic backend tests: `AcademicMigrationIntegrationTest` (7) + `AcademicApiIntegrationTest` (14) = 21.

## 2. The three requested verifications

**Parent-child integrity** — `AcademicApiIntegrationTest`:
- `creatingASubjectUnderAMissingExamIsNotFound` → 404 (`Exam not found`).
- `creatingAChildUnderAnArchivedParentIsRejected` → 409 `PARENT_NOT_ELIGIBLE`.
- `creatingAChildUnderAnInactiveParentIsRejected` → 409 `PARENT_NOT_ELIGIBLE`.
- `AcademicMigrationIntegrationTest.orphanChildIsRejectedByTheForeignKey` → DB FK rejects an orphan.

**Archive does not cascade** — `AcademicApiIntegrationTest`:
- `archivingAParentWithActiveDescendantsIsRejectedAndDoesNotCascade` → 409
  `ACADEMIC_DESCENDANTS_EXIST`, and both the exam and the subject remain `ACTIVE` (nothing changed).
- `aChainCanBeArchivedBottomUpAndALeafArchivesDirectly` → each level archives 204 only after its
  children are archived; no operation archives descendants.

**Hierarchy loads lazily** — `academic-hierarchy.test.tsx`:
- On render only `listExams` is called; `listSubjects` is **not** called.
- Expanding an exam triggers exactly one `listSubjects(examId)` call — one level at a time.
- Backend: `childListsReturnOnlyDirectChildrenOfTheRequestedParent` proves each list endpoint returns
  only direct children (no tree preload / N+1).

## 3. Scoped code uniqueness — `AcademicMigrationIntegrationTest`, `AcademicApiIntegrationTest`

- `subjectCodeIsUniquePerExamButReusableAcrossExams` (DB) and `subjectCodeIsScopedToItsExam` (API):
  the same code under a different exam is allowed; duplicate within one exam is 409.
- `duplicateExamCodeIsConflict` / `duplicateExamCodeIsRejected` cover global exam-code uniqueness.

## 4. Acceptance checklists

Backend (`§124`): Exam/Subject/Chapter/Topic/SubTopic/Language/Tag CRUD; parent + parent-lifecycle
validation; scoped code uniqueness; DB constraints and indexes verified; standard envelopes;
pagination/filtering/sorting; OpenAPI; JWT + RBAC; audit events; unit + integration + API tests — all
implemented and covered.

Database (`§126`): all seven tables exist; every unique constraint, foreign key, and index present
(`AcademicMigrationIntegrationTest`); Flyway owns the schema.

Security (`§127`): authenticated access; RBAC; backend authorization; students cannot mutate masters
(`aStudentCannotReadOrMutateAcademicMasters`); malformed IDs rejected (400); invalid input rejected;
arbitrary sort field blocked (400); parameterized queries; no secrets in logs; audit actor captured.

Frontend (`§125`): workspace loads; per-resource routes; parent filters; create/edit/archive
confirmation; server validation displayed; permission-based action visibility; loading/empty/error
states; query invalidation; responsive layout; end-to-end tests.

## 5. Known limitations

The consolidated register is [`docs/known-gaps.md`](../../known-gaps.md). The P2-relevant items:

- **Audit persistence** — `audit_log` lands with Administration (frozen migration `V11`); P2 emits
  structured audit events (GAP-001).
- **Migration numbering** — Academic is `V4`/`V5`, not the recommended `V3`/`V13` (GAP-020).
- **Permission names** — the `EXAM_*`…`TAG_*` codes are spec "implementation candidates" pending
  Identity catalog alignment (GAP-021).
- **Single mapper** — one `AcademicMapper` rather than seven classes (GAP-022).
- **No Academic Redis cache** in this slice (GAP-023).
- **No seed vocabulary** — languages/tags are created by the pilot, not seeded (GAP-024).
- **Conflict-only archive** — no approved controlled cascade (GAP-025).
- **No tree endpoint** — hierarchy is read one level at a time (GAP-026).
- **Performance/observability not measured on staging data** (GAP-027).
- **Generated OpenAPI not retained as an artifact** (GAP-028).
- **`§25` repository queries partially unused** by list endpoints, which use Specifications (GAP-029).
- **No `@Version`/idempotency** on Academic entities (GAP-030).

CI is still absent (GAP-008), so the "CI green" exit-gate item is the only unverifiable one; local
verification is green.
