# P3 Acceptance Evidence

- **Phase:** P3 — Content and Question Bank
- **Date:** 2026-10-10
- **Frozen source:** `specs/12-P3-Content-V1.5.md §150-§173`

Commands were run and their results recorded, not claimed.

## 1. Verification command set

| Command | Result |
|---|---|
| `cd backend && ./mvnw clean verify` | PASS — 154 tests, 0 failures, 0 errors, BUILD SUCCESS |
| `cd frontend && npm run lint` | PASS — 0 errors |
| `npm run type-check` | PASS |
| `npm test` | PASS — 44 tests, 15 files |
| `npm run build` | PASS — `/questions`, `/questions/new`, `/questions/[id]`, `/questions/[id]/versions/[versionId]`, `/question-templates` built |

Content backend tests: rendering regression (14), leakage (1), migration (5), API (10) = 30.

## 2. The three requested verifications

**Rendering regression corpus** — `ContentRenderingRegressionTest` (14 tests):
- Ordinary text, inline `\(...\)`, block `\[...\]`, and mixed content render.
- `An item costs $500 and the tax is $50.` keeps `$500`/`$50` as **ordinary text** with no math.
- `$x$` is **not** parsed as math; `$$x$$` is rejected (`UNSUPPORTED_MATH_DELIMITER`).
- Every raw-HTML sample (`<p>`, `<strong>`, `<div>`, `<script>`, `<img>`, `<span onclick>`) is rejected;
  `import …` (MDX) is rejected; unbalanced `\(` is rejected; malformed `[asset: …]` is rejected.
- Sanitizer removes `<script>` and event handlers (defence in depth).

**Published version immutability** — `ContentApiIntegrationTest.aPublishedVersionIsImmutable`:
- After publish, `PUT`/`POST` option and `POST` explanation all return **409**.
- `POST /questions/{id}/versions` creates **version 2** in DRAFT — corrections require a new version;
  the published version is untouched.

**No correct-answer leakage** — `ContentLeakageTest`:
- A student projection built from entities that DO carry `isCorrect` serializes **no** `isCorrect`,
  `correctOptionId`, or `answerKey`, and no `correct` substring at all.
- `SET`/`GET` of authoring options still returns `isCorrect` (authoring/review APIs are allowed).

## 3. Review workflow and publication (P3-14, P3-15)

`ContentApiIntegrationTest`:
- `theAuthoringJourneyPublishesAVersion` — create → options → submit → approve (different reviewer) → publish.
- `selfReviewIsBlocked` — an ADMIN who submits cannot approve their own version (403).
- `illegalReviewTransitionsAreRejected` — approving a DRAFT and double-submitting return 409.
- `publishIsRejectedBeforeApproval` and `publishIsRejectedWhenTheTemplateRulesAreNotMet` (422).
- `contentReferencingAnotherVersionAssetIsRejected` — `ASSET_VERSION_MISMATCH` (400).
- `taxonomyMustExistWhenCreatingAQuestion` — 404.
- `aStudentCannotAccessTheQuestionBank` — 403.

## 4. Acceptance checklists

`§151-§170`: module skeleton; schema verification (9 tables, constraints, indexes); QuestionTemplate,
Question, QuestionVersion, QuestionOption; content validator; renderer; asset service; explanation and
hint; previous exam history; tags; search/filter; review; publication; authoring/review/preview
frontend; security and audit; tests (unit, integration, API, rendering regression) — implemented and
covered. RBAC enforced on the backend; audit events emitted on mutations.

## 5. Known limitations

See [`docs/known-gaps.md`](../../known-gaps.md). P3-relevant: KaTeX SSR boundary (GAP-031), no draft
version-content edit endpoint (GAP-032), tag association via question update (GAP-033), protected
asset streaming deferred to the files endpoint (GAP-034), bulk import deferred (GAP-035), permission
names as candidates (GAP-036), no Content Redis cache (GAP-037), performance evidence on staging
outstanding (GAP-038), migration numbering V6/V7 (GAP-039). CI is still absent (GAP-008), so the
"CI passes" exit-gate item remains the only unverifiable one; local verification is green.
