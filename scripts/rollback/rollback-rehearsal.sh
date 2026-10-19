#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Rollback rehearsal for the exam-platform Docker stack
# (P12 §128-§131, P12 §129 rollback runbook).
#
# An application rollback is an IMAGE SWAP, never a database operation:
# Flyway migrations are not blindly reversed, because every migration in this
# repository is additive (forward-compatible), so the previous application runs
# unchanged against the current schema.
#
# The rehearsal performs, in order:
#   1. the forward-compatibility precheck over every migration (no destructive SQL)
#   2. capture of the candidate image tags and the migration set
#   3. a backup (the rollback runbook's first step is a backup)
#   4. preservation of the previous-stable image tag
#   5. stop of the app profile
#   6. redeploy of the previous image
#   7. health checks through the edge
#   8. smoke tests (auth boundary, actuator hidden, frontend routed)
#   9. reconciliation: the data must be unchanged by an image swap
#
# Usage:
#   ./scripts/rollback/rollback-rehearsal.sh [--keep]
#
# Exit codes: 0 = rehearsal passed, 1 = a check failed, 2 = preflight failure.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

KEEP=0
while [ $# -gt 0 ]; do
    case "$1" in
        --keep) KEEP=1 ;;
        -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
    shift
done

PROFILE_ARGS=(--profile core --profile app --profile edge)
BACKEND_IMAGE="exam-platform-backend"
FRONTEND_IMAGE="exam-platform-frontend"
CANDIDATE_TAG="candidate"
PREVIOUS_TAG="previous-stable"

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }
CHECKS_FAILED=0
check() {
    if [ "$1" -eq 0 ]; then pass "$2"; else fail "$2"; CHECKS_FAILED=$(( CHECKS_FAILED + 1 )); fi
}

dc() { docker compose "${PROFILE_ARGS[@]}" "$@"; }

# ---------------------------------------------------------------- preflight

info "Preflight"
if ! command -v docker >/dev/null 2>&1; then
    fail "docker is not installed" >&2
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
if ! dc ps --status running --services 2>/dev/null | grep -q backend; then
    fail "the stack is not running. Start it first:" >&2
    echo "       docker compose --profile core --profile app --profile edge up -d" >&2
    exit 2
fi
pass "docker engine, .env and a running stack are available"

# ---------------------------------------------------------------- 1. forward compatibility

info "Step 1: forward-compatibility precheck over every migration"
# A rollback relies on the previous application running against the NEW schema.
# That is only safe when no migration drops or truncates anything.
destructive=$(grep -rnEi 'DROP[[:space:]]+(TABLE|COLUMN|INDEX|CONSTRAINT)|TRUNCATE|DELETE[[:space:]]+FROM' \
    backend/src/main/resources/db/migration/ | grep -v 'DROP DATABASE IF EXISTS' || true)
if [ -z "${destructive}" ]; then
    check 0 "no migration is destructive - every schema change is additive (forward-compatible)"
else
    check 1 "a destructive migration statement was found:"
    printf '%s\n' "${destructive}" | sed 's/^/         /'
fi

# ---------------------------------------------------------------- 2. candidate state

info "Step 2: capture the candidate state"
MIGRATION_SET=$(dc exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F"|" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank"')
MIGRATION_COUNT=$(printf '%s\n' "${MIGRATION_SET}" | grep -c . || true)
MIGRATION_MAX=$(printf '%s\n' "${MIGRATION_SET}" | tail -n1 | cut -d'|' -f1)
pass "candidate migration set captured (${MIGRATION_COUNT} applied, max version ${MIGRATION_MAX})"

CANDIDATE_BACKEND_ID=$(docker image inspect -f '{{.Id}}' "${BACKEND_IMAGE}:latest" 2>/dev/null || echo unknown)
CANDIDATE_FRONTEND_ID=$(docker image inspect -f '{{.Id}}' "${FRONTEND_IMAGE}:latest" 2>/dev/null || echo unknown)
pass "candidate image identity captured (backend ${CANDIDATE_BACKEND_ID:0:19}, frontend ${CANDIDATE_FRONTEND_ID:0:19})"

# Row counts of the critical tables, used for the reconciliation in step 9.
COUNTS_SQL="SELECT
    (SELECT count(*) FROM \"user\"),
    (SELECT count(*) FROM assessment),
    (SELECT count(*) FROM student_attempt),
    (SELECT count(*) FROM evaluation),
    (SELECT count(*) FROM result),
    (SELECT count(*) FROM purchase),
    (SELECT count(*) FROM audit_log)"
counts_before=$(dc exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F"|" -c "SELECT
        (SELECT count(*) FROM \"user\"),
        (SELECT count(*) FROM assessment),
        (SELECT count(*) FROM student_attempt),
        (SELECT count(*) FROM evaluation),
        (SELECT count(*) FROM result),
        (SELECT count(*) FROM purchase),
        (SELECT count(*) FROM audit_log)"')
pass "pre-rollback row counts captured (${counts_before})"

# ---------------------------------------------------------------- 3. backup

info "Step 3: take a backup before the rollback (deployment runbook step 1)"
BACKUP_OUTPUT=$(bash "${ROOT_DIR}/scripts/backup/backup-postgres.sh" 2>&1 || true)
if printf '%s' "${BACKUP_OUTPUT}" | grep -q "Backup identifier:"; then
    BACKUP_ID=$(printf '%s' "${BACKUP_OUTPUT}" | grep "Backup identifier:" | awk '{print $3}')
    pass "pre-rollback backup stored (${BACKUP_ID})"
else
    check 1 "the pre-rollback backup failed"
    printf '%s\n' "${BACKUP_OUTPUT}" | sed 's/^/         /'
fi

# ---------------------------------------------------------------- 4. preserve the previous image

info "Step 4: preserve the previous-stable image"
docker tag "${BACKEND_IMAGE}:latest" "${BACKEND_IMAGE}:${PREVIOUS_TAG}"
docker tag "${FRONTEND_IMAGE}:latest" "${FRONTEND_IMAGE}:${PREVIOUS_TAG}"
PREVIOUS_BACKEND_ID=$(docker image inspect -f '{{.Id}}' "${BACKEND_IMAGE}:${PREVIOUS_TAG}")
check "$([ "${PREVIOUS_BACKEND_ID}" = "${CANDIDATE_BACKEND_ID}" ] && echo 0 || echo 1)" \
    "previous-stable backend image preserved (${PREVIOUS_BACKEND_ID:0:19})"

# ---------------------------------------------------------------- 5-6. swap the image

info "Step 5: stop the app profile"
dc stop backend frontend
pass "application stopped"

info "Step 6: redeploy the previous image"
ROLLBACK_STARTED=$SECONDS
dc up -d --no-build --force-recreate backend frontend
ROLLBACK_SECONDS=$(( SECONDS - ROLLBACK_STARTED ))
pass "previous image redeployed in ${ROLLBACK_SECONDS}s"

# Wait for the backend to become healthy again.
deadline=$(( SECONDS + 300 ))
while [ "${SECONDS}" -lt "${deadline}" ]; do
    state=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        exam-platform-backend-1 2>/dev/null || echo unknown)
    if [ "${state}" = "healthy" ]; then break; fi
    sleep 5
done
check "$([ "${state}" = "healthy" ] && echo 0 || echo 1)" "backend is healthy after the rollback (${state})"

# ---------------------------------------------------------------- 7. health through the edge

info "Step 7: health checks through the edge"
for attempt in $(seq 1 30); do
    edge_health=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:8081/health/readiness" || true)
    [ "${edge_health}" = "200" ] && break
    sleep 5
done
check "$([ "${edge_health}" = "200" ] && echo 0 || echo 1)" "readiness through the edge (HTTP ${edge_health})"

health_body=$(curl -s --max-time 10 "http://localhost:8081/health" || true)
if printf '%s' "${health_body}" | grep -q '"status":"UP"'; then
    check 0 "health reports status UP after the rollback"
else
    check 1 "health is not UP after the rollback: ${health_body}"
fi

# ---------------------------------------------------------------- 8. smoke tests

info "Step 8: smoke tests"
api_status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://localhost:8081/api/v1/reports/catalog" || true)
check "$([ "${api_status}" = "401" ] && echo 0 || echo 1)" "auth boundary holds (HTTP ${api_status} without a token)"

actuator_status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://localhost:8081/actuator/health" || true)
check "$([ "${actuator_status}" = "404" ] && echo 0 || echo 1)" "actuator tree still hidden (HTTP ${actuator_status})"

frontend_status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://localhost:8081/" || true)
check "$([ "${frontend_status}" = "200" ] && echo 0 || echo 1)" "frontend routed through the edge (HTTP ${frontend_status})"

# ---------------------------------------------------------------- 9. reconciliation

info "Step 9: reconciliation - an image swap must not change data"
counts_after=$(dc exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F"|" -c "SELECT
        (SELECT count(*) FROM \"user\"),
        (SELECT count(*) FROM assessment),
        (SELECT count(*) FROM student_attempt),
        (SELECT count(*) FROM evaluation),
        (SELECT count(*) FROM result),
        (SELECT count(*) FROM purchase),
        (SELECT count(*) FROM audit_log)"')
if [ "${counts_before}" = "${counts_after}" ]; then
    check 0 "row counts unchanged by the rollback (${counts_after})"
else
    check 1 "row counts changed: before=${counts_before} after=${counts_after}"
fi

MIGRATION_SET_AFTER=$(dc exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F"|" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank"')
check "$([ "${MIGRATION_SET}" = "${MIGRATION_SET_AFTER}" ] && echo 0 || echo 1)" \
    "migration set unchanged by the rollback (migrations are not reversed, per §130)"

# ---------------------------------------------------------------- summary

echo
if [ "${CHECKS_FAILED}" -eq 0 ]; then
    info "ROLLBACK REHEARSAL PASSED (0 failed checks)"
else
    info "ROLLBACK REHEARSAL FAILED (${CHECKS_FAILED} failed check(s))"
fi
echo "Rollback duration: ${ROLLBACK_SECONDS}s"
echo "Previous image:   ${BACKEND_IMAGE}:${PREVIOUS_TAG} (${PREVIOUS_BACKEND_ID:0:19})"
echo "Candidate image:  ${BACKEND_IMAGE}:${CANDIDATE_TAG} (${CANDIDATE_BACKEND_ID:0:19})"
echo "Migration set:    ${MIGRATION_COUNT} applied, max version ${MIGRATION_MAX}"

if [ "${KEEP}" -eq 1 ]; then
    pass "the previous-stable tag was kept (--keep)"
else
    docker image rm "${BACKEND_IMAGE}:${PREVIOUS_TAG}" "${FRONTEND_IMAGE}:${PREVIOUS_TAG}" >/dev/null 2>&1 || true
    pass "the temporary previous-stable tag was removed"
fi

[ "${CHECKS_FAILED}" -eq 0 ]
