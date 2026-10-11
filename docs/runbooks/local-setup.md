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
| `observability` | prometheus, grafana, loki | metrics and log exploration |

### Minimum local environment

```bash
docker compose up -d postgres redis
```

Then run the applications on the host:

```bash
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

### Full stack

```bash
docker compose --profile core --profile edge --profile app up -d
```

### With observability

```bash
docker compose --profile core --profile edge --profile app --profile observability up -d
```

## Connection paths

- **Local direct:** application → PostgreSQL on `localhost:5432`.
- **Local pooled / production-like:** application → PgBouncer on `localhost:6432` → PostgreSQL.
  Flyway migrations connect directly to PostgreSQL and do not route through PgBouncer.

## Shutting down

```bash
docker compose --profile core --profile edge --profile app --profile observability down
```

Add `-v` to remove local volumes (PostgreSQL/Redis data) for a clean slate.

## Troubleshooting

- **Backend fails at startup** with a database error: confirm `docker compose ps` shows
  postgres healthy before starting the backend. The application uses health-aware startup.
- **Redis unavailable:** the application continues for cache-optional operations and logs a
  controlled warning; cache failure never becomes data loss.
- **Port conflicts:** override `SERVER_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `PGBOUNCER_PORT`,
  or the frontend port via environment variables.
