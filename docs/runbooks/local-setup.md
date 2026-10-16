# Local Setup Runbook

## Prerequisites

- JDK 21 (or 25) — Java 25 preferred, Java 21 approved fallback
- Node.js 22
- Docker with Compose v2
- Maven is **not** required: use the committed wrapper `backend/mvnw`

Verify the environment:

```powershell
./scripts/verify-environment.ps1
```

```bash
./scripts/verify-environment.sh
```

## Compose profiles

The local stack is grouped so that a developer only starts what is needed
(`specs/09-P0-Foundation-V1.5.md §51`).

| Profile | Services | Use for |
|---|---|---|
| `core` | postgres, redis | running backend/frontend with `spring-boot:run` and `npm run dev` |
| `edge` | pgbouncer, nginx | exercising the production request path locally |
| `app` | backend, frontend | running the containerized applications |
| `observability` | prometheus, grafana, loki, promtail | metrics and log exploration |

### Minimum local environment

```bash
docker compose --profile core up -d postgres redis
```

Then run the applications on the host:

```bash
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

### Full stack

```bash
cp .env.example .env
docker compose --profile core --profile app --profile edge up -d --build
```

`app` requires `edge`: the backend's only database path is PgBouncer, and
PgBouncer belongs to the `edge` profile. Starting `app` without `edge` is
rejected by Compose rather than silently starting a backend with no database.

### Published ports

| Host (loopback unless noted) | Service | Purpose |
|---|---|---|
| `8081` (all interfaces) | nginx | the edge: frontend, API, health |
| `8080` | backend | direct API/actuator access, bypassing the edge |
| `3000` | frontend | direct Next.js access, bypassing the edge |
| `5432` | postgres | host-run backend (`application-local.yml`) |
| `6432` | pgbouncer | host-run backend against the pooled path |
| `6379` | redis | host-run backend cache |
| `9090` / `3001` / `3100` | prometheus / grafana / loki | observability profile |

Override any of them through the matching variable in `.env`
(`EDGE_HTTP_PORT`, `BACKEND_HOST_PORT`, `POSTGRES_PORT`, …).

### Verification

```bash
bash scripts/verify-stack.sh
```

Starts the stack, waits for readiness, and asserts through the edge that the
backend health and readiness endpoints answer, that readiness includes the
database check, that PgBouncer is in the backend's path, that API routing works
and returns 401 without a token, that the frontend is routed, that the actuator
tree is **not** exposed, and that `X-Request-Id` is propagated. It exits non-zero
and prints the relevant logs on any failure.

### With observability

```bash
docker compose --profile core --profile app --profile edge --profile observability up -d
```

## Connection paths

- **Local direct:** application → PostgreSQL on `localhost:5432`.
- **Local pooled / production-like:** application → PgBouncer on `localhost:6432` → PostgreSQL.
  Flyway migrations are the exception: they use their own direct datasource
  (`SPRING_FLYWAY_URL`) and do not route through PgBouncer, so a migration holds a
  real session for its lock (`specs/09-P0-Foundation-V1.5.md` §27).
- **In the Compose stack** the application reaches PgBouncer as `pgbouncer:6432`;
  service names are used, never `localhost`.

## TLS

TLS is **not** enabled by default. No private key is committed, and Nginx refuses
to start when a configured certificate is missing, so the 443 listener ships
inert. To enable it locally, generate a certificate pair and uncomment the TLS
server block plus the 443 port mapping — see
[`infrastructure/nginx/certs/README.md`](../../infrastructure/nginx/certs/README.md).

## Shutting down

```bash
docker compose --profile core --profile app --profile edge --profile observability down
```

Add `-v` to remove local volumes (PostgreSQL/Redis data) for a clean slate.

## Troubleshooting

- **Backend fails at startup** with a database error: confirm `docker compose ps` shows
  postgres healthy before starting the backend. The application uses health-aware startup.
- **Redis unavailable:** the application continues for cache-optional operations and logs a
  controlled warning; cache failure never becomes data loss.
- **Port conflicts:** override `SERVER_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `PGBOUNCER_PORT`,
  or the frontend port via environment variables.
