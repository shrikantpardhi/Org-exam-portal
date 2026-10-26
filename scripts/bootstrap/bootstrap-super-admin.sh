#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Bootstrap the installation SUPER_ADMIN (ADR-0032, ADR-0033, ADR-0034).
#
# The SUPER_ADMIN is a secret-bearing, non-demotable, non-archivable account, so it
# is created off the HTTP surface by a non-web Spring CLI (GAP-085 pattern): the
# script forwards the credential to SuperAdminBootstrapCli, which boots a non-web
# context, hashes with the production PasswordEncoder, assigns the SUPER_ADMIN role,
# and records a SUPER_ADMIN_BOOTSTRAP audit entry. No endpoint is added.
#
# Idempotent: if a SUPER_ADMIN already exists the CLI exits 0 without modifying it.
#
# Production gate: refuse to run against a production environment unless
# ALLOW_SUPER_ADMIN_BOOTSTRAP=true is set explicitly (GAP-085).
#
# Usage:
#   SUPER_ADMIN_EMAIL=owner@example.com \
#   SUPER_ADMIN_PASSWORD='...' \
#   ALLOW_SUPER_ADMIN_BOOTSTRAP=true \
#   ./scripts/bootstrap/bootstrap-super-admin.sh
# -----------------------------------------------------------------------------
set -euo pipefail

# --- 1. Fail fast on missing input -------------------------------------------------
: "${SUPER_ADMIN_EMAIL:?SUPER_ADMIN_EMAIL is required}"
: "${SUPER_ADMIN_PASSWORD:?SUPER_ADMIN_PASSWORD is required}"

# --- 2. Production gate ------------------------------------------------------------
APP_ENVIRONMENT="${APP_ENVIRONMENT:-${SPRING_PROFILES_ACTIVE:-}}"
if [[ "${APP_ENVIRONMENT,,}" == *"production"* || "${APP_ENVIRONMENT,,}" == *"prod"* ]]; then
  if [[ "${ALLOW_SUPER_ADMIN_BOOTSTRAP:-}" != "true" ]]; then
    echo "ERROR: refusing to bootstrap SUPER_ADMIN in a production environment." >&2
    echo "       Set ALLOW_SUPER_ADMIN_BOOTSTRAP=true only for an explicit, audited install." >&2
    exit 2
  fi
fi

# --- 3. Resolve the backend container ---------------------------------------------
BACKEND_CONTAINER="${BACKEND_CONTAINER:-${P7_BACKEND_CONTAINER:-exam-platform-backend-1}}"
PROFILE="${BOOTSTRAP_PROFILE:-staging}"

# --- 4. Forward the credential to the non-web CLI (GAP-085) ------------------------
# The jar is a Spring Boot executable archive; PropertiesLauncher boots an alternate
# main class (see load/seed.mjs for the same mechanism used by the load cohort).
printf '{"email":%s,"password":%s}' \
  "$(printf '%s' "${SUPER_ADMIN_EMAIL}" | jq -Rs .)" \
  "$(printf '%s' "${SUPER_ADMIN_PASSWORD}" | jq -Rs .)" \
  | docker exec -i "${BACKEND_CONTAINER}" java \
      -Dloader.main=com.company.exam.identity.bootstrap.SuperAdminBootstrapCli \
      -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
      --spring.profiles.active="${PROFILE}"

exit $?
