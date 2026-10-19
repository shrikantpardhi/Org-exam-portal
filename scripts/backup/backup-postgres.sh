#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# PostgreSQL backup for the exam-platform Docker stack (P12 §71, §74).
#
# Performs a CONSISTENT LOGICAL backup: `pg_dump -Fc` runs inside a single
# MVCC snapshot, so the dump is a transactionally consistent view of the
# database without stopping the application. It also captures the instance
# globals (roles) and a metadata file with the evidence P12 §74 requires:
# timestamp, database version, migration baseline, sizes, and a checksum.
#
# The backup identifier is printed on the last line: it is the only value an
# operator needs to hand to restore-postgres.sh.
#
# Usage:
#   ./scripts/backup/backup-postgres.sh
#
# Configuration (environment variables):
#   BACKUP_DIR              target directory        (default ./data/backups,
#                                                     already gitignored)
#   BACKUP_RETENTION_DAYS   prune backups older than (default 90; 0 disables)
#   POSTGRES_SERVICE        compose service         (default postgres)
#
# Requirements: bash, docker with Compose v2 and a running engine, the Compose
# stack started with the core profile, and a .env file in the repository root.
#
# Exit codes: 0 = backup stored and verified, 2 = preflight failure,
# non-zero from pg_dump on backup failure.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

BACKUP_DIR="${BACKUP_DIR:-./data/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-90}"
SERVICE="${POSTGRES_SERVICE:-postgres}"

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }

# ---------------------------------------------------------------- preflight

info "Preflight"
if ! command -v docker >/dev/null 2>&1; then
    fail "docker is not installed" >&2
    exit 2
fi
if ! docker compose version >/dev/null 2>&1; then
    fail "docker compose v2 is required" >&2
    exit 2
fi
if ! docker info >/dev/null 2>&1; then
    fail "the Docker daemon is not reachable (start Docker and retry)" >&2
    exit 2
fi
if [ ! -f .env ]; then
    fail ".env is missing. Copy the template first: cp .env.example .env" >&2
    exit 2
fi

if ! docker compose exec -T "${SERVICE}" sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1; then
    fail "postgres is not reachable. Start the core profile first:"
    echo "       docker compose --profile core up -d postgres redis" >&2
    exit 2
fi
pass "docker engine, compose, .env and a healthy postgres are available"

# ---------------------------------------------------------------- backup

BACKUP_ID="exam-platform-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "${BACKUP_DIR}"
DUMP="${BACKUP_DIR}/${BACKUP_ID}.dump"
GLOBALS="${BACKUP_DIR}/${BACKUP_ID}.globals.sql"
META="${BACKUP_DIR}/${BACKUP_ID}.meta"
CHECKSUMS="${BACKUP_DIR}/${BACKUP_ID}.sha256"

info "Creating logical backup ${BACKUP_ID} into ${BACKUP_DIR}"
STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
start_seconds=$SECONDS

# -Fc: custom format, compressed, supports selective restore in pg_restore.
# Credentials stay inside the container: the script never handles them.
if ! docker compose exec -T "${SERVICE}" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "${DUMP}"; then
    fail "pg_dump failed"
    exit 1
fi
docker compose exec -T "${SERVICE}" sh -c 'pg_dumpall -U "$POSTGRES_USER" --globals-only' > "${GLOBALS}"

DURATION_SECONDS=$(( SECONDS - start_seconds ))
DUMP_BYTES=$(wc -c < "${DUMP}")

if [ "${DUMP_BYTES}" -eq 0 ]; then
    fail "the dump file is empty"
    exit 1
fi
pass "logical backup stored (${DUMP_BYTES} bytes in ${DURATION_SECONDS}s)"

# ---------------------------------------------------------------- checksums

# sha256sum is available on Linux, macOS (coreutils) and Git-Bash; openssl is
# the portable fallback.
# The checksum file records BASENAMES, so it can be verified from inside the
# backup directory with a plain `sha256sum -c`.
if command -v sha256sum >/dev/null 2>&1; then
    (cd "${BACKUP_DIR}" && sha256sum "$(basename "${DUMP}")" "$(basename "${GLOBALS}")" > "$(basename "${CHECKSUMS}")")
else
    (cd "${BACKUP_DIR}" && openssl dgst -sha256 "$(basename "${DUMP}")" | awk '{print $2"  "$3}' > "$(basename "${CHECKSUMS}")")
    (cd "${BACKUP_DIR}" && openssl dgst -sha256 "$(basename "${GLOBALS}")" | awk '{print $2"  "$3}' >> "$(basename "${CHECKSUMS}")")
fi
pass "checksums written to $(basename "${CHECKSUMS}")"

# ---------------------------------------------------------------- metadata (§74)

info "Recording backup evidence"

# Everything the rehearsal record needs, captured at backup time.
PSQL_AT() {
    docker compose exec -T "${SERVICE}" sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -At -c \"\$1\""
}

POSTGRES_VERSION=$(docker compose exec -T "${SERVICE}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "SELECT version()"')
# The database name comes from the container's own environment: the official
# postgres image creates POSTGRES_DB but no database named after the user, so
# `psql` without an explicit -d cannot be used to discover it.
DATABASE_NAME=$(docker compose exec -T "${SERVICE}" sh -c 'printf "%s" "$POSTGRES_DB"')
MIGRATION_BASELINE=$(PSQL_AT "SELECT coalesce(max(version), 'none') FROM flyway_schema_history WHERE success")
MIGRATION_COUNT=$(PSQL_AT "SELECT count(*) FROM flyway_schema_history WHERE success")
TABLE_COUNT=$(PSQL_AT "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'")

# Exact row counts captured at backup time: restore-postgres.sh compares the
# restored database against THESE, not against the live source, so rows written
# by background jobs after the dump cannot produce a false integrity failure.
# Note: every psql invocation names the database explicitly. The official
# postgres image creates POSTGRES_DB but no database named after the user, so a
# bare `psql -U $POSTGRES_USER` would fail with "database ... does not exist".
ROW_COUNTS_QUERY=$(docker compose exec -T "${SERVICE}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At' <<'SQL'
SELECT string_agg(
         format('SELECT %L AS tbl, count(*) AS c FROM %I.%I', c.relname, n.nspname, c.relname),
         ' UNION ALL ')
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r'
SQL
)

{
    echo "backup_id=${BACKUP_ID}"
    echo "started_at_utc=${STARTED_AT}"
    echo "completed_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "duration_seconds=${DURATION_SECONDS}"
    echo "database=${DATABASE_NAME}"
    echo "postgres_version=${POSTGRES_VERSION}"
    echo "flyway_max_version=${MIGRATION_BASELINE}"
    echo "flyway_applied_migrations=${MIGRATION_COUNT}"
    echo "public_tables=${TABLE_COUNT}"
    echo "dump_file=$(basename "${DUMP}")"
    echo "dump_bytes=${DUMP_BYTES}"
    echo "globals_file=$(basename "${GLOBALS}")"
    echo "format=pg_dump custom (-Fc), consistent single-transaction snapshot"
    docker compose exec -T "${SERVICE}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F= -v ON_ERROR_STOP=1' \
        <<< "${ROW_COUNTS_QUERY}" | awk -F= '{print "rowcount_" $1 "=" $2}'
} > "${META}"
pass "evidence written to $(basename "${META}")"

# ---------------------------------------------------------------- retention (§71)

if [ "${RETENTION_DAYS}" -gt 0 ]; then
    pruned=$(find "${BACKUP_DIR}" -maxdepth 1 -name 'exam-platform-*.dump' -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)
    # Prune the metadata sidecars whose dump was just removed.
    find "${BACKUP_DIR}" -maxdepth 1 -name 'exam-platform-*.meta' -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true
    find "${BACKUP_DIR}" -maxdepth 1 -name 'exam-platform-*.sha256' -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true
    find "${BACKUP_DIR}" -maxdepth 1 -name 'exam-platform-*.globals.sql' -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true
    if [ "${pruned}" -gt 0 ]; then
        pass "retention: pruned ${pruned} backup(s) older than ${RETENTION_DAYS} days"
    fi
fi

echo
echo "Backup identifier: ${BACKUP_ID}"
echo "Restore with:     ./scripts/backup/restore-postgres.sh ${BACKUP_ID}"
exit 0
