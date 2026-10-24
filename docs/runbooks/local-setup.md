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

**Open the application at `http://localhost:8081`, not at `:3000`.**

| Host (loopback unless noted) | Service | Purpose |
|---|---|---|
| `8081` (all interfaces) | nginx | **the edge: use this in the browser** — frontend, API, health on one origin |
| `8080` | backend | direct API/actuator access, bypassing the edge |
| `3000` | frontend | direct Next.js process; it serves **no** `/api/` routes (see below) |
| `5432` | postgres | host-run backend (`application-local.yml`) |
| `6432` | pgbouncer | host-run backend against the pooled path |
| `6379` | redis | host-run backend cache |
| `9090` / `3001` / `3100` | prometheus / grafana / loki | observability profile |

Override any of them through the matching variable in `.env`
(`EDGE_HTTP_PORT`, `BACKEND_HOST_PORT`, `POSTGRES_PORT`, …).

#### Why port 3000 cannot log you in

The frontend calls the API on the **same origin** it was served from (`/api/v1`). Only the edge
serves `/api/` — it proxies that path to the backend. The Next.js process on `:3000` knows nothing
about `/api/`, so it answers with its own **HTML 404 page**. The login form treats any failure as
bad credentials, so the symptom is a misleading *"Invalid username or password"* even for a
perfectly valid account.

```bash
# :3000 -> 404 text/html (Next.js's own 404 page, not the API)
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' -X POST \
  -H 'Content-Type: application/json' -d '{}' http://localhost:3000/api/v1/auth/login
# :8081 -> the real API
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' -X POST \
  -H 'Content-Type: application/json' -d '{}' http://localhost:8081/api/v1/auth/login
```

Port 3000 is published only for inspecting the Next.js process directly, not for using the app.

#### There is no default login

Migrations seed no usable account (only a disabled system actor), so a fresh stack has **nobody who
can sign in**. Provision one through the audited in-container CLI — it adds no HTTP surface and is
environment-gated:

```bash
printf '[{"email":"me@example.test","firstName":"Me","lastName":"Local"}]' | \
  docker compose --profile core --profile app --profile edge exec -T backend \
  sh -c "cat > /tmp/m.json; java '-Dloader.main=com.company.exam.identity.loadcohort.LoadCohortCli' \
    -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
    --spring.profiles.active=staging --app.load-cohort.enabled=true < /tmp/m.json"
```

Read the generated password from the `LOAD_COHORT_RESULT` line. It is printed once and never
stored in plaintext (the database holds only a hash), so capture it immediately.

That account is a **STUDENT**. To reach the administration screens, grant `ADMIN` with the
documented operational step from ADR-0014 (`load/seed.mjs` performs the same insert):

```sql
INSERT INTO user_role (id, user_id, role_id, status, created_by, created_on, updated_by, updated_on)
SELECT gen_random_uuid(), u.id, r.id, 'ACTIVE', u.id, now(), u.id, now()
FROM public."user" u JOIN role r ON r.code = 'ADMIN'
WHERE u.email = 'me@example.test'
ON CONFLICT (user_id, role_id) DO UPDATE SET status = 'ACTIVE';
```

### Verification

```bash
bash scripts/verify-stack.sh
```

Starts the stack, waits for readiness, and asserts through the edge that the
backend health and readiness endpoints answer, that readiness includes the
database check, that PgBouncer is in the backend's path, that API routing works
and returns 401 without a token, that the frontend is routed, that the actuator
tree is **not** exposed, that the response-header policy is emitted, that CORS
answers a trusted cross-origin preflight and refuses an untrusted one, and that
`X-Request-Id` is propagated. It exits non-zero and prints the relevant logs on
any failure.

The last group of assertions is about the **browser**, not curl:

- the bundle served to the browser carries a *relative* API base, not a baked
  absolute origin;
- the CSP served with the frontend HTML permits the API connection;
- a real headless browser loads the page, runs its JavaScript, and reaches the
  API through the edge (`scripts/smoke-frontend.sh`).

That last check is what catches "the page renders but the API call fails". A
curl request to `/api/v1/...` proves routing only; it cannot see the inlined
bundle value, CORS, or CSP enforcement, which live in the browser.

To check only the browser layer:

```bash
bash scripts/smoke-frontend.sh
```

Set `EDGE_URL` to test a host other than `http://localhost:8081`. Set
`SMOKE_EMAIL` and `SMOKE_PASSWORD` to also drive a real login through the form
and assert the authenticated `/api/v1/me` call succeeds. Without credentials it
still probes the API from the page's own context, which is enough to prove the
network path works.

### The frontend cannot reach the API

The frontend calls the API on the **same origin** it was served from, using the
relative base `/api/v1`. Three things must hold, and each has a distinct symptom:

1. **The bundle has a relative base.** `NEXT_PUBLIC_API_BASE_URL` is inlined at
   `next build` time. If it is an absolute origin, the image only works for the
   one host it was built with, and every other host is refused. Confirm what the
   running image actually contains:

   ```bash
   docker compose --profile core --profile app --profile edge exec frontend \
     sh -c "grep -rho 'NEXT_PUBLIC_API_BASE_URL:\"[^\"]*\"' /app/.next/static/chunks/ | sort -u"
   ```

   The value must be `/api/v1`. A change here requires
   `docker compose build --no-cache frontend`; changing the environment of a
   running container has no effect, because the value is inside the bundle.

2. **The edge forwards the browser's `Host` verbatim, port included.** Nginx uses
   `$http_host`, not `$host`. `$host` drops the port, so the application sees
   itself as `localhost` while the browser's `Origin` is `http://localhost:8081`;
   Spring then treats a same-origin call as cross-origin and answers
   `403 Invalid CORS request`.

3. **Every origin a user actually types is in `CORS_ALLOWED_ORIGINS`.**
   `http://127.0.0.1:8081` and `http://localhost:8081` are different origins to a
   browser. Add each hostname the deployment is served from.

`403 Invalid CORS request` means (2) or (3). `Unable to reach the server` with a
CSP `connect-src` violation in the console means (1).

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
- **The frontend shows "Unable to reach the server"**: see
  *The frontend cannot reach the API* under Verification above.
- **Redis unavailable:** the application continues for cache-optional operations and logs a
  controlled warning; cache failure never becomes data loss.
- **Port conflicts:** override `SERVER_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `PGBOUNCER_PORT`,
  or the frontend port via environment variables.
- **No account can log in on a fresh stack:** migrations seed only a disabled system actor, so
  there is no default login. Provision a real account through the audited in-container CLI
  (it adds no HTTP surface and is environment-gated):

  ```bash
  echo '[{"email":"me@example.test","firstName":"Me","lastName":"Local"}]' | \
    docker compose --profile core --profile app --profile edge exec -T backend \
    java '-Dloader.main=com.company.exam.identity.loadcohort.LoadCohortCli' \
      -cp /app/app.jar \
      org.springframework.boot.loader.launch.PropertiesLauncher \
      --spring.profiles.active=staging --app.load-cohort.enabled=true
  ```

  The generated credential is printed once on the `LOAD_COHORT_RESULT` line and is never
  stored in plaintext. It is idempotent by email, so re-running updates rather than duplicates.
