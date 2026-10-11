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

> **Current state: P0 — Foundation.** Only engineering rails exist. No business features are
> implemented. See [`docs/implementation-status.md`](docs/implementation-status.md).

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

### Full integration stack

```bash
docker compose --profile core --profile edge --profile app up -d
# Nginx edge: http://localhost:8081
```

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
docker compose config                 # validate the Compose model
docker compose up -d postgres redis   # core services
```

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
