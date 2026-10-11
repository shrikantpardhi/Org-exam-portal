# Smart Competitive Examination Platform
## P0 — Foundation Implementation Specification

**Version:** 1.5
**Phase:** P0 — Repository and Agent Foundation
**Status:** IMPLEMENTATION-READY
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 preferred, Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Migration:** Flyway
**Cache:** Redis
**Production Connection Pooling:** PgBouncer
**Reverse Proxy:** Nginx
**Containers:** Docker / Docker Compose
**CI/CD:** GitHub Actions
**Observability:** Prometheus + Grafana + Loki
**API:** REST + JSON, `/api/v1`
**OpenAPI:** Swagger / springdoc-compatible OpenAPI implementation
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Architects

---

## 1. Purpose

This document converts the frozen architecture, technical design, database standards, API contract, roadmap, and ExamEngine and CDUI contract into the first implementation task.

P0 is the engineering foundation only.

P0 does not implement Question Bank business features, Blueprint business features, Assessment Builder, Assessment Delivery, Evaluation, Analytics, Commerce, Communication, Full Administration, or AI features.

P0 creates the rails on which those features are implemented.

The goal of P0 is:

```text
Repository exists
        |
        v
Builds reproducibly
        |
        v
Applications start
        |
        v
PostgreSQL works
        |
        v
Flyway works
        |
        v
Redis works
        |
        v
PgBouncer path verified
        |
        v
API framework works
        |
        v
Security foundation exists
        |
        v
Frontend foundation works
        |
        v
CI verifies everything
        |
        v
Implementation can proceed safely
```

P0 is successful when a new developer can clone the repository, run the documented commands, and obtain the same deterministic development environment without manually editing source code.

---

## 2. Source-of-truth hierarchy

The implementation reads the project documents in the following order.

```text
Master PRD
        |
        v
Domain Model
        |
        v
Technical Design Document
        |
        v
Database Standards and Schema
        |
        v
API Contract
        |
        v
Architecture Freeze
        |
        v
Development Roadmap
        |
        v
ExamEngine and CDUI Contract
        |
        v
Phase Implementation Specifications P0 to P13
```

This document does not supersede the earlier product or architecture documents. Where this document adds implementation detail, that detail remains inside the frozen architecture.

Where a conflict is discovered:

```text
DO NOT silently choose a side.
DO NOT silently alter a frozen document.
DO NOT invent a new architecture.

Create an engineering decision record and stop the affected task.
```

---

## 3. P0 objectives

P0 has eight concrete objectives.

### 3.1 Repository foundation

Create the approved repository structure.

### 3.2 Backend foundation

Create a minimal Spring Boot application with the approved modular package structure.

### 3.3 Frontend foundation

Create a minimal Next.js application using the approved frontend stack.

### 3.4 Persistence foundation

Create PostgreSQL connectivity, Flyway migration infrastructure, JPA and Hibernate integration, and database test support.

### 3.5 Cache foundation

Create Redis configuration and a cache abstraction.

Redis remains cache-only.

The application remains functionally usable when Redis is unavailable for non-cache-critical operations.

### 3.6 Runtime and infrastructure foundation

Create Docker, Docker Compose, Nginx, PgBouncer, environment configuration, local development services, production-oriented configuration structure, and the PgBouncer integration boundary.

### 3.7 Delivery and engineering foundation

Create the standard API response envelope, standard error handling, request correlation, validation infrastructure, structured logging, health endpoints, OpenAPI infrastructure, and test framework.

### 3.8 AI-agent foundation

Create repository instructions, contributor guidelines, task workflow, repository validation commands, branch and PR rules, and architecture-change stop rules.

---

## 4. P0 non-goals

P0 does not implement business-domain behavior beyond what is necessary to prove infrastructure.

P0 does not create:

```text
Question CRUD
Assessment CRUD
Student Attempt CRUD
Payment processing
Analytics calculations
Notification delivery
```

P0 does not create placeholder APIs that appear to implement those features.

P0 does not create temporary business tables that are not in the approved schema.

P0 does not add convenience entities such as:

```text
FeatureFlag
GenericConfig
UserPreference
FrontendConfiguration
ExamEngineConfiguration
```

unless an approved downstream document explicitly requires them.

---

## 5. Repository structure

Approved high-level repository.

```text
exam-platform/
|
+-- AGENTS.md
+-- README.md
+-- CONTRIBUTING.md
+-- SECURITY.md
+-- docker-compose.yml
|
+-- backend/
|   +-- pom.xml
|   +-- Dockerfile
|   +-- .dockerignore
|   +-- src/
|       +-- main/
|       |   +-- java/
|       |   |   +-- com/
|       |   |       +-- company/
|       |   |           +-- exam/
|       |   |               +-- common/
|       |   |               +-- config/
|       |   |               +-- identity/
|       |   |               +-- academic/
|       |   |               +-- content/
|       |   |               +-- assessment/
|       |   |               +-- delivery/
|       |   |               +-- evaluation/
|       |   |               +-- analytics/
|       |   |               +-- commerce/
|       |   |               +-- communication/
|       |   |               +-- administration/
|       |   |               +-- infrastructure/
|       |   +-- resources/
|       |       +-- application.yml
|       |       +-- db/
|       |           +-- migration/
|       +-- test/
|           +-- java/
|
+-- frontend/
|   +-- package.json
|   +-- next.config.*
|   +-- tsconfig.json
|   +-- Dockerfile
|   +-- .dockerignore
|   +-- app/
|   +-- components/
|   +-- features/
|   +-- lib/
|   +-- hooks/
|   +-- schemas/
|   +-- types/
|
+-- database/
|   +-- documentation/
|
+-- docs/
|   +-- architecture/
|   +-- decisions/
|   +-- requirements/
|   +-- runbooks/
|   +-- testing/
|   +-- implementation-status.md
|
+-- infrastructure/
|   +-- docker/
|   +-- nginx/
|   +-- pgbouncer/
|   +-- monitoring/
|       +-- prometheus/
|       +-- grafana/
|       +-- loki/
|
+-- .github/
    +-- workflows/
    +-- pull_request_template.md
    +-- ISSUE_TEMPLATE/
```

The exact framework-generated files may differ by current toolchain conventions. The architectural directories remain recognizable.

---

## 6. Monorepo principle

P0 uses one repository for the implementation.

The repository contains backend, frontend, infrastructure, database documentation, and project documentation.

The backend and frontend remain separately buildable. The repository does not require one runtime process to build the other.

---

## 7. Git foundation

Create the initial Git repository with `main` as the protected default branch.

All implementation work uses feature branches.

Recommended pattern.

```text
feature/p0-foundation
feature/p1-identity-auth
feature/p2-academic
feature/p3-content
feature/p4-assessment
feature/p5-delivery
feature/p6-evaluation
feature/p7-pilot
feature/p8-analytics
feature/p9-commerce
feature/p10-communication
feature/p11-administration
feature/p12-hardening
feature/p13-reporting
```

Bug fixes use `fix/<short-description>`.

Documentation uses `docs/<short-description>`.

Infrastructure uses `infra/<short-description>`.

No direct push to `main`.

---

## 8. Branch protection

The repository requires.

```text
Pull request before merge
CI success
No direct push to main
```

Where supported, at least one review is required for protected branches.

Working branches may be committed to directly. Repository protection is not bypassed.

---

## 9. Initial commit strategy

P0 is broken into reviewable commits.

Recommended initial sequence.

```text
Commit 01  Repository structure and documents
Commit 02  Backend Spring Boot bootstrap
Commit 03  Frontend Next.js bootstrap
Commit 04  Database and Flyway
Commit 05  Redis
Commit 06  Docker Compose
Commit 07  Nginx and PgBouncer boundary
Commit 08  API and common infrastructure
Commit 09  Testing infrastructure
Commit 10  GitHub Actions CI
Commit 11  P0 verification evidence
```

Commits remain logically grouped. Avoid one enormous initial commit.

---

## 10. Java backend baseline

Create a Spring Boot 4 application.

Preferred Java version is 25.

Fallback is Java 21 only when the approved engineering environment requires it.

The project uses Maven.

The root Maven module is `backend`.

---

## 11. Backend package structure

Use feature-oriented modules.

Each module prepares for.

```text
controller/
service/
repository/
entity/
dto/
mapper/
validation/
exception/
util/
```

P0 does not create dozens of empty classes.

Create only the infrastructure classes required at foundation stage.

Example.

```text
common/
    api/
    exception/
    audit/
    logging/
    validation/
    security/
    web/

config/
    SecurityConfig
    JacksonConfig
    OpenApiConfig
    RedisConfig
    SchedulingConfig
    DatabaseConfig
```

When feature development begins, the feature packages are expanded.

---

## 12. Common backend foundation

P0 establishes the following.

```text
common/
  api/
    ApiResponse
    ApiErrorResponse
    PageResponse
  exception/
    ValidationException
    AuthenticationException
    AuthorizationException
    NotFoundException
    ConflictException
    BusinessException
    InvalidStateException
    GlobalExceptionHandler
  logging/
  validation/
  web/
    RequestIdFilter
    RequestContext
```

These are infrastructure building blocks, not business logic.

---

## 13. API response contract

P0 provides the standard success envelope.

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": {}
}
```

Collection endpoints support the standardized paginated response pattern defined by the API contract.

The API implementation does not expose JPA entities directly.

```text
Entity
   |
   v
Mapper
   |
   v
Response DTO
   |
   v
Controller
```

---

## 14. Error contract

P0 establishes a single global exception handler.

Minimum error categories.

```text
VALIDATION_ERROR
AUTHENTICATION_ERROR
AUTHORIZATION_ERROR
NOT_FOUND
CONFLICT
BUSINESS_ERROR
INVALID_STATE
RATE_LIMITED
SYSTEM_ERROR
```

Recommended error fields.

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "fieldName",
      "code": "INVALID_VALUE",
      "message": "Value is invalid"
    }
  ],
  "timestamp": "2026-10-07T10:00:00Z",
  "path": "/api/v1/example"
}
```

Never return stack traces, database credentials, SQL, JWT, payment secrets, or internal filesystem paths.

---

## 15. HTTP status mapping

The foundation supports.

```text
200 OK
201 Created
202 Accepted
204 No Content
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
412 Precondition Failed
422 Unprocessable Entity
429 Too Many Requests
500 Internal Server Error
503 Service Unavailable
```

Business modules determine which status applies to a specific operation.

---

## 16. Request correlation

Every API request receives `X-Request-Id`.

Rules.

```text
If a valid request ID is supplied, propagate it safely.
Otherwise, generate one.
Return it in the response.
```

The request ID appears in structured logs.

Arbitrary client input does not become a trusted security identity.

---

## 17. JSON and serialization

Jackson remains the JSON serialization mechanism.

The backend standardizes.

```text
camelCase JSON
snake_case database columns
ISO-8601 timestamps
UUID string representation
```

Null handling, date and time serialization, and unknown-field handling are defined explicitly.

Do not globally enable behavior that silently converts malformed client data into valid business state.

---

## 18. Database foundation

PostgreSQL is the authoritative transactional datastore.

P0 establishes.

```text
application -> PgBouncer -> PostgreSQL   (production and staged profiles)
application -> PostgreSQL                 (local direct profile)
```

Flyway owns all schema evolution.

No manual schema edits are permitted as part of the development workflow.

---

## 19. Flyway foundation rule

P0 creates the migration directory.

```text
backend/src/main/resources/db/migration/
```

The initial migration creates only foundation-level objects approved by the database specification.

The complete schema is added through its corresponding phases.

```text
P0  verifies migration mechanism
P1  creates identity schema
P2  creates academic schema
P3  creates content schema
P4  creates assessment schema
P5  creates delivery schema
P6  creates evaluation schema
P8  creates analytics schema
P9  creates commerce schema
P10 creates communication schema
P11 creates administration schema
P13 creates reporting-related schema
```

---

## 20. Flyway validation

CI executes migrations against a clean database.

Required check.

```text
Empty PostgreSQL
        |
        v
Flyway migrate
        |
        v
Application starts
        |
        v
Health passes
```

The agent also proves that an already migrated database restarts cleanly.

Migration execution is tested explicitly.

---

## 21. Database naming

Follow the frozen database standards.

```text
snake_case
singular table names
UUID primary keys
standard audit fields
standard status fields
```

Constraint names.

```text
pk_<table>
fk_<table>_<reference>
uk_<table>_<column>
idx_<table>_<column>
```

P0 establishes conventions but does not invent business tables.

---

## 22. Entity base class

Where the domain model requires a common base entity, P0 provides reusable base persistence support for.

```text
id
status
created_by
created_on
updated_by
updated_on
```

The base implementation does not force fields into tables that are not specified to use them. Mapping patterns keep persistence concerns reusable without creating an oversized inheritance hierarchy.

---

## 23. Auditing foundation

P0 creates the technical foundation for audit fields.

The implementation distinguishes entity audit metadata from `AuditLog` business and security history.

P0 does not implement all business audit events. It provides the infrastructure required for future modules to record them consistently.

---

## 24. JPA and Hibernate rules

P0 establishes Spring Data JPA, Hibernate, PostgreSQL dialect and configuration, and transaction management.

Rules.

- No entity exposure from controllers.
- No repository access from controllers.
- No business logic in repositories.
- No broad eager relationships.
- No unrestricted entity graph loading.
- Avoid N+1 query patterns.
- Transaction boundaries belong primarily in services.

These become enforced engineering rules in later modules.

---

## 25. Transaction foundation

Create baseline transaction configuration.

Default business transactions are service-oriented. P0 does not add generic transaction boundaries around every controller.

One small infrastructure integration transaction may be demonstrated only if required to validate configuration.

---

## 26. Connection pool strategy

Two distinct layers exist.

```text
Application connection pool
        |
        v
PgBouncer
        |
        v
PostgreSQL
```

The exact JDBC pool implementation follows normal Spring Boot conventions.

PgBouncer is a connection management component, not a substitute for application-side pool configuration.

P0 documents the relationship.

---

## 27. PgBouncer development strategy

Production path.

```text
Spring Boot
    |
    v
PgBouncer
    |
    v
PostgreSQL
```

Local development may support a direct database profile and a pooled profile.

Both profiles are documented.

Database migrations use a direct PostgreSQL connection where appropriate rather than routing migration tooling through the application traffic pooler.

Flyway does not depend on PgBouncer.

---

## 28. PgBouncer configuration boundary

Store PgBouncer configuration under.

```text
infrastructure/pgbouncer/
```

Configuration is environment-driven.

Never commit production passwords, production database URLs, or production secrets.

Use placeholders in repository configuration examples.

---

## 29. Redis foundation

Redis is a cache only.

Approved cached categories.

```text
Organization settings
Business rules
Lookup values
Roles and permissions
Exam masters
```

P0 creates Redis connection configuration, a cache abstraction, health visibility, and failure handling.

Do not cache student responses, payment state, official results, answer keys as a client-trusted source, or attempt submission state.

---

## 30. Redis failure behavior

The application does not crash merely because Redis is unavailable for optional caching.

Expected behavior.

```text
Cache hit
    |
    v
Return cached value

Cache miss
    |
    v
Read PostgreSQL
    |
    v
Optionally repopulate cache
```

When Redis is unavailable.

```text
Redis failure
    |
    v
Log controlled warning and metric
    |
    v
Read authoritative datastore
```

The feature continues whenever its business semantics permit it.

---

## 31. Spring Security foundation

P0 establishes the security framework.

The baseline uses Spring Security, JWT, RBAC, and secure password hashing.

P0 configures the authentication pipeline without implementing the complete Identity domain.

Required foundation concepts.

```text
SecurityFilterChain
JWT decoding and validation boundary
Authentication context
Unauthorized response
Forbidden response
Password hashing service and configuration
```

---

## 32. What P0 does not do in security

P0 does not add OAuth, SSO, 2FA, biometric authentication, device trust, or advanced proctoring.

P0 prepares extension points without implementing excluded security features.

---

## 33. Password handling

Use a secure password hashing mechanism supported by the selected Spring Security baseline.

Never store plaintext passwords, log passwords, or return password hashes through DTOs.

Never create test credentials that accidentally leak into production configuration.

Use explicit development-only configuration.

---

## 34. CORS

Configure CORS for trusted frontend origins.

Rules.

```text
No wildcard production origins
No broad credentialed wildcard configuration
Environment-specific origin configuration
```

Local development can use the local frontend origin.

---

## 35. CSRF

Because the API contract uses JWT-based REST authentication, the implementation documents its CSRF strategy explicitly rather than leaving framework defaults unexplained.

P0 includes an architecture decision record explaining authentication transport, CSRF exposure model, and browser credential model.

Security middleware is not disabled without documenting why.

---

## 36. API authentication boundary

P0 establishes public infrastructure endpoints and authenticated API endpoints.

Business authorization belongs to later Identity and feature modules.

Do not invent a generic `isAdmin = true` security mechanism.

---

## 37. Health endpoints

P0 exposes operational health through the approved Spring Boot health and actuator mechanism.

Health visibility distinguishes application, database, and Redis.

Do not expose secrets or internal infrastructure configuration through public health endpoints.

Public liveness and restricted detailed readiness views are separated where practical.

---

## 38. OpenAPI foundation

P0 establishes OpenAPI and Swagger generation.

Documentation includes API title, version, description, authentication scheme, standard error responses, and request correlation header.

Every later business endpoint is added to the same OpenAPI document.

---

## 39. API versioning

The API base path is `/api/v1`.

P0 establishes the routing convention without inventing resource endpoints.

Business APIs use `/api/v1`.

---

## 40. Validation foundation

Create a validation mechanism based on standard Spring validation.

P0 demonstrates.

```text
Request DTO
        |
        v
Field validation
        |
        v
Standard error response
```

Complex business validation is not placed in controller annotations alone. Later domain validation belongs in the service and domain layer.

---

## 41. Frontend baseline

Create a Next.js application using.

```text
Next.js
TypeScript
Tailwind CSS
shadcn/ui
TanStack Query
React Hook Form
Zod
Axios
```

The exact currently supported package versions may be selected by the implementation environment. The technology categories are frozen.

Selected versions are documented.

---

## 42. Frontend directory responsibilities

Recommended.

```text
app/          routes, pages, layouts
components/   reusable UI
features/     business feature modules
lib/          API client and utilities
hooks/        reusable hooks
schemas/      Zod schemas
types/        shared frontend DTO types
```

P0 keeps feature directories minimal. Do not create fake implementations of every future screen.

---

## 43. Frontend API client

Create one centralized Axios-based API client.

Responsibilities.

```text
Base URL
Request id
Authentication token handling
Common headers
Response normalization
Error normalization
```

The API client does not contain exam-specific behavior, business authorization, scoring, timer authority, or submission rules.

---

## 44. Frontend authentication foundation

P0 creates the structural foundation for authenticated API requests.

It may include authentication state abstraction, token handling boundary, protected-route foundation, and logout boundary.

The complete user and login product flow belongs to P1 Identity. The P0 frontend has a clean place for P1 to connect those flows.

---

## 45. TanStack Query foundation

Configure the application-level query client.

Provide standard defaults for retry behavior, stale time, and error handling.

Do not create arbitrary global retries that can duplicate critical commands.

Especially not for POST, submit, payment, or response save.

---

## 46. Axios retry rule

P0 does not introduce blind automatic retry for state-changing requests.

Later sensitive operations require idempotency according to the API contract.

Safe automatic retry is limited to explicitly safe or idempotent operations.

---

## 47. Zod foundation

Create Zod infrastructure for API response validation where appropriate, environment configuration, and shared configuration payloads.

The ExamEngine and CDUI contract later adds the delivery configuration schemas. P0 does not duplicate that schema.

---

## 48. Tailwind and shadcn/ui foundation

Establish Tailwind, shadcn/ui, global styles, and design token location.

P0 proves a minimal accessible component renders successfully.

P0 does not spend time building product-specific dashboards.

---

## 49. Accessibility baseline

P0 component examples establish semantic HTML, keyboard focus, labels, focus visibility, and accessible error messages.

Later student, staff, and admin experiences inherit this baseline.

---

## 50. Docker foundation

Create containers for backend, frontend, PostgreSQL, Redis, Nginx, and PgBouncer.

Production observability services may be represented through infrastructure configuration and local development profiles.

Not every service runs in every developer's default local stack.

---

## 51. Docker Compose profiles

Use profiles to keep local developer startup manageable.

Suggested conceptual groups.

```text
core          postgres, redis
app           backend, frontend
edge          nginx, pgbouncer
observability prometheus, grafana, loki
```

The exact Compose implementation can differ. The repository documents which profile is required for which workflow.

---

## 52. Local default

The easiest supported local command starts the minimum useful environment.

Conceptually.

```bash
docker compose up -d postgres redis
```

Then.

```bash
./mvnw spring-boot:run
npm run dev
```

A full integration environment has a documented Compose command.

---

## 53. Docker networking

Services communicate using Compose service names.

Do not hardcode `localhost` inside container-to-container configuration.

Use environment variables for database host, database port, Redis host, PgBouncer host, and API URL.

---

## 54. Nginx foundation

Nginx sits at the edge.

Production request flow.

```text
Internet
   |
   v
Nginx
   |
   +-- Next.js
   +-- Spring Boot API
```

Nginx configuration belongs under `infrastructure/nginx/`.

P0 establishes frontend routing, API routing, proxy headers, request ID forwarding, and basic request size limits.

Business routing decisions do not live in Nginx.

---

## 55. Static and protected file boundary

P0 establishes the folder and routing boundary for future protected files.

Do not expose arbitrary server filesystem paths.

The later API contract provides an authenticated file download boundary.

P0 ensures no default static mapping accidentally exposes uploads, application properties, environment files, database files, or logs.

---

## 56. File upload foundation

P0 does not implement question assets.

P0 establishes configuration names for maximum upload size, allowed content types, and storage provider mode.

Actual policy enforcement is implemented with the Content module.

---

## 57. Background job foundation

The architecture uses asynchronous processing for long-running operations.

P0 establishes worker package boundary, scheduler configuration boundary, job execution abstraction, and retry and error logging convention.

P0 does not create a separate job table. The general-purpose `background_job` table is created in its own phase.

Background operations may initially be represented by application-level scheduling and worker components.

---

## 58. Critical background workloads

The architecture anticipates reports, analytics, email, imports, bulk operations, cleanup, retry, scorecard generation, and attempt snapshot preparation.

The attempt snapshot preparation worker is especially important for delivery.

P0 only establishes the worker foundation. Actual snapshot generation is implemented in Delivery and Assessment phases.

---

## 59. Job safety

Every background operation has a defined safety property.

```text
Idempotent
or
Deduplicated
or
Safe to retry
```

No worker blindly repeats a non-idempotent transaction.

---

## 60. Logging foundation

Use structured application logs.

Minimum common fields.

```text
timestamp
level
service
environment
requestId
userId when available
module
operation
duration when relevant
errorCode
```

Never log passwords, JWT, payment secrets, raw authorization headers, sensitive request bodies, answer keys, or student answers when not required.

---

## 61. Log levels

Use ERROR, WARN, INFO, DEBUG.

Production does not run at broad DEBUG logging.

The default level is environment-specific.

---

## 62. Observability foundation

The observability stack uses.

```text
Metrics -> Prometheus -> Grafana
Logs    -> Loki       -> Grafana
```

P0 establishes the collection boundary.

P0 does not build dozens of business dashboards.

---

## 63. Core metrics

P0 exposes enough infrastructure and application metrics to prove HTTP request count, HTTP error count, HTTP latency, JVM health, database connection pool state, Redis connectivity, and background job execution counts.

Later delivery metrics extend this with start latency, response-save latency, submission latency, and snapshot readiness.

---

## 64. Monitoring and secrets

Monitoring configuration does not contain credentials in Git.

Use environment variables, secret stores, and deployment configuration.

The repository contains example configuration only.

---

## 65. Environment model

Minimum environments.

```text
local
test
staging
production
```

Configuration is environment-aware.

Do not branch business logic by environment.

Prefer externalized configuration.

---

## 66. Configuration rules

Configuration covers database, Redis, JWT, CORS, file storage, logging, observability, and external integrations.

Business rules that need relational consistency are not stored in unrestricted generic JSON configuration.

---

## 67. Environment files

Commit `.env.example`.

Do not commit `.env`, `.env.production`, or real secrets.

The exact secret-management implementation can be environment-specific.

---

## 68. Build reproducibility

P0 pins the major build tool versions.

Backend pins Java version, Maven wrapper, and Spring Boot version.

Frontend pins Node.js major version, package manager, and lockfile.

Infrastructure pins Docker Compose version expectations and container image tags.

Lockfiles are not removed to "fix" dependency issues.

---

## 69. Dependency policy

Every new dependency has a reason.

P0 does not add libraries simply because they are popular, convenient, or reduce one line of code.

For P0, every dependency is classified as required, optional, test-only, or infrastructure.

---

## 70. Dependency security

CI runs dependency checks appropriate to the chosen build tooling.

At minimum: dependency resolution, known vulnerability scan where available, and outdated dependency report as informational.

A vulnerable dependency directly used in security-sensitive code is reviewed before merge.

---

## 71. Testing strategy

P0 establishes the testing layers.

Backend: unit, integration, and API.

Frontend: component, schema, and API-client.

End-to-end infrastructure testing may be introduced in P0 at a very small smoke-test level.

Full assessment end-to-end belongs later.

---

## 72. Backend unit test baseline

At least one test proves common validation behavior, exception mapping, or utility behavior.

Do not fabricate domain tests before the domain exists.

---

## 73. Backend integration test baseline

Prove Spring Boot connecting to PostgreSQL using the approved test strategy.

Prefer a real PostgreSQL-compatible test environment where practical.

---

## 74. Database integration test

The P0 integration test verifies.

```text
Connection established
Migration applied
Application can query database
Transaction commits
Transaction rolls back when expected
```

No business tables beyond P0 scope are required.

---

## 75. Redis integration test

Verify application to Redis and cache set, cache get, cache miss, and Redis-unavailable behavior.

The final Redis test confirms that cache failure does not incorrectly become business-data loss.

---

## 76. API smoke test

Create an infrastructure-only endpoint or application info and health-compatible endpoint if needed by the implementation.

The smoke test verifies that the API starts, the HTTP response is valid, the standard envelope is valid where applicable, and the request ID is present.

Do not create fake domain endpoints just to inflate P0 coverage.

---

## 77. Frontend build test

CI proves `npm ci` and `npm run build` succeed and runs lint, type-check, and test according to the exact scripts established in `package.json`.

---

## 78. End-to-end smoke test

P0 may include a browser, Nginx, and Next.js flow, and a Next.js to Spring Boot flow.

A small health or information screen is sufficient.

P0 does not pretend that exam delivery already exists.

---

## 79. GitHub Actions

Create workflows for backend CI, frontend CI, and repository validation.

A unified CI workflow is also acceptable if failures remain clearly attributable.

---

## 80. Pull request gate

Every PR runs backend compile, backend unit tests, backend integration tests where configured, frontend install, frontend lint, frontend type-check, frontend tests, frontend build, and migration validation.

Infrastructure syntax validation is added where practical.

---

## 81. CI failure principle

Tests are not weakened to make CI green.

Forbidden.

```text
Skip test
Disable test
Remove assertion
Ignore compiler warning that indicates a real bug
Delete failing suite
```

The implementation is fixed or the failure is reported.

---

## 82. Code quality

P0 establishes the rules that future work follows.

Required principles.

```text
Single responsibility
Constructor injection
DTO-based API
Service-layer business logic
Repository-only persistence access
Meaningful naming
Small focused classes
Controlled dependency direction
No duplicate logic
```

---

## 83. Module dependency rule

The dependency direction.

```text
controller
   |
   v
service and application
   |
   v
repository
   |
   v
database
```

Within the modular monolith.

```text
module A
   |
   v
explicit application or service contract
   |
   v
module B
```

Avoid module A calling module B's repository. Avoid unrestricted JPA entity sharing.

---

## 84. Common module rule

The `common` package remains small.

It does not become a container for everything from every module.

Only genuinely cross-cutting infrastructure belongs there.

---

## 85. Architecture decision records

Create `docs/decisions/`.

P0 creates records for modular monolith choice, technology stack, JWT and RBAC security foundation, PostgreSQL as source of truth, Redis cache-only policy, PgBouncer connection management, configuration-driven UI boundary, and Markdown-LaTeX content boundary.

These records reference the frozen source decisions rather than duplicating the entire PRD.

---

## 86. Architecture change rule

Before changing database schema, API contract, domain model, technology stack, security architecture, exam engine contract, or CDUI contract, the affected task stops.

It produces reason, impact, affected documents, proposed change, migration impact, API impact, and test impact.

No silent architecture changes.

---

## 87. Repository agent instructions

Create a concise root-level `AGENTS.md`.

It states.

```text
Read docs before coding.
Follow frozen documents.
Do not invent database tables.
Do not change API contracts silently.
Do not hard-code exam names into frontend components.
Do not expose correct answers to students.
Do not generate snapshots synchronously from Start Exam.
PostgreSQL is source of truth.
Redis is cache only.
Backend is authoritative.
Use Flyway for database changes.
Tests are part of implementation.
Never commit secrets.
Never push directly to main.
Stop on architecture conflicts.
```

The file points agents to the more detailed project documents rather than copying all requirements into one file.

---

## 88. Agent operating modes

The repository supports three logical agent modes.

**Planning.** Reads documents and proposes a plan. No source changes.

**Implementation.** Modifies the repository within the approved task scope.

**Review.** Read-oriented. Checks requirements, architecture, security, tests, and diff.

A reviewer agent does not silently rewrite the implementation it is reviewing.

---

## 89. Standard task format

Every implementation task includes.

```text
Task id
Objective
Source documents
Scope
Out of scope
Required files
Required tests
Acceptance criteria
Forbidden changes
Verification commands
Expected final report
```

---

## 90. Agent final report

After each task, the agent reports.

```text
Implemented
Files changed
Database migrations
Tests added
Tests executed
Build result
Architecture deviations
Known issues
```

---

## 91. Repository search rule

Before editing a file, the agent inspects existing implementation, related tests, relevant documentation, and dependency callers.

Files are not regenerated blindly. Working code is not overwritten merely because an example is easier.

---

## 92. Diff discipline

At the end of every task.

```bash
git status
git diff --stat
git diff
```

The agent reviews the diff for unrelated changes, generated junk, secret files, dependency changes, accidental deletions, and formatting churn.

---

## 93. Generated files

Generated build output is not committed.

Typical exclusions.

```text
target/
node_modules/
.next/
coverage/
logs/
.env
```

The `.gitignore` is generated from the selected toolchains and verified.

---

## 94. Documentation navigation

`README.md` provides project purpose, architecture documents, local setup, build commands, test commands, Docker commands, repository layout, branch rules, and agent workflow.

A new engineer does not need private conversation history to understand how to start the repository.

---

## 95. Local developer quick start

The README provides one canonical onboarding path.

Conceptually.

```bash
git clone <repo>
cd exam-platform

docker compose up -d postgres redis

cd backend
./mvnw test
./mvnw spring-boot:run
```

In another terminal.

```bash
cd frontend
npm ci
npm run dev
```

The exact commands may be adjusted to the generated project. They are tested before P0 is declared complete.

---

## 96. Full integration startup

Document the full stack.

```text
PostgreSQL
Redis
PgBouncer
Spring Boot
Next.js
Nginx
```

The exact command is tested by CI or a documented local verification step.

---

## 97. Environment verification script

Create a lightweight validation script under `infrastructure/` or `scripts/`.

It checks Java version, Maven availability, Node version, Docker availability, database connectivity, Redis connectivity, and required environment variables.

It reports actionable errors.

---

## 98. No hidden manual steps

P0 is not complete if setup requires manual SQL editing, manual dependency download, manual classpath changes, or manual source code changes.

Required setup steps are documented and reproducible.

---

## 99. Database passwords for local development

Use development-only values through environment configuration.

Local credentials are not treated as production secrets. Production-style secrets are not hardcoded into source code.

---

## 100. Time and time zone foundation

The platform uses timestamp-aware types.

Backend infrastructure uses timezone-aware Java time types and persists timestamps consistently.

The runtime and storage convention is documented.

Exam schedules and attempt timing later use authoritative timestamps.

`LocalDateTime` is not used as a substitute for an instant when a globally comparable event timestamp is required.

---

## 101. UUID foundation

The project uses UUID identifiers.

P0 establishes consistent UUID handling in Java, JSON, JPA, PostgreSQL, and tests.

No integer auto-increment identity scheme is introduced for primary keys unless a source specification explicitly calls for it.

---

## 102. Serialization compatibility

UUID and timestamp fields round-trip consistently.

```text
database
 -> entity
 -> DTO
 -> JSON
 -> frontend
```

Basic tests are created for this.

---

## 103. Error handling for unknown routes

Spring Boot returns a controlled API error for unsupported API routes.

Framework internals are not leaked.

The frontend converts API errors into a consistent user-safe representation.

---

## 104. Frontend error boundary

P0 adds an application-level error boundary and not-found handling.

The goal.

```text
Unexpected UI error
       |
       v
Safe fallback
       |
       v
Request correlation identifier where useful
```

Stack traces are not exposed.

---

## 105. Loading and empty state foundation

Create reusable patterns for loading, error, empty, and success.

This becomes important for later operations screens.

---

## 106. API environment configuration

The frontend does not hardcode the API host.

Use environment configuration.

Conceptually `NEXT_PUBLIC_API_BASE_URL` if compatible with the selected Next.js architecture.

Secrets are not placed in `NEXT_PUBLIC_*` variables.

---

## 107. Backend environment configuration

Use environment-driven configuration for database URL, database user, database password, Redis URL, JWT signing and verification configuration, CORS origins, file storage settings, logging, and profile.

The exact secret strategy is environment-specific.

---

## 108. Build profiles

At minimum support local, test, staging, and production.

Spring profiles alter infrastructure configuration, not business behavior.

---

## 109. Testcontainers and integration environment

Where practical, use disposable PostgreSQL and Redis-compatible containers for integration testing.

If the project chooses not to use Testcontainers, the implementation documents the alternative.

Unit tests do not introduce external services that make CI nondeterministic.

---

## 110. Backend package compilation check

P0 ends with all intended packages compiling, no circular module dependency, and no dead infrastructure configuration.

Unused placeholder packages are removed rather than left as empty scaffolding.

---

## 111. Frontend type check

P0 enforces `npx tsc --noEmit` or the repository's equivalent script.

TypeScript strictness is not loosened solely to make P0 compile.

---

## 112. Linting

Configure linting for backend and frontend using the official ecosystem tooling.

The goal is deterministic CI enforcement.

---

## 113. Formatting

Select and document canonical formatting for Java, TypeScript, JSON, YAML, Markdown, and SQL.

Agents use repository formatters rather than introducing inconsistent local formatting.

---

## 114. SQL formatting

Flyway SQL is reviewable, ordered, deterministic, and free of environment-specific secrets.

Ad-hoc development SQL is not placed into production migration files.

---

## 115. YAML safety

Configuration YAML is checked for incorrect indentation, unquoted special characters, secret leakage, and environment mismatch.

CI fails on malformed configuration where practical.

---

## 116. Nginx safety

Nginx configuration is tested before merge.

The CI or local verification runs the equivalent of `nginx -t` inside the selected container or image where feasible.

---

## 117. Docker health checks

Services provide health checks where the runtime supports them.

At minimum: PostgreSQL, Redis, and Spring Boot.

Nginx and PgBouncer receive health or readiness checks appropriate to their role.

---

## 118. Startup dependency rule

Container started does not equal service ready.

The stack uses health-aware startup where supported.

Spring Boot fails clearly if required infrastructure is unavailable at startup.

Optional dependencies such as Redis caching are handled according to the failure behavior defined earlier.

---

## 119. Graceful shutdown

Spring Boot supports graceful shutdown.

This is especially important before later implementing active exam sessions, background workers, and response persistence.

P0 proves the application can stop without abruptly corrupting state in a simple smoke test.

---

## 120. Background worker shutdown

P0 scheduler and worker infrastructure does not terminate mid-operation without a defined behavior.

The full delivery and snapshot worker behavior is defined later.

P0 only establishes controlled worker lifecycle, logging, failure handling, and shutdown.

---

## 121. Request limits

Nginx and Spring Boot establish reasonable request-size and timeout configuration boundaries.

Extremely large global limits are not set for convenience.

Later content-upload and report endpoints can define specific limits.

---

## 122. API timeout principle

A global timeout is not so aggressive that legitimate long-running operations are killed.

Long-running operations do not remain synchronous merely because the timeout was increased.

Use asynchronous job APIs for operations identified by the API contract as long-running.

---

## 123. Performance instrumentation

P0 makes it possible to measure request latency, database latency, Redis latency, application CPU and memory, and connection pool behavior.

The full concurrent-user exam workload test comes later.

---

## 124. P0 performance smoke gate

P0 demonstrates that a simple health or information request is measurable and that observability receives the timing.

P0 smoke results do not claim that the full-scale performance requirement is satisfied.

---

## 125. Security smoke gate

P0 proves protected routes reject unauthenticated callers, invalid credentials produce 401, forbidden access produces 403, sensitive headers are not logged, and secrets do not appear in the repository.

Full RBAC is P1 Identity.

---

## 126. Secret scanning

CI includes repository secret scanning where the chosen GitHub setup supports it.

At minimum, manual verification confirms that no passwords, tokens, private keys, or production URLs containing credentials are committed.

---

## 127. Dependency direction gate

The P0 repository does not contain controller to repository access, controller to database entity mutation, frontend to database, or common to feature-specific business implementation.

These are architectural review failures.

---

## 128. First example flow

P0 proves one tiny end-to-end non-business flow.

```text
Browser
  |
  v
Nginx
  |
  v
Next.js
  |
  v
Axios
  |
  v
Spring Boot
  |
  v
Health or information response
  |
  v
Next.js renders result
```

This confirms that the foundational boundaries connect correctly.

---

## 129. First database flow

Prove Spring Boot connecting through JPA to PostgreSQL against the P0 schema.

---

## 130. First cache flow

Prove Spring Boot connecting through the cache abstraction to Redis.

Prove the fallback path where Redis is unavailable and the application continues for cache-optional behavior.

---

## 131. First CI flow

Push a P0 branch.

CI runs checkout, backend build, backend tests, migration validation, frontend build, frontend checks, and infrastructure configuration validation.

The exact job parallelization is optional.

---

## 132. Release artifact

P0 produces at least a backend artifact or container, a frontend artifact or container, infrastructure configuration, and documentation.

The exact artifact publishing mechanism may remain local until later.

---

## 133. CI matrix

Where practical, Java, Node, PostgreSQL, and Redis versions are explicit.

Tests do not run against an unbounded "latest."

---

## 134. Container image strategy

Use explicit image tags.

Production deployment definitions do not use `latest`.

Development Compose may use a documented moving tag only when there is a deliberate reason.

---

## 135. Database migration review

Every migration added later answers:

```text
Which frozen requirement does this implement?
Which table or entity does it affect?
What constraints are required?
What indexes are justified?
Is historical data affected?
Is rollback behavior understood?
```

P0 creates this checklist.

---

## 136. P0 and the full table inventory

The frozen relational model contains the approved examination and platform entities.

P0 does not modify that inventory.

The later implementation adds the approved tables in dependency order.

No new table is created solely for CDUI, content rendering, PgBouncer, or snapshot workers.

P0 preserves this discipline.

---

## 137. P0 and the exam engine

P0 creates only the technical location and dependency boundary for the future ExamEngine.

P0 does not implement TCS_ION, IBPS, NTA, UPSC, RAILWAY, or CUSTOM behavior.

The exam engine contract is implemented after P1 when the Identity boundary and core configuration foundation exist.

The first actual engine implementation can be `CUSTOM`.

---

## 138. P0 and CDUI

P0 may create frontend configuration directories, Zod schema location, feature boundary, and provider location.

P0 does not build the complete assessment screen. That belongs to Delivery.

---

## 139. P0 and content rendering

P0 may establish the Markdown and LaTeX rendering package boundary, the security dependency boundary, and the test fixture location.

P0 does not implement the full question renderer before the Content slice.

The canonical format remains `MARKDOWN_LATEX_V1` with `\( ... \)`, `\[ ... \]`, and `[asset: uuid]`.

Raw HTML and executable MDX remain prohibited.

---

## 140. P0 and attempt snapshot

P0 establishes worker infrastructure.

P0 does not create `AttemptQuestion` or `AttemptQuestionOption` rows.

Those belong to the Delivery and Assessment implementation.

The mandatory runtime property remains that Start Exam does not perform snapshot generation.

---

## 141. P0 and PgBouncer

P0 creates the infrastructure boundary and configuration.

The actual optimal pool sizes are not frozen yet.

Pool sizes are not hardcoded as if the concurrent-user target has already been validated.

Pool sizing is an engineering tuning exercise supported by load testing.

---

## 142. P0 and the concurrency target

P0 does not claim to meet the concurrent-user requirement.

P0 creates measurement capability, connection infrastructure, observability, a repeatable environment, and the CI foundation.

The full concurrency validation belongs to the later scale and hardening gate.

---

## 143. Acceptance criteria summary

P0 is accepted only when all of the following are true.

```text
[ ] Repository structure exists
[ ] AGENTS.md exists
[ ] README onboarding works
[ ] Backend starts
[ ] Frontend starts
[ ] PostgreSQL connects
[ ] Flyway runs successfully
[ ] Redis connects
[ ] Redis fallback behavior verified
[ ] PgBouncer configuration exists and is testable
[ ] Nginx routes correctly
[ ] API error handling works
[ ] Request ID works
[ ] OpenAPI works
[ ] Security foundation works
[ ] Health checks work
[ ] Structured logs work
[ ] Metrics foundation works
[ ] Backend tests pass
[ ] Frontend tests and checks pass
[ ] Migration validation passes
[ ] Docker startup works
[ ] CI passes
[ ] No secrets committed
[ ] No business-domain features invented
[ ] No frozen architecture modified silently
```

---

## 144. P0 definition of done

P0 is complete only when the following hold.

### Functional

```text
Application boots
Database connects
Redis connects
API responds
Frontend renders
Nginx routes
PgBouncer path verified
```

### Technical

```text
Build reproducible
Tests reproducible
Migrations reproducible
Docker reproducible
CI reproducible
```

### Security

```text
JWT boundary prepared
Unauthorized requests handled
Forbidden requests handled
Secrets protected
Logs sanitized
```

### Operations

```text
Health checks available
Metrics available
Structured logs available
Correlation id available
```

### Development

```text
AGENTS.md available
Task protocol documented
Architecture change rule enforced
PR workflow documented
Verification commands documented
```

---

## 145. P0 task breakdown

Execute in bounded tasks.

### P0-01 - Repository bootstrap

Create repository layout, README, CONTRIBUTING, SECURITY, AGENTS, docs folders, infrastructure folders, and `.gitignore`.

Verify Git status is clean except intended files.

### P0-02 - Backend bootstrap

Create Spring Boot project.

Verify `./mvnw test` and `./mvnw package`.

### P0-03 - Common API infrastructure

Implement ApiResponse, ApiErrorResponse, PageResponse, GlobalExceptionHandler, and RequestIdFilter.

Add tests.

### P0-04 - Persistence bootstrap

Implement PostgreSQL datasource, JPA, Hibernate, Flyway, and transaction configuration.

Verify clean migration.

### P0-05 - Redis bootstrap

Implement Redis connection, cache abstraction, and fallback behavior.

Add integration tests.

### P0-06 - Security bootstrap

Implement Spring Security, JWT boundary, password encoder, and 401 and 403 handling.

Do not implement complete Identity.

### P0-07 - Frontend bootstrap

Implement Next.js, TypeScript, Tailwind, shadcn/ui, Axios, TanStack Query, React Hook Form, and Zod.

Verify build and type-check.

### P0-08 - Frontend API boundary

Implement Axios client, request id, common API error handling, and environment configuration.

Add tests.

### P0-09 - Docker

Implement PostgreSQL, Redis, PgBouncer, backend, and frontend containers.

Verify startup.

### P0-10 - Edge infrastructure

Implement Nginx and PgBouncer.

Verify configuration and routing.

### P0-11 - Observability

Implement structured logs, health, metrics, Prometheus configuration, Grafana boundary, and Loki boundary.

### P0-12 - CI/CD

Implement backend CI, frontend CI, migration check, and infrastructure validation.

### P0-13 - Security and secret validation

Run secret scan, dependency check, and configuration review.

### P0-14 - Full smoke test

Run build, test, migration, Docker, Nginx, PgBouncer, Redis, health, frontend, and CI-equivalent commands.

### P0-15 - Final review

A second review pass checks architecture, dependency direction, security, repository hygiene, Docker, CI, and documentation.

Only then is human approval requested.

---

## 146. Recommended task prompt

The following is the recommended initial implementation prompt.

```text
You are implementing P0 of the Smart Competitive Examination Platform.

Read the full specification package before modifying the repository.

Objective:
Implement P0 Foundation only.

Do not implement business features.

Do not invent tables.

Do not invent API resources.

Do not modify frozen architecture.

Do not hard-code exam names into frontend code.

Do not create a synchronous exam snapshot path.

Do not expose secrets.

Do not commit secrets.

Do not bypass Git branch protection.

Required outcome:
A reproducible modular-monolith repository in which backend, frontend,
PostgreSQL, Redis, PgBouncer and Nginx have documented integration
boundaries; CI builds and tests the repository; API and security
foundations exist; and the project is ready for P1 Identity.

Execution rules:

1.  Inspect the repository before editing.
2.  Build one bounded task at a time.
3.  Run tests after each meaningful change.
4.  Keep commits small and coherent.
5.  Review git diff before finishing.
6.  If a frozen contract is insufficient, STOP and report the ambiguity.
7.  Do not solve architecture gaps by adding undocumented schema or API elements.
8.  Use environment configuration for infrastructure settings.
9.  Keep PostgreSQL authoritative.
10. Keep Redis cache-only.
11. Keep frontend non-authoritative.
12. Document every material implementation choice.

At the end report:

- Files changed
- Commands executed
- Test results
- Docker result
- Migration result
- CI result
- Architecture deviations
- Unresolved issues
```

---

## 147. What requires human review

The implementation stops for human review when it encounters schema conflict, API conflict, domain conflict, security architecture conflict, exam engine contract conflict, CDUI contract conflict, technology substitution, new persistent entity requirement, or breaking API requirement.

It does not stop for routine implementation choices such as class naming, method extraction, test naming, internal utility location, or standard Spring configuration, provided they remain inside the frozen architecture.

---

## 148. Human review checklist

At the end of P0, the human reviewer inspects the highest-value points.

```text
[ ] Repository structure matches architecture
[ ] No unexpected technologies added
[ ] No business scope leaked into P0
[ ] Security boundaries are sane
[ ] PostgreSQL remains source of truth
[ ] Redis is cache-only
[ ] PgBouncer boundary is correct
[ ] Nginx boundary is correct
[ ] API conventions are consistent
[ ] CI actually fails on broken builds and tests
[ ] No secrets are committed
[ ] Documentation is usable by a fresh engineer and by an implementation agent
```

---

## 149. P0 evidence package

The implementation leaves evidence for.

```text
Build logs
Test summary
Migration success
Docker startup result
Health endpoint result
Frontend build result
Nginx validation
PgBouncer validation
Redis validation
CI result
Secret scan result
```

Evidence is stored under `docs/testing/p0/`.

Secrets and large transient logs are not stored in Git.

---

## 150. P0 exit gate

P0 exits into P1 only when.

```text
                    P0
                     |
             Foundation verified
                     |
          +----------+----------+
          |                     |
        PASS                    FAIL
          |                     |
          v                     v
      P1 Identity            Fix P0
```

There is no exception for "the code mostly works."

---

## 151. P1 readiness criteria

The repository is ready for P1 Identity when Spring Boot works, Next.js works, PostgreSQL works, Redis works, PgBouncer works, Docker works, CI works, the security foundation works, API conventions work, request correlation works, OpenAPI works, developer onboarding works, and agent rules work.

P1 then implements Organization, User, Role, Permission, UserRole, RolePermission, and LoginSession without having to rebuild infrastructure.

---

## 152. P0 anti-patterns

Reject any P0 implementation that contains:

### Business logic in infrastructure

```text
Nginx deciding exam state
Docker deciding authorization
Frontend deciding scoring
```

### Generic configuration engine

```text
JSON blob -> arbitrary code
```

### Direct SQL in controllers

```text
Controller -> JDBC or SQL
```

### JPA entity API exposure

```text
Entity -> JSON
```

### Redis as source of truth

```text
Redis -> official state
```

### Hidden manual setup

```text
clone -> ask developer to edit code -> ask developer to run secret SQL
```

### Secret committed to Git

Immediate failure.

---

## 153. P0 architectural invariants

The following remain true.

```text
PostgreSQL = transactional source of truth
Redis = cache only
Backend = authorization and business authority
Frontend = presentation and interaction client
Published examination artifacts = immutable
Flyway = schema change authority
ExamEngine = rule interpretation
Delivery = runtime execution
CDUI = declarative capability-driven UI
Start Exam is not snapshot generation
PgBouncer = connection management, not business state
Nginx = edge routing, not application logic
```

---

## 154. P0 and future scale

P0 makes future scale testing possible without prematurely implementing scale-specific architecture.

The future workload includes concurrent users, high response-save concurrency, submission burst, result generation, and snapshot preparation.

The foundation avoids infrastructure decisions that prevent horizontal scaling.

The application remains stateless at the HTTP layer where the architecture requires stateless APIs.

---

## 155. P0 and future multi-organization expansion

The current release is single-organization.

P0 does not add full multi-tenant architecture.

P0 avoids hard-coding an organization name everywhere or baking global singleton business values into Java classes.

Organization context belongs to the later Identity and Administration implementation.

The foundation remains structurally reusable without prematurely implementing multi-tenancy.

---

## 156. P0 and AI coding cost control

Because the repository may be developed with implementation agents, P0 minimizes unnecessary model context.

Agents rely on AGENTS.md, the task specification, the relevant domain document, the relevant API section, the relevant schema section, and existing code and tests.

Every agent task does not need to ingest every document when the task is already narrowly scoped.

The coordinator provides the minimal relevant document set.

---

## 157. Context efficiency rule

Keep durable engineering knowledge in repository files.

Recommended.

```text
AGENTS.md
docs/architecture/
docs/decisions/
docs/testing/
docs/implementation-status.md
```

Do not rely on chat history, memory, or one-off prompts as the only source of project truth.

---

## 158. Agent handoff rule

When one task finishes, the next task determines what changed, why it changed, what remains, what failed, and what is intentionally deferred from repository evidence.

This prevents repeated rediscovery and reduces token consumption.

---

## 159. P0 implementation status file

Create `docs/implementation-status.md` with at least.

```text
Project version
Current phase
Completed tasks
In progress
Blocked
Deferred
Latest verified commit
Latest CI result
Known risks
Architecture deviations
```

Update this file at the end of each milestone.

---

## 160. P0 final verification command set

The exact scripts depend on the generated repository. The final verification is equivalent to.

```bash
# Backend
./mvnw clean verify

# Frontend
npm ci
npm run lint
npx tsc --noEmit
npm test
npm run build

# Infrastructure
docker compose config

# Migration
clean PostgreSQL and Flyway migrate

# Edge
nginx -t

# Runtime
docker compose up -d
health checks
```

The actual commands and results are recorded rather than claimed.

---

## 161. Final P0 decision

P0 is intentionally small, reproducible, secure, observable, testable, and friendly to automated implementation.

P0 is not intended to demonstrate product-market fit. It is intended to make the product development process reliable enough that later phases proceed quickly without repeatedly rebuilding infrastructure.

---

## 162. Final engineering principle

> Build the rails once, verify them aggressively, then move quickly inside those rails.

Architecture and acceptance boundaries are approved by humans. Implementation is performed by the team and by implementation agents. CI detects regressions. Review challenges the implementation. Git provides reversibility. The repository remains the durable engineering source of truth.

---

## 163. Document status

**Status:** IMPLEMENTATION-READY foundation specification.

**Depends on:** Master PRD, Domain Model, Technical Design, Database Schema, API Contract, Architecture Freeze, Development Roadmap, ExamEngine and CDUI Contract.

**Next implementation slice:** P1 Identity and Security.

No P1 business implementation begins until the P0 exit gate is satisfied.