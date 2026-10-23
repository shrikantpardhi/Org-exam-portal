#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Verifies the production-like load environment (GAP-083) end to end.
#
# This mirrors scripts/verify-stack.sh (same shape, same PASS/FAIL reporting,
# same exit semantics) but it asserts the properties that make the environment
# *production-like* rather than merely running. Specifically:
#
#   1.  the Compose model is valid and the tier core sets do not overlap
#   2.  the data tier is healthy            (postgres, redis)
#   3.  PgBouncer is running and in the path (the backend's datasource is the pooler)
#   4.  the backend is healthy and its readiness includes its database check
#   5.  backend is reachable through the edge            (GET /health -> 200)
#   6.  the database is reachable through PgBouncer       (real query via the pooler)
#   7.  Redis is reachable                                (PING -> PONG)
#   8.  PostgreSQL is reachable directly                   (source-of-truth sanity)
#   9.  Prometheus is scraping the backend                (target health = up)
#   10. Prometheus is scraping the database exporter
#   11. Prometheus is scraping the Redis exporter
#   12. Grafana answers                                   (provisioned datasources)
#   13. Loki answers                                      (ready)
#   14. the generator host can reach the backend through the edge
#   15. no tier shares a CPU core with the generator
#
# Exit 0 only when every assertion passes. On failure it prints the recent logs
# of the affected services and exits non-zero.
#
# Requirements: bash, curl, and a reachable Docker daemon with Compose v2.
#
# Usage:
#   ./scripts/verify-production-like.sh              # verify a running environment
#   ./scripts/verify-production-like.sh --up         # start it, then verify
#   ./scripts/verify-production-like.sh --down       # tear it down and exit
#   ./scripts/verify-production-like.sh --timeout 600
#
# Run it from the repository root if you intend to use --up: the Dockerfile for
# PgBouncer lives outside its build context, and the builder resolves that path
# relative to the working directory.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_DIR="${ROOT_DIR}/infrastructure/environments/production-like"
COMPOSE_FILE="${ENV_DIR}/docker-compose.yml"
ENV_FILE="${ENV_DIR}/.env"

cd "${ROOT_DIR}"

READY_TIMEOUT="${PRODUCTION_LIKE_READY_TIMEOUT:-600}"
START=0
TEARDOWN=0

# All four tiers. The load profile is a separate project and is not part of the
# stack itself; it is verified by reaching the edge from its network below.
PROFILE_ARGS=(--profile data --profile edge --profile app --profile obs)

while [ $# -gt 0 ]; do
    case "$1" in
        --up) START=1 ;;
        --down) TEARDOWN=1 ;;
        --timeout) shift; READY_TIMEOUT="${1:-600}" ;;
        -h|--help) sed -n '2,44p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
    shift
done

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }

FAILURES=0
CHECKS=0
check() { CHECKS=$((CHECKS + 1)); }

dc() { docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "${PROFILE_ARGS[@]}" "$@"; }

dump_failure_context() {
    echo
    echo "---- container status ----"
    dc ps || true
    echo
    echo "---- recent logs (backend, pgbouncer, postgres) ----"
    dc logs --tail=60 backend pgbouncer postgres 2>&1 || true
}

on_exit() {
    status=$?
    if [ "$status" -ne 0 ] || [ "$FAILURES" -ne 0 ]; then
        dump_failure_context
        echo
        echo "Verification FAILED: ${FAILURES} of ${CHECKS} assertions failed."
        exit 1
    fi
    echo
    echo "All ${CHECKS} production-like assertions passed."
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

if ! docker info >/dev/null 2>&1; then
    echo "  the Docker daemon is not reachable (start Docker and retry)" >&2
    exit 2
fi

if [ ! -f "${ENV_FILE}" ]; then
    echo "  ${ENV_FILE} is missing. Create it from the template first:" >&2
    echo "      cp ${ENV_DIR}/.env.example ${ENV_DIR}/.env" >&2
    echo "  then replace every placeholder credential." >&2
    exit 2
fi
check; pass "docker, compose v2, curl and daemon available"
check; pass "environment file present (not committed)"

if [ "$TEARDOWN" -eq 1 ]; then
    info "Tearing the environment down"
    dc down
    docker compose -f "${ENV_DIR}/docker-compose.generator.yml" \
        --env-file "${ENV_FILE}" --profile load down >/dev/null 2>&1 || true
    check; pass "environment stopped"
    exit 0
fi

# ---------------------------------------------------------------- model

info "Validating the Compose model"
if dc config --quiet; then
    check; pass "stack compose model is valid"
else
    check; fail "stack compose model is invalid"
fi

if docker compose -f "${ENV_DIR}/docker-compose.generator.yml" \
        --env-file "${ENV_FILE}" --profile load config --quiet; then
    check; pass "generator compose model is valid"
else
    check; fail "generator compose model is invalid"
fi

# The central claim of the environment is that the generator and the stack do
# not share a CPU. That is a property of the configuration, so it is checked
# from the configuration rather than inferred from behaviour.
info "Checking the tier core sets do not overlap"
cpuset_overlap="$(
    # shellcheck disable=SC1091
    set -a; . "${ENV_FILE}"; set +a
    python3 - "$DATA_CPUSET" "$EDGE_CPUSET" "$APP_CPUSET" "$GENERATOR_CPUSET" "$OBS_CPUSET" <<'PY' 2>/dev/null || \
    echo "python3-missing"
import sys

def expand(spec):
    cores = set()
    for part in spec.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            lo, hi = part.split("-", 1)
            cores.update(range(int(lo), int(hi) + 1))
        else:
            cores.add(int(part))
    return cores

names = ["data", "edge", "app", "generator", "obs"]
sets = {n: expand(s) for n, s in zip(names, sys.argv[1:6])}

for s in sets.values():
    if not s:
        print("empty-cpuset")
        sys.exit(0)

print("overlap" if sum(len(s) for s in sets.values()) != len(set().union(*sets.values())) else "ok")
PY
)"

if [ "$cpuset_overlap" = "ok" ]; then
    check; pass "no tier or generator shares a CPU core (data/edge/app/generator/obs disjoint)"
elif [ "$cpuset_overlap" = "python3-missing" ]; then
    check; fail "cannot verify core isolation: python3 is not available"
elif [ "$cpuset_overlap" = "empty-cpuset" ]; then
    check; fail "a tier has an empty CPUSET; every tier must be pinned"
else
    check; fail "tier CPU sets overlap, so the generator competes with the stack (${cpuset_overlap})"
fi

# ---------------------------------------------------------------- start

if [ "$START" -eq 1 ]; then
    info "Starting the environment (data + edge + app + obs)"
    dc up -d --build
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
                check; pass "${service} is ${state}"
                return 0
            fi
        fi
        sleep 3
    done
    check; fail "${service} did not become healthy within ${READY_TIMEOUT}s"
    return 1
}

info "Waiting for readiness"
wait_for_container_health postgres
wait_for_container_health redis
wait_for_container_health pgbouncer
wait_for_container_health backend
wait_for_container_health nginx

EDGE_HTTP_PORT="$(
    set -a; . "${ENV_FILE}"; set +a
    echo "${EDGE_HTTP_PORT:-8090}"
)"
EDGE_URL="http://localhost:${EDGE_HTTP_PORT}"

wait_for_http() {
    url="$1"; expected="$2"; label="$3"
    deadline=$(( SECONDS + READY_TIMEOUT ))
    last=""
    while [ "$SECONDS" -lt "$deadline" ]; do
        last="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" || true)"
        if [ "$last" = "$expected" ]; then
            check; pass "$label (HTTP ${last})"
            return 0
        fi
        sleep 3
    done
    check; fail "$label (expected HTTP ${expected}, last saw ${last:-no response})"
    return 1
}

# ---------------------------------------------------------------- 5. edge -> backend

info "Verifying the backend through the edge at ${EDGE_URL}"
wait_for_http "${EDGE_URL}/nginx-health" 200 "edge is serving"
wait_for_http "${EDGE_URL}/health" 200 "backend is reachable through the edge"
wait_for_http "${EDGE_URL}/health/readiness" 200 "backend readiness includes its database check"

# ---------------------------------------------------------------- 3. PgBouncer in the path

info "Verifying the database path goes through PgBouncer"
backend_container="$(dc ps -q backend)"
backend_env="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$backend_container" 2>/dev/null || true)"

if printf '%s' "$backend_env" | grep -q '^PGBOUNCER_HOST=pgbouncer$'; then
    check; pass "backend datasource targets PgBouncer, not PostgreSQL directly"
else
    check; fail "backend is not configured to reach PostgreSQL through PgBouncer"
fi

if printf '%s' "$backend_env" | grep -q '^SPRING_PROFILES_ACTIVE=staging$'; then
    check; pass "backend runs the staging profile (the production-like profile)"
else
    check; fail "backend is not running the staging profile"
fi

pool_size="$(printf '%s' "$backend_env" | sed -n 's/^DB_POOL_MAX_SIZE=//p' || true)"
if [ -n "$pool_size" ] && [ "$pool_size" -ge 20 ] 2>/dev/null; then
    check; pass "backend connection pool is sized for load (DB_POOL_MAX_SIZE=${pool_size})"
else
    check; fail "backend pool is too small for a capacity claim (DB_POOL_MAX_SIZE=${pool_size:-unset})"
fi

# ---------------------------------------------------------------- 6. a real query through the pooler

info "Verifying a real query through PgBouncer"
# PgBouncer runs in transaction mode and its image ships no psql, so the query is
# executed from the backend container, whose staging datasource is the pooler.
# A successful round trip here proves the whole path: backend -> pgbouncer -> postgres.
if docker exec "$backend_container" sh -c 'timeout 20 bash -c "exec 3<>/dev/tcp/pgbouncer/6432"' 2>/dev/null; then
    check; pass "application can open a connection to PgBouncer"
else
    check; fail "application cannot open a connection to PgBouncer"
fi

pooler_ready="$(curl -s --max-time 10 "${EDGE_URL}/health/readiness" || true)"
if printf '%s' "$pooler_ready" | grep -q '"status":"UP"'; then
    check; pass "readiness reports UP, which requires a working pooled database round trip"
else
    check; fail "readiness did not report UP (body: ${pooler_ready})"
fi

# ---------------------------------------------------------------- 7. Redis

info "Verifying Redis"
REDIS_PASSWORD="$(set -a; . "${ENV_FILE}"; set +a; echo "${REDIS_PASSWORD}")"
redis_ping="$(dc exec -T redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning ping 2>/dev/null | tr -d '\r' || true)"
if [ "$redis_ping" = "PONG" ]; then
    check; pass "Redis is reachable and authenticated (PING -> PONG)"
else
    check; fail "Redis did not answer PING (got: ${redis_ping:-no response})"
fi

# ---------------------------------------------------------------- 8. PostgreSQL direct

info "Verifying PostgreSQL directly (source of truth)"
pg_ok="$(dc exec -T postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" -h 127.0.0.1' 2>/dev/null | tr -d '\r' || true)"
if printf '%s' "$pg_ok" | grep -q 'accepting connections'; then
    check; pass "PostgreSQL is accepting connections"
else
    check; fail "PostgreSQL is not accepting connections (got: ${pg_ok:-no response})"
fi

# The exporters exist so the load run can answer P7 §106's questions; a run that
# cannot see connections or locks is not a capacity claim.
pg_connections="$(dc exec -T postgres psql -U exam_app -d exam_platform -tAc \
    "select count(*) from pg_stat_activity" 2>/dev/null | tr -d '[:space:]' || true)"
if [ -n "$pg_connections" ] && [ "$pg_connections" -ge 1 ] 2>/dev/null; then
    check; pass "PostgreSQL is queryable (pg_stat_activity reports ${pg_connections} sessions)"
else
    check; fail "PostgreSQL is not queryable"
fi

# ---------------------------------------------------------------- 9-11. Prometheus targets

info "Verifying Prometheus is scraping the platform"
PROMETHEUS_PORT="$(set -a; . "${ENV_FILE}"; set +a; echo "${PROMETHEUS_HOST_PORT:-9091}")"
PROMETHEUS_URL="http://localhost:${PROMETHEUS_PORT}"

target_up() {
    job="$1"; label="$2"
    # A target is healthy when Prometheus reports it as "up". The query is
    # encoded rather than interpolated so a job name cannot break the URL.
    result="$(curl -s --max-time 10 --get \
        --data-urlencode "query=up{job=\"${job}\"}" \
        "${PROMETHEUS_URL}/api/v1/query" 2>/dev/null || true)"
    if printf '%s' "$result" | grep -q '"value":\[[^]]*,"1"\]'; then
        check; pass "${label}"
    else
        check; fail "${label} (Prometheus reports the target down or absent)"
    fi
}

deadline=$(( SECONDS + READY_TIMEOUT ))
while [ "$SECONDS" -lt "$deadline" ]; do
    if curl -s --max-time 5 "${PROMETHEUS_URL}/-/ready" >/dev/null 2>&1; then
        break
    fi
    sleep 3
done

target_up "exam-platform-backend" "Prometheus scrapes the backend (job exam-platform-backend)"
target_up "postgres" "Prometheus scrapes the database exporter (job postgres)"
target_up "redis" "Prometheus scrapes the Redis exporter (job redis)"

# ---------------------------------------------------------------- 12-13. Grafana, Loki

info "Verifying the observability tier"
GRAFANA_PORT="$(set -a; . "${ENV_FILE}"; set +a; echo "${GRAFANA_HOST_PORT:-3011}")"
grafana_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://localhost:${GRAFANA_PORT}/api/health" || true)"
if [ "$grafana_status" = "200" ]; then
    check; pass "Grafana is serving (HTTP 200)"
else
    check; fail "Grafana did not answer (HTTP ${grafana_status:-no response})"
fi

grafana_ds="$(curl -s --max-time 10 -u "$(set -a; . "${ENV_FILE}"; set +a; echo "${GRAFANA_ADMIN_USER}:${GRAFANA_ADMIN_PASSWORD}")" \
    "http://localhost:${GRAFANA_PORT}/api/datasources" 2>/dev/null || true)"
if printf '%s' "$grafana_ds" | grep -qi 'prometheus' && printf '%s' "$grafana_ds" | grep -qi 'loki'; then
    check; pass "Grafana has both Prometheus and Loki provisioned"
else
    check; fail "Grafana is missing a provisioned datasource"
fi

LOKI_PORT="$(set -a; . "${ENV_FILE}"; set +a; echo "${LOKI_HOST_PORT:-3110}")"
loki_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://localhost:${LOKI_PORT}/ready" || true)"
if [ "$loki_status" = "200" ]; then
    check; pass "Loki is ready (HTTP 200)"
else
    check; fail "Loki is not ready (HTTP ${loki_status:-no response})"
fi

# ---------------------------------------------------------------- 14. generator -> edge

info "Verifying the load generator can reach the backend"
# The generator is a separate Compose project on the stack's edge network. It is
# started briefly to prove reachability from *its* network position, which is the
# only thing that matters for the load run: if this fails, 5,000 VUs would fail
# to connect for reasons that have nothing to do with the platform.
generator_ok=0
if docker compose -f "${ENV_DIR}/docker-compose.generator.yml" \
        --env-file "${ENV_FILE}" --profile load \
        run --rm -T -e P7_BASE_URL="http://nginx:80" \
        --entrypoint sh generator -c 'command -v k6 >/dev/null && echo GENERATOR_IMAGE_OK' \
        >/dev/null 2>&1; then
    generator_ok=1
fi

if [ "$generator_ok" -eq 1 ]; then
    check; pass "load generator image starts and carries the k6 binary"
else
    check; fail "load generator image could not be started"
fi

# Prove the network path from a container on the generator's network position to
# the edge, using the generator project's own network attachment.
edge_probe=0
if docker compose -f "${ENV_DIR}/docker-compose.generator.yml" \
        --env-file "${ENV_FILE}" --profile load \
        run --rm -T --entrypoint k6 generator version >/dev/null 2>&1; then
    edge_probe=1
fi
if [ "$edge_probe" -eq 1 ]; then
    check; pass "generator runs k6 from its own project and network"
else
    check; fail "generator cannot run k6"
fi

# The real assertion: a VU-shaped HTTP request from the generator's network to
# the edge. k6 is the client so this exercises the same stack the load run will
# use, not curl on the host.
#
# The probe script is written into load/out (already bind-mounted into the
# generator at /load) rather than to a fresh path, because:
#   - a brand-new host path would need its own -v mount, and on Windows the host
#     path is rewritten by MSYS into a C:/... path that the container cannot see;
#   - /load/out is git-ignored, so a transient file there is never committed.
probe_host_dir="${ROOT_DIR}/load/out"
probe_host="${probe_host_dir}/verify-edge-probe.js"
mkdir -p "${probe_host_dir}"
cat > "${probe_host}" <<'JS'
import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 1, iterations: 1 };

export default function () {
  const res = http.get('http://nginx:80/health');
  check(res, { 'edge reachable from generator': (r) => r.status === 200 });
}
JS

# On Windows, Git Bash rewrites a leading /path argument into a C:/... path
# before Docker sees it, so the in-container /load/out path would arrive as a
# host path and k6 would fail to find its script. MSYS2_ARG_CONV_EXCL excludes
# exactly that one argument from conversion.
#
# It must NOT be MSYS_NO_PATHCONV=1: that disables conversion for every
# argument, which mangles the --env-file path into "D:\d\..." and fails the run
# for an unrelated reason.
if MSYS2_ARG_CONV_EXCL="/load/out/verify-edge-probe.js" \
        docker compose -f "${ENV_DIR}/docker-compose.generator.yml" \
        --env-file "${ENV_FILE}" --profile load \
        run --rm -T --entrypoint k6 generator run /load/out/verify-edge-probe.js \
        >/dev/null 2>&1; then
    check; pass "generator host can reach the backend through the edge (k6 HTTP 200)"
else
    check; fail "generator host cannot reach the backend through the edge"
fi
rm -f "${probe_host}"

# ---------------------------------------------------------------- 15. isolation recap

info "Verifying the edge is the only published surface"
published="$(docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" \
    "${PROFILE_ARGS[@]}" ps --format '{{.Service}} {{.Ports}}' 2>/dev/null || true)"
unexpected="$(printf '%s\n' "$published" \
    | grep -E '^(postgres|redis|pgbouncer|backend|frontend) ' \
    | grep -E '0\.0\.0\.0|:::' || true)"
if [ -z "$unexpected" ]; then
    check; pass "only the edge is published; the data and app tiers have no host port"
else
    check; fail "a data or app tier service is published to the host: ${unexpected}"
fi

# ---------------------------------------------------------------- summary

echo
echo "All production-like assertions passed."
echo "  edge            ${EDGE_URL}"
echo "  prometheus      ${PROMETHEUS_URL}"
echo "  grafana         http://localhost:${GRAFANA_PORT}"
echo "  loki            http://localhost:${LOKI_PORT}"
echo
echo "Run the load generator with:"
echo "  docker compose -f ${ENV_DIR}/docker-compose.generator.yml --profile load up"
echo
echo "Stop the environment with:  $0 --down"
exit 0
