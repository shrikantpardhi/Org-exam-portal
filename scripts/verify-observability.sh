#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Verifies the observability stack (P0-11, P12 §106-§114).
#
# It starts the app (so Prometheus has a scraping target) plus observability,
# waits for the three tools to become reachable, and then asserts *from the
# tools' own APIs*:
#   1. Prometheus is ready and reports the backend target as UP,
#   2. the alert rules are loaded and evaluable,
#   3. Grafana is healthy and has provisioned datasources and dashboards,
#   4. Loki is ready and has at least one ingested log series so the Loki
#      datasource is not a dangling configuration.
#
# Exits 0 only when every assertion passes.
#
# Requirements: bash, curl, and a reachable Docker daemon with Compose v2.
# Usage:
#   ./scripts/verify-observability.sh                 # start app + observability, verify
#   ./scripts/verify-observability.sh --down          # tear the stack down
#   ./scripts/verify-observability.sh --timeout 300   # readiness timeout in seconds
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

PROM_HOST_PORT="${PROMETHEUS_HOST_PORT:-9090}"
GRAFANA_HOST_PORT="${GRAFANA_HOST_PORT:-3001}"
LOKI_HOST_PORT="${LOKI_HOST_PORT:-3100}"
PROM_URL="http://localhost:${PROM_HOST_PORT}"
GRAFANA_URL="http://localhost:${GRAFANA_HOST_PORT}"
LOKI_URL="http://localhost:${LOKI_HOST_PORT}"
GRAFANA_USER="${GRAFANA_ADMIN_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-}"
READY_TIMEOUT="${OBSERVABILITY_READY_TIMEOUT:-360}"
TEARDOWN=0

# The Grafana credential is defined in .env for the stack; the script reads it
# from the same file so an operator does not have to export it separately.
if [ -z "${GRAFANA_PASSWORD}" ] && [ -f .env ]; then
    GRAFANA_PASSWORD=$(grep -E '^GRAFANA_ADMIN_PASSWORD=' .env | head -n1 | cut -d= -f2-)
fi
if [ -z "${GRAFANA_USER}" ] || [ "${GRAFANA_USER}" = "admin" ] && [ -f .env ]; then
    GRAFANA_USER=$(grep -E '^GRAFANA_ADMIN_USER=' .env | head -n1 | cut -d= -f2- || true)
    GRAFANA_USER="${GRAFANA_USER:-admin}"
fi

APP_ARGS=(--profile core --profile app --profile edge)
OBS_ARGS=(--profile observability)

while [ $# -gt 0 ]; do
    case "$1" in
        --down) TEARDOWN=1 ;;
        --timeout) shift; READY_TIMEOUT="${1:-360}" ;;
        -h|--help) sed -n '2,32p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
    shift
done

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }

all_stack_args=(--profile core --profile app --profile edge --profile observability)

dc() { docker compose "${all_stack_args[@]}" "$@"; }

on_exit() {
    status=$?
    if [ "$status" -ne 0 ]; then
        echo
        echo "Observability verification FAILED (exit ${status})."
        dc logs --tail=40 prometheus grafana loki 2>&1 || true
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
if [ ! -f .env ]; then
    echo "  .env is missing. Copy the template first: cp .env.example .env" >&2
    exit 2
fi
if [ -z "$GRAFANA_PASSWORD" ]; then
    echo "  GRAFANA_ADMIN_PASSWORD is required to query the Grafana API" >&2
    exit 2
fi
pass "docker, compose v2, curl, .env and Grafana credentials present"

if [ "$TEARDOWN" -eq 1 ]; then
    info "Tearing the stack down"
    docker compose --profile core --profile app --profile edge --profile observability down
    pass "stack stopped"
    exit 0
fi

# ---------------------------------------------------------------- start

info "Validating the Compose model"
docker compose "${all_stack_args[@]}" config --quiet
pass "compose model is valid"

info "Starting the application and the observability profile"
dc up -d --build

wait_healthy() {
    service="$1"
    deadline=$(( SECONDS + READY_TIMEOUT ))
    while [ "$SECONDS" -lt "$deadline" ]; do
        container_id="$(docker compose "${all_stack_args[@]}" ps -q "$service" 2>/dev/null || true)"
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

deadline=$(( SECONDS + READY_TIMEOUT ))
wait_for_http() {
    url="$1"; expected="$2"; label="$3"
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

wait_healthy backend
wait_healthy prometheus
wait_healthy grafana
wait_healthy loki
wait_healthy promtail

info "Verifying ${PROM_URL}, ${GRAFANA_URL}, ${LOKI_URL}"

# ---------------------------------------------------------------- Prometheus

wait_for_http "${PROM_URL}/-/ready" 200 "Prometheus is ready"

targets_json="$(curl -s --max-time 10 "${PROM_URL}/api/v1/targets" || true)"
if printf '%s' "$targets_json" | grep -q '"scrapePool":"exam-platform-backend".*"health":"up"'; then
    pass "Prometheus reports the backend target as UP"
else
    fail "the exam-platform-backend target is not UP in Prometheus"
fi

rules_json="$(curl -s --max-time 10 "${PROM_URL}/api/v1/rules?type=alert" || true)"
for rule in ApiErrorRateHigh ApiP95LatencyHigh SubmissionFailureSpike SnapshotBacklogHigh \
            ResponseSaveFailureSpike BackendDown DatabaseConnectionSaturation RedisUnavailable \
            PgBouncerSaturation; do
    if printf '%s' "$rules_json" | grep -q "\"name\":\"${rule}\""; then
        pass "alert rule loaded: ${rule}"
    else
        fail "alert rule not loaded: ${rule}"
    fi
done

# ---------------------------------------------------------------- Grafana

wait_for_http "${GRAFANA_URL}/api/health" 200 "Grafana is healthy"

datasources="$(curl -s -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" --max-time 10 \
    "${GRAFANA_URL}/api/datasources" || true)"
for datasource in exam-prometheus exam-loki; do
    if printf '%s' "$datasources" | grep -q "\"uid\":\"${datasource}\""; then
        pass "datasource provisioned: ${datasource}"
    else
        fail "datasource not provisioned: ${datasource}"
    fi
done

dashboards="$(curl -s -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" --max-time 10 \
    "${GRAFANA_URL}/api/search?query=" || true)"
for dashboard in exam-system-health exam-assessment-delivery exam-worker-backlog; do
    if printf '%s' "$dashboards" | grep -q "\"uid\":\"${dashboard}\""; then
        pass "dashboard provisioned: ${dashboard}"
    else
        fail "dashboard not provisioned: ${dashboard}"
    fi
done

alerts="$(curl -s -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" --max-time 10 \
    "${GRAFANA_URL}/api/dashboards/uid/exam-system-health" || true)"
if printf '%s' "$alerts" | grep -q '"title":"System Health"'; then
    pass "system-health dashboard is readable through the Grafana API"
else
    fail "system-health dashboard is not readable through the Grafana API"
fi

# ---------------------------------------------------------------- Loki

wait_for_http "${LOKI_URL}/ready" 200 "Loki is ready"

# Ready alone does not prove ingestion; a Loki with no series would still be ready.
sleep 20
series_json="$(curl -s --max-time 10 --get \
    --data-urlencode 'match[]={service=~".+"}' \
    "${LOKI_URL}/loki/api/v1/labels" || true)"
if printf '%s' "$series_json" | grep -q 'service'; then
    pass "Loki is ingesting labeled container logs"
else
    fail "Loki has no ingested labeled series yet (check promtail and the Docker socket)"
fi

# ---------------------------------------------------------------- summary

echo
echo "Observability verification passed."
echo "  Prometheus    ${PROM_URL}"
echo "  Grafana       ${GRAFANA_URL}"
echo "  Loki          ${LOKI_URL}"
echo
echo "Stop the stack with:"
echo "  docker compose --profile core --profile app --profile edge --profile observability down"
exit 0
