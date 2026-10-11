# P0 Verification Evidence

This directory holds P0 verification evidence (`docs/testing/p0/`,
`specs/09-P0-Foundation-V1.5.md §149`). Secrets and large transient logs are **not** stored in
Git; only concise, reviewable summaries are committed.

## Evidence index

| Evidence | File | Notes |
|---|---|---|
| Backend build and tests | `backend-verify.md` | `./mvnw clean verify` summary |
| Migration success | `migration.md` | clean-database Flyway migrate result |
| Frontend build and checks | `frontend-verify.md` | `npm ci`, lint, type-check, test, build |
| Docker startup | `docker.md` | compose config + container startup result |
| Health endpoint result | `health.md` | liveness/readiness responses |
| Nginx validation | `nginx.md` | `nginx -t` result |
| PgBouncer validation | `pgbouncer.md` | configuration validation result |
| Redis validation | `redis.md` | cache set/get/miss and fallback |
| CI result | `ci.md` | workflow run links |
| Secret scan result | `secret-scan.md` | scanning outcome |

## P0 verification command set

Equivalent to `specs/09-P0-Foundation-V1.5.md §160`:

```bash
# Backend
cd backend && ./mvnw clean verify

# Frontend
cd frontend && npm ci && npm run lint && npm run type-check && npm test && npm run build

# Infrastructure
docker compose config

# Migration
# clean PostgreSQL + Flyway migrate (proven by the backend integration test)

# Edge
nginx -t

# Runtime
docker compose up -d && health checks
```

The actual commands and results are recorded, not claimed.
