#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Capture one ramp step's database/PgBouncer detail that the exporters do not
# publish as gauges, plus the §27 snapshot baseline the ramp runner asserts on.
#
# Usage: snapshot-step.sh <label> <output-dir>
# Writes <output-dir>/<label>.snapshot.json and prints it.
# ---------------------------------------------------------------------------
set -u

LABEL="${1:?usage: snapshot-step.sh <label> <outdir>}"
OUTDIR="${2:?usage: snapshot-step.sh <label> <outdir>}"
PG="${P7_PSQL_CONTAINER:-exam-platform-prodlike-postgres-1}"
USER="${P7_PSQL_USER:-exam_app}"
DB="${P7_PSQL_DB:-exam_platform}"

mkdir -p "$OUTDIR"

row() {
    docker exec "$PG" psql -U "$USER" -d "$DB" -t -A -F'|' -c "$1" 2>/dev/null | tr -d '\r' | head -1
}

# §27 baseline: these two counts must not change across a start burst.
AQ="$(row 'SELECT count(*) FROM attempt_question;')"
AQO="$(row 'SELECT count(*) FROM attempt_question_option;')"
READY="$(row "SELECT count(*) FROM student_attempt WHERE snapshot_status='READY';")"
FAILED="$(row "SELECT count(*) FROM student_attempt WHERE snapshot_status='FAILED';")"
PENDING="$(row "SELECT count(*) FROM student_attempt WHERE snapshot_status='PENDING';")"
STARTED="$(row "SELECT count(*) FROM student_attempt WHERE started_on IS NOT NULL;")"
SUBMITTED="$(row "SELECT count(*) FROM result;")"
EVALUATED="$(row "SELECT count(*) FROM student_response;")"

# Database detail the exporters do not expose as gauges.
LOCKS="$(row "SELECT count(*) FROM pg_locks l JOIN pg_database d ON d.oid=l.database WHERE d.datname='$DB';")"
LOCKWAIT="$(row "SELECT count(*) FROM pg_stat_activity WHERE datname='$DB' AND wait_event_type='Lock';")"
CONNS="$(row "SELECT count(*) FROM pg_stat_activity WHERE datname='$DB';")"
ACTIVE="$(row "SELECT count(*) FROM pg_stat_activity WHERE datname='$DB' AND state='active';")"
WAL="$(row "SELECT coalesce(sum(size),0) FROM pg_ls_waldir();")"
DEADLOCKS="$(row "SELECT coalesce(sum(deadlocks),0) FROM pg_stat_database WHERE datname='$DB';")"
DB_SIZE="$(row "SELECT pg_database_size('$DB');")"

printf '{"label":"%s","attempt_question":%s,"attempt_question_option":%s,"ready":%s,"pending":%s,"failed":%s,"started":%s,"submitted":%s,"evaluated":%s,"pg_locks":%s,"pg_lock_waiters":%s,"pg_connections":%s,"pg_active":%s,"pg_wal_bytes":%s,"pg_deadlocks":%s,"db_size_bytes":%s}\n' \
    "$LABEL" "$AQ" "$AQO" "$READY" "$PENDING" "$FAILED" "$STARTED" "$SUBMITTED" "$EVALUATED" \
    "$LOCKS" "$LOCKWAIT" "$CONNS" "$ACTIVE" "$WAL" "$DEADLOCKS" "$DB_SIZE" \
    | tee "$OUTDIR/$LABEL.snapshot.json"
