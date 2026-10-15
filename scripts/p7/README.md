# P7 load-test tooling

Scale-validation tooling for `specs/16-P7-Pilot-V1.5.md`. It drives the **frozen HTTP contract**
only and never writes to the database; database integrity gates are a separate read-only step.

## Files

| File | Purpose |
|---|---|
| `p7-harness.mjs` | Dependency-free Node (>=18) load harness: authenticate, read, start, autosave (ETag), recover, submit. Emits a JSON evidence file per run. |
| `reconcile.sql` | Read-only post-run reconciliation and integrity-gate queries (§49-§54, §93-§98). |
| `p7-dataset.runbook.md` | The ordered API sequence used to build the reference dataset (1 org, 5,000 students, 1 published assessment version, 100 questions, prepared attempts, answer key). |

## Prerequisites

1. A **production-like environment** (§12): Next.js, Spring Boot, Nginx, PgBouncer, PostgreSQL, Redis,
   background workers, storage, and the monitoring stack. This does not exist on the development
   host (see `docs/known-gaps.md` GAP-083/087).
2. A **provisioned dataset** built via `p7-dataset.runbook.md`, with attempts **READY before** any
   start burst (§23).
3. A **credentials file** (`P7_CREDENTIALS_FILE`): a JSON array of
   `{"email": "...", "password": "...", "scheduleId": "<uuid>"}`.
   The frozen P1 onboarding assigns a non-usable credential and reset delivery is not built
   (GAP-002/GAP-085), so a synthetic cohort cannot self-authenticate — credentials must be
   provisioned out of band by the operator.

## Usage

```bash
# Start burst (attempts must already be READY)
node scripts/p7/p7-harness.mjs --profile=start --users=5000 \
  --credentials-file=/secure/p7-creds.json --schedule-id=<uuid> \
  --release=<sha> --environment=staging --label=staging-start-5000

# Ramp 100 -> 5000, stopping on a severe start failure (§33)
node scripts/p7/p7-harness.mjs --profile=ramp --credentials-file=/secure/p7-creds.json --schedule-id=<uuid> --label=staging-ramp

# Autosave burst (ETag, 10% stale-retry) / submission burst / recovery burst
node scripts/p7/p7-harness.mjs --profile=autosave   --users=5000 ...
node scripts/p7/p7-harness.mjs --profile=submit     --users=5000 ...
node scripts/p7/p7-harness.mjs --profile=recovery   --users=5000 ...

# Soak and spike
node scripts/p7/p7-harness.mjs --profile=soak  --users=1000 --duration-seconds=3600 ...
node scripts/p7/p7-harness.mjs --profile=spike --users=5000 ...
```

Key options (env `P7_*` or `--flag=value`): `base-url`, `seed`, `profile`, `users`, `ramp`,
`questions`, `concurrency`, `duration-seconds`, `out`, `credentials-file`, `schedule-id`,
`release`, `environment`, `label`.

## What the harness measures and asserts

- Latency **p50/p95/p99 and max** per operation (§44).
- Error classification: `network`, `timeout`, `http_4xx`, `http_5xx`, `precondition` (§46).
- HTTP assertions: `start` succeeds and returns the caller's attempt; recovery returns the same
  attempt; duplicate `submit` is idempotent (same `submittedOn`, durably `SUBMITTED`) (§47);
  stale `If-Match` autosave returns 412 and the retry with the fresh ETag succeeds (§29).

It does **not** read the database, so the database integrity gates (§49-§54) must be run separately:

```bash
psql "$P7_DATABASE_URL" -v schedule_id="'<uuid>'" -v expected_questions=100 -v expected_options=4 \
  -f scripts/p7/reconcile.sql
```

## Status

The harness has been **validated end-to-end only at zero cohort** on the development host (it runs,
authenticates, and emits an evidence file). The staged runs (100→5,000, bursts, soak, spike, failure
injection) are **blocked** and are not claimed until a production-like environment exists.
