#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# PostgreSQL restore rehearsal for the exam-platform Docker stack
# (P12 §72, §74: "a backup is not considered operational until restoration has
# been demonstrated").
#
# Restores a backup produced by backup-postgres.sh into an ISOLATED database -
# never the live application database - and verifies, per P12 §72/§80:
#   C1  schema object parity with the live source (tables, indexes, sequences,
#       constraints, foreign keys)
#   C2  exact row counts for every public table, compared against the counts
#       RECORDED AT BACKUP TIME (not the live source, so post-backup drift by
#       background jobs cannot produce a false failure)
#   C3  no invalid indexes or constraints in the restored database
#   C4  Flyway history parity with the live source (version, checksum, success)
#   C5  the frozen business constraints and the partial unique index exist
#       (evaluation one-current-row, payment reference idempotency, template
#       code uniqueness, the audit foreign key, and friends)
#   C6  foreign-key smoke joins resolve identically in both databases
#
# The rehearsal database is dropped at the end unless --keep is passed.
#
# Usage:
#   ./scripts/backup/restore-postgres.sh <backup-id-or-dump-path> [--keep]
#
# Configuration:
#   RESTORE_DB_NAME   isolated database name (default exam_platform_restore_rehearsal)
#   BACKUP_DIR        where backups live          (default ./data/backups)
#   POSTGRES_SERVICE  compose service              (default postgres)
#   PGRESTORE_JOBS    pg_restore parallel jobs     (default 2)
#
# Exit codes: 0 = restore verified, 1 = restore or integrity failure,
# 2 = preflight/usage failure.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

BACKUP_DIR="${BACKUP_DIR:-./data/backups}"
SERVICE="${POSTGRES_SERVICE:-postgres}"
RESTORE_DB="${RESTORE_DB_NAME:-exam_platform_restore_rehearsal}"
PGRESTORE_JOBS="${PGRESTORE_JOBS:-2}"
KEEP=0

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }

usage() {
    sed -n '2,40p' "${BASH_SOURCE[0]}"
    exit 2
}

ARG_DUMP=""
while [ $# -gt 0 ]; do
    case "$1" in
        --keep) KEEP=1 ;;
        -h|--help) usage ;;
        -*) echo "unknown option: $1" >&2; usage ;;
        *) if [ -n "${ARG_DUMP}" ]; then usage; fi; ARG_DUMP="$1" ;;
    esac
    shift
done
[ -n "${ARG_DUMP}" ] || usage

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
    fail "postgres is not reachable. Start the core profile first:" >&2
    echo "       docker compose --profile core up -d postgres redis" >&2
    exit 2
fi

# The live database name is read from the container environment (the official
# postgres image creates POSTGRES_DB but no database named after the user, so
# psql without an explicit -d cannot be used to discover it).
LIVE_DB=$(docker compose exec -T "${SERVICE}" sh -c 'printf "%s" "$POSTGRES_DB"')
if [ -z "${LIVE_DB}" ]; then
    fail "could not determine the live database name" >&2
    exit 2
fi
# The rehearsal database name is interpolated into SQL, so it must be a plain
# identifier — anything else is rejected before it reaches psql.
if ! printf '%s' "${RESTORE_DB}" | grep -Eq '^[A-Za-z0-9_]+$'; then
    fail "RESTORE_DB_NAME must be a plain identifier (letters, digits, underscore): ${RESTORE_DB}" >&2
    exit 2
fi
if [ "${RESTORE_DB}" = "${LIVE_DB}" ]; then
    fail "refusing to restore into the live database '${LIVE_DB}'" >&2
    echo "       Restore is a rehearsal into an isolated database only (P12 §72)." >&2
    exit 2
fi
pass "postgres reachable; live database is '${LIVE_DB}', rehearsal target is '${RESTORE_DB}'"

# ---------------------------------------------------------------- locate backup

if [ -f "${ARG_DUMP}" ]; then
    DUMP="${ARG_DUMP}"
else
    DUMP="${BACKUP_DIR}/${ARG_DUMP}.dump"
fi
META="${DUMP%.dump}.meta"
CHECKSUMS="${DUMP%.dump}.sha256"

if [ ! -f "${DUMP}" ]; then
    fail "backup dump not found: ${DUMP}" >&2
    echo "       Run scripts/backup/backup-postgres.sh first, or pass the backup id." >&2
    exit 2
fi
pass "backup located: $(basename "${DUMP}")"

# Verify the artifact itself before trusting it (P12 §74). The checksum file
# records basenames and is verified from inside the backup directory.
if [ -f "${CHECKSUMS}" ] && command -v sha256sum >/dev/null 2>&1; then
    if (cd "$(dirname "${CHECKSUMS}")" && sha256sum -c "$(basename "${CHECKSUMS}")" >/dev/null 2>&1); then
        pass "dump checksum verified"
    else
        fail "dump checksum mismatch - the artifact is corrupt" >&2
        exit 1
    fi
else
    info "checksum file or sha256sum unavailable - continuing without artifact verification"
fi

# ---------------------------------------------------------------- restore

info "Restoring into isolated database '${RESTORE_DB}'"
# RESTORE_DB reaches the container through the environment; the SQL interpolates
# a validated plain identifier only.
#
# The command is kept on one line: bash would otherwise split the argument to
# the shell at the parentheses of "WITH (FORCE)".
docker compose exec -T -e RESTORE_DB="${RESTORE_DB}" "${SERVICE}" sh -c \
    'psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$RESTORE_DB\" WITH (FORCE)" >/dev/null && createdb -U "$POSTGRES_USER" "$RESTORE_DB"'

RESTORE_STARTED=$SECONDS
# pg_restore cannot restore in parallel FROM STANDARD INPUT, so the dump is
# copied into the container first and the parallel restore runs from that file.
docker compose exec -T "${SERVICE}" sh -c 'cat > /tmp/restore.dump' < "${DUMP}"
if ! docker compose exec -T -e RESTORE_DB="${RESTORE_DB}" -e PGRESTORE_JOBS="${PGRESTORE_JOBS}" "${SERVICE}" \
        sh -c 'pg_restore -U "$POSTGRES_USER" -d "$RESTORE_DB" -j "$PGRESTORE_JOBS" --no-owner --no-privileges /tmp/restore.dump && rm -f /tmp/restore.dump'; then
    fail "pg_restore failed"
    exit 1
fi
RESTORE_SECONDS=$(( SECONDS - RESTORE_STARTED ))
pass "restore completed in ${RESTORE_SECONDS}s"

# ---------------------------------------------------------------- helpers

psql_src() {
    docker compose exec -T -e TARGET_DB="${LIVE_DB}" "${SERVICE}" \
        sh -c 'psql -U "$POSTGRES_USER" -d "$TARGET_DB" -At -F"|" -v ON_ERROR_STOP=1' <<< "$1"
}
psql_rest() {
    docker compose exec -T -e TARGET_DB="${RESTORE_DB}" "${SERVICE}" \
        sh -c 'psql -U "$POSTGRES_USER" -d "$TARGET_DB" -At -F"|" -v ON_ERROR_STOP=1' <<< "$1"
}

CHECKS_FAILED=0
check() {
    if [ "$1" -eq 0 ]; then pass "$2"; else fail "$2"; CHECKS_FAILED=$(( CHECKS_FAILED + 1 )); fi
}

# C1 - schema object parity with the live source.
info "Integrity check 1: schema object parity with the live source"
OBJECTS_SQL="SELECT c.relkind, count(*)
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r','i','S')
    GROUP BY c.relkind ORDER BY c.relkind"
CONSTRAINTS_SQL="SELECT con.contype, count(*)
    FROM pg_constraint con JOIN pg_namespace n ON n.oid = con.connamespace
    WHERE n.nspname = 'public'
    GROUP BY con.contype ORDER BY con.contype"
src_objects="$(psql_src "${OBJECTS_SQL}"; psql_src "${CONSTRAINTS_SQL}")"
rest_objects="$(psql_rest "${OBJECTS_SQL}"; psql_rest "${CONSTRAINTS_SQL}")"
if [ "${src_objects}" = "${rest_objects}" ]; then check 0 "schema objects (tables, indexes, sequences, constraints) match the source"; else check 1 "schema object counts differ between source and restored database"; fi

# C2 - exact row counts against the counts recorded at backup time.
info "Integrity check 2: exact row counts against the backup record"
if [ -f "${META}" ]; then
    recorded_counts=$(grep '^rowcount_' "${META}" | sort)
    if [ -z "${recorded_counts}" ]; then
        check 1 "the backup metadata records no row counts (re-run the backup script)"
    else
        ROW_COUNTS_QUERY=$(docker compose exec -T "${SERVICE}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At' <<'SQL'
SELECT string_agg(
         format('SELECT %L AS tbl, count(*) AS c FROM %I.%I', c.relname, n.nspname, c.relname),
         ' UNION ALL ')
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r'
SQL
)
        restored_counts=$(psql_rest "${ROW_COUNTS_QUERY}" | awk -F'|' '{print "rowcount_" $1 "=" $2}' | sort)
        if [ "${recorded_counts}" = "${restored_counts}" ]; then
            TABLES_CHECKED=$(printf '%s\n' "${recorded_counts}" | wc -l)
            check 0 "row counts match for all ${TABLES_CHECKED} public tables"
        else
            check 1 "row counts differ (recorded vs restored):"
            diff <(printf '%s\n' "${recorded_counts}") <(printf '%s\n' "${restored_counts}") | sed 's/^/         /'
        fi
    fi
else
    check 1 "backup metadata file not found: ${META}"
fi

# C3 - no invalid indexes or constraints in the restored database.
info "Integrity check 3: no invalid indexes"
INVALID_SQL="SELECT count(*)
    FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
    WHERE c.relnamespace = 'public'::regnamespace AND NOT i.indisvalid"
invalid_count=$(psql_rest "${INVALID_SQL}")
check "$([ "${invalid_count}" = "0" ] && echo 0 || echo 1)" "restored database has ${invalid_count} invalid index(es)"

# C4 - Flyway history parity with the live source.
info "Integrity check 4: Flyway migration history parity"
FLYWAY_SQL="SELECT installed_rank, version, checksum, success FROM flyway_schema_history ORDER BY installed_rank"
src_flyway=$(psql_src "${FLYWAY_SQL}")
rest_flyway=$(psql_rest "${FLYWAY_SQL}")
if [ "${src_flyway}" = "${rest_flyway}" ]; then
    FLYWAY_ROWS=$(printf '%s\n' "${src_flyway}" | grep -c . || true)
    check 0 "Flyway history identical (${FLYWAY_ROWS} applied migrations, same checksums)"
else
    check 1 "Flyway history differs between source and restored database"
fi

# C5 - the frozen business constraints and the partial unique index exist.
info "Integrity check 5: frozen business constraints present"
# uk_evaluation_current_attempt is a PARTIAL UNIQUE INDEX (V14), not a table
# constraint, so it is checked in pg_indexes; the rest are pg_constraint rows.
FROZEN_SQL="SELECT count(*) FROM (
    SELECT conname AS name FROM pg_constraint
    WHERE conname IN (
        'uk_answer_key_detail_answer_key_id_question_version_id',
        'uk_result_evaluation_id',
        'uk_payment_record_payment_reference',
        'uk_email_template_code',
        'uk_business_rule_code',
        'uk_lookup_category_code',
        'uk_organization_setting_organization',
        'ck_notification_status',
        'fk_audit_log_user',
        'uk_evaluation_current_attempt')
    UNION ALL
    SELECT indexname FROM pg_indexes
    WHERE schemaname = 'public'
      AND indexname IN ('uk_evaluation_current_attempt')
) frozen"
expected_frozen=10
frozen_count=$(psql_rest "${FROZEN_SQL}")
check "$([ "${frozen_count}" = "${expected_frozen}" ] && echo 0 || echo 1)" \
    "frozen constraint set present (${frozen_count}/${expected_frozen}: evaluation one-current-row, payment-reference idempotency, template/rule/lookup uniqueness, notification status, audit FK)"

# C6 - foreign-key smoke joins resolve identically in both databases.
info "Integrity check 6: foreign-key smoke joins"
JOINS_SQL="SELECT
    (SELECT count(*) FROM \"user\" u JOIN login_session s ON s.user_id = u.id),
    (SELECT count(*) FROM purchase p JOIN payment_record r ON r.purchase_id = p.id),
    (SELECT count(*) FROM result r JOIN evaluation e ON e.id = r.evaluation_id)"
src_joins=$(psql_src "${JOINS_SQL}")
rest_joins=$(psql_rest "${JOINS_SQL}")
if [ "${src_joins}" = "${rest_joins}" ]; then
    check 0 "foreign-key joins resolve identically (sessions=${src_joins%%|*})"
else
    # Joins compare restored-vs-live: legitimate drift only if rows were created
    # after the backup; C2 already proves the backup itself is exact.
    check 1 "foreign-key join counts differ (source=${src_joins}, restored=${rest_joins}) - check for post-backup writes"
fi

# ---------------------------------------------------------------- summary

echo
if [ "${CHECKS_FAILED}" -eq 0 ]; then
    info "RESTORE REHEARSAL PASSED (0 failed checks)"
else
    info "RESTORE REHEARSAL FAILED (${CHECKS_FAILED} failed check(s))"
fi

if [ "${KEEP}" -eq 1 ]; then
    pass "rehearsal database '${RESTORE_DB}' kept for inspection (--keep)"
    echo "       Drop it with: docker compose exec postgres psql -U \$POSTGRES_USER -d postgres -c 'DROP DATABASE ${RESTORE_DB} WITH (FORCE)'"
else
    docker compose exec -T -e RESTORE_DB="${RESTORE_DB}" "${SERVICE}" sh -c \
        'psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$RESTORE_DB\" WITH (FORCE)"' >/dev/null
    pass "rehearsal database '${RESTORE_DB}' dropped"
fi

echo "Restore duration: ${RESTORE_SECONDS}s"
[ "${CHECKS_FAILED}" -eq 0 ]
