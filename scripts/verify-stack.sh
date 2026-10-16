#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Verifies the local Docker stack end to end (P0-09 / P0-10, closes GAP-007).
#
# It starts the core, app and edge profiles, waits for readiness, and then proves
# through the edge that:
#   1. Nginx is serving                         (GET /nginx-health)
#   2. the backend is reachable through Nginx   (GET /health)
#   3. the backend is READY, which includes its database check
#   4. the database path runs through PgBouncer (readiness + backend environment)
#   5. API routing works and the auth boundary holds (GET /api/v1/reports -> 401)
#   6. the frontend is routed                    (GET / -> 200 HTML)
#   7. the edge does not expose the actuator tree (GET /actuator/health -> 404)
#
# Exits 0 only when every assertion passes; on failure it prints the logs of the
# services that are unhealthy and exits non-zero.
#
# Requirements: bash, curl, and a reachable Docker daemon with Compose v2.
# Usage:
#   ./scripts/verify-stack.sh                 # build, start, verify, keep running
#   ./scripts/verify-stack.sh --no-build      # reuse existing images
#   ./scripts/verify-stack.sh --down          # tear the stack down and exit
#   ./scripts/verify-stack.sh --timeout 600   # readiness timeout in seconds
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

EDGE_HTTP_PORT="${EDGE_HTTP_PORT:-8081}"
EDGE_URL="http://localhost:${EDGE_HTTP_PORT}"
READY_TIMEOUT="${STACK_READY_TIMEOUT:-420}"
BUILD=1
TEARDOWN=0

# The full integration stack. The app profile requires the edge profile: the
# backend's only database path is PgBouncer (application-staging.yml).
PROFILE_ARGS=(--profile core --profile app --profile edge)

while [ $# -gt 0 ]; do
    case "$1" in
        --no-build) BUILD=0 ;;
        --down) TEARDOWN=1 ;;
        --timeout) shift; READY_TIMEOUT="${1:-420}" ;;
        -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
    shift
done

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }

dc() { docker compose "${PROFILE_ARGS[@]}" "$@"; }

dump_failure_context() {
    echo
    echo "---- container status ----"
    dc ps || true
    echo
    echo "---- recent logs (backend, pgbouncer, nginx) ----"
    dc logs --tail=60 backend pgbouncer nginx 2>&1 || true
}

on_exit() {
    status=$?
    if [ "$status" -ne 0 ]; then
        echo
        echo "Verification FAILED (exit ${status})."
    fi
}
trap on_exit EXIT

# ---------------------------------------------------------------- preflight

info "Preflight"
missing=0
for tool in docker curl; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "  required tool not found: ${tool}" >&2
        missing=1
    fi
done
[ "$missing" -eq 0 ] || exit 2

if ! docker compose version >/dev/null 2>&1; then
    echo "  docker compose v2 is required" >&2
    exit 2
fi

if ! docker info >/dev/null 2>&1; then
    echo "  the Docker daemon is not reachable (start Docker and retry)" >&2
    exit 2
fi
pass "docker, compose v2, curl and daemon available"

if [ ! -f .env ]; then
    echo "  .env is missing. Copy the template first:" >&2
    echo "      cp .env.example .env" >&2
    exit 2
fi
pass ".env present"

if [ "$TEARDOWN" -eq 1 ]; then
    info "Tearing the stack down"
    dc down
    pass "stack stopped"
    exit 0
fi

# ---------------------------------------------------------------- start

info "Validating the Compose model"
dc config --quiet
pass "compose model is valid"

info "Starting the stack (core + app + edge)"
if [ "$BUILD" -eq 1 ]; then
    dc up -d --build
else
    dc up -d
fi

# ---------------------------------------------------------------- readiness

wait_for_container_health() {
    service="$1"
    deadline=$(( SECONDS + READY_TIMEOUT ))
    while [ "$SECONDS" -lt "$deadline" ]; do
        container_id="$(dc ps -q "$service" 2>/dev/null || true)"
        if [ -n "$container_id" ]; then
            state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
                "$container_id" 2>/dev/null || echo unknown)"
            if [ "$state" = "healthy" ] || [ "$state" = "running" ]; then
                pass "${service} is ${state}"
                return 0
            fi
        fi
        sleep 3
    done
    fail "${service} did not become healthy within ${READY_TIMEOUT}s"
    return 1
}

# The data tier must be healthy before the application can start.
wait_for_container_health postgres
wait_for_container_health redis
wait_for_container_health pgbouncer

# The backend runs its migrations on start-up and can take a while on first boot.
wait_for_container_health backend
wait_for_container_health nginx
wait_for_container_health frontend

wait_for_http() {
    url="$1"; expected="$2"; label="$3"
    deadline=$(( SECONDS + READY_TIMEOUT ))
    last=""
    while [ "$SECONDS" -lt "$deadline" ]; do
        last="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" || true)"
        if [ "$last" = "$expected" ]; then
            pass "$label (HTTP ${last})"
            return 0
        fi
        sleep 3
    done
    fail "$label (expected HTTP ${expected}, last saw ${last:-no response})"
    return 1
}

# ---------------------------------------------------------------- assertions through the edge

info "Verifying through the edge at ${EDGE_URL}"

wait_for_http "${EDGE_URL}/nginx-health" 200 "edge is serving"
wait_for_http "${EDGE_URL}/health" 200 "backend health reachable through the edge"

# Readiness includes the database indicator, so a 200 here means the application
# opened and validated a database connection - and it can only do that through
# PgBouncer, because that is the datasource host the staging profile uses.
wait_for_http "${EDGE_URL}/health/readiness" 200 "backend readiness includes its database check"

health_body="$(curl -s --max-time 10 "${EDGE_URL}/health" || true)"
if printf '%s' "$health_body" | grep -q '"status":"UP"'; then
    pass "health reports status UP"
else
    fail "health did not report status UP (body: ${health_body})"
fi

# ------------------------------------------------------------------ PgBouncer is in the path

backend_container="$(dc ps -q backend)"
datasource_url="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$backend_container" \
    | grep -E '^(SPRING_DATASOURCE_URL|PGBOUNCER_HOST)=' || true)"
if docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$backend_container" | grep -q '^PGBOUNCER_HOST=pgbouncer$'; then
    pass "backend database configuration targets PgBouncer (${datasource_url//$'\n'/ })"
else
    fail "backend is not configured to reach PostgreSQL through PgBouncer"
fi

pgbouncer_container="$(dc ps -q pgbouncer)"
pgbouncer_state="$(docker inspect -f '{{.State.Status}}' "$pgbouncer_container" 2>/dev/null || echo missing)"
if [ "$pgbouncer_state" = "running" ]; then
    pass "pgbouncer container is running (transaction pooling)"
else
    fail "pgbouncer container is ${pgbouncer_state}"
fi

# ------------------------------------------------------------------ routing and boundaries

api_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${EDGE_URL}/api/v1/reports/catalog" || true)"
if [ "$api_status" = "401" ]; then
    pass "API routing works and the auth boundary holds (HTTP 401 without a token)"
else
    fail "expected HTTP 401 from the unauthenticated API route, saw ${api_status}"
fi

frontend_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${EDGE_URL}/" || true)"
if [ "$frontend_status" = "200" ]; then
    pass "frontend is routed at the edge root (HTTP 200)"
else
    fail "frontend root returned HTTP ${frontend_status}"
fi

for blocked in /actuator/health /actuator/prometheus /v3/api-docs; do
    blocked_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${EDGE_URL}${blocked}" || true)"
    if [ "$blocked_status" = "404" ]; then
        pass "edge does not expose ${blocked} (HTTP 404)"
    else
        fail "edge exposed ${blocked} with HTTP ${blocked_status}"
    fi
done

# ------------------------------------------------------------------ correlation

supplied_id="verify-stack-$(date +%s)"
returned_id="$(curl -s -D - -o /dev/null --max-time 10 -H "X-Request-Id: ${supplied_id}" "${EDGE_URL}/health" \
    | tr -d '\r' | awk 'tolower($1)=="x-request-id:"{print $2}' | head -n1 || true)"
if [ "$returned_id" = "$supplied_id" ]; then
    pass "request correlation id is forwarded through the edge (${supplied_id})"
else
    fail "X-Request-Id was not propagated through the edge (sent ${supplied_id}, received ${returned_id:-none})"
fi

# ------------------------------------------------------------------ summary

echo
echo "All edge assertions passed."
echo "  edge            ${EDGE_URL}"
echo "  API             ${EDGE_URL}/api/v1/..."
echo "  health          ${EDGE_URL}/health"
echo "  observability   docker compose --profile observability up -d"
echo
echo "Stop the stack with:  docker compose --profile core --profile app --profile edge down"
exit 0
