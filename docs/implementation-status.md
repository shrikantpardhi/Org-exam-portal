# Implementation Status

> Durable, repository-level status. Updated at the end of each milestone
> (`specs/09-P0-Foundation-V1.5.md §159`). Do not rely on chat history as project truth.

## Project

- **Project:** Smart Competitive Examination Platform
- **Version:** 1.5
- **Architecture:** Modular monolith
- **Organization scope:** single organization per deployment

## Current phase

- **Phase:** P5 — Assessment Delivery and Runtime Examination Engine
- **State:** Implemented

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
| P5-21 | Timer enforcement (server-authoritative global timer) | Completed (GAP-060) |
| P5-22 | Idempotent submission | Completed |
| P5-23 | Evaluation handoff via `background_job` | Completed |
| P5-24 | Runtime frontend (engine-agnostic, Zod-validated, ETag autosave) | Partial — lean slice (GAP-058) |
| P5-25 | Observability (runtime counters + structured logs) | Partial — dashboards/alerts outstanding (GAP-025) |
| P5-26 | Security tests (ownership, no correct-answer leakage, config safety) | Completed |
| P5-27 | Concurrency tests (start burst, submission idempotency) | Completed |
| P5-28 | Concurrent-user burst tests | Partial — in-process bursts; 5,000-user staging load outstanding (GAP-059) |
| P5-29 | Pilot end-to-end | Partial — golden path covered by integration tests; pilot rehearsal outstanding |
| P5-30 | P5 release evidence | Completed |

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

## Latest verified commit

- To be recorded when P1 is committed.

## Latest CI result

- Not applicable: no CI workflow exists yet.

## Known risks

- No Docker on the development host: container runtime, `nginx -t`, and PgBouncer path verification
  (P0-09/P0-10/P0-14) are still outstanding.
- Java 25 is not installed; the approved Java 21 fallback is used.
- The host environment sets `NODE_ENV=production`; `npm ci` must run with `NODE_ENV` unset or
  devDependencies are skipped.
- Refresh tokens are session-bound and cannot revive an expired access token (ADR-0011).
- No idle session timeout; the frozen schema has no last-activity column (ADR-0011).
- Access token is stored in `sessionStorage`; httpOnly-cookie transport is a future hardening item
  (ADR-0010).

## Known gaps

- The consolidated register of open gaps, deferred items, and by-design limitations for P0 to P5
  is [`docs/known-gaps.md`](known-gaps.md) (69 tracked items: audit persistence, reset delivery,
  refresh/timeout limits, token storage, rate limiting, CI, Docker/edge, phase migration numbering,
  permission-name candidates, the KaTeX rendering boundary, the delivery_policy and background_job
  prerequisites, the assessment examId filter, the lean P4/P5 frontends, blueprint validation gaps,
  the pilot candidate population, the global-only timer and missing deadline enforcement, navigation
  and session-expiry gaps, seed data, performance evidence, and others).

## Architecture deviations

- None recorded. All P1 decisions that touch the authentication, refresh, reset, caching, and system
  actor contracts are captured in ADR-0010 through ADR-0014; P2 in ADR-0015; P3 in ADR-0016; P4 in
  ADR-0017; P5 in ADR-0018. All preserve the frozen schema and API.

## Environment tool versions used

- JDK: 21
- Maven: via the committed Maven Wrapper
- Node.js: 22
- Docker: not available on the development host
