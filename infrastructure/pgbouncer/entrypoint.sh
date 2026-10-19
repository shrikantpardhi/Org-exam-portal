#!/bin/sh
# ---------------------------------------------------------------------------
# Renders the PgBouncer configuration from environment variables and starts
# PgBouncer.
#
# No credential is baked into the image: the ini is rendered from
# pgbouncer.ini.template and the auth file is written from the environment at
# start-up (specs/09-P0-Foundation-V1.5.md section 28).
# ---------------------------------------------------------------------------
set -eu

require() {
    name="$1"
    eval "value=\${$name:-}"
    if [ -z "$value" ]; then
        echo "pgbouncer-entrypoint: required environment variable $name is not set" >&2
        exit 1
    fi
}

require POSTGRES_HOST
require POSTGRES_PORT
require POSTGRES_DB
require POSTGRES_USER
require POSTGRES_PASSWORD
require PGBOUNCER_LISTEN_PORT
require PGBOUNCER_ADMIN_USER
require PGBOUNCER_ADMIN_PASSWORD
require PGBOUNCER_POOL_MODE
require PGBOUNCER_MAX_PREPARED_STATEMENTS

# envsubst substitutes only the variables it is given, so every placeholder in
# the template must have a value here. Defaults are applied in the shell, not with
# the ${VAR:-default} syntax, which envsubst does not understand.
: "${PGBOUNCER_MAX_CLIENT_CONN:=200}"
: "${PGBOUNCER_DEFAULT_POOL_SIZE:=20}"
: "${PGBOUNCER_MIN_POOL_SIZE:=5}"
: "${PGBOUNCER_RESERVE_POOL_SIZE:=5}"
export PGBOUNCER_MAX_CLIENT_CONN PGBOUNCER_DEFAULT_POOL_SIZE PGBOUNCER_MIN_POOL_SIZE \
    PGBOUNCER_RESERVE_POOL_SIZE

CONFIG_DIR=/etc/pgbouncer

# Render the configuration. envsubst is restricted to the variables this file
# actually declares, so nothing else is substituted by accident.
envsubst '${POSTGRES_DB} ${POSTGRES_HOST} ${POSTGRES_PORT} ${PGBOUNCER_LISTEN_PORT}
          ${PGBOUNCER_POOL_MODE} ${PGBOUNCER_ADMIN_USER} ${PGBOUNCER_MAX_PREPARED_STATEMENTS}
          ${PGBOUNCER_MAX_CLIENT_CONN} ${PGBOUNCER_DEFAULT_POOL_SIZE} ${PGBOUNCER_MIN_POOL_SIZE}
          ${PGBOUNCER_RESERVE_POOL_SIZE}' \
    < "${CONFIG_DIR}/pgbouncer.ini.template" > "${CONFIG_DIR}/pgbouncer.ini"

# No unresolved placeholder may reach PgBouncer: it would abort the start-up
# with "invalid value ... for parameter" instead of applying a default.
if grep -q '\${' "${CONFIG_DIR}/pgbouncer.ini"; then
    echo "pgbouncer-entrypoint: unresolved placeholder in the rendered configuration" >&2
    grep '\${' "${CONFIG_DIR}/pgbouncer.ini" >&2
    exit 1
fi

# The auth file holds the two credentials PgBouncer authenticates:
#   - the application user (the same identity the application uses end to end);
#   - the admin/stats console user, which exists only inside PgBouncer.
umask 077
cat > "${CONFIG_DIR}/userlist.txt" <<EOF
"${POSTGRES_USER}" "${POSTGRES_PASSWORD}"
"${PGBOUNCER_ADMIN_USER}" "${PGBOUNCER_ADMIN_PASSWORD}"
EOF

# The auth file must not be world readable.
chown pgbouncer:pgbouncer "${CONFIG_DIR}/userlist.txt" "${CONFIG_DIR}/pgbouncer.ini"
chmod 600 "${CONFIG_DIR}/userlist.txt" "${CONFIG_DIR}/pgbouncer.ini"

if [ ! -d /var/log/pgbouncer ]; then
    mkdir -p /var/log/pgbouncer
fi
chown pgbouncer:pgbouncer /var/log/pgbouncer

echo "pgbouncer-entrypoint: starting ${PGBOUNCER_POOL_MODE} pooling on port ${PGBOUNCER_LISTEN_PORT}" \
     "(prepared statements: ${PGBOUNCER_MAX_PREPARED_STATEMENTS}) -> ${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"

# Drop privileges before handing over to PgBouncer.
exec su-exec pgbouncer:pgbouncer pgbouncer "${CONFIG_DIR}/pgbouncer.ini"
