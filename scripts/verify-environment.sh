#!/usr/bin/env bash
# Verifies the local development environment for the Smart Competitive Examination Platform.
# See specs/09-P0-Foundation-V1.5.md §97. Does not modify the environment.
set -u

failures=0
ok()   { printf '  [ OK ] %s: %s\n' "$1" "$2"; }
fail() { printf '  [FAIL] %s: %s\n' "$1" "$2"; [ -n "${3:-}" ] && printf '         remedy: %s\n' "$3"; failures=$((failures + 1)); }
info() { printf '  [INFO] %s\n' "$1"; }

printf '\nSmart Competitive Examination Platform - environment verification\n\n'

# --- Java ---
if command -v java >/dev/null 2>&1; then
  major="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n1)"
  if [ -n "$major" ] && [ "$major" -ge 21 ]; then
    ok "Java" "version $major"
  else
    fail "Java" "version ${major:-unknown}" "Install JDK 21 (approved fallback) or JDK 25."
  fi
else
  fail "Java" "not found" "Install JDK 21 or JDK 25 and add it to PATH."
fi

# --- Maven wrapper ---
if [ -f backend/mvnw ]; then ok "Maven wrapper" "backend/mvnw present"; else fail "Maven wrapper" "missing" "Ensure backend/mvnw is committed."; fi

# --- Node ---
if command -v node >/dev/null 2>&1; then
  node_major="$(node -v | sed 's/^v\([0-9]*\).*/\1/')"
  if [ "$node_major" -ge 20 ]; then ok "Node.js" "$(node -v)"; else fail "Node.js" "$(node -v)" "Install Node.js 22 (LTS)."; fi
else
  fail "Node.js" "not found" "Install Node.js 22 (LTS)."
fi

# --- Docker ---
if command -v docker >/dev/null 2>&1; then
  ok "Docker" "$(docker --version 2>/dev/null)"
  if docker info >/dev/null 2>&1; then ok "Docker daemon" "reachable"; else fail "Docker daemon" "not reachable" "Start the Docker daemon."; fi
else
  fail "Docker" "not found" "Install Docker with Compose v2."
fi

# --- .env ---
if [ -f .env ]; then ok "Local .env" "present (not committed)"; else info "Local .env not present - copy .env.example to .env for local runs."; fi

# --- PostgreSQL port (informational) ---
pg_host="${POSTGRES_HOST:-localhost}"; pg_port="${POSTGRES_PORT:-5432}"
if command -v bash >/dev/null 2>&1 && (exec 3<>"/dev/tcp/${pg_host}/${pg_port}") 2>/dev/null; then
  ok "PostgreSQL port" "${pg_host}:${pg_port} reachable"
else
  info "PostgreSQL port ${pg_host}:${pg_port} not reachable - start it with 'docker compose up -d postgres'."
fi

# --- Redis port (informational) ---
redis_host="${REDIS_HOST:-localhost}"; redis_port="${REDIS_PORT:-6379}"
if (exec 3<>"/dev/tcp/${redis_host}/${redis_port}") 2>/dev/null; then
  ok "Redis port" "${redis_host}:${redis_port} reachable"
else
  info "Redis port ${redis_host}:${redis_port} not reachable - start it with 'docker compose up -d redis'."
fi

echo ""
if [ "$failures" -gt 0 ]; then
  printf 'Environment verification FAILED with %s issue(s).\n' "$failures"
  exit 1
fi
printf 'Environment verification PASSED.\n'
