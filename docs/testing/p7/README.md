# P7 — Scale Validation & Live Pilot: Evidence Package

Phase specification: `specs/16-P7-Pilot-V1.5.md`.

> **Status: PARTIAL — the measurement now exists and the gate FAILS.**
>
> On **2026-10-22** the full §33 ramp — 100 → 500 → 1,000 → 2,000 → 3,000 → 4,000 → 5,000 — was executed with a reproducible
> harness (`load/`, runbook [`load-run.md`](load-run.md), evidence [`load-2026-10-22.md`](load-2026-10-22.md)).
> Every step completed.
>
> - **Integrity: passed at every step.** Zero `AttemptQuestion`/`AttemptQuestionOption` rows were created
>   by any start burst, submission success was 100%, and exam failure 0.00% — including at 5,000
>   concurrent students.
> - **Latency: failed at every step.** p95 was 155.1 / 352.6 / 475.5 / 552.6 / 395.0 / 516.4 / 430.3 ms
>   against the < 100 ms target. It fails even at 100 users, so the verdict is not an
>   infrastructure-headroom problem. Two endpoints account for it: `attempt.questions` (a per-question
>   N+1, GAP-156) and `attempt.start`.
> - **Still not executed:** the standalone A–F profile suite beyond profile F, the soak, the spike,
>   failure injection, availability measurement, and the live pilot.
>
> The environment was a **single development host** (generator and stack co-resident), which P7 §12/§13
> and P12 §56 do not accept as production-like. The numbers therefore characterise that host and are
> **not** a production capacity claim in either direction. No staged number in this document is
> estimated or inferred; where a metric was not produced it is recorded as absent.
>
> P7 §4: "The pilot is an evidence exercise, not a feature-development phase." P7 §151/§152 place
> go/no-go, security approval and production change approval with humans. Nothing here is a readiness
> declaration, and the release decision remains **NO-GO** because GAP-130 is measured and missed.

## 1. Frozen measurement definitions (fixed before any test, §44-§48)

These were frozen **before** execution so a result cannot be redefined after the fact (§46).

| Term | Frozen definition |
|---|---|
| **Latency gate** (§45) | p95 < 100 ms for applicable synchronous APIs. Applicable: auth, attempt read, question read, response save, start, submit, recovery, `me`. Excluded: asynchronous endpoints (prepare-attempts, re-evaluate batches) and scorecard generation. p50/p95/p99 retained (§44). |
| **Submission success** (§47) | The client receives the authoritative `200` acknowledgement with `data.status = "SUBMITTED"` **and** the attempt is durably `SUBMITTED` in PostgreSQL. A toast or a closed connection is not success. |
| **Exam failure** (§48) | Any of: cannot start, cannot recover, responses not persisted, timer invalid, submission not completed, wrong question snapshot, cross-user exposure, wrong attempt association. |
| **Error budget** (§46) | Submission success > 99%; exam failure < 1%. |
| **Integrity gate** (§49) | Latency success without data-integrity success is a failed test; the §49-§54 checks are mandatory. |

## 2. Release candidate (§14)

| Field | Value |
|---|---|
| Backend version | `exam-platform-backend 1.5.0-SNAPSHOT` (working tree `dev`, P0–P6 implemented) |
| Database migration set | `V1` … `V15` |
| Frontend version | present (P0–P5 slices); not exercised by P7 smoke |
| Container image digest | **not applicable** — no container build on this host (GAP-083) |
| Configuration revision | `application-*.yml` in the working tree |

The candidate is **not** immutable-tagged: P7 §14 requires a tagged release candidate and a frozen
migration set before the primary scale test. Recorded as GAP-083.

## 3. Environment matrix (§12, §13)

| Component | Required | Available here | Consequence |
|---|---|---|---|
| Next.js deployment | yes | no (dev only) | not exercised |
| Spring Boot deployment | yes | yes (JUnit context; MockMvc, no socket) | local smoke only |
| Nginx (edge) | yes | **no** | latency/edge gates not measurable |
| PgBouncer | yes | **no** | pool/pool-exhaustion validation blocked |
| PostgreSQL | yes | embedded, disposable (ADR-0009) | not a load target |
| Redis | yes | **no** (tests point at an unreachable port) | cache/restart validation blocked |
| Background workers | yes | yes (disabled in tests; invoked explicitly) | worker-crash injection blocked |
| Prometheus / Grafana / Loki | yes | **no** | §58-§61 monitoring validation blocked |
| Object/file storage | yes | local filesystem only | storage-failure injection blocked |
| Load-generation capacity | 5,000 concurrent | 1 dev host | staged ramp blocked |

Environment separation (§13) is preserved: the smoke uses a disposable embedded database; no test
points at a shared or production database.

## 4. Dataset design (§21-§23)

Reference scenario: 1 organization, 5,000 students, 1 published assessment version, 100 questions,
representative options, 1 schedule, prepared READY attempts, responses, answer key + details.

- Construction method: `scripts/p7/p7-dataset.runbook.md` (real frozen APIs; no SQL seeding).
- Reproducibility: a recorded PRNG seed (§142) governs any randomized distribution.
- **Resolved (GAP-085, 2026-10-22):** the frozen P1 onboarding assigns a non-usable credential and
  reset delivery is not built (GAP-002), so a synthetic cohort cannot authenticate through the normal
  flow. Credentials are now issued by an environment-gated provisioning CLI
  (`LoadCohortCli`) that hashes with the production encoder, audits each account and is idempotent;
  `load/seed.mjs` invokes it and is the only caller.
- **Blocker (GAP-084) — resolved:** the reference dataset and the concurrent load were produced and
  the 100 → 5,000 ramp executed 2026-10-22. The remaining blocker is a production-like environment
  (GAP-083) and the latency gate itself (GAP-130).

## 5. Workload profiles (§25-§35)

| Profile | Workload | Status |
|---|---|---|
| PROFILE-01 Start burst | 5,000 starts in a narrow interval, snapshot READY | implemented; **staged run BLOCKED** |
| PROFILE-02 Steady exam | reads, navigation, saves, review, clear, idle, recover, submit | implemented; BLOCKED |
| PROFILE-03 Autosave burst | periodic saves, duplicate retry, network errors, stale If-Match retry | implemented; BLOCKED |
| PROFILE-04 Submission burst | 5,000 submits, duplicates, late, timer-expired | implemented; BLOCKED |
| PROFILE-05 Recovery burst | disconnect, reconnect, recover, continue | implemented; BLOCKED |
| PROFILE-06 Mixed realistic | weighted mix of the above | implemented; BLOCKED |
| PROFILE-07 Failure injection | Redis / instance / worker / PgBouncer / PostgreSQL / storage | **BLOCKED** (no such components to fail) |

Profiles are implemented in `scripts/p7/p7-harness.mjs` and are versioned with this document.

## 6. Evidence index

| # | Evidence | Artifact | Status |
|---|---|---|---|
| E1 | Harness validity self-check (zero cohort) | `scripts/p7/p7-harness.mjs` run; JSON evidence | **EXECUTED** (harness runs, emits evidence, correctly fails an empty cohort) |
| E2 | Local reduced-scale pilot smoke | `backend/.../integration/p7/PilotSmokeIntegrationTest.java` | **EXECUTED — 1 test, PASS** |
| E3 | Reconciliation SQL | `scripts/p7/reconcile.sql` | supplied (run per staged run) |
| E4 | Ramp 100/500/1,000/2,000/3,000/4,000/5,000 | — | **BLOCKED** |
| E5 | Start / autosave / submission / recovery bursts at 5,000 | — | **BLOCKED** |
| E6 | Soak (steady-state) and spike | — | **BLOCKED** |
| E7 | Failure injection (§37-§42) | — | **BLOCKED** |
| E8 | Monitoring/dashboard/log/alert validation (§58-§61) | — | **BLOCKED** |
| E9 | Backup/restore and rollback (§62-§65) | — | **BLOCKED** |
| E10 | Dry runs and live pilot (§67-§69, §76-§79) | — | **BLOCKED** |

## 7. Local reduced-scale pilot smoke — E2 (executed)

**Scope (labelled):** single application instance, MockMvc over the embedded, disposable
PostgreSQL, no Nginx, no PgBouncer, no Redis, no monitoring. 16 candidates, 5 questions, 16-way
concurrency. **This is not the staged 5,000-user ramp and does not satisfy §145.**

| Step | Result |
|---|---|
| Pre-conditions | every candidate attempt `READY`; zero attempts started before the burst; snapshot rows = prepared × 5 |
| PROFILE-01 start burst (16 concurrent) | all `200`; each returns the caller's own attempt; **`attempt_question` and `attempt_question_option` row counts unchanged (zero new rows)**; exactly one session per attempt; attempt `IN_PROGRESS` |
| PROFILE-03 autosave (16 × 3 questions) | `200` on save; a wrong `If-Match` returns **412** and does not corrupt the answer; the retry with the fresh ETag succeeds |
| PROFILE-05 recovery | same attempt, full 5-question snapshot, the 3 saved answers returned |
| PROFILE-04 submission burst (16 concurrent, duplicated) | all `200` and `SUBMITTED`; duplicate submit returns the same `submittedOn`; **exactly one `EVALUATION` handoff job per attempt**; session closed on submit |
| Evaluation + publication | one current evaluation per attempt; result published with a rank |
| Reconciliation (§94) | started = submitted = evaluated = published = 16; no duplicate response rows; no response for an unassigned question; no null `obtained_marks`; no published result without a rank |

**Run:** `cd backend && ./mvnw test -Dtest=PilotSmokeIntegrationTest` → `Tests run: 1, Failures: 0,
Errors: 0` (~17 s wall).

Interpretation: the runtime invariants P7 is most concerned about — *Start inserts no snapshot rows*,
*autosave honours the stale-write precondition*, *submission is durable and idempotent with no
duplicate evaluation*, *recovery returns the same snapshot*, and *counts reconcile* — are proven at
reduced scale over the real HTTP contract. Throughput, p95/p99 under thousands of concurrent
sessions, and failure behaviour of the absent infrastructure are **not** proven.

## 8. Integrity gates (§49-§54)

| Gate | Local smoke | Staged |
|---|---|---|
| §49 data integrity (counts) | PASS (16-user reconciliation) | BLOCKED |
| §50 snapshot integrity (count/order/exact references) | PASS (5 questions, exact `question_version`) | BLOCKED |
| §51 response integrity (one per question, ownership) | PASS | BLOCKED |
| §52 evaluation integrity (submitted only, one current, no duplicates) | PASS | BLOCKED |
| §53 result integrity (marks, percentage, rank/percentile, publication) | PASS | BLOCKED |
| §54 historical reproducibility (mutate later versions, re-evaluate) | covered by P6 `ReEvaluationIntegrationTest` | BLOCKED |

## 9. Security load validation (§55-§57)

- The under-load isolation checks (no cross-user/cross-org data, no correct answers, no privileged
  student calls) are covered functionally by P6 `EvaluationSecurityIntegrationTest` and the
  delivery ownership tests, **but not under concurrency**.
- Token-expiry and rate-limit behaviour under load: **BLOCKED** (no rate limiter is configured —
  GAP-011 — and no staged environment).

## 10. Findings

1. The frozen runtime path is **correct** at reduced scale for the start/autosave/submit/recover
   invariants; no data-integrity or duplicate-processing defect was found.
2. The largest P7 risks are **environmental, not functional**: no production-like environment and,
   historically, no load-generation capacity. Both capacity claims are now settled: generation is
   proven (GAP-084 closed) and the latency gate was measured and missed (GAP-130).
3. A synthetic cohort cannot self-authenticate through the standard onboarding flow, because the frozen
   contract deliberately issues a non-usable credential. This is now solved by a dedicated,
   environment-gated provisioning mechanism rather than an out-of-band database write (GAP-085 closed).
4. Database reconciliation cannot be performed by the HTTP harness; it needs `psql` (or a driver),
   which the dev host lacks (GAP-086). `reconcile.sql` is provided for the operator.
5. The harness itself is validated only at zero cohort; its profiles are unexercised at scale.

## 11. Exit-criteria status (§144-§146, §163-§168)

| Gate | Status |
|---|---|
| §144 soak exit (no unbounded growth/leak/latency creep) | NOT RUN |
| §145 scale exit (5,000 concurrent, start burst, >99% submissions, <1% exam failure, no corruption, recovery) | **NOT MET** (no staged environment) |
| §146 live-pilot exit (cohort completed, reconciled, archived) | NOT STARTED |
| §164 non-functional · §165 security · §166 data integrity · §167 operations · §168 pilot | **NOT MET** at scale |
| §169 architecture-decision gate | No architecture change proposed; scale data absent |

## 12. Blockers and gaps

| Gap | Description |
|---|---|
| GAP-083 | No production-like environment on the development host (no Docker/edge/PgBouncer/PostgreSQL server/Redis/monitoring); the scale suite and the immutable release candidate cannot be produced (extends GAP-007). |
| GAP-084 | **Closed** — load-generation capacity is proven; the 100 → 5,000 ramp executed 2026-10-22. The remaining constraint is environment fidelity (GAP-083) and the latency gate (GAP-130). |
| GAP-085 | **Closed** — synthetic load-cohort credentials are issued by an environment-gated, audited, idempotent provisioning CLI (`LoadCohortCli`) that hashes with the production encoder and refuses to run in production. No HTTP endpoint was added. |
| GAP-086 | The HTTP harness cannot perform database reconciliation; requires `psql`/a driver absent from the dev host. |
| GAP-087 | Prometheus/Grafana/Loki dashboards and alerts are not built, so §58-§61 cannot be validated (extends GAP-009/025). |
| GAP-088 | Backup/restore, blue-green/rollback and dry-run validation are not performed (extends GAP-007). |

## 13. Reproducing this evidence package

```bash
# E2 — local reduced-scale pilot smoke (executed here)
cd backend && ./mvnw test -Dtest=PilotSmokeIntegrationTest

# E1 — harness validity (no cohort; proves the tooling runs and writes evidence)
node scripts/p7/p7-harness.mjs --profile=ramp --users=0 --credentials-file=<empty.json> --label=selfcheck

# E3 — reconciliation (after a staged run, needs psql)
psql "$P7_DATABASE_URL" -v schedule_id="'<uuid>'" -v expected_questions=100 -v expected_options=4 \
  -f scripts/p7/reconcile.sql

# E4-E9 — staged runs (require the production-like environment; see scripts/p7/README.md)
node scripts/p7/p7-harness.mjs --profile=ramp --users=5000 --credentials-file=... --schedule-id=<uuid>
```

Large artifacts (metrics exports, logs) are **not** committed; only this concise summary is. Secrets
are never stored here (§16, §60).
