# P12 Rehearsal Record — 2026-10-19

Backup, restore and rollback rehearsal for the release candidate, executed against the **Docker
stack** (`docker-compose.yml`, profiles `core` + `app` + `edge`), never against embedded PostgreSQL.

Specification: `specs/21-P12-Hardening-V1.5.md` §71–§79, §128–§131.

---

## 1. Candidate version

| Field | Value |
|---|---|
| Application version | 1.5 (`exam-platform-backend` 1.5.0-SNAPSHOT) |
| Frontend version | 1.5 (`exam-platform-frontend` 1.5.0) |
| Backend image id | `sha256:d63e9db30965…` (`exam-platform-backend:latest`) |
| Frontend image id | `sha256:b685ffbc122d…` (`exam-platform-frontend:latest`) |
| Database migration version | **V24** (24 applied migrations, all `success = true`) |
| Database | PostgreSQL 16 (`postgres:16-alpine`) |
| Connection path | application → **PgBouncer** (`pgbouncer:6432`, transaction pooling) → PostgreSQL |
| Cache | Redis 7 (`redis:7-alpine`, `--requirepass`) |
| Edge | Nginx 1.27-alpine, publishing `http://localhost:8081` |
| Host | Windows, Docker Desktop 29.8.2 (per-user install, engine started for this rehearsal) |
| Date | 2026-10-19 |

## 2. Backup (§71, §74)

| Field | Value |
|---|---|
| Tool | `scripts/backup/backup-postgres.sh` (`pg_dump -Fc`, custom format) |
| Backup identifier | `exam-platform-20261019T142009Z` (rehearsal), `exam-platform-20261019T142138Z` (pre-rollback) |
| Storage location | `data/backups/` (gitignored) — dump, globals, `.sha256`, `.meta` |
| Backup size | **195,392 bytes** (≈191 KiB) |
| Backup duration | **1 s** (plus 1 s for the globals dump and evidence capture) |
| Consistency | Single MVCC snapshot (`pg_dump -Fc`); the application was running throughout, with no downtime |
| Integrity artefact | `sha256` checksums of the dump and the globals, plus a `.meta` evidence file (PostgreSQL version, migration baseline, table count, and **exact row counts for all 64 public tables**) |
| Retention | `BACKUP_RETENTION_DAYS` (default 90); pruning is implemented and reported by the script |

## 3. Restore (§72, §74)

| Field | Value |
|---|---|
| Tool | `scripts/backup/restore-postgres.sh` |
| Target | `exam_platform_restore_rehearsal` — an **isolated** database, never the live one (the script refuses if the target equals the live database) |
| Restore duration | **1 s** (dump copied into the container, then `pg_restore -j 2` from that file) |
| Method | `pg_restore` with `--no-owner --no-privileges`; the rehearsal database is dropped afterwards |

### Integrity verification result — **6 of 6 checks PASSED**

| # | Check | Result |
|---|---|---|
| C1 | Schema object parity with the live source (tables, indexes, sequences, constraints) | **PASS** |
| C2 | Exact row counts for all **64 public tables**, compared against the counts recorded *at backup time* | **PASS** |
| C3 | No invalid indexes in the restored database | **PASS** (0 invalid) |
| C4 | Flyway history parity — installed rank, version, **checksum**, success | **PASS** (24 migrations, identical checksums) |
| C5 | The frozen business constraints exist (evaluation one-current-row partial unique index, `payment_record` reference idempotency, template/rule/lookup uniqueness, notification status, audit FK) | **PASS** (10/10) |
| C6 | Foreign-key smoke joins resolve identically in source and restored databases | **PASS** |

## 4. Rollback (§128–§131)

| Field | Value |
|---|---|
| Tool | `scripts/rollback/rollback-rehearsal.sh` |
| Forward-compatibility precheck | **PASS** — no migration contains `DROP TABLE/COLUMN/INDEX/CONSTRAINT`, `TRUNCATE` or `DELETE`; every schema change V1–V24 is additive |
| Previous image | `exam-platform-backend:previous-stable` = `sha256:d63e9db30965…` (preserved by tagging before the swap) |
| Candidate image | `exam-platform-backend:candidate` = `sha256:d63e9db30965…` |
| Migration set at rollback | 24 applied, max version 24 — **unchanged**; migrations are not reversed (§130) |
| **Rollback duration** | **2 s** to redeploy the previous image; the backend was healthy again on the first health poll |
| Health after rollback | `/health/readiness` → 200 through the edge; `/health` reports `status: UP` (db, redis, liveness, readiness all UP) |
| Smoke after rollback | API without a token → **401** (auth boundary holds); `/actuator/health` → **404** (actuator tree still hidden); frontend root → **200** |
| **Reconciliation result** | **PASS** — row counts of `user`, `assessment`, `student_attempt`, `evaluation`, `result`, `purchase`, `audit_log` are **identical before and after** the rollback, and the migration set is byte-identical |

**Rollback rehearsal result: 12 of 12 checks passed (0 failures).**

## 5. Unexpected findings

Five real defects were found **by executing the stack** — none was visible from configuration review,
and four would have broken a production deployment. They are fixed in this change set.

| # | Finding | Impact | Fix |
|---|---|---|---|
| F1 | `spring.datasource.hikari.connection-timeout: 5s` in `application-local.yml`, `application-staging.yml` and `application-prod.yml`. HikariCP's `connection-timeout` is a **long in milliseconds**, not a duration string. | The application **fails to start** ("failed to convert java.lang.String to long"). Only the `test` profile was ever exercised, so three of four profiles were broken. | Changed to `connection-timeout: 5000` in all three profiles, with a comment explaining the unit. |
| F2 | The stack's Redis runs with `--requirepass`, but nothing supplied the password to the client. | `NOAUTH HELLO must be called with the client already authenticated` — the Redis health check failed and the overall health was DOWN. | `spring.data.redis.password` is now wired to `REDIS_PASSWORD` (empty by default, so a password-less local Redis still works) and the compose `backend` service supplies it. |
| F3 | `frontend/Dockerfile` copied `/app/public`, but the repository has **no `public/` directory**. | The frontend image **failed to build** on a clean clone — every image build was broken. | The `public/` copy was removed; Next.js treats it as optional and static assets come from `.next/static`. |
| F4 | Alpine's `pgbouncer` package (1.22.1) creates **no `pgbouncer` user or group**. | The PgBouncer image failed to build (`chown: unknown user/group pgbouncer:pgbouncer`). | The image now creates the unprivileged account explicitly before the `chown`. |
| F5 | `pgbouncer.ini.template` used `${VAR:-default}` syntax. `envsubst` does **not** expand shell default-value syntax, so the literal text reached PgBouncer. | PgBouncer **refused to start** ("invalid value … for parameter max_client_conn"), and the whole stack could not come up. | Defaults are now applied in the shell (`: "${VAR:=default}"`), the template uses bare placeholders, and the entrypoint **fails fast** if any `${` placeholder survives rendering. |

Three script defects were also found by executing the rehearsal itself:

| # | Finding | Fix |
|---|---|---|
| F6 | `psql` was invoked without `-d "$POSTGRES_DB"`. The official postgres image creates `POSTGRES_DB` but **no database named after the user**, so the metadata and row-count queries failed with `database "exam_app" does not exist`. | Every `psql` call now names the database explicitly; the live database name is read from the container's own environment. |
| F7 | `pg_restore` cannot restore **in parallel from standard input**. | The dump is copied into the container first, then restored in parallel from that file. |
| F8 | The restore script had a mismatched brace — `"$(basename "${CHECKSUMS})"` — whose unterminated quote made the shell report a syntax error ~17 lines later, far from the cause. | Fixed, and the script is now covered by `bash -n` syntax checking. |

**Also corrected:** earlier records stated that Docker Desktop was "not installed" on this host. That
was wrong — it is installed per-user at
`C:\Users\<user>\AppData\Local\Programs\DockerDesktop`, but it is **not running by default**, so the
engine must be started before any container work. GAP-007 has been updated accordingly.

## 6. Residual limitations

1. **PITR is not configured and therefore not claimed** (§73 is explicit on this point). What is
   proven is logical backup and restore to the *backup point*, not recovery to an arbitrary time. The
   procedure to enable and prove PITR is in `scripts/backup/pitr.md`; every acceptance box there is
   still unticked. **GAP-131 therefore closes for logical backup/restore only.**
2. **The rollback rehearsal did not exercise a version delta.** Only one candidate build has ever
   existed, so `previous-stable` is byte-identical to the candidate. The rehearsal proves the
   *procedure* (image preservation, stop, redeploy, health, smoke, reconciliation, duration) and the
   forward-compatibility of the schema, but not "previous code against newer schema". The first real
   release with two distinct candidates will exercise that.
3. **The database was empty of business data.** The rehearsal ran against a freshly migrated
   database (one seeded system user, no attempts, results or purchases), so the restore proved
   *schema and mechanism* fidelity rather than the fidelity of a large, relationally rich dataset.
   Re-running against a populated database is the next step, and it is the P7-scale dataset
   (GAP-083/084).
4. **No RPO/RTO targets are claimed** (§75 forbids inventing them). The measured restore duration
   (1 s) is a fact about this dataset size, not a contractual recovery time.
5. **Backup storage is local.** `data/backups/` lives on the host volume; there is no off-host copy,
   no encryption at rest, and no monitoring of backup age. Those are operational decisions
   (GAP-133 area) that this rehearsal does not cover.
6. **Migration rehearsal at production-like volume remains open** (GAP-133): the migration chain
   applies cleanly and is proven forward-compatible, but it has not been timed against a
   production-scale dataset.

## 7. Verdict

| Gate | Result |
|---|---|
| A backup completes and is stored | **PASS** — 195,392 bytes in 1 s, checksummed, with evidence |
| A restore into an isolated database completes | **PASS** — 1 s, into `exam_platform_restore_rehearsal` |
| Integrity checks pass after restore | **PASS** — 6 of 6 |
| A rollback rehearsal completes with the previous image | **PASS** — 12 of 12 checks, 2 s redeploy |
| The rehearsal record is committed | This record, in `docs/testing/p12/` |

**GAP-131 (backup and restore) and GAP-132 (rollback) are closed for the scope proven above.**
The P12 release decision remains **NO-GO** on the three gates that this rehearsal does not touch:
GAP-130 (no performance gate measured), GAP-133 (migration rehearsal at production-like volume),
and GAP-135 (dress rehearsal, production smoke test, post-release monitoring).
