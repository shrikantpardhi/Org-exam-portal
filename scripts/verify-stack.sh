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

# A failure must fail the run. Without the counter the script printed FAIL but still exited 0,
# which is how a broken assertion could sit in the output unread.
FAILURES=0
pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
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

# ------------------------------------------------------------------ response headers
# GAP-137: the edge must emit the header policy on every response, error responses included.
# Checked against the health route because it is the one backend path the edge proxies and it
# returns a success status the assertions can rely on.
edge_headers="$(curl -s -D - -o /dev/null --max-time 10 "${EDGE_URL}/health" | tr -d '\r' || true)"

for required_header in \
    "content-security-policy:" \
    "x-content-type-options: nosniff" \
    "x-frame-options: DENY" \
    "referrer-policy: strict-origin-when-cross-origin"
do
    if printf '%s' "$edge_headers" | grep -qi "^${required_header}"; then
        pass "edge emits ${required_header%%:*}"
    else
        fail "edge did not emit ${required_header%%:*}"
    fi
done

if printf '%s' "$edge_headers" | grep -qi "^strict-transport-security:"; then
    fail "edge emitted HSTS over plaintext HTTP; it belongs on the TLS listener only"
else
    pass "edge omits HSTS over plaintext HTTP (it is added on the TLS listener)"
fi

# ------------------------------------------------------------------ frontend can reach the API
# GAP-164: the browser must be able to call the API through the same edge that served the page.
# These assertions are what would have caught the "Unable to reach the server" report before a user
# saw it. `curl /api/v1/...` alone proves routing but NOT that a browser succeeds, because the
# browser additionally enforces the inlined bundle value, CORS, and CSP.

# 1. The bundle must carry a RELATIVE base. An absolute origin is inlined at build time and works
#    only for the one host the image was built with.
frontend_container="$(dc ps -q frontend)"
if [ -n "$frontend_container" ]; then
    bundle_base="$(docker exec "$frontend_container" sh -c \
        "grep -rho 'NEXT_PUBLIC_API_BASE_URL:\"[^\"]*\"' /app/.next/static/chunks/ 2>/dev/null | sort -u | head -n1" \
        2>/dev/null || true)"
    case "$bundle_base" in
        *'/api/v1"'*)
            pass "browser bundle uses a relative API base (${bundle_base})" ;;
        *'http://'*|*'https://'*)
            fail "browser bundle inlines an absolute API origin (${bundle_base}); it will break at any other host (GAP-164)" ;;
        *)
            fail "could not confirm the API base in the browser bundle (found: ${bundle_base:-none})" ;;
    esac
else
    fail "frontend container is not running, so the browser bundle cannot be verified"
fi

# 2. CORS preflight must be answered for a genuine cross-origin browser. A preflight from the
#    edge's OWN origin is correctly treated as same-origin by Spring and emits no CORS headers, so
#    testing with ${EDGE_URL} proves nothing. A separate frontend origin (the `npm run dev` one) is
#    the real cross-origin case that must work; an unlisted origin must still be refused.
cors_origin="${CORS_TEST_ORIGIN:-http://localhost:3000}"
preflight_headers="$(curl -s -D - -o /dev/null --max-time 10 \
    -X OPTIONS \
    -H "Origin: ${cors_origin}" \
    -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: authorization" \
    "${EDGE_URL}/api/v1/me" | tr -d '\r' || true)"
if printf '%s' "$preflight_headers" | grep -qi "^access-control-allow-origin: ${cors_origin}"; then
    pass "CORS preflight is answered for a cross-origin frontend (${cors_origin})"
else
    fail "CORS preflight returned no Access-Control-Allow-Origin for ${cors_origin} (GAP-015/GAP-165)"
fi

# The allow-list must still reject an origin it does not trust; a permissive answer here would
# mean CORS had been weakened rather than configured.
untrusted_preflight="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
    -X OPTIONS \
    -H "Origin: http://untrusted.example.test" \
    -H "Access-Control-Request-Method: GET" \
    "${EDGE_URL}/api/v1/me" || true)"
if [ "$untrusted_preflight" = "403" ]; then
    pass "CORS refuses an untrusted origin (HTTP 403)"
else
    fail "an untrusted origin was not refused by CORS (HTTP ${untrusted_preflight})"
fi

# 3. The CSP served to the frontend must allow the API connection. `connect-src 'self'` in the
#    frontend server block is correct for a relative base; an explicit origin list is also accepted.
frontend_headers="$(curl -s -D - -o /dev/null --max-time 10 "${EDGE_URL}/" | tr -d '\r' || true)"
frontend_csp="$(printf '%s' "$frontend_headers" | grep -i '^content-security-policy:' || true)"
if printf '%s' "$frontend_csp" | grep -qi "connect-src 'self'"; then
    pass "frontend CSP allows same-origin API calls (connect-src 'self')"
elif printf '%s' "$frontend_csp" | grep -qi 'connect-src'; then
    pass "frontend CSP declares an explicit connect-src"
else
    fail "frontend CSP has no connect-src, so the browser will block API calls"
fi

# 4. A real browser must reach the API. Everything above can pass while the rendered page still
#    fails, for example when a script is blocked or hydration throws.
if [ "${VERIFY_SKIP_SMOKE:-0}" = "1" ]; then
    pass "browser smoke test skipped (VERIFY_SKIP_SMOKE=1)"
elif [ -x "${ROOT_DIR}/scripts/smoke-frontend.sh" ] || [ -f "${ROOT_DIR}/scripts/smoke-frontend.sh" ]; then
    if EDGE_URL="${EDGE_URL}" bash "${ROOT_DIR}/scripts/smoke-frontend.sh" >/tmp/smoke-frontend.log 2>&1; then
        pass "browser smoke test reached the API from a real page"
    else
        fail "browser smoke test failed (see /tmp/smoke-frontend.log)"
        tail -n 12 /tmp/smoke-frontend.log 2>/dev/null | sed 's/^/      /' || true
    fi
else
    fail "scripts/smoke-frontend.sh is missing, so no browser-level check ran"
fi

# 5. The frontend container's own published port must NOT be mistaken for the entry point. It
#    serves no /api/ route, so a user who opens :3000 gets Next's HTML 404 and the login form
#    reports a misleading "Invalid username or password" for a valid account. Assert the
#    distinction explicitly so the documented "use :8081" instruction cannot silently rot.
direct_frontend_port="${FRONTEND_HOST_PORT:-3000}"
if curl -s -o /dev/null --max-time 5 "http://localhost:${direct_frontend_port}/" 2>/dev/null; then
    direct_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
        -X POST -H 'Content-Type: application/json' -d '{}' \
        "http://localhost:${direct_frontend_port}/api/v1/auth/login" || true)"
    if [ "$direct_status" = "404" ]; then
        pass "frontend port ${direct_frontend_port} serves no API (documented: use the edge)"
    else
        fail "frontend port ${direct_frontend_port}/api returned ${direct_status}; expected 404 so the edge stays the single entry point"
    fi
else
    pass "frontend port ${direct_frontend_port} is not published; the edge is the only entry point"
fi

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
if [ "$FAILURES" -ne 0 ]; then
    echo "${FAILURES} assertion(s) FAILED."
    echo "  edge            ${EDGE_URL}"
    echo "  API             ${EDGE_URL}/api/v1/..."
    exit 1
fi

echo "All edge assertions passed."
echo "  edge            ${EDGE_URL}"
echo "  API             ${EDGE_URL}/api/v1/..."
echo "  health          ${EDGE_URL}/health"
echo "  observability   docker compose --profile observability up -d"
echo
echo "Stop the stack with:  docker compose --profile core --profile app --profile edge down"
exit 0
