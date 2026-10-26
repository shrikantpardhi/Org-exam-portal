# Smart Competitive Examination Platform

A configurable competitive-examination engine with a web-based learning, assessment,
commerce, analytics, and administration platform around it.

- **Version:** 1.5 (implementation baseline)
- **Architecture:** Modular monolith
- **Backend:** Java 21/25 + Spring Boot 4
- **Frontend:** Next.js + TypeScript
- **Database:** PostgreSQL (transactional source of truth)
- **Cache:** Redis (cache only)
- **Connection pooling:** PgBouncer
- **Migration:** Flyway
- **Edge:** Nginx
- **Containers:** Docker / Docker Compose
- **CI/CD:** GitHub Actions
- **Observability:** Prometheus + Grafana + Loki

The platform is **not** "a website that conducts tests". It is a configurable examination
engine. Configuration drives examination behaviour; code provides the platform capabilities.

> **Current state: P0–P13 implemented.** The full platform is built: identity, academic
> structure, content and question bank, blueprints and assessments, the runtime exam engine,
> evaluation and scorecards, analytics, commerce and access management, communication and
> notification, administration, and reporting. The container stack runs and is verified.
>
> The release decision is **NO-GO** pending the outstanding performance gate. Start here:
> [`docs/PROJECT-SUMMARY.md`](docs/PROJECT-SUMMARY.md) — the standing summary of what exists, what is
> verified, and what is still open — then
> [`docs/implementation-status.md`](docs/implementation-status.md) for the per-phase detail and
> [`docs/known-gaps.md`](docs/known-gaps.md) for the open gaps.

---

## 1. Specifications (source of truth)

The frozen specification package lives in [`specs/`](specs/). Read it before writing code.

Read order (`specs/README.md`):

1. [`specs/README.md`](specs/README.md)
2. [`specs/V1.5-SCOPE-AND-FREEZE-AUTHORITY.md`](specs/V1.5-SCOPE-AND-FREEZE-AUTHORITY.md)
3. [`specs/01-Master-PRD-V1.5.md`](specs/01-Master-PRD-V1.5.md)
4. [`specs/02-Domain-Model-V1.5.md`](specs/02-Domain-Model-V1.5.md)
5. [`specs/03-Technical-Design-Document-V1.5.md`](specs/03-Technical-Design-Document-V1.5.md)
6. [`specs/04-Database-Schema-V1.5.md`](specs/04-Database-Schema-V1.5.md)
7. [`specs/05-API-Contract-V1.5.md`](specs/05-API-Contract-V1.5.md)
8. [`specs/06-Architecture-Freeze-V1.5.md`](specs/06-Architecture-Freeze-V1.5.md)
9. [`specs/07-Development-Roadmap-V1.5.md`](specs/07-Development-Roadmap-V1.5.md)
10. [`specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md`](specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md)
11. Phase specifications `specs/09-...` through `specs/22-...`

Authority order is defined in
[`specs/V1.5-SCOPE-AND-FREEZE-AUTHORITY.md §5`](specs/V1.5-SCOPE-AND-FREEZE-AUTHORITY.md).

---

## 2. Repository layout

```text
.
├── AGENTS.md              # Instructions for AI coding agents
├── README.md              # This file
├── CONTRIBUTING.md        # Contribution workflow
├── SECURITY.md            # Security policy and reporting
├── docker-compose.yml     # Local development stack
├── .env.example           # Example environment configuration
│
├── backend/               # Spring Boot modular monolith (Maven)
├── frontend/              # Next.js web application
├── database/documentation # Schema documentation (schema itself lives in Flyway)
├── docs/
│   ├── architecture/      # Architecture overview
│   ├── decisions/         # Architecture Decision Records
│   ├── requirements/      # Requirement pointers
│   ├── runbooks/          # Operational runbooks
│   ├── testing/           # Verification evidence
│   └── implementation-status.md
├── infrastructure/
│   ├── docker/            # Docker support assets
│   ├── nginx/             # Edge reverse proxy configuration
│   ├── pgbouncer/         # Connection pooling configuration
│   └── monitoring/        # prometheus / grafana / loki
├── scripts/               # Environment verification and helper scripts
└── specs/                 # Frozen specification package
```

Backend and frontend build independently; neither requires the other's runtime to build.

---

## 3. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (LTS) or 25 | Java 25 preferred; Java 21 is the approved enterprise fallback |
| Node.js | 22 (LTS) | pinned in `frontend/.nvmrc` |
| Docker | current | required for the containerized stack |
| Docker Compose | v2 | `docker compose` |

The Maven build uses the committed **Maven Wrapper** (`backend/mvnw`) — no global Maven
install is required.

Verify your machine:

```bash
# PowerShell
./scripts/verify-environment.ps1

# Bash
./scripts/verify-environment.sh
```

---

## 4. Local quick start (canonical onboarding)

```bash
git clone <repository-url>
cd smart-exam-portal

# 1. Start PostgreSQL and Redis
docker compose up -d postgres redis

# 2. Run the backend
cd backend
./mvnw test              # unit + integration tests
./mvnw spring-boot:run   # http://localhost:8080

# 3. In another terminal, run the frontend
cd frontend
npm ci
npm run dev              # http://localhost:3000
```

Health and docs once the backend is running:

- Liveness: `GET http://localhost:8080/actuator/health/liveness`
- Readiness: `GET http://localhost:8080/actuator/health/readiness`
- OpenAPI UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

The OpenAPI UI and document are available in `local` and on the `staging` stack, but are **refused
in production** (GAP-136): the `prod` profile disables springdoc and `SecurityConfig` returns 401 for
those paths.

Only `/actuator/health`, `/actuator/health/**` and `/actuator/info` are anonymous (GAP-138). The rest
of the actuator tree — including `/actuator/prometheus` — requires either a bearer token matching
`MONITORING_METRICS_TOKEN` (the Prometheus scrape job's credential) or an authenticated principal
holding `SYSTEM_HEALTH_VIEW`. Without `MONITORING_METRICS_TOKEN` the surface fails closed rather than
opening. To read metrics by hand:

```bash
curl -H "Authorization: Bearer $MONITORING_METRICS_TOKEN" http://localhost:8080/actuator/prometheus
```

### Full integration stack (Docker)

Runs the whole platform — PostgreSQL, Redis, PgBouncer, the backend, the frontend
and the Nginx edge — on a Docker-capable host. This is the environment P0-09 and
P0-10 were never validated against.

```bash
# 1. Provide credentials (required: Compose fails fast without them).
cp .env.example .env

# 2. Validate the Compose model without starting anything.
docker compose --env-file .env.example config --quiet

# 3. Start the full stack (all three profiles are required: the backend's only
#    database path is PgBouncer, which belongs to the edge profile).
docker compose --profile core --profile app --profile edge up -d --build

# 4. Follow the application start-up (Flyway runs its migrations on boot).
docker compose logs -f backend
```

Reach the stack through the edge:

| Route | Purpose |
|---|---|
| `http://localhost:8081` | Frontend through Nginx |
| `http://localhost:8081/api/v1/...` | API through Nginx (path preserved) |
| `http://localhost:8081/health` | Backend health through Nginx |
| `http://localhost:8081/health/readiness` | Readiness, **including the database check** |
| `http://localhost:8080/actuator/health` | Backend directly (loopback only, bypasses the edge) |
| `http://localhost:8081/actuator/...` | Deliberately **404**: the edge never exposes the actuator tree |

Verify the stack end to end (starts it, waits for readiness, asserts routing, the
PgBouncer path, the auth boundary and request-id propagation):

```bash
bash scripts/verify-stack.sh              # build, start, verify
bash scripts/verify-stack.sh --no-build   # reuse existing images
bash scripts/verify-stack.sh --down       # tear the stack down
```

Stop it with:

```bash
docker compose --profile core --profile app --profile edge down
# add -v to also remove the database and artifact volumes
```

Add the observability profile (Prometheus, Grafana, Loki, Promtail):

```bash
docker compose --profile core --profile app --profile edge --profile observability up -d
# Grafana http://localhost:3001  ·  Prometheus http://localhost:9090
```

Profile reference:

| Profile | Services | When |
|---|---|---|
| `core` | postgres, redis, mailpit | Always; this is also all a host-run backend needs |
| `app` | backend, frontend | To run the application in containers |
| `edge` | nginx, pgbouncer | With `app`: the edge and the database connection path |
| `observability` | prometheus, grafana, loki, promtail | Operator tooling |

**The email provider is configuration.** Three adapters ship: `FAKE` (in memory, the application
default), `SMTP` (Mailpit locally, or any relay / hosted SMTP endpoint) and `HTTP_API` (any
JSON-over-HTTP provider — SendGrid, Mailgun, Postmark, Brevo, Resend). Adopting a provider means
setting `COMMUNICATION_PROVIDER`, `COMMUNICATION_HTTP_URL`, `COMMUNICATION_HTTP_BODY_TEMPLATE` and
the name of the environment variable holding the key — no code change (`ADR-0049`). The API key is
never a configuration value: `COMMUNICATION_HTTP_API_KEY_ENV` names the variable, and the value is
read from the environment at send time.

**Mail is delivered to Mailpit, not to real recipients.** The containerised backend runs with
`COMMUNICATION_PROVIDER=SMTP` and `SPRING_MAIL_HOST=mailpit`, so every rendered email — password
resets, first-password invitations, announcements — lands in the Mailpit web UI at
<http://localhost:8025> instead of going nowhere. The application default remains `FAKE`, so a
host-run backend and every test send nothing. Both ports are published on loopback only
(`ADR-0048`).

**PgBouncer is in the backend's database path.** The backend runs the `staging`
profile, which resolves its datasource to `pgbouncer:6432` rather than to
PostgreSQL directly, so every application query is pooled. The pool uses
transaction pooling with tracked server-side prepared statements.

**Flyway is the one deliberate exception.** Migrations get their own direct
PostgreSQL datasource (`SPRING_FLYWAY_URL`) so they hold a real session for their
lock, matching the frozen rule that Flyway does not depend on PgBouncer
(`specs/09-P0-Foundation-V1.5.md` §27). The `/health/readiness` route reports the
application database indicator, so it still proves the pooled path.

Certificates are not committed: the TLS listener ships inert and the 443 port
mapping is commented out. See
[`infrastructure/nginx/certs/README.md`](infrastructure/nginx/certs/README.md).

See [`docs/runbooks/local-setup.md`](docs/runbooks/local-setup.md) for profile details.

---

## 5. Build and test commands

### Backend

```bash
cd backend
./mvnw clean verify      # compile, unit tests, integration tests, package
./mvnw test              # tests only
./mvnw spring-boot:run   # run locally
```

Integration tests use an embedded, disposable PostgreSQL instance (see
`docs/decisions/ADR-0009-integration-test-datastore.md`) so that CI and local runs are
deterministic without requiring a pre-running database.

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm run type-check
npm test
npm run build
```

### Infrastructure

```bash
docker compose --env-file .env.example config --quiet   # validate the Compose model
docker compose --profile core up -d postgres redis mailpit   # core services only
bash scripts/verify-stack.sh                             # full-stack verification
```

`--env-file .env.example` validates the model without creating a `.env`; the real
stack reads `.env`.

---

## 6. Environment configuration

Configuration is externalized and environment-aware (never branched in business logic).

| Profile | Purpose |
|---|---|
| `local` | Docker Compose PostgreSQL/Redis; direct database connection |
| `test` | Deterministic automated tests (embedded datastore) |
| `staging` | Production-like; PgBouncer connection path |
| `production` | Production; PgBouncer path; secrets from a secret manager |

Copy `.env.example` to `.env` for local development. **Never commit `.env`.**

Backend settings are read from environment variables (see `.env.example`) and
`backend/src/main/resources/application*.yml`.

---

## 7. Branch and pull request rules

Branches (`specs/09-P0-Foundation-V1.5.md §7`):

```text
main                      # protected default branch
feature/<short-name>      # e.g. feature/p1-identity-auth
fix/<short-name>
docs/<short-name>
infra/<short-name>
```

- No direct push to `main`.
- Every change merges through a pull request with green CI.
- Pull requests use the repository PR template and report database, API, test, and security
  impact.

---

## 8. Agent workflow

This repository is developed with AI implementation agents. See [`AGENTS.md`](AGENTS.md).

Working protocol for every task:

1. Read `specs/README.md`, the scope authority, and the relevant phase specification.
2. Inspect the existing implementation before editing.
3. Implement the smallest correct change inside the frozen architecture.
4. Write tests.
5. Run the tests and the build.
6. Review the diff (`git status`, `git diff --stat`, `git diff`).
7. Report files changed, migrations, endpoints, tests, commands, build result, and unresolved
   issues.
8. Stop for review before the next high-risk task.

**Stop on architecture conflicts.** If a frozen contract is insufficient, do not invent
schema, API, or authorization. Record the gap and stop.

---

## 9. What is not in scope

Compliance frameworks, native mobile apps, SMS/WhatsApp channels, pricing, go-to-market,
support operations, numeric recovery objectives, and AI capabilities are outside the
engineering package (`specs/README.md`).
