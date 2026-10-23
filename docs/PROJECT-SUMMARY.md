# Project Summary

**Smart Competitive Examination Platform, version 1.5.** This document is the standing summary of
what exists, what is verified, and what is still open. It is updated at the end of every task, so a
reader can orient themselves without reading the full history.

Detailed material lives elsewhere — this document points at it rather than repeating it:

| Document | Purpose |
|---|---|
| [`docs/implementation-status.md`](implementation-status.md) | The per-task, per-phase status tables and the release decision |
| [`docs/known-gaps.md`](known-gaps.md) | The register of every open gap, deferred item and by-design limitation |
| [`docs/decisions/`](decisions/) | Architecture Decision Records (ADR-0001 … ADR-0027) |
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
| Flyway migrations | 27 (`V1` … `V27`), no gaps, no duplicated versions |
| Controllers / endpoint mappings | 56 / 237 |
| Backend tests | **457 passing, 0 failures** |
| Frontend tests | 70 files / **422 tests passing**; lint clean; production build succeeds (59 routes) |
| Tracked gaps | 158 (see `docs/known-gaps.md` for the register) |
| Container stack | Full stack runs; `scripts/verify-stack.sh` passed 15/15 edge assertions when last executed 2026-10-19. **Five header assertions were added 2026-10-23 (GAP-137) and have not been run** — Docker is unavailable on this host (GAP-007) |
| Observability | Prometheus target UP, 9 alert rules load, 2 datasources + 3 dashboards provisioned, Loki ingesting |
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
| P7 | Scale validation and live pilot | **Load harness delivered and the 100 → 5,000 ramp executed 2026-10-22 (GAP-084 closed); cohort credentials now come from an environment-gated, audited, idempotent provisioning mechanism (GAP-085 closed, 2026-10-22). The latency gate was measured and FAILS (GAP-130), driven by a per-question N+1 in `attempt.questions` (GAP-156).** Soak, spike, failure injection, availability and the live pilot remain outstanding |
| P8 | Analytics and insights | Complete; derived aggregates only, no time-series tables — **the analytics dashboard frontend is delivered (GAP-092, 2026-10-22)** |
| P9 | Commerce and access management | Complete; reference gateway only (GAP-099) — **the entitlement contract is now enforced by Delivery (GAP-100, 2026-10-22) and the commerce UI is delivered (GAP-104, 2026-10-22)** |
| P10 | Communication and notification | Complete; fake email provider only (GAP-108) — **the communication UI is delivered (GAP-109, 2026-10-22)** |
| P11 | Full administration | Complete; 4 of 22 report-family surfaces still partial — **the administration UI is delivered (GAP-123, 2026-10-22)** |
| P12 | Production hardening and readiness | Rehearsal executed; **NO-GO**. Release candidate **v1.5.0-rc1** is tagged (`c966d1c`) with two recorded image digests and a migration rehearsal at production volume (GAP-140, GAP-133, 2026-10-23). The three security findings from the production configuration review are now fixed — API docs are refused in production (GAP-136), the CSP/HSTS/Referrer-Policy header policy is enforced at the application layer and configured at the edge (GAP-137), and the actuator tree beyond health/info requires the monitoring scope or `SYSTEM_HEALTH_VIEW` (GAP-138), all 2026-10-23. The index amendment raised by the database review is applied under ADR-0028, with measured plans (GAP-139, 2026-10-23) |
| P13 | Broader and operational reporting | Complete; all 23 catalog report types backed, and the reporting UI is delivered (GAP-141, GAP-148, 2026-10-22) |

## 4. Release decision — NO-GO

The P12 release gates are not all met. The outstanding trigger:

- **GAP-130** — the performance gate has now been **measured, and it fails**. The 100 → 500 → 1,000 →
  2,000 → 3,000 → 4,000 → 5,000 ramp ran on 2026-10-22. Integrity passed at every step (zero snapshot inserts, 100%
  submission success, 0.00% exam failure), but **p95 latency was 155.1 / 352.6 / 475.5 / 483.1 /
  430.3 ms against the < 100 ms target** — it fails even at 100 users. The cause is identified: a
  per-question N+1 in `attempt.questions` issues ~400 queries and 400 LaTeX renders per 100-question
  read while the underlying SQL takes 0.08 ms (**GAP-156**), plus a slow `attempt.start` path. A missed
  gate is still a missed gate: this stays NO-GO until the endpoints are fixed and the ramp is re-run on
  production-like infrastructure. Evidence: `docs/testing/p7/load-2026-10-22.md`.

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

## 5. The most recent change — the snapshot read is batched (GAP-156)

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

## 5b. The most recent change — release candidate v1.5.0-rc1 (GAP-140, GAP-133)

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

## 5c. The most recent change — the worker and notification index amendment (GAP-139)

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

## 5d. The most recent change — P12 security hardening (GAP-136, GAP-137, GAP-138)

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

## 5e. The most recent change — synthetic load-cohort credential provisioning (GAP-085)

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

## 5f. The most recent change — the P7 load harness and the first executed scale ramp (GAP-084, GAP-130, GAP-156, GAP-157)

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

## 5g. The most recent change — the assessment authoring, runtime and content-authoring UIs (GAP-049, GAP-058, GAP-153)

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

## 5h. The most recent change — attempt completion contract (GAP-073)

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

## 5i. The most recent change — multi-correct answer keys (GAP-072)

Closed GAP-072 with additive schema amendment ADR-0025: new child table
`answer_key_detail_option` (migration V26) holding the correct option set for a
multi-correct `MULTIPLE_CHOICE` question. `answer_key_detail.correct_option_id`
is retained for single-correct and free-text keys; when child rows exist they
form the authoritative set. `AnswerKeyDetailRequest`/`Response` gain a
`correctOptionIds` array; the evaluator compares the student's selected option
set against the authoritative set with all-or-nothing scoring (partial selection
scores 0). Single-correct behaviour is unchanged.

## 5j. The most recent change — the paid-content entitlement gate (GAP-100)

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

## 5k. The most recent change — session expiry sweep and the staff attempt read (GAP-064, GAP-065)

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

## 5l. The most recent change — the assessment `examId` filter (GAP-041)

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

## 5m. The most recent change — the analytics dashboard frontend (GAP-092)

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

## 5n. The most recent change — the results and scorecard frontend (GAP-128)

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

## 5o. The most recent change — the commerce frontend (GAP-104)

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

## 5p. The most recent change — the communication frontend (GAP-109)

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

## 5q. The most recent change — the administration frontend (GAP-123)

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

## 5r. The most recent change — the remaining report providers and the reporting UI (GAP-141, GAP-148)

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
