# Whole-project verification — 2026-10-15

A full re-verification of everything implemented so far (P0 through P11), performed from a clean
working tree. This is the authoritative baseline for the repository as it stands.

## What was run

| Check | Command | Result |
|---|---|---|
| Backend compile | `mvnw.cmd -DskipTests compile` | Clean |
| Backend tests | `mvnw.cmd test` | **306 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** |
| Frontend lint | `npm run lint` | Clean (exit 0) |
| Frontend tests | `npm test` (`vitest run`) | **18 test files, 56 tests, all passing** |
| Frontend build | `npm run build` (`next build`) | Succeeds; static/dynamic routes generated |
| Frontend dependency install | `npm install` | 475 packages (see the lock-file finding below) |

Environment: `JAVA_HOME=C:\java\jdk-25.0.4.1`, Maven Wrapper compiling with `--release 21`,
Node 24 / npm 11.19.0, Windows. `NODE_ENV` was unset for the frontend commands.

## Schema

23 Flyway migrations, `V1` through `V23`, with no gaps in the sequence. Flyway-versioned
`flyway_schema_history` is asserted per phase by the migration tests, and the full chain applies
cleanly on a fresh embedded PostgreSQL for every integration-test class.

Phase ownership of the migrations:

```text
V1-V3    Foundation + Identity (base schema, identity tables, identity seeds)
V4-V5    Academic
V6-V7    Content
V8       Delivery policy (Foundation prerequisite)
V9-V10   Assessment
V11-V13  Delivery runtime, background_job, delivery seeds
V14-V15  Evaluation
V16-V17  Analytics
V18-V19  Commerce
V20-V21  Communication
V22-V23  Administration
```

## Static checks

- No `TODO`, `FIXME`, `XXX`, `HACK`, "not implemented" marker, or `UnsupportedOperationException`
  anywhere in Java, TypeScript, SQL, or YAML sources.
- Every background worker is actually scheduled: `SnapshotPreparationWorker`, `EvaluationWorker`,
  `AnalyticsWorker`, `CommunicationWorkerScheduler` and the Identity `SessionExpiryJob` all carry
  `@Scheduled` under a `@EnableScheduling` configuration with a bounded task scheduler. Worker
  scheduling is therefore *not* an outstanding risk.
- Migrations are idempotent where they seed data (`ON CONFLICT DO NOTHING`), and the seeds are
  permission/role grants rather than invented domain data.

## Findings raised by this verification

1. **The committed `package-lock.json` was out of sync, and `npm ci` failed.**
   `npm ci` aborts with `EUSAGE ... Missing: @emnapi/runtime@1.11.3 from lock file` and
   `Missing: @emnapi/core@1.11.3 from lock file`. This is *not* the previously documented
   `NODE_ENV=production` artifact — `NODE_ENV` was unset when it failed. Repaired by running
   `npm install`, which added the two missing packages (`+36 / -20` lines) and left no version bumps
   elsewhere.
   **Impact:** `frontend/Dockerfile` line 10 runs `npm ci`, so every frontend container image build
   would have failed on a clean clone. Recorded as GAP-126.

2. **The frontend had never been verified.** `node_modules` was absent and no prior frontend
   lint/test/build result existed. It is now verified and green (see the table above). Recorded as
   GAP-127.

3. **`npm` 11 blocked three dependency install scripts** (`esbuild@0.21.5` and `unrs-resolver@1.12.2`
   postinstall) under its new `allowScripts` policy. Tests and build still passed, but a fresh CI host
   may need `npm install-scripts approve` (or an allow-list entry) or the native esbuild binary may be
   absent. Recorded as GAP-129.

4. **The frontend covers P1–P5 only.** Existing features are `auth`, `academic`, `content`,
   `assessment`, `delivery` and `system`. There is no evaluation/results UI; the P8, P9, P10 and P11
   frontends already have recorded gaps, but P6's did not. Recorded as GAP-128.

## Verification of the phase-specific acceptance claims

| Claim | Evidence |
|---|---|
| P6 scoring reads correctness only from `answer_key_detail` | `EvaluationCalculatorTest`, `EvaluationApiIntegrationTest`, `ReEvaluationIntegrationTest` |
| P6 rank/percentile determinism | `RankPercentileIntegrationTest` |
| P7 pilot path end-to-end against the real HTTP contract | `PilotSmokeIntegrationTest` (16 candidates × 5 questions, 16-way concurrency) |
| P8 analytics rebuild determinism and read-only behaviour | `AnalyticsIntegrationTest` + `AnalyticsCalculatorTest` |
| P9 duplicate callback does not create duplicate grants; amount tampering blocked | `CommerceIntegrationTest` |
| P10 asynchronous fan-out, retry backoff, provider-failure isolation | `CommunicationIntegrationTest`, `RetryPolicyTest` |
| P11 audit atomicity and immutability, search permission filtering, health secrecy, support masking | `P11AdministrationIntegrationTest`, `P11MigrationIntegrationTest` |

## Carried-forward limitations

The following remain unverified or unbuilt and are tracked in `docs/known-gaps.md` rather than being
claimed as done: the Docker/edge stack (GAP-007), CI (GAP-008), the observability stack (GAP-009),
the P7 staged scale/pilot evidence (GAP-083…088), the provider adapters for payments and email
(GAP-099, GAP-108), the P11 imports and bulk operations (GAP-119, GAP-120), and the deferred
frontends.
