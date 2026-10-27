# Project Summary

**Smart Competitive Examination Platform, version 1.5.** This document is the standing summary of
what exists, what is verified, and what is still open. It is updated at the end of every task, so a
reader can orient themselves without reading the full history.

Detailed material lives elsewhere — this document points at it rather than repeating it:

| Document | Purpose |
|---|---|
| [`docs/implementation-status.md`](implementation-status.md) | The per-task, per-phase status tables and the release decision |
| [`docs/known-gaps.md`](known-gaps.md) | The register of every open gap, deferred item and by-design limitation |
| [`docs/decisions/`](decisions/) | Architecture Decision Records (ADR-0001 … ADR-0037) |
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
| Flyway migrations | 43 (`V1` … `V43`), no gaps, no duplicated versions. `V42` raises `MAX_ADMINS_PER_ORG` to 5; `V43` seeds `PLATFORM_ADMIN_PRIMARY_ASSIGN` (ADR-0054). `V37`-`V39` add the organization profile and primary administrator; `V40` moves business-rule authoring to SUPER_ADMIN; `V41` seeds the platform administrator-management permissions |
| Controllers / endpoint mappings | 63 / 259 |
| Backend tests | **655 tests, 0 failures, 2 skipped** |
| Frontend tests | **585 tests, 0 failures (100 files)** |
| Tracked gaps | 200 rows, 14 open (see `docs/known-gaps.md` for the register) |
| Container stack | Local stack runs and is verified end to end. `scripts/verify-stack.sh` now passes **31/31 assertions, exit 0** (2026-10-24), including the five header assertions added 2026-10-23 for GAP-137, which had **never been run** until now (`specs/21-P12 §26`). The run also proves the browser path: the bundle carries a relative API base, the frontend CSP allows same-origin connectivity, CORS answers a trusted cross-origin preflight and refuses an untrusted one, and a headless browser loads the page and reaches the API. A separate production-like environment (`infrastructure/environments/production-like/`) passes **31/31** via `scripts/verify-production-like.sh` as of 2026-10-23 |
| Observability | In the production-like environment Prometheus scrapes 5 targets (backend, postgres, redis, pgbouncer, itself) all UP, with 2 datasources and 3 dashboards provisioned. **The local stack's Prometheus crash-loops** on `--config.expand-env`, a flag Prometheus does not have (GAP-158) |
| Load harness | `load/` (k6) — six profiles, seed, ramp runner; 100 → 5,000 ramp executed 2026-10-22; cohort credentials come from the environment-gated `LoadCohortCli` (GAP-085) |
| **Release decision** | **NO-GO** — see §4 |

## 3. Phase map

| Phase | Subject | State |
|---|---|---|
| P0 | Foundation: schema, security, Docker, Nginx, PgBouncer, observability boundary | Complete; stack verified 2026-10-19 |
| P1 | Identity and security: sessions, RBAC, password reset | Complete |
| P2 | Academic structure: exams, subjects, chapters, topics | Complete |
| P3 | Content and question bank, authoring and review | Complete — **the full authoring editor is delivered: template/taxonomy/language selectors, options editor, math helpers, asset uploader, explanation/hint, tags and previous-exam history (GAP-049, 2026-10-22)** |
| P4 | Blueprint and assessment management | Complete — **the assessment `examId` filter now works through the selection-rule provenance (GAP-041, 2026-10-22, ADR-0027)**; **the blueprint/assessment builders, schedule and collection UI are delivered (GAP-049, 2026-10-22)** |
| P5 | Assessment delivery and the runtime exam engine | Complete — **the per-question N+1 in the snapshot read removed (GAP-156, 2026-10-23: 309→10 statements, 84→27 ms for 100 questions), deadline enforcement + auto-submit (GAP-062, 2026-10-20), submit-time answer-key handoff (GAP-071, 2026-10-21), the paid-content entitlement gate (GAP-100) and the session expiry sweep + staff attempt read (GAP-064/065, 2026-10-22) added**; **the full runtime page, multi-tab guard and offline queue are delivered (GAP-058, 2026-10-22)** |
| P6 | Evaluation, results and scorecards | Complete — **attempt completion contract added 2026-10-21 (GAP-073 closed); the results and scorecard UI is delivered (GAP-128, 2026-10-22)** |
| P7 | Scale validation and live pilot | **Load harness delivered and the full ramp executed three times, most recently on the tagged candidate `v1.5.0-rc2` (GAP-084 closed, GAP-083 closed); the rc2 ramp passes every integrity gate but fails the per-endpoint p95 gate on `attempt.start` and `attempt.questions` at every step (GAP-130 Residual); cohort credentials now come from an environment-gated, audited, idempotent provisioning mechanism (GAP-085 closed, 2026-10-22). The latency gate was measured and FAILS (GAP-130), driven by a per-question N+1 in `attempt.questions` (GAP-156).** Soak, spike, failure injection, availability and the live pilot remain outstanding A documented production-like environment now exists at `infrastructure/environments/production-like/` — five CPU-pinned tiers, four networks, 2,000-client PgBouncer and Prometheus scraping the DB/Redis/PgBouncer exporters — verified 31/31 by `scripts/verify-production-like.sh`, with the generator driving 5,000 VUs at 5,078 req/s and 0.00 % failures (GAP-083 Open: environment ready, staged re-run not yet executed). |
| P8 | Analytics and insights | Complete; derived aggregates only, no time-series tables — **the analytics dashboard frontend is delivered (GAP-092, 2026-10-22)** |
| P9 | Commerce and access management | Complete; reference gateway only (GAP-099) — **the entitlement contract is now enforced by Delivery (GAP-100, 2026-10-22) and the commerce UI is delivered (GAP-104, 2026-10-22)** |
| P10 | Communication and notification | Complete; fake email provider only (GAP-108) — **the communication UI is delivered (GAP-109, 2026-10-22)** |
| P11 | Full administration | Complete; 4 of 22 report-family surfaces still partial — **the administration UI is delivered (GAP-123, 2026-10-22)** |
| P12 | Production hardening and readiness | Rehearsal executed; **NO-GO**. Release candidate **v1.5.0-rc1** is tagged (`c966d1c`) with two recorded image digests and a migration rehearsal at production volume (GAP-140, GAP-133, 2026-10-23). The three security findings from the production configuration review are now fixed — API docs are refused in production (GAP-136), the CSP/HSTS/Referrer-Policy header policy is enforced at the application layer and configured at the edge (GAP-137), and the actuator tree beyond health/info requires the monitoring scope or `SYSTEM_HEALTH_VIEW` (GAP-138), all 2026-10-23. The index amendment raised by the database review is applied under ADR-0028, with measured plans (GAP-139, 2026-10-23) |
| P13 | Broader and operational reporting | Complete; all 23 catalog report types backed, and the reporting UI is delivered (GAP-141, GAP-148, 2026-10-22) |

## 4. Release decision — NO-GO

The P12 release gates are not all met. The outstanding trigger:

- **GAP-130** — the performance gate has been measured three times and **still fails**, now on the
  immutably tagged candidate `v1.5.0-rc2`. The rc2 ramp (`100 / 500 / 1,000 / 2,500 / 5,000`) ran on
  2026-10-23 on the production-like environment. Every integrity gate passed — zero snapshot inserts at
  every step, 100% submission success, 0% exam failure at 5,000 users, 0 cross-user exposure, and
  993/993 submitted attempts evaluated with exactly one current evaluation and one result each. The
  latency gate fails at **every** step on two endpoints: `attempt.start` 1203.4 / 2523.6 / 2325.5 /
  2062.0 / 1921.8 ms and `attempt.questions` 1069.6 / 2107.7 / 1592.5 / 1431.8 / 1312.2 ms against the
  < 100 ms target. Both are flat around 1–2.5 s and do not degrade with load, which is a fixed
  per-request cost, not contention; the connection pool is idle throughout (`pool_maxwait_s` = 0 at
  every step, PG never above 15 of 300 backends). `response.save` (48.4 ms), `attempt.submit`
  (41.8 ms) and `session.recover` (44.6 ms) are inside the gate at 5,000 users. **The GAP-156 and
  GAP-059 fixes did not move the start path into the gate.** The 5,000-user numbers are additionally
  not a valid population measurement: **GAP-160** (the edge has no `listen ... backlog=`) refused 4,786
  first connections, so only 214 of 5,000 students actually started. Evidence:
  `docs/testing/p7/load-2026-10-23-rc2.md`.

Three harness defects and one platform defect were found and fixed during this run, and each was a
reason the gate could not have passed before: **GAP-159** (`load/seed.mjs` could not grant the roles a
fresh database needs, so there was no administrator, and it never published an answer key), **GAP-161**
(`EvaluationWorker` threw `TransactionRequiredException` on every tick because it bypassed
`JobStateService`, and `EVALUATION_WORKER_ENABLED` defaulted to false — 992 jobs sat QUEUED and 0
results existed; after the fix 992/992 completed with 0 failures), plus the unquoted-UUID bug that made
`scripts/p7/reconcile.sql` fail on every invocation. Two further defects are recorded as open:
**GAP-162** (the `start_burst_zero_new_rows` gate does not exclude `snapshot_status = 'FAILED'`) and
**GAP-163** (the harness reports a global p95 across all endpoints, which masks per-endpoint breaches).

Also open: **GAP-135** (dress rehearsal, production smoke test, post-release monitoring),
**GAP-088** (the live pilot), **GAP-008/010** (the CI workflows exist but have not run, and branch
protection is documented rather than configured), **GAP-141a** (three commerce report types named by
`22-P13 §55` have no frozen catalog identifier, so they need a catalog amendment), and **GAP-142**
(student self-service reporting is implemented and self-scoped on the backend, but students hold no
report permission, so the student screen is present and unreachable until the permission decision is
taken).

**GAP-084 is closed:** load-generation capacity exists and was proven — the generator sustained 5,000
concurrent VUs for 30 s with zero interrupted iterations, and a full 5,000-student ramp ran to
completion.

Gates that *were* closed by the 2026-10-19 rehearsal: **GAP-131** (logical backup and restore, 6/6
integrity checks) and **GAP-132** (rollback, 12/12 checks). PITR is deliberately **not** claimed
(`scripts/backup/pitr.md`).

## 4a. The most recent change — permission model hardening, tasks 1-6

**What changed.** Seven of the twelve permission defects identified in the hardening mission's
pre-work are closed. Two migrations, four new ADRs, one ADR amendment, three service changes, and 29
tests.

| Defect | What it was | Fix |
|---|---|---|
| D-01 | `SUPER_ADMIN` returned 403 on `GET /roles` and `GET /permissions`, so both platform seeds screens were broken | `V32` grants `ROLE_VIEW` + `PERMISSION_VIEW`; ADR-0032's scope sentence clarified |
| D-02 | an `ADMIN` could strip the `SUPER_ADMIN` role's permissions (200) and lock the installation owner out | `RoleService` refuses every mutation of a contract-fixed role — 409 `ROLE_IMMUTABLE` (ADR-0038) |
| D-03 | an `ADMIN` could place `PLATFORM_*` on any role (200) | reserved set declared as the `RESERVED_PERMISSIONS` business rule (`V33`), bypass granted to nobody — 403 `RESERVED_PERMISSION_DENIED` (ADR-0039) |
| D-04, D-09 | `ROLE_PERMISSION_ASSIGN` was a god mode: a caller could grant codes it did not hold | bounded delegation — 403 `PERMISSION_NOT_HELD` (ADR-0040) |
| D-07 | `PUT /roles/{id}` had no self-guard, so an `ADMIN` deactivated its own role and locked itself out | status changes on a role the caller holds are refused — 403 `SELF_PERMISSION_MODIFICATION` (ADR-0043) |
| D-12 | no test referenced a `PLATFORM_*` code, so the owner's capability could drift silently | `SuperAdminPermissionCatalogueTest` pins the exact 10-code set and its absences |

**Why.** The pre-work reproduced all twelve defects against the live stack and the decision matrix was
approved for tasks 1-6. Two of them (D-02, D-07) *damaged* the running deployment during
reproduction, which is why the guards are in the service layer rather than in the controllers: the
same paths will be reused.

**How it was verified.** 29 new integration tests, all passing; the full backend suite is green; no
existing test was modified, weakened, or deleted. One new test failed on its first full-suite run
because these tests share one non-rollback database, and was scoped to the seeded catalogue rather
than relaxed.

**What is still open.** Requirement **R2 is not met**. D-05 (roles are globally scoped; `role` has no
`organization_id`), D-10 (`GET /users` returns 5,010 users across both organizations to a
single-organization `ADMIN`) and D-11 (`audit_log` has no organization attribute) remain, all behind
Gate B. D-06 (no `SUPER_ADMIN` recovery path for a locked-out `ADMIN`) and D-08 (two-step escalation
through a created role) remain behind Gate A. The pre-work, the decision matrix, and the per-defect
reproductions live under `docs/testing/security/permission-hardening/`.

**Correction to the pre-work.** The D-03 record claimed `GET /permissions` concealed `PLATFORM_*` from
an `ADMIN`. It does not — the earlier reading was a single page of 100 against a 163-code catalogue
sorted by `code`. The `ADMIN` can discover **and** assign every reserved code, which makes D-03 worse
than recorded, not better. The record is corrected in place.

## 4b. The most recent change — permission model hardening, tasks 7-8 (Gate A)

**What changed.** Gate A was approved on 2026-10-26 with four answers, and three of them widened the
platform tier. `V34`, two new endpoints, one new read surface, two ADRs, and 15 tests.

| Decision | What it means | Implemented as |
|---|---|---|
| **A1** | a locked-out ADMIN can be recovered through the API | `POST /platform/organizations/{orgId}/admins/{userId}/reset-password` and `.../unlock-sessions`, `PLATFORM_ADMIN_RECOVER`, ADR-0042 |
| **A2** | SUPER_ADMIN authors roles — **create, then assign** | `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN` granted in `V34`; `CreateRoleRequest` unchanged, so no contract amendment |
| **A2b** | SUPER_ADMIN holds the reserved-set bypass | `UNBOUNDED_PERMISSION_ASSIGN` granted, otherwise authoring could not build any role carrying a `PLATFORM_*` code |
| **A3** | D-08 Rule A | `RolePermissionPolicy.checkAssignable` → 403 `ROLE_ESCALATION_DETECTED`, ADR-0044 |
| **A4** | build the cross-organization user list | `GET /platform/users`, `PLATFORM_ADMIN_VIEW`, read-only, includes the `PLATFORM` organization |

**Why.** D-06 is new scope, not a bug fix, and the record says so: `10-P1` §63 deliberately placed
administrator recovery outside the API. That decision was taken when several ADMINs could share an
organization; ADR-0032's `MAX_ADMINS_PER_ORG = 1` removed the replacement path and left a database
session — plus, as D-07 showed, a manual Redis key deletion — as the only recovery.

**How it was verified.** 15 new integration tests; the full backend suite is green at **566 tests, 0
failures, 2 skipped**. Five pre-existing tests pinned the pre-Gate-A contract and were updated — that
is the intended consequence, recorded per test. Three defects in
my own first draft were found by those tests and fixed: a wrong eviction method (`evict` instead of
`evictAfterCommit`), a refusal order that reported the installation owner as `NOT_AN_ADMIN` instead of
`SUPER_ADMIN_IMMUTABLE`, and a PLATFORM-organization assertion that would have passed for the wrong
reason against a fixture with no resident.

**What this costs.** `SuperAdminPermissionCatalogueTest` was edited from 10 pinned codes to 16, `RoleImmutabilityIntegrationTest`'s
count from 10 to 16, and `SuperAdminCatalogueReadIntegrationTest`'s authoring-refusal pin was inverted.
That
edit is the forcing function Task 6 existed to provide — widening the owner's capability now requires
a reviewer to see it in a file whose only purpose is to be read. Separately, D-05 gets **more**
urgent: `SUPER_ADMIN` and every organization's `ADMIN` now write the same global role rows.

**What is still open.** Requirement **R2 is still not met** (D-05, D-10, D-11 — all behind Gate B).
D-08's Rule A is closed; nothing remains at Gate A.

## 4c. Permission model hardening, tasks 9-11 (Gate B) — D-05, D-10, D-11

Gate B was approved 2026-10-26 with five decisions. The three remaining defects are now implemented,
and **R2 is recorded as PARTIALLY met, not met** — that distinction is the most important line in this
section.

### What changed

| Task | Defect | Change | Migration |
|---|---|---|---|
| 9 | D-05 | roles are organization-owned (`organization_id`; NULL = platform-wide) | `V35` |
| 10 | D-10 | `GET /users` and `GET /users/{id}` scoped to the caller's organization | none |
| 11 | D-11 | `audit_log.organization_id`, written at write time, read scoped | `V36` |

Four ADRs were added: **ADR-0045** (organization-owned roles), **ADR-0046** (audit trail carries an
organization), **ADR-0047** (the two frozen-schema amendments — **Proposed, awaiting sign-off**), and
ADR-0044 (role escalation) from Gate A.

### Why R2 is *partially* met

Gate B chose shape **S2** over the recommended **S1**: the seven seeded roles stay platform-wide. So:

| Behaviour | R2 |
|---|---|
| An `ADMIN` reads or edits a role **its organization created** | met |
| An `ADMIN` reads another organization's **users** or **audit trail** | met — those paths are closed |
| An `ADMIN` edits `STAFF` / `STUDENT` / `REVIEWER` / `SUPPORT` / `FINANCE` / `ADMIN` | **not met** — affects every organization |

This is documented in ADR-0045, in `gate-b-memo.md` §8, and as **GAP-184**. It is not softened
anywhere, because an `ADMIN` renaming `STAFF` still changes what every organization sees.

### The frozen-schema problem, and why it is an ADR rather than a test edit

`V35` and `V36` change two frozen tables. Two exhaustive schema pins caught both —
`IdentityMigrationIntegrationTest` (`uk_role_code should exist`) and `P11MigrationIntegrationTest`
(`unexpected: [organization_id]`). Neither pin was relaxed silently: both now name the addition and
cite ADR-0047, and both still fail on any column or index that is in neither the frozen spec nor the
ADR. **ADR-0047 is Proposed, not Accepted** — the amendments are implemented and tested but await
product/architecture sign-off, and `specs/04` §25.3/§34.4 have not been edited to match. **GAP-185**
tracks it.

### Verification

| Check | Result |
|---|---|
| New tests | 18 (7 role scoping, 5 user scoping, 4 audit scoping, 2 platform-tier visibility) |
| Identity package | 165 tests across 32 classes, 0 failures |
| Full backend suite | **584 tests, 0 failures, 0 errors, 2 skipped — BUILD SUCCESS** |
| Live stack | re-verified at migration 35 then 36 |

### The regression this pass introduced, and caught

The first version of Task 9 scoped **every** caller to its own organization — including `SUPER_ADMIN`.
Once a tenant created a role, the system administrator could no longer see it. That is a regression
against **R1**, introduced by the fix for D-05.

**The suite passed with the bug in place.** Every fixture role was platform-wide, and in that state the
buggy and correct code return the same list. Live probing is what exposed it, by creating a
tenant-owned role and comparing the two catalogues:

~~~text
ADMIN       visible roles: ... ,LIVE_T9_PROBE2
SUPER_ADMIN visible roles: ...          <- the tenant's role is missing
~~~

Fixed by `RoleService.isSystemAdministrator`, keyed on holding the `SUPER_ADMIN` role rather than on
`PLATFORM`-organization residence. Pinned by `PlatformRoleVisibilityIntegrationTest` (2 tests) and
recorded as **GAP-186**. Recorded here rather than quietly fixed, because "the tests were green" was
true and misleading at the same time.

### Two tests that would have passed for the wrong reason

Both were fixed at the fixture, not the assertion:

1. **Audit scoping.** The first draft asserted audit rows existed for users the fixture created. The
   base class writes users straight through the repository, so no audit row is emitted. Two of three
   tests failed against an empty expectation; the fix was to drive the real `SecurityEventLogger`.
2. **Platform organization list.** My new tests create organizations, which pushed the target
   organization off page 1 of an unfiltered list. The test now scopes by `search`.

### Residuals

| Residual | Register |
|---|---|
| The seeded roles are still shared across organizations (S2) | GAP-184 |
| `V35`/`V36` amend frozen tables and `specs/04` has not been edited | GAP-185 |
| `RESERVED_PERMISSIONS` is still editable via `PUT /business-rules/{id}` | GAP-183 |

## 4d. Local mail delivery — SMTP gateway and Mailpit (ADR-0048)

The stack had no way to deliver real email: only `FakeEmailGateway` shipped, which records messages in
memory. A developer could trigger a password reset and find the mail nowhere. `specs/19-P10` §29 already
names an SMTP client as a legitimate provider, so this needed no frozen-contract change.

### What was added

| Change | File |
|---|---|
| `SmtpEmailGateway` — an `EmailGateway` adapter over `JavaMailSender` | `communication/gateway/SmtpEmailGateway.java` |
| `mailpit` compose service (SMTP `1025`, UI `8025`) | `docker-compose.yml` |
| `spring-boot-starter-mail`, confined to the adapter | `backend/pom.xml` |
| `app.communication.smtp.from` and `spring.mail.*` transport | `application.yml` |
| 6 unit tests for failure classification and the boundary | `SmtpEmailGatewayTest` |

No migration, no endpoint, no schema change. The `EmailGateway` contract is unchanged — this is a
second implementation of it.

### Two defaults, deliberately different

| Layer | Default | Why |
|---|---|---|
| Application (`application.yml`) | `FAKE` | a host-run backend and every test must send nothing |
| Compose backend | `SMTP` | `SPRING_MAIL_HOST` is pinned to the `mailpit` container |

The compose default is `SMTP` *because* the transport is pinned to a container that is not a relay.
Defaulting it to `FAKE` would mean mail silently goes nowhere, which is the gap this closed. The
application default stays `FAKE`, and `SmtpEmailGateway` is `@ConditionalOnProperty(havingValue =
"SMTP")`, so a bare test or host run cannot emit SMTP traffic.

### Failure classification

`MailException`/`MessagingException` → retryable (relay down, auth, 4xx/5xx). A missing recipient →
**permanent**, because retrying an invalid address fails identically five times and only delays the
operator seeing it. This is the §34 split, and it is what the 6 tests pin.

### Verified end to end

Against the running stack, not just in unit tests:

| Step | Result |
|---|---|
| `POST /auth/forgot-password` | HTTP 200 |
| Worker picks up the queued `EMAIL_DELIVERY` job | delivered within the interval |
| Mailpit inbox | **1 message**, `To: demo.student@example.test`, `Subject: Reset your password` |
| Envelope sender | `no-reply@exam-platform.local` |
| Body | 583 chars of HTML containing `reset-password?token=` |
| Backend log grep for `token=` / `reset-password?token=` | **no match** — the reset token is not logged (§27) |

### Residuals

| Residual | Note |
|---|---|
| Mailpit is a sink, not a relay | it never delivers to real recipients; that is the intent |
| `angus-mail` is a runtime dependency | `mvnw -o` fails on a clean machine until one online build runs |
| Ports on loopback only | nothing off-host can inject mail into the dev stack |

## 4e. Any provider, by configuration — the generic HTTP adapter (ADR-0049)

SMTP (ADR-0048) covers self-hosted relays, Mailpit, and every hosted provider that exposes an SMTP
endpoint. It does not cover providers whose only integration is a JSON API. This adds one adapter for
that shape, so **changing email provider is a configuration change, not a code change.**

### The shape almost every provider shares

POST JSON to a URL with the key in a header. Everything variable is a setting:

| Setting | Example |
|---|---|
| `provider` | `HTTP_API` |
| `url` | `https://api.sendgrid.com/v3/mail/send` |
| `auth-scheme` | `BEARER`, `X_API_KEY`, `API_KEY`, `NONE` |
| `api-key-env` | `SENDGRID_API_KEY` — the **name**, never the key |
| `body-template` | the JSON payload, with `{to}` `{from}` `{subject}` `{html}` `{text}` |
| `success-field` | `status`, for providers that answer 2xx with an error envelope |
| `reference-field` | `data.id`, where the provider's message id lives |

### Two decisions worth naming

**The key is never configuration.** `api-key-env` names an environment variable; the value is read at
send time. A key therefore cannot reach a config file, a compose file, a log line or an actuator
response.

**A half-configured adapter refuses to start.** A missing `url` or `body-template` throws at
construction rather than spending the retry budget discovering an operator mistake.

### Retry classification (§34)

| Response | Retryable |
|---|---|
| 2xx | no |
| 2xx with a failing `success-field` | no |
| 408, 429, 5xx | **yes** |
| other 4xx | no |
| transport error, timeout | **yes** |
| missing key, bad template, no URL | no |

### Verified end to end

A stub provider was attached to the compose network and the backend pointed at it **by configuration
only**. A real `POST /auth/forgot-password` produced:

| Observed | Value |
|---|---|
| method / path | `POST /send` |
| auth header | `Bearer live-stub-key-abc123` |
| body `to` / `from` | `demo.student@example.test` / `no-reply@exam-platform.local` |
| body `subject` | `Reset your password` |
| body `html` | 581 chars, contains `reset-password?token=` |

13 unit tests cover the wire format against a real `HttpServer` — auth schemes, the retry split, the
2xx-error case, JSON escaping of quotes and HTML, and the fail-fast checks.

### Where the three adapters now stand

| Provider | Use it for | Default |
|---|---|---|
| `FAKE` | tests; records in memory | **yes** |
| `SMTP` | Mailpit locally, any relay, hosted SMTP | compose stack |
| `HTTP_API` | any JSON-over-HTTP provider | no |

`provider: FAKE` remains the application default, so no test run or host-run backend can emit real
traffic.


## 4f. Frontend UI modernization (light-only design system)

Full audit in [`docs/frontend/ui-audit-2026-10-26.md`](frontend/ui-audit-2026-10-26.md).

| Change | Detail |
|---|---|
| Semantic design tokens | `globals.css` now defines surface/border/muted/primary/success/warning/danger/info in one place; `tailwind.config.ts` maps them |
| Light-only | 168 `dark:` variants removed from 19 files, `ThemeToggle` deleted, the root dark bootstrap script removed. `specs/Design-Theme.md` §21 says "no dark mode until validated" — this ships only the validated mode |
| UI primitives | `components/ui/` grew from 2 to 12: input, label, textarea, select, badge, alert, empty-state, spinner, page-header, table |
| Student workspace | New `WorkspaceShell` + `StudentShell` + `STUDENT_MENU` + `app/student/layout.tsx`. The student area had **no shell at all** — ten pages each rendered their own `<main>` |
| Staff area gate | `StaffAreaLayout` + layouts for `academic/*` and `question-templates/*`, which previously rendered outside any guard and any shell |
| Landing page | Rebuilt with real product content, a working sign-in action and three portal entries. No testimonials, statistics, or gradients |
| Responsive | 13 raw tables wrapped in scrollable keyboard-reachable containers; shell offsets derive from a single constant pair |

**Verified:** lint pass, type-check pass, **513/513 tests** (was 487), production build compiled successfully.
Live stack re-checked after redeploy; `/`, `/student`, `/academic`, `/docs` and the three login portals all serve.

**Not done, deliberately:** ~800 module-level colour literals remain (mechanical, high-diff, zero functional
gain, deferred so it can be done per module); no browser/axe run because Playwright and axe-core are not
installed — accessibility was verified structurally at the DOM level instead.


## 4g. Workspace consistency and organization profile (2026-10-26)

Follow-up to the light-theme work, driven by a per-portal review of what each role could actually
reach and understand. No business rule, examination behaviour, or authorization decision changed; the
changes are navigation, presentation, and one additive schema migration.

| Area | Change |
|---|---|
| Cross-role pages | `/account` and `/change-password` were the only authenticated screens with no workspace chrome. `RoleShell` now renders the shell matching the signed-in role; `/results` runs in `StudentShell`. Change-password keeps a focused single-card layout because the backend blocks every other endpoint until the flag clears |
| SUPER_ADMIN lookups | `GET /api/v1/lookups` required `LOOKUP_VIEW`, which `V29`/`V32` never granted, so the read-only seeds screen 403'd. `V38` grants it (read-only, mirroring `V32`) and `SuperAdminPermissionCatalogueTest` is updated — the pinned-set test is the forcing function |
| Org admin scope | `SUPER_ADMIN` removed from the users role filter and the roles list; a role the administrator holds is read-only in the permissions panel (escalation guard); permissions catalogue gained a search/status filter; Cancel added next to Save |
| System health | Removed from the organization administrator entirely (page, screen, menu entry). `/platform/system` still reports dependency health to the platform owner |
| Business rules | List now explains each rule and offers View before Edit; create and edit moved to dedicated pages; explanation registry is written against the enforcing code (`MAX_ADMINS_PER_ORG`, `RESERVED_PERMISSIONS`) |
| Dashboard | Recent activity from the audit trail, per-module card provenance, and a link to organization analytics |
| Organization profile | **`V37`** adds ten nullable profile columns (address, contact, website, logo) to `organization`; `PUT /api/v1/admin/organization-config/profile` writes them, scoped to the caller's organization and gated on `ORGANIZATION_SETTING_UPDATE`; editable from `/admin/settings` and visible to the platform owner. Recorded in **ADR-0051** because it amends a frozen table |
| Student | Home lists examinations actually available via access grants (assigned or purchased) instead of a link grid; Store renamed **Explore**; Reports removed from student navigation; menu entry renamed Profile |
| Post-creation handover | Creating an organization lands on its detail page with an explicit "one step remains: create the administrator" cue |

**Verified:** frontend `lint` 0 errors / 0 warnings, `type-check` clean, **521/521 tests (93 files)**,
production build compiled. Backend **603/603, `BUILD SUCCESS`**. Live stack re-checked: `/`,
`/student`, `/results`, `/account`, `/change-password`, `/admin/settings`, `/admin/business-rules`,
`/analytics/organization` all 200; `/admin/system-health` now 404; organization profile persists across
reads; business rules, audit log, and organization analytics return real data; SUPER_ADMIN lookups
grant applied by `V38`.

**Open:** ~800 module-level palette literals (GAP-189) and no browser/axe run (GAP-190) remain as
recorded. `specs/04` §25.2 and `specs/05` still need to record `V37`/`V38` before V1.5 is re-frozen.


## 4h. Reports for the organization administrator, and the layout sweep (2026-10-26)

Two parts: a missing navigation entry, and a systematic audit of how every page gets its workspace.

### Reports

`/admin/reports` already existed, was already wrapped in `AuthenticatedGuard` with
`permission="REPORT_EXECUTIVE_READ"`, and the `ADMIN` role already held all eight `REPORT_*` grants
(`V24`). **The screen was simply not in the sidebar**, so it was reachable only by typing the URL.
Added to `ADMIN_MENU` beside Analytics, and converted from a hand-rolled `<main>` to `AdminShell` like
every other administrative screen.

### Layout sweep

Audited all 98 `page.tsx` files for how each obtains its chrome (own shell, inherited `layout.tsx`, or
its own `<main>`), and found three distinct defects:

| Defect | Pages | Fix |
|---|---|---|
| **Two `main` landmarks** — page rendered `<main>` inside `AppShell`/`AdminShell`, which already emits one | 7 (`/admin/commerce`, `/admin/communication/**`) | Page `<main>` becomes `<div>`; the shell keeps the landmark |
| **No chrome at all** — guarded, but no sidebar, so no way back | 25 across `/finance`, `/questions`, `/assessments`, `/blueprints`, `/collections`, `/schedules`, `/staff`, `/attempts` | Added `layout.tsx` per tree using the existing `StaffAreaLayout`, which was built for exactly this problem (`specs/20-P11` §63) |
| **Mixed-role tree** — `/analytics` belongs to students *and* staff | 4 | `RoleShell` at the tree root, with `/analytics/organization` exempt because it supplies its own `AdminShell` |

`/exam/**` stays deliberately shell-less (full-screen attempt), and `/login/**`, `/`, `/forgot-password`,
`/reset-password` keep `PublicLayout` — those are correct, not defects.

Also migrated the four most palette-heavy pages reached during the sweep (`/analytics`, `/blueprints`,
`/blueprints/[id]`, `/admin/communication`) from `slate-*`/`bg-white` literals to semantic tokens.

### Two defects found while proving Reports works end to end

Both were invisible to the test suite because the tests do not use the mounted volume or the real
history query path:

1. **Every report generation failed in the deployed stack** (GAP-195). `backend-data` is a Docker
   *named* volume; because the image had no `/app/data` path, the daemon created it owned by `root`
   while the container runs as uid 1001. `ReportArtifactStore.write` could not create its directory,
   so `POST /api/v1/reports` returned 500 for every report type — and uploads under
   `STORAGE_LOCAL_ROOT=/app/data/uploads` would have failed the same way. Fixed in the Dockerfile by
   creating both directories owned by `exam` before the volume is declared.
2. **Report history is always empty for the report types the screen offers** (GAP-196).
   `ReportService.history` reads only `background_job` rows, but synchronous reports write an audit
   entry and no job row, and every executive/ops type is `SYNC`. Left **open** and reported: making
   sync runs appear in history changes what `background_job` means and needs a spec decision first
   (rule 20). The UI degrades honestly with "No report history yet."

### Palette migration completed

The sweep that started on four pages was finished: **every** `slate/gray/zinc` palette literal in
`frontend/app`, `frontend/features` and `frontend/components` (roughly 1,100 occurrences across
~160 files) now uses the semantic tokens. This closes the bulk of **GAP-189**. Verified: 0 remaining
matches, lint 0, type-check 0, 521/521 tests, production build compiled.

**Verified:** frontend `lint` 0 errors / 0 warnings, `type-check` clean, **521/521 tests (93 files)**,
production build compiled. Live: `/api/v1/reports/catalog` **200** with 26 report types;
`POST /api/v1/reports` now returns a real 4-row `EXECUTIVE_KPI_SUMMARY` (5,010 active users) where it
previously returned 500; `ADMIN` holds all eight `REPORT_*` permissions.

## 4i. Organization rules, primary administrators, and platform admin CRUD (2026-10-26)

Product direction from the SUPER_ADMIN: organization rules are managed by the platform owner only;
an organization may hold several administrators but no one may edit a *peer's* roles except its
primary administrator; and the platform owner needs full CRUD over an organization's administrators
in a TanStack grid with 10/50/100 paging. Three requirements, three contracts that did not exist.

### Organization rules belong to the owner (`V40`, ADR-0052)

`V23` had granted `BUSINESS_RULE_CREATE` and `BUSINESS_RULE_UPDATE` to `ADMIN`, which contradicts the
direction: a business rule expresses platform-wide policy (`MAX_ADMINS_PER_ORG` is itself a rule), and
the organization administrator is its *subject*. `V40` deactivates those two grants rather than
deleting them — `role_permission` keeps them with `INACTIVE` so the history of the grant survives —
and grants all three rule codes to `SUPER_ADMIN`, which previously could not author a rule at all.
`BUSINESS_RULE_VIEW` is deliberately kept for `ADMIN`: it may read what governs it.

Verified live as both principals: `GET /api/v1/business-rules` returned **200** for ADMIN **and**
SUPER_ADMIN; `POST /api/v1/business-rules` returned **403** for ADMIN and **200** for SUPER_ADMIN.

### A designated primary administrator (`V39`, ADR-0052)

With `MAX_ADMINS_PER_ORG = 1` there was only ever one candidate to answer who may reassign roles, so
no such concept existed. Allowing several administrators removes that answer — any of them could
demote the others, including the account created to own that decision. The `LAST_ADMIN` guard
prevents an empty organization but not the *wrong* administrator taking over.

`organization.primary_admin_user_id` (nullable, FK to `user(id)` `ON DELETE SET NULL`, partial index)
records the designated authority. It is a column on `organization`, not a flag on `user_role`, so
at most one primary is a schema property rather than something every writer must remember — a
per-assignment boolean would permit several primaries and defeat the guard it expresses.

`UserService.replaceRoles` gains `guardPrimaryAdminAuthority`: a same-organization `ADMIN` who is not
the primary, changing another user's roles, receives **403 `PRIMARY_ADMIN_REQUIRED`**. The rule is
**fail-closed** — if the organization designates no primary, role editing is refused for everyone in
it. Existing organizations are deliberately **not** backfilled: inferring the primary from
`created_on` would hand the power to whoever happened to be oldest, precisely the escalation the
column exists to prevent. Creating an organization's first administrator designates it, which is the
escape from that state and why the create path is not itself subject to the guard.

Five integration tests cover it (`PrimaryAdminAuthorityIntegrationTest`), including that being primary
grants nothing over oneself: `SELF_ROLE_MODIFICATION` still takes precedence.

### Platform administrator CRUD (`V41`, ADR-0053, §72.10-§72.13)

§72.5 defined only `POST .../admins`, so the owner could create an administrator but not list,
correct or remove one — a mistyped invitation was unfixable from the UI and the ownership permanent.
Three endpoints and two permissions (`PLATFORM_ADMIN_ROLE_UPDATE`, `PLATFORM_ADMIN_DEACTIVATE`,
SUPER_ADMIN only) close that. Cross-organization targets answer **404, never 403**: a 403 would
confirm the id exists elsewhere.

Deliberate refusals, each asserted: `PATCH .../roles` cannot add or remove `ADMIN` (that is the
cap/primary path) or grant `SUPER_ADMIN`; `DELETE` deactivates rather than deletes, and refuses the
primary administrator and the last active administrator (409).

> **Superseded 2026-10-27 by §4j / ADR-0054.** Primacy transfer *is* now implemented
> (`PATCH .../primary-admin`, GAP-197 closed) and `DELETE` now **archives reversibly** with a matching
> `POST .../restore`; the last-administrator and primary refusals are retained.

Live: `GET .../admins` returned the 3 administrators of `DEFAULT` with pagination and
`isPrimary: false` for each — correct, because that organization predates `V39` and has no primary.

### The grid, and a real focus bug (`@tanstack/react-table` 8.21.3)

`components/ui/data-grid.tsx` is a new server-paginated grid on TanStack Table with a 10/50/100 rows-
per-page select. The existing `components/admin/data-table.tsx` is **kept** for the twenty-odd
screens that use it: it renders a fixed column list, which is all they need, and changing them was
not part of this work.

Building `OrganizationAdminsScreen` surfaced a genuine defect: the roles editor was rendered inside a
TanStack cell, so React recreated the input on every keystroke — the field lost focus and silently
kept only the first character. Caught because the test asserted the input's own value before
asserting the request body. The editor is now a form panel outside the table.

### Navigation and honesty about lookups

Lookups are seeded master data with **no consumer**: the `lookup` table holds 0 rows and the only
reader was a dashboard card counting `values("QUESTION_DIFFICULTY")` against an empty category. The
entry is removed from both sidebars and the card from the dashboard; routes and API remain
(GAP-198). `Multi-org` is kept but is no longer a dead anchor: an entry without a route renders as a
non-link `<span aria-disabled>` with a V2 badge and the ADR-0033 reason. The public footer's
`/docs/terms|privacy|support` links resolved to nothing and now point at `/docs`.

The sidebar collapses to 56px and **persists** the choice in `localStorage`, read in a mount effect
(never during render, so SSR and first paint agree), with a compact initials mark so branding
survives collapsing and the home link keeps its accessible name.

### Verification

| Check | Result |
|---|---|
| Backend tests | **641, 0 failures, 0 errors, 2 skipped — BUILD SUCCESS** |
| Frontend tests | **585, 0 failures (100 files)** |
| Lint / type-check | 0 errors, 0 warnings / clean |
| Production build | Compiled successfully |
| Migrations live | `V39`, `V40`, `V41` applied; `primary_admin_user_id` nullable; ADMIN rule-write grants **0**; SUPER_ADMIN rule grants **3** |
| Endpoints live | `GET .../admins` 200 with 3 rows and pagination; ADMIN `GET` rules 200 / `POST` 403 |
| Routes live | 9 checked, all 200, including `/platform/organizations/{id}/admins` |

**Gaps opened:** GAP-197 (no primacy transfer), GAP-198 (lookups inert). **Closed:** GAP-199
(administrator management did not exist).

## 4j. Reversible administrator archive and primary transfer (2026-10-27)

**What changed.** The organization-administrator surface became a full lifecycle the platform owner
controls, per product direction: choose the primary administrator when adding one, change the primary
later, assign/edit/remove any number of roles, and *archive* an administrator (visible as archived,
restorable) instead of deactivating them.

| Area | Change |
|---|---|
| `V42__raise_admin_cap.sql` | `MAX_ADMINS_PER_ORG` raised `1` → `5`. A data `UPDATE`, not a schema change: the rule's code and readers are unchanged and `business_rule` has `UNIQUE(code)`. This is what makes multi-administrator configurations reachable at all. |
| `V43__platform_primary_admin_permission.sql` | Seeds `PLATFORM_ADMIN_PRIMARY_ASSIGN`, granted to `SUPER_ADMIN` only. Separate from `PLATFORM_ADMIN_ROLE_UPDATE` because transferring primacy decides *who may edit roles*, a strictly stronger act than editing them. |
| `PATCH .../primary-admin` (§72.14) | Transfers the designation. Closes **GAP-197**, which existed because the governance was unstated — the owner is now named as the decision-maker. |
| `POST .../admins/{userId}/restore` (§72.15) | Un-archives: account `ACTIVE`, ADMIN assignment `ACTIVE`, permission cache evicted. |
| `DELETE .../admins/{userId}` (§72.12) | Now sets `Status.ARCHIVED` (was `INACTIVE`), expires sessions and evicts the permission cache, and **keeps the `user_role` row `ACTIVE`**. |
| `CreateAdminRequest.makePrimary` (§72.5) | Designate the primary while creating. Implicit designation when the organization has none stays ungated — it is the only escape from ADR-0052's fail-closed state. |
| `UserRoleRepository` | Cap and admin-id queries now exclude `u.status = ARCHIVED`, so a retained role assignment occupies no slot and archive frees capacity. `INACTIVE` is deliberately still counted. |
| `updateAdminRoles` | Now calls `RolePermissionPolicy.checkAssignable` — without it the endpoint was a privilege-escalation hole (D-08 Rule A), unlike the equivalent `UserService.replaceRoles`. Validates the whole set before mutating. |
| Frontend | Status filter All/Active/Archived; "Make primary"; checkbox role editor fed by `listRoles()` excluding ADMIN/SUPER_ADMIN; Archive via `ConfirmDialog`; Restore on archived rows; create-form primary checkbox; detail screen names `primaryAdminEmail` or warns it is absent. |

**The invariant worth knowing** (documented at the query, in ADR-0054 and in `specs/04` §25.1a.1): an
archived account keeps an **ACTIVE** `ADMIN` assignment. That is deliberate, not corruption — restore
is then a one-field write needing no history table, and access is still revoked because
`findEffectivePermissionCodes` requires `user.status = ACTIVE`. Verified live: the archived account
resolved to **0** effective permissions while its role row read `ACTIVE`.

### Verification

| Check | Result |
|---|---|
| Backend tests | **633, 0 failures, 0 errors, 2 skipped — BUILD SUCCESS** (was 617; +16) |
| Frontend tests | **561, 0 failures (96 files)** (was 549; +12) |
| Lint / type-check | exit 0, clean — re-run independently, not taken on report |
| Production build | `Compiled successfully`, 82/82 static pages |
| Migrations live | `V42`, `V43` applied, `success = t` |
| Live: create with primary | `POST .../admins {makePrimary:true}` → `isPrimary=true`, `primaryAdminEmail` updated |
| Live: transfer | `PATCH .../primary-admin` moved primacy both ways; `primaryAdminEmail` followed each time |
| Live: multi-role | set `[STUDENT, STAFF]`, then removed all → `roles=[]` |
| Live: archive | `DELETE` → message "Administrator archived"; absent from the default list; present under `?status=ARCHIVED`; `adminCount` dropped to exclude it |
| Live: restore | `POST .../restore` → `status=ACTIVE`, **previous roles `[STUDENT]` intact**, back in the default list |
| Live: guards | archiving the primary → **409**; archived account holds **0** effective permissions |

**Gaps opened:** GAP-200 (archive is reversible on the platform surface but has no restore counterpart
on `/admin/users`). **Closed:** GAP-197 (no primacy transfer).

## 4k. Organization status lever, name-based breadcrumbs, and a header diet (2026-10-27)

**What changed.** Requested in six parts, all delivered:

| # | Request | Delivered |
|---|---|---|
| 1,6 | Detail descriptions removed where the breadcrumb already carries them | `PlatformPageHeader` lost its `Platform` eyebrow (it duplicated the breadcrumb root on every screen); descriptions deleted on `/platform`, `/new`, `/{id}` and made one-line elsewhere. The `/admins/new` text **"Each organization accepts exactly one ADMIN" was factually wrong after `V42`** (cap is 5) and was replaced. |
| 2 | Breadcrumbs show the organization **name**, not the UUID | `Breadcrumbs` gained an optional `labels` override plus a `BreadcrumbLabelsProvider` in `WorkspaceShell` and a `useBreadcrumbLabel` hook. The trail is still path-derived; only display text is substituted, so links are unaffected. |
| 3 | Edit actions with few fields use dialogs | New `ui/form-dialog.tsx` built on `ConfirmDialog`'s accessibility contract. The administrator roles editor moved from an inline panel into it. Rule applied: **≤3 fields and no upload → dialog**. Zero-field confirmations stay `ConfirmDialog`. |
| 4 | System info: versions + service status only | Git commit and build date removed from the screen (fields kept on the DTO). Service status now lists **four** components instead of two — `PlatformInfoService` had been filtering out `APPLICATION`, `BACKGROUND_WORKERS` and the cache fallback that the health service already computed. |
| 5 | Change an organization's status | `PATCH /organizations/{id}/status` (§72.16) under the **pre-existing** `PLATFORM_ORGANIZATION_UPDATE`. |

**The thing worth knowing:** `organization.status` **already gated authentication** —
`AuthenticationService` refuses any login whose organization is not `ACTIVE` — and
`PLATFORM_ORGANIZATION_UPDATE` was seeded and granted by `V29` while **no endpoint consumed it**. So
the platform advertised an authority it did not offer, and a tenant could only be suspended by editing
the database directly, with no audit trail. ADR-0055 gives that dead grant its purpose rather than
minting a new permission. `INACTIVE` genuinely locks out **every user of the tenant**, so the dialog
says so before applying; `PLATFORM` is refused; `ARCHIVED` is refused (the login gate collapses every
non-`ACTIVE` value, so a third state would add vocabulary without behaviour).

### Verification

| Check | Result |
|---|---|
| Backend tests | **641, 0 failures, 0 errors, 2 skipped — BUILD SUCCESS** (was 633; +8) |
| Frontend tests | **622, 0 failures (102 files)** (was 585; +37) |
| Lint / type-check | exit 0, clean |
| Production build | `Compiled successfully` |
| Live: suspend | `PATCH .../status` `ACTIVE → INACTIVE → ACTIVE` on a throwaway org; each transition returned 200 and re-read correctly. The login-blocking consequence is proven by test (`PlatformOrganizationStatusIntegrationTest`) rather than live, because the throwaway org had no pre-verified member credential. |
| Live: guards | `ARCHIVED` refused **400**; `PLATFORM` tenant refused **404**; re-setting the current status returned 200 and wrote **no** audit row (confirmed in `audit_log`: exactly 2 rows for the 2 real transitions) |
| Live: breadcrumb | the deployed bundle contains the label mechanism and both organization screens register `organizationId → name`; the substitution itself is asserted by `breadcrumbs.test.tsx` (SSR renders the client shell's skeleton, so a raw HTML fetch cannot prove it) |
| Live: system | `/platform/system` API returns **four** dependencies (`application`, `postgresql`, `redis`, `background_workers`, all `UP`); the deployed bundle contains **0** occurrences of "Git commit" and "Build date" |

**Gaps opened:** none. **Closed:** GAP-201 (no way to change an organization's status).

## 4l. Organization profile captured at creation and correctable by the platform owner (2026-10-27)

**The complaint.** *"Create organization also accepts fields which are required like address and
others."* — investigating it surfaced a larger gap than "the form is missing fields".

**What was actually wrong (ADR-0056).** `organization` has carried **ten** profile columns since `V37`
(ADR-0051): two address lines, city, state/region, postal code, country, contact email, contact phone,
website URL, logo URL. The platform organization detail screen **already displayed all ten**. And yet:

| | Before | After |
|---|---|---|
| Create request | `code`, `name`, `description` only | all 10 profile fields, bounded to the exact `V37` column widths |
| Platform-side profile edit | **did not exist** | `PATCH /organizations/{id}` (§72.17) |
| Required fields | `code`, `name` | unchanged — `code`, `name` |

The only profile write in the system was `PUT /admin/organization-config/profile`, which resolves the
organization **from the caller's own principal**. A `SUPER_ADMIN` is not an ADMIN of any tenant, so
that endpoint is structurally incapable of editing a chosen organization. A typo made at creation was
permanently uncorrectable from the super-admin surface — and the screen showed ten fields while
offering no way to fill any of them.

**Why the profile fields stay optional.** Every one of those columns is NULLable by an explicit
ADR-0051 decision: the `PLATFORM` and `DEFAULT` organizations predate the profile and a backfill
would have had to invent values. Making address or contact *required* at the API boundary would
contradict the schema without changing it, reject an institute that legitimately has no public street
address, and introduce a requirement the data model does not express. The form marks `code` and
`name` as required and leaves the rest visibly optional. The request that prompted this work assumed
address was required; it is not, and making it so would have been the wrong fix.

**PATCH, not PUT.** Omitted means *unchanged*; empty string means *cleared*. Without the second, a
stale phone number could be created and never removed. The edit deliberately **refuses** `code`,
`status` and primacy — each already has its own endpoint, permission and audit action, and a second
path to the same state is the escalation hole ADR-0054 had to close. Because the application sets
`fail-on-unknown-properties: true`, sending one of those fields is a **400**, not a silent partial
apply: the operator is told the field is wrong instead of believing it took effect.

### Verification

| Check | Result |
|---|---|
| Backend tests | **655, 0 failures, 0 errors, 2 skipped — BUILD SUCCESS** (was 641; +14) |
| Frontend tests | **622, 0 failures (102 files)** (was 585; +37) |
| Lint / type-check | exit 0, clean |
| Live: create | `POST /platform/organizations` with the full 13-field body returned 200; city/country/email/phone/website all round-tripped through `GET /{id}` |
| Live: partial | create with only `code` + `name` succeeds and leaves the profile null (test) |
| Live: edit | `PATCH /{id}` corrected name + city to `Corrected Institute`/`Mumbai`, cleared `contactPhone` to null, and left `country` untouched — true PATCH semantics |
| Live: guards | `code` in the body → **400** (`fail-on-unknown-properties`); blank `name` → **400**; `PLATFORM` tenant → 404 (test); org ADMIN → 403 (test) |

**Gaps opened:** none. **Closed:** GAP-202 (profile unsettable at creation), GAP-203 (no platform-side
profile edit). No migration: the columns and the grant both already existed.

### Follow-on requests in the same pass

**"When any form opening have cancel button as well which navigates back/close form."** Audited every
form in the app (26 `<form>` elements, 5 dedicated `/new` pages). Result:

| Form | Before | After |
|---|---|---|
| Create organization | no exit | `Cancel` link to `/platform/organizations` |
| Create administrator | no exit | `Cancel` link to `/{id}/admins` |
| Create question | no exit | `Cancel` link to `/questions` |
| Create business rule | already had `Cancel` | unchanged |
| Create user | already had `Cancel` | unchanged |
| Every dialog (`FormDialog`, `ConfirmDialog`) | already had `Cancel` | unchanged |

Deliberately **not** changed: the inline create panels (`MasterForm` in the academic managers,
`CommerceForm`, `CommunicationForm`) and the filter forms. They are not "opened" — they sit
permanently beside the list they add to, with nothing to cancel back to. Adding a Cancel there would
be a control that does nothing. The rule applied: **a form that is opened gets a way to close it; a
form that is always present does not.**

**"Permissions page have tanstack table with search box, pagination with few default values. So it's
easy to see in one page by navigation."** The permissions screen used the old fixed `DataTable` and
fetched a single hard-coded page of **200** rows, so it never paginated: the footer always read
"Page 1 of 1" and the operator had no way to move through a **169-row** catalogue. Rewritten on
`DataGrid` (TanStack Table) with **10 / 50 / 100** rows per page, Previous/Next, and a real
"Showing 1–50 of 169". Default is 50.

Live-verified against the running stack: `size=10` → 10 items, 17 pages, last page holds 9;
`size=50` → 4 pages; `size=100` → 2 pages; total **169**.

The search box stays deliberately honest: `GET /permissions` exposes **no** search parameter, so
filtering is local to the current page and the status line says so ("Filtering applies to this page
only; use page navigation to see the rest"). The previous copy admitted this in small print while the
screen looked like it searched everything — the new default of 50 rows per page narrows the trap
without pretending the API can do more than it does.

## 5. The most recent change — final verification (Step 8)

**What changed.** No code. The complete nine-step mission was verified end to end one final time.

- Backend: mvnw -q clean verify — 522 tests, 0 failures, 0 errors, 2 skipped; the Spring Boot jar
  repackages successfully.
- Frontend: npm run lint and npx tsc --noEmit clean; npm test -- --run — 86 files / 487 tests;
  npm run build succeeds and emits every public, portal, /platform and /admin route plus the /docs
  section.

**Why.** Step 8 is the closing gate: confirm the whole delivery — landing page, three login portals,
the SUPER_ADMIN and ADMIN UIs, the /docs site and the supporting backend — still passes after the
docs-content step.

**How it was verified.** Fresh, clean runs of the standard verify commands above; no migration,
endpoint, or permission was added or modified.

**Step 8 follow-up — browser test.** The full Docker stack (core + app + edge) was built and started; all six containers reach healthy. A real headless-browser smoke test (Chrome over CDP) loaded the landing page through the Nginx edge and reached the API (401 for the unauthenticated probe, as expected). It surfaced a landing-footer defect: the Terms and Privacy links pointed at `/terms` and `/privacy` (404) instead of the actual docs pages, and the footer omitted the `/support` link the design listed. Fixed in `frontend/components/public/public-layout.tsx` to `/docs/terms`, `/docs/privacy`, `/docs/support`, and the design docs (`docs/frontend/public-landing.md`, `docs/frontend/navigation.md`) were aligned. Re-verified: frontend lint + tsc clean, 85 files / 483 tests, build succeeds; the frontend image was rebuilt and the re-run browser smoke test now reports only the expected 401 (unauthenticated) and a cosmetic 404 favicon.

## 5a-1. The most recent change — the three portals are provable end to end

**What changed.** Two delivered screens did not survive a signed-in browser pass, and both are now fixed.

- **The student home page did not exist (GAP-171).** `lib/auth/role-home.ts` and
  `docs/frontend/navigation.md` both name `/student` as the STUDENT post-login destination, but the
  App Router had no `app/student/page.tsx`: every student authenticated successfully (200 from
  `/auth/login`, 200 from `/me`) and was then sent to a 404. The missing page now exists, listing the
  student-owned screens that already shipped (results, own analytics, own reports, store, access,
  purchases, announcements, notifications). It adds no endpoint, permission, or query.
- **A regression test now catches this class of defect.** `frontend/lib/auth/role-home-routes.test.ts`
  resolves every `roleHome` destination against the filesystem and fails when the matching
  `app/<route>/page.tsx` is absent, so a future role-home change cannot ship a 404 landing.
- **The smoke driver can test a portal, not just `/login`.** `scripts/smoke-frontend.mjs` gained
  `--path` (default `/login`) and now logs the path the app actually landed on, failing when a
  successful-looking login never navigates away. Previously it could only exercise the student portal,
  so the failure above was invisible to it.

**Provisioning an account per portal.** No account is seeded by design, so a fresh stack has no
working login at all — 5,008 users existed before this pass and not one password was recoverable
(every credential is bcrypt-hashed and the stack runs `FakeEmailGateway`, so a reset link is enqueued
and never delivered). Getting in therefore means provisioning, using the same audited mechanisms a
real installation uses, in this order:

| Portal | Provisioned with | Account created |
|---|---|---|
| System admin (`/login/admin`) | `scripts/bootstrap/bootstrap-super-admin.sh` (ADR-0032/0033/0034) | `superadmin@platform.test` |
| Organization admin (`/login/organization`) | `LoadCohortCli` for the account, then the ADR-0014 `user_role` grant for `ADMIN` | `demo.admin2@example.test` |
| Student (`/login` or `/login/student`) | `LoadCohortCli` (grants `STUDENT`) | `demo.student@example.test` |

`ADMIN` needs the separate grant because `ADMIN_ROLE_ASSIGN` is deliberately withheld from every role
(V3, ADR-0014, GAP-116), so no API call can create an administrator; the `user_role` insert is the
documented operational step, the same one `load/seed.mjs` performs.

The bootstrap password is never usable indefinitely: the owner starts with `must_change_password`,
so the first sign-in is forced through `/change-password` before `/platform` — which is what the
browser pass exercised.

**The live local passwords are not in this repository.** They are written to
`load/out/local-portal-credentials.md`, which `.gitignore` excludes (`load/out/`) alongside the P7
load-cohort manifests, precisely because it holds credentials. Regenerating any of them is
re-running the command above; `LoadCohortCli` re-issues a fresh password for an existing `.test`
account and refuses to touch a non-`.test` one.

**Why.** The browser pass is only meaningful if it signs in. Until now the stack had 5,008 users and
not one recoverable password — every account is bcrypt-hashed, and the stack runs `FakeEmailGateway`,
so a reset link is enqueued and never delivered. Rather than weaken anything to get in, the owner
account was created through the audited bootstrap CLI and the other two through the audited cohort
CLI, which is exactly how a real installation is provisioned.

**How it was verified.** A headless Chrome session drove the real login form on each portal through
the edge, and every one reached its own home with no console errors, no CSP or CORS violation and no
blocked request:

| Portal | Landed on | API calls after sign-in |
|---|---|---|
| SUPER_ADMIN | `/change-password` → `/platform` once rotated | `/auth/login`, `/me`, `/platform/status`, `/platform/system` |
| ADMIN | `/admin` | `/auth/login`, `/me`, two `/notifications`, `/admin/organization-config`, `/admin/dashboard` |
| STUDENT | `/student` | `/auth/login`, `/me` |

The only remaining `>= 400` response on any portal is the cosmetic `404 /favicon.ico`. Frontend
verification after the change: `npm run lint` and `npx tsc --noEmit` clean, `npm test -- --run` —
**86 files / 487 tests** (was 85 / 483; the four new assertions are the route-existence guard),
`npm run build` succeeds and now emits `/student`. Backend was untouched by this change, so its
522-test result stands.

## 5b. The most recent change — the public docs are complete (Step 7)

**What changed.** The in-product /docs site now has its full content and a build-time-generated API
reference, closing GAP-170.

- **API reference from OpenAPI.** The backend now retains its OpenAPI document as a build artifact:
  a new integration test writes /v3/api-docs to target/generated/openapi.json during verify. The
  frontend flattens that document at build time into a browsable, searchable endpoint list: every
  operation is grouped by tag and expandable to show its parameters and request body, and a filter
  narrows the list by path, method, summary or description. /docs/api therefore always matches the
  running contracts instead of drifting as prose (ADR-0036, docs-site §35–36).
- **Content.** The nine curated pages are completed: the getting-started page names the three
  portals (/login/student, /login/organization, /login/admin) and the first-sign-in password change;
  the roles page maps each role to its portal and records the one-admin-per-organization cap; and
  the configuration page documents the MAX_ADMINS_PER_ORG business rule.

The backend change is test-only (no endpoint, schema, or permission change); the frontend change is
content plus the generation pipeline (no new endpoint).

**Why.** GAP-170 (the /docs/api reference shipped as static prose instead of a build-time-generated
OpenAPI document) was deferred to this step, and the curated content had to reflect the now-shipped
SUPER_ADMIN and ADMIN surfaces from Steps 5–6.

**How it was verified.** Backend mvnw clean verify (522 tests, 0 failures, 2 skipped) including the
new OpenAPI-generation test; frontend npm run lint, npx tsc --noEmit, npm test -- --run (85 files /
483 tests) and npm run build all clean. The generated reference is rendered from the 211 KB
OpenAPI document captured from /v3/api-docs.

## 5c. The most recent change — the ADMIN screens complete (Step 6)

**What changed.** The ADMIN /admin tree is complete for user management (the priority) and the
redundant second navigation is gone.

- **Shell cleanup.** AdminShell no longer renders the old horizontal AdminNav or its own main
  element and padding — it now wraps just the page heading and the per-screen PermissionGuard,
  mirroring the Step 5 PlatformShell. admin-nav.tsx is deleted and /admin now renders the real
  dashboard instead of a navigation-only entry page, so the Step 4 sidebar is the single nav.
- **Create user** (/admin/users/new) — a Zod-validated form (firstName/lastName/email/mobile plus a
  role multi-select) posting POST /users. The role options are capped to the creatable set (ADMIN
  and SUPER_ADMIN excluded) and the frozen body carries no status and no password (onboarding
  assigns a non-usable credential; activation flows through reset, 10-P1 §56/§58).
- **User detail** (/admin/users/[id]) — name/email/mobile/roles/status with an Edit CTA.
- **Edit user** (/admin/users/[id]/edit) — firstName/lastName/mobile plus roles (SUPER_ADMIN
  excluded; self-role editing disabled). Email is shown read-only (it is the immutable login
  identifier) and status is not editable (it changes only through archive). Posts PUT /users/{id}.
- **Users list** — a "+ New user" CTA (gated on USER_CREATE) and a View link per row, alongside the
  existing server-paginated/filtered table and archive dialog.

The backend enforces the ADMIN role cap (MAX_ADMINS_PER_ORG), the last-ADMIN invariant, and
ADMIN_ROLE_ASSIGN for ADMIN-role changes; the frontend only hides non-creatable roles as a UX
affordance. The backend is unchanged.

**Why.** Steps 1–5 are approved; Step 6 delivers the priority ADMIN surface (user management) the
admin-flow.md §3–§8 specifies, and removes the duplicate AdminNav the Step 4 shell made redundant.

**How it was verified.** Frontend npm run lint (clean), npx tsc --noEmit (clean),
npm test -- --run (83 files / 479 tests, 0 failures), and npm run build (emits the new
/admin/users/new, /admin/users/[id] and /admin/users/[id]/edit routes). The backend is unchanged
from Step 3 (521 tests). No migration, endpoint, or permission was added.

## 5d. The most recent change — the SUPER_ADMIN screens complete (Step 5)

**What changed.** The SUPER_ADMIN /platform business screens now bind to the Step 3 platform API,
under the Step 4 AppShell:

- **Dashboard** (/platform) — the empty-state prompt ("No business organization yet" + create CTA)
  or live organization/administrator/user counts from GET /platform/status, plus a system summary
  card (version, migration, seeds, health) from GET /platform/system.
- **Organizations** (/platform/organizations) — a server-paginated, server-filtered, allow-listed
  sorted DataTable (code/name/status) over GET /platform/organizations, with a create action.
- **Create organization** (/platform/organizations/new) — a Zod-validated form posting
  POST /platform/organizations, then landing on the new detail.
- **Organization detail** (/platform/organizations/[id]) — code/name/description/status plus
  admin/user counts, with the one-ADMIN handover prompt when adminCount is 0.
- **Create administrator** (/platform/organizations/[id]/admins/new) — posts
  POST /platform/organizations/{id}/admins and confirms the invitation.
- **Providers** (/platform/providers) — read-only, masked email/payment configuration (configured,
  providerName, fromAddress / webhookSecretSet); secrets are never shown.
- **System info** (/platform/system) — build/migration/uptime from GET /platform/system and a
  dependency-health table.
- **Seeds** (/platform/seeds/roles, /platform/seeds/permissions, /platform/seeds/lookups) —
  read-only catalogues reusing the existing shared administration hooks (useRoles / usePermissions /
  useLookups); no endpoint or permission was invented.

Each screen is wrapped in PlatformShell (platform page heading + PermissionGuard) and gated by the
frozen PLATFORM_* permission it calls. The frontend binds through
frontend/features/platform/{schemas,api,hooks,query-keys,components}; the backend is unchanged from
Step 3.

**Why.** Steps 1–4 are approved; Step 5 delivers the install-owner surface the Step 3 contracts were
written for, reusing the Step 4 shell and the shared administration data-table/form/sorting
primitives instead of re-implementing them.

**How it was verified.** Frontend npm run lint (clean), npx tsc --noEmit (clean),
npm test -- --run (80 files / 474 tests, 0 failures — 14 new platform tests: 7 API, 4 schema,
3 dashboard), and npm run build (emits all /platform routes including /platform/organizations/[id]
and /platform/organizations/[id]/admins/new). The backend is unchanged from Step 3 (521 tests). No
migration, endpoint, or permission was added.

## 5e. The most recent change — the SUPER_ADMIN / ADMIN UI frontend complete (Step 4)

**What changed.** The mission's frontend half now exists end to end, on top of the Step 3 backend:

- The public landing page (`/`) — ten sections (Hero, What we do, Who it is for, Capabilities,
  How it works, Pricing, FAQ, Contact) under a scroll-aware header/footer, with a working
  `POST /public/contact` form (name/email/message validation, per-IP 429 surfaced as a friendly
  message).
- The three split login portals (`/login/student`, `/login/organization`, `/login/admin`, with
  `/login` redirecting to `/login/student`) — one `LoginForm` sends the ADR-0035 `portal`
  discriminator, renders `PORTAL_MISMATCH` generically, cross-links the other two portals, and
  routes each role to its home (`roleHome`, honoring `mustChangePassword`).
- The authenticated shell (`AppShell`) — collapsible 264px/56px sidebar, fixed 64px header, mobile
  drawer, breadcrumbs, global search, notification bell, theme toggle and avatar menu — gated
  client-side in the order token → `mustChangePassword` → role → permission, and wired into
  `app/admin/layout.tsx` and `app/platform/layout.tsx` with role menus (`SUPER_ADMIN_MENU`,
  `ADMIN_MENU`).
- The change-password screen (`/change-password`) — current/new/confirm, then routes home.
- The in-UI `/docs` skeleton (ADR-0036) — a safe prose-only Markdown renderer (no raw HTML/MDX,
  rule 17), server-side loading from `frontend/content/docs/` (9 pages), sidebar + scroll-spy TOC,
  and `generateStaticParams` for `/docs/[slug]`.
- Light/dark/system theme persistence with a no-FOUC inline script (the full semantic-token
  system from `design/Design-Theme.md` is not yet implemented; components use Tailwind utilities
  with `dark:` variants).

**Why.** Steps 1–3 are approved; Step 4 gives Steps 5 (SUPER_ADMIN screens) and 6 (ADMIN screens)
a real shell and public surface to build inside, and closes the landing, split-login and
change-password scope the ADRs opened.

**How it was verified.** Frontend `npm run lint` (clean), `npx tsc --noEmit` (clean),
`npm test -- --run` (77 files / 460 tests, 0 failures), and `npm run build` (emits `/`,
`/login/{student,organization,admin}`, `/change-password`, `/platform`, the `/admin` sub-routes,
and `/docs` + `/docs/[slug]` as static/SSG pages). The backend is unchanged from Step 3 (521 tests).

## 5z. The most recent change — the SUPER_ADMIN / ADMIN UI backend complete (Step 3)

**What changed.** The SUPER_ADMIN and ADMIN UI mission reached Step 3 and the backend is now complete
end to end. Eleven endpoints exist:

- `POST /api/v1/auth/change-password` (authenticated) — verifies the current password, enforces the
  platform `PasswordPolicy`, clears `must_change_password`, keeps the caller's own session while
  expiring every other session, and audits `PASSWORD_CHANGE`.
- `POST /api/v1/platform/organizations`, `GET /api/v1/platform/organizations`,
  `GET /api/v1/platform/organizations/{id}`, `POST /api/v1/platform/organizations/{id}/admins` — the
  SUPER_ADMIN business-organization lifecycle (ADR-0032, ADR-0033), with `PLATFORM` excluded from
  list/detail and the data-driven `MAX_ADMINS_PER_ORG=1` cap enforced.
- `GET /api/v1/platform/status`, `GET /api/v1/platform/system` — platform counts, seed status,
  migration/version/uptime and dependency health, with no container inventory (C6).
- `GET /api/v1/platform/providers`, `PUT /api/v1/platform/providers` — masked provider configuration
  stored in `organization_setting.configuration` for the PLATFORM org (C5, GAP-099/108).
- `GET /api/v1/admin/organization-config` (ADR-0037, ADMIN role only) — the caller's organization
  `{name, code, timezone}`, feature flags, masked provider status, migration version and app version;
  never installation-level detail or provider secrets.
- `POST /api/v1/public/contact` (ADR-0037, public) — validated, per-IP rate-limited (in-memory fixed
  window, tunable via `app.contact.rate-limit.*`), and recorded as a `CONTACT_REQUEST` background job
  (no user, no business data).

The login `portal` discriminator (ADR-0035) now enforces the split login: an absent portal keeps the
pre-existing behavior, an unknown portal is `INVALID_PORTAL` (400), and a wrong portal is a generic
`PORTAL_MISMATCH` (401) that never names the correct one. Two invariants land in `UserService`: the
SUPER_ADMIN owner cannot be archived or have its roles modified (`SUPER_ADMIN_IMMUTABLE`, ADR-0032
§18) and an organization accepts at most one ADMIN (`ADMIN_CAP_EXCEEDED`, data-driven through
`MAX_ADMINS_PER_ORG=1`, ADR-0032 C3), enforced on both the create and role-replacement paths.

Supporting machinery: `MustChangePasswordFilter` (server-side forced first-login gate, ADR-0034),
`EmailPasswordResetDelivery` (`@Primary`, wires reset and first-password links through the
Communication email pipeline, closing GAP-112), `SuperAdminBootstrapService` +
`SuperAdminBootstrapCli` (environment-gated, idempotent, off-HTTP installation-owner creation), and
migrations V29 (SUPER_ADMIN role + PLATFORM org + PLATFORM_* permissions + MAX_ADMINS_PER_ORG), V30
(`user.must_change_password`), V31 (`{{resetLink}}` in the PASSWORD_RESET template). No migration was
needed for Step 3's remaining deltas: `CONTACT_REQUEST` reuses the frozen `background_job` table's
`VARCHAR` `job_type`.

**Why.** Steps 1 (ADR-0032/0033/0034/0035/0036/0037) and 2 (design docs) were approved; Step 3
implements the eleven contracts in `specs/05-API-Contract-V1.5.md §72/§73` so Steps 5/6 (the
SUPER_ADMIN and ADMIN UIs) have an authoritative backend to bind to.

**How it was verified.** `backend/mvnw.cmd clean verify` — BUILD SUCCESS, **521 tests, 0 failures,
2 skipped** (14 added in five classes: `LoginPortalIntegrationTest`, `SuperAdminImmutabilityIntegrationTest`,
`AdminCapIntegrationTest`, `AdminOrgConfigIntegrationTest`, `PublicContactIntegrationTest`; the P11
multiple-admin test in `IdentityRoleApiIntegrationTest` was rewritten to assert the cap).

## 5x. The most recent change — the frontend could not reach the API from a browser, and the cause was two layers deep (GAP-164, GAP-165)

**The report.** A user opened the frontend, the page rendered, and every API call failed with
*"Unable to reach the server. Check your connection and try again."* That message comes from exactly
one place: `normalizeApiError` returns it only when Axios has **no response at all**
(`frontend/lib/errors.ts`). So the browser never completed an HTTP round trip; nothing in the
rendering layer was involved.

**Diagnosis.** Every layer that curl can see was already healthy: six containers up, the API
answering `401` through the edge and `/health` answering `200`, readiness including the database
check through PgBouncer, and `X-Request-Id` propagated. The bundle did contain
`NEXT_PUBLIC_API_BASE_URL`, so the naive reading was "configuration is fine". Two defects only
became visible once a **real browser** was placed in the loop.

1. **GAP-164 — the bundle inlined an absolute origin.** `NEXT_PUBLIC_*` values are baked in at
   `next build` time, and the image was built with `http://localhost:8081`. The page therefore called
   that exact origin no matter which host had served it. Opened at `http://127.0.0.1:8081`, the
   request became cross-origin and the edge's `connect-src 'self'` policy blocked it. Reproduced in
   headless Chrome, which reported verbatim:
   `Connecting to 'http://localhost:8081/api/v1/auth/login' violates the following Content Security
   Policy directive: "connect-src 'self'"`, with `loadingFailed blockedReason: csp`.

2. **GAP-165 — Nginx stripped the port from `Host`.** The edge proxied with
   `proxy_set_header Host $host`, and `$host` omits the port. The application therefore saw itself
   as `localhost` while the browser's `Origin` was `http://localhost:8081`; Spring's CORS filter
   judged a same-origin call to be cross-origin and rejected it with `403 Invalid CORS request`,
   which is *not* the JSON envelope and so was not even a shaped error. Proven by a controlled
   experiment: the identical login request sent straight to the backend returned
   `Invalid CORS request` with `Host: 127.0.0.1`, and a normal `401` with `Host: 127.0.0.1:8081`.

The second defect had been masked by the first: while the build-time origin was absolute the call
really *was* cross-origin, so the wrong `Host` never mattered. Fixing only the bundle moved the
failure from a CSP block to a CORS 403 — which is why both are recorded as separate gaps.

**What changed.** The API base is now the relative path `/api/v1`, resolved by the browser against
whatever origin served the page, so the UI and the API are always same-origin under the edge and
CORS is not on the path at all. `resolveApiBaseUrl` accepts an empty value, a relative base, or an
absolute origin, and appends the version prefix exactly once, so no configuration can produce
`/api/v1/api/v1`. Nginx now forwards the client's `Host` verbatim, port included, via a
`map $http_host $edge_host` that falls back to `$host` only for an HTTP/1.0 request with no `Host`
header. The loopback IP origin was added to `CORS_ALLOWED_ORIGINS`, because `http://127.0.0.1:8081`
and `http://localhost:8081` are different origins to a browser.

**No security header was weakened.** The CSP is unchanged and still `connect-src 'self'`; the fix
makes the same-origin claim *true* rather than loosening the policy that checks it. CORS was
configured, not removed — and the verification now asserts both directions: a trusted origin is
answered and an untrusted one is refused with `403`. The backend is still not exposed to the
browser; every path goes through the edge on its single port.

**What now proves it.** `scripts/verify-stack.sh` gained five browser-layer assertions and now
**fails the run** on any failure (it previously printed `FAIL` and still exited `0`, which is how a
broken assertion could sit unread). `scripts/smoke-frontend.sh` plus `scripts/smoke-frontend.mjs`
drive a real headless Chrome over the DevTools protocol — no Playwright dependency — loading
the page, running its JavaScript, capturing every console message and blocked request, and either
logging in through the form or probing the API from the page's own context. Verified: **31/31
assertions, exit 0**, and an authenticated end-to-end login from both `localhost` and `127.0.0.1`
(`200` on `/api/v1/auth/login` then `200` on `/api/v1/me`).

**What is newly open.** Nothing. **GAP-015** keeps only its HTTPS/HSTS half open, with its CORS half
now verified on a running stack; **GAP-137** is recorded as verified at the edge rather than merely
configured.

## 5a. The most recent change — five contract gaps closed with three ADRs (GAP-141a, GAP-142, GAP-154, GAP-155, GAP-157)

*(2026-10-23.)*

Five small, independent contract gaps. Each needed a documented decision before code, because each
either touched a frozen contract or could only be closed by inventing one.

**GAP-141a — three commerce reports added (ADR-0029).** `22-P13 §55` names seven candidate commerce
reports; the frozen catalog registered four. `COMMERCE_SUBSCRIPTIONS`, `COMMERCE_COUPONS` and
`COMMERCE_REFUNDS` are now registered under the existing `REPORT_COMMERCE_READ` permission — one
financial capability does not need four permission codes. The honest constraint is the schema:
there is no `subscription` table, so Subscription Summary is **plan-level** (§59 forbids inventing a
recurring billing model), and there is no `refund` table, so Refund Activity reports the
authoritative refund **state** already frozen on `purchase` / `payment_record` (§62). Filter schemas
declare only filters the providers honour, because a declared-but-ignored filter on a financial
report would silently return unfiltered data.

**GAP-142 — student reporting is permission-based (ADR-0030).** The student screen existed but was
unreachable: it is gated on `REPORT_STUDENT_READ`, which no student held. The older comment claimed
students must hold *no* report permission. That was stricter than the contracts: `10-P1 §9` says not to
encode business authorization as `if role == …`, and §66 defines the student set as "self-service
session and current-user permissions only" — which a self-service report read satisfies. `V28` grants
exactly one permission to `STUDENT`. Self-scoping was already enforced twice over
(`ReportService.scopedFilters`, `ReportCriteria.scopedSubject`), so this closed the authorization
question, not a security hole.

One existing test **did** assert the stronger claim: `IdentityAuthenticationIntegrationTest
.aStudentHasNoAdministrativePermissions` required an empty permission set. It was not deleted — its
assertion was rewritten to pin the exact set to `["REPORT_STUDENT_READ"]` and to assert the absence of
`USER_*`, `ROLE_*` and `AUDIT_*` permissions, which is what its name actually intends. The full
before/after is recorded in ADR-0030 under "Changed test assertions".

**GAP-154 / GAP-155 — by design, with the reason recorded (ADR-0031).** `§110` wants a version
created date, but the frozen `question_version` table has **no `created_on` column** — there is
nothing to expose, and substituting `submitted_for_review_on` would silently misreport drafts.
`§105` wants asset preview, but the frozen asset contract has no content route, and reconstructing a
path from `file_path` is exactly the workaround the spec forbids. Both panels state the absence and
list what does exist; ADR-0031 records the amendment that would be required to change either.

**GAP-157 — the failed-snapshot sweep, and two defects it exposed (no ADR; no endpoint).**
`retryFailed` had no caller, so a snapshot left `FAILED` by an interrupted preparation blocked that
candidate's start until an operator edited SQL. `SnapshotPreparationWorker` now runs a bounded sweep
each tick, gated by the `DELIVERY_SNAPSHOT_RETRY_SWEEP` business rule (absent or non-ACTIVE means
**disabled** — the documented safe default, P11 §9.4). No endpoint was added. The sweep is bounded
three ways because the frozen schema stores no retry counter: batch size per run, a cool-down since
the last failure, and a maximum age from attempt creation, after which the attempt stays FAILED for a
human.

Proving the sweep surfaced two real defects:

1. **Every retry was doomed.** `retryFailed` reset the status but never cleared the partially
   materialized snapshot from the failed run. `attempt_question` carries
   `UNIQUE(attempt_id, question_version_id)` and `UNIQUE(attempt_id, display_order)`, so the
   re-prepare collided and failed forever — the retry path had never worked. Both the explicit retry
   and the sweep now delete the stale snapshot inside the same transaction as the reset.
2. **The sweep is global.** The frozen schema has no per-attempt retry bookkeeping, so a
   permanently-broken attempt would have been retried forever; the maximum-age bound is what makes
   the loop finite.

**Counts.** Migrations 27 → 28 (`V28`). Report catalog 23 → 25 types. Endpoints unchanged at 237.
Backend tests 464 → 477 (13 added). Release decision unchanged (**NO-GO**): this work touches none of the
latency gate.

**Verified.** `backend/mvnw.cmd -o verify` — BUILD SUCCESS, 477 tests, 0 failures, 2 skipped.

## 5b. The most recent change — the rc2 P7 gate is re-run and still fails on the start path (GAP-130, GAP-159–GAP-163)

*(2026-10-23.)*

The P7 performance gate was re-run on an **immutably tagged** candidate, which is what the previous two
attempts lacked (GAP-083).

**Candidate.** `v1.5.0-rc2` → `f07acfd`, carrying `27fa543` (GAP-156 batching), `9cd38ac` (GAP-059
start-path trim) and `f07acfd` (GAP-083 environment). Deployed images: backend
`sha256:cb17c03dca41dc48253123762b3e1fe53c9437abcd99d2e6db63574116c8cd6c`, frontend
`sha256:6b5a05edd607b79ba53f35be956ca540e42a3d9fae154aba00ea0352b72ca439`.

**Result.** Integrity passes completely; latency fails by an order of magnitude on `attempt.start` and
`attempt.questions`, at every step, from the smallest. Full detail and the reproduction commands are in
`docs/testing/p7/load-2026-10-23-rc2.md`.

**What was fixed to make the run meaningful.** Four defects, all of which would have silently
invalidated the evidence:

1. **GAP-159** — `load/seed.mjs` could not produce a working environment from an empty database. The
   provisioner only ever grants `STUDENT` and `ADMIN_ROLE_ASSIGN` is deliberately ungranted (GAP-116),
   so a fresh database has **no administrator**; the harness step that used to insert the role was
   removed in `e4c008e`, leaving every ADMIN call at `403`. The seed also never published an **answer
   key**, so submitted attempts were stored and never evaluated. Fixed by restoring an idempotent
   `grantCorpusRoles()` and adding `load/publish-answer-key.mjs`, which resolves `correct_option_id`
   from `question_option.is_correct` (rule 19) and refuses to publish a partial key. The credential
   builder also used to fall back to `studentPassword` for the corpus author and reviewer, who are
   `STUDENT` members with prepared attempts — they received another account's password and were the
   first two logins. The ramp cohort is now restricted to the student list.
2. **GAP-161** — the evaluation worker never ran. `EVALUATION_WORKER_ENABLED` defaults to `false`
   (a leftover from GAP-071, closed but never revisited) and `EvaluationWorker` called
   `jobRepository.claim(...)` directly from a `@Scheduled` method, so every tick threw
   `TransactionRequiredException`. 992 jobs were queued with 0 evaluations. Routed all state
   transitions through `JobStateService` (which exists for exactly this and which every other worker
   already used), set the flag, and added a test that pins the transactional route. Verified: 992/992
   jobs completed, 0 failures.
3. **`scripts/p7/reconcile.sql`** used bare `:schedule_id` for UUIDs, so psql substituted them unquoted
   and **every run failed** with `trailing junk after numeric literal`. All UUID substitutions are now
   quoted. The reconciliation gate had never actually executed.

**What is newly open.** **GAP-160** — the edge publishes `listen 80` with no `backlog=`, so 500+ VUs
opening their first connection in the same second overflow the listen queue and are refused (4,786 at
5,000 users, all in the first second; only 214 of 5,000 students started). The 5,000-user measurement
is therefore **not a valid population measurement**, and this must be fixed before the gate can be
assessed at 5,000. **GAP-162** — `start_burst_zero_new_rows` does not exclude
`snapshot_status = 'FAILED'`, so preparation failures are reported as Start-Exam snapshot writes (it
flagged exactly the two attempts whose preparation died during a backend restart; no started attempt
was missing questions). **GAP-163** — `load/k6/summary.js` derives `applicableLatency` from the
untagged `http_req_duration`, mixing all eight endpoints; the resulting global p95 (48.6 ms at 100
users) is dominated by the 99% of requests that are fast and **masks** the per-endpoint breach, and
k6's `stopOnGateFailure` evaluates the same global figure so the ramp never stopped on it.

**Release decision unchanged: NO-GO.**

## 5c. The most recent change — a production-like load environment exists (GAP-083)

*(2026-10-23.)*

**Why this was needed.** Every load number in this repository up to now came from
one co-resident development host: k6, the backend, PostgreSQL and Redis sharing
the same CPU. That made the results useless as capacity claims, and both
`docs/testing/p7/load-2026-10-22.md` and `specs/16-P7` §12–§13 say so. GAP-083
recorded the absence of a production-like environment as a blocker on the P7
scale suite and on any immutable release candidate.

**What now exists.** `infrastructure/environments/production-like/` — a separate
Compose environment with five disjoint tiers, each pinned to its own CPU set with
a memory ceiling:

| Tier | Cores | Services |
|---|---|---|
| data | `0-3` | PostgreSQL 16, Redis, postgres/redis exporters |
| edge | `4-7` | Nginx, PgBouncer, pgbouncer exporter |
| app | `8-15` | Spring Boot, Next.js |
| generator | `16-21` | k6, as its own Compose project |
| observability | `22-23` | Prometheus, Grafana, Loki, promtail |

Four bridge networks enforce the boundaries rather than describing them, so the
generator is on the edge network only and holds no database credential. Nginx is
the sole published port. The choice (co-resident VMs on one physical host rather
than cloud or bare metal) and its limits are documented in the environment's
`README.md`; the allocation and *measured* headroom are in `sizing.md`.

**Two real defects were found by building it, not by reading it.**

1. **The edge could not serve 5,000 users.** The committed `nginx.conf` sets
   `worker_connections 1024`, a development default. Each proxied request uses two
   connections, so the ceiling was ~1,024 concurrent users; the first 5,000-VU run
   failed 93.5 % and Nginx logged `1024 worker_connections are not enough`. The
   environment rewrites only that directive at start-up (to 16384), keeping the
   shared configuration authoritative for everything else.
2. **The local stack's Prometheus has never worked.** It is started with
   `--config.expand-env`, which is a *Loki* flag; Prometheus 2.54.1 rejects it and
   crash-loops. The observability profile therefore never produced a scrape of the
   local stack, and the GAP-138 credential design was never exercised locally.
   Recorded as **GAP-158**; the local stack was deliberately left alone to keep
   this change scoped.

**Measured, not assumed.** The generator drives the full population: 5,000
`constant-vus` sustained at **5,078 req/s with 0.00 % failures**, using 1.14 GB of
its 4 GB but ~85 % of its 6 cores. Memory has headroom; **CPU does not**. And the
scaling curve shows a knee: 2,500 VUs gave 8,463 req/s at p95 330 ms, while 5,000
VUs gave 5,078 req/s at p95 1,650 ms — doubling load *reduced* throughput by 40 %.
That is queueing saturation, and it means a 5,000-user result on this host will
show this host's knee rather than a platform defect.

**What this does and does not settle.** It removes the co-residency objection: the
generator no longer competes with the stack it measures, the pooler is sized at
2,000 clients / 100 pool instead of 200/20, and `scripts/verify-production-like.sh`
passes **31/31**. It does **not** produce a 5,000-user pass: the edge saturates at
that load on this host, and the Docker VM here has 8 GB against a ~10.2 GB budget,
so the measured headroom is genuinely small. **GAP-083 is closed**: the staged re-run executed on
2026-10-23 against `v1.5.0-rc2`, and its evidence is `docs/testing/p7/load-2026-10-23-rc2.md`.

## 5d. The most recent change — the Start Exam path is profiled and trimmed (GAP-059)

*(2026-10-23.)*

**The claim that needed checking.** GAP-059 recorded `attempt.start` p95 at 415 ms for 100 users and
3,107 ms at 5,000, and the working assumption was that Start Exam was issuing "about 30–50 statements"
because of an unbatched metadata read, a re-resolved `DeliveryConfiguration`, and a heavy entitlement
gate.

**What measurement showed.** That assumption was wrong. Start Exam was already issuing **11 statements
(free) and 13 (paid)** on a warm single session, and 16–17 ms of wall time — nowhere near 30–50, and
already inside the ≤30 ms single-session target before any change. The individual suspects were all
clean: the `ExamEngineRegistry` is an in-memory `EnumMap`, `RuntimeConfigurationService` is pure
computation over an already-loaded policy, and the entitlement gate was already three indexed reads. An
11-statement path cannot produce a 3,107 ms p95 by itself.

**What was actually removable.** Four round trips were redundant and are gone:

| Change | Effect |
|---|---|
| Schedule and published version read in one join (`requireScheduleBinding`) | −1 statement |
| Redundant attempt re-read after a won `NOT_STARTED → IN_PROGRESS` race | −1 statement |
| Entitlement package-mapping read and grant count merged into one join (`findPackageGrants`) | −1 statement |

Result: **free 11 → 9 statements, paid 13 → 10**, both under the ≤10 ceiling, with the
`NOT_STARTED → IN_PROGRESS` conditional update and the session insert still done as written (two round
trips, which is the intended shape). Measured on a warm single session: **p50 13.0 ms, p95 19.7 ms, p99
20.0 ms**. The untouched cold first-request measure was p95 62.4 ms, which is JIT and pool warm-up rather
than query cost.

**What this does not fix, stated plainly.** The 3,107 ms figure at 5,000 users cannot be attributed to
statement count, and it is not fixed here. The 2026-10-23 ramp showed the host saturating on
`auth.login` (bcrypt, strength 10) before start became the bottleneck, against `DB_POOL_MAX_SIZE=10` in
front of PgBouncer. The remaining distance is capacity and CPU, not removable round trips — so GAP-059
stays **Residual**, not Closed, even though its query-cost component is now bounded and enforced.

**Behaviour preserved.** No schema, migration, endpoint, engine or authorization change. The entitlement
decision keeps identical semantics (free iff no ACTIVE package grants the assessment; otherwise a valid
grant is required), the snapshot READY check is untouched, and Start Exam still inserts zero
`AttemptQuestion`/`AttemptQuestionOption` rows — now asserted, along with the statement ceiling, in
`StartAssessmentQueryBudgetIntegrationTest` (5 tests: free, paid, denied, repeated, and the
zero-snapshot/no-scaling invariant).

## 5e. The most recent change — the load gate re-measured after the batching fix (GAP-130)

*(2026-10-23. Evidence only; no production code changed in this step.)*

**Why re-run.** GAP-156 removed the per-question N+1 from `GET /attempts/{id}/questions` — the endpoint
that GAP-130 had identified as the dominant cause of its failed p95 gate. The fix was measured in isolation
(309 → 10 statements, 84 → 27 ms), but an isolated improvement is not a load result. The P7 `mixed` ramp was
therefore re-run against the fix.

**Result at the 100-user step, the one step both runs executed.**

| Metric | Before (`c966d1c`) | After (`27fa543`) | Change |
|---|---|---|---|
| `attempt.questions` p95 | 1,962 ms | **861 ms** | **−56%** |
| `attempt.questions` p50 | 1,210 ms | 653 ms | −46% |
| `attempt.read` p95 | 93 ms | 65 ms | −30% |
| `response.save` p95 | 109 ms | 67 ms | −39% |
| `auth.login` p95 | 911 ms | 1,240 ms | +36% |
| Gateway p95 | 332 ms | 368 ms | +11% |

Integrity was clean throughout: 100/100 submissions, 0.00% exam failure, 0.00% HTTP failure, and
`snapshotDelta` exactly `+0/+0`, so Start Exam materialized nothing (P7 §27).

**The finding that matters.** The endpoint under investigation is fixed and has stopped being the
bottleneck, but **the gate still fails, 3.7× over target**, and the reason has moved. The dominant term is
now `auth.login` at 1,240 ms p95, with `attempt.start` at 935 ms behind it. Login cost is structural:
`SecurityConfig.passwordEncoder()` is the delegating encoder, i.e. bcrypt at strength 10, which is
*deliberately* tens of milliseconds of CPU per verification. At 100 concurrent logins on an 8 GB VM the CPU
saturates and requests queue. That is a capacity and login-hardening problem, not a query-count problem, and
it is outside GAP-156's scope.

**What was not done.** The full seven-step ramp was not re-run. The runner stops at the first threshold
breach rather than escalating (P7 §33), and it stopped at 100 users. Completing 500 → 5,000 would have cost
hours without adding information about the endpoint under test, which had already improved and was already
not the bottleneck. The consequence is recorded honestly: the 2,000-user step where `attempt.questions` was
3,534 ms p95 has **no after-value**.

**Dataset work required to get a valid measurement.** Three harness faults had to be fixed first, each of
which produced invalid numbers until corrected: the credential manifest contained (student, schedule) pairs
that were already submitted, so 100% of starts failed; the snapshot worker was still preparing unrelated
schedules during the burst, which the runner correctly read as Start Exam materializing snapshots
(`attempt_question +19,700`) and aborted on; and the original cohort credentials no longer matched their
hashes. The dataset was extended through the frozen HTTP contract and `LoadCohortCli` (GAP-085), never by
direct SQL. Evidence: `docs/testing/p7/load-2026-10-23.md`.

**Release decision unchanged: NO-GO.** GAP-130 remains open as a Residual improvement: the specific
endpoint defect is closed, the platform still misses p95 < 100 ms.

## 5f. The most recent change — the snapshot read is batched (GAP-156)

*(2026-10-23. One internal read path changed. No contract, schema, endpoint or migration changed, and
the response payload is byte-identical.)*

**The problem.** `GET /attempts/{id}/questions` assembled the attempt from the snapshot in two batched
reads, and then undid that work: for each question it called `contentCatalog.runtimeQuestion(...)`,
which runs four queries and re-renders every option through the LaTeX pipeline. For immutable published
content that never changes for a given version. A 100-question read therefore cost **309 SQL statements
and ~400 renders**, while the snapshot SQL underneath it was 0.08 ms — the cost was entirely in the
loop, not in the query. This one endpoint was the largest single contributor to the GAP-130 p95 failure.

**The fix.** `ContentCatalog` gained a batch method, `runtimeQuestions(List<UUID>)`, returning the whole
materialized map. It performs a fixed number of reads no matter how many versions are asked for: one
join returning each version with its template's configuration JSON (so `requiresLatexParsing` costs no
extra query), and one read for all options of all versions, ordered by `display_order` in SQL and
grouped in memory so option order survives without a per-version sort. `AttemptQueryService.questions`
now calls it once and passes the resolved content into `toView`; the single-question endpoint keeps
using `runtimeQuestion` unchanged. Empty input short-circuits without touching the database.

**Why a batch rather than a cache.** A Redis cache keyed by `questionVersionId + contentFormat +
rendererVersion` is permitted (`12-P3 §62, §131`) and would also have removed the cost, but it makes the
common case depend on a cache hit while leaving the miss path slow, and it adds invalidation surface for
no benefit: the batch read is already fast and every deployment stays correct with Redis down. The
render itself is still performed per option; at 10 statements the read is no longer query-bound.

**Measured, on the development host, 100-question assessment.**

| | Before | After |
|---|---|---|
| SQL statements | 309 | **10** |
| Server-side latency | 84 ms | **27 ms** |

The statement count is flat: a 10-question read costs the same 10 statements as a 100-question read,
which is the property that separates a batch from a cheaper N+1. Of the 10, two are the content batch
itself (versions-with-template, then options); the other eight are session, attempt, schedule,
assessment version, section names, delivery policy, snapshot rows and snapshot options.

**Verified.** `mvnw verify` passes, 457 tests, 0 failures. Two new integration tests carry the guarantee:
`AttemptQuestionsQueryBudgetIntegrationTest` asserts the ≤10 ceiling and that a smaller attempt costs
the same number of statements, and `AttemptQuestionsPayloadEquivalenceIntegrationTest` compares every
field of the frozen view against the database directly, proves the batch and single-question paths
return identical payloads, and confirms no correct answer or asset path leaks.

**Not claimed.** The measurement is single-session on a development host; the 5,000-user ramp (GAP-130)
has not been re-run, so the p95 gate remains open until it is. The fix removes this endpoint's
per-question cost, which was the dominant term, but it does not by itself prove the system now meets
p95 < 100 ms.

**Release decision unchanged: NO-GO.** GAP-130 is still the trigger. This closes a large part of its
cause; it does not close the gap.

## 5g. The most recent change — release candidate v1.5.0-rc1 (GAP-140, GAP-133)

*(2026-10-23. No product code changed. This pins the candidate to immutable identifiers and rehearses the
migration chain at production data volume. Two images were built; one migration was re-run against large
tables and timed.)*

**The problem.** P12 §11 requires a candidate to be identifiable by application version, frontend version,
database migration version, container image digest and configuration revision. None of the container
identifiers existed: no tag, no image, no digest, so no evidence could be tied to an immutable artefact.
Separately, every migration had been proven to *apply*, but none had been timed against a production-scale
table, so the duration, lock behaviour and expand/contract safety of large-table DDL were unknown.

**What was done.** The candidate is tagged `v1.5.0-rc1` at commit `c966d1c`, and both images were built from
that commit's own Dockerfiles:

| | Identifier |
|---|---|
| Commit | `c966d1c2664eb5bdf5849ab163691ea02a8471f8` |
| Tag | `v1.5.0-rc1` (annotated, on that commit) |
| Application / frontend version | `1.5.0-SNAPSHOT` / `1.5.0` |
| Migration version | `27` |
| Backend image | `sha256:e1d015257c1b54988b61ee59f53cf9604c8436fe6e6230903655a1d9e93a14b4` |
| Frontend image | `sha256:7b0c1c713c7cbaa6861cfc6cfcb5f9c132a9609799a375e7c8f3cd5e7534af2b` |

The rehearsal ran on `local-docker-single-host` — Postgres 16.15 in Docker, 8 GB VM on a 24-core / 15.71 GB
host — after inflating the two tables V27 touches to **4.4M `background_job` rows (420 MB heap)** and **5M
`notification` rows (802 MB heap)**, with `fk_notification_user` left enforced rather than disabled to make
seeding convenient.

**The result, and the finding inside it.** V1–V26 applied 26/26 in 0.929 s; V27 applied in 10.407 s; final
state version 27, 27/27 successful, nothing failed or pending. The indexes are not merely created but
*chosen* at volume — 0.078 ms on `background_job`, 0.146 ms on `notification`, neither with a sort step.

The rehearsal was worth running because it changed a deployment instruction from advice to evidence. GAP-133
asked about lock behaviour, and on a table this size the non-concurrent `CREATE INDEX` that V27 uses **blocks
concurrent writes for the entire build**:

| Build method | Build duration | Concurrent writer blocked |
|---|---|---|
| `CREATE INDEX` (V27 as written) | ~10.2 s | **9,749 ms** |
| `CREATE INDEX CONCURRENTLY` | ~12.6 s | **104 ms** |

ADR-0028 already said production should use an out-of-band `CONCURRENTLY` build; it now records the measured
consequence. The migration is safe to ship as written for small tables, but the production runbook must not
simply `flyway migrate` it at volume.

**Corrections to the brief, recorded rather than papered over.** The task described the working tree as dirty
with GAP-126 uncommitted. It was not: `git status` was already empty and GAP-126's lock repair was in
`c966d1c`. No empty commit was invented and the commit was not amended. The candidate commit's message (`H2`)
does not describe its contents — left uncorrected because `AGENTS.md` forbids history rewriting and the brief
forbids amending once tagged. Docker was described as unavailable; it was in fact running (29.8.2), which is
why the rehearsal was possible at all.

**What this does not do.** The digests are locally built, not registry digests, so a third party cannot yet
reproduce the images. The base images are tag-pinned, not digest-pinned. No staging deployment or P12 §145
smoke test was run for this candidate.

**Release decision unchanged: NO-GO.** GAP-130 remains the trigger — the measured p95 was 4.3× over the
<100 ms target across the whole P7 ramp. A reproducible candidate is not a passing candidate. This change
closes the identity gap, not the performance gate.

## 5h. The most recent change — the worker and notification index amendment (GAP-139)

*(2026-10-23. Schema amendment plus one migration. Two indexes are added; no table, column, constraint,
endpoint, or existing index is changed. SQL only — no Java or frontend behaviour changed, and the
application code issuing both queries is untouched.)*

**The problem.** The P12-04 index review found that the two highest-volume read paths filter on two
columns and order by a third, while the frozen schema offered only single-column indexes. That is a
shape a single-column index cannot serve: PostgreSQL narrows on the first predicate, then reads every
matching heap row, discards the ones that fail the second predicate, and sorts the survivors. Both
queries therefore degrade as history accumulates rather than staying proportional to the page being
returned. Because the frozen index list is explicit (`specs/04 §33.1, §35.1`), the review correctly
recorded this as an amendment candidate rather than changing it in-phase.

**What was built.** `ADR-0028` records the amendment; `V27__add_worker_and_notification_indexes.sql`
applies it:

```sql
CREATE INDEX idx_background_job_type_status_queued
    ON background_job (job_type, status, queued_on);
CREATE INDEX idx_notification_user_status_created
    ON notification (user_id, notification_status, created_on);
```

Equality columns first, ordering column last, so the trailing column arrives pre-sorted and the sort
step disappears. `created_on` is declared `ASC` even though the notification query orders `DESC`,
because PostgreSQL scans a B-tree backwards at no cost and one index then serves both directions. The
seven frozen single-column indexes are **retained**: they still serve admin counts, per-user reads
without a status filter, report history, the idempotency lookups, and the FK checks. Rollback is
`DROP INDEX` on both, which returns the schema to exactly the frozen lists.

**A correction to the gap's own wording.** GAP-139 described the worker *claim* as index-served. It is
not: the claim is `UPDATE background_job … WHERE id = :id AND status = 'QUEUED'`, which uses
`pk_background_job` and already touches one row. What the composite index improves is the candidate
selection that runs immediately before the claim. The register row now says so.

**How it was verified, including what the measurement overturned.** Eight tests in
`backend/src/test/java/com/company/exam/integration/performance/` — `WorkerIndexPlanIntegrationTest`
(7, the gate) and `WorkerIndexPlanCaptorTest` (1, regenerates the evidence). Plans were captured with
`EXPLAIN (ANALYZE, BUFFERS)` on the embedded PostgreSQL instance and recorded in
[`docs/testing/p12/worker-index-plans.md`](testing/p12/worker-index-plans.md).

The first two measurement attempts produced wrong answers, and the file keeps both because the
distinction is the finding:

- a seed that interleaved `QUEUED` rows uniformly through history made the composite index look
  chosen while the baseline was *already* 0.099 ms — the index was doing nothing, and the seed was
  flattering it;
- a deep history with a shallow 100-row backlog made PostgreSQL **decline** the composite index and
  use `idx_background_job_status`, which is the correct plan at that size. The two worker-plan
  assertions failed on that shape, which is why the gate now seeds the shape the index exists for.

Measured results: the **notification list** improves unconditionally, 0.994 ms / 230 buffers →
0.088 ms / 8 buffers (~11×, sort eliminated), because its predicate and ordering both match the index.
**Worker candidate selection** improves conditionally, 21.337 ms / 1,576 buffers → 0.084 ms / 4 buffers
(two orders of magnitude; ~120–250× across runs, ~45,000 rows no longer discarded), but only under a
deep multi-type backlog. It is recorded as
backlog insurance rather than a blanket speed-up, because with a shallow queue the index is pure write
cost.

Full backend suite: **449 tests, 0 failures** (441 before). Timings are single-run on one Windows
development host and are not a capacity claim (P7 §12, P12 §56).

**Release decision unchanged: NO-GO.** GAP-130 remains the trigger — the measured p95 is 4.3× over
target, and indexing two worker queries does not address the `attempt.questions` N+1 behind it.

## 5i. The most recent change — P12 security hardening (GAP-136, GAP-137, GAP-138)

*(2026-10-23. Configuration and security configuration only. No table, column, migration, business
endpoint, or frozen contract changed: the public business endpoints — login, forgot-password,
reset-password, and the provider-signed payment callback — behave exactly as before. What changed is
what the infrastructure surface exposes to an unauthenticated caller.)*

**The problem.** The P12 production configuration review recorded three ways the running system
described itself to callers who had not authenticated. `/v3/api-docs/**`, `/swagger-ui/**` and
`/swagger-ui.html` were in `SecurityConfig`'s public allow-list, so production published the entire API
surface — every endpoint, parameter, schema and enumeration — before an attacker sent a single probe.
No `Content-Security-Policy`, `Strict-Transport-Security`, or `Referrer-Policy` was configured at
either tier, so only Spring Security's defaults applied. And `/actuator/prometheus` was public, which
published request rates, error counts, JVM internals, pool saturation and worker backlog to anyone who
asked.

**What was built.**

*API-documentation exposure (GAP-136).* Three independent controls, because any one alone is
defeatable. `application-prod.yml` disables `springdoc.api-docs.enabled` and
`springdoc.swagger-ui.enabled`, so the document is not even built. `SecurityConfig` removes the four
documentation paths from the public list and gates them on `SecurityExposurePolicy.apiDocsReachable()`.
The policy refuses production when **either** `app.environment` says so **or** a `prod`/`production`
profile is active — the `docker-compose` stack sets `APP_ENVIRONMENT=staging` in the container
environment, which outranks the profile YAML, so a process started with
`--spring.profiles.active=prod` reports itself as staging. That exact combination produced GAP-085;
the gate treats it as production and carries a regression test asserting the premise (environment
reads `staging`, `prod` is active, docs are still refused). Swagger UI remains fully usable in `local`
and on the `staging` stack, so the local developer experience is unchanged.

*Security headers (GAP-137).* `SecurityConfig` now emits `Content-Security-Policy`,
`Strict-Transport-Security`, `Referrer-Policy: strict-origin-when-cross-origin`,
`X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff`. The CSP value is produced by
`SecurityExposurePolicy` so the header cannot disagree with the environment it describes: production
gets `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` — the backend
answers JSON and needs to load nothing — while non-production gets a same-origin policy plus the
inline script and style that Swagger UI actually emits. HSTS is emitted only when the request arrived
over TLS, directly or via `X-Forwarded-Proto: https`, because the header is meaningless over plain
HTTP and the edge serves plain HTTP locally (§27). At the edge, `infrastructure/nginx/nginx.conf`
carries the same CSP and referrer policy plus `X-Permitted-Cross-Domain-Policies` and
`Cross-Origin-Opener-Policy`, all with `always` so error responses are covered; its TLS listener —
still inert without a committed certificate — now repeats the complete header set rather than only
HSTS, because Nginx does not inherit `add_header` into a server block that declares its own.

*Actuator restriction (GAP-138).* Only `/actuator/health`, `/actuator/health/**` and `/actuator/info`
remain public; container healthchecks, compose `depends_on` conditions, and the edge probes all depend
on them, and `show-details: never` is what keeps them safe to leave open. Everything else requires
`SecurityExposurePolicy.mayReadActuator` to approve, which accepts either the `MONITORING_SCOPE`
authority — granted by the new `MonitoringTokenAuthenticationFilter` when the scrape job presents its
static token — or the existing `SYSTEM_HEALTH_VIEW` permission, so an operator inspects metrics with
the credential they already hold. The token is compared with `MessageDigest.isEqual` (constant time), is
only inspected on `/actuator/**` so presenting it on `/api/**` authenticates nothing, and the mechanism
fails closed: with no token configured the surface requires a normal principal instead of falling open.
`prometheus.yml` sends `authorization: Bearer ${MONITORING_METRICS_TOKEN}`, expanded at start-up through
`--config.expand-env`, so no secret is committed. Production observability is not weakened — scraping
continues and the liveness/readiness groups stay public.

**How it was verified.** 27 new tests in five classes under
`backend/src/test/java/com/company/exam/integration/security/`, all green: `ApiDocsExposureIntegrationTest`
(3 — docs still served outside production, and genuinely populated), `ApiDocsProductionGateIntegrationTest`
(5 — prod profile active with `app.environment=staging`, every documentation path refused),
`SecurityHeadersIntegrationTest` (7 — both HSTS branches, plus a 401 to prove `always` semantics),
`ActuatorAuthorizationIntegrationTest` (8 — anonymous, under-privileged, monitoring-token and
`SYSTEM_HEALTH_VIEW` callers, plus a check that the token cannot act as a user) and
`ActuatorWithoutMonitoringTokenIntegrationTest` (4 — the fail-closed deployment). Full backend suite:
**441 tests, 0 failures** (414 before). Container-level verification was not possible — Docker is
unavailable on this host (GAP-007) — so the edge configuration is reviewed rather than exercised; that
limitation is already tracked by GAP-015.

**Release decision unchanged: NO-GO.** Closing these three gaps does not move the blocker. GAP-130
remains the outstanding trigger — the measured p95 was 4.3× over target.

## 5j. The most recent change — synthetic load-cohort credential provisioning (GAP-085)

*(2026-10-22. New backend component plus harness rewiring. No frozen contract, table, column, migration or
endpoint was added: `UserService.create` still issues a non-usable secret exactly as P1 §56/§58 requires,
and the new mechanism has no HTTP surface at all.)*

**The problem.** GAP-085 recorded that a synthetic load cohort had no way to obtain a usable credential.
`POST /api/v1/users` deliberately assigns a random secret that nobody knows, because the account is meant
to be activated through password reset (§56, §58) — and reset delivery is not built (GAP-002). A synthetic
student has no mailbox, so the 5,000-account cohort the P7 harness needs could never authenticate. The
harness worked around this by piping raw `INSERT` statements into PostgreSQL with a `{noop}`-prefixed
plaintext password. That bypassed hashing, wrote to tables the platform owns, produced no audit trail, and
was not gated on environment in any way.

**What was built.** A version-controlled provisioning mechanism under
`backend/src/main/java/com/company/exam/identity/loadcohort/`:

- `LoadCohortProvisioningService` creates accounts through the platform's own entities and repositories,
  assigns `STUDENT`, hashes every credential with the production `PasswordEncoder`, and records a
  `LOAD_COHORT_CREATE` (or `LOAD_COHORT_REISSUE`) audit entry per account through `SecurityEventLogger`, so
  the trail is transactional with the mutation.
- `LoadCohortCli` is a plain `main` that boots a non-web Spring context and exits. **It is not a
  controller and adds no endpoint**, so nothing new is reachable in production.
- `LoadCohortProperties` holds the gate, defaulting to disabled.

**Idempotency.** Each member is matched by normalised email. An existing account keeps its identity and has
its credential re-issued and its role re-asserted, so re-running the seed never duplicates a user. Accounts
are only eligible if their address is a synthetic `.test` address, which stops a mistyped argument from
resetting a real user's password.

**The gate.** Two independent switches must both allow provisioning: `app.load-cohort.enabled=true`, and a
non-production environment. The environment check inspects **both** `app.environment` and the active Spring
profiles — a second check added after an end-to-end run found the first insufficient. `docker-compose.yml`
supplies an `APP_ENVIRONMENT` variable that outranks the value in `application-prod.yml`, so a process
started with `--spring.profiles.active=prod` still reported `app.environment=staging` and provisioning
succeeded. That hole is now closed and covered by a regression test.

**How it was verified.** 17 new tests pass (414 backend total, 0 failures). Beyond the unit/integration
tests, the mechanism was exercised inside the running backend container against PostgreSQL: a probe account
was created (`created=1`, `LOAD_COHORT_CREATE` emitted), its generated credential authenticated through the
real `POST /api/v1/auth/login` and returned a 261-character token, a second run reported
`created=0, updated=1` with the same `userId`, and the user table still held exactly one row for that
address. The prod-profile run was refused with
`Synthetic load-cohort provisioning is not permitted with the 'prod' profile active`. Every stored hash was
`{bcrypt}` — no `{noop}` remains. Probe rows were removed afterwards.

**Harness changes.** `load/seed.mjs` now calls the CLI and is the only invoker. The SQL builders,
`--psql-*` settings and `provision.sql` output are gone. Because credentials are now generated per account,
the manifest carries `studentCredentials` (email → credential) instead of one shared password, and
`load/build-credentials.mjs` looks the password up per email and fails loudly on a gap.

**Release decision unchanged: NO-GO.** GAP-130 remains measured and missed (§4). Closing GAP-085 makes the
load evidence *reproducible*; it does not improve latency.

## 5k. The most recent change — the P7 load harness and the first executed scale ramp (GAP-084, GAP-130, GAP-156, GAP-157)

*(2026-10-22. New `load/` harness, runbook and evidence. `backend/` application code is untouched: no
frozen contract, table, column, migration or endpoint was added. The only non-documentation source
changes are two optional environment pass-throughs in `docker-compose.yml` and the harness itself.)*

**The problem.** GAP-130 said the P12 §9 performance gate had never been measured, so nothing was known
about behaviour under production load — and GAP-084 said it *could not* be measured, because a single
development host supposedly had no capacity to generate 5,000 concurrent users.

**What was built.** A reproducible k6 harness under `load/`: a seed script that creates 5,000 students,
100 published questions and 15,003 prepared attempts entirely through the frozen HTTP contract; a
readiness poller; a credential builder; a ramp runner that enforces the stop-and-diagnose rule; and six
workload profiles, including profile F (mixed realistic exam). Every student authenticates as itself. The
start burst asserts the snapshot invariant externally by counting `attempt_question` and
`attempt_question_option` before and after each step, so GAP-059's core claim is now tested at scale by
observation rather than by inference.

**What happened.** GAP-084's premise is **false**. A generator-only probe sustained 5,000 VUs for 30 s
with zero interrupted iterations, and the full §33 ramp — 100 → 500 → 1,000 → 2,000 → 3,000 → 4,000 → 5,000 — ran to completion
with 5,000 real student exam flows. GAP-084 is closed.

The gate itself **fails**, and this is a genuine platform finding rather than a harness artefact:

| Users | p95 (ms) | Submission success | Exam failure | Δ snapshot rows |
|---:|---:|---:|---:|---:|
| 100 | 155.1 | 100% | 0.00% | 0 |
| 500 | 352.6 | 100% | 0.00% | 0 |
| 1,000 | 475.5 | 100% | 0.00% | 0 |
| 2,000 | 552.6 | 100% | 0.00% | 0 |
| 3,000 | 395.0 | 100% | 0.00% | 0 |
| 4,000 | 516.4 | 100% | 0.00% | 0 |
| 5,000 | 430.3 | 100% | 0.00% | 0 |

**Integrity passed at every step; latency failed at every step, including 100 users** — so the verdict
cannot be changed by adding infrastructure headroom. The dominant cost is `attempt.questions`: it returns
the whole 100-question snapshot, its SQL runs in **0.08 ms**, yet a single-session read takes 90–104 ms
because `AttemptQueryService.toView` calls `contentCatalog.runtimeQuestion(...)` **once per question**,
and each of those performs four lookups plus a LaTeX re-render of every option. One read therefore issues
roughly **400 queries and 400 renders** for immutable content (filed as GAP-156). `attempt.start` is the
second hotspot, rising to 3,107 ms p95 at 5,000 users (GAP-059 latency half). An operator finding is also
recorded: a `FAILED` snapshot from an interrupted preparation has no retry path, because `retryFailed` is
unreachable through the API (GAP-157).

**How it was verified.** Four bugs in the harness itself were found and fixed before its numbers were
trusted, because each would have produced a wrong result: a single ETag reused across questions (43.8%
spurious `412`s), `constant-vus` restarting a fresh exam each loop (6,591 logins for 500 users), cohort
selection derived from a consumed count that is not a contiguous prefix (55–100% spurious exam failures),
and an over-broad readiness gate. The evidence file records provenance (commit `a6672ba`, V24, image, DB
settings), the workload, the dataset, and every limitation — including that the host was a single
co-resident development machine and is **not** production-like under P7 §12/§13 or P12 §56.

**Release decision unchanged: NO-GO.** GAP-130 is now *measured and missed* rather than unmeasured. See
§4 and `docs/testing/p7/load-2026-10-22.md`.

## 5l. The most recent change — the assessment authoring, runtime and content-authoring UIs (GAP-049, GAP-058, GAP-153)

*(2026-10-22. Frontend and documentation only; `backend/` is untouched and no frozen contract,
table, column, migration or endpoint was added.)*

**The problem.** P3, P4 and P5 were complete on the server but the frontend was a lean slice. Staff
could list blueprints and assessments and see a validation panel, but the actual authoring — writing a
blueprint configuration, picking questions, building selection rules, scheduling, grouping into
collections — existed only as raw API calls. Content authors could not use the template, taxonomy,
asset, explanation, hint, tag or previous-exam capabilities at all. And the runtime was a single lean
runner: no palette legend, no section navigator, no mark-for-review or clear-response, no submission
confirmation, no recovery banner, no multi-tab protection and no offline queue.

**The change.**
- **Blueprint builder** (`app/blueprints/[id]/versions/[versionId]`) — sections editor (name, order,
  question count, marks, duration), navigation, timer, calculator and language selection. The emitted
  `configuration` is a strict Zod projection of the frozen `BlueprintConfiguration` record, so an
  unknown key cannot be sent; the platform parser would reject it anyway.
- **Assessment builder** (`app/assessments/[id]/versions/[versionId]/builder`) — version editor,
  server-side question picker (search/filter/pagination with explicit version choice and published-only
  Add, never a full bank download), rule builder, hybrid counts, section assignment restricted to the
  selected BlueprintVersion's sections, scoring editor that states QuestionVersion defaults may differ
  from AssessmentQuestion actuals, reorder, the seven-category validation panel, authoring-only
  preview, and publish gated on a successful server validation.
- **Schedule and collection UI** (`app/schedules`, `app/collections`, `app/collections/[id]`).
- **Runtime** (`app/exam/[attemptId]`, rewritten) — palette with a five-state legend, previous/next,
  section navigator, mark for review, clear response, save indicator, non-blocking offline indicator,
  submission confirmation with answered/unanswered/review counts, expired state and recovery banner.
  The timer is server-authoritative through an **arrival anchor** (`buildTimerAnchor`,
  `remainingFromAnchor`, `reconcileAnchor`): it advances locally, survives sleep/wake, and can only
  move *earlier*, so a replayed payload cannot extend it. Auto-submit fires once from the same
  calculation.
- **Multi-tab guard** (`lib/tab-lock.ts`) — a client-only single-owner lease (storage token +
  heartbeat, 15 s staleness) that makes the non-owning tab read-only and fails open when storage is
  unavailable. It claims no authority the backend does not grant.
- **Offline queue** (`lib/response-queue.ts`) — bounded and per-question: latest value wins,
  `MAX_SAVE_ATTEMPTS=5` with capped exponential backoff, batched draining, and an entry reaches
  "saved" only on server acknowledgement. A failed entry is never presented as committed.
- **Content authoring** (`app/questions/new`, `app/questions/[id]`, `app/questions/review`) — template,
  data-driven taxonomy cascade, language, options editor, math helpers that emit only `\(...\)` and
  `\[...\]` (never `$...$`, and `$500` is left alone), asset uploader inserting canonical
  `[asset: <uuid>]`, version-bound explanation and hint disabled for published versions, tag
  multi-select, previous-exam history driven by the Academic masters, version history, and a review
  queue.

**Two integration bugs found and fixed during review.** The rule builder was emitting `templateId` and
`tagId`, but `QuestionSelectionService.criteria` reads `templateCode` and `tagIds` (a list) — a rule
scoped that way would have parsed cleanly and silently matched nothing. And `materialize` was offering
`MANUAL`/`RULE`/`HYBRID` when the contract defines exactly one mode, `REPLACE_UNMATERIALIZED`
(`13-P4 §63`); every other value is rejected with `VALIDATION_ERROR`. Both are now correct and pinned by
tests, and the materialize mode is rejected client-side before any request. Recorded as GAP-153.

**Verification.** `npx tsc --noEmit` clean; `npx eslint .` clean (0 errors, 0 warnings); **422 frontend
tests in 70 files passing** (225 new); `npx next build` succeeds with 59 routes, including all 20 new
assessment, blueprint, schedule, collection, question and exam routes. Key behavioural coverage: the
runtime is driven end-to-end (load → answer → mark → clear → navigate → confirm → submit → read-only)
against a mocked transport; a rejected save is asserted never to render as "saved"; a 412 is retried
with the fresh ETag; the frozen configuration round-trips with no extra keys; the anchor tests catch the
frozen-countdown bug that a per-render offset would cause; and a section literally named `IBPS` is used
in a test to prove no exam-name branching exists.

**Release decision unchanged: NO-GO** — see §4. The remaining gates are staging load evidence
(GAP-083/084/130) and the release rehearsals (GAP-131/132/135).

## 5m. The most recent change — attempt completion contract (GAP-073)

**The problem.** Evaluation persisted the official `Evaluation` and `Result` but never told
Delivery, so the Delivery-owned `student_attempt.attempt_status` stayed `SUBMITTED` and
`student_attempt.total_score` stayed null forever. P6 must not write Delivery state directly, so the
completion had no path at all.

**The change.**
- `delivery/api/AttemptCompletionContract` — a Delivery-owned contract exposing
  `markEvaluated(attemptId, totalScore, evaluatedOn)`. Evaluation reports the completed attempt
  through it and still touches no Delivery repository.
- `delivery/service/AttemptCompletionService` — the implementation. It performs the
  `SUBMITTED -> EVALUATED` transition as a **conditional** update (so the affected-row count is the
  ownership signal), writes `total_score`, and emits an `EVALUATE` audit event through the shared
  `SecurityEventLogger` boundary, which persists durably and transactionally. Repeating the call for
  an attempt already `EVALUATED` with the same score is a no-op, so a retried evaluation is safe and
  does not duplicate the audit event; any other state is rejected with `INVALID_STATE`.
- `StudentAttemptRepository.markEvaluated` — the conditional bulk update, matching the pattern of
  every other attempt transition.
- `EvaluationService.evaluate` calls the contract at the end of the successful evaluation, after the
  Evaluation and the Result are persisted, **inside the same transaction**.
- `AttemptCompletionContract.refreshScore(attemptId, totalScore, evaluatedOn)` — the second
  operation of the same contract, used by `ReEvaluationService`. A controlled re-evaluation
  supersedes the previous Result and creates a new one with a new `obtained_marks` (`15-P6 §46,
  §52, §56`), so the Delivery-owned `total_score` must follow the current official score. The
  refresh is a conditional update on an `EVALUATED` attempt, is idempotent for the same score, and
  emits its own audit event. Without it, `total_score` kept the superseded score while the current
  Result carried a different one.
- Two frozen-behaviour adjustments the completion forced, both required to keep P6's own rules true:
  the evaluate-once check now precedes the `SUBMITTED` precondition (`15-P6 §44, §45`: a repeated
  request is routed to the existing evaluation, and a completed attempt is no longer `SUBMITTED`),
  and re-evaluation accepts an `EVALUATED` attempt (`§46, §52`: a corrected key re-evaluates
  attempts that were already evaluated).
- **No table, no column, no endpoint was added.**

**Verification.** `AttemptCompletionServiceTest` — 7 tests: the transition and its audit event; a
repeat for the same score is a no-op with no second audit; an attempt that is not `SUBMITTED` is
rejected; an already-evaluated attempt with a different score is rejected; the score refresh
updates an evaluated attempt and audits it; repeating the refresh for the same score is a no-op; a
refresh for an attempt that is not `EVALUATED` is rejected.
`AttemptCompletionIntegrationTest` — 3 tests through the HTTP contract: a successful evaluation
advances the attempt to `EVALUATED` with `total_score` equal to the Result's `obtained_marks`;
repeating the evaluation returns the same evaluation and keeps one score; a re-evaluation of a
completed attempt still succeeds and `total_score` follows the new official score.
Full backend suite: **388 tests, 0 failures**.

**Related (already closed).** GAP-071 — the submit handoff that triggers the evaluation now carries
an explicit answer key, so the worker that completes the attempt carries a key too.

## 5n. The most recent change — multi-correct answer keys (GAP-072)

Closed GAP-072 with additive schema amendment ADR-0025: new child table
`answer_key_detail_option` (migration V26) holding the correct option set for a
multi-correct `MULTIPLE_CHOICE` question. `answer_key_detail.correct_option_id`
is retained for single-correct and free-text keys; when child rows exist they
form the authoritative set. `AnswerKeyDetailRequest`/`Response` gain a
`correctOptionIds` array; the evaluator compares the student's selected option
set against the authoritative set with all-or-nothing scoring (partial selection
scores 0). Single-correct behaviour is unchanged.

## 5o. The most recent change — the paid-content entitlement gate (GAP-100)

**The problem.** The Commerce entitlement contract existed and was implemented, but Delivery never
consulted it, so **every assessment was startable by any eligible student regardless of purchase**.
The frozen schema carries no "free assessment" configuration field, so there was no rule at all
telling the platform which assessments are paid and which are free — and without such a rule paid
content could not be gated even in principle.

**The decision (ADR-0026).** An assessment is **free if and only if no AccessPackage grants access
to it**, derived from the package→collection→assessment graph. The rejected alternative — an
explicit `is_free` column on the assessment — would have needed a schema amendment to a frozen
table and would have created a second, contradicting source of truth (an assessment sold by a
package yet flagged free). The derived rule needs no migration, cannot contradict the package
mapping, and puts the free/paid decision in the module that owns packages and grants.

**The change.**
- `EntitlementResolver.checkAssessmentAccess(userId, assessmentId, serverNow)` — the frozen contract
  method (`18-P9 §28.1`), implemented in `EntitlementResolverService`. It resolves the assessment's
  collections, the `ACTIVE` package mappings for them, and the valid-grant count over the covering
  packages at the given server time. No package ⇒ free; a package ⇒ a currently valid grant is
  required. `serverNow` is a parameter, so the decision is reproducible.
- `canAccessAssessment` now delegates to the same decision, so the boolean and the rich decision
  cannot drift. One deliberate behaviour change: a free assessment now reports accessible, where the
  old implementation returned `false` for any unmapped assessment (correct per `18-P9 §16`).
- `delivery/application/EntitlementAccessGate` — the single place where the decision becomes a
  request outcome. It calls the Commerce **contract**, never the AccessGrant repository
  (`18-P9 §99, §100`); the module boundary was verified by grepping cross-module imports.
- `delivery/exception/PaidContentAccessDeniedException` — HTTP 403 with the controlled error code
  `ACCESS_DENIED_PAID_CONTENT`.
- `StartAssessmentService.startAttempt` calls the gate **after schedule validation and before the
  attempt transition**, so a denied start mutates no attempt state: the attempt stays `NOT_STARTED`
  and no session is created.
- **No table, no column, no migration, no endpoint was added.** The gate adds three indexed reads
  and no writes, so the Start Exam latency budget is unchanged in shape; the p95 gate itself remains
  unmeasured (GAP-130).

**PRACTICE mode is not gated** (ADR-0026, Decision 3). The mode is server-owned — copied from the
schedule by the preparation worker (`14-P5 §94`) — so a client cannot move a paid EXAM assessment
into an ungated mode. The residual is operator misconfiguration: a paid assessment also scheduled
in PRACTICE mode stays reachable in that mode. This is recorded deliberately.

**Verification.** `EntitlementGateIntegrationTest` — 8 tests through the HTTP contract: a student
with a valid grant starts a paid assessment; a student without one is denied with HTTP 403
`ACCESS_DENIED_PAID_CONTENT` and the attempt is left `NOT_STARTED` with no session; a free
assessment starts with no grant; an assessment in a collection that no package maps is also free;
the gate cannot be bypassed from the client (query parameters and a body claiming entitlement are
ignored); a grant for a different package does not unlock the assessment; an expired grant does not
unlock it; a paid assessment scheduled in PRACTICE mode is not gated (ADR-0026, Decision 3).
Full backend suite: **388 tests, 0 failures**.

**Related.** ADR-0021 §8's "implemented but not yet enforced" consequence was amended in the same
change.

## 5p. The most recent change — session expiry sweep and the staff attempt read (GAP-064, GAP-065)

**The problem.** Two gaps. GAP-065: a runtime session ended only on submit or an explicit
`/sessions/{id}/end`, so a stale session stayed ACTIVE indefinitely — and attempt expiry was not
separated from session expiry at all. GAP-064: `DELIVERY_ATTEMPT_VIEW_ANY` was seeded but nothing
used it, so staff could not read another student's runtime attempt through the API.

**Part 1 — the session expiry sweep** (`14-P5 §90`, `§91`).
- `delivery/application/SessionExpiryService` — the derived-expiry policy and the sweep. The frozen
  `assessment_session` table has no expiry column, so expiry is **derived** from persisted state:
  a session is stale when (a) no runtime activity was recorded since `last_activity_on` for longer
  than the idle timeout, or (b) the attempt deadline has passed **and** the attempt is no longer
  `IN_PROGRESS`. The deadline comes from the same `DeadlineEnforcementService` authority used
  everywhere else, so the two can never disagree.
- `delivery/config/DeliverySessionProperties` — the idle timeout, default **two hours**
  (`app.delivery.session.idle-timeout`). Runtime activity is recorded on every autosave (the DEFAULT
  policy autosaves every 10 s), so only an abandoned connection goes quiet for that long.
- `delivery/worker/SessionExpiryWorker` — the scheduled sweep, on its own bounded batch and interval,
  disabled in tests like the other Delivery workers.
- `AssessmentSessionRepository.markExpired` — a conditional `ACTIVE -> EXPIRED` update. The
  affected-row count is the ownership signal: a retry affects no rows, `ended_on` is not rewritten,
  and the audit event is emitted exactly once.
- **The sweep never mutates an attempt.** It expires the session row only, so a submitted attempt
  is left byte-identical. Attempt expiry and session expiry stay distinct (§91): the attempt's own
  expiry path remains the single owner of `IN_PROGRESS -> SUBMITTED`.
- **No table, column or migration was added.** The idle timeout is a configuration value.

**Part 2 — the staff attempt read** (`14-P5 §83`).
- `GET /attempts/{attemptId}` now resolves `DELIVERY_ATTEMPT_VIEW_ANY` from the authenticated
  principal through `AuthorizationService` and passes it to `AttemptQueryService.getAttempt`. The
  permission is never taken from the request, so the check cannot be skipped by the client.
- **Self-scoping remains the default**; the staff scope is a widening of the same read, not a
  replacement. A student reading another student's attempt is still refused with 404.
- Both paths render the same `AttemptResponse`, so a staff read cannot expose anything an owner read
  cannot — no answer keys, no correct-answer flags, no audit columns.
- A staff read is an operational inspection rather than the runtime, so it is not refused with 409
  on an attempt past its deadline; staff must be able to see the state of a finished attempt.
- **No new endpoint was added.** The question and response sub-reads under the same path remain
  self-scoped; widening them is a separate contract decision and is recorded as a residual.

**Verification.** `SessionExpiryIntegrationTest` — 7 tests: a session past its idle timeout becomes
EXPIRED while its attempt is untouched; a session left ACTIVE behind a finished attempt is EXPIRED; a
session inside its window is unaffected; an expired attempt still IN_PROGRESS keeps its session until
the idle timeout (attempt expiry and session expiry stay distinct); a submitted attempt and its
session are unchanged by a sweep; a repeated sweep is a no-op that rewrites neither `ended_on` nor the
audit event; expiring a stale session does not strand a live attempt (the student can still save and a
Start Exam re-establishes a session).
`StaffAttemptReadIntegrationTest` — 4 tests: a student reading another student's attempt is refused;
a staff holder with the permission reads it; the owner still reads their own; and the staff response
carries no correct-answer data, no audit columns and no owner identifier.
Full backend suite: **388 tests, 0 failures**.

## 5q. The most recent change — the assessment `examId` filter (GAP-041)

**The problem.** `13-P4 §64` and `§95` both name the assessment list filters: status, **examId**,
blueprintId, search. The frozen `assessment` table has no exam column (`04-Schema §28.4`), so the
filter had no path and was implemented as a controlled 400 `UNSUPPORTED_FILTER` — a documented filter
answering with an error.

**What the schema actually contains.** Every `exam_id` column in the platform is on `subject`
(Academic), `question` and `previous_exam_history` (Content). None is on an Assessment table. So two
of the candidate routes are **not paths at all**: `blueprint` (neither the table nor its
configuration shape carries an exam) and `assessment_collection` (it is the commerce bundle, with no
academic taxonomy).

**The decision (ADR-0027).** The link is the `examId` in a version's **selection-rule configuration**
— the scope the platform itself acts on when it materializes the question pool (`13-P4 §45`, `§46`),
and which `04-Schema §28.8` calls the rule's provenance. The rejected alternatives: removing the
filter (contradicts `§64`/`§95`, and answers a filtered query with an unfiltered list); adding an
`exam_id` column (a schema amendment creating a lossy second source of truth — a version may draw
from several exams or none).

**The change.**
- `AssessmentRepository.findAssessmentIdsBySelectionRuleExamId` — the exam-scoped assessment ids,
  resolved through an indexed join on `assessment_version` → `assessment_selection_rule` with the
  JSONB extraction applied only to those rules.
- `AssessmentService.list` / `specification` — the id set restricts the existing paginated query, so
  the filter composes with status, blueprintId and search. An exam no assessment is authored against
  yields an empty page, never an error and never an unfiltered list.
- **No table, column, migration or endpoint was added**, and the frontend does not use the filter.

**Verification.** `AssessmentExamFilterIntegrationTest` — 3 tests: the filter returns the assessment
authored against that exam and excludes one authored against a different exam and one with no
versions; an exam with no assessment and an unknown exam id both return an empty page; and the filter
composes with the status filter while the other filters stay unaffected.
Full backend suite: **388 tests, 0 failures**.

## 5r. The most recent change — the analytics dashboard frontend (GAP-092)

**The problem.** P8 delivered the analytics read models and the nine frozen read endpoints, but the
frontend was a stub: a student or an authorized staff member could only call the API by hand.
GAP-092 recorded that UI as deferred, so P8 was marked partial even though its backend was complete.

**The change (frontend only).**
- Routes under `frontend/app/analytics/`: `student`, `assessments/[id]`, `questions/[id]`,
  `topics/[id]` and `organization`, plus an index that links the views the caller may use.
- Six reusable components in `frontend/components/analytics/`: `MetricCard`, `TrendChart`,
  `SectionAnalysis`, `TopicAnalysis`, `QuestionAnalysis` and `ProgressView`, with shared
  loading, empty, error and freshness primitives.
- TanStack Query hooks in `frontend/features/analytics/hooks/use-analytics.ts` call only the frozen
  endpoints (`GET /analytics/student`, `/student/assessments`, `/student/topics`,
  `/assessments/{id}`, `/assessments/{id}/questions`, `/questions/{id}`, `/topics/{id}`,
  `/organization`); every response is validated with Zod at the boundary.
- States: loading (`role="status"`), empty (`role="note"`, with the §146 wording), error
  (`role="alert"` with a keyboard-operable retry). A failed refresh keeps the last known values and
  shows a non-blocking stale notice instead of zeroing them (§147).
- Freshness: every dashboard and every chart renders `lastCalculatedOn` with the API status
  (CURRENT / STALE / REFRESHING / EMPTY) (§51, §69).
- Accessibility: charts are plain inline SVG because **no charting library is on the approved
  stack**; each data point is focusable with an accessible label, and every chart has a tabular
  fallback in a native keyboard-operable `<details>` element (§145).
- Authorization: `ANALYTICS_VIEW` hides staff links and gates staff pages; the student page is
  self-scoped by the backend because its path carries no user id. Hiding a link is never treated as
  authorization (§148).
- **No backend file, endpoint, migration, API contract or dependency changed.**

**Verification.** 29 new frontend tests: schema tolerance for null populations and decimal strings
(4), `MetricCard` missing-versus-zero and freshness (4), `TrendChart` focusable points, tabular
fallback, disclosure activation and empty state (5), and loading/empty/error for all five dashboards
plus a data-rendering student dashboard (16). Full frontend suite: **22 files / 85 tests, 0
failures**; `npm run lint` and `npm run type-check` clean; `npm run build` succeeds and emits all
five analytics routes. The release decision is unchanged (NO-GO): GAP-092 was a product-surface gap,
not a release gate.

## 5s. The most recent change — the results and scorecard frontend (GAP-128)

**The problem.** P6 delivered evaluation, results, publication and scorecards, but the frontend had
no screen for any of it: students could not see a published result or a scorecard, and only the API
could. GAP-128 recorded that UI as deferred.

**The change (frontend only).**
- Routes under `frontend/app/results/`: a student result list (`/results`), a single result
  (`/results/[id]`) showing obtained marks, percentage, rank, percentile and status, and a
  scorecard view (`/results/[id]/scorecard`); plus the attempt-bound result at
  `/attempts/[id]/result`.
- Reusable components in `frontend/components/results/`: `ResultCard`, `ScorecardView`,
  `RankBadge` and `ResultStatusBadge`, with explicit loading, empty, error and visibility states.
- TanStack Query hooks in `frontend/features/evaluation/hooks/use-results.ts` call only the frozen
  reads: `GET /results/{id}`, `GET /attempts/{attemptId}/result`,
  `GET /results/{resultId}/scorecard` and the self-scoped `GET /attempts`. Every payload is
  validated with Zod at the boundary.
- **Publication is enforced twice.** The backend answers 404 for an unpublished result or another
  student's result; the UI independently renders only a PUBLISHED, non-superseded result and shows a
  "not published" notice otherwise. A 404 is presented as "not available", so neither existence nor
  ownership is revealed.
- **The student list is composed, not invented.** `GET /results` requires `RESULT_VIEW`, which only
  ADMIN and STAFF hold, and the platform freezes no student-scoped result-list endpoint. The list
  therefore composes the self-scoped attempt read with the per-attempt result read, ignores a 404,
  accepts only PUBLISHED non-superseded results, and is bounded to the 25 most recent terminal
  attempts.
- **No backend file, endpoint, migration, API contract or dependency changed.**

**Verification.** 27 new frontend tests: schema tolerance and the missing-status default (5);
`ResultCard` metrics, unranked-versus-zero and status labels (4); `ScorecardView` metadata and the
unpublished-result guard (3); `ResultDetail` publication/supersession guards (3); and student-list
loading/empty/error, the exclusion of a 404 and a superseded result, the single-result and
attempt-result 404 paths, and the scorecard render/empty/unpublished paths (12). Full frontend
suite: **27 files / 112 tests, 0 failures**; `npm run lint` and `npm run type-check` clean;
`npm run build` succeeds and emits `/results`, `/results/[id]`, `/results/[id]/scorecard` and
`/attempts/[id]/result`. The release decision is unchanged (NO-GO): GAP-128 was a product-surface
gap, not a release gate.

## 5t. The most recent change — the commerce frontend (GAP-104)

**The problem.** P9 delivered the catalog, purchase, payment, refund and entitlement APIs, but the
product had no commerce screen: students could not browse packages or check out, and finance could
not review or refund a purchase. GAP-104 recorded that UI as deferred.

**The change (frontend only).**
- Student routes: `app/student/store` (catalog), `packages/[id]` (detail, collections and plans),
  `checkout` (plan + coupon + summary), `purchases` (history), `purchases/[id]` (payment status and
  retry) and `access` (entitlements).
- Finance routes: `app/finance/purchases`, `payments`, `refunds`, `coupons` and `plans`; staff
  route `app/staff/access-grants`; admin route `app/admin/commerce` (packages, collection mapping
  and reconciliation).
- The eight reusable components (`PackageCard`, `PackageDetail`, `PlanSelector`, `CouponInput`,
  `PurchaseSummary`, `PaymentStatus`, `AccessGrantCard`, `PurchaseHistory`) sit on TanStack Query
  hooks over the frozen endpoints; Zod validates every response and every form.
- **No client pricing.** The checkout submits only `subscriptionPlanId` and `couponCode`. Every
  displayed amount is the plan price, the server's coupon-validation `finalAmount`, or the
  server-created purchase amount; the browser never subtracts a discount or totals a cart.
- **No client status mutation.** Payment state is read from the purchase/payment endpoints, may be
  initiated through `POST /purchases/{id}/payment`, and is otherwise followed by polling or an
  explicit refresh. The pending-to-success transition is observed from the server, never set
  locally.
- **No client-side unlock.** On a server-confirmed SUCCESS the screen invalidates the access and
  catalog reads so entitlement appears without a re-login; content remains gated by the backend.
- Permission-gated links and controls (refund, manual grant, administration) are UI affordances
  only; every endpoint re-authorizes.
- **No backend file, endpoint, migration, API contract or dependency changed.**
- **Recorded limitation:** no staff purchase-list endpoint is frozen, so the finance purchase and
  payment workspace looks up one purchase by id (`GET /purchases/{id}`, `PURCHASE_VIEW`), and provider
  payment detail remains owner-only.

**Verification.** 19 new frontend tests: schema parsing and server-coupon preservation (5); checkout
validation, server-priced summary and a purchase body that carries no amount (3); the payment
PENDING -> SUCCESS transition observed through a refetch with no POST (2); student access cards,
empty state and a staff lookup scoped by user id (3); finance view + confirmed refund + a pending
purchase that cannot be refunded (3); purchase history (1); store catalog (2). Full frontend suite:
**34 files / 131 tests, 0 failures**; `npm run lint` and `npm run type-check` clean; `npm run build`
succeeds and emits every commerce route. The release decision is unchanged (NO-GO): GAP-104 was a
product-surface gap, not a release gate.

## 5u. The most recent change — the communication frontend (GAP-109)

**The problem.** P10 delivered notifications, email templates and announcements, but the product had
no communication screen: students could not see notifications or announcements, and administrators
had no template or announcement UI. GAP-109 recorded that UI as deferred.

**The change (frontend only).**
- Student routes: `app/student/notifications` (inbox, bell, filters, optimistic read actions) and
  `app/student/announcements` (published announcements within their visibility window).
- Admin routes: `app/admin/communication/templates` (list/create/search), `templates/[id]` (edit,
  code immutable) and `templates/[id]/preview` (synthetic context); plus
  `announcements` (list/create) and `announcements/[id]` (edit, publish, archive).
- The eight required components — `NotificationBell`, `UnreadBadge`, `NotificationPopover`,
  `NotificationList`, `NotificationItem`, `NotificationFilters`, `MarkReadAction`,
  `MarkAllReadAction` — sit on TanStack Query hooks over the frozen endpoints; Zod validates every
  response and form.
- **Optimistic read UX with rollback.** Mark-read updates every cached notification list and the
  unread count immediately, snapshots the prior cache, and restores it exactly if the mutation
  fails (`§107`). Mark-all-read uses the same discipline.
- **No invented unread endpoint.** The unread count is derived from the paginated unread read
  (`§106`), not a new API.
- **Safe preview.** Placeholders use the frozen `{{variableName}}` syntax with the documented
  synthetic context (`§71`); the body is rendered as text, never injected as HTML, so admin-authored
  markup cannot execute in the browser (`§72`).
- **No SMS or push channel** was added; the platform remains in-app notification plus the existing
  email boundary.
- **No backend file, endpoint, migration, API contract or dependency changed.**

**Verification.** 16 new frontend tests: schema parsing and preview substitution (4); optimistic
mark-read removal, failure rollback and mark-all (3); bell, popover, filter and read actions (3);
announcement edit + publish and student visibility without admin controls (3); template list, edit
with immutable code and safe preview (3). Full frontend suite: **39 files / 147 tests, 0 failures**;
`npm run lint` and `npm run type-check` clean; `npm run build` succeeds and emits every communication
route. The release decision is unchanged (NO-GO): GAP-109 was a product-surface gap, not a release
gate.

## 5v. The most recent change — the administration frontend (GAP-123)

**The problem.** P11 delivered the administrative APIs — dashboard, users, roles, permissions,
settings, business rules, lookups, audit and system health — but there was no admin UI at all.
GAP-123 recorded the frontend as deferred.

**The change (frontend only).**
- Ten admin routes: `/admin/dashboard`, `/admin/users`, `/admin/students`, `/admin/roles`,
  `/admin/permissions`, `/admin/settings`, `/admin/business-rules`, `/admin/lookups`,
  `/admin/audit` and `/admin/system-health`, plus an `/admin` navigation entry.
- The required components: `AdminPageHeader`, `PermissionGuard`, `DataTable`, `FilterBar`,
  `StatusBadge`, `ConfirmDialog`, `DangerActionDialog`, `AuditDrawer` and `HealthCard`, with
  server pagination, loading/empty/error states and keyboard-accessible dialogs.
- TanStack Query hooks over the frozen reads; Zod validates every response and filter, and React
  Hook Form drives the configuration forms.
- **Permission-driven navigation** from the current-user permission context (`GET /me` via the auth
  provider): each link renders only when one of its permissions is present. Hidden links and
  disabled actions are affordances; the backend re-checks every permission (§67).
- **Dangerous actions require confirmation** (§68): user archive, role-permission replacement,
  settings and rule changes, and lookup delete all pass through `DangerActionDialog`.
- **Audit explorer** filters by actor, entity, entity id, action and date range, paginates on the
  server, and opens a read-only `AuditDrawer`. It exposes no mutation control (§73).
- **Allow-listed server sorting** (§65) was added after the first pass: `components/admin/sorting.ts`
  mirrors each frozen controller's `QuerySupport` allow-list (users: createdOn/email/firstName/
  lastName/status; roles and permissions: code/name/status/createdOn; audit: createdOn/action/
  entityName), `DataTable` renders a sortable header only for an allow-listed field and exposes
  `aria-sort`, and the screens send the Spring `field,direction` parameter. A non-allow-listed field
  is neither offered nor sent, because the backend rejects the whole request.
- **No backend file, endpoint, migration, API contract or dependency changed.** Composite screens
  use existing APIs: students resolve the STUDENT role then filter the user list, because no
  composite student endpoint is frozen (§75).
- **Test runner stabilisation.** The growing jsdom/userEvent suite was flaky at the default 5s
  timeout when files ran in parallel on a busy host, so `vitest.config.ts` now runs files
  sequentially. Every test still runs; only parallel file execution was disabled.

**Verification.** 20 new frontend tests: permission-gated archive visibility with and without
`USER_ARCHIVE` (2), permission-driven navigation filtered and full (2), audit filter translation and
read-only drawer plus malformed-id rejection (3), audit server sorting — allow-listed sort carries
the active filters, direction cycling, and no control on a non-allow-listed column (3), the sorting
allow-list helper (7), and accessible dialog/table/health components (3).
Full frontend suite: **43 files / 167 tests, 0 failures**; `npm run lint` and
`npm run type-check` clean; `npm run build` succeeds and emits every administration route. The
release decision is unchanged (NO-GO): GAP-123 was a product-surface gap, not a release gate.

## 5w. The most recent change — the remaining report providers and the reporting UI (GAP-141, GAP-148)

**The problem.** P13 shipped the reporting framework — catalog, validation, sync/async execution,
CSV and PDF renderers, protected download and history — but only 5 of the 23 catalog report types
had a projection provider. The Student, Assessment, Content and Commerce families were registered and
`enabled=false`, so students, staff and finance could not get a report through P13 at all (GAP-141).
No reporting UI existed either (GAP-148).

**The change — backend (read contracts and providers).**
- Five new **owning-module read contracts**, so Reporting never reaches into another module's
  repository (`22-P13 §10`): `ReportingAnalyticsCatalog` (student, topic, question, assessment and
  organization aggregates), `ReportingDeliveryCatalog` (report-safe attempt rows), and
  `ReportingAssessmentCatalog`, `ReportingContentCatalog`, `ReportingCommerceCatalog`. Each has a
  `@Transactional(readOnly = true)` service. Every collection read takes an explicit id set and every
  list read is bounded, so no report scans an unbounded table (`§7.6`).
- Five provider classes following the existing `OperationsReportProviders` pattern:
  `StudentReportProviders`, `AssessmentReportProviders`, `ContentReportProviders`,
  `CommerceReportProviders` and `OperationalReportProviders`, plus a shared `Projections` helper that
  caps rows at `MAX_ROWS = 2000` and renders locale-independent values.
- **All 23 catalog types are now `enabled=true`**, and the enums' javadoc was corrected: it had
  claimed `enabled=true` meant "reserved, not implemented", which is the inverse of the truth.
- **Semantics preserved rather than invented.** `ASSESSMENT_PERFORMANCE` omits an assessment with no
  calculated aggregate instead of reporting a zero (`§45`); `CONTENT_TAG_COVERAGE` emits an explicit
  `UNTAGGED` row (`§52`); `COMMERCE_REVENUE_SUMMARY` groups by authoritative `payment_status` so a
  pending purchase is never counted as collected (`§60`); `STUDENT_TOPIC_PERFORMANCE` derives accuracy
  from the student's own attempts, snapshots, evaluation outcomes and taxonomy, and reports
  `UNMAPPED` rather than guessing a topic (`§41`); `OPERATIONS_COMMUNICATION_STATUS` aggregates the
  durable delivery backlog from `background_job` because Communication reads per user only (`§63`).
- **Nothing sensitive is projected:** no answer key or correctness to a student (`§39`), masked
  email and no credential in the profile (`§82`), no payment secret, and no job error *message* (only
  the stable code), because a message can echo internals.
- **No framework file changed:** `ReportService`, `ReportController`, `ReportRequestValidator`,
  `ReportCriteria`, `ReportProjection`, the renderers and the storage layer are untouched. No new
  table, migration or endpoint. All six `REPORT_*` permissions were already seeded, so no seed
  migration was needed either.

**The change — frontend (the reporting UI).**
- Three screens: `/student/reports`, `/staff/reports` and `/admin/reports`, sharing one
  `ReportingWorkspace`. Reusable components in `frontend/components/reporting/`: `ReportCatalog`
  (grouped by category, disabled entries render no control at all), `ReportFilterForm` (one input per
  frozen `filterSchema` key; `*Id` as UUID, `from`/`to` converted to ISO instants, format limited to
  `supportedFormats`), `ReportJobStatus` and `ReportHistory`.
- Data access in `frontend/features/reporting/` over the frozen endpoints only. **Polling is bounded
  twice**: `pollInterval` stops on any terminal status *and* at `MAX_JOB_POLLS` (60 polls at 2.5 s ≈
  2.5 minutes), and `refetchIntervalInBackground` is false so a backgrounded tab stops. The oracle is
  a pure exported function so both bounds are asserted directly.
- Zod validates every response; an unknown or missing job status resolves to `UNKNOWN`, never to
  `COMPLETED`. A malformed UUID is rejected before any request is sent.

**A repository defect found and fixed.** `.gitignore` line 58 `reports/` — intended for local runtime
artifacts at the repository root — is unanchored, so it also matched the Next.js route segments
`app/{student,staff,admin}/reports/`. All three new route pages were silently invisible to git
(`git check-ignore -v` confirmed it) and would have been left out of a commit. The runtime-data
entries are now root-anchored (`/reports/` and siblings), which preserves the original intent and
makes the routes trackable.

**Verification.** Backend: 9 new provider tests and a rewritten integration test that requests
**every** catalog type and requires a real 200 plus a projection or an accepted job; full suite
**397 tests, 0 failures**, `mvn compile` clean. Frontend: 30 new tests (17 schema/polling, 5 catalog,
8 workspace); full suite **46 files / 197 tests, 0 failures**; `tsc --noEmit` and `eslint .` clean;
`next build` succeeds and emits all three reporting routes. The release decision is unchanged
(NO-GO): these were product-surface gaps, not release gates.

**Still open, and deliberately not resolved here.** `22-P13 §55` names seven commerce reports, but
the frozen `ReportType` catalog registers only four; Subscription Summary, Coupon Activity and Refund
Activity have no identifier. Adding one would change the frozen catalog contract and the
`GET /reports/catalog` payload, so it is recorded as **GAP-141a** instead of being invented. And the
student screen is built and the backend already self-scopes student requests, but students hold no
report permission, so that screen is reachable only once **GAP-142** is decided.


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
cd backend && JAVA_HOME=C:\java\jdk-25.0.4.1 ./mvnw test      # 388 tests, 0 failures

# Frontend
cd frontend && npm ci && npm run lint && npm run type-check && npm test && npm run build

# Container stack (Docker Desktop must be running)
docker compose --profile core --profile app --profile edge up -d --build
bash scripts/verify-stack.sh                                       # 15/15 on 2026-10-19; 5 header assertions added 2026-10-23, not yet run
bash scripts/verify-observability.sh                               # 20/20 observability assertions

# Backup / restore / rollback rehearsal
bash scripts/backup/backup-postgres.sh
bash scripts/backup/restore-postgres.sh <backup-id>
bash scripts/rollback/rollback-rehearsal.sh
```
