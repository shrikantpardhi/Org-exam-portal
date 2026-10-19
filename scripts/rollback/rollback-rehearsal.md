# Rollback rehearsal — procedure and record template

**Specification:** `specs/21-P12-Hardening-V1.5.md` §128–§131.

This document is the procedure `docs/testing/p12/rehearsal-*.md` records evidence against.

---

## 1. The rollback rule this rehearsal proves (§130, §78)

> Flyway migrations are **not blindly reversed**. Application rollback deploys the **previous
> application image** against the **current schema**, which is why every migration must be
> forward-compatible (additive): the previous application version must run correctly against the
> schema produced by the newer migration set.

Consequence for this platform: a rollback never touches `flyway_schema_history`. It is an
**image swap plus verification**, not a database operation.

## 2. Pre-rollback state to capture

| Field | How captured |
|---|---|
| Candidate image tags | `docker compose images` before the rehearsal |
| Migration set | `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank` |
| Data snapshot for reconciliation | run `backup-postgres.sh` immediately before the rollback (this is also step 1 of the deployment runbook §128) |
| Container/image identity | `docker inspect --format '{{.Image}}'` on backend and frontend |

## 3. Forward-compatibility precheck (performed before any rollback is attempted)

The previous application must run against the new schema. Verify the candidate's migration set is
purely additive — no destructive statement exists in any migration it added:

```bash
# Must return nothing for a forward-compatible release.
grep -rnEi 'DROP[[:space:]]+(TABLE|COLUMN|INDEX|CONSTRAINT)|TRUNCATE|DELETE[[:space:]]+FROM' \
    backend/src/main/resources/db/migration/ | grep -v 'DROP DATABASE IF EXISTS'
```

For V1–V24 this check returns only the restore script's own `DROP DATABASE IF EXISTS` guard —
every migration is `CREATE TABLE` / `CREATE INDEX` / `INSERT` seed data, so schema V24 is
backward-compatible with any application built for an earlier migration set.

## 4. Rollback steps

1. **Preserve the previous image** (this must already exist as a tag; a rollback without a
   preserved previous image is impossible — which is why release candidates tag their images):

   ```bash
   docker tag exam-platform-backend:candidate exam-platform-backend:previous-stable
   docker tag exam-platform-frontend:candidate exam-platform-frontend:previous-stable
   ```

2. **Stop further rollout** (§129): stop the stack's app profile.

   ```bash
   docker compose --profile core --profile app --profile edge stop backend frontend
   ```

3. **Deploy the previous stable image** by recreating the app services from the preserved tag:

   ```bash
   docker compose --profile core --profile app --profile edge up -d \
     --no-build --force-recreate backend frontend
   ```

4. **Health checks** (§129): wait for the backend container health, then through the edge:

   ```bash
   curl -fsS http://localhost:8081/health           # overall status UP
   curl -fsS http://localhost:8081/health/readiness # includes the database check (through PgBouncer)
   ```

5. **Smoke tests**: the API auth boundary still refuses anonymous access, and the edge still
   hides the actuator tree:

   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/api/v1/reports/catalog   # 401
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/actuator/health           # 404
   ```

6. **Reconciliation**: the rollback must not have altered data. Compare row counts of the
   critical tables before and after (from the backup `.meta` taken in step 2, or by re-running
   the counts and comparing). Expected: identical — an application rollback only swaps images.

7. **Observe critical paths and record the incident** (§129): note the rollback duration, the
   trigger, and the follow-up.

## 5. Honest scope limitation of this rehearsal

Only **one** candidate build has ever been produced in this repository, so the
`previous-stable` image is byte-identical to the candidate. The rehearsal therefore proves the
**procedure** — image preservation, stop, redeploy, health, smoke, reconciliation, duration —
but **not a version delta** (previous code running against newer schema). A version-delta
rollback is exercised for the first time in the first real release with two distinct candidates.
Until then, the record in `docs/testing/p12/rehearsal-*.md` must state this explicitly, and the
forward-compatibility precheck in §3 is the compensating control for schema compatibility.
