# Smart Competitive Examination Platform
## Technical Design Document

**Version:** 1.5
**Status:** FROZEN
**Architecture:** Modular Monolith
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Connection Pooling:** PgBouncer
**Migration:** Flyway
**API:** REST + JSON
**API Documentation:** OpenAPI / Swagger
**Containerization:** Docker
**Organization Scope:** Single Organization per Deployment
**Content Rendering:** MARKDOWN_LATEX_V1 + Unified/Remark + KaTeX SSR + Rehype Sanitization

---

## 1. Document purpose

This document translates the product requirements and domain model into an implementation-oriented technical architecture.

It defines system architecture, technology stack, backend module structure, frontend architecture, domain boundaries, API architecture, security architecture, database architecture, transaction management, caching, background processing, file storage, exception handling, logging, monitoring, deployment, testing, coding standards, and development rules.

The objective is to give the engineering team a clear technical baseline before implementation begins.

---

## 2. Architecture principles

The system follows these principles.

1. Modular monolith first.
2. Clear domain boundaries.
3. API-first development.
4. Database ownership by domain.
5. Business logic inside services and domain modules.
6. No unnecessary microservices.
7. Configuration over hardcoding.
8. Published examination data is immutable.
9. Security is implemented at every layer.
10. Transactional data remains relational.
11. JSONB is used only for flexible configuration.
12. Background processing is used for long-running operations.
13. APIs remain stateless.
14. All important operations are auditable.
15. The architecture permits future extraction into microservices.
16. Async preparation is separated from latency-critical runtime paths.

---

## 3. High-level architecture

The deployment architecture is a web-first, modular-monolith architecture.

The request path is:

```text
Browser / Desktop / Tablet
            |
          HTTPS
            |
            v
+-------------------------------+
|             NGINX             |
| Reverse Proxy / TLS / Routing |
+---------------+
                |
        +-------+--------+
        |                |
        v                v
+--------------+  +-----------------------------+
|   Next.js    |  |      Spring Boot 4          |
|   Frontend   |  |    Modular Monolith         |
+--------------+  |                             |
                  | Identity                    |
                  | Academic                    |
                  | Content                     |
                  | Assessment                  |
                  | Delivery                    |
                  | Evaluation                  |
                  | Analytics                   |
                  | Commerce                    |
                  | Communication               |
                  | Administration              |
                  +--------------+--------------+
                                 |
                    +------------+
                    |            |             |
                    v            v             v
             +------------+ +----------+ +-------------+
             | PgBouncer  | |  Redis   | | Object      |
             |            | |  Cache   | | Storage     |
             +-----+------+ +----------+ | Local / S3  |
                   |                     +-------------+
                   v
             +------------+
             | PostgreSQL |
             | Source of  |
             | Truth      |
             +------------+

                         +----------------------+
                         | Background Jobs      |
                         | Reports / Analytics  |
                         | Email / Imports      |
                         | Bulk / Cleanup       |
                         | Snapshot Preparation |
                         +----------------------+
```

### 3.1 Frontend boundary

Next.js is the single web frontend and contains:

- Public pages
- Student portal
- Operations workspace
- Administration portal
- Authentication screens
- Assessment delivery interface

The frontend communicates with the backend through the versioned REST API.

```text
Next.js
   |
   | REST / JSON
   v
Spring Boot 4
```

The frontend is not an authorization boundary.

### 3.2 Reverse proxy boundary

Nginx sits in front of both the Next.js frontend and the Spring Boot API.

```text
Internet
   |
   v
 Nginx
   |
   +-- Next.js
   |
   +-- Spring Boot API
```

Nginx may provide HTTPS termination, routing, request limits, static asset handling, and basic proxy configuration.

### 3.3 Backend module boundary

Spring Boot is one deployable application. Business domains remain separate modules.

```text
Spring Boot 4
|
+-- Identity
+-- Academic
+-- Content
+-- Assessment
+-- Delivery
+-- Evaluation
+-- Analytics
+-- Commerce
+-- Communication
+-- Administration
+-- Infrastructure
```

These are architectural modules, not separate services.

Each module owns its business logic and persistence access. Modules communicate through explicit service and application contracts rather than unrestricted access to another module's repositories or JPA entities.

### 3.4 Primary business flow

The central examination flow.

```text
Exam Configuration
        |
        v
Blueprint
        |
        v
Assessment
        |
        v
Delivery
        |
        v
Evaluation
        |
        v
Result
        |
        v
Analytics
```

At the transaction level.

```text
Question
   |
   v
Assessment
   |
   v
Attempt
   |
   v
Evaluation
   |
   v
Result
   |
   v
Analytics
```

### 3.5 Commerce flow

Commerce is separated from assessment structure.

```text
Catalog
   |
   v
Access Package
   |
   v
Purchase
   |
   v
Payment
   |
   v
Access Grant
   |
   v
Assessment Authorization
```

Payment providers are isolated behind a payment gateway interface.

```text
Commerce Module
      |
Payment Gateway Interface
      |
Provider Adapter
      |
External Payment Gateway
```

AccessGrant remains the authorization source of truth.

### 3.6 Background processing boundary

Long-running work must not block normal HTTP requests.

```text
HTTP Request
     |
Validate
     |
Create Job
     |
202 Accepted
     |
Background Worker
     |
Job Status / Result
```

Background work includes:

- Report generation
- Analytics aggregation
- Email delivery
- Large imports
- Bulk operations
- Attempt snapshot preparation
- Scorecard generation
- Cleanup
- Retryable asynchronous work

Job state is persisted in the `background_job` table for reports, imports, bulk operations, analytics refresh, and scorecards. Attempt snapshot preparation is tracked on the `student_attempt` record itself, not in `background_job`.

### 3.7 Observability boundary

Metrics and logs are separate flows.

```text
Spring Boot Application
        |
        +-- Metrics --> Prometheus --> Grafana
        |
        +-- Logs    --> Loki       --> Grafana
```

The operational observability stack is separate from the business Administration module.

### 3.8 Data responsibility

PostgreSQL is the authoritative transactional source of truth.

Redis is a supporting cache and must not become authoritative for student responses, payment transactions, results, answer keys, access grants, or audit records.

Object storage holds files such as question assets, profile images, organization assets, reports, and imported files.

---

## 4. Why modular monolith

The system uses a modular monolith rather than microservices.

The application is deployed as one backend application but internally separated into business modules.

```text
One deployable application
        |
        +-- Identity
        +-- Academic
        +-- Content
        +-- Assessment
        +-- Delivery
        +-- Evaluation
        +-- Analytics
        +-- Commerce
        +-- Communication
        +-- Administration
        +-- Infrastructure
```

This provides lower operational complexity, easier local development, simpler transactions, easier debugging, faster initial development, fewer distributed-system problems, and clear future migration boundaries.

The module boundaries must be strong enough that individual modules can be extracted later if required.

---

## 5. Future microservice compatibility

The architecture should not prematurely introduce microservices. However, boundaries must be designed so that future extraction is possible.

Potential future services:

- Identity service
- Content service
- Assessment service
- Delivery service
- Evaluation service
- Analytics service
- Commerce service
- Notification service
- Reporting service

No module should depend on another module's internal implementation.

---

## 6. Technology stack

### 6.1 Backend

| Technology | Purpose |
|---|---|
| Java 25 | Backend language and runtime |
| Spring Boot 4 | Application framework |
| Spring Security | Authentication and authorization |
| Spring Data JPA | Persistence abstraction |
| Hibernate | ORM |
| PostgreSQL | Primary database |
| Redis | Cache |
| PgBouncer | Production connection pooling |
| Flyway | Database migrations |
| OpenAPI | API contract |
| Swagger UI | API exploration |
| Docker | Containerization |

Java 21 may be used instead if organizational standards require it.

### 6.2 Frontend

| Technology | Purpose |
|---|---|
| Next.js | Web application framework |
| TypeScript | Type safety |
| Tailwind CSS | Styling |
| shadcn/ui | UI component foundation |
| TanStack Query | Server state |
| React Hook Form | Forms |
| Zod | Client-side schema validation |
| Axios | API client |

### 6.3 Content rendering

| Technology | Purpose |
|---|---|
| Unified / Remark-compatible pipeline | Markdown to AST |
| KaTeX | Server-side math rendering |
| Rehype-compatible sanitization | HTML safety |
| Custom asset shortcode resolver | `[asset: uuid]` |

### 6.4 Infrastructure

| Technology | Purpose |
|---|---|
| GitHub | Source control |
| GitHub Actions | CI/CD |
| Docker / Docker Compose | Containerization |
| Nginx | Edge reverse proxy |
| Prometheus | Metrics |
| Grafana | Dashboards |
| Loki | Log aggregation |

The exact cloud provider is intentionally not hardcoded.

---

## 7. Backend project structure

Recommended package root: `com.company.exam`

```text
src/
  main/
    java/
      com/company/exam/
        common/
        config/
        identity/
        academic/
        content/
        assessment/
        delivery/
        evaluation/
        analytics/
        commerce/
        communication/
        administration/
        infrastructure/
    resources/
      application.yml
      application-dev.yml
      application-test.yml
      application-prod.yml
      db/
        migration/
```

---

## 8. Module internal structure

Each business module follows the same structure.

```text
content/
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

Example.

```text
content/
  controller/
    QuestionController.java
    QuestionTemplateController.java
  service/
    QuestionService.java
    QuestionVersionService.java
  repository/
    QuestionRepository.java
    QuestionVersionRepository.java
  entity/
    Question.java
    QuestionVersion.java
  dto/
    QuestionCreateRequest.java
    QuestionResponse.java
    QuestionVersionResponse.java
  mapper/
  validation/
  exception/
  util/
```

---

## 9. Common module

The common module contains genuinely shared infrastructure.

```text
common/
  entity/
  dto/
  exception/
  enum/
  audit/
  validation/
  util/
  response/
```

Examples: BaseEntity, ApiResponse, ApiError, PageResponse, GlobalExceptionHandler, CommonEnums.

The common module remains small. Business logic is not placed into `common` merely to avoid deciding which domain owns it.

---

## 10. Configuration module

```text
config/
  SecurityConfig
  JwtConfig
  RedisConfig
  DatabaseConfig
  OpenApiConfig
  SchedulerConfig
  StorageConfig
  WebConfig
```

Configuration must not contain business logic.

---

## 11. Domain module dependencies

Preferred dependency direction.

```text
common
   ^
domain modules
   ^
application services
   ^
controllers
```

Conceptually.

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
Database
```

Cross-module access occurs through defined service and application interfaces.

---

## 12. Dependency rules

Allowed.

```text
Assessment
    |
    v
Content service
```

Avoid.

```text
Assessment
    |
    v
ContentRepository
```

The Assessment module does not directly manipulate Content persistence.

---

## 13. Entity design

JPA entities represent persistence state. They are not exposed through APIs.

```text
Entity
   |
   v
Mapper
   |
   v
DTO
   |
   v
API
```

Never expose JPA entities directly from REST controllers as the standard pattern.

---

## 14. DTO architecture

DTO categories.

```text
Create Request
Update Request
Search Request
Response DTO
Summary DTO
Detail DTO
```

This prevents database structure from becoming the public API contract.

---

## 15. Mapping

Mapping is explicit and predictable.

```text
Request DTO
    |
    v
Mapper
    |
    v
Entity
```

and.

```text
Entity
    |
    v
Mapper
    |
    v
Response DTO
```

MapStruct may be used to reduce repetitive mapping code.

---

## 16. API architecture

The API is REST-based.

Base URL: `/api/v1`

Examples.

```text
/api/v1/auth/login
/api/v1/users
/api/v1/exams
/api/v1/questions
/api/v1/assessments
/api/v1/attempts
/api/v1/results
/api/v1/reports
/api/v1/admin/system-health
```

---

## 17. API versioning

The first API version is `v1`.

```text
/api/v1/questions
```

Future breaking API changes create `/api/v2/...` rather than silently changing V1 behaviour.

---

## 18. HTTP methods

| Method | Purpose |
|---|---|
| GET | Retrieve |
| POST | Create or execute action |
| PUT | Full update |
| PATCH | Partial update where justified |
| DELETE | Archive or delete according to business rules |

DELETE does not imply physical deletion. For historical entities it normally means archive or inactivate.

---

## 19. API response standard

Success.

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": {}
}
```

Collection.

```json
{
  "success": true,
  "message": "Questions retrieved successfully",
  "data": [],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

---

## 20. Error response

Standard error envelope.

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "code": "INVALID_EMAIL",
      "message": "Email address is invalid"
    }
  ],
  "timestamp": "2026-10-07T10:00:00Z",
  "path": "/api/v1/questions"
}
```

Errors are understandable to clients without exposing internal implementation details.

---

## 21. Error categories

```text
VALIDATION_ERROR
BUSINESS_ERROR
AUTHENTICATION_ERROR
AUTHORIZATION_ERROR
NOT_FOUND
CONFLICT
INVALID_STATE
RATE_LIMITED
SYSTEM_ERROR
```

HTTP status codes follow standard REST semantics.

---

## 22. Global exception handling

Conceptual flow.

```text
Controller
    |
    v
Exception
    |
    v
GlobalExceptionHandler
    |
    +-- validation
    +-- business
    +-- authorization
    +-- not found
    +-- system
          |
          v
      Standard API error
```

Unexpected exceptions are logged. Internal stack traces are never returned to users.

---

## 23. Authentication architecture

Authentication uses username or email plus password plus JWT.

Password reset is supported. OAuth and SSO are not part of this version.

---

## 24. Login flow

```text
Browser
   |
   | POST /auth/login
   v
Auth Controller
   |
   v
Authentication Service
   |
   v
User Repository
   |
   v
Password Hash Verification
   |
   +-- Failed -> Authentication error
   |
   v
JWT Generation
   |
   v
Login Session
   |
   v
Response
```

---

## 25. JWT architecture

Protected requests carry a JWT.

```text
Authorization: Bearer <token>
```

Request flow.

```text
Request
   |
   v
Security Filter
   |
   v
Validate JWT
   |
   v
Extract User
   |
   v
Load Authorities
   |
   v
Authorization
   |
   v
Controller
```

---

## 26. RBAC architecture

Authorization has two levels.

```text
User
  |
  v
Role
  |
  v
Permission
```

The backend is authoritative. Frontend permissions are for UI behaviour only.

---

## 27. Permission checks

Example.

```text
POST /api/v1/questions
```

may require.

```text
QUESTION_CREATE
```

Question publication may require.

```text
QUESTION_PUBLISH
```

The exact permission catalog is maintained separately.

---

## 28. Security rules

Mandatory.

- HTTPS
- Password hashing
- JWT validation
- RBAC
- Input validation
- Secure session handling
- CORS restrictions
- Security audit logging
- Protected APIs
- Secure file access
- No sensitive data in logs
- Audit insertion is transactional with the business mutation

---

## 29. Password storage

Passwords are never stored in plaintext. The database `password` column contains only a secure password hash.

The application uses a modern adaptive password hashing mechanism supported by Spring Security.

---

## 30. Session management

Login sessions are persisted.

Session states: `ACTIVE`, `EXPIRED`, `LOGGED_OUT`.

The system supports session timeout. Logout invalidates the applicable authentication and session state.

---

## 31. System actor

A well-known system user represents background and scheduled operations.

```text
UUID:  00000000-0000-0000-0000-000000000001
Email: system@platform.internal
```

Login is blocked for this identity. Background jobs, scheduled tasks, and data migrations use the system actor. Audit attribution reflects this.

---

## 32. Authorization architecture

Authorization occurs at the API, service, and business-operation layers where necessary.

Merely having access to an Assessment endpoint does not automatically mean the student can attempt every assessment.

Business authorization validates.

```text
User
+
Assessment
+
Schedule
+
Access
+
Attempt rules
```

---

## 33. Assessment security boundary

Assessment delivery is a sensitive runtime operation.

The server remains authoritative for attempt identity, attempt state, submission state, assessment timing rules, response persistence, access authorization, and evaluation.

The browser is not the source of truth for examination results.

---

## 34. Assessment attempt flow

```text
Student
   |
   v
Open assessment
   |
   v
Authentication
   |
   v
Authorization
   |
   v
Schedule validation
   |
   v
Access validation
   |
   v
Create or recover session
   |
   v
Create or recover attempt
   |
   v
Load assessment version
   |
   v
Deliver questions
```

---

## 35. Response submission flow

```text
Student selects answer
        |
        v
Frontend
        |
        v
API
        |
        v
Delivery service
        |
        v
Validate attempt
        |
        v
Validate question
        |
        v
Persist response
        |
        v
Update activity timestamp
        |
        v
Response
```

---

## 36. Auto-save with stale-write protection

The assessment frontend periodically synchronizes responses.

```text
Local UI state
      |
      v
Auto save
      |
      v
Delivery API
      |
      v
Student response
```

The backend is authoritative.

Auto-save is idempotent for the same attempt and question version.

### 36.1 ETag contract

The GET and PUT response endpoints participate in an HTTP ETag cycle.

GET returns:

```text
ETag: "resp-{updatedOnMillis}-{crc32OfResponse}"
```

PUT accepts:

```text
If-Match: "<etag>"
```

Rules:

| Condition | Behaviour |
|---|---|
| If-Match matches current ETag | Accept, return new ETag |
| If-Match does not match | 412 Precondition Failed |
| If-Match: * | Accept only if row exists |
| No If-Match | Accept (first save) |

On 412, the client re-fetches the current response, merges state, and retries with the new ETag. An older delayed request never overwrites a newer accepted response.

---

## 37. Session recovery

If the browser disconnects.

```text
Browser disconnect
       |
       v
Reconnect
       |
       v
Authenticate
       |
       v
Find active session
       |
       v
Find active attempt
       |
       v
Load saved responses
       |
       v
Continue
```

The system avoids creating duplicate attempts during recovery.

---

## 38. Transaction management

Spring transactions are used around business operations that require atomicity.

Examples: create question version, publish assessment, submit attempt, create evaluation, publish result, process purchase, create access grant.

---

## 39. Transaction boundary principle

Transactions are small, business-oriented, atomic, and free from long external network operations where possible.

Avoid keeping a database transaction open while waiting for external services.

---

## 40. External payment transaction

Payment processing does not assume that a database transaction and a payment gateway transaction are the same transaction.

```text
Create Purchase
      |
      v
Payment initiation
      |
      v
Gateway
      |
      v
Payment confirmation
      |
      v
Update payment
      |
      v
Create or update access grant
```

Payment callbacks are idempotent.

---

## 41. Idempotency

Important operations tolerate duplicate requests.

Examples: payment callback, attempt submission, response auto-save, notification retry, background job retry.

Duplicate requests do not create duplicate business records.

The general-purpose `background_job` table stores job state; workers claim jobs by conditional state transition, so a job is not processed twice by concurrent workers.

---

## 42. Database architecture

Primary database: PostgreSQL.

Responsibilities: transactional data, relationships, constraints, referential integrity, searchable business data, historical records.

---

## 43. Database schema principles

Tables follow `snake_case` and singular names.

```text
student_attempt
question_version
assessment_schedule
access_grant
background_job
```

Primary keys: `id UUID`.

---

## 44. Database constraints

Use database constraints wherever appropriate.

```text
PRIMARY KEY
FOREIGN KEY
UNIQUE
NOT NULL
CHECK
```

Application validation remains necessary. Database constraints provide final integrity protection.

---

## 45. Foreign keys

Relationships are normally represented using foreign keys.

Cross-domain references still follow module ownership rules at the application layer.

---

## 46. Index strategy

Indexes exist for primary keys, foreign keys where useful, unique business identifiers, search columns, frequently filtered status fields, date columns used for operational queries, and composite query patterns.

Indexes are created based on actual query patterns rather than indexing every column.

---

## 47. Soft delete and archive

Historical examination data is not physically deleted casually.

The standard lifecycle uses.

```text
ACTIVE
INACTIVE
ARCHIVED
```

The record remains available for historical reference.

---

## 48. Published data immutability

The following are immutable after publication.

```text
Published QuestionVersion
Published BlueprintVersion
Published AssessmentVersion
Published AnswerKey
Published Result
```

Changes create a new version or follow a controlled correction or re-evaluation workflow.

---

## 49. Flyway migration architecture

All schema changes are version controlled.

```text
db/migration/
  V1__create_organization.sql
  V2__create_identity_tables.sql
  V3__create_academic_tables.sql
  ...
  V100__answer_key_detail.sql
  V101__evaluation_versioning.sql
  V102__question_taxonomy.sql
  V103__question_version_review_state.sql
  V104__assessment_selection_rule.sql
  V105__assessment_engine.sql
  V106__background_job.sql
  V107__seed_system_actor.sql
```

Migration naming is consistent.

---

## 50. Database change rule

No manual production schema changes.

Every structural change goes through developer change, Flyway migration, code review, test, deployment.

---

## 51. Redis architecture

Redis is used for cache and lightweight coordination where appropriate.

Redis is not the primary transactional database.

---

## 52. Cacheable data

Good cache candidates.

```text
Organization settings
Business rules
Lookup values
Roles and permissions
Exam masters
Frequently accessed configuration
```

---

## 53. Data that is not cached as primary state

Avoid treating Redis as source of truth for.

```text
Student responses
Student attempts
Payments
Results
Purchases
Access grants
Background job status
```

Transactional records belong in PostgreSQL.

---

## 54. Cache flow

```text
Request
  |
  v
Cache
  |
  +-- HIT --> Return
  |
  +-- MISS
        |
        v
     Database
        |
        v
      Cache
        |
        v
      Return
```

---

## 55. Cache invalidation

When configuration changes.

```text
Update database
      |
      v
Evict or refresh cache
```

The database remains authoritative.

---

## 56. Redis failure behaviour

If Redis is unavailable, core application functionality continues wherever possible.

```text
Redis unavailable
      |
      v
Read from PostgreSQL
      |
      v
Continue operation
```

Cache failure does not become data loss.

---

## 57. Background processing

Long-running operations do not block normal HTTP requests.

Examples: report generation, email delivery, notification processing, analytics calculation, cleanup, large imports, large exports, snapshot preparation.

---

## 58. Background job flow

```text
API request
    |
    v
Create job
    |
    v
Queue / Scheduler
    |
    v
Worker
    |
    v
Execute
    |
    +-- SUCCESS
    |
    +-- FAILURE
           |
           v
         Retry
```

---

## 59. Job requirements

Job state is persisted in `background_job`.

Each job record contains.

```text
id
job_type
reference_type
reference_id
initiated_by
status
parameters
result_reference
progress_current
progress_total
error_code
error_message
queued_on
started_on
completed_on
attempt_count
last_attempt_on
```

Job types: `REPORT_GENERATION`, `IMPORT`, `BULK_OPERATION`, `ANALYTICS_REFRESH`, `SCORECARD_GENERATION`.

Attempt snapshot preparation is not stored in `background_job`. Snapshot state is tracked on `student_attempt`.

---

## 60. Job claiming

Workers claim jobs through a conditional state transition.

```sql
UPDATE background_job
SET status = 'RUNNING', started_on = now()
WHERE id = :id AND status = 'QUEUED';
```

Only the transaction that updates one row owns the job. Concurrent workers cannot double-claim.

---

## 61. Retry rules

Only retry operations that are safe to retry.

Examples: email delivery, notification delivery, analytics calculation, report generation.

Financial operations are not blindly retried without idempotency protection.

---

## 62. Report generation

Small reports may be generated synchronously. Large reports are asynchronous.

```text
User
 |
 v
Request report
 |
 v
Create background job
 |
 v
Background worker
 |
 v
Generate file
 |
 v
Store file
 |
 v
Report history
 |
 v
Download
```

Supported formats: PDF and CSV.

---

## 63. File storage architecture

The application supports local storage in development and object storage in production.

File metadata remains in the database. Storage keys are opaque references generated by the server.

---

## 64. File storage structure

Logical paths.

```text
/uploads/questions
/uploads/profiles
/uploads/organization
/uploads/documents
/reports
/imports
```

The physical storage implementation may differ by environment.

---

## 65. File naming

Do not rely on original filenames as unique storage identifiers. Use a generated unique storage name.

```text
original: physics-diagram.png
stored:   a8f3c1-...-...-...-...png
```

The original filename remains metadata.

---

## 66. File validation

Validate file type, file size, filename, storage path, and access permission.

Do not trust client-provided MIME type alone.

---

## 67. Protected files

Files may be public or protected.

Protected files require authorization before download.

```text
Question assets
Scorecards
Private documents
Report artifacts
Import staging files
```

Report artifacts and import files are retrieved only through the protected download endpoint.

---

## 68. Frontend architecture

Next.js is used for the web application.

```text
app/
  (public)/
  (auth)/
  student/
  operations/
  admin/
  ...
```

The route tree can evolve while maintaining business boundaries.

---

## 69. Frontend application areas

```text
Public website
  Landing
  Exam discovery
  Pricing
  Login

Student portal
  Dashboard
  Assessments
  Attempts
  Analytics
  Notifications
  Profile

Operations
  Questions
  Assessments
  Blueprint
  Review
  Publishing
  Schedules

Administration
  Users
  Roles
  Settings
  Business rules
  Lookups
  Audit
  Reports
  Imports
  System health
```

---

## 70. Frontend state management

Use different tools for different state types.

Server state: TanStack Query.

Form state: React Hook Form and Zod.

Local UI state: React state or a lightweight approach.

Avoid introducing a global state library unless an actual requirement appears.

---

## 71. API client

Axios provides a centralized API client.

Responsibilities: base URL, authorization headers, error handling, request configuration, response normalization, request IDs, and retry where appropriate.

---

## 72. Frontend authentication

The frontend does not treat decoded JWT claims as sufficient authorization. It uses server-provided current-user and permission context.

UI hides inaccessible actions. Backend authorization remains mandatory.

---

## 73. Frontend form validation

Forms validate required fields, formats, lengths, ranges, and business-friendly input errors.

Zod provides client-side schema validation. Backend validation remains authoritative.

---

## 74. Assessment UI architecture

The assessment screen prioritizes question, options, navigation, timer, section, question status, save status, mark for review, and submit.

The UI minimizes unnecessary network calls while ensuring response synchronization.

---

## 75. Assessment frontend state

Conceptually.

```text
Assessment state
  assessmentVersion
  currentQuestion
  currentSection
  answers
  markedForReview
  visited
  timer
  saveStatus
  sessionStatus
```

The browser state is temporary. Backend state remains authoritative.

---

## 76. Assessment synchronization

The frontend tracks saved, saving, and failed.

```text
Answer changed
     |
     v
Local state updated
     |
     v
Auto save queued
     |
     v
API request
     |
     v
Success
     |
     v
Saved
```

If synchronization fails, the frontend retries without silently losing the student's answer. The ETag contract resolves any stale-write conflict.

---

## 77. API pagination

Large collections support pagination.

Standard parameters: page, size, sort.

Filtering is supported where operationally required.

---

## 78. Search architecture

The main search uses PostgreSQL-supported search and filtering.

Examples: question code, question text, assessment name, student email, user name, transaction reference.

Global search across resources uses a fixed resource registry with permission filtering.

The `pg_trgm` extension supports fuzzy matches on names. Search queries are parameterized, bounded by a limit, and rate-limited.

No separate search engine is required.

---

## 79. Logging architecture

Logs are structured.

Useful fields.

```text
timestamp
level
service or module
request_id
user_id
operation
duration
status
error_code
```

---

## 80. Log categories

At minimum.

```text
Application log
Business log
Security log
Error log
```

---

## 81. Sensitive data logging

Never log.

```text
Passwords
JWT tokens
Payment secrets
Private credentials
Sensitive personal information unnecessarily
Reset tokens
```

Request and response logging is configured carefully.

---

## 82. Request correlation

Each request receives a correlation and request identifier.

```text
Request
   |
   v
Request ID
   |
   v
Controller
   |
   v
Service
   |
   v
Repository
   |
   v
Log
```

This allows a production issue to be traced across application logs.

---

## 83. Monitoring

Monitor application health, API latency, API error rate, database connectivity, database performance, Redis availability, background jobs, assessment sessions, file storage, and system resources.

---

## 84. Health checks

Application health exposes operational health information.

```text
Application
  Database
  Redis
  PgBouncer
  Background workers
  Storage
  Optional providers
```

Health endpoints do not expose sensitive infrastructure information publicly.

The administrative system-health endpoint aggregates component health with bounded timeouts and short caching.

---

## 85. Metrics

Prometheus-compatible metrics include HTTP request count, HTTP request duration, HTTP error count, database connection usage, background job count, background job failures, active assessment sessions, attempt submissions, and evaluation processing.

---

## 86. Dashboards

Grafana visualizes system health, API performance, database health, background jobs, assessment runtime, and error trends.

The operational dashboard is separate from the business administration dashboard.

---

## 87. Centralized logs

Loki is used for centralized log aggregation.

```text
Application
   |
   v
Structured logs
   |
   v
Loki
   |
   v
Grafana
```

---

## 88. Deployment environments

Minimum environments.

```text
Development
Test
Staging
Production
```

---

## 89. Environment configuration

Configuration is environment-specific.

Examples: database URL, database credentials, JWT configuration, Redis URL, storage configuration, email configuration, payment configuration.

Secrets are not committed to Git.

---

## 90. Configuration strategy

Use application.yml, application-{profile}.yml, environment variables, and secure secret management.

Sensitive values come from secure environment configuration.

---

## 91. Docker architecture

Backend and frontend are containerized.

Supporting services for local development include PostgreSQL, Redis, and PgBouncer.

Docker Compose orchestrates local development.

---

## 92. Reverse proxy

Nginx sits in front of the applications.

Responsibilities include HTTPS termination, routing, static asset handling, request limits, and basic proxy configuration.

Nginx is the production ingress and reverse-proxy boundary. It does not contain business logic.

---

## 93. CI/CD

GitHub Actions automates checkout, build, unit tests, integration tests, static checks, security checks, Docker image build, artifact publishing, and deployment.

Production deployment requires appropriate approval controls.

---

## 94. Release versioning

Releases are versioned.

```text
v1.5.0
v1.5.1
v1.6.0
```

Database migrations remain compatible with the deployment strategy.

---

## 95. Rollback

The deployment process supports rollback.

Application rollback: version N to version N-1.

Database rollback does not rely on manually reversing production migrations. Forward-compatible migration strategies are preferred.

---

## 96. Backup strategy

Backup covers PostgreSQL, file and object storage, and critical configuration.

Restore procedures are tested periodically. A backup that has never been restored is not assumed reliable.

---

## 97. Testing architecture

Testing levels.

```text
Unit tests
Integration tests
API tests
Security tests
End-to-end tests
Performance tests
Concurrency tests
Failure-injection tests
```

---

## 98. Unit testing

Unit tests focus on business logic.

Examples: question validation, assessment validation, scoring rules, access validation, coupon calculation, result calculation, permission checks, review-state transitions, rule-based selection ordering.

Services are testable without starting the entire application.

---

## 99. Integration testing

Integration tests verify service, repository, and database together.

Examples: create question, publish assessment, save attempt, submit attempt, evaluate attempt, create purchase, create access grant, create background job.

---

## 100. API testing

Verify HTTP status, response structure, validation, authorization, pagination, filtering, business errors, and precondition handling.

---

## 101. Assessment testing

Assessment delivery requires dedicated testing.

Test timer, navigation, auto-save, reconnect, session recovery, attempt submission, duplicate submission, expired session, concurrent requests, and ETag stale-write handling.

---

## 102. Evaluation testing

Test correct answer, incorrect answer, unanswered, negative marking, question-specific negative marking, bonus marks, grace marks, percentage, rank, percentile, answer-key version, and re-evaluation.

Test that evaluation reads only from `answer_key_detail`, never from `question_option.is_correct`.

---

## 103. Commerce testing

Test free assessment, paid assessment, bundle, coupon, successful payment, failed payment, refund, access expiration, duplicate payment callback, and access grant.

---

## 104. Security testing

Verify unauthenticated access, unauthorized access, role permissions, permission boundaries, session expiry, invalid JWT, expired JWT, password reset, protected files, API validation, and system actor login rejection.

---

## 105. Performance testing

Performance testing focuses on examination-time traffic.

Important scenarios.

```text
Many students starting assessment
Many responses being saved
Many students submitting simultaneously
Result calculation
Analytics processing
Snapshot preparation
Report generation
```

The assessment delivery path remains stable under expected concurrent load.

---

## 106. Performance design principles

Avoid N+1 database queries, large unnecessary API payloads, synchronous long-running operations, repeated configuration queries, and unbounded result sets.

Prefer pagination, batch operations, caching, async processing, proper indexes, and projection queries where useful.

---

## 107. API payload design

Do not return unnecessarily large objects.

Use `QuestionSummary` for question lists and `QuestionDetail` for question detail. This prevents list endpoints from returning large assets and explanations unnecessarily.

---

## 108. Lazy vs eager loading

Default to controlled loading. Avoid broad eager relationships such as Assessment to Questions to Assets to Explanations to Analytics to Attempts.

Fetch exactly what the use case needs.

---

## 109. N+1 query prevention

When retrieving collections with relationships, use appropriate joins, fetch strategies, projections, batch fetching, and carefully designed repository queries.

Do not solve N+1 by making every relationship eager.

---

## 110. Concurrency

The platform accounts for simultaneous response saves, attempt submissions, payment callbacks, result publication, and configuration updates.

Database constraints and transaction isolation protect critical state.

---

## 111. Attempt submission concurrency

Submission is idempotent.

If two submit requests arrive, only one valid submission transition occurs. The second request returns the existing state rather than creating a duplicate submission.

---

## 112. Optimistic locking

Optimistic locking is considered for entities where concurrent updates are realistic.

This is separate from business versioning.

---

## 113. Business version vs JPA version

These are not confused.

Business version is used for historical and published content: Question Version 1, 2, 3.

JPA optimistic lock is used to prevent conflicting simultaneous updates.

They solve different problems.

---

## 114. Audit architecture

Auditing captures important actions.

Examples: user created, role assigned, question published, assessment published, answer key published, result published, settings changed, business rule changed, access manually granted, refund processed, re-evaluation performed, report generated.

---

## 115. Audit log structure

```text
AuditLog
  user_id
  entity_name
  entity_id
  action
  remarks
  created_on
```

Audit records are append-only.

---

## 116. Audit atomicity

Audit insertion participates in the business transaction.

If audit insertion fails, the transaction rolls back. No important action happens without audit evidence.

Background operations use the system actor for attribution.

Autosave is not audited.

---

## 117. Data retention

Retention is configurable where required.

Potentially configurable areas: audit logs, login sessions, temporary files, background job history, notification history, report artifacts.

Academic records and financial records are not deleted merely because an operational retention window expired unless permitted by business and legal requirements.

---

## 118. Error handling philosophy

Errors are predictable, actionable, safe, consistent, and logged when necessary.

User-facing messages are understandable. Internal exceptions are logged, not returned.

---

## 119. Coding standards

Mandatory standards.

- Meaningful names
- Small focused classes
- Single responsibility
- Constructor injection
- DTO-based APIs
- No duplicate business logic
- Validation at boundaries
- Consistent exception handling
- Unit tests for business logic
- Integration tests for critical flows
- Code review
- API documentation

---

## 120. Dependency injection

Use constructor injection. Avoid field injection.

---

## 121. Service design

Services represent business operations.

Good examples.

```text
publishAssessment()
submitAttempt()
evaluateAttempt()
grantAccess()
publishQuestion()
materializeSelectionRules()
startBackgroundJob()
```

Avoid services that merely duplicate repository methods without business meaning.

---

## 122. Repository design

Repositories focus on persistence operations.

A repository retrieves and stores data. It does not decide whether a question is allowed to be published.

---

## 123. Validation architecture

Validation occurs at multiple levels.

API validation: required, format, length, range.

Business validation: assessment cannot publish without questions, published version cannot be modified, student cannot attempt expired schedule.

Database validation: primary key, foreign key, unique, not null, check.

All three layers have different responsibilities.

---

## 124. Configuration principles

Avoid hardcoding marks, negative marking, assessment duration, email configuration, payment settings, business rules, and organization settings when they are intended to be configurable.

Use domain configuration and master data.

---

## 125. Hardcoding exceptions

Core technical constants may remain code-based: API version, internal error codes, technical defaults, system limits.

Configuration is introduced when there is an actual business need.

---

## 126. API documentation

Every public API endpoint is documented through OpenAPI.

Documentation describes endpoint, method, authentication, parameters, request body, response, validation, and errors.

Swagger UI is used for developer exploration.

---

## 127. API contract ownership

The API contract is owned by the backend architecture. The frontend consumes documented contracts rather than depending on JPA entity structure.

---

## 128. Development workflow

For every module.

```text
Requirement
   |
   v
Domain model
   |
   v
Flyway migration
   |
   v
Entity
   |
   v
Repository
   |
   v
Service
   |
   v
DTO
   |
   v
Mapper
   |
   v
Validation
   |
   v
Controller
   |
   v
OpenAPI
   |
   v
Tests
   |
   v
Security
   |
   v
Code review
```

---

## 129. Recommended module development order

```text
1.  Foundation
2.  Identity
3.  Academic
4.  Content
5.  Assessment
6.  Delivery
7.  Evaluation
8.  Analytics
9.  Commerce
10. Communication
11. Administration
12. Infrastructure
13. Reporting
14. Hardening and production
```

---

## 130. Foundation phase

Implement Spring Boot application, database connection, Flyway, base entity, exception handling, API response, security foundation, OpenAPI, logging, Docker, environment configuration, and background job infrastructure.

---

## 131. Identity phase

Implement organization, user, role, permission, user role, role permission, login session, authentication, JWT, authorization, password reset, and system actor handling.

---

## 132. Academic phase

Implement exam, subject, chapter, topic, sub-topic, language, and tag.

---

## 133. Content phase

Implement question, question version, question template, question option, asset, explanation, hint, previous exam history, question tag, taxonomy mapping, and review workflow.

Include draft, review, approval, publication, and versioning.

---

## 134. Assessment phase

Implement blueprint, blueprint version, assessment, assessment version, assessment question, assessment schedule, assessment collection, collection assessment, and assessment selection rule.

Include validation, preview, publication, scheduling, versioning, rule-based selection, and engine resolution.

---

## 135. Delivery phase

Implement assessment session, student attempt, attempt question, attempt question option, student response, and delivery policy.

Include async snapshot preparation, Start Exam, autosave with ETag, session recovery, navigation, timer, and submission.

---

## 136. Evaluation phase

Implement answer key, answer key detail, evaluation, result, and scorecard.

Include scoring, negative marking, bonus, grace, ranking, percentile, answer-key versioning, and re-evaluation.

---

## 137. Analytics phase

Implement student analytics, assessment analytics, question analytics, topic analytics, and organization analytics.

Calculate asynchronously where appropriate.

---

## 138. Commerce phase

Implement access package, access package collection, subscription plan, purchase, payment record, access grant, and coupon.

Support free assessments, paid assessments, bundles, coupons, payment, refund, and access expiry.

---

## 139. Communication phase

Implement notification, email template, and announcement.

Support in-app notifications, email, delivery status, retry, and announcement fan-out.

---

## 140. Administration phase

Implement organization setting, business rule, lookup, and audit log.

Support user management, role management, business configuration, system monitoring, audit, bulk operations, imports, global search, and system health.

---

## 141. Reporting phase

Implement report catalog, synchronous small reports, asynchronous large reports, job history, protected artifact delivery, and executive and operational reports.

---

## 142. Content rendering pipeline

```text
Canonical Markdown + LaTeX
          |
          v
Validated content AST
          |
          v
Markdown renderer
          |
          v
LaTeX tokenization
          |
          v
KaTeX server-side rendering
          |
          v
Asset shortcode resolution
          |
          v
Sanitized HTML
          |
          v
Next.js SSR / Server Components
          |
          v
Student browser
```

The browser does not depend on MathJax for question rendering.

---

## 143. Exam engine resolution

```text
AssessmentVersion.engine_type
        |
        v
ExamEngineRegistry.resolve(type)
        |
        v
ExamEngine implementation
        |
        v
resolveConfiguration(context, attempt)
        |
        v
DeliveryConfiguration
        |
        v
Runtime
```

Engine type is immutable after publication. A change requires a new AssessmentVersion.

---

## 144. Answer key detail integration

Evaluation reads answer correctness only from `answer_key_detail`. The `question_option.is_correct` field is an authoring default and is not consulted at evaluation time.

This allows corrected answer keys to be published as new versions without modifying the underlying question content.

---

## 145. Evaluation versioning integration

When an attempt is evaluated, a versioned evaluation record is created. When re-evaluation is performed with a new AnswerKey, a new evaluation version is created and the previous version is marked superseded.

A partial unique index enforces at most one current evaluation per attempt.

---

## 146. Review workflow integration

Question versions follow the state machine: DRAFT, IN_REVIEW, APPROVED, REJECTED, PUBLISHED.

Illegal transitions return a 409 error. Self-review is blocked when the same user holds both submit and review permissions.

---

## 147. Rule-based selection integration

Assessment selection rules are materialized into AssessmentQuestion rows during authoring. Once materialized, the rules are provenance and are not re-executed at runtime.

Materialization is deterministic: same rule configuration, same published QuestionVersion pool, and same prior-use state produce the same result.

---

## 148. Global search integration

Global search uses a fixed resource registry. Each resource declares its searchable fields and required permission. Results are permission-filtered before the query executes. PII is masked by default.

Query length is bounded, the query timeout is bounded, and the endpoint is rate-limited.

---

## 149. System health integration

The administrative system-health endpoint aggregates component health on demand with a short cache. Probes have bounded timeouts. The response contains no secrets.

---

## 150. Support scope integration

Support users receive granular permissions. Sensitive fields are masked by default. Destructive operations require separate permissions not granted to support by default.

---

## 151. Report history integration

Report history is backed by the general-purpose `background_job` table. Report artifacts are stored via the existing file storage abstraction and served through the protected file endpoint.

---

## 152. Critical end-to-end acceptance flow

Before declaring the platform operational, the following complete flow must work.

```text
Admin
  |
  v
Create exam
  |
  v
Create subject and topic
  |
  v
Create question
  |
  v
Create question version
  |
  v
Submit review
  |
  v
Approve
  |
  v
Publish question
  |
  v
Create blueprint
  |
  v
Publish blueprint version
  |
  v
Create assessment
  |
  v
Add question versions or configure selection rules
  |
  v
Materialize rules
  |
  v
Validate
  |
  v
Publish assessment version
  |
  v
Schedule assessment
  |
  v
Student gets access
  |
  v
Snapshot preparation (async)
  |
  v
Student starts assessment
  |
  v
Responses auto-save
  |
  v
Student submits
  |
  v
Answer key applied
  |
  v
Evaluation
  |
  v
Result
  |
  v
Scorecard
  |
  v
Analytics
```

---

## 153. Critical data integrity rules

The implementation guarantees the following.

1. A published QuestionVersion cannot be silently modified.
2. A published AssessmentVersion cannot be silently modified.
3. A published BlueprintVersion cannot be silently modified.
4. A published AnswerKey cannot be silently modified.
5. A StudentAttempt cannot belong to an invalid AssessmentVersion.
6. A StudentResponse must belong to the attempt.
7. A result must originate from an evaluation.
8. An evaluation must reference an AnswerKey.
9. Paid access is represented through AccessGrant.
10. Financial operations are idempotent.
11. At most one current evaluation exists per attempt.
12. AttemptQuestion and AttemptQuestionOption are immutable after snapshot READY.
13. Audit insertion participates in the business transaction.
14. Background jobs are claimed by conditional state transition, preventing double-claim.

---

## 154. Critical security rules

The backend never trusts client-provided score, rank, percentage, access status, permissions, attempt status, deadline, remaining time, or evaluation state.

The server calculates and controls these values.

---

## 155. Critical assessment rules

The browser is not treated as authoritative for time remaining, attempt status, submission status, score, evaluation, or access.

The backend remains authoritative.

---

## 156. Critical commerce rules

Paid access is not determined from frontend state, purchase UI state, or payment page success redirect.

Authorization uses verified payment state and AccessGrant.

---

## 157. Critical analytics rules

Analytics does not alter transactional academic records.

Analytics reads and derives. It does not modify attempts or results.

---

## 158. Critical audit rules

Audit logging is not a mechanism to modify business state.

Audit data is historical evidence of an operation.

---

## 159. Future AI integration boundary

AI is a future capability and is not tightly coupled to core transactional logic.

Core scoring and examination integrity remain deterministic and server-controlled.

AI outputs are treated as generated, reviewed, approved, and only then published for examination content.

---

## 160. Future offline and PWA capability

The architecture remains compatible with future offline and PWA capabilities.

Offline functionality does not compromise authoritative server-side assessment state.

---

## 161. Internationalization

The frontend architecture remains compatible with multilingual support.

Areas: UI labels, question content, assessment content, notifications, email.

Language is represented in the academic domain.

---

## 162. Responsive design

The frontend supports desktop, tablet, and mobile browser. The primary examination experience is optimized for desktop and tablet.

---

## 163. Accessibility

The UI follows practical accessibility principles: keyboard navigation, form labels, clear focus states, readable contrast, semantic HTML, and accessible error messages.

Accessibility is considered during component development rather than added at the end.

---

## 164. Observability architecture

```text
                    +--------------+
                    | Spring Boot  |
                    +------+-------+
                           |
              +------------+------------+
              |            |            |
              v            v            v
          Metrics        Logs       Health
              |            |            |
              v            v            |
        +-----------+  +---------+      |
        | Prometheus|  |  Loki   |      |
        +-----+-----+  +----+----+      |
              |             |           |
              +------+------+           |
                     v                  |
                  Grafana <-------------+
```

---

## 165. Production readiness checklist

### Application

- Build succeeds
- Tests pass
- API documented
- Security configured
- Error handling configured
- Logging configured
- Monitoring configured

### Database

- Flyway migrations tested
- Indexes reviewed
- Constraints reviewed
- Backup configured
- Restore tested

### Security

- HTTPS
- Password hashing
- JWT configuration
- RBAC
- CORS
- File protection
- Sensitive logging review

### Assessment

- Async snapshot preparation
- Start Exam
- Auto-save with ETag
- Session recovery
- Submission
- Timer
- Evaluation
- Result publication

### Commerce

- Payment
- Payment callback
- Idempotency
- Access grant
- Refund
- Expiration

### Reporting

- Report catalog
- Synchronous and asynchronous execution
- Job history
- Protected artifact delivery
- Executive KPI definitions

---

## 166. Architecture decision summary

| Area | Decision |
|---|---|
| Architecture | Modular monolith |
| Backend | Java 25 or Java 21 |
| Framework | Spring Boot 4 |
| Security | Spring Security |
| ORM | Hibernate and Spring Data JPA |
| Database | PostgreSQL |
| Cache | Redis |
| Connection pooling | PgBouncer |
| Migration | Flyway |
| API | REST and JSON |
| API version | v1 |
| Documentation | OpenAPI and Swagger |
| Frontend | Next.js |
| Frontend language | TypeScript |
| UI | Tailwind and shadcn/ui |
| Server state | TanStack Query |
| Forms | React Hook Form |
| Validation | Zod |
| HTTP client | Axios |
| Content rendering | MARKDOWN_LATEX_V1, KaTeX SSR |
| Container | Docker |
| Local infrastructure | Docker Compose |
| Reverse proxy | Nginx |
| Metrics | Prometheus |
| Dashboards | Grafana |
| Logs | Loki |
| CI/CD | GitHub Actions |
| File storage | Local dev, S3-compatible production |
| DB migration | Flyway |
| Organization model | Single organization |
| Primary IDs | UUID |
| API payload | JSON |
| Versioning | Business versioning for immutable published entities |
| Evaluation | Versioned |
| Answer key | Versioned with per-question detail |
| Background processing | Async jobs via background_job |
| Stale-write protection | HTTP ETag |
| Audit | Transactional, append-only |
| System actor | Well-known UUID |
| Future AI | Separate capability layer |

---

## 167. Technical design freeze

The architecture is the baseline.

```text
                              Browser / Desktop / Tablet
                                         |
                                       HTTPS
                                         v
                                +-----------------+
                                |      NGINX      |
                                | Reverse Proxy   |
                                +--------+--------+
                                         |
                           +-------------+-------------+
                           |                           |
                           v                           v
                    +-------------+          +---------------------+
                    |   Next.js   |          |   Spring Boot 4     |
                    |  Frontend   | REST/JSON|   Modular Monolith  |
                    +-------------+          +----------+----------+
                                                        |
             +------------------------------------------+-------------------------+
             |             |             |              |             |           |
             v             v             v              v             v           v
         Identity      Academic       Content       Assessment      Delivery   Evaluation
             |             |             |              |             |           |
             +-------------+-------------+--------------+-------------+-----------+
                                                        |
                                      +-----------------+------------------+
                                      v                 v                  v
                                  Analytics          Commerce        Communication
                                                        |
                                                        v
                                                  Administration
                                                        |
                                                        v
                                                  Infrastructure

Spring Boot
     |
     +-- PgBouncer  --> PostgreSQL  <-- authoritative transactional source of truth
     +-- Redis                       <-- cache only
     +-- Object storage              <-- files and artifacts
     +-- Background jobs             <-- async work
```

Infrastructure.

```text
GitHub
   |
   v
GitHub Actions
   |
   v
Docker images
   |
   v
Deployment
   |
   v
Nginx
   |
   +-- Next.js
   +-- Spring Boot API
```

Observability.

```text
Spring Boot and infrastructure
        |
        +-- Metrics --> Prometheus --> Grafana
        |
        +-- Logs    --> Loki       --> Grafana
```

The architecture remains one deployable backend application with explicit domain and module boundaries.

The core correctness flow is:

```text
Question
   |
   v
Assessment
   |
   v
Attempt
   |
   v
Evaluation
   |
   v
Result
   |
   v
Analytics
```

---

## 168. Final engineering principle

The system is built as one application, clearly separated business domains, one authoritative transactional database, strong versioning, secure APIs, and asynchronous processing where it actually provides value.

The goal is not to create the most complicated architecture. The goal is to create an architecture that can reliably deliver the examination lifecycle at scale while preserving examination integrity and allowing the platform to evolve.

---

## 169. Document status

**Status:** FROZEN technical design baseline.

This document is the implementation reference for backend architecture, frontend architecture, module boundaries, API implementation, security implementation, persistence implementation, background processing, deployment, testing, and observability.