# ---------------------------------------------------------------------------
# PgBouncer image.
#
# Build context is ./infrastructure/pgbouncer (its configuration and entrypoint
# live there, per specs/09-P0-Foundation-V1.5.md section 28); this Dockerfile is
# kept under infrastructure/docker/ with the other shared build assets.
#
# The base tag is overridable so a security patch does not require editing this
# file:  docker build --build-arg ALPINE_TAG=3.21 .
#
# Alpine's pgbouncer package is >= 1.21, which is required for
# max_prepared_statements (transaction pooling with tracked prepared statements).
# ---------------------------------------------------------------------------
ARG ALPINE_TAG=3.20
FROM alpine:${ALPINE_TAG}

# gettext       -> envsubst, for rendering the configuration template
# su-exec       -> drop privileges before starting PgBouncer
# busybox nc    -> the container healthcheck's liveness probe
RUN apk add --no-cache pgbouncer gettext su-exec

# The package creates the unprivileged pgbouncer account and /etc/pgbouncer.
COPY pgbouncer.ini.template /etc/pgbouncer/pgbouncer.ini.template
COPY entrypoint.sh /usr/local/bin/pgbouncer-entrypoint.sh
RUN chmod 0755 /usr/local/bin/pgbouncer-entrypoint.sh \
    && mkdir -p /var/log/pgbouncer \
    && chown pgbouncer:pgbouncer /var/log/pgbouncer

# Rendered at start-up from the environment; never present in the image.
ENV PGBOUNCER_LISTEN_PORT=6432 \
    PGBOUNCER_POOL_MODE=transaction \
    PGBOUNCER_MAX_PREPARED_STATEMENTS=100

EXPOSE 6432

# The healthcheck is defined in docker-compose.yml alongside the other services.
ENTRYPOINT ["/usr/local/bin/pgbouncer-entrypoint.sh"]
