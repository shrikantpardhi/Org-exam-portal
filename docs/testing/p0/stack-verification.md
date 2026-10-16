# P0-09 / P0-10 — Container stack verification record

Scope: closing GAP-007 by making the full local stack runnable on a Docker-capable host.
Specification: `specs/09-P0-Foundation-V1.5.md` §50-§54, §116-§117, §28.

## What was run here

| Check | Command | Result |
|---|---|---|
| Compose model is valid | `docker compose --env-file .env.example config --quiet` | **Pass** (exit 0) |
| Profile sets resolve correctly | `docker compose … config --services` per profile set | **Pass** — see below |
| Script syntax | `bash -n scripts/verify-stack.sh` | **Not run** — no `bash` on this host |

Profile resolution, measured:

```text
core                                    -> postgres, redis
core + app + edge                       -> postgres, redis, pgbouncer, backend, frontend, nginx
core + app + edge + observability       -> those six + prometheus, grafana, loki, promtail
```

## What could NOT be run here, and why

The development host has the Docker **CLI** (29.8.2) and **Compose** (v5.6.0) but **no engine**:

```text
docker info
  failed to connect to the docker API at
  npipe:////./pipe/dockerDesktopLinuxEngine; check if the path is correct and if the daemon is
  running: open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

Docker Desktop is not installed, so no engine can be started on this machine. Consequently the
following remain **unverified** and must be executed by an operator on a Docker-capable host:

- `docker compose --profile core --profile app --profile edge up -d --build`
- `nginx -t` (configuration parse) and the live routing assertions
- the PgBouncer → PostgreSQL functional path
- `bash scripts/verify-stack.sh` exiting 0

`bash` is also absent from this host, so the script could not even be syntax-checked (`bash -n`).
It was reviewed by hand instead; one defect found in review (an `on_exit` handler defined but never
registered) was fixed by adding `trap on_exit EXIT`.

**Acceptance criterion "the verification script exits 0" is therefore NOT demonstrated.**
GAP-007 moves from *Open* to *Delivered — runtime verification pending*.

## How to complete the verification

On a host with a running Docker engine:

```bash
cp .env.example .env
docker compose --env-file .env.example config --quiet      # model check
bash scripts/verify-stack.sh                               # starts, waits, asserts, exits 0/non-zero
```

Attach the script output as the evidence. The script asserts, through the edge:

1. the edge serves (`/nginx-health`);
2. the backend health route answers (`/health`) and reports `UP`;
3. readiness answers 200 — which includes the **database** indicator, so it can only pass if the
   application opened a working connection **through PgBouncer**;
4. the backend container's `PGBOUNCER_HOST=pgbouncer` (direct evidence the pool is in the path) and
   the PgBouncer container is running;
5. API routing works and the auth boundary holds (`/api/v1/reports/catalog` → 401 without a token);
6. the frontend is routed (`/` → 200);
7. the actuator tree is **not** exposed by the edge (`/actuator/health`, `/actuator/prometheus`,
   `/v3/api-docs` → 404);
8. a caller-supplied `X-Request-Id` is propagated through the edge and echoed back.

On failure it prints container status and the last 60 log lines of the backend, PgBouncer and Nginx,
and exits non-zero.

## Design decisions worth review

- **Flyway deliberately bypasses PgBouncer.** The backend runs the `staging` profile, so its
  datasource points at `pgbouncer:6432`; migrations get a separate direct datasource
  (`SPRING_FLYWAY_URL`) so they hold a real session for their lock. This matches
  §27 ("Flyway does not depend on PgBouncer") and removes the transaction-pooling lock hazard.
- **PgBouncer uses transaction pooling with `max_prepared_statements = 100`** so the PostgreSQL
  JDBC driver keeps using server-side prepared statements. `server_reset_query` is intentionally
  empty, because `DISCARD ALL` would invalidate exactly those tracked statements.
- **TLS ships inert.** No private key is committed, and Nginx will not start with a missing
  certificate, so the 443 listener and its port mapping are commented out and documented.
- **The edge does not expose the actuator tree** (404), which also closes the exposure concern
  behind GAP-138 for this path.
- **PgBouncer's container healthcheck is a liveness probe** (`nc -z`), because the PgBouncer package
  ships no PostgreSQL client; the functional path is proven end to end by the readiness assertion
  instead.
- **`app` requires `edge`.** The backend's only database path is PgBouncer, which is in the `edge`
  profile, so Compose rejects `--profile app` alone rather than starting a backend with no database.

## Deviations from P0-09 / P0-10

1. **Runtime verification is pending** (above). P0-09 says "verify startup" and P0-10 says "verify
   configuration and routing"; neither verification could be executed here.
2. **The observability profile has four services, not three.** `promtail` is added because a Loki
   boundary with nothing shipping into it is not a working boundary. Its container-log discovery is
   host-dependent (Linux), which is documented in `infrastructure/observability/promtail/`.
3. **`promtail` and `pgbouncer` names/tags** are pinned by digest-less tags (`grafana/promtail:3.1.1`,
   `alpine:3.20` via build arg). Image pinning by digest is a supply-chain hardening step
   (GAP-010) rather than part of P0.
4. **PgBouncer is built from Alpine rather than pulled from a third-party image**, so the tier has no
   dependency on an external publisher's tag. The image adds `gettext` (for `envsubst`) and `su-exec`
   to the stock `pgbouncer` package.
