# Project Summary

**Smart Competitive Examination Platform, version 1.5.** This document is the standing summary of
what exists, what is verified, and what is still open. It is updated at the end of every task, so a
reader can orient themselves without reading the full history.

Detailed material lives elsewhere — this document points at it rather than repeating it:

| Document | Purpose |
|---|---|
| [`docs/implementation-status.md`](implementation-status.md) | The per-task, per-phase status tables and the release decision |
| [`docs/known-gaps.md`](known-gaps.md) | The register of every open gap, deferred item and by-design limitation |
| [`docs/decisions/`](decisions/) | Architecture Decision Records (ADR-0001 … ADR-0024) |
| [`docs/testing/`](testing/) | Per-phase testing notes and the P12 rehearsal evidence package |
| [`docs/runbooks/`](runbooks/) | Operational runbooks (local setup, and others as they land) |
| [`specs/`](../specs) | The frozen V1.5 specification package — the source of truth |

---

## 1. What the platform is

A modular monolith (Spring Boot 4 / Java 21 target) with a Next.js frontend, PostgreSQL as the source
of truth, Redis as cache only, and PgBouncer for connection management. Eleven business modules:

```text
identity  academic  content  assessment  delivery  evaluation
analytics  commerce  communication  administration  reporting
```

plus three shared packages — `common` (API conventions, audit, jobs, cache, security), `config`, and
`infrastructure`. Every business module owns its tables and exposes narrow application contracts; no
module reads another module's repository.

## 2. Current state

| Dimension | Value |
|---|---|
| Phases implemented | P0 – P13 (P12 is a hardening/rehearsal phase) |
| Flyway migrations | 25 (`V1` … `V25`), no gaps, no duplicated versions |
| Controllers / endpoint mappings | 56 / 237 |
| Backend tests | **337 passing, 0 failures** |
| Frontend tests | 18 files / 56 tests passing; lint clean; production build succeeds |
| Tracked gaps | 152 (see `docs/known-gaps.md` for the register) |
| Container stack | Full stack runs; `scripts/verify-stack.sh` passes 15/15 edge assertions |
| Observability | Prometheus target UP, 9 alert rules load, 2 datasources + 3 dashboards provisioned, Loki ingesting |
| **Release decision** | **NO-GO** — see §4 |

## 3. Phase map

| Phase | Subject | State |
|---|---|---|
| P0 | Foundation: schema, security, Docker, Nginx, PgBouncer, observability boundary | Complete; stack verified 2026-10-19 |
| P1 | Identity and security: sessions, RBAC, password reset | Complete |
| P2 | Academic structure: exams, subjects, chapters, topics | Complete |
| P3 | Content and question bank, authoring and review | Complete |
| P4 | Blueprint and assessment management | Complete |
| P5 | Assessment delivery and the runtime exam engine | Complete — **deadline enforcement and expiry auto-submit added 2026-10-20 (GAP-062 closed)** |
| P6 | Evaluation, results and scorecards | Complete |
| P7 | Scale validation and live pilot | Tooling complete; **staged load evidence outstanding** (GAP-083/084) |
| P8 | Analytics and insights | Complete; derived aggregates only, no time-series tables |
| P9 | Commerce and access management | Complete; reference gateway only (GAP-099) |
| P10 | Communication and notification | Complete; fake email provider only (GAP-108) |
| P11 | Full administration | Complete; 4 of 22 report-family surfaces still partial |
| P12 | Production hardening and readiness | Rehearsal executed; **NO-GO** |
| P13 | Broader and operational reporting | Framework complete; 4 of 22 report types backed (GAP-141) |

## 4. Release decision — NO-GO

The P12 release gates are not all met. The outstanding trigger:

- **GAP-130** — no performance gate was measured (5,000 concurrent users, 99.9% availability,
  p95 < 100 ms, >99% submission success, <1% exam failure). An unmeasured gate cannot be claimed
  as passed.

Also open: **GAP-135** (dress rehearsal, production smoke test, post-release monitoring),
**GAP-088** (the live pilot), **GAP-008/010** (the CI workflows exist but have not run, and branch
protection is documented rather than configured), and **GAP-141** (18 of 22 report types have no
projection provider yet).

Gates that *were* closed by the 2026-10-19 rehearsal: **GAP-131** (logical backup and restore, 6/6
integrity checks) and **GAP-132** (rollback, 12/12 checks). PITR is deliberately **not** claimed
(`scripts/backup/pitr.md`).

## 5. The most recent change — P5 deadline enforcement (GAP-062)

**The problem.** The backend derived and returned the attempt deadline but never enforced it: a
student whose time had run out could still save answers, and their attempt was only finalized if
they pressed Submit.

**The change.**
- `delivery/application/DeadlineEnforcementService` — the single authority for the deadline,
  computed from `student_attempt.started_on` plus the effective duration resolved from the published
  assessment version and the delivery policy (the same computation the client is given at Start
  Exam, so the browser countdown and the server can never disagree).
- `delivery/exception/AttemptExpiredException` — HTTP 409, code `ATTEMPT_EXPIRED`.
- Enforcement wired into the response save, the attempt question and question-list reads, the
  attempt summary (the navigation entry point) and session recovery.
- `delivery/application/AttemptExpiryService` + `delivery/worker/AttemptExpiryWorker` — a bounded,
  scheduled sweep that finalizes attempts whose deadline has passed.
- `SubmissionService` now has **one** shared `finalize(...)` path. Manual submit and expiry both use
  the same conditional `IN_PROGRESS → SUBMITTED` update, so only one of them can win: the loser sees
  zero affected rows. Exactly one session end and one evaluation handoff result.
- `V25__enable_auto_submit_on_expiry.sql` — sets `autoSubmitOnExpiry` on the `DEFAULT` delivery
  policy. It is a JSONB configuration value on an existing row: **no table, no column, no endpoint.**

**Verification.** `AttemptDeadlineIntegrationTest` — 8 tests: post-deadline save / question load /
recovery each rejected with 409 `ATTEMPT_EXPIRED`; pre-deadline save and question load unaffected; the
sweep submits a stale attempt exactly once (a second sweep leaves the timestamp unchanged and the
evaluation count at one); the sweep leaves an attempt inside its deadline alone; and a manual submit
racing the expiry yields one `SUBMITTED` state and one evaluation job.

**Still open (related, not fixed).** GAP-065 — an assessment *session* still has no expiry sweep of
its own, so a session row can linger `ACTIVE` after its attempt has been finalized.

## 6. Conventions that must be kept

1. Flyway for every schema change; never edit an applied migration; never invent a table or column.
2. No endpoint that accepts SQL, a table name, or a caller-supplied expression.
3. Audit insertion stays transactional with the business mutation.
4. Correctness comes only from `answer_key_detail`; never expose `is_correct` to a student.
5. AccessGrant is the authorization source of truth; Purchase and Payment never authorize.
6. Redis is cache only; PostgreSQL is the source of truth; the backend is authoritative.
7. Report/worker work is orchestrated through the shared `background_job` table, never a new queue
   table.
8. A gate is never marked passed on verbal confirmation; evidence is retained per gate.
9. A material change after validation invalidates the affected evidence.
10. On every task: implement, test, build, review the diff, **update this summary**, and report.

## 7. How to verify the current state

```bash
# Backend
cd backend && JAVA_HOME=C:\java\jdk-25.0.4.1 ./mvnw test      # 337 tests, 0 failures

# Frontend
cd frontend && npm ci && npm run lint && npm run type-check && npm test && npm run build

# Container stack (Docker Desktop must be running)
docker compose --profile core --profile app --profile edge up -d --build
bash scripts/verify-stack.sh                                       # 15/15 edge assertions
bash scripts/verify-observability.sh                               # 20/20 observability assertions

# Backup / restore / rollback rehearsal
bash scripts/backup/backup-postgres.sh
bash scripts/backup/restore-postgres.sh <backup-id>
bash scripts/rollback/rollback-rehearsal.sh
```
