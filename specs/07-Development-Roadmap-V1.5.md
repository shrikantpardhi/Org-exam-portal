# Smart Competitive Examination Platform
## Development Roadmap

**Version:** 1.5
**Status:** FROZEN
**Architecture:** Modular Monolith
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Connection Pooling:** PgBouncer
**Migration:** Flyway
**Deployment:** Docker + Nginx + PgBouncer
**Repository:** GitHub
**CI/CD:** GitHub Actions

---

## 1. Purpose

This document converts the product requirements, domain model, database standards, API contract, and architecture freeze into an executable engineering roadmap.

It defines implementation order, module dependencies, sprint sequence, backend work, frontend work, database work, API work, testing work, DevOps work, security checkpoints, performance checkpoints, release gates, definition of done, production-readiness requirements, change-control process, and the future AI roadmap.

The roadmap is dependency-driven. The team does not attempt to build all modules simultaneously.

Recommended sequence.

```text
Foundation
    |
    v
Identity
    |
    v
Academic masters
    |
    v
Question bank
    |
    v
Blueprint
    |
    v
Assessment builder
    |
    v
Assessment delivery
    |
    v
Evaluation
    |
    v
Analytics
    |
    v
Communication
    |
    v
Commerce
    |
    v
Administration
    |
    v
Reporting
    |
    v
Hardening
    |
    v
Production
```

---

## 2. Implementation philosophy

The platform is built as a modular monolith with strict domain boundaries.

The goal is not merely to make the first version work. The goal is to create a codebase that can later evolve into distributed services without requiring a complete rewrite.

The implementation follows these principles.

1. Build domain foundations before dependent features.
2. Database schema follows the approved domain model.
3. APIs follow the frozen API contract.
4. Published examination data is immutable.
5. Business rules remain in backend services.
6. Frontend is not the source of truth for authorization.
7. Every important workflow has explicit state transitions.
8. Every module has automated tests.
9. Every schema change uses Flyway.
10. Every production change is version controlled.
11. Configuration is externalized.
12. Sensitive data is never exposed through APIs or logs.
13. Long-running operations are asynchronous.
14. Analytics is derived from transactional data.
15. Commerce authorization is based on AccessGrant.
16. Assessment delivery prioritizes correctness over convenience.
17. Performance optimization does not compromise exam correctness.
18. Snapshot preparation is asynchronous and outside Start Exam.

---

## 3. Development sequence

### 3.1 Dependency graph

```text
Common and Foundation
        |
        +-- Identity
        |
        +-- Academic
        |
        +-- Administration foundations
                 |
                 v
              Content
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
        +--------+--------+
        v                 v
    Analytics       Communication
        |                 |
        +--------+--------+
                 v
              Commerce
                 |
                 v
          Administration
                 |
                 v
             Reporting
                 |
                 v
         Production hardening
```

Some components can be developed in parallel once their dependencies exist.

---

## 4. Overall phase plan

| Phase | Primary area | Outcome |
|---|---|---|
| 1 | Foundation | Running application skeleton |
| 2 | Identity and security | Users, roles, authentication |
| 3 | Academic masters | Exam hierarchy and master data |
| 4 | Content | Question bank |
| 5 | Blueprint | Configurable exam structure |
| 6 | Assessment | Assessment creation and publishing |
| 7 | Delivery | Student test-taking engine |
| 8 | Evaluation | Scoring and results |
| 9 | Analytics | Performance intelligence |
| 10 | Communication | Notifications and announcements |
| 11 | Commerce | Subscriptions and access |
| 12 | Administration | Operational control plane |
| 13 | Reporting | Broader reporting and operational reporting |
| 14 | Hardening | Security, performance, reliability |
| 15 | Production | Release and operational readiness |
| 16 | Phase 2 | AI and advanced capabilities |

---

## 5. Team workstream structure

Development is divided into workstreams.

### 5.1 Backend workstream

Responsible for Java, Spring Boot, Spring Security, JPA and Hibernate, PostgreSQL, Redis, PgBouncer integration, Flyway, REST APIs, business logic, background jobs, and integrations.

### 5.2 Frontend workstream

Responsible for Next.js, TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, React Hook Form, Zod, the student portal, the staff workspace, the admin portal, and responsive UI.

### 5.3 Database workstream

Responsible for schema, migrations, indexes, constraints, seed data, query optimization, backup validation, and data integrity.

Database changes are reviewed by backend owners.

### 5.4 QA workstream

Responsible for test strategy, functional testing, API testing, integration testing, browser testing, assessment workflow testing, regression testing, performance testing, security testing, and release validation.

### 5.5 DevOps workstream

Responsible for Docker, environments, GitHub Actions, deployment, Nginx, PgBouncer, monitoring, logging, backup, rollback, and infrastructure configuration.

---

## 6. Phase 1 - Foundation

### 6.1 Objective

Create the technical foundation required by every subsequent module.

### 6.2 Backend

Create.

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

Implement Spring Boot application, environment configuration, PostgreSQL connection, PgBouncer wiring for production profiles, Flyway, JPA, Redis, security foundation, global exception handling, API response wrapper, pagination, common DTOs, audit fields, UUID generation, status handling, request correlation ID, logging framework, OpenAPI, health endpoint, and the general-purpose background job table foundation.

### 6.3 Frontend

Create.

```text
app/
components/
features/
lib/
hooks/
services/
types/
schemas/
layouts/
```

Implement Next.js application, TypeScript, Tailwind, shadcn/ui, API client, authentication state foundation, error handling, loading states, form infrastructure, reusable tables, reusable filters, pagination component, modal and dialog infrastructure, notification and toast infrastructure, and responsive layout.

### 6.4 DevOps

Create GitHub repository, branch protection, Dockerfile, Docker Compose, local PostgreSQL, local Redis, local PgBouncer, local application, local frontend, GitHub Actions, and basic CI pipeline.

### 6.5 Definition of done

- Application starts successfully.
- Database connection works.
- Flyway migration executes.
- Redis connection works.
- PgBouncer path verified.
- Frontend starts.
- Backend API is reachable.
- Frontend can call backend.
- Swagger is available.
- Health endpoint works.
- Docker Compose starts the stack.
- CI builds successfully.
- Unit tests execute in CI.

---

## 7. Phase 2 - Identity and security

### 7.1 Objective

Implement authentication, authorization, and user management.

### 7.2 Database

Create organization, user, role, permission, user_role, role_permission, and login_session.

Seed initial roles, permissions, and the system actor.

### 7.3 Backend

Implement login, logout, refresh token, password reset, current user, permissions, role management, user management, session management, password hashing, JWT, authorization checks, and system actor login exclusion.

Security hierarchy.

```text
Authentication
      |
      v
User
      |
      v
Roles
      |
      v
Permissions
      |
      v
API authorization
```

### 7.4 Frontend

Implement login, forgot password, reset password, session handling, protected routes, permission-aware navigation, profile, and logout.

### 7.5 Security tests

Test invalid credentials, expired token, revoked session, unauthorized resource, forbidden resource, role restrictions, permission restrictions, password reset, session timeout, and system actor login rejection.

---

## 8. Phase 3 - Academic masters

### 8.1 Objective

Create the academic hierarchy required by the question bank and assessment engine.

### 8.2 Hierarchy

```text
Exam
  |
  v
Subject
  |
  v
Chapter
  |
  v
Topic
  |
  v
SubTopic
```

Supporting masters: Language and Tag.

### 8.3 Backend

Implement complete CRUD APIs with validation, status handling, search, filtering, pagination, sorting, and duplicate prevention.

### 8.4 Frontend

Create exam management, subject management, chapter management, topic management, sub-topic management, language management, and tag management.

### 8.5 Important rule

Academic masters are not physically deleted when referenced by transactional or historical records. Use ACTIVE, INACTIVE, ARCHIVED.

---

## 9. Phase 4 - Question bank

### 9.1 Objective

Build the reusable question repository.

The question bank is critical because assessments depend on approved question versions.

### 9.2 Implementation order

```text
Question template
      |
      v
Question
      |
      v
Question version
      |
      v
Question options
      |
      v
Assets
      |
      v
Explanation
      |
      v
Hint
      |
      v
Previous exam history
      |
      v
Tags
      |
      v
Taxonomy mapping
      |
      v
Review
      |
      v
Approval
      |
      v
Publication
```

### 9.3 Backend

Implement question templates, question CRUD, question versioning, taxonomy mapping, option management, asset management, explanations, hints, previous-year mapping, tags, review submission, approval, rejection, publication, search, filters, and bulk import foundation.

### 9.4 Question version rule

Draft versions may change. Published versions do not change.

```text
Question Q100

Version 1 - Draft
Version 2 - In review
Version 3 - Approved and published
```

Once Version 3 is published, it is immutable. Any correction requires Version 4.

### 9.5 Review workflow

```text
DRAFT
  |
  v
IN_REVIEW
  |
  +-- approve --> APPROVED --> PUBLISHED
  |
  +-- reject  --> REJECTED --> edit --> DRAFT
```

Self-review is blocked when the same user holds both submit and review permissions.

### 9.6 Frontend

Implement question list, advanced filters, question editor, template selector, answer editor, option editor, explanation editor, asset uploader, metadata editor, taxonomy selector, previous-year mapping, tag selection, review workflow, version history, and preview.

### 9.7 Question quality gates

A question cannot be published unless.

- Question text exists.
- Valid question type exists.
- Required options exist where applicable.
- Correct answer exists where applicable.
- Marks exist.
- Negative marking is valid.
- Exam and subject mapping exist.
- Language is set.
- Content format is valid.
- Version is approved.

---

## 10. Phase 5 - Blueprint engine

### 10.1 Objective

Create configurable exam structures.

Blueprint determines what an assessment is supposed to contain. It does not itself represent the actual student attempt.

### 10.2 Blueprint structure

```text
Blueprint
 |
 v
Blueprint version
 |
 +-- Section A
 |     Subject
 |     Question count
 |     Marks
 |     Negative marks
 |     Time
 |
 +-- Section B
       Subject
       Question count
       Marks
       Negative marks
       Time
```

### 10.3 Backend

Implement blueprint CRUD, versioning, section configuration, question distribution, marks, negative marking, timing, navigation rules, language rules, calculator rules, scoring configuration, validation, and publishing.

### 10.4 Blueprint validation

Validate total questions equals the sum of section question counts. Validate total marks equal the sum of calculated section marks. Validate negative marking values, time limits, question availability, language availability, section configuration, and navigation constraints.

---

## 11. Phase 6 - Assessment engine

### 11.1 Objective

Turn a blueprint and question pool into a concrete assessment.

### 11.2 Assessment lifecycle

```text
DRAFT
  |
  v
VALIDATED
  |
  v
PUBLISHED
  |
  v
SCHEDULED
  |
  v
OPEN
  |
  v
CLOSED
```

### 11.3 Assessment creation modes

Manual: staff explicitly selects questions.

Rule-based: system selects questions using rules.

Hybrid: staff manually selects some questions while rules select the remainder.

### 11.4 Rule-based selection

Selection rules are stored on `assessment_selection_rule` and materialized into `assessment_question` rows during authoring.

Materialization algorithm.

1. Load eligible published question versions matching filters.
2. Exclude versions already used in this assessment version.
3. Apply deterministic ordering by difficulty, then least-recently-used, then id ascending.
4. Take exactly the target count.
5. If insufficient, fail with a structured shortage error.
6. Persist assessment question rows.
7. Record materialization metadata on the rule.

Once materialized, rules are provenance and are not re-executed at runtime.

### 11.5 Engine resolution

Assessment version carries engine type and delivery policy. Publication requires both. Engine type is immutable after publication.

Supported engine identifiers.

```text
TCS_ION
IBPS
NTA
UPSC
RAILWAY
CUSTOM
```

### 11.6 Assessment validation

Before publishing.

```text
Blueprint valid
        +
Questions available
        +
Question versions published
        +
Question count valid
        +
Section counts valid
        +
Marks valid
        +
Timing valid
        +
Language valid
        +
Scoring valid
        +
Engine type supported
        +
Delivery policy present
        |
        v
Assessment can publish
```

### 11.7 Frontend

Implement assessment list, assessment creation, builder, question selection, rule builder, hybrid selection, reorder, section configuration, preview, validation result, publish, schedule, and collection management.

---

## 12. Phase 7 - Assessment delivery engine

### 12.1 Objective

Build the student test-taking experience.

This is a high-risk module and receives additional testing.

### 12.2 Delivery flow

```text
Student
   |
   v
Access validation
   |
   v
Start assessment
   |
   v
Assessment session
   |
   v
Student attempt
   |
   v
Load questions from snapshot
   |
   v
Answer
   |
   v
Autosave with ETag
   |
   v
Navigation
   |
   v
Submit
   |
   v
Evaluation
```

### 12.3 Snapshot preparation

Snapshot preparation is asynchronous and occurs before the exam window.

```text
Schedule
   |
   v
Eligible students identified
   |
   v
Snapshot preparation job
   |
   v
StudentAttempt created (NOT_STARTED)
   |
   v
Batch create AttemptQuestion rows
   |
   v
Batch create AttemptQuestionOption rows
   |
   v
Validate snapshot completeness
   |
   v
Snapshot status = READY
```

Workers claim attempts by conditional state transition. Snapshot rows are immutable after READY.

Start Exam validates that snapshot status is READY and performs no snapshot child-row creation.

### 12.4 Start Exam flow

```text
1. Authenticate.
2. Authorize.
3. Verify access.
4. Verify schedule window.
5. Resolve prepared student attempt.
6. Verify attempt is eligible.
7. Verify snapshot status is READY.
8. Resolve exam engine.
9. Resolve delivery configuration.
10. Transition attempt to IN_PROGRESS.
11. Stamp started_on.
12. Create or activate lightweight session.
13. Return session context and normalized configuration.
```

### 12.5 Autosave

Responses are saved incrementally.

The save endpoint uses HTTP conditional requests. The GET response returns an ETag. The PUT accepts If-Match. Mismatch returns 412 with a stale-response error. The client re-fetches, merges, and retries.

This prevents an older delayed request from overwriting a newer accepted response.

### 12.6 Session recovery

```text
Login
  |
  v
Active session check
  |
  v
Recover session
  |
  v
Restore attempt
  |
  v
Restore responses
```

Recovery does not reconstruct the snapshot.

### 12.7 Delivery security

Never send correct answer, internal answer key, evaluation rules that could expose scoring internals, other student responses, or internal audit information to the student client.

### 12.8 Delivery performance target

Design for fast question loading, low-latency response saving, predictable navigation, safe concurrent sessions, and minimal database contention.

Load testing specifically simulates simultaneous students starting and submitting assessments.

---

## 13. Phase 8 - Evaluation and results

### 13.1 Objective

Convert submitted attempts into official results.

### 13.2 Evaluation flow

```text
Submitted attempt
       |
       v
Answer key + answer key detail
       |
       v
Evaluation
       |
       v
Score
       |
       v
Result
       |
       v
Scorecard
       |
       v
Ranking and percentile
```

### 13.3 Evaluation engine

Must support correct answers, incorrect answers, unattempted questions, positive marks, negative marks, question-specific negative marking, zero-negative questions, bonus marks, grace marks, answer-key versions, and re-evaluation.

### 13.4 Answer key detail

Evaluation reads correctness only from `answer_key_detail`. The `question_option.is_correct` field is an authoring default and is not consulted at evaluation time.

Outcome semantics.

- NORMAL: correct gives +marks, wrong gives negative, unanswered gives 0.
- BONUS: full marks awarded to every attempt.
- DROPPED: 0 marks, response ignored.

### 13.5 Score calculation

Conceptual formula.

```text
Score =
Sum of correct marks
-
Sum of applicable negative marks
+
Bonus marks
+
Grace marks
```

The exact calculation is configurable by assessment rules. All arithmetic uses BigDecimal. Minimum score has no floor. Rounding uses HALF_UP for obtained marks and percentage.

### 13.6 Evaluation versioning

Evaluation is versioned. A partial unique index enforces at most one current evaluation per attempt. Re-evaluation creates version N+1 and marks version N superseded.

### 13.7 Rank and percentile

Rank uses competition ranking over published results within the same assessment version.

Percentile.

```text
percentile = (count of candidates with score <= my score) / total * 100
```

Both are null until the result is published.

### 13.8 Result publication

Evaluation and publication are separate.

```text
Evaluated
   |
   v
Verified
   |
   v
Published
```

Students do not receive official results merely because an internal evaluation completed. Only published results are visible to students.

### 13.9 Re-evaluation

```text
Evaluation v1
     |
     v
Issue identified
     |
     v
Answer key v2
     |
     v
Re-evaluation
     |
     v
Evaluation v2 (current)
     |
     +-- Evaluation v1 superseded
```

Historical records remain auditable.

---

## 14. Phase 9 - Analytics

### 14.1 Objective

Transform transactional examination data into actionable insights.

### 14.2 Analytics layers

Student: attempts, scores, accuracy, speed, section performance, topic performance, progress, weak areas.

Assessment: total attempts, completion rate, average score, average time, question difficulty, section performance.

Question: attempts, correct percentage, incorrect percentage, skipped percentage, average response time, difficulty indicators.

Topic: performance, accuracy, progress, weak areas.

Organization: users, attempts, engagement, assessments.

### 14.3 Section time analytics

The platform retains enough information to calculate section A time, section B time, and section C time — not merely total attempt time.

Section time is derived from question-level timing and section assignment.

### 14.4 Analytics architecture

Transactional tables remain the source of truth. Analytics tables are derived.

```text
Transactional data
        |
        v
Aggregation jobs
        |
        v
Analytics tables
        |
        v
Dashboards
```

Analytics is recalculable.

---

## 15. Phase 10 - Communication

### 15.1 Objective

Implement user communication.

Build notifications, notification center, email templates, announcements, publishing, delivery status, and retry.

### 15.2 Notification flow

```text
Business event
      |
      v
Notification service
      |
      v
Notification record
      |
      v
Delivery queue
      |
      v
Email or in-app
      |
      v
Delivery status
```

Long-running or external delivery operations do not block transactional requests.

---

## 16. Phase 11 - Commerce

### 16.1 Objective

Implement paid access and subscription management.

### 16.2 Commerce dependency

Commerce integrates with assessment authorization.

```text
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

The assessment engine does not directly infer access from payment records.

### 16.3 Commerce modules

Implement access packages, package-to-collection mapping, subscription plans, purchases, payment records, payment callbacks, refunds, access grants, coupons, coupon validation, collections, and bundles.

### 16.4 Freemium

Support free assessment, access grant or free access, and student attempt.

A free assessment may have different duration behavior when configured as a practice test. The assessment configuration remains authoritative.

### 16.5 Payment safety

Payment callback processing is idempotent. If the same callback is received multiple times, the platform detects an existing transaction and ignores duplicates. No duplicate purchase or access grant is created.

---

## 17. Phase 12 - Administration

### 17.1 Objective

Complete the organization-level operational control plane.

Implement organization settings, business rules, lookups, user management, student management, role management, commerce operations, audit logs, operational monitoring, support console, master data import, bulk operations, global search, and system health dashboard.

### 17.2 Administration principles

Admin functionality does not bypass domain services.

```text
Admin controller
      |
      v
Assessment service
      |
      v
Business rules
      |
      v
Repository
```

Do not implement.

```text
Admin controller
      |
      v
Direct DB manipulation
```

### 17.3 Global search

PostgreSQL-backed search with a fixed resource registry. Each resource declares its searchable fields and required permission. Results are permission-filtered before the query executes. PII is masked by default.

### 17.4 System health

Administrative system health aggregates component status with bounded timeouts and a short cache. No health-history table is required.

---

## 18. Phase 13 - Reporting

### 18.1 Objective

Turn the report contract into a controlled, production-grade reporting capability.

### 18.2 Reporting architecture

```text
                 Report API
                     |
                     v
            Report application service
                     |
          +----------+----------+
          v                     v
 Report authorization      Report request validator
          |                     |
          +----------+----------+
                     v
               Report registry
                     |
        +------------+------------+
        v                         v
Small or bounded query     Async report job
        |                         |
        v                         v
 Report projector            Report worker
        |                         |
        +------------+------------+
                     v
                Report renderer
                     |
                     v
             Protected file store
                     |
                     v
              Authorized download
```

### 18.3 Report categories

Student, Assessment, Content, Commerce, Operations, Executive.

### 18.4 Report execution

Small reports may be generated synchronously. Large reports use the general-purpose background job table and are generated asynchronously.

### 18.5 Artifact delivery

Report artifacts are stored via the file storage abstraction and served through the protected file endpoint. No public URLs are exposed.

---

## 19. Hardening phase

After functional development, new features are frozen temporarily.

Focus on correctness, security, performance, reliability, UX, and operational readiness.

### 19.1 Security hardening

Validate authentication, authorization, JWT expiration, session invalidation, password hashing, password reset, input validation, SQL injection resistance, XSS protection, CSRF strategy, CORS, file upload security, access control, audit logging, sensitive-data masking, and error-message safety.

### 19.2 Performance hardening

Test login, dashboard, question search, assessment loading, question navigation, response save, submission, result generation, analytics, and report generation.

Focus especially on concurrent assessment start, concurrent response save, concurrent submission, and concurrent result generation.

### 19.3 Database performance

Review foreign-key indexes, search indexes, composite indexes, query plans, slow queries, pagination, large table scans, and analytics queries.

Do not add indexes blindly. Every major index has a query justification.

---

## 20. Testing strategy

### 20.1 Unit tests

Test business rules, calculations, validators, services, scoring, permission logic, coupon rules, access rules, review-state transitions, rule-based selection ordering, and evaluation versioning.

### 20.2 Repository and integration tests

Test JPA mappings, queries, constraints, transactions, Flyway migrations, and PostgreSQL-specific behavior.

Use a real PostgreSQL-compatible test environment where practical.

### 20.3 API tests

Every major API tests success, validation failure, unauthorized, forbidden, not found, conflict, invalid state, duplicate request, pagination, filtering, and precondition failure.

### 20.4 Frontend tests

Test components, forms, validation, navigation, API error states, permission-based UI, assessment UI, timer, and response state.

### 20.5 End-to-end tests

### Student

```text
Register or login
   |
   v
Browse assessment
   |
   v
Purchase or obtain free access
   |
   v
Start assessment
   |
   v
Answer questions
   |
   v
Submit
   |
   v
View result
   |
   v
View analytics
```

### Staff

```text
Login
 |
 v
Create question
 |
 v
Submit review
 |
 v
Approve
 |
 v
Publish
 |
 v
Create blueprint
 |
 v
Create assessment
 |
 v
Publish
 |
 v
Schedule
```

### Admin

```text
Login
 |
 v
Manage user
 |
 v
Assign role
 |
 v
Review audit
 |
 v
Manage business rules
 |
 v
Review system health
```

---

## 21. Assessment engine test matrix

### Timing

Test normal completion, timer expiration, browser refresh, browser crash, session recovery, network interruption, delayed save, and concurrent requests.

### Navigation

Test next, previous, direct question navigation, section navigation, mark for review, unanswered, answered, and navigation restrictions.

### Responses

Test answer save, answer update, duplicate save, delayed save, retry, offline recovery, submission race condition, and stale-write precondition handling.

### Submission

Test normal submission, double submission, submission after timeout, network retry, duplicate request, and session expiration.

### Snapshot

Test snapshot readiness, immutable snapshot rows, Start Exam without snapshot insertion, deterministic question ordering, deterministic option ordering, and preparation worker idempotency.

---

## 22. Data migration strategy

All database changes are delivered through Flyway.

The recommended migration range.

```text
V1  through V13  - initial schema and seeds
V100 through V107 - evaluation, content, delivery, assessment, infrastructure extensions
```

The migration naming strategy remains consistent across the repository.

### 22.1 Migration rules

Never manually modify production schema, delete old migrations, rewrite an already-applied migration, or make schema changes without migration scripts.

Allowed.

```text
New requirement
      |
      v
New migration
      |
      v
Review
      |
      v
CI
      |
      v
Deployment
```

---

## 23. Seed data strategy

Seed only controlled master data.

Potential seed data: initial organization, default roles, permissions, supported languages, default lookup values, default delivery policy, initial question templates, and the system actor.

Do not seed fake transactional data into production.

Development environments may include sample users, exams, subjects, questions, and assessments, clearly identified as development data.

---

## 24. Git strategy

Recommended repository branches.

```text
main
develop
feature/*
bugfix/*
hotfix/*
release/*
```

### 24.1 Feature branch

Examples.

```text
feature/question-versioning
feature/assessment-builder
feature/student-attempt
feature/payment-callback
feature/reporting
```

### 24.2 Pull request requirements

Every PR contains a description, affected module, database changes, API changes, tests, migration information, screenshots for significant UI changes, and security considerations where applicable.

### 24.3 Branch protection

`main` requires successful CI, review approval, tests passing, and no unresolved merge conflicts.

Production deployments occur from controlled release commits or tags.

---

## 25. CI pipeline

Recommended pipeline.

```text
Commit
  |
  v
Checkout
  |
  v
Dependency resolution
  |
  v
Compile
  |
  v
Unit tests
  |
  v
Static analysis
  |
  v
Frontend build
  |
  v
Backend build
  |
  v
Integration tests
  |
  v
Docker build
  |
  v
Security checks
  |
  v
Artifact
```

### 25.1 CI quality gates

A build fails if compilation fails, unit tests fail, integration tests fail, migration fails, frontend build fails, required static analysis fails, a critical security issue is detected, or API contract validation fails.

---

## 26. Environment strategy

Use separate environments.

```text
Local
   |
   v
Development
   |
   v
Test
   |
   v
Staging
   |
   v
Production
```

### 26.1 Local

Purpose: developer work, feature testing, debugging.

Infrastructure: Docker Compose, PostgreSQL, Redis, PgBouncer, local file storage.

### 26.2 Development

Purpose: integration between developers, early QA, feature validation.

### 26.3 Test

Purpose: formal QA, regression, automated end-to-end testing.

### 26.4 Staging

Closely resembles production. Used for release validation, migration testing, performance testing, and production-like workflows.

### 26.5 Production

Production provides HTTPS, backups, monitoring, logging, alerting, controlled deployment, rollback capability, database migration strategy, and access restrictions.

---

## 27. Deployment strategy

Recommended deployment flow.

```text
Git tag
   |
   v
CI
   |
   v
Build artifact
   |
   v
Docker image
   |
   v
Staging
   |
   v
Smoke test
   |
   v
Approval
   |
   v
Production
```

### 27.1 Deployment order

For releases containing schema and application changes.

```text
Backup
 |
 v
Database migration
 |
 v
Application deployment
 |
 v
Health check
 |
 v
Smoke test
```

For incompatible database changes, use an expand and contract strategy.

---

## 28. Rollback strategy

Application rollback.

```text
Current release
      |
      v
Failure
      |
      v
Previous stable image
      |
      v
Deploy
```

Database rollback does not depend on blindly reversing migrations.

Prefer forward-compatible migrations.

```text
V10 adds new nullable column
V11 application starts using column
V12 removes deprecated structure
```

This allows safer application rollback.

---

## 29. Observability

Monitor application request rate, response time, error rate, JVM health, memory, and CPU.

Database connection pool, query latency, slow queries, connection failures, and storage.

Redis availability, memory, and hit rate.

Jobs execution count, failure count, retry count, and duration.

Assessment delivery requires special monitoring: active sessions, response save failures, submission failures, session recovery events, snapshot readiness, and abnormal session termination.

---

## 30. Logging rules

Logs contain useful diagnostic context but never expose secrets.

Do not log passwords, JWT tokens, payment secrets, card information, sensitive personal information unnecessarily, or internal security credentials.

Useful fields.

```text
timestamp
level
service
module
requestId
userId
operation
duration
status
errorCode
```

---

## 31. Background job strategy

Jobs are created for report generation, email delivery, notifications, analytics aggregation, cleanup, large imports, bulk operations, and scorecard generation.

```text
POST /reports/generate
        |
        v
202 Accepted
        |
        v
jobId
        |
        v
Background worker
        |
        v
Report generated
```

Job execution is retry safe. Attempt snapshot preparation runs on its own worker and uses the `student_attempt` snapshot state, not the general-purpose background job table.

---

## 32. Bulk operation strategy

Bulk operations do not execute thousands of synchronous HTTP operations.

```text
Upload file
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
Process
     |
     v
Success and failure report
```

This applies to question import, student import, master data import, and bulk status operations.

---

## 33. Definition of done

A feature is done only when all applicable conditions are satisfied.

### Functional

- Requirement implemented.
- Business rules implemented.
- Edge cases handled.
- Invalid states rejected.

### Backend

- Entity.
- Repository.
- Service.
- DTO.
- Mapper.
- Validation.
- Controller.
- Exception handling.

### Database

- Migration.
- Constraints.
- Indexes.
- Foreign keys.
- Seed data if required.

### Frontend

- Screen.
- Loading state.
- Empty state.
- Error state.
- Validation.
- Responsive behavior.
- Authorization.

### API

- OpenAPI documentation.
- Request examples.
- Response examples.
- Error responses.

### Testing

- Unit tests.
- Integration tests.
- API tests.
- End-to-end test for critical workflows.

### Security

- Authorization verified.
- Sensitive fields protected.
- Input validated.
- Audit requirements considered.

### Operations

- Logs added.
- Monitoring considered.
- Background job retry behavior considered.

### Review

- Code review completed.
- CI successful.
- Migration reviewed.

---

## 34. Feature completion checklist

```text
[ ] Requirement implemented
[ ] Database migration added
[ ] API implemented
[ ] DTOs created
[ ] Validation added
[ ] Authorization verified
[ ] Frontend implemented
[ ] Error states handled
[ ] Unit tests added
[ ] Integration tests added
[ ] End-to-end test added where required
[ ] Swagger updated
[ ] Logging added
[ ] Audit requirements checked
[ ] Performance considered
[ ] Security reviewed
[ ] CI passing
```

---

## 35. Production readiness checklist

### Application

```text
[ ] All critical features complete
[ ] No critical defects
[ ] No unresolved release-blocking defects
[ ] Error handling verified
[ ] Configuration externalized
```

### Database

```text
[ ] Production migrations tested
[ ] Backup tested
[ ] Restore tested
[ ] Indexes reviewed
[ ] Constraints reviewed
```

### Security

```text
[ ] HTTPS
[ ] JWT security
[ ] Password hashing
[ ] RBAC
[ ] Authorization testing
[ ] File upload security
[ ] Security logging
[ ] Secrets outside source code
```

### Assessment

```text
[ ] Assessment start tested
[ ] Snapshot preparation tested
[ ] Autosave tested with ETag
[ ] Recovery tested
[ ] Timer tested
[ ] Submission tested
[ ] Duplicate submission protected
[ ] Evaluation tested
[ ] Answer key detail tested
[ ] Result publication tested
```

### Commerce

```text
[ ] Payment success tested
[ ] Payment failure tested
[ ] Callback tested
[ ] Callback idempotency tested
[ ] Refund tested
[ ] Access grant tested
```

### Reporting

```text
[ ] Report generation tested
[ ] Job history tested
[ ] Artifact delivery tested
[ ] Protected download verified
```

### Operations

```text
[ ] Monitoring
[ ] Logs
[ ] Alerts
[ ] Backup
[ ] Restore
[ ] Rollback
[ ] Health checks
```

---

## 36. Release readiness gate

```text
Feature complete
      |
      v
Code freeze
      |
      v
Regression
      |
      v
Security validation
      |
      v
Performance validation
      |
      v
Migration validation
      |
      v
Staging deployment
      |
      v
Smoke test
      |
      v
Release approval
      |
      v
Production
```

---

## 37. Critical release scenarios

### Scenario 1 - Free practice test

```text
Student login
 |
 v
Open free assessment
 |
 v
Start
 |
 v
Answer
 |
 v
Submit
 |
 v
Evaluate
 |
 v
Result
 |
 v
Analytics
```

### Scenario 2 - Paid assessment

```text
Student login
 |
 v
Select package
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
Open assessment
 |
 v
Attempt
 |
 v
Submit
 |
 v
Result
```

### Scenario 3 - Staff assessment creation

```text
Create question
 |
 v
Review
 |
 v
Approve
 |
 v
Publish
 |
 v
Create blueprint
 |
 v
Create assessment
 |
 v
Add questions or configure rules
 |
 v
Materialize rules
 |
 v
Validate
 |
 v
Publish
 |
 v
Schedule
```

### Scenario 4 - Assessment recovery

```text
Start
 |
 v
Answer questions
 |
 v
Browser failure
 |
 v
Login again
 |
 v
Recover session
 |
 v
Continue
 |
 v
Submit
```

### Scenario 5 - Payment callback duplication

```text
Payment success
 |
 v
Callback 1
 |
 v
Access granted
 |
 v
Callback 2
 |
 v
No duplicate access
```

### Scenario 6 - Re-evaluation

```text
Attempt evaluated
 |
 v
Answer key correction
 |
 v
Re-evaluation with new key
 |
 v
New evaluation version
 |
 v
Previous version superseded
 |
 v
Result updated through controlled publication
```

---

## 38. Performance testing plan

Execute in stages.

Stage 1: single-user baseline.

Stage 2: small concurrent load.

Stage 3: expected production concurrency.

Stage 4: peak expected concurrency.

Stage 5: stress test beyond expected load.

Key metrics: p50 latency, p95 latency, p99 latency, throughput, error percentage, database CPU, database connections, JVM memory, Redis utilization, PgBouncer pool utilization.

Assessment delivery receives separate load scenarios: start burst, autosave burst, submission burst, recovery burst.

---

## 39. Security testing plan

Perform authentication testing, authorization testing, API access testing, IDOR testing, input validation testing, SQL injection testing, XSS testing, file upload testing, session testing, token testing, rate-limit testing, and sensitive-data exposure testing.

Security testing is repeated before major releases.

---

## 40. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Assessment delivery failure | Critical | Dedicated load and end-to-end testing |
| Incorrect scoring | Critical | Extensive scoring tests, immutable answer keys |
| Data corruption | Critical | Constraints, transactions, backups |
| Duplicate payments | Critical | Idempotency |
| Unauthorized assessment access | Critical | AccessGrant and backend authorization |
| Browser crash | High | Autosave and recovery |
| Large question imports | High | Async jobs |
| Slow analytics | Medium | Derived analytics and indexes |
| Redis outage | Medium | Graceful degradation |
| Email failure | Medium | Retry queue |
| Large reports | Medium | Async report generation |
| Schema incompatibility | High | Expand and contract migrations |
| Deployment failure | High | CI/CD and rollback |
| Security vulnerability | Critical | Secure coding and testing |
| Snapshot preparation backlog | High | Monitoring, bounded workers, pre-generation lead time |

---

## 41. Change management

Changes are classified.

### Category A - Bug fix

Does not change architecture or public contract. Proceeds through normal development.

### Category B - Minor enhancement

Does not invalidate frozen architecture. Requires review.

### Category C - Contract change

Changes API, schema, domain model, or workflow. Requires architectural review.

### Category D - Architecture change

Changes framework, database, major frontend strategy, service boundaries, or deployment model. Requires explicit architecture-version update.

---

## 42. Versioning strategy

Documents evolve together.

Example.

```text
Architecture freeze 1.5
Database standards 1.5
API contract 1.5
Development roadmap 1.5
```

If architecture changes, the package increments together.

Do not silently change a frozen decision.

---

## 43. Phase 2 roadmap

After the platform stabilizes, consider AI question generation, AI difficulty estimation, AI explanations, personalized recommendations, natural language search, and an AI admin assistant.

AI outputs are treated as generated, reviewed, approved, and published for examination content.

---

## 44. Advanced platform capabilities

Potential future additions.

- PWA
- Offline capabilities
- Advanced proctoring
- 2FA
- SSO
- Mobile applications
- Advanced recommendation engine
- AI admin assistant
- Advanced reporting
- Data warehouse
- Event streaming
- Microservices where justified

These are not required for the current baseline.

---

## 45. Microservice extraction strategy

The platform does not prematurely become microservices.

If scale later requires extraction, candidate boundaries are identity service, assessment service, delivery service, evaluation service, commerce service, notification service, analytics service, and reporting service.

The modular-monolith boundaries make future extraction possible.

The first extraction candidate is selected based on measured operational requirements.

---

## 46. Future event architecture

The modular monolith may later introduce domain events.

```text
AssessmentSubmitted
        |
        v
Evaluation
        |
        v
ResultPublished
        |
        v
AnalyticsUpdated
        |
        v
NotificationSent
```

Initially these remain internal application events and background jobs. A distributed event broker is introduced only when there is a demonstrated need.

---

## 47. Documentation requirements

The repository contains the full specification set plus implementation documentation for architecture, API, database, security, deployment, operations, and testing.

---

## 48. Repository-level structure

```text
exam-platform/
|
+-- backend/
|   +-- src/
|   +-- pom.xml
|   +-- Dockerfile
|
+-- frontend/
|   +-- app/
|   +-- components/
|   +-- features/
|   +-- lib/
|   +-- package.json
|   +-- Dockerfile
|
+-- database/
|   +-- documentation/
|
+-- docs/
|
+-- infrastructure/
|   +-- docker/
|   +-- nginx/
|   +-- pgbouncer/
|   +-- monitoring/
|
+-- docker-compose.yml
+-- README.md
```

The exact repository organization may be adjusted if backend and frontend are maintained as separate repositories.

---

## 49. Recommended development order inside every module

```text
1.  Domain clarification
        |
        v
2.  Database migration
        |
        v
3.  Entity
        |
        v
4.  Repository
        |
        v
5.  Service
        |
        v
6.  DTO
        |
        v
7.  Mapper
        |
        v
8.  Validation
        |
        v
9.  Controller
        |
        v
10. OpenAPI
        |
        v
11. Unit tests
        |
        v
12. Integration tests
        |
        v
13. Frontend API integration
        |
        v
14. Frontend UI
        |
        v
15. End-to-end tests
        |
        v
16. Review
```

This sequence prevents frontend behavior from drifting away from backend business rules.

---

## 50. API-first development rule

```text
Requirement
 |
 v
Domain rule
 |
 v
API contract
 |
 v
Backend
 |
 v
Frontend
```

The frontend does not define business behavior independently.

---

## 51. Transaction boundary rules

Transactions belong primarily in service-layer business operations.

### Publish assessment

```text
Validate
 |
 v
Load assessment version
 |
 v
Validate questions
 |
 v
Validate blueprint
 |
 v
Change publication state
 |
 v
Audit
 |
 v
Commit
```

### Submit attempt

```text
Validate session
 |
 v
Validate attempt
 |
 v
Persist final responses
 |
 v
Mark submitted
 |
 v
Close session
 |
 v
Create evaluation handoff
 |
 v
Commit
```

### Payment callback

```text
Validate callback
 |
 v
Check idempotency
 |
 v
Update payment
 |
 v
Create or update purchase
 |
 v
Create access grant
 |
 v
Commit
```

### Re-evaluation

```text
Load current evaluation
 |
 v
Validate new answer key
 |
 v
Create evaluation N+1
 |
 v
Create result N+1
 |
 v
Supersede evaluation N
 |
 v
Audit
 |
 v
Commit
```

---

## 52. Concurrency principles

Critical operations are designed for concurrent execution, particularly response saving, assessment submission, payment callback, result publication, access grant creation, and re-evaluation.

Use appropriate transaction boundaries, unique constraints, optimistic locking where required, idempotency keys, and state validation.

Database constraints protect against race conditions that application code alone cannot reliably prevent.

---

## 53. Data integrity principles

The system prefers.

```text
Database constraint
+
Application validation
+
Business validation
```

Rather than application validation alone.

Example: application enforces only one response per question; the database enforces `UNIQUE(attempt_id, question_version_id)`.

Both layers protect the invariant.

---

## 54. Frontend development principles

The frontend is responsive, accessible, componentized, permission-aware, API-driven, resilient to network failures, and explicit about loading, empty, and error states.

Assessment UI receives special treatment because it has different UX requirements from ordinary administration screens.

---

## 55. Student UX priority

The student experience minimizes unnecessary complexity.

```text
Discover
 |
 v
Understand
 |
 v
Access
 |
 v
Start
 |
 v
Attempt
 |
 v
Submit
 |
 v
Understand result
 |
 v
Improve
```

---

## 56. Staff UX priority

```text
Create content
 |
 v
Review
 |
 v
Approve
 |
 v
Build assessment
 |
 v
Validate
 |
 v
Publish
 |
 v
Schedule
 |
 v
Monitor
```

---

## 57. Admin UX priority

```text
Configure
 |
 v
Manage
 |
 v
Monitor
 |
 v
Audit
 |
 v
Resolve
```

Administration focuses on operational control rather than duplicating every staff workflow.

---

## 58. Final implementation milestones

### M1 - Technical foundation

Application, database, CI/CD.

### M2 - Secure platform

Authentication and RBAC.

### M3 - Content platform

Academic and question bank.

### M4 - Assessment authoring

Blueprint and assessment builder.

### M5 - Examination engine

Delivery, autosave, recovery, snapshot preparation.

### M6 - Result engine

Evaluation, answer key detail, results, re-evaluation.

### M7 - Intelligence layer

Analytics and recommendation foundation.

### M8 - Commercial platform

Subscriptions, payments, access.

### M9 - Operations platform

Administration, communication, reporting.

### M10 - Production release

Security, performance, reliability, monitoring.

---

## 59. Exit criteria

The platform is implementation complete when the following hold.

### Product

Supported exam structures work. Question bank works. Assessment builder works. Assessment delivery works. Evaluation works. Results work. Analytics work. Subscriptions and access work. Administration works. Reporting works.

### Technical

APIs documented. Database migrations complete. Automated tests passing. CI/CD operational. Monitoring operational. Logging operational. Backup operational. Rollback procedure tested.

### Security

Authentication complete. RBAC complete. Authorization verified. Sensitive data protected. Audit logging operational. System actor login blocked.

### Assessment integrity

Autosave verified with ETag. Session recovery verified. Duplicate submission protected. Scoring verified. Answer key detail verified. Evaluation versioning verified. Result publication verified. Snapshot readiness verified.

### Operational

Staging validated. Production deployment tested. Smoke tests defined. Incident procedure documented.

---

## 60. Final roadmap

```text
                         +------------------+
                         |   FOUNDATION     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         | IDENTITY AND     |
                         | SECURITY         |
                         +--------+---------+
                                  |
                         +--------v---------+
                         | ACADEMIC MASTERS |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |  QUESTION BANK   |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |    BLUEPRINT     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |   ASSESSMENT     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |     DELIVERY     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |    EVALUATION    |
                         +--------+---------+
                                  |
                    +-------------+-------------+
                    |                           |
             +------v-------+           +-------v------+
             |   ANALYTICS  |           | COMMUNICATION |
             +------+-------+           +-------+------+
                    |                           |
                    +-------------+-------------+
                                  |
                         +--------v---------+
                         |     COMMERCE     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         | ADMINISTRATION   |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |    REPORTING     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |    HARDENING     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |   PRODUCTION     |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |   PHASE 2 / AI   |
                         +------------------+
```

---

## 61. Implementation governance

The package forms the implementation baseline. The order represents the dependency chain.

```text
Business requirements
        |
        v
Domain
        |
        v
Technical architecture
        |
        v
Database
        |
        v
API
        |
        v
Technology freeze
        |
        v
Implementation roadmap
```

These documents are treated as the baseline before implementation begins.

Any material change to one layer is checked against all downstream layers.

---

## 62. Final engineering principle

The platform is built around one central principle.

> The backend owns business truth; the database protects data integrity; the frontend delivers the experience; the assessment engine protects examination correctness; and every published artifact remains historically reproducible.

This principle guides implementation decisions that are not explicitly covered elsewhere in the specification.

---

## 63. Document status

**Status:** FROZEN development roadmap.

This document is the roadmap reference for backend work, frontend work, database work, API work, testing work, DevOps work, security checkpoints, performance checkpoints, release gates, definition of done, production-readiness requirements, and change control.