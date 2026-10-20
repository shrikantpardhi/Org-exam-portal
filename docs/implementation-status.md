# Implementation Status

> Durable, repository-level status. Updated at the end of each milestone
> (`specs/09-P0-Foundation-V1.5.md §159`). Do not rely on chat history as project truth.

## Project

- **Project:** Smart Competitive Examination Platform
- **Version:** 1.5
- **Architecture:** Modular monolith
- **Organization scope:** single organization per deployment

# Implementation Status

> The standing summary of the project — what exists, what is verified, and what is still open — is
> [`docs/PROJECT-SUMMARY.md`](PROJECT-SUMMARY.md). This document holds the per-phase, per-task detail.

## Current phase

- **Phase:** P13 — Broader Reporting & Operational Reporting (complete) / **P12 release rehearsal (executed 2026-10-19)**
- **State:** Reporting framework complete (catalog, validation, sync/async execution, CSV + PDF renderers, protected download, history); 4 of 22 report types backed, 18 registered-disabled. The P12 rehearsal closed **GAP-131** (backup/restore) and **GAP-132** (rollback) and verified **GAP-007/009/087/134** against the running stack. The release decision remains **NO-GO** on **GAP-130** (no performance gate measured).
- **Testing notes:** [`docs/testing/p13/README.md`](testing/p13/README.md)
- **Rehearsal record:** [`docs/testing/p12/rehearsal-2026-10-19.md`](testing/p12/rehearsal-2026-10-19.md)

## Completed tasks

P0 Foundation (application foundation):

| Task | Description | State |
|---|---|---|
| P0-01 | Repository bootstrap | Completed |
| P0-02 | Backend Spring Boot bootstrap | Completed |
| P0-03 | Common API infrastructure | Completed |
| P0-04 | Persistence bootstrap | Completed |
| P0-05 | Redis bootstrap | Completed |
| P0-06 | Security bootstrap | Completed |
| P0-07 | Frontend bootstrap | Completed |
| P0-08 | Frontend API boundary | Completed |
| P0-09 | Docker | Outstanding (no Docker on the development host) |
| P0-10 | Edge infrastructure (Nginx, PgBouncer) | Outstanding (no Docker on the development host) |
| P0-11 | Observability | Partial (actuator/health/metrics + structured logs; monitoring stack files outstanding) |
| P0-12 | CI/CD | Outstanding (no workflow yet) |
| P0-13 | Security and secret validation | Partial |
| P0-14 | Full smoke test | Outstanding (blocked on Docker) |
| P0-15 | Final review | Outstanding |

P1 Identity and Security:

| Task | Description | State |
|---|---|---|
| P1-01 | Identity Flyway migration (7 tables, constraints, indexes, seeds, system actor) | Completed |
| P1-02 | Organization entity, repository, service foundation | Completed |
| P1-03 | User entity, repository, service, mapper, DTOs, validation | Completed |
| P1-04 | Role and Permission entities, repositories, services, DTOs | Completed |
| P1-05 | UserRole and RolePermission (unique constraints, transactional operations) | Completed |
| P1-06 | LoginSession entity, repository, SessionService, state transitions | Completed |
| P1-07 | Identity services (User, Role, Permission, Session, Authorization, Authentication) | Completed |
| P1-08 | Password hashing policy and wiring | Completed |
| P1-09 | JWT issuance/validation, authentication filter, principal, 401/403 | Completed |
| P1-10 | `POST /api/v1/auth/login` | Completed |
| P1-11 | `POST /api/v1/auth/logout` | Completed |
| P1-12 | `GET /api/v1/me` | Completed |
| P1-13 | `GET /api/v1/me/permissions` and AuthorizationService | Completed |
| P1-14 | Users API (list/get/create/update/archive) | Completed |
| P1-15 | Roles API (list/create/update/get permissions/replace permissions) | Completed |
| P1-16 | Sessions API (list/get) | Completed |
| P1-17 | `POST /api/v1/auth/forgot-password` (non-enumerating) | Completed |
| P1-18 | `POST /api/v1/auth/reset-password` (single-use token) | Completed |
| P1-19 | Authorization matrix tests | Completed |
| P1-20 | Frontend authentication (login/logout/reset/forgot/me/permissions/protected routes) | Completed |
| P1-21 | End-to-end authentication flow | Completed |
| P1-22 | Security review | Completed |
| P1-23 | P1 acceptance | Completed |

P2 Academic Structure:

| Task | Description | State |
|---|---|---|
| P2-01 | Academic module package skeleton | Completed |
| P2-02 | Academic Flyway migration (7 tables) + permission seeds | Completed |
| P2-03 | Exam entity, repository, service | Completed |
| P2-04 | Subject entity, repository, service (exam parent, scoped code) | Completed |
| P2-05 | Chapter entity, repository, service (subject parent, scoped code) | Completed |
| P2-06 | Topic entity, repository, service (chapter parent, scoped code) | Completed |
| P2-07 | SubTopic entity, repository, service (topic parent, scoped code) | Completed |
| P2-08 | Language master CRUD/archive | Completed |
| P2-09 | Tag master CRUD/archive | Completed |
| P2-10 | REST controllers for all frozen endpoints | Completed |
| P2-11 | RBAC authorization on academic endpoints | Completed |
| P2-12 | Audit/security events on mutations | Completed |
| P2-13 | Frontend academic routes shell | Completed |
| P2-14 | Hierarchical navigation UI with lazy child loading | Completed |
| P2-15 | Forms and mutation UI (RHF/Zod/Query/permission-aware) | Completed |
| P2-16 | Integration and end-to-end suite | Completed |
| P2-17 | Performance and observability verification | Completed |
| P2-18 | Code review and exit gate | Completed |

P3 Content and Question Bank:

| Task | Description | State |
|---|---|---|
| P3-01 | Content module package skeleton | Completed |
| P3-02 | Content Flyway migration (9 tables) + permission/template seeds | Completed |
| P3-03 | QuestionTemplate entity/repo/service/API | Completed |
| P3-04 | Question entity/repo/service/API (taxonomy mapping) | Completed |
| P3-05 | QuestionVersion entity/repo/service (versioning, immutability) | Completed |
| P3-06 | QuestionOption entity/repo/service/API | Completed |
| P3-07 | Content validator for MARKDOWN_LATEX_V1 | Completed |
| P3-08 | Markdown+LaTeX renderer with KaTeX boundary and sanitizer | Completed |
| P3-09 | Asset service (upload, ownership, protected access) | Completed |
| P3-10 | Explanation and Hint services/APIs | Completed |
| P3-11 | PreviousExamHistory service/API | Completed |
| P3-12 | QuestionTag association via Academic contract | Completed |
| P3-13 | Search and filter API | Completed |
| P3-14 | Review workflow state machine (self-review blocked) | Completed |
| P3-15 | Publication service with preconditions and immutability | Completed |
| P3-16 | Authoring frontend (list, editor, options, tags) | Completed |
| P3-17 | Review frontend (queue, approve/reject/publish) | Completed |
| P3-18 | Preview + rendering integration (same renderer) | Completed |
| P3-19 | RBAC, audit, correct-answer leakage guard | Completed |
| P3-20 | Rendering regression corpus + API/integration/security tests | Completed |
| P3-21 | Pilot content provisioning (templates/seed) | Partial — templates seeded; import platform deferred (GAP-035) |
| P3-22 | Performance and observability verification | Partial — not measured on staged data (GAP-038) |
| P3-23 | Code review and exit gate | Completed |

P4 Blueprint and Assessment Management:

| Task | Description | State |
|---|---|---|
| P4-01 | Assessment module package skeleton | Completed |
| P4-02 | Assessment Flyway migration (9 tables) + delivery_policy + permissions | Completed |
| P4-03 | Blueprint logical CRUD | Completed |
| P4-04 | BlueprintVersion (versioning, draft mutation, publication boundary) | Completed |
| P4-05 | Typed Blueprint configuration model + parser | Completed |
| P4-06 | Blueprint validation | Completed |
| P4-07 | Blueprint publication (immutable after) | Completed |
| P4-08 | Assessment logical CRUD + filters | Completed (examId filter unsupported — GAP-041) |
| P4-09 | AssessmentVersion (blueprint ref, engine type, delivery policy) | Completed |
| P4-10 | AssessmentQuestion CRUD (published QV only, section/scoring validation) | Completed |
| P4-11 | AssessmentQuestion ordering | Completed |
| P4-12 | Question selection service (Content contract) | Completed |
| P4-13 | Manual selection | Completed |
| P4-14 | Rule-based selection (deterministic, idempotent, shortage) | Completed |
| P4-15 | Hybrid selection | Completed |
| P4-16 | Assessment validation (counts, marks, duration, engine, policy) | Completed |
| P4-17 | Assessment preview (no runtime rows, P3 rendering) | Completed |
| P4-18 | Assessment publication (blocks incomplete, immutable, engine immutable) | Completed |
| P4-19 | Scheduling (published version, valid window, mode, close) | Completed |
| P4-20 | Collections | Completed |
| P4-21 | Frontend builders (blueprint/assessment list, validation panel) | Completed (lean) |
| P4-22 | Security, audit, tests, performance baseline | Partial — security/audit/tests done; performance baseline not measured (GAP-038) |
| P4-23 | Delivery handoff and exit gate | Completed |

P5 Assessment Delivery and Runtime Examination Engine:

| Task | Description | State |
|---|---|---|
| P5-01 | Delivery module package skeleton | Completed |
| P5-02 | Delivery runtime Flyway migration (5 tables) + background_job | Completed |
| P5-03 | Runtime entities and repositories (race-safe conditional updates) | Completed |
| P5-04 | DeliveryPolicy contract and typed attribute parser | Completed |
| P5-05 | ExamEngine registry integration (declarative pilot engine) | Completed |
| P5-06 | Normalized DeliveryConfiguration validation | Completed |
| P5-07 | Candidate eligibility contract (Identity `CandidateDirectory`) | Completed (GAP-057) |
| P5-08 | Attempt creation (PENDING, bound to candidate) | Completed |
| P5-09 | Race-safe snapshot claim | Completed |
| P5-10 | Atomic snapshot preparation | Completed |
| P5-11 | Snapshot retry and stale reclaim | Completed |
| P5-12 | Snapshot integrity checks before READY | Completed |
| P5-13 | Start Exam (zero snapshot inserts, race-safe) | Completed |
| P5-14 | Session create/activate | Completed |
| P5-15 | Attempt read APIs | Completed |
| P5-16 | Question read APIs from the immutable snapshot | Completed |
| P5-17 | Response validation and save with ETag | Completed |
| P5-18 | Stale-write precondition handling (412) | Completed |
| P5-19 | Recovery (returns the same snapshot) | Completed |
| P5-20 | Navigation enforcement (server-side capability checks) | Completed |
| P5-21 | Timer enforcement (server-authoritative global timer) | Completed — central `DeadlineEnforcementService`, post-deadline rejection with 409 `ATTEMPT_EXPIRED`, and expiry auto-submit (GAP-062 closed; section-wise timing remains GAP-060) |
| P5-22 | Idempotent submission | Completed |
| P5-23 | Evaluation handoff via `background_job` | Completed |
| P5-24 | Runtime frontend (engine-agnostic, Zod-validated, ETag autosave) | Partial — lean slice (GAP-058) |
| P5-25 | Observability (runtime counters + structured logs) | Partial — dashboards/alerts outstanding (GAP-025) |
| P5-26 | Security tests (ownership, no correct-answer leakage, config safety) | Completed |
| P5-27 | Concurrency tests (start burst, submission idempotency) | Completed |
| P5-28 | Concurrent-user burst tests | Partial — in-process bursts; 5,000-user staging load outstanding (GAP-059) |
| P5-29 | Pilot end-to-end | Partial — golden path covered by integration tests; pilot rehearsal outstanding |
| P5-30 | P5 release evidence | Completed |

P6 Evaluation, Results, and Scorecards:

| Task | Description | State |
|---|---|---|
| P6-01 | Evaluation module package skeleton | Completed |
| P6-02 | Evaluation Flyway migration (answer_key, answer_key_detail, evaluation) + permission seeds | Completed |
| P6-03 | Result and Scorecard entities and repositories | Completed |
| P6-04 | AnswerKey CRUD | Completed |
| P6-05 | AnswerKeyDetail CRUD | Completed |
| P6-06 | AnswerKey validation (completeness, outcome invariants, option ownership) | Completed |
| P6-07 | AnswerKey publication (immutable after) | Completed |
| P6-08 | `QuestionEvaluator` interface | Completed |
| P6-09 | `QuestionEvaluatorRegistry` | Completed |
| P6-10 | Objective evaluator (option-based, numeric, text) | Completed |
| P6-11 | Negative-marking calculation | Completed |
| P6-12 | BigDecimal and HALF_UP rounding policy | Completed |
| P6-13 | Evaluation orchestration (`EvaluationEngine`, `EvaluationService`) | Completed |
| P6-14 | Evaluation persistence (version uniqueness, partial unique index) | Completed |
| P6-15 | Result creation | Completed |
| P6-16 | Result APIs (list/get/attempt/rankings) | Completed |
| P6-17 | Result publication (+ rank recomputation batch) | Completed |
| P6-18 | Rank and percentile recomputation (competition ranking; serialized per version) | Completed |
| P6-19 | Re-evaluation (version N+1 supersedes N) | Completed |
| P6-20 | Scorecard generation | Completed (HTML artifact; PDF deferred — GAP-075) |
| P6-21 | Secure scorecard access | Completed |
| P6-22 | Observability (counters + timers) | Partial — dashboards/alerts outstanding (GAP-025) |
| P6-23 | Security tests | Completed |
| P6-24 | Concurrency tests | Partial — evaluate-once and publication idempotency verified; staged multi-node burst outstanding (GAP-076) |
| P6-25 | Reproducibility tests (determinism, correction, version history) | Completed |
| P6-26 | Evaluation load tests | Outstanding — no staging data (GAP-076) |
| P6-27 | Scorecard load tests | Outstanding — no staging data (GAP-076) |
| P6-28 | Pilot end-to-end | Partial — golden path covered by integration tests; pilot rehearsal outstanding |
| P6-29 | P6 release evidence | Completed — `docs/testing/p6/README.md`, `docs/decisions/ADR-0019` |

P7 Pilot Operations, Scale Validation, and Live Pilot (evidence phase):

| Task | Description | State |
|---|---|---|
| P7-01 | Build load-test data | Partial — dataset runbook + harness shipped; reduced-scale dataset built by the smoke; 5,000-user cohort credential provisioning blocked (GAP-085) |
| P7-02 | Build test environment | Blocked — no production-like environment on the host (GAP-083/084/087/088) |
| P7-03 | Validate release candidate | Partial — working-tree candidate; not immutably tagged (GAP-083) |
| P7-04 | Run 100-user baseline | Blocked (representative-stage smoke only) |
| P7-05 | Run 500-user baseline | Blocked |
| P7-06 | Run 1,000-user test | Blocked |
| P7-07 | Run 2,000-user test | Blocked |
| P7-08 | Run 3,000-user test | Blocked |
| P7-09 | Run 4,000-user test | Blocked |
| P7-10 | Run 5,000-user test | Blocked |
| P7-11 | Run autosave burst | Blocked — profile implemented and exercised at reduced scale |
| P7-12 | Run submission burst | Blocked — profile implemented and exercised at reduced scale |
| P7-13 | Run recovery burst | Blocked — profile implemented and exercised at reduced scale |
| P7-14 | Run soak test | Blocked |
| P7-15 | Run failure injection | Blocked — no Redis/edge/worker/store to fail |
| P7-16 | Verify data integrity | Partial — reduced-scale reconciliation PASS (`PilotSmokeIntegrationTest`); `scripts/p7/reconcile.sql` for staged runs |
| P7-17 | Verify security | Partial — functional P6 security suite; not under concurrency |
| P7-18 | Validate dashboards and alerts | Blocked — observability stack absent (GAP-087) |
| P7-19 | Full dress rehearsal | Blocked |
| P7-20 | Live pilot | Not started |
| P7-21 | Reconcile results | Partial — procedure and gates provided |
| P7-22 | Archive evidence | Partial — `docs/testing/p7/README.md` + `scripts/p7/` |
| P7-23 | Post-pilot review | Not started |
| P7-24 | Prepare next-phase recommendation | Completed — recommendation: provision the production-like environment and load host before any P7 scale claim |

P8 Full Analytics and Insights:

| Task | Description | State |
|---|---|---|
| P8-01 | Analytics module package skeleton | Completed |
| P8-02 | Analytics Flyway migration (5 tables) + permission seeds | Completed |
| P8-03 | Source query contracts (Identity, Delivery, Assessment, Content, Evaluation) | Completed |
| P8-04 | Student analytics calculation | Completed |
| P8-05 | Assessment analytics calculation | Completed |
| P8-06 | Question analytics calculation (frozen difficulty index) | Completed |
| P8-07 | Topic analytics calculation | Completed |
| P8-08 | Organization analytics calculation | Completed |
| P8-09 | Section dynamic analytics (no section table) | Completed |
| P8-10 | Dashboard read models (student / assessment) | Completed |
| P8-11 | Analytics read APIs (nine frozen GET endpoints) | Completed |
| P8-12 | Cache strategy (scope keys, TTL, invalidation, PostgreSQL fallback) | Completed |
| P8-13 | Incremental refresh worker | Completed |
| P8-14 | Full rebuild worker | Completed |
| P8-15 | Data-quality checks and drift detection | Completed |
| P8-16 | Student dashboard (frontend) | Partial — backend read model delivered; UI deferred (GAP-092) |
| P8-17 | Assessment dashboard (frontend) | Partial — read model delivered; UI deferred (GAP-092) |
| P8-18 | Question analytics UI | Partial — API delivered; UI deferred (GAP-092) |
| P8-19 | Topic analytics UI | Partial — API delivered; UI deferred (GAP-092) |
| P8-20 | Organization analytics UI | Partial — API delivered; UI deferred (GAP-092) |
| P8-21 | Security tests | Completed |
| P8-22 | Repository and integration tests | Completed |
| P8-23 | API tests | Completed |
| P8-24 | Analytics correctness tests | Completed |
| P8-25 | Load tests | Outstanding — no staging data (GAP-094) |
| P8-26 | Rebuild tests | Partial — determinism and isolation verified; staged rebuild load outstanding (GAP-094) |
| P8-27 | Pilot-data backfill | Partial — full rebuild path provides it; not run against pilot data |
| P8-28 | Documentation | Completed — `docs/decisions/ADR-0020`, `docs/testing/p8/README.md` |
| P8-29 | Release evidence | Completed — this status, ADR-0020, and the P8 test notes |

P9 Commerce and Access Management:

| Task | Description | State |
|---|---|---|
| P9-01 | Commerce module package skeleton | Completed |
| P9-02 | AccessPackage entity, repository, CRUD, audit | Completed |
| P9-03 | AccessPackageCollection | Completed |
| P9-04 | Package mapping API | Completed |
| P9-05 | SubscriptionPlan | Completed |
| P9-06 | Coupon | Completed |
| P9-07 | Coupon calculator (fixed amount) | Completed |
| P9-08 | Purchase (server-calculated amount) | Completed |
| P9-09 | PaymentRecord | Completed |
| P9-10 | PaymentGateway abstraction + reference gateway | Completed |
| P9-11 | Payment initiation | Completed |
| P9-12 | Payment callback with idempotency and amount verification | Completed |
| P9-13 | AccessGrant (policy, merge, revocation) | Completed |
| P9-14 | Assessment authorization integration | Partial — `EntitlementResolver` implemented; Delivery enforcement deferred (GAP-100) |
| P9-15 | Refund with access revocation | Completed |
| P9-16 | Finance workspace | Partial — API only; UI deferred (GAP-104) |
| P9-17 | Student store | Partial — API only; UI deferred (GAP-104) |
| P9-18 | Reconciliation | Partial — on-demand service; scheduled job deferred (GAP-101) |
| P9-19 | Redis cache for catalog only | Deferred — catalog reads are served from PostgreSQL; caching is optional (§55) |
| P9-20 | Observability (metrics) | Completed |
| P9-21 | Security tests | Completed |
| P9-22 | Concurrency tests | Partial — validation and locking implemented; callback/refund burst tests outstanding |
| P9-23 | End-to-end test | Completed |
| P9-24 | Production readiness | Partial — provider adapter, rate limiting and UI outstanding (GAP-099/104/105) |

P10 Communication and Notification:

| Task | Description | State |
|---|---|---|
| P10-01 | Communication module package skeleton | Completed |
| P10-02 | Notification entity, repository, service | Completed |
| P10-03 | Notification list API (paged, status filter, self-scoped) | Completed |
| P10-04 | Mark notification read (idempotent) | Completed |
| P10-05 | Mark all read (scoped to the caller) | Completed |
| P10-06 | Notification center frontend | Partial — API only (GAP-109) |
| P10-07 | EmailTemplate entity, repository, service | Completed |
| P10-08 | EmailTemplate CRUD APIs | Completed |
| P10-09 | EmailTemplate admin UI | Partial — API only (GAP-109) |
| P10-10 | Template rendering contract (pure `{{variable}}` replacement) | Completed |
| P10-11 | EmailGateway abstraction | Completed |
| P10-12 | Fake email provider (with failure injection) | Completed |
| P10-13 | Background email delivery (`EMAIL_DELIVERY` job) | Completed |
| P10-14 | Retry policy (bounded, exponential backoff, jitter, cap) | Completed |
| P10-15 | Announcement entity, repository, service | Completed |
| P10-16 | Announcement CRUD APIs | Completed |
| P10-17 | Announcement publish action (asynchronous fan-out job) | Completed |
| P10-18 | Announcement UI | Partial — API only (GAP-109) |
| P10-19 | Event-to-Communication handlers | Completed |
| P10-20 | Commerce integrations (payment success/failure/refund, access granted) | Completed |
| P10-21 | Result and Assessment integrations | Completed |
| P10-22 | Metrics, logging, alerts | Partial — metrics and logging done; dashboards/alerts not built (GAP-087) |
| P10-23 | Security tests | Completed |
| P10-24 | Concurrency and idempotency tests | Partial — read idempotency and job-claim idempotency covered; concurrent-burst tests outstanding |
| P10-25 | Fan-out and load tests | Partial — fan-out correctness covered; staged load outstanding (GAP-094) |
| P10-26 | Staging rollout | Not started — no staging environment (GAP-083) |
| P10-27 | Production enablement | Not started — provider adapter and frontend outstanding (GAP-108/109) |

P11 Full Administration:

| Task | Description | State |
|---|---|---|
| P11-01 | Administration module skeleton | Completed |
| P11-02 | Organization settings (registry, validation, serialized update, contract) | Completed |
| P11-03 | Business rules (data-only, lifecycle, typed resolver contract) | Completed |
| P11-04 | Lookups (CRUD, conservative delete, consumer contract) | Completed |
| P11-05 | Audit explorer (durable `audit_log`, shared write boundary, search API) | Completed — closes GAP-001 |
| P11-06 | User administration | Partial — the existing P1 Identity APIs; no new admin composite surface (ADR-0023) |
| P11-07 | Student administration | Partial — composition across existing owner APIs; no composite detail endpoint |
| P11-08 | Role and permission administration | Partial — the existing P1 Identity APIs and permission cache |
| P11-09 | Commerce operations | Partial — the existing P9 APIs |
| P11-10 | Content and assessment operations | Partial — the existing P3/P4 APIs |
| P11-11 | Delivery operations | Partial — the existing P5 APIs |
| P11-12 | Evaluation operations | Partial — the existing P6 APIs |
| P11-13 | Communication operations | Partial — the existing P10 APIs |
| P11-14 | Admin dashboard | Completed — five sourced cards (GAP-122) |
| P11-15 | Support console | Partial — masked user view and ad-hoc notification; other capabilities deferred (GAP-121) |
| P11-16 | Imports | Not started — frozen target registry unbuilt (GAP-119) |
| P11-17 | Bulk operations | Not started — frozen operation allow-list unbuilt (GAP-120) |
| P11-18 | Global search | Partial — framework plus USER and AUDIT_LOG handlers (GAP-118) |
| P11-19 | System health | Completed — eight components, parallel bounded checks (GAP-124) |
| P11-20 | Security hardening | Completed — explicit permissions, no god mode, gating tests |
| P11-21 | Mixed workload | Not started — no staging environment (GAP-083) |
| P11-22 | End-to-end and release | Not started — imports/bulk/frontend outstanding |

P12 Production Hardening and Production Readiness:

| Task | Description | State |
|---|---|---|
| P12-01 | Hardening freeze | Done — freeze declared; candidate not uniquely identifiable (no commit/tag, no image digest) |
| P12-02 | Security regression | Executed — full suite green; security classes pass; config review recorded |
| P12-03 | API regression | Executed — 55 controllers / 232 endpoints inventoried; all API tests pass |
| P12-04 | Database performance | Partial — 105 indexes reviewed and matched to the frozen list; no plan/throughput measurement possible |
| P12-05 | Concurrent-user scale validation | **BLOCKED** — no load environment or tooling (GAP-130) |
| P12-06 | Concurrency regression | Executed — start burst, response preconditions, recovery, callback idempotency all green |
| P12-07 | Failure injection | Partial — in-test injection (cache, email, payment, job claim); real DB/pool/network/restart injection blocked |
| P12-08 | Backup validation | **Executed** — consistent logical backup stored with checksums and evidence (GAP-131 closed) |
| P12-09 | Restore rehearsal | **Executed** — restore into an isolated database, 6/6 integrity checks passed (GAP-131 closed) |
| P12-10 | Migration rehearsal | Partial — applies cleanly on the real stack and is forward-compatible; not timed at production volume (GAP-133) |
| P12-11 | Rollback rehearsal | **Executed** — 12/12 checks, 2 s redeploy, data unchanged (GAP-132 closed) |
| P12-12 | Monitoring and alert validation | **Executed** — target UP, 9 rules load, 2 datasources and 3 dashboards provisioned, Loki ingesting (GAP-009/087/134 verified) |
| P12-13 | UX regression | Partial — frontend lint/test/build green; no manual/visual pass possible |
| P12-14 | Production configuration review | Executed — review complete with findings GAP-136/137/138 |
| P12-15 | Dress rehearsal | **BLOCKED** — requires staging (GAP-135) |
| P12-16 | Release evidence package | Complete — `docs/testing/p12/README.md`, with BLOCKED gates recorded |
| P12-17 | Production smoke test | **BLOCKED** — requires a deployed candidate (GAP-135) |
| P12-18 | Post-release monitoring | **BLOCKED** — requires a released candidate and the monitoring stack (GAP-135) |

P13 Broader Reporting and Operational Reporting:

| Task | Description | State |
|---|---|---|
| P13-01 | Reporting module skeleton | Completed |
| P13-02 | Report registry (22 types, 6 categories, filter schemas) | Completed |
| P13-03 | Request and filter validation | Completed |
| P13-04 | Authorization (per-type permission, student self-scoping) | Completed — student permissions deferred (GAP-142) |
| P13-05 | Synchronous execution | Completed |
| P13-06 | Asynchronous execution via `background_job` | Completed |
| P13-07 | Job status | Completed |
| P13-08 | History (from `background_job`) | Completed |
| P13-09 | Artifact storage (protected, per-job, sanitized names) | Completed |
| P13-10 | PDF renderer (dependency-free, paginated) | Completed — plain presentation (GAP-147) |
| P13-11 | CSV renderer (formula-injection mitigation) | Completed |
| P13-12 | Student reports | Not backed — registered-disabled (GAP-141) |
| P13-13 | Assessment reports | Not backed — registered-disabled (GAP-141) |
| P13-14 | Content reports | Not backed — registered-disabled (GAP-141) |
| P13-15 | Commerce reports | Not backed — registered-disabled (GAP-141) |
| P13-16 | Operations reports | Completed — 3 reports |
| P13-17 | Executive reports | Completed — KPI summary with definitions |
| P13-18 | Observability | Completed — generation/failure/queue/latency/download meters |
| P13-19 | Security testing | Completed — IDOR, spoofed token, filter schema, no-SQL |
| P13-20 | Performance testing | Not started — no load environment (GAP-130, GAP-150) |
| P13-21 | Reconciliation | Completed — rows sourced from owner contracts; read-only proven |
| P13-22 | Staged rollout | Not started — no staging environment (GAP-135) |

## Release decision

- **NO-GO**, on one remaining §188 trigger: **GAP-130** — no performance gate was measured
  (5,000 concurrent users, 99.9% availability, p95 < 100 ms, >99% submission success, <1% exam
  failure). An unmeasured gate cannot be claimed as passed.
- The three previously blocking gates are now closed or materially advanced:
  - **GAP-131 (backup/restore) — closed** for logical backup and restore (PITR still not
    configured, and therefore not claimed).
  - **GAP-132 (rollback) — closed**: the procedure is rehearsed end to end; the residual is that no
    version delta was exercised, because only one candidate image has ever existed.
  - **GAP-133 (migration rehearsal) — partially addressed**: the chain applies cleanly on the real
    stack and is proven forward-compatible, but not timed at production volume.
- **GAP-135** (dress rehearsal, production smoke test, post-release monitoring) and **GAP-008**
  (CI first run) also remain open, as does the live pilot (GAP-088).
- Full reasoning: [`docs/testing/p12/README.md`](testing/p12/README.md) §9 and
  [`docs/testing/p12/rehearsal-2026-10-19.md`](testing/p12/rehearsal-2026-10-19.md).

## In progress

- None.

## Blocked

- None (P0-09/P0-10/P0-14 are blocked on Docker availability, not on code).

## Deferred

- Persistent `audit_log` persistence is delivered by Administration (frozen migration `V11`); P1
  emits structured security events (ADR-0013).
- Real password-reset delivery (email/notification) is not built in P1 (ADR-0012).
- Business modules (Evaluation, Analytics, Commerce, Communication, Administration) remain
  unimplemented.
- P5: the pilot candidate population is the STUDENT-role operational set pending Commerce/AccessGrant
  (GAP-057); section-wise timers await a schema amendment (GAP-060); `background_job` is created
  ahead of Infrastructure (GAP-055).
- P6: the asynchronous evaluation handoff requires an explicit answer key that the P5 submit handoff
  does not carry (GAP-071); multi-correct `MULTIPLE_CHOICE` answers are not representable in the
  frozen `answer_key_detail` (GAP-072); the attempt is not advanced to `EVALUATED` and
  `student_attempt.total_score` stays null (GAP-073); the maximum-score check is not enforced as a
  gate (GAP-078); per-question outcomes are not persisted or exposed (GAP-079); evaluation caching
  and a database-ordered rank projection are deferred (GAP-081, GAP-082).
- P7: the staged scale runs (ramp, bursts, soak, spike, failure injection, monitoring, backup and
  the live pilot) are blocked on the production-like environment and load capacity (GAP-083, GAP-084,
  GAP-087, GAP-088); a synthetic cohort cannot self-authenticate (GAP-085); the HTTP harness cannot
  perform database reconciliation without `psql` (GAP-086). Only a local reduced-scale pilot smoke
  was executed.
- P8: question/topic correctness re-derives per-question outcomes from the P6 evaluator because P6
  does not persist them (GAP-090); incremental question/topic refresh is scoped to the affected
  assessment versions (GAP-091); the analytics dashboard frontend and trend buckets are deferred
  (GAP-092, GAP-093); rebuild/refresh performance evidence is outstanding (GAP-094).
- P9: only the reference `TEST` payment gateway ships; a real provider adapter is deferred
  (GAP-099). The entitlement contract is implemented but Delivery/Assessment enforcement needs an
  approved free-assessment rule (GAP-100); reconciliation is on-demand (GAP-101); purchase-creation
  idempotency is a service-level dedupe because no idempotency store is frozen (GAP-098); the
  commerce frontend and rate limiting are deferred (GAP-104, GAP-105).
- P10: only the reference `FAKE` email provider ships; a real provider adapter is deferred
  (GAP-108). There is no durable email delivery table (GAP-107) and no outbox, so event handoff is
  in-process and at-least-once (GAP-110/111); password-reset delivery is still Identity's logging
  implementation (GAP-112); announcements fan out to all active users (GAP-113); the communication
  frontend and rate limiting are deferred (GAP-109, GAP-115).
- P11: the cross-domain administration surfaces remain the existing owner APIs (no new composite
  endpoints); imports and bulk operations are not implemented (GAP-119, GAP-120); only the USER and
  AUDIT_LOG search handlers are backed (GAP-118); support exposes the masked user view and ad-hoc
  notification only (GAP-121); the dashboard sources five cards (GAP-122); the administration
  frontend is deferred (GAP-123); audit immutability is application-level (GAP-117); optional health
  components report UNKNOWN so an unconfigured deployment is DEGRADED by design (GAP-124). GAP-001
  (durable audit) is closed.

## Latest verified result

- **Backend:** 337 tests, 0 failures, 0 errors, 0 skipped — `BUILD SUCCESS`
  (`mvnw.cmd test`, `JAVA_HOME=C:\java\jdk-25.0.4.1`, compiled with `--release 21`).
- **Frontend:** lint clean, 18 test files / 56 tests passing, `next build` succeeds.
- **Schema:** 25 Flyway migrations `V1`–`V25` apply cleanly with no numbering gaps. P13 adds a
  permission seed only, and P5 hardening adds one configuration seed only — **no reporting table and
  no new column**.
- **Container stack (GAP-007 — closed 2026-10-19):** the full stack runs and
  `scripts/verify-stack.sh` passes 15/15 assertions (every service healthy, the backend reachable
  through the Nginx edge with its database check UP, PgBouncer confirmed in the backend's path, the
  API auth boundary holding, the actuator tree hidden, the request id forwarded). Running it found
  and fixed **five real defects** (see the rehearsal record §5).
- **Rehearsal (GAP-131/132 — closed 2026-10-19):** a logical backup (195,392 bytes, 1 s) restored
  into an isolated database in 1 s with **6/6 integrity checks passing**, and a rollback rehearsal
  with **12/12 checks passing** (2 s redeploy, data unchanged).
- **Observability (GAP-009/087/134 — verified 2026-10-19):** Prometheus reports the backend target
  UP, all 9 alert rules load, Grafana provisions 2 datasources and 3 dashboards, and Loki ingests
  labeled logs (20/20 checks).
- Full record: [`docs/testing/full-verification.md`](testing/full-verification.md) and
  [`docs/testing/p12/rehearsal-2026-10-19.md`](testing/p12/rehearsal-2026-10-19.md).
- Nothing is committed; all changes live in the working tree.

## Latest CI result

- Not applicable: no CI workflow exists yet (GAP-008), so these results are local-only.

## Known risks

- The committed `frontend/package-lock.json` was out of sync and broke `npm ci` (and therefore the
  frontend image build, which uses `npm ci`). Repaired during the re-verification (GAP-126); CI must
  confirm.
- Frontend verification now exists but is manual; nothing enforces it until CI lands (GAP-127/008).
- npm 11 blocked the `esbuild`/`unrs-resolver` install scripts under `allowScripts`; a fresh host may
  need an allow-list entry (GAP-129).
- No Docker on the development host: container runtime, `nginx -t`, and PgBouncer path verification
  (P0-09/P0-10/P0-14) are still outstanding. This also blocks the P7 scale environment (GAP-083).
- JDK 25 is installed at `C:\java\jdk-25.0.4.1` (a user `JAVA_HOME` not inherited by the build
  shell); the Maven Wrapper needs `JAVA_HOME` set explicitly and the build compiles with
  `--release 21`.
- No load test tooling (`k6`, `jmeter`), no `psql`, and no `redis-server` are installed
  (P7 GAP-084/086).
- The host environment sets `NODE_ENV=production`; `npm ci` must run with `NODE_ENV` unset or
  devDependencies are skipped.
- Refresh tokens are session-bound and cannot revive an expired access token (ADR-0011).
- No idle session timeout; the frozen schema has no last-activity column (ADR-0011).
- Access token is stored in `sessionStorage`; httpOnly-cookie transport is a future hardening item
  (ADR-0010).

## Known gaps

- The consolidated register of open gaps, deferred items, and by-design limitations for P0 to P13
  is [`docs/known-gaps.md`](known-gaps.md) (152 tracked items). GAP-001 is closed by P11; **GAP-062
  (attempt deadline enforcement and expiry auto-submit) is closed** by the P5 hardening change;
  GAP-126/127 were raised and closed by the re-verification; **GAP-131** (backup/restore) and
  **GAP-132** (rollback) are closed by the 2026-10-19 rehearsal; **GAP-007/009/087/134** were
  verified against the running stack on 2026-10-19; **GAP-130** (no performance evidence) remains
  the outstanding P12 release blocker; **GAP-141** (18 of 22 report types unbacked) is the principal
  P13 limitation; GAP-151/152 record what the observability stack still needs (exporters and two
  §112 instruments); **GAP-008/010 are implemented as four GitHub Actions workflows** that have not
  run yet (branch protection is documented, not configured); **GAP-126/127/129 are now enforced by
  CI** rather than fixed once.

## Architecture deviations

- P1 decisions that touch the authentication, refresh, reset, caching, and system
  actor contracts are captured in ADR-0010 through ADR-0014; P2 in ADR-0015; P3 in ADR-0016; P4 in
  ADR-0017; P5 in ADR-0018; P6 in ADR-0019; P8 in ADR-0020; P9 in ADR-0021; P10 in ADR-0022; P11 in
  ADR-0023; P13 in ADR-0024. All preserve the frozen schema and API.

## Environment tool versions used

- JDK: 25 (`release 21` target); set `JAVA_HOME` explicitly for the Maven Wrapper
- Maven: via the committed Maven Wrapper
- Node.js: 24
- Python: 3.15
- Docker: not available on the development host
- Load tooling (`k6`/`jmeter`), `psql`, `redis-server`: not available on the development host
