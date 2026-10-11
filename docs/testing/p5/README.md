# P5 Acceptance Evidence

- **Phase:** P5 — Assessment Delivery and Runtime Examination Engine
- **Date:** 2026-10-10
- **Frozen source:** `specs/14-P5-Delivery-V1.5.md §157-§164`

Commands were run and their results recorded, not claimed.

## 1. Verification command set

| Command | Result |
|---|---|
| `cd backend && ./mvnw test` | PASS — 183 tests, 0 failures, 0 errors, BUILD SUCCESS (P5 adds 19) |
| `cd frontend && npm run type-check` | PASS |
| `npm run lint` | PASS — 0 errors |
| `npm test` | PASS — 56 tests, 18 files (P5 adds 9) |
| `npm run build` | PASS — `/exam/[attemptId]` built |

P5 backend tests: migration/seed (5) + Start Exam/security (5) + response precondition (3) +
recovery/submission (3) + start-burst concurrency (2) + metrics (1) = 19.

## 2. The four requested verifications

**Start Exam performs zero snapshot inserts** — `StartExamIntegrationTest.startExamPerformsZeroSnapshotInsertsAndIsIdempotent`:
- `attempt_question` and `attempt_question_option` counts are **identical** before and after Start.
- One session is created; a repeated Start returns the established context and creates no second
  session.
- `StartBurstConcurrencyTest` asserts the same invariant **under a 16-thread burst** on one attempt
  and across 10 concurrent users.

**Stale-write precondition handling** — `ResponsePreconditionIntegrationTest`:
- First save (no `If-Match`) → 200 with a new `ETag`.
- Save with the current ETag → 200 with a new `ETag`; the stored value is the newer response.
- Save with the now-stale ETag → **412** `STALE_RESPONSE`; the newer response is not overwritten.
- `If-Match: *` with no existing row → **412**.
- Saving twice for the same question updates one row (`UNIQUE(attempt_id, question_version_id)`).

**Recovery returns the same snapshot** — `RecoveryAndSubmissionIntegrationTest`:
- The question-version list is identical before and after `POST /sessions/{id}/recover`.
- `started_on` is unchanged and no second session is created (`§51`).

**Concurrent-user start burst** — `StartBurstConcurrencyTest`:
- 16 concurrent starts on one attempt → 1 logical start, 1 session, 0 snapshot inserts.
- 10 concurrent users → 10 distinct attempts started, 1 session each, snapshot row count unchanged.

## 3. Additional coverage

- **Submission idempotency** (`§64`): a second submit returns the same `submittedOn`; exactly one
  `SUBMITTED` row, one `EVALUATION` `background_job`, and no ACTIVE session.
- **Correct-answer protection** (`§41, §127`): the runtime question payload never contains
  `isCorrect` / `is_correct` / `answerKey`; the strict frontend schema rejects a leaked `isCorrect`.
- **Ownership** (`§83`): a student reading another student's attempt receives 404.
- **Configuration security** (`§35`): unknown/executable configuration keys are rejected by the
  strict Zod schema and fail safe to defaults on the backend.

## 4. Acceptance checklists

`§158-§164`: module skeleton and boundaries; entities map to the frozen schema; repositories use the
frozen indexes; delivery policy and `DeliveryConfiguration` validated; engine registry wired;
candidate source chosen and documented; attempt creation; race-safe claim; atomic snapshot;
idempotent retries; READY only after integrity validation; no snapshot inserts by Start; race-safe
`NOT_STARTED -> IN_PROGRESS`; session created/activated; question APIs use the snapshot; idempotent
ETag response save; stale-write handling; recovery returns the same attempt; server-authoritative
timer; navigation enforced server-side; idempotent submit; durable idempotent evaluation handoff;
engine-agnostic Zod-validated runtime; metrics and structured logs.

## 5. Known limitations

See [`docs/known-gaps.md`](../../known-gaps.md). P5-relevant: `background_job` created ahead of
Infrastructure (GAP-055), Delivery permission codes are candidates (GAP-056), the pilot candidate
population is the STUDENT-role operational set pending Commerce/AccessGrant (GAP-057), the lean
runtime frontend (GAP-058), in-process burst tests rather than a 5,000-user staging load (GAP-059),
global timer only (GAP-060), migration numbering V11/V12/V13 (GAP-061), no post-deadline mutation
rejection or auto-submit (GAP-062), no frozen navigation endpoint (GAP-063), no staff cross-user read
path (GAP-064), no session-expiry sweep (GAP-065), no request-level `Idempotency-Key` dedupe
(GAP-066), in-memory attempt listing (GAP-067), no delivery-policy authoring API (GAP-068), and
client-supplied question timing (GAP-069). CI is still absent (GAP-008); local verification is green.
