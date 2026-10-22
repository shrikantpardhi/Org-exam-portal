# ADR-0018 — P5 delivery runtime decisions

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P5 — Assessment Delivery and Runtime Examination Engine
- **Frozen source:** `specs/14-P5-Delivery-V1.5.md`, `specs/04-Database-Schema-V1.5.md §29`, `specs/05-API-Contract-V1.5.md §44-§46`, `specs/08-Exam-Engine-and-CDUID-Contract-V1.5.md`

These decisions sit inside the frozen architecture. None changes a frozen schema, API, or domain
contract.

## 1. Asynchronous snapshot preparation with a database-safe claim

- The immutable runtime snapshot is `attempt_question` + `attempt_question_option`; snapshot **state**
  lives on `student_attempt.snapshot_status` (no new table — `14-P5 §6, §155`).
- Each attempt is prepared in explicit, separate transactions: a **claim** transaction
  (`UPDATE ... WHERE id = :id AND snapshot_status = 'PENDING'`), a **materialization** transaction
  that also marks `READY`, and an independent **failure** transaction that marks `FAILED`.
- Only the transaction that receives one affected row owns the preparation. No in-process lock is
  used (`§22`). Stale `PREPARING` rows are reclaimed using the approved `updated_on` timestamp
  (`§23`).

## 2. Start Exam performs zero snapshot inserts

- Start Exam is a conditional transition only:
  `UPDATE student_attempt SET attempt_status = 'IN_PROGRESS' WHERE id = :id AND attempt_status =
  'NOT_STARTED' AND snapshot_status = 'READY'`, plus lightweight session activation.
- It never inserts `attempt_question` / `attempt_question_option`, randomizes questions, or builds a
  snapshot (`§26, §27`). This is the thundering-herd defense.

## 3. Stale-write protection via HTTP conditional requests

- The ETag is `resp-{updatedOnMillis}-{crc32OfResponse}`, derived from `student_response.updated_on`.
- **No `response_version` column is added** (`§155`). A mismatched `If-Match` returns **412**
  `STALE_RESPONSE`; `If-Match: *` succeeds only if a row exists; no `If-Match` is the first save
  (`§43`).

## 4. Evaluation handoff is durable and idempotent

- Submission performs the conditional `IN_PROGRESS -> SUBMITTED` transition; only the winning
  transition ends the session and enqueues **one** `background_job` of type `EVALUATION`
  (`§64, §66, §67`). The job uses the **system actor** identity (`§14 invariant 22`).
- `background_job` (Infrastructure) is created by migration `V12` ahead of its phase because the
  handoff has no other durable mechanism. Recorded as **GAP-055**.

## 5. Server-authoritative timer

- The pilot implements the **global** timer from `started_on` + duration; the browser clock is a
  display aid. Section timers are deferred until a schema amendment permits them (`§57, §58`,
  **GAP-060**).

## 6. Candidate population and access boundary

- `AccessGrant` is the frozen source of truth for paid content. Commerce is implemented (P9), and
  Delivery now consults it through the `EntitlementResolver` contract at Start Exam: an assessment no
  AccessPackage grants is free content, and a paid assessment requires a currently valid grant
  (**ADR-0026**, GAP-100 closed).
- Delivery resolves the operational candidate set through a new Identity application contract,
  `CandidateDirectory`, whose default implementation returns ACTIVE users holding the `STUDENT` role.
  Recorded as **GAP-057** (the grant-driven candidate population is still deferred; the entitlement
  gate is the *access* control and does not narrow preparation).

## 7. Delivery permission codes

- The frozen Identity catalogue defines no Delivery permission codes, and `§83` states codes are not
  invented in Delivery. The minimal operational codes required by the authorization matrix
  (`DELIVERY_PREPARE_ATTEMPTS`, `DELIVERY_ATTEMPT_VIEW_ANY`, `DELIVERY_OPERATE`) are seeded and must
  be realigned with the Identity catalogue. Recorded as **GAP-056**. Student runtime endpoints are
  authorized by **resource ownership**, not a permission code.
- `DELIVERY_ATTEMPT_VIEW_ANY` is now used to **widen** the attempt read: `GET /attempts/{id}` keeps
  self-scoping as the default and additionally allows a caller holding the permission to read another
  student's runtime attempt. The permission is resolved from the authenticated principal, never from
  the request, and both paths render the same payload. Recorded as **GAP-064** (closed).

## 8. Module contracts (no cross-module repository access)

- Delivery consumes `AssessmentDefinitionCatalog` (published P4 handoff), `ContentCatalog`
  (`runtimeQuestion`, student-safe content with no `isCorrect`), `CandidateDirectory`, and the
  Delivery-owned `DeliveryPolicyCatalog`. It never reads another module's repositories
  (`§14 invariant 16`).
