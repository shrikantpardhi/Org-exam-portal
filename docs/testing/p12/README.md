# P12 — Production Hardening & Production Readiness: Release Evidence Package

**Decision: NO-GO for production release.** See §"Go / No-Go decision".

Phase specification: `specs/21-P12-Hardening-V1.5.md`.
Prepared: 2026-10-15. Operator: engineering (automated execution on the development host).

This package records, for every P12 gate, either the measured evidence or an explicit BLOCKED entry
with the reason. No gate is marked passed on verbal confirmation (§10), and no number in this
document is estimated or extrapolated. Where a gate needs infrastructure the development host does
not have, it is recorded as blocked and carried in `docs/known-gaps.md` — never inferred.

---

## 1. Release candidate identity (§11)

| Identifier | Value |
|---|---|
| Application version | `1.5` (`app.info.version`, from the Maven project version) |
| Frontend version | `1.5` (frontend `package.json`) |
| Database migration version | **`V23`** (highest applied; 23 migrations `V1`–`V23`) |
| Configuration revision | `application.yml` + `application-test.yml` as committed in the working tree |
| Container image digest | **BLOCKED** — no Docker on the host (GAP-007), so no image was built |
| Source revision | **Not committed** — all work is in the working tree on branch `dev`; there is no release commit/tag to identify |

The absence of a commit/tag and an image digest means the candidate is **not uniquely
reproducible** from artefacts. This alone is a release-process blocker until the work is committed
and an image built.

## 2. Code freeze (§12)

| Freeze item | State |
|---|---|
| Application code | Frozen — no source change during P12 |
| Migration set | Frozen at `V23` — no migration added during P12 |
| Test data model | Frozen |
| Load profile | **BLOCKED** — no load profile exists (GAP-130) |
| Core runtime policies | Frozen |

**No change was made to application or migration code during P12.** The only file changed in this
phase's working tree is `frontend/package-lock.json`, which was repaired in the *preceding*
re-verification (GAP-126): the committed lock was out of sync and `npm ci` failed, which also broke
the frontend image build. Per §13 that is a material change after which affected evidence must be
re-run — the frontend lint/test/build evidence below was captured **after** that repair.

## 3. Gate results

### EXECUTED — evidence retained

| Task | Gate | Result | Evidence |
|---|---|---|---|
| P12-01 | Hardening freeze | **Done, with caveat** | Freeze declared; candidate identity incomplete (no commit, no image digest) |
| P12-02 | Security regression | **PASS on executed scope** | Full suite green (security classes included): `IdentityAuthenticationIntegrationTest`, `IdentityAuthorizationIntegrationTest`, `IdentityConcurrencyIntegrationTest`, `IdentitySessionApiIntegrationTest`, `IdentityPasswordResetIntegrationTest`, `EvaluationSecurityIntegrationTest`, `P11AdministrationIntegrationTest` (endpoint gating + IDOR refusal), `CommunicationIntegrationTest` (IDOR). 306 tests / 0 failures. Config-level review in §4 |
| P12-03 | API regression | **PASS on executed scope** | Inventory measured: **55 controllers, 232 endpoint mappings, 52 class-level `@RequestMapping` bases**. All API-level integration tests pass; response envelope, request-id and error shape asserted by `SystemApiIntegrationTest`, `GlobalExceptionHandlerTest`, `RequestIdFilterTest` |
| P12-04 | Database performance | **PARTIAL** | Index review measured (§5). No query-plan or throughput measurement is possible without a production-scale database (BLOCKED for the perf half) |
| P12-06 | Concurrency regression | **PASS on executed scope** | `StartBurstConcurrencyTest`, `IdentityConcurrencyIntegrationTest`, `RecoveryAndSubmissionIntegrationTest`, `ResponsePreconditionIntegrationTest`, `CommerceIntegrationTest` (duplicate callback), `CommunicationIntegrationTest` (job claim) all green |
| P12-07 | Failure injection | **PARTIAL — in-test injection only** | Redis-unavailable fallback (`RedisCacheServiceTest`, cache-backed reads fall back to PostgreSQL), email provider outage (`FakeEmailGateway.failTransiently`, `CommunicationIntegrationTest`), payment callback duplicate/failed/refunded (`CommerceIntegrationTest`), worker job re-claim and retry backoff (P10). **Not** exercised: real PostgreSQL loss, PgBouncer loss, process restart, network partition (BLOCKED — see §6) |
| P12-13 | UX regression | **PASS on executed scope** | Frontend lint clean; **18 test files / 56 tests passing**; `next build` succeeds. No manual/visual regression pass was performed (BLOCKED — no browser/staging) |
| P12-14 | Production configuration review | **PASS with findings** | §4 below |
| P12-10 | Migration rehearsal | **PARTIAL** | All 23 migrations apply cleanly from scratch on embedded PostgreSQL, asserted per phase and by `FlywayMigrationIntegrationTest`; `validate-on-migrate: true`. A rehearsal against production-like **data volume** and a real PostgreSQL server was **not** performed (BLOCKED) |

### BLOCKED — no evidence, cannot pass

| Task | Gate | Blocking reason | Gap |
|---|---|---|---|
| P12-05 | Concurrent-user scale validation (5,000 users) | No load tooling (`k6`/`jmeter`), no staging/load environment, no Docker-hosted PostgreSQL/Redis, no PgBouncer | GAP-130 (extends GAP-083/084) |
| P12-08 | Backup validation | No PostgreSQL server or backup tooling on the host | GAP-131 |
| P12-09 | Restore rehearsal | Same; restore is untestable without a backup | GAP-131 |
| P12-11 | Rollback rehearsal | No deployment infrastructure, no image, no rollback procedure to rehearse | GAP-132 |
| P12-12 | Monitoring and alert validation | Prometheus/Grafana/Loki configuration and stack absent (GAP-009); no dashboards or alerts exist to validate | GAP-134 |
| P12-15 | Dress rehearsal | Requires the staging/load environment | GAP-135 |
| P12-17 | Production smoke test | Requires a deployed production/production-like environment | GAP-135 |
| P12-18 | Post-release monitoring | Requires a released candidate and the monitoring stack | GAP-135 |
| — | Performance gates (§9): 5,000 concurrent users, 99.9% availability, p95 < 100 ms, >99% submission success, <1% exam failure | **Unmeasured.** No workload was run, so none of these can be claimed | GAP-130 |

`P12-16 Release evidence package` is this document.

## 4. Production configuration review (P12-14)

**Verified good:**

- No committed secrets. Every credential is an environment variable; `JWT_SECRET` defaults to empty
  and fails fast (§28).
- CORS is an explicit origin list (default `http://localhost:3000`) — no wildcard.
- **CSRF disabled deliberately** and correctly: authentication is a stateless `Authorization: Bearer`
  token, there is no cookie-based session, so CSRF does not apply (§25 strategy documented).
- Sessions are `STATELESS`; authorization is deny-by-default with an explicit public allow-list.
- Error hardening: `include-message`, `include-stacktrace`, `include-binding-errors` all `never`
  (§30).
- Mass-assignment hardening: `spring.jackson.deserialization.fail-on-unknown-properties: true` (§19).
- `ddl-auto: validate` + Flyway `validate-on-migrate: true` — the schema is owned by migrations and
  Hibernate cannot mutate it (§76).
- `open-in-view: false` — no lazy loading during view rendering.
- Bounded resource limits: header size 16 KB, multipart 25 MB/30 MB, Redis connect/read timeouts 2 s,
  graceful shutdown 20 s.
- Actuator exposes only `health,info,prometheus,metrics`; `health` shows components but
  `show-details: never`, so no infrastructure configuration leaks. Since GAP-138, only `health` and
  `info` are anonymous — the rest requires the monitoring scope or `SYSTEM_HEALTH_VIEW`.
- Explicit `Content-Security-Policy`, `Strict-Transport-Security` (TLS requests only),
  `Referrer-Policy`, `X-Frame-Options` and `X-Content-Type-Options` are emitted on every response
  (GAP-137), and API documentation is refused in production (GAP-136).
- Trusted-proxy header handling is opt-in (`false` by default).

**Findings from the freeze-phase audit, and their disposition:**

The audit recorded the first three items as gaps rather than fixing them, because the freeze phase
required recording before changing. All three have since been fixed; the last two remain open.

1. **GAP-136 — API documentation was publicly reachable. FIXED 2026-10-23.** `/v3/api-docs/**`,
   `/swagger-ui/**` and `/swagger-ui.html` were in the public allow-list and springdoc was enabled
   with no per-profile switch, so production disclosed every endpoint and schema to an
   unauthenticated caller. Now: `application-prod.yml` disables both springdoc surfaces, the paths
   are gated by `SecurityExposurePolicy` rather than being public, and the policy refuses production
   on **either** signal (`app.environment` or an active `prod`/`production` profile) so the
   `docker-compose` `APP_ENVIRONMENT=staging` override cannot re-open them. Swagger UI still works
   in `local` and on the `staging` stack. Evidence: `ApiDocsProductionGateIntegrationTest`,
   `ApiDocsExposureIntegrationTest`.
2. **GAP-137 — No explicit HTTP security headers. FIXED 2026-10-23.** No
   `Content-Security-Policy`, `Strict-Transport-Security`, or `Referrer-Policy` was configured;
   only Spring Security's defaults applied. Now both layers carry the policy: `SecurityConfig`
   emits CSP, HSTS, Referrer-Policy, `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff`,
   with the CSP chosen by `SecurityExposurePolicy` (maximally restrictive in production, the
   same-origin policy Swagger UI needs elsewhere) and HSTS emitted only when the request arrived
   over TLS directly or via `X-Forwarded-Proto`; `infrastructure/nginx/nginx.conf` carries the same
   policy for the edge, and its TLS listener repeats the full set rather than only HSTS. Evidence:
   `SecurityHeadersIntegrationTest`.
3. **GAP-138 — `/actuator/prometheus` was publicly reachable. FIXED 2026-10-23.** Metrics were
   unauthenticated. Now only `/actuator/health`, `/actuator/health/**` and `/actuator/info` are
   public (healthchecks and edge probes depend on them, and `show-details: never` is what keeps
   them safe). Everything else requires either the `MONITORING_SCOPE` authority — granted when the
   scrape job presents its static token — or the existing `SYSTEM_HEALTH_VIEW` permission, so a
   logged-in user without that permission is still denied. With no token configured the surface
   fails closed rather than open. Evidence: `ActuatorAuthorizationIntegrationTest`,
   `ActuatorWithoutMonitoringTokenIntegrationTest`.
4. **GAP-130 — performance gates measured and missed** (see above; unchanged by these fixes).
5. **Rate limiting remains absent** (GAP-011/105/115) — §96 rate-limit regression cannot be validated.

## 5. Database performance and index review (P12-04, §48/§49)

- **109 index statements** across the 27 migrations: the 105 present when this review was performed,
  plus 2 added later by `V26__create_answer_key_detail_option.sql`, plus the 2 composites added by
  ADR-0028/`V27`. Includes the two partial/unique keys that carry
  business invariants (`uk_evaluation_current_attempt` — one current evaluation per attempt;
  `uk_email_template_code`; `uk_business_rule_code`; `uk_lookup_category_code`; the unique
  `payment_reference` anchor for callback idempotency).
- **Every index matches the frozen schema list, plus the recorded amendment.** Spot-checked against
  `specs/04-Database-Schema-V1.5.md`: `notification` declares exactly `idx_notification_user_id` and
  `idx_notification_notification_status`; `background_job` declares exactly `idx_background_job_job_type`,
  `_initiated_by`, `_status`, `_queued_on`, `_reference`. The implementation adds no index the schema
  does not freeze except the two that ADR-0028 amends in, and omits none it does.
- Every foreign key and every hot filter/join column carries an index (verified across identity,
  academic, content, assessment, delivery, evaluation, analytics, commerce, communication,
  administration).
- **Index amendment — GAP-139, closed 2026-10-23 (ADR-0028, `V27`).** The review recorded that the two
  highest-volume worker queries were served by single-column indexes only:
  - the background worker candidate read `job_type + status ORDER BY queued_on`, covered by three
    separate single-column indexes;
  - the notification centre read `user_id + notification_status ORDER BY created_on`, covered by two.

  A composite index serves each better at scale, and the freeze rule correctly required an amendment
  rather than an in-phase change. `ADR-0028` records the amendment, `V27` applies it, and measured
  plans are in [`worker-index-plans.md`](worker-index-plans.md). Results, stated without
  overclaiming:

  - **notification list — unambiguous:** 0.994 ms / 230 buffers → 0.088 ms / 8 buffers (~11×), and
    the `Sort` node disappears. This holds for any long-lived account, because the predicate and the
    ordering both match the index.
  - **worker candidate selection — conditional:** 21.337 ms / 1,576 buffers → 0.084 ms / 4 buffers
    (two orders of magnitude; ~120–250× across runs) **when the queue is deep and shared between job
    types**. With a shallow queue PostgreSQL
    correctly prefers `idx_background_job_status` and the composite buys nothing, so this is backlog
    insurance rather than a routine speed-up. The evidence file records two seed shapes that
    produced misleading answers (a uniform interleave flatters the index; an interleaved backlog can
    flip the planner's choice) because that distinction is easy to get wrong when measuring.

  Note the original gap text described the worker *claim* as index-served. That was inaccurate: the
  claim is `UPDATE … WHERE id = :id AND status = 'QUEUED'` and uses `pk_background_job`. What the
  composite index improves is the candidate selection preceding it. The row in `known-gaps.md` is
  corrected accordingly.
- **Known in-memory paging (GAP-067):** `GET /attempts` filters/sorts/paginates in memory over the
  caller's attempts. Acceptable for one student's history, unsuitable for a large staff-scoped
  listing — a real database-paging concern for scale.

## 6. Failure-injection coverage (P12-07, §61–§70)

| Injection | Covered | How |
|---|---|---|
| Redis unavailable | Yes | Cache reads fall back to PostgreSQL; `RedisCacheServiceTest` + cache-backed services |
| Email provider transient failure | Yes | `FakeEmailGateway.failTransiently`, retry with backoff, exhaustion → FAILED |
| Email provider permanent failure | Yes | Missing template → permanent failure, no retry loop |
| Payment callback duplicate | Yes | `payment_reference` uniqueness — no duplicate grant |
| Payment failure / refund | Yes | State transitions + grant revocation |
| Concurrent attempt start (start burst) | Yes | `StartBurstConcurrencyTest` |
| Worker job re-claim after crash | Partial | `background_job` claim semantics + reschedule are tested; a real process restart is not |
| PostgreSQL down | **No** | Requires a real database host |
| PgBouncer down | **No** | Not deployed |
| Network partition / latency | **No** | Requires a proxy or chaos tooling |
| Application restart during exam / evaluation | **No** | Requires a deployed process (§67/§68) |
| Provider-side timeout at the real gateway | **No** | Only the reference `TEST` gateway exists (GAP-099) |

## 7. Data integrity gates (§80–§90)

All ten integrity gates are asserted by the phase integration suites rather than by inspection:

| Gate | Evidence |
|---|---|
| Assessment integrity (exact version) | `AssessmentApiIntegrationTest`, `EvaluationIntegrationTest` |
| Snapshot correctness (no new `AttemptQuestion` on start burst) | `StartBurstConcurrencyTest`, `PilotSmokeIntegrationTest` |
| Response correctness (ETag / stale-write) | `ResponsePreconditionIntegrationTest` |
| Submission correctness | `RecoveryAndSubmissionIntegrationTest` |
| Evaluation correctness (answer key is the only source) | `EvaluationCalculatorTest`, `EvaluationApiIntegrationTest` |
| Result correctness (rank/percentile, supersede) | `RankPercentileIntegrationTest`, `ReEvaluationIntegrationTest` |
| Commerce integrity (no duplicate grants, amount tampering blocked) | `CommerceIntegrationTest` |
| Communication integrity (fan-out idempotency, isolation) | `CommunicationIntegrationTest` |
| Analytics integrity (derived, recalculable, read-only) | `AnalyticsIntegrationTest` |
| Administration integrity (audit atomic, immutable) | `P11AdministrationIntegrationTest` |

**No critical integrity defect was found.** However §153 "Critical release scenario — Bulk and
import" **cannot pass**: bulk operations and imports are not implemented (GAP-119/120), and §147
"Free practice" depends on an approved free-assessment rule (GAP-100).

## 8. Evidence fields required by §10

| Field | Value |
|---|---|
| Software version | 1.5 (uncommitted working tree) |
| Frontend version | 1.5 |
| Database migration version | V23 |
| Infrastructure and configuration | Development host only: JDK 25 (`C:\java\jdk-25.0.4.1`), compiled `--release 21`, Node 24 / npm 11.19.0, Windows. Embedded PostgreSQL for tests. **No Docker, no Redis server, no PgBouncer, no Nginx, no Prometheus/Grafana/Loki** |
| Workload | Automated test suites only; **no load workload was run** |
| Data volume | Test-fixture scale only (tens of rows per aggregate) |
| Test duration | Backend suite ≈ 5–7 minutes; frontend suite ≈ 21 s |
| Test profile | `test` profile (embedded PostgreSQL, deterministic fake payment/email providers, workers disabled and driven explicitly) |
| Result | Backend 306/0/0/0; frontend 18 files / 56 tests; lint clean; build OK |
| Known limitations | §9 below |
| Operator and date | Engineering (automated), 2026-10-15 |

## 9. Go / No-Go decision (§188, §189)

**NO-GO.**

Per §188, a release is no-go if any of the following is unresolved. Four are unresolved, and two of
§278's explicit no-go examples are unmet:

| §188 trigger | Status here |
|---|---|
| Critical assessment-integrity failure | **Not triggered** — integrity gates pass |
| Critical authorization bypass | **Not triggered** — security regression green |
| Unexplained financial inconsistency | **Not triggered** — commerce integrity green |
| Unsafe migration | **Not triggered** — migrations apply cleanly; production-data-volume rehearsal not done |
| **Failed rollback** | **Unresolved** — rollback is *untested* (§278: "Rollback is untested" is a no-go example) (GAP-132) |
| **Backup or restore failure for required scope** | **Unresolved** — neither backup nor restore was ever exercised (GAP-131) |
| **Materially missed core performance gate** | **Unresolved** — no performance gate was measured at all (GAP-130) |
| **Uncontrolled exam failure rate** | **Unresolved** — no workload was run, so the <1% failure rate is unknown (GAP-130) |

Conditional acceptance (§189) is **not available** for these: they are not documented non-critical
limitations with a workaround and an assigned owner — they are *unmeasured* gates. Critical integrity
and security defects cannot be waived with a known-issue label, and neither can absent evidence.

Additional scope gaps that block the §"Production readiness matrix" row "Product — critical journeys
pass": the bulk/import journey (§153) is unimplemented (GAP-119/120).

### What would be required to reach GO

1. Commit and tag the release candidate; build and record the container image digest.
2. Stand up the staging/load environment (Docker, PostgreSQL, Redis, PgBouncer, Nginx) — clears
   GAP-007/083.
3. Run the 5,000-concurrent-user workload and record the scaling curve, p95 latency, submission
   success rate and exam failure rate (GAP-130).
4. Execute and record a backup, a restore, a point-in-time recovery, and a rollback rehearsal
   (GAP-131/132).
5. Rehearse the migration on production-like data volume (GAP-133).
6. Deploy the monitoring stack, create the baseline dashboards and alerts, and validate them
   (GAP-134).
7. Run the dress rehearsal and the production smoke test on the frozen candidate (GAP-135).
8. Decide the bulk/import scope (implement, or formally descope from this release) (GAP-119/120).
9. Resolve or formally accept the configuration findings (GAP-136/137/138) and rate limiting.

Any change made to satisfy the above invalidates the frontend and backend results in this package
(§13); those suites must be re-run against the frozen candidate.

## 10. Approval

Not requested. The package is submitted to the release authority with a **NO-GO** recommendation;
approval would require the evidence in §9.
