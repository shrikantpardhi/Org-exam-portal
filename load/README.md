# `load/` — P7 load harness

Reproducible load harness for the frozen P7 workloads (`specs/16-P7-Pilot-V1.5.md` §24–§35) and the
P12 performance gate (`specs/21-P12-Hardening-V1.5.md` §9, §56–§60).

The operator-facing procedure is [`docs/testing/p7/load-run.md`](../docs/testing/p7/load-run.md). This
file explains what the pieces are and why they are shaped this way.

## What is here

| Path | Role |
|---|---|
| `seed.mjs` | Builds the dataset through the frozen HTTP contract: identities, academic structure, 100 published questions, published blueprint + assessment version, schedules, snapshot preparation. |
| `wait-ready.mjs` | Blocks until every prepared attempt is `READY`. Mandatory before any start burst. |
| `build-credentials.mjs` | Turns the seed manifest into the k6 credential manifest. |
| `k6/scenarios.js` | The six workload profiles (A–F) and the entry point k6 runs. |
| `k6/common.js` | HTTP helpers, metric definitions and business-outcome assertions. |
| `k6/options.js` | Per-profile scenario shape and the frozen thresholds. |
| `k6/summary.js` | `handleSummary` — reshapes a run into the P12 §10 evidence fields. |
| `k6/config.js` | Every environment variable, with defaults. |
| `ramp.mjs` | Runs the P7 §33 ramp (100 → 5,000), asserting the zero-snapshot-insert invariant between steps. |
| `out/` | Generated: manifests, credentials, per-run summaries, ramp evidence. Git-ignored. |

## Profiles

| | Profile | k6 export | Executor | Models |
|---|---|---|---|---|
| A | `start` | `startBurst` | `shared-iterations` | 5,000 starts in a narrow interval; snapshots already READY (§26–§27) |
| B | `steady` | `steadyExam` | `shared-iterations` | One student working: read, save, navigate, submit (§28) |
| C | `autosave` | `autosaveBurst` | `shared-iterations` | Autosave under contention, duplicate retries, stale `If-Match` (§29) |
| D | `submit` | `submissionBurst` | `shared-iterations` | Submissions plus duplicate submits (§30) |
| E | `recovery` | `recoveryBurst` | `shared-iterations` | Reconnect to the same attempt/snapshot/answers/timer (§31) |
| F | `mixed` | `mixedExam` | `shared-iterations` | The realistic blend used for the capacity claim (§32) |

Every profile uses `shared-iterations` with exactly `P7_USERS` iterations, so each VU is **one student
sitting one exam**. `P7_DURATION_SECONDS` is the length of the exam work inside an iteration, not the
length of the scenario; `P7_VUS` caps parallelism without adding iterations.

> This was a correctness fix, not a preference. With `constant-vus`, each VU began a *fresh* exam when it
> finished one, so a 60-second run at 500 VUs produced 6,591 logins instead of 500 and the headline
> concurrency was wrong. It also measured the start path instead of exam work.

## Design decisions worth knowing

**The harness never writes to the database.** Every business mutation goes through the frozen HTTP
contract, so the platform enforces its own invariants rather than the generator bypassing them. The single
exception is credential provisioning — see below.

**The zero-snapshot-insert assertion is external.** Start Exam must consume an already-prepared snapshot.
The harness cannot see tables, so §27 is proven by a read-only count comparison around the burst
(`attempt_question`, `attempt_question_option`). `ramp.mjs` does this automatically between steps and
stops the ramp if either count moves.

**Success means the business outcome, not the socket.** A `200` whose payload does not report `SUBMITTED`
is not a successful submission. Latency success without data-integrity success is a failed run (§48).

**Absent data is reported as absent.** A metric a profile never produced is `null` in the summary, never
zero. Zero would be indistinguishable from a perfect result.

**Thresholds are per profile.** k6 evaluates a threshold even when its metric has no samples, which would
fail the start burst on an empty `submission_success_rate`. Each threshold is attached only to the profiles
that generate it; the *values* are the frozen gate numbers.

**Disjoint cohorts per ramp step.** A student who has already submitted cannot start again. Each step draws
`[OFFSET, OFFSET + USERS)` from the credential manifest, which `build-credentials.mjs` builds from
attempts that are genuinely `NOT_STARTED` and `READY` — so slicing by index is exact. The full P7 §33
progression needs `100+500+1,000+2,000+3,000+4,000+5,000 = 15,600` pairs, so it is run as two invocations
with the manifest rebuilt in between; no data is mutated by hand between steps.

> An earlier version derived the offset from a *count* of consumed attempts. That is wrong: the consumed
> set is not a contiguous prefix, so the slice overlapped students who had already started and produced
> 55–100% spurious "no prepared attempt" failures.

**Concurrency is explicit.** For a concurrency ramp, a step of N users means N concurrent users, so the VU
count defaults to `USERS`. `P7_VUS` exists only to cap the burst profiles, where iteration count governs
the workload.

## Credential provisioning

`POST /api/v1/users` deliberately assigns a non-usable credential (P1 §56/§58) because accounts are meant
to be activated by password reset, and reset delivery is not built (GAP-002). A synthetic cohort has no
mailbox, so it could never authenticate through the normal onboarding flow.

`seed.mjs --apply-sql` therefore invokes the application's own provisioning CLI
(`com.company.exam.identity.loadcohort.LoadCohortCli`) inside the backend container. It creates accounts
through the platform's entities, assigns `STUDENT`, hashes each credential with the production
`PasswordEncoder`, audits every account, and is idempotent by email. It **refuses to run when
`app.environment` is production** and is disabled unless `app.load-cohort.enabled=true`. It adds no HTTP
endpoint: the CLI boots a non-web context and exits.

Credentials are generated per account, so `out/manifest.json` carries `studentCredentials`
(email → credential) rather than one shared password. `out/` is git-ignored, so no credential is ever
committed. Without `--apply-sql` the script reports what it would provision and stops.

## Requirements the environment must meet

- A provisioned dataset with every attempt `READY` *before* the start burst.
- A delivery worker able to prepare the cohort in bounded time. With the production defaults (batch 50,
  interval 30 s) 5,000 snapshots take roughly 50 minutes; `DELIVERY_WORKER_BATCH_SIZE` /
  `DELIVERY_WORKER_INTERVAL` raise that for dataset construction and the values used are recorded in the
  evidence.
- Enough headroom that the load generator is not competing with the stack it is measuring. On a single
  development host it is, and the evidence says so.
