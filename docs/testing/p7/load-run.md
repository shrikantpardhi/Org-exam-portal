# P7 load-test runbook

Reproduces every number in `docs/testing/p7/load-YYYY-MM-DD.md` from a clean environment.

Frozen definitions this runbook implements are in [`README.md`](README.md) §1–§4; the specification is
`specs/16-P7-Pilot-V1.5.md` §24–§36 and the gate is `specs/21-P12-Hardening-V1.5.md` §9, §56–§60.

> **Read this first.** A load run is only evidence if the environment matches the workload. On a
> single development host the numbers describe *that host*, not production capacity. The evidence file
> states this explicitly and the gap register records it as a residual limitation. Do not copy a
> passing p95 from a dev host into a release decision.

---

## 1. Prerequisites

| Requirement | Detail |
|---|---|
| k6 | v2.2.0 verified. Installed at `C:\Program Files\k6`; prepend to `PATH` in every new shell. |
| Node.js | ≥ 18 (used by the seed, credential, readiness and ramp scripts). |
| Docker + Compose | Required only for the local stack and for the read-only reconciliation queries. |
| Stack | `docker compose --profile core --profile app --profile edge up -d`, all services healthy. |
| `.env` | Copy from `.env.example`. Must exist or Compose refuses to start (`${VAR:?}` guards). |
| Disk | ~50 MB per full run in `load/out/` (summaries + manifests). |

Verify the generator can reach the target before doing anything expensive:

```powershell
$env:Path = "C:\Program Files\k6;" + $env:Path
k6 version
Invoke-RestMethod http://localhost:8080/actuator/health | Select-Object status
```

## 2. Environment variables

Every value the harness reads, with its default. A run is reproducible when this table plus the seed is
the same.

| Variable | Default | Meaning |
|---|---|---|
| `P7_BASE_URL` | `http://localhost:8081` | Target base URL. `8081` is the Nginx edge; `8080` is the backend directly. |
| `P7_PROFILE` | `start` | `start`, `steady`, `autosave`, `submit`, `recovery`, `mixed`. |
| `P7_USERS` | `100` | Students for this run. Burst profiles act once per student. |
| `P7_QUESTIONS` | `100` | Questions per attempt; used to assert the snapshot is complete. |
| `P7_VUS` | `200` | Concurrent VUs. Caps the burst's parallelism. |
| `P7_DURATION_SECONDS` | `60` | Duration for sustained profiles (steady, autosave, mixed). |
| `P7_AUTOSAVE_INTERVAL` | `10` | Client autosave pacing assumption for profile C. |
| `P7_CREDENTIALS_FILE` | `load/out/credentials.json` | Credential manifest, relative to `load/`. |
| `P7_RELEASE` | *(unset)* | Release identifier recorded in the evidence. |
| `P7_ENVIRONMENT` | *(unset)* | Environment label recorded in the evidence. |
| `P7_OPERATOR` | *(unset)* | Operator recorded in the evidence. |
| `P7_LABEL` | `ramp` | Free-text run label. |
| `P7_PSQL_CONTAINER` | `exam-platform-postgres-1` | Container used for read-only reconciliation. |

## 3. Build the dataset

The dataset is built through the frozen HTTP contract so the platform enforces its own invariants
(published content, published assessment, prepared snapshots). Four ordered steps, run in this order,
from the repository root.

### 3.1 Provision identities

```powershell
$env:NODE_ENV = $null
node load/seed.mjs --students=5000 --questions=100 --label=reference --apply-sql=true
```

**Why provisioning is a separate step:** `POST /api/v1/users` deliberately assigns a non-usable
credential (P1 §56/§58) because the account is meant to be activated by password reset, and reset
delivery is not built (GAP-002). A synthetic student has no mailbox, so the cohort could never
authenticate through the normal onboarding flow.

The seed therefore invokes the application's own load-cohort provisioning CLI
(`com.company.exam.identity.loadcohort.LoadCohortCli`), which runs inside the backend container. It
creates the accounts through the platform's own entities, assigns `STUDENT`, hashes each credential with
the production `PasswordEncoder`, writes a `LOAD_COHORT_CREATE` audit entry per account, and is
idempotent by email — re-running re-issues credentials instead of duplicating users.

Two safeguards make this safe:

* **Environment gate.** The mechanism refuses to run when `app.environment` is `production`. It is also
  off unless `app.load-cohort.enabled=true`, so a deployment that never opts in has no provisioning code
  path. Both switches are required.
* **No endpoint.** There is no HTTP surface for this. The CLI boots a non-web Spring context and exits,
  so nothing new ships to production and the frozen onboarding contract is untouched.

Credentials are generated per account, so the manifest carries `studentCredentials` (email → credential)
rather than a single shared password. `load/out/` is git-ignored: no credential is ever committed.

Without `--apply-sql` the script reports what it would provision and stops, so a read-only run cannot
mutate the database.

The seed also creates **two corpus identities**: an admin author and a **separate reviewer**. The platform
refuses to let a user approve a version they submitted (P3 §112), so the lifecycle needs both.

### 3.2 Create assessment + prepare snapshots

The same command continues: academic structure → 100 published questions → blueprint → assessment
version → rule materialization → publish → schedule → `POST /{scheduleId}/prepare-attempts`.
It writes `load/out/manifest.json`.

### 3.3 Wait for READY

```powershell
node load/wait-ready.mjs --timeout-seconds=7200 --interval-seconds=15
```

**This step is mandatory.** Preparation is an asynchronous background job (`SnapshotPreparationWorker`,
batch 50, interval 30 s by default). Starting the burst while snapshots are `PENDING` would measure
snapshot *creation*, which P7 §26–§27 forbid. At the default batch size, 5,000 snapshots need roughly
50 minutes. The script prints the `attempt_question` / `attempt_question_option` baseline — **record
both numbers**, they are the baseline for the zero-insert assertion.

### 3.4 Build the credential manifest

```powershell
node load/build-credentials.mjs
```

## 4. Run the profiles

Each profile runs independently. Set the target, run, read the summary from `load/out/`.

```powershell
$env:Path = "C:\Program Files\k6;" + $env:Path
$env:P7_BASE_URL = "http://localhost:8080"
$env:P7_QUESTIONS = "100"
$env:P7_RELEASE = "rc-<tag-or-commit>"
$env:P7_ENVIRONMENT = "local-docker-single-host"
$env:P7_OPERATOR = "<your name>"

$env:P7_PROFILE = "start";    $env:P7_USERS = "5000"; $env:P7_VUS = "200"
k6 run load/k6/scenarios.js
```

| Profile | Command | Models |
|---|---|---|
| A `start` | `P7_PROFILE=start P7_USERS=5000` | 5,000 starts in a narrow interval (§26) |
| B `steady` | `P7_PROFILE=steady P7_USERS=500 P7_DURATION_SECONDS=120` | Steady exam work (§28) |
| C `autosave` | `P7_PROFILE=autosave P7_USERS=500 P7_DURATION_SECONDS=120` | Autosave burst + duplicate/stale retries (§29) |
| D `submit` | `P7_PROFILE=submit P7_USERS=5000` | Submission burst + duplicate submits (§30) |
| E `recovery` | `P7_PROFILE=recovery P7_USERS=2000` | Reconnect, no duplicate attempt (§31) |
| F `mixed` | `P7_PROFILE=mixed P7_USERS=500 P7_DURATION_SECONDS=300` | Realistic blend (§32) |

Every profile uses `shared-iterations` with exactly `P7_USERS` iterations, so each VU models **one
student sitting one exam**. `P7_DURATION_SECONDS` is the length of the exam *work* inside one iteration,
not the length of the scenario. `P7_VUS` (default: same as `P7_USERS`) only caps how many Iterations run
at once; it never adds iterations.

> **Why every profile is `shared-iterations`.** An earlier version used `constant-vus` for the sustained
> profiles. That made each VU start a *fresh* exam every time it finished one, so a 60-second run with 500
> VUs performed 6,591 logins instead of 500 and the reported concurrency was meaningless. It also spent
> most of its time in the start path rather than measuring exam work. `P7_DURATION_SECONDS` therefore no
> longer bounds a sustained scenario.

> **A note on profile A and D ordering.** Both start or submit the *same* attempt per student. A student
> who already submitted cannot start again (the API returns `Attempt is already submitted`). Run the
> burst profiles against a freshly prepared dataset, or re-prepare between them. The ramp runner checks
> this and refuses to measure more users than there are prepared attempts.

## 5. Run the ramp

```powershell
node load/ramp.mjs --profile=mixed --steps=100,500,1000,2000,3000,4000,5000
```

Those are the P7 §33 steps. The runner consumes a **disjoint cohort per step**, so the ramp needs
`100+500+1000+2000+3000+4000+5000 = 15,600` fresh `(student, schedule)` pairs — see §4 on preparing
enough schedules. It refuses to start if the credential manifest cannot cover the sum.

The runner, per step: records the snapshot baseline → runs k6 → re-reads the counts → asserts the delta
is **exactly zero** → checks thresholds → decides whether to escalate. It stops rather than escalating
on a snapshot insert, a threshold breach, or an exam-failure rate above budget (P7 §33: "stop and
diagnose rather than blindly escalating"). It performs **no data mutation between steps**; the only
writes are the exam traffic itself.

Output: `load/out/p7-ramp-<profile>-<epoch>.json` containing every step's measured numbers.

## 6. Verify the zero-snapshot-insert assertion

The harness cannot see the database, so §27 is proven by a read-only comparison around the burst.

```powershell
# before
docker exec exam-platform-postgres-1 psql -U exam_app -d exam_platform -t -A `
  -c "SELECT count(*) FROM attempt_question; SELECT count(*) FROM attempt_question_option;"
```

Run profile A, then re-run the same query. **Both numbers must be identical.** An increase means Start
Exam materialized a snapshot, which is a critical integrity failure regardless of latency. The ramp
runner performs this automatically.

## 7. Post-run reconciliation

```powershell
Get-Content scripts/p7/reconcile.sql | docker exec -i exam-platform-postgres-1 psql -U exam_app -d exam_platform
```

Read-only. Confirms: exactly one submission per attempt, no duplicate attempts per student, no
cross-user responses, snapshot status distribution. Any deviation means the run fails the integrity
gate even if latency passed.

## 8. Record the evidence

Copy every measured number into `docs/testing/p7/load-YYYY-MM-DD.md`. The file must carry the P12 §10
fields: software version, frontend version, DB migration version, infrastructure/config, workload, data
volume, duration, profile, result, known limitations, operator, date. Fill in the limitations section
honestly — an absent measurement is recorded as absent, never as zero.

## 9. Stop conditions

Abort and diagnose — do not continue the ramp — when any of these occur:

- snapshot insert delta ≠ 0 (integrity failure);
- exam failure rate > 1%;
- submission success ≤ 99%;
- p95 ≥ 100 ms on applicable endpoints;
- any 5xx trend, or the backend losing its database connection;
- host saturation (sustained CPU > 90% or memory pressure) making the generator itself the bottleneck.

## 10. Tear-down

```powershell
docker compose --profile core --profile app --profile edge --profile observability down
```

The dataset lives in the Postgres volume. Remove it with `down -v` to reclaim space; the next run needs
a fresh seed because credentials are provisioned by SQL.
