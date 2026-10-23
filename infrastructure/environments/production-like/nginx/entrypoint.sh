#!/bin/sh
# ---------------------------------------------------------------------------
# Nginx entrypoint for the production-like environment (GAP-083).
#
# The shared edge configuration (infrastructure/nginx/nginx.conf) is the single
# source of truth for routing, headers and proxy behaviour, and this environment
# must not fork it: a forked copy would drift from the configuration the other
# environments verify.
#
# But one value in it is host-sized rather than architecture: `worker_connections
# 1024` is a development default. Each proxied request consumes TWO connections
# (one from the client, one to the upstream), so with the edge pinned to two
# cores (two workers) the ceiling is roughly 2 x 1024 / 2 = ~1,024 concurrent
# users. At 5,000 VUs Nginx logs "1024 worker_connections are not enough" and
# starts refusing sockets, which would be measured as platform latency.
#
# So the file is copied to a writable path and only the capacity directives are
# rewritten at start-up. Everything else is byte-identical to the committed
# file. The values come from the environment and are recorded in sizing.md.
# ---------------------------------------------------------------------------
set -eu

: "${NGINX_WORKER_CONNECTIONS:=16384}"
: "${NGINX_WORKER_RLIMIT_NOFILE:=65536}"

RENDERED=/tmp/nginx.conf

cp /etc/nginx/nginx.conf "${RENDERED}"

# Rewrite the two capacity directives. They are anchored to the start of a line
# (allowing the indentation the file uses) so a value inside a comment or a
# string cannot be matched by accident.
sed -i "s|^[[:space:]]*worker_connections .*|    worker_connections ${NGINX_WORKER_CONNECTIONS};|" "${RENDERED}"
sed -i "s|^worker_processes .*|worker_processes auto;\nworker_rlimit_nofile ${NGINX_WORKER_RLIMIT_NOFILE};|" "${RENDERED}"

if ! grep -Eq "^[[:space:]]*worker_connections ${NGINX_WORKER_CONNECTIONS};" "${RENDERED}"; then
    echo "nginx-entrypoint: failed to set worker_connections" >&2
    exit 1
fi

echo "nginx-entrypoint: worker_connections=${NGINX_WORKER_CONNECTIONS} worker_rlimit_nofile=${NGINX_WORKER_RLIMIT_NOFILE}"

# Config test before handing over: a broken render must fail here, not in
# production-like traffic.
nginx -t -c "${RENDERED}"

exec nginx -c "${RENDERED}" -g "daemon off;"
