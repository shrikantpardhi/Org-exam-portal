# Smart Competitive Examination Platform
## Architecture Freeze & Final Technology Stack

**Version:** 1.5
**Status:** FROZEN
**Audience:** Product, Backend, Frontend, QA, DevOps, Security, Engineering Leadership
**Content Rendering:** MARKDOWN_LATEX_V1 + Unified/Remark + KaTeX SSR + Rehype Sanitization

---

## 1. Purpose

This document formally freezes the technical architecture and technology stack for the Smart Competitive Examination Platform.

It answers five questions.

1. How is the platform architected?
2. Which technologies are approved?
3. How are the major modules separated?
4. What technical decisions are intentionally excluded?
5. What rules must developers follow when implementation begins?

This document is downstream of the Master PRD, Domain Model, Technical Design Document, Database Standards and Schema, and API Contract. It becomes the technical baseline for the Development Roadmap and the phase implementation specifications.

---

## 2. Architecture status

The architecture is frozen.

The following are approved decisions.

- Modular Monolith
- Java 25 preferred; Java 21 acceptable if organizational standards require it
- Spring Boot 4
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Redis (cache only)
- PgBouncer (production connection pooling)
- Flyway
- REST APIs
- OpenAPI and Swagger
- Next.js
- TypeScript
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form
- Zod
- Axios
- KaTeX server-side rendering
- Unified / Remark-compatible Markdown pipeline
- Rehype-compatible HTML sanitization
- Docker
- Nginx
- GitHub
- GitHub Actions
- Prometheus
- Grafana
- Loki

---

## 3. Product architecture context

The platform is a web-first competitive examination system designed to support.

- SSC
- IBPS
- SBI
- RBI
- Railways
- JEE Main
- NEET
- CUET
- UPSC
- State PSC
- CAT
- GATE
- Custom examination frameworks

The platform is intentionally designed as a configurable examination engine rather than a collection of hard-coded exam implementations.

The architecture supports.

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
Analytics
```

Rather than.

```text
SSC-specific code
NEET-specific code
UPSC-specific code
CAT-specific code
```

---

## 4. Core architectural principle

The most important architectural principle is this.

> Configuration drives examination behavior; code provides the platform capabilities.

For example, negative marking is not implemented as.

```java
if (exam == SSC) {
    ...
}
```

Instead.

```text
Assessment version
        |
        v
Scoring configuration
        |
        v
Evaluation engine
```

The same principle applies to section timing, question distribution, navigation, languages, calculator availability, scoring, negative marking, question selection, assessment scheduling, and access rules.

---

## 5. Architectural style

### 5.1 Selected architecture

Modular Monolith.

The backend is deployed as one application with strong internal domain boundaries.

The production request path.

```text
                              Browser / Desktop / Tablet
                                         |
                                       HTTPS
                                         v
                                +-----------------+
                                |      NGINX      |
                                | Reverse Proxy   |
                                | TLS / Routing   |
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
       +------------------------------------------------+------------------------------------------------+
       |              |             |             |      |       |        |              |                  |
       v              v             v             v      v       v        v              v                  v
   Identity        Academic      Content      Assessment Delivery Evaluation Analytics  Commerce   Communication
       |              |             |             |      |       |        |              |                  |
       +--------------+-------------+-------------+------+-------+--------+--------------+------------------+
                                                        |
                                      +-----------------+------------------+
                                      v                 v                  v
                                 PgBouncer           Redis            Object Storage
                                      |             (cache)         Local / S3-compatible
                                      v
                                 PostgreSQL
                                 Source of truth
```

Eleven business modules are separate architectural modules.

- Identity
- Academic
- Content
- Assessment
- Delivery
- Evaluation
- Analytics
- Commerce
- Communication
- Administration
- Infrastructure

They are not separate services.

Each module owns its business logic, its repositories, its persistence rules, and exposes explicit service and application contracts. Each module uses DTOs at API boundaries. No module freely manipulates another module's JPA entities or repositories.

The architecture therefore combines one deployable backend, strong business-domain boundaries, one authoritative transactional database, and supporting cache, storage, and background infrastructure.

---

## 6. Why modular monolith

The platform does not need the operational complexity of microservices.

A modular monolith provides simpler deployment, simpler local development, simpler debugging, lower infrastructure cost, easier transactions, easier database consistency, faster feature development, easier testing, and fewer distributed-system failure modes.

At the same time, strong module boundaries preserve a future extraction path.

---

## 7. Why not microservices

Microservices would introduce additional requirements.

- Service discovery
- Distributed tracing
- Inter-service authentication
- Network failure handling
- Distributed transactions
- Message brokers
- Deployment complexity
- Service versioning
- Infrastructure overhead
- More complicated local development

These are not justified for the initial platform.

The architecture therefore optimizes for correctness, development speed, maintainability, and operational simplicity rather than premature distribution.

---

## 8. Future microservice compatibility

The monolith is designed so that the following boundaries could eventually become independent services.

```text
Identity
Assessment
Delivery
Evaluation
Commerce
Notification
Analytics
Reporting
```

This means modules own their business logic, cross-module calls go through services, JPA entities are not freely shared, repositories are module-private, domain rules remain inside modules, shared code remains limited, APIs use DTOs, and modules communicate through explicit contracts.

---

## 9. Backend technology stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 25 | Primary backend language |
| Framework | Spring Boot 4 | Application framework |
| Security | Spring Security | Authentication and authorization |
| Persistence | Spring Data JPA | Repository abstraction |
| ORM | Hibernate | Object-relational mapping |
| Database | PostgreSQL | Transactional source of truth |
| Cache | Redis | Low-latency caching |
| Connection pooling | PgBouncer | PostgreSQL connection pooling |
| Migration | Flyway | Database versioning |
| API | REST and JSON | Client-server communication |
| API documentation | OpenAPI and Swagger | API contract |
| Build | Maven | Dependency and build management |
| Container | Docker | Packaging and deployment |

---

## 10. Java version decision

Preferred.

```text
Java 25
```

Java 25 is the preferred baseline when the target production environment and organizational standards support it.

Compatibility option.

```text
Java 21
```

Java 21 remains acceptable if enterprise infrastructure standardizes on Java 21, the hosting environment does not yet support Java 25, organizational security policy requires Java 21, or deployment compatibility becomes a concern.

The application avoids unnecessary coupling to one JVM-specific feature where practical.

---

## 11. Spring Boot 4

Spring Boot 4 is the selected application framework.

Responsibilities.

- Application lifecycle
- Dependency injection
- Configuration
- Web layer
- Validation
- Database integration
- Security integration
- Actuator and health
- Testing infrastructure

The platform uses Spring Boot conventions rather than creating a custom application framework.

---

## 12. Spring Security

Spring Security is responsible for authentication, authorization, JWT processing, password handling integration, endpoint protection, security context, and method-level authorization.

Authorization is enforced by the backend.

The frontend hides unavailable features for UX purposes. That is not a security boundary.

---

## 13. ORM and persistence

Selected stack.

```text
Spring Data JPA
        +
Hibernate
        +
PostgreSQL
```

JPA entities represent persistence state. They are not exposed directly through REST APIs.

Use.

```text
Entity
  |
  v
Mapper
  |
  v
Response DTO
```

Rather than.

```text
Entity
  |
  v
JSON
```

---

## 14. Database technology

Selected database: PostgreSQL.

PostgreSQL is the authoritative transactional datastore. It stores identity, academic masters, questions, question versions, blueprints, assessments, sessions, attempts, responses, evaluations, results, commerce, notifications, audit records, and infrastructure job records.

---

## 15. Database responsibility

PostgreSQL is responsible for durability, transactional integrity, relational constraints, foreign keys, uniqueness, historical records, transactional state, and source-of-truth data.

Redis must never become the authoritative source for these records.

---

## 16. Redis architecture

Redis is a supporting infrastructure component.

Use Redis for organization settings, business rules, lookup values, roles, permissions, exam master configuration, and short-lived application cache.

Do not use Redis as the primary store for student responses, payment transactions, results, answer keys, access grants, audit records, background job status, or assessment sessions as the only source of truth.

---

## 17. Redis failure strategy

The application degrades gracefully if Redis is unavailable.

Preferred behavior.

```text
Redis available
      |
      v
Read cache
      |
      v
Fast response
```

If unavailable.

```text
Redis failure
      |
      v
Fallback to PostgreSQL
      |
      v
Continue operation
```

Redis is an optimization layer, not a mandatory transactional dependency.

---

## 18. PgBouncer

PgBouncer sits in front of PostgreSQL in production.

```text
Application
    |
    v
PgBouncer
    |
    v
PostgreSQL
```

Transaction pooling is used for short-lived request and transaction workloads where the application does not depend on session-scoped PostgreSQL state.

PgBouncer solves connection-management pressure. It does not eliminate database write load. The platform therefore combines snapshot pre-generation, bounded workers, efficient SQL, PgBouncer, and load testing as a single capacity strategy.

---

## 19. Database migration

Selected technology: Flyway.

Every schema change is represented by a versioned migration.

Production database changes are never made manually.

---

## 20. Frontend technology stack

| Layer | Technology |
|---|---|
| Framework | Next.js |
| Language | TypeScript |
| Styling | Tailwind CSS |
| UI components | shadcn/ui |
| Server state | TanStack Query |
| Forms | React Hook Form |
| Validation | Zod |
| HTTP client | Axios |

---

## 21. Why Next.js

Next.js is selected because the platform contains two distinct categories of frontend experience.

Public: landing pages, exam catalog, assessment discovery, subscription pages, pricing, and informational content. These benefit from SEO, server rendering, optimized routing, and metadata management.

Authenticated: student dashboard, examination interface, staff workspace, administration portal, analytics, reports. These behave like application dashboards.

Next.js supports both within one frontend architecture.

---

## 22. Next.js application structure

Recommended conceptual structure.

```text
app/
  (public)/
    home
    exams
    assessments
    pricing
    about

  (auth)/
    login
    forgot-password
    reset-password

  student/
    dashboard
    assessments
    attempts
    analytics
    notifications
    subscriptions
    profile

  staff/
    dashboard
    questions
    blueprints
    assessments
    schedules
    reports

  admin/
    dashboard
    users
    roles
    settings
    business-rules
    lookups
    audit
    reports
    imports
    system-health
```

The exact route structure may evolve. Role boundaries remain explicit.

---

## 23. Frontend state strategy

Use different tools for different state types.

Server state: TanStack Query. Examples: assessments, questions, student analytics, notifications, subscriptions, report job status.

Form state: React Hook Form. Examples: question editor, blueprint builder, assessment configuration, user management.

Validation: Zod.

Local UI state: React state and hooks. Examples: modal open, selected tab, local filters, temporary UI state.

This avoids unnecessary global state complexity.

---

## 24. API communication

Selected approach.

```text
Next.js
   |
   v
Axios
   |
   v
REST API
   |
   v
Spring Boot
```

API contracts are defined in the API Contract document. The frontend does not invent API semantics.

---

## 25. UI component architecture

shadcn/ui provides the foundation for buttons, dialogs, forms, dropdowns, tables, tabs, cards, alerts, pagination, and search interfaces.

Product-specific components are composed above this layer.

```text
shadcn/ui
    |
    v
Shared components
    |
    v
Domain components
    |
    v
Feature screens
```

---

## 26. Styling architecture

Tailwind CSS is the standard styling system.

Avoid creating a second competing styling framework.

Use design tokens for colors, typography, spacing, borders, radius, shadows, and states.

The student examination interface may use a dedicated layout while still using the same underlying design system.

---

## 27. Backend module architecture

The backend follows feature and domain-based packaging.

```text
com.company.exam
  common
  config
  identity
  academic
  content
  assessment
  delivery
  evaluation
  analytics
  commerce
  communication
  administration
  infrastructure
```

---

## 28. Standard module structure

Each feature module contains.

```text
module/
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

Not every module requires every package. Do not create empty architectural layers merely to satisfy a template.

---

## 29. Common module

The common module contains genuinely shared functionality.

Examples.

```text
common/
  entity/
  dto/
  exception/
  enum/
  util/
  pagination/
  audit/
  response/
```

Common does not become a dumping ground. If code belongs to a domain, it stays inside that domain.

---

## 30. Config module

Central configuration includes.

```text
config/
  security/
  jwt/
  redis/
  database/
  swagger/
  scheduler/
  web/
```

Environment-specific configuration remains externalized.

---

## 31. Identity module

Owns Organization, User, Role, Permission, UserRole, RolePermission, LoginSession.

Responsibilities include authentication, authorization, user lifecycle, role assignment, and session lifecycle.

Identity does not own assessment business rules.

---

## 32. Academic module

Owns Exam, Subject, Chapter, Topic, SubTopic, Language, Tag.

Responsibilities include academic hierarchy, master data, and taxonomy.

---

## 33. Content module

Owns Question, QuestionVersion, QuestionTemplate, QuestionOption, Asset, Explanation, Hint, PreviousExamHistory, QuestionTag.

Responsibilities include question authoring, versioning, taxonomy mapping, review, approval, publication, and content metadata.

---

## 34. Assessment module

Owns Blueprint, BlueprintVersion, Assessment, AssessmentVersion, AssessmentQuestion, AssessmentSchedule, AssessmentCollection, CollectionAssessment, AssessmentSelectionRule.

Responsibilities include blueprint creation, assessment construction, question selection, rule-based materialization, publishing, scheduling, collections, and engine resolution.

---

## 35. Delivery module

Owns AssessmentSession, StudentAttempt, AttemptQuestion, AttemptQuestionOption, StudentResponse, DeliveryPolicy.

Responsibilities include async snapshot preparation, assessment launch, timer, navigation, autosave with stale-write protection, session recovery, and submission.

This module is intentionally separated from assessment authoring.

---

## 36. Evaluation module

Owns AnswerKey, AnswerKeyDetail, Evaluation, Result, Scorecard.

Responsibilities include scoring, answer-key detail management, evaluation versioning, ranking, percentile, bonus, grace marks, re-evaluation, result publication, and scorecard generation.

---

## 37. Analytics module

Owns StudentAnalytics, AssessmentAnalytics, QuestionAnalytics, TopicAnalytics, OrganizationAnalytics.

Responsibilities include aggregation, metrics, insights, dashboards, and derived data.

Analytics does not modify transactional source data.

---

## 38. Commerce module

Owns AccessPackage, AccessPackageCollection, SubscriptionPlan, Purchase, PaymentRecord, AccessGrant, Coupon.

Responsibilities include products and packages, subscriptions, purchases, payment lifecycle, refunds, coupons, and authorization grants.

---

## 39. Communication module

Owns Notification, EmailTemplate, Announcement.

Responsibilities include in-app notifications, email, announcements, delivery status, and retries.

---

## 40. Administration module

Owns OrganizationSetting, BusinessRule, Lookup, AuditLog.

Responsibilities include system configuration, operational control, auditing, master configuration, global search, imports, bulk operations, and support operations.

---

## 41. Infrastructure module

Owns BackgroundJob.

Responsibilities include asynchronous job persistence, worker claiming, job status tracking, and job history serving for reports, imports, bulk operations, analytics refresh, and scorecards.

Snapshot preparation is not stored here. Snapshot state lives on `student_attempt`.

---

## 42. Module dependency rules

Dependencies are directional.

Preferred.

```text
Delivery
   |
   v
Assessment service
```

Avoid.

```text
Assessment
   |
   v
Delivery repository
```

A module communicates with another module through its public service or application interface.

---

## 43. Entity access rule

Do not freely access another module's JPA entities.

Bad.

```java
@Autowired
private AssessmentEntityRepository assessmentRepository;
```

inside an unrelated module.

Preferred.

```java
assessmentService.getPublishedAssessment(...)
```

This protects module boundaries.

---

## 44. DTO boundary rule

REST controllers accept and return DTOs. Never expose JPA entities directly.

```text
HTTP request
     |
     v
Request DTO
     |
     v
Validation
     |
     v
Service
     |
     v
Entity
```

Response.

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
HTTP
```

---

## 45. Service layer rule

Business rules belong primarily in services.

Controller responsibilities: HTTP, authentication context, validation trigger, request mapping, response mapping.

Controller does not contain scoring, payment rules, access decisions, assessment publication rules, complex SQL, or workflow orchestration.

---

## 46. Repository rule

Repositories handle persistence.

Repositories may contain query methods, specifications, optimized projections, and database-specific queries where justified.

Repositories do not contain business workflows.

---

## 47. Transaction rule

Transactions belong at business-operation boundaries.

Example.

```text
Publish assessment
        |
        v
@Transactional
        |
        v
Validate
        |
        v
Update
        |
        v
Audit
        |
        v
Commit
```

Do not create unnecessarily large transactions covering unrelated operations.

Audit insertion participates in the business transaction. Audit failure rolls back the business mutation.

---

## 48. API architecture

Base path.

```text
/api/v1
```

Examples.

```text
/api/v1/auth/login
/api/v1/exams
/api/v1/questions
/api/v1/assessments
/api/v1/attempts
/api/v1/results
/api/v1/reports
```

---

## 49. API versioning

Version 1 is represented in the URL.

```text
/api/v1
```

Breaking changes introduce `/api/v2` rather than silently changing V1 behavior.

---

## 50. API state transition rule

Do not allow clients to arbitrarily manipulate important workflow states.

Prefer.

```text
POST /assessments/{id}/publish
```

over.

```text
PUT /assessments/{id}
{
  "status": "PUBLISHED"
}
```

Similarly.

```text
POST /attempts/{id}/submit
POST /attempts/{id}/re-evaluate
POST /results/{id}/publish
POST /purchases/{id}/refund
POST /announcements/{id}/publish
```

This makes workflows explicit and auditable.

---

## 51. API response standard

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
  "message": "Records retrieved successfully",
  "data": [],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

Error.

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
  "path": "/api/v1/users"
}
```

---

## 52. API error architecture

Standard categories.

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

HTTP mapping.

| HTTP | Meaning |
|---|---|
| 200 | Success |
| 201 | Created |
| 202 | Accepted, asynchronous |
| 204 | No content |
| 400 | Invalid request |
| 401 | Authentication required or failed |
| 403 | Forbidden |
| 404 | Not found |
| 409 | Conflict or invalid state |
| 412 | Precondition failed |
| 422 | Business validation failure |
| 429 | Rate limited |
| 500 | Server error |
| 503 | Dependency unavailable |

---

## 53. Idempotency architecture

Idempotency is mandatory for critical operations.

Required areas.

```text
Payment callbacks
Attempt submission
Response save where necessary
Notification delivery
Background jobs
Bulk operations
Report requests
```

The server detects duplicate processing and returns the original logical result where appropriate.

---

## 54. Assessment delivery architecture

Assessment delivery receives special architectural treatment.

```text
Student browser
      |
      v
Next.js delivery UI
      |
      v
Delivery API
      |
      +-- Session validation
      +-- Access validation
      +-- Attempt validation
      +-- Response processing
      |
      v
PostgreSQL
```

Redis may be used for carefully selected short-lived optimizations. PostgreSQL remains authoritative.

---

## 55. Assessment session design

A session has an authenticated student, an assessment version, an attempt, a start time, expiry information, session status, and recovery capability.

The backend determines whether a session is valid. The browser timer is a presentation mechanism, not the authoritative clock.

---

## 56. Timer architecture

The backend calculates authoritative timing from server start time plus configured duration.

The client displays remaining time but is not trusted to determine whether the attempt is still valid. This prevents simple client-side timer manipulation.

---

## 57. Autosave architecture

Student responses are persisted incrementally.

```text
User action
     |
     v
Client state
     |
     v
Debounced API
     |
     v
Response service
     |
     v
Validation
     |
     v
Database
```

The client indicates saving, saved, failed, and retrying.

The save endpoint uses HTTP conditional requests to prevent stale writes.

---

## 58. Response conflict handling

If multiple response-save requests arrive:

- Validate attempt state.
- Validate question membership.
- Validate response format.
- Maintain latest valid response.
- Use ETag and If-Match to reject stale writes.

The system does not silently overwrite a newer answer with an older network-delayed request.

---

## 59. Submission architecture

Submission is a state transition.

```text
IN_PROGRESS
      |
      v
SUBMIT_REQUEST
      |
      v
Validate session
      |
      v
Persist final state
      |
      v
SUBMITTED
      |
      v
Evaluation
```

Double submission is safe. A second submission request does not create another attempt or a duplicate result.

---

## 60. Evaluation architecture

Evaluation is deterministic.

Inputs.

```text
Attempt
+
Assessment version
+
Answer key
+
Answer key detail
+
Scoring configuration
```

Output.

```text
Evaluation
+
Result
+
Scorecard
```

Given identical immutable inputs, the calculation produces the same result.

---

## 61. Answer key architecture

Answer keys are versioned.

```text
Answer key V1
      |
      v
Published
      |
      v
Correction
      |
      v
Answer key V2
      |
      v
Re-evaluation
```

Evaluation reads correctness only from `answer_key_detail`. The `question_option.is_correct` field is an authoring default and is not consulted at evaluation time.

---

## 62. Evaluation versioning architecture

Evaluation is versioned.

```text
Attempt
   |
   v
Evaluation v1 (current)
   |
   v
Re-evaluation
   |
   v
Evaluation v2 (current)
   |
   +-- v1 superseded
```

A partial unique index enforces at most one current evaluation per attempt.

---

## 63. Published artifact immutability

The following published versions are immutable.

- Question version
- Blueprint version
- Assessment version
- Answer key version

Corrections create a new version.

This provides reproducibility.

---

## 64. Historical reproducibility

The platform answers the question.

> Why did this student receive this result?

Using historical data.

The required chain.

```text
Student attempt
      |
      v
Assessment version
      |
      v
Question versions
      |
      v
Answer key version
      |
      v
Answer key detail
      |
      v
Evaluation rules
      |
      v
Evaluation
      |
      v
Result
```

Historical records do not depend on mutable current configuration.

---

## 65. Commerce architecture

Commerce follows.

```text
Catalog
  |
  v
Access package
  |
  v
Purchase
  |
  v
Payment
  |
  v
Access grant
  |
  v
Assessment authorization
```

---

## 66. Access grant principle

AccessGrant is the authorization source of truth.

The system does not determine access using payment success alone.

Instead.

```text
Student
 +
Assessment or collection access
 +
Valid access grant
 =
Authorized
```

This makes access auditable, revocable, time-bound, package-aware, and independent of payment implementation.

---

## 67. Payment architecture

Payment providers are isolated behind a service boundary.

```text
Commerce service
       |
       v
Payment gateway interface
       |
       v
Provider adapter
       |
       v
External gateway
```

The core commerce module is not tightly coupled to one payment provider.

---

## 68. File storage architecture

Development: local filesystem.

Production: S3-compatible object storage.

Files include question images, question documents, profile images, organization assets, report files, and imported files.

---

## 69. File storage rules

Every upload validates file size, extension, MIME type, storage path, and filename safety.

Store generated filename, original filename, content type, size, and storage location.

Do not trust client-provided filenames as storage keys.

---

## 70. File access

Public resources may be publicly accessible when intentionally configured. Protected resources require authorization.

Examples of protected resources: student report, internal question asset, administrative document, report artifact, import staging file.

All protected downloads go through the authenticated file endpoint.

---

## 71. Background processing

Long-running operations do not block HTTP requests.

Examples: report generation, analytics aggregation, email delivery, large imports, bulk operations, scorecard generation, cleanup, and attempt snapshot preparation.

Pattern.

```text
HTTP request
     |
     v
Validate
     |
     v
Create job
     |
     v
202 Accepted
     |
     v
Background worker
     |
     v
Job status
```

---

## 72. Job reliability

Jobs support retry, failure status, logging, idempotency, observability, eventual completion, and safe restart.

A job does not produce duplicate business effects after retry.

Job state is persisted in `background_job`. Workers claim jobs by conditional state transition.

---

## 73. Caching architecture

Recommended cache hierarchy.

```text
Frontend cache
      |
      v
API
      |
      v
Redis
      |
      v
PostgreSQL
```

Cache appropriate read-heavy configuration. Do not cache data whose correctness depends on immediate transactional state unless explicit invalidation and consistency rules exist.

---

## 74. Cache invalidation

When configuration changes.

```text
Update PostgreSQL
      |
      v
Invalidate Redis
      |
      v
Future request
      |
      v
Read fresh value
```

The database update remains authoritative.

---

## 75. Analytics architecture

Analytics is derived from transactional records.

```text
Transactional tables
       |
       v
Aggregation service and jobs
       |
       v
Analytics tables
       |
       v
Read APIs
       |
       v
Dashboards
```

Analytics tables are rebuildable.

---

## 76. Reporting architecture

Supported formats: PDF and CSV.

Large reports are generated asynchronously.

```text
Generate report
      |
      v
202 Accepted
      |
      v
Job id
      |
      v
Background processing
      |
      v
Report history
      |
      v
Download
```

Report history is served from `background_job`. Artifacts are delivered through the protected file endpoint.

---

## 77. Notification architecture

Notifications are event-driven internally.

```text
Assessment published
       |
       v
Notification service
       |
       v
Create notification
       |
       v
In-app delivery
       +
Email delivery
```

Failures in email delivery do not roll back the original business transaction.

---

## 78. Audit architecture

Audit logs are immutable.

Record important events such as login, logout, role assignment, question publication, assessment publication, assessment schedule changes, answer-key changes, result publication, payment and refund, access changes, and administrative actions.

Audit logs capture who, what, when, where, target, and remarks.

Audit insertion participates in the business transaction. Audit failure rolls back the business mutation.

Background operations use the system actor.

---

## 79. Observability stack

Selected.

```text
Prometheus
     |
     v
Metrics
     |
     v
Grafana
     |
     v
Dashboards
```

Logs.

```text
Application
     |
     v
Loki
     |
     v
Grafana
```

---

## 80. Monitoring categories

### Infrastructure

CPU, memory, disk, container health.

### Application

Request count, latency, errors, JVM, thread pools.

### Database

Connections, query latency, locks, storage, slow queries.

### PgBouncer

Active clients, waiting clients, pool utilization, errors.

### Redis

Availability, memory, hit rate.

### Assessment

Active sessions, starts, submissions, response failures, recovery events, snapshot readiness.

### Commerce

Payments, callback failures, refunds, access-grant failures.

### Reporting

Report queue depth, generation latency, artifact failures.

---

## 81. Logging architecture

Structured logging.

```json
{
  "timestamp": "2026-10-07T10:00:00Z",
  "level": "INFO",
  "module": "delivery",
  "operation": "attempt.submit",
  "requestId": "uuid",
  "userId": "uuid",
  "attemptId": "uuid",
  "durationMs": 145,
  "status": "SUCCESS"
}
```

Never log secrets.

---

## 82. Correlation ID

Every request receives or propagates an `X-Request-Id`.

This identifier appears in application logs, error responses where appropriate, audit records where useful, and downstream requests.

---

## 83. Security architecture

Security baseline.

```text
HTTPS
+
JWT
+
Password hashing
+
RBAC
+
Permission checks
+
Input validation
+
Secure file handling
+
Audit logging
+
Secure configuration
+
System actor isolation
```

---

## 84. Security exclusions

The following are not mandatory features.

- OAuth
- SSO
- 2FA
- Advanced proctoring
- Biometric verification
- AI proctoring

They may be introduced in a later version.

Their absence does not weaken the existing authentication and authorization model.

---

## 85. Secrets management

Secrets are never committed to Git.

Examples: JWT secrets, database passwords, payment credentials, email credentials, object-storage credentials.

Use environment-specific secret management.

---

## 86. Configuration architecture

Configuration categories.

```text
Application
Database
Redis
PgBouncer
Security
Email
Storage
Payment
Business rules
Monitoring
```

Configuration is externalized.

Do not hardcode URLs, credentials, environment-specific paths, payment keys, or email credentials.

---

## 87. Environment configuration

Recommended profiles.

```text
application-local.yml
application-dev.yml
application-test.yml
application-staging.yml
application-prod.yml
```

Secrets are not stored directly in files that are committed to source control.

---

## 88. Docker architecture

Services are containerized.

```text
+--------------+
|    Nginx     |
+------+-------+
       |
 +-----+-----+
 v           v
Next.js   Spring Boot
              |
        +-----+-----+
        v           v
   PgBouncer     Redis
        |
        v
   PostgreSQL
```

Monitoring components may be deployed separately depending on environment.

---

## 89. Nginx responsibility

Nginx provides TLS termination, reverse proxy, routing, compression, static resource handling, request limits, security headers, and frontend and backend routing.

Nginx does not contain business logic.

---

## 90. CI/CD architecture

GitHub Actions automates.

```text
Commit
 |
 v
Build
 |
 v
Test
 |
 v
Static analysis
 |
 v
Security checks
 |
 v
Docker build
 |
 v
Artifact
 |
 v
Deploy staging
 |
 v
Validation
 |
 v
Production approval
```

---

## 91. Quality gates

A build is not promoted when compilation fails, tests fail, migration fails, API contract checks fail, frontend build fails, or critical security checks fail.

---

## 92. Testing architecture

```text
Unit
  |
  v
Integration
  |
  v
API
  |
  v
Component and UI
  |
  v
End-to-end
  |
  v
Performance
  |
  v
Security
  |
  v
Concurrency
  |
  v
Failure injection
```

Critical business calculations receive strong unit coverage.

---

## 93. Assessment testing priority

Assessment delivery is a critical subsystem.

Testing covers concurrent starts, concurrent saves, network interruption, browser refresh, browser crash, session recovery, timer expiration, duplicate submission, delayed requests, stale responses, and scoring correctness.

---

## 94. Performance architecture

Performance optimization priorities.

1. Assessment delivery.
2. Question retrieval.
3. Response persistence.
4. Evaluation.
5. Analytics.
6. Reports.

Do not optimize based only on theoretical performance. Use production measurements.

---

## 95. Scalability strategy

Initial scaling model.

```text
Multiple application instances
          |
          v
Shared PostgreSQL
          +
Shared Redis
          +
Shared PgBouncer
          +
Shared object storage
```

The application remains stateless wherever practical.

---

## 96. Stateless application principle

Do not store critical session state only in application memory.

Persistent session and attempt state belongs in shared infrastructure.

---

## 97. Database scaling strategy

Current state.

```text
PostgreSQL primary
```

Future options if required: read replicas, partitioning, archival, and specialized analytics datastore.

Do not introduce these before actual workload requires them.

---

## 98. Redis scaling strategy

Current state: single Redis deployment suitable for expected workload.

Future: replication, Sentinel, cluster. Only when operational scale requires it.

---

## 99. Search strategy

The main search uses PostgreSQL search capabilities and indexed fields.

Do not introduce Elasticsearch or OpenSearch merely for architectural completeness.

---

## 100. Internationalization

The architecture remains i18n-ready.

The platform supports configurable question languages. The frontend avoids hardcoding user-visible text in components where practical.

---

## 101. Offline and PWA readiness

The platform is web-first. The architecture does not prevent future PWA support.

Examination correctness remains the primary concern.

---

## 102. Dark and light theme

The UI architecture is compatible with light and dark modes. Theme support is implemented at the design-system level.

---

## 103. Accessibility

Frontend targets accessible interfaces.

Important areas: keyboard navigation, semantic HTML, focus handling, labels, form errors, contrast, and screen-reader-friendly controls.

The assessment UI receives special attention because students may navigate primarily through keyboard interaction.

---

## 104. Browser compatibility

The platform supports modern browsers.

Primary targets: Chrome, Edge, Firefox, Safari.

Testing focuses on supported current versions rather than obsolete browsers.

---

## 105. Data retention

Retention is configurable where legally and operationally appropriate.

Historical examination records are not casually deleted because they may be required for result reproducibility.

---

## 106. Soft delete strategy

Use status-based lifecycle for appropriate entities.

Physical deletion is restricted to data where deletion is explicitly safe.

---

## 107. Published data rule

Once published.

```text
Question version  -> Immutable
Blueprint version -> Immutable
Assessment version -> Immutable
Answer key version -> Immutable
```

This is one of the most important architectural rules.

---

## 108. Business rule architecture

Business rules are configurable where the product requires configurability.

Examples: attempt limits, scoring, negative marking, subscription duration, access validity, assessment timing, navigation, and coupon rules.

Not every piece of software behavior becomes configurable. Configuration is justified by a real business requirement.

---

## 109. Configuration vs code rule

Use configuration for "what should the examination do?"

Use code for "how does the platform implement that capability?"

Example.

```text
Configuration: Negative marking = 0.25

Code: Evaluation engine calculates negative marking.
```

---

## 110. Domain event readiness

The application is capable of introducing internal events.

Examples.

```text
QuestionPublished
AssessmentPublished
AssessmentStarted
AssessmentSubmitted
ResultPublished
PaymentCompleted
AccessGranted
```

Initially these remain internal application events. A distributed event broker is not required.

---

## 111. Future AI architecture

AI remains outside the critical scoring path unless explicitly validated.

Potential AI services: AI question generator, AI difficulty estimator, AI explanation generator, AI recommendation engine, AI search, AI admin assistant.

AI outputs are treated as generated, reviewed, approved, and published for examination content.

---

## 112. AI safety principle

AI must not silently alter official examination data.

Specifically, answer keys, published questions, official results, scoring rules, and student marks remain deterministic and controlled.

---

## 113. Technology exclusions

The following are intentionally not part of the mandatory stack.

- Kubernetes
- Kafka
- Elasticsearch or OpenSearch
- GraphQL
- MongoDB
- Cassandra
- Microservices
- Service mesh
- OAuth or SSO
- 2FA
- Advanced proctoring
- Mobile-native applications
- AI-driven scoring

These can be reconsidered when actual requirements justify them.

---

## 114. Technology selection rule

Future technology additions must answer the following questions.

1. What problem does it solve?
2. Why can't the current stack solve it?
3. What operational complexity does it add?
4. What is the migration cost?
5. Does it introduce a new source of truth?
6. Does it affect existing APIs?
7. Does it affect deployment?
8. Does it affect security?
9. Does it improve measurable system capability?

If these questions cannot be answered, the technology is not added merely because it is popular.

---

## 115. Architecture decision matrix

| Decision | Choice | Reason |
|---|---|---|
| Backend architecture | Modular monolith | Simplicity and domain boundaries |
| Backend language | Java 25 | Modern enterprise baseline |
| Compatibility | Java 21 | Enterprise fallback |
| Backend framework | Spring Boot 4 | Mature Java ecosystem |
| Security | Spring Security | Integrated security |
| ORM | Hibernate and JPA | Relational domain model |
| Database | PostgreSQL | Strong transactional model |
| Cache | Redis | Fast configuration and cache |
| Connection pooling | PgBouncer | Production connection management |
| Migration | Flyway | Controlled schema evolution |
| Frontend | Next.js | Public and authenticated experience |
| Frontend language | TypeScript | Type safety |
| UI | shadcn/ui | Reusable accessible primitives |
| Styling | Tailwind | Consistent utility styling |
| Server state | TanStack Query | API and server-state management |
| Forms | React Hook Form | Efficient complex forms |
| Validation | Zod | Frontend schema validation |
| HTTP | Axios | Centralized API client |
| Content rendering | KaTeX SSR | Mathematical content |
| Reverse proxy | Nginx | Production routing |
| Containers | Docker | Consistent environments |
| CI/CD | GitHub Actions | Integrated repository automation |
| Metrics | Prometheus | Standard metrics |
| Dashboards | Grafana | Visualization |
| Logs | Loki | Centralized logging |
| Async jobs | background_job table | Persisted job state |

---

## 116. Architecture invariants

The following invariants remain true.

1. PostgreSQL is the transactional source of truth.
2. AccessGrant is the authorization source of truth for purchased and free access.
3. Published examination artifacts are immutable.
4. Backend authorization cannot be bypassed through frontend behavior.
5. Assessment timing is backend authoritative.
6. Evaluation is deterministic for immutable inputs.
7. Critical operations are idempotent.
8. Analytics are derived from transactional data.
9. Schema changes are managed by Flyway.
10. REST API contracts are versioned.
11. Start Exam performs no synchronous snapshot generation.
12. Audit insertion is transactional with the business mutation.
13. Background jobs are claimed by conditional state transition.
14. System actor represents background and scheduled operations.
15. Stale-write protection uses HTTP conditional requests.
16. Evaluation reads correctness only from `answer_key_detail`.

---

## 117. Architectural anti-patterns

The following are rejected during code review.

### God service

```text
ExamService
    |
    v
Everything
```

Keep services focused.

### Shared entity abuse

```text
Every module
    |
    v
Every entity
```

Avoid unrestricted access.

### Controller business logic

```text
Controller
    |
    v
1000 lines of business logic
```

Reject.

### Direct database manipulation

```text
Admin UI
   |
   v
SQL
```

Reject.

### Frontend authorization

```text
Hidden button
=
Security
```

Reject.

### Cache as source of truth

```text
Redis
 |
 v
Official result
```

Reject.

### Mutable published content

```text
Published question
 |
 v
Direct edit
```

Reject.

### Synchronous snapshot generation on Start

```text
Start Exam
   |
   v
2.5M-row burst
```

Reject.

---

## 118. Architecture review checklist

Before approving a major feature, ask.

```text
[ ] Does it respect module boundaries?
[ ] Does it use the correct service layer?
[ ] Does it require a schema migration?
[ ] Does it change an API?
[ ] Does it affect published data?
[ ] Does it affect scoring?
[ ] Does it affect access authorization?
[ ] Does it introduce concurrency risks?
[ ] Does it require idempotency?
[ ] Does it require background processing?
[ ] Does it require audit logging?
[ ] Does it expose sensitive data?
[ ] Does it affect performance?
[ ] Does it affect security?
[ ] Is the change configuration or code?
[ ] Does it require an architecture-version change?
```

---

## 119. Runtime architecture freeze

```text
                              Internet / Users
                                    |
                                  HTTPS
                                    v
                           +------------------+
                           |       NGINX      |
                           | Reverse Proxy    |
                           | TLS / Routing    |
                           +--------+---------+
                                    |
                     +--------------+--------------+
                     |                             |
                     v                             v
              +-------------+             +---------------------+
              |   Next.js   |             |   Spring Boot 4     |
              |  Frontend   | REST/JSON   |   Modular Monolith  |
              +-------------+             +----------+----------+
                                                     |
      +----------------------------------------------+------------------------------------------------+
      |              |             |             |    |       |        |             |                  |
      v              v             v             v    v       v        v             v                  v
  Identity        Academic      Content      Assessment Delivery Evaluation Analytics Commerce Communication
      |              |             |             |    |       |        |             |                  |
      +--------------+-------------+-------------+----+-------+--------+-------------+------------------+
                                                     |
                                +--------------------+--------------------+
                                v                    v                    v
                         Administration      Infrastructure        Object storage
                                                     |
                              +----------------------+----------------------+
                              v                      v                      v
                         PgBouncer               Redis              PostgreSQL
                              |                  (cache)           (source of truth)
                              v
                         PostgreSQL
```

---

## 120. Core examination flow

```text
Exam configuration
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

---

## 121. Commerce flow

```text
Catalog
   |
   v
Access package
   |
   v
Purchase
   |
   v
Payment
   |
   v
Access grant
   |
   v
Assessment authorization
```

Payment integration.

```text
Commerce module
      |
Payment gateway interface
      |
Provider adapter
      |
External payment gateway
```

AccessGrant is the authorization source of truth.

---

## 122. Background processing

```text
HTTP request
     |
     v
Validate
     |
     v
Create job
     |
     v
202 Accepted
     |
     v
Background worker
     |
     v
Job status and result
```

Asynchronous work includes report generation, analytics aggregation, email delivery, large imports, bulk operations, scorecard generation, cleanup, and retryable background processing.

Attempt snapshot preparation uses the `student_attempt` record's snapshot state and its own worker, not the `background_job` table.

---

## 123. Observability

```text
Application
   |
   +-- Metrics --> Prometheus --> Grafana
   |
   +-- Logs    --> Loki       --> Grafana
```

---

## 124. CI/CD and deployment

```text
GitHub
   |
   v
GitHub Actions
   |
   v
Build and test
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

---

## 125. Architecture rules

The frozen architecture requires the following.

1. Nginx is the production reverse-proxy and ingress boundary.
2. Next.js is the web frontend.
3. Spring Boot is one modular-monolith backend.
4. All eleven business modules remain explicit.
5. PostgreSQL is the transactional source of truth.
6. Redis is a supporting cache, never the authoritative store for transactional data.
7. PgBouncer manages production database connections.
8. Object storage holds files and generated artifacts.
9. Long-running operations use background jobs.
10. Backend authorization remains authoritative.
11. Published examination artifacts remain immutable.
12. AccessGrant remains the authorization source of truth.
13. Observability uses Prometheus, Grafana, and Loki.
14. Content rendering uses MARKDOWN_LATEX_V1 with KaTeX server-side rendering.
15. ExamEngine resolution reads from persisted assessment configuration.
16. Evaluation versioning and answer key detail are mandatory.
17. Audit is transactional and append-only.
18. Stale-write protection uses HTTP conditional requests.
19. Start Exam performs no synchronous snapshot generation.
20. Background jobs are claimed by conditional state transition.

No microservices, Kafka, Kubernetes, GraphQL, Elasticsearch, OpenSearch, or another distributed platform are introduced without formal architecture review.

---

## 126. Final technology stack

### Backend

```text
Java 25
Spring Boot 4
Spring Security
Spring Data JPA
Hibernate
Maven
```

### Database

```text
PostgreSQL
PgBouncer
Flyway
```

### Cache

```text
Redis
```

### Frontend

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

### Content rendering

```text
Unified / Remark-compatible Markdown pipeline
KaTeX server-side rendering
Rehype-compatible sanitization
Asset shortcode resolver
```

### Infrastructure

```text
Docker
Docker Compose
Nginx
```

### DevOps

```text
GitHub
GitHub Actions
```

### Observability

```text
Prometheus
Grafana
Loki
```

### Storage

```text
Local filesystem in development
S3-compatible object storage in production
```

---

## 127. Final architecture principle

The platform is not designed as "a website that conducts tests."

It is designed as a configurable examination engine with a web-based learning, assessment, commerce, analytics, and administration platform around it.

The architecture protects five things above everything else.

1. Examination correctness.
2. Data integrity.
3. Historical reproducibility.
4. Authorization correctness.
5. Operational reliability.

Everything else is optimized around these foundations.

---

## 128. Architecture freeze statement

The implementation baseline is.

```text
Modular Monolith
+
Java 25 and Spring Boot 4
+
PostgreSQL
+
PgBouncer
+
Redis
+
Flyway
+
Next.js
+
TypeScript
+
KaTeX SSR
+
Docker
+
Nginx
+
GitHub Actions
+
Prometheus
+
Grafana
+
Loki
```

No major technology substitution occurs during implementation without a formal architecture review.

Minor implementation improvements that remain inside the frozen architecture proceed through normal engineering review.

Major changes increment the architecture version and trigger review of product requirements, domain model, database schema, API contract, development roadmap, testing strategy, and deployment strategy.

---

## 129. Relationship to development roadmap

This document establishes what technology and architecture must be built.

The Development Roadmap establishes in what order it must be built.

Together they form the engineering baseline.

---

## 130. Document status

**Status:** FROZEN architecture and technology baseline.

This document is the architecture reference for backend architecture, frontend architecture, module boundaries, technology selection, deployment, testing strategy, and operational readiness.