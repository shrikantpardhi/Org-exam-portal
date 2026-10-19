# Point-in-Time Recovery (PITR) — procedure and status

**Applies to:** the `postgres:16-alpine` service in `docker-compose.yml` (core profile).
**Specification:** `specs/21-P12-Hardening-V1.5.md` §73.

> **PITR is NOT configured in this stack and is therefore NOT claimed as a capability.**
> §73 is explicit: *"PITR capability is not claimed unless it is actually configured and tested."*
> What is configured and tested today is **logical backup and restore** (`backup-postgres.sh`,
> `restore-postgres.sh`), which restores a database to the state at backup time — not to an
> arbitrary point in time. This document is the procedure an operator follows to add and prove
> PITR before it may be claimed.

---

## 1. What exists today

| Capability | Status | Tool |
|---|---|---|
| Consistent logical backup (snapshot at dump time) | Configured, rehearsed | `backup-postgres.sh` (`pg_dump -Fc` + globals + checksum + row-count evidence) |
| Restore into an isolated database with integrity checks | Configured, rehearsed | `restore-postgres.sh` |
| Restore to an arbitrary point in time | **Not configured, not tested, not claimed** | this document, §2–§4 |

## 2. Enabling WAL archiving in this deployment

The stack's `postgres` service needs three changes (none affects the frozen schema; they are
infrastructure configuration only):

1. **A WAL archive volume** in `docker-compose.yml`:

   ```yaml
   postgres:
     # ... existing configuration ...
     volumes:
       - postgres-data:/var/lib/postgresql/data
       - postgres-wal-archive:/var/lib/postgresql/wal-archive   # add
   ```

   and a matching named volume:

   ```yaml
   volumes:
     postgres-wal-archive:                                          # add
   ```

2. **PostgreSQL settings** (WAL level and archiving), via the `command:` key of the service so
   they survive any container rebuild:

   ```yaml
   postgres:
     command:
       - postgres
       - -c
       - wal_level=replica
       - -c
       - archive_mode=on
       - -c
       - archive_command=test ! -f /var/lib/postgresql/wal-archive/%f && cp %p /var/lib/postgresql/wal-archive/%f
   ```

   `archive_command` must return non-zero when the file already exists, so retries are safe.

3. **A base backup** — the starting point any recovery replays WAL from. With the stack running:

   ```bash
   # Physical base backup from the running primary, labels included
   docker compose exec -T postgres sh -c \
     'pg_basebackup -U "$POSTGRES_USER" -D /var/lib/postgresql/wal-archive/base -Fp -Xs -P'
   ```

   Schedule this alongside the logical backup (the same cadence as `backup-postgres.sh`).

## 3. Recovery to a point in time

Rehearse PITR only against an **isolated** PostgreSQL instance, never by pointing the live
service's data volume at a recovery.

1. Start a disposable instance with the archived WAL mounted:

   ```bash
   docker run --rm -d --name pitr-rehearsal \
     -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
     -v exam-platform_postgres-wal-archive:/wal-archive:ro \
     -v pitr-data:/var/lib/postgresql/data \
     postgres:16-alpine
   ```

2. Copy the base backup into its data directory and create the recovery signal:

   ```bash
   docker exec pitr-rehearsal sh -c '
     rm -rf /var/lib/postgresql/data/* &&
     cp -r /wal-archive/base/* /var/lib/postgresql/data/ &&
     chmod 700 /var/lib/postgresql/data &&
     touch /var/lib/postgresql/data/recovery.signal &&
     cat >> /var/lib/postgresql/data/postgresql.auto.conf <<"EOF"
   restore_command = "cp /wal-archive/%f %p"
   recovery_target_time = "2026-10-16 12:34:56+00"
   recovery_target_action = "promote"
   EOF'
   docker restart pitr-rehearsal
   ```

3. Verify the instance reaches the target time: `SELECT pg_last_wal_replay_lsn();` returns a
   value and `pg_is_in_recovery()` returns false after promotion.

4. **Integrity:** run the same six checks as `restore-postgres.sh` against the recovered instance
   (schema parity, row counts, invalid indexes, Flyway history, frozen constraints, FK joins).

## 4. Acceptance: what must be true before PITR may be claimed

- [ ] WAL archiving enabled and verified (`SELECT archived_count FROM pg_stat_archiver;` increases).
- [ ] A base backup exists and is restorable.
- [ ] A recovery-to-a-chosen-time has been performed in an isolated instance.
- [ ] The six integrity checks pass on the recovered instance.
- [ ] Durations recorded in the rehearsal record (base-backup size, WAL volume, recovery time).
- [ ] This document updated to "configured and tested", with the rehearsal record linked.

Until every box is checked, the release evidence package must continue to record PITR as
**not available**, with logical restore as the only demonstrated recovery path.

## 5. Operational parameters to define before production (§75)

The source does not freeze RPO/RTO numbers and they must not be invented to complete a
checklist. Before production, define and record:

| Parameter | Definition to record | Current value |
|---|---|---|
| RPO | Maximum data-loss window: the logical backup cadence (or WAL retention once PITR is enabled) | backup cadence of `backup-postgres.sh` (operator-defined; not yet scheduled) |
| RTO | Measured restore time for the current backup size | recorded per rehearsal in `docs/testing/p12/rehearsal-*.md` |
| Retention | How long backups and WAL are kept | `BACKUP_RETENTION_DAYS` (default 90) |
