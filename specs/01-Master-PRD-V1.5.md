# Smart Competitive Examination Platform
## Master Product Requirements Document

**Version:** 1.5
**Status:** FROZEN — implementation baseline
**Date:** 7 October 2026

---

## 1. Vision

Build a modern, enterprise-grade competitive examination platform for:

- Government examination coaching institutes
- Private coaching centers
- Universities and colleges
- Corporate assessments
- Recruitment examinations
- Certification examinations

The platform is modular, scalable, secure, and cloud-ready.

It is architected as a **configurable examination engine** rather than a collection of hard-coded exam implementations. A new examination framework is added through configuration and policy mapping, not by writing exam-specific code scattered through the runtime.

---

## 2. Product scope

The platform comprises ten business modules:

1. Identity & Access Management
2. Academic Structure
3. Question Bank
4. Assessment Management
5. Assessment Delivery
6. Evaluation & Results
7. Analytics
8. Commerce
9. Communication
10. Administration

Each module owns its business logic, its persistence, and its state transitions. Modules communicate through explicit application contracts. No module directly manipulates another module's persistence.

---

## 3. Product documents

The package consists of:

- `01-Master-PRD-V1.5.md` — this document
- `02-Domain-Model-V1.5.md`
- `03-Technical-Design-Document-V1.5.md`
- `04-Database-Schema-V1.5.md`
- `05-API-Contract-V1.5.md`
- `06-Architecture-Freeze-V1.5.md`
- `07-Development-Roadmap-V1.5.md`
- `08-Exam-Engine-and-CDUI-Contract-V1.5.md`
- `09-P0-Foundation-V1.5.md` through `22-P13-Reporting-V1.5.md`
- `V1.5-IMPLEMENTATION-STATUS.md`
- `V2-MULTI-TENANT-BOUNDARY.md`

These documents are the single source of truth for implementation.

---

## 4. Architecture

**Architecture style:** Modular monolith.

**Future-ready:** Microservice-friendly module boundaries. No microservices are introduced in this version.

**Organization scope:** One organization per deployment.

---

## 5. Backend technology

- Java 25 (Java 21 acceptable if organizational standards require it)
- Spring Boot 4
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Redis (cache only)
- PgBouncer (production connection pooling)
- Flyway
- OpenAPI / Swagger
- Docker

---

## 6. Frontend technology

- Next.js
- TypeScript
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form
- Zod
- Axios

Content rendering:

- Unified / Remark-compatible Markdown pipeline
- KaTeX server-side rendering
- Rehype-compatible HTML sanitization

---

## 7. DevOps

- GitHub
- GitHub Actions
- Docker Compose
- Nginx
- Prometheus
- Grafana
- Loki
- PgBouncer

---

## 8. Security

- JWT authentication
- Role-based access control
- Secure password hashing
- Input validation
- Audit logging (transactional; failure rolls back the business mutation)
- API authorization
- HTTPS in production
- System actor for background processes

---

## 9. Database

**Database:** PostgreSQL.

**Standards:**

- UUID primary keys
- Flyway migrations
- Versioned entities
- Base entity with status and audit fields
- Soft delete using `status`
- Relational modeling for transactional data
- JSONB only for genuinely configurable structures
- 63 tables

---

## 10. Core domains

**Identity**

- Organization, User, Role, Permission, UserRole, RolePermission, LoginSession

**Academic**

- Exam, Subject, Chapter, Topic, SubTopic, Language, Tag

**Content**

- Question, QuestionVersion, QuestionTemplate, QuestionOption, Asset, Explanation, Hint, PreviousExamHistory, QuestionTag

**Assessment**

- Blueprint, BlueprintVersion, Assessment, AssessmentVersion, AssessmentQuestion, AssessmentSchedule, AssessmentCollection, CollectionAssessment, AssessmentSelectionRule

**Delivery**

- AssessmentSession, StudentAttempt, AttemptQuestion, AttemptQuestionOption, StudentResponse, DeliveryPolicy

**Evaluation**

- AnswerKey, AnswerKeyDetail, Evaluation, Result, Scorecard

**Analytics**

- StudentAnalytics, AssessmentAnalytics, QuestionAnalytics, TopicAnalytics, OrganizationAnalytics

**Commerce**

- AccessPackage, AccessPackageCollection, SubscriptionPlan, Purchase, PaymentRecord, AccessGrant, Coupon

**Communication**

- Notification, EmailTemplate, Announcement

**Administration**

- OrganizationSetting, BusinessRule, Lookup, AuditLog

**Infrastructure**

- BackgroundJob

---

## 11. Development principles

- Clean architecture
- SOLID principles
- REST APIs
- DTO pattern; no JPA entities exposed through controllers
- Validation layer: API, business, database
- Global exception handling
- Structured logging
- Pagination on all list endpoints
- API versioning (`/api/v1`)
- OpenAPI documentation
- Explicit state transitions through action endpoints

---

## 12. API standards

- Base path: `/api/v1/`
- JWT-protected except where explicitly public
- UUID identifiers
- Standard response envelope
- Standard error envelope
- Pagination, filtering, sorting with allow-lists
- HTTP conditional requests for stale-write protection on response save

---

## 13. Content representation contract

All renderable examination content uses a single contract: `MARKDOWN_LATEX_V1`.

**Governed fields:**

- `question_version.content`
- `question_option.content`
- `explanation.content`
- `hint.content`

**Accepted math delimiters:**

- Inline: `\( ... \)`
- Block: `\[ ... \]`

**Rejected math delimiters:** `$...$` and `$$...$$`. Dollar signs remain ordinary text and are never parsed as math.

**Raw HTML is rejected.** All content is sanitized before HTML serialization using an explicit allowlist. Executable MDX is prohibited.

**Asset shortcodes:** `[asset: <uuid>]`. The referenced asset must belong to the same `question_version`.

**Renderer:** KaTeX server-side rendering. No MathJax in the student delivery path.

---

## 14. Examination reproducibility

The platform must be able to answer, at any future time:

> "What exactly did the student take, and how was the student's score calculated?"

The reproducible chain:

```
StudentAttempt
↓
AttemptQuestion (exact QuestionVersion, order, section, marks, negative marks)
↓
AttemptQuestionOption (exact option ordering)
↓
StudentResponse
↓
AnswerKey (exact version) + AnswerKeyDetail (per-question answer)
↓
Evaluation (exact version)
↓
Result
```


Published QuestionVersions, BlueprintVersions, AssessmentVersions, and AnswerKeys are immutable. Corrections create new versions.

---

## 15. Pilot-first execution strategy

The complete platform remains the target product. The first real deployment is a thin vertical slice through the examination engine.

**Pilot scope:**

1. Identity & Access Management
2. Academic Structure required by the pilot exams
3. Question Bank and review/publishing workflow
4. Blueprint engine
5. Assessment authoring, validation, publishing, and scheduling
6. Assessment delivery
7. Evaluation & results
8. Minimal pilot analytics and telemetry
9. Minimal pilot operations workspace

**Deferred after pilot validation:**

- Commerce
- Communication
- Full analytics and insights
- Full administration portal
- Advanced reporting suite
- AI roadmap capabilities

Domain models and database structures for deferred modules remain in the frozen baseline even when their implementation is deferred.

---

## 16. Pilot access model

For the first coaching-institute pilot, commercial checkout is deferred.

Student authorization is established by a controlled, version-controlled AccessGrant onboarding process.
```
Pilot User
↓
Controlled AccessGrant provisioning
↓
Assessment authorization
↓
Scheduled examination
```

The process must not bypass the authorization model. `AccessGrant` remains the authorization source of truth.

The pilot onboarding mechanism must be auditable and must not require ad hoc schema changes or unrestricted production SQL access.

---

## 17. Pilot success gate

The core product hypothesis is the correctness, configurability, and operational reliability of the examination engine.

The pilot must prove, with evidence:

- authentic exam behavior for selected NTA / TCS iON-style configurations
- reliable assessment start and recovery
- correct autosave behavior
- deterministic question and option ordering
- correct scoring and result publication
- stable behavior under the 5,000-concurrent-user workload
- greater than 99% successful submissions
- less than 1% exam failure rate
- p95 latency under 100 ms for applicable APIs

Only after these gates are met should deferred modules be prioritized for broader implementation.

---

## 18. Thundering-herd protection

The platform must never synchronously build the full attempt snapshot from the public Start Exam request path.

For a 100-question / 4-option assessment:
1 StudentAttempt
1 AssessmentSession
100 AttemptQuestion
400 AttemptQuestionOption
502 rows per student

At 5,000 simultaneous starts:
5,000 × 502 = 2,510,000 rows

Snapshot child-row creation is an asynchronous preparation workload. It does not execute in the latency-critical Start Exam request.

---

## 19. Core pilot flow
```
Assessment published
↓
Schedule created
↓
Eligible students identified
↓
Async snapshot preparation
↓
AttemptQuestion + AttemptQuestionOption persisted
↓
Snapshot status = READY
↓
Exam start window opens
↓
Start Exam request
↓
Validate access + schedule + READY snapshot
↓
Mark StudentAttempt IN_PROGRESS
↓
Create or activate lightweight AssessmentSession
↓
Return exam context
```

Start Exam is a lightweight state transition. It performs no snapshot generation.

---

## 20. Exam engine abstraction

The backend exposes a common exam-engine contract with implementations for:

- TCS iON
- IBPS
- NTA
- UPSC
- Railway
- Custom

Exam engines control layout, navigation, timers, calculator, section behavior, review workflow, and submission rules.

The frontend does not branch on exam name. It receives a normalized delivery configuration and renders capabilities.

---

## 21. Delivery modes

Every student attempt has an immutable mode:

- `PRACTICE`
- `EXAM`

Practice mode may allow explanations, flexible navigation, and no enforced duration where configuration permits.

Exam mode enforces the configured examination rules.

Mode is selected at schedule level and persisted on `StudentAttempt.attempt_mode`.

---

## 22. Performance acceptance targets

- 5,000 concurrent users at launch target
- 99.9% availability target
- sub-100 ms API response for 95% of applicable requests
- greater than 99% successful submissions
- less than 1% exam failure rate

These are release acceptance targets, verified through performance, concurrency, resilience, and submission testing.

---

## 23. Non-functional requirements

- High availability
- Horizontal scalability
- Secure by default
- Cloud ready
- Container ready
- Monitoring
- Auditability
- Backup and recovery
- Performance optimization

---

## 24. Analytics

**Current scope:** derived analytics from transactional data — student, assessment, question, topic, and organization aggregates. Analytics is recalculable and non-authoritative.

**Future:** AI-driven insights, personalized recommendations, natural language search.

Analytics tables are projections. They never replace transactional source data.

---

## 25. Future AI capabilities

The following are future capabilities and are not part of this release:

- AI question generation
- AI explanations
- difficulty prediction
- personalized study recommendations
- performance insights beyond deterministic analytics
- natural language search
- AI admin assistant

Core scoring and examination integrity remain deterministic and server-controlled.

AI must never silently alter official examination data. AI-generated questions follow Draft → Review → Approved → Published.

---

## 26. Freeze policy

The following are frozen:

- Product requirements
- Domain model
- Database schema
- API contracts
- Technology stack
- Naming standards
- Development standards
- Exam engine contract
- Content representation contract
- Evaluation and scoring semantics

Future enhancements are implemented through versioning rather than modifying a frozen contract.

---

## 27. Project status

| Area | Status |
|---|---|
| Architecture | Complete |
| Design | Complete |
| Database design | Complete |
| API contract | Complete |
| Content rendering contract | Complete |
| Exam engine contract | Complete |
| Evaluation contract | Complete |
| Reporting contract | Complete |
| Ready for development | Yes |
| Open schema or API blockers | None |

---

## 28. Final statement

This document is the frozen product requirements baseline. All development aligns with this document and its downstream specifications. Future changes follow a controlled versioning process to preserve stability, maintainability, and scalability.

Where this document and a higher-ranked document in `V1.5-SCOPE-AND-FREEZE-AUTHORITY.md` disagree, the authority order in that document governs.
