# Smart Competitive Examination Platform
## P11 — Full Administration Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P11 — Full Administration
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, P4 Blueprint and Assessment Management, P5 Assessment Delivery and Runtime Examination Engine, P6 Evaluation and Results, P7 Scale Validation and Live Pilot, P8 Full Analytics and Insights, P9 Commerce and Access Management, P10 Communication and Notification
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**API:** REST + JSON, `/api/v1`
**Observability:** Prometheus, Grafana, Loki
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Operations, Architects

---

## 1. Purpose

This document defines the implementation contract for the Full Administration phase.

Administration is the organizational and operational control plane.

Administration owns only four tables.

```text
organization_setting
business_rule
lookup
audit_log
```

All other administrative capabilities operate through the owning domain services.

The central rule.

```text
Admin controller
    |
    v
Authorization
    |
    v
Owning application service
    |
    v
Business rules
    |
    v
Repository
    |
    v
Audit
```

Never.

```text
Admin controller
    |
    v
Direct database manipulation
```

---

## 2. Source-of-truth hierarchy

Read these documents before P11 implementation:

- Master PRD
- Domain Model
- Technical Design Document
- Database Standards and Schema
- API Contract
- Architecture Freeze
- Development Roadmap
- ExamEngine and CDUI Contract
- P0 Foundation Implementation Specification
- P1 Identity and Security Implementation Specification
- P2 Academic Structure Implementation Specification
- P3 Content and Question Bank Implementation Specification
- P4 Blueprint and Assessment Management Implementation Specification
- P5 Assessment Delivery and Runtime Examination Engine Implementation Specification
- P6 Evaluation and Results Implementation Specification
- P7 Scale Validation and Live Pilot Implementation Specification
- P8 Full Analytics and Insights Implementation Specification
- P9 Commerce and Access Management Implementation Specification
- P10 Communication and Notification Implementation Specification
- This document

The earlier documents already freeze the administration schema, the domain ownership rules, and the audit boundary. This document does not modify those decisions.

---

## 3. Administration mission

Administration provides:

```text
Configuration
Governance
User and role operations
Operational support
Master-data control
Bulk and import orchestration
Audit visibility
System health visibility
Controlled cross-domain actions
```

Administration is a control plane. It does not replace a business domain.

---

## 4. Scope

In scope.

```text
Organization settings
Business rules
Lookups
Audit search
User administration
Student administration
Role and permission administration
Commerce operations
Content and assessment operations
Delivery and evaluation support
Communication administration
Bulk operations
Imports
Global search
System health
Support console
Operational monitoring
Admin dashboard
Security
Tests
Runbooks
```

Out of scope.

```text
Multi-tenant control plane
SSO administration
Advanced 2FA administration
Advanced proctoring
Biometric and device trust management
CRM
Marketing automation
Support ticket domain
Incident management domain
AI admin assistant
New search platform
New event broker
New persistent admin history tables
```

---

## 5. Architectural boundary

The eleven business modules remain separate architectural modules inside one modular monolith.

Administration owns:

```text
organization_setting
business_rule
lookup
audit_log
```

It may call explicit application and service contracts exposed by other modules.

It does not call another module's repository or manipulate another module's JPA entities directly.

---

## 6. No god mode

ADMIN is not a reason to bypass permissions.

Do not implement:

```java
if (role == ADMIN) {
    allowEverything();
}
```

Use:

```text
AuthorizationService
    |
    v
Explicit permission
    |
    v
Operation
```

Role names are business master data. Permissions are the authorization boundary.

---

## 7. Frozen administration tables

The frozen administration database model contains four tables.

```text
organization_setting
business_rule
lookup
audit_log
```

No support_ticket, incident, admin_action, import_history, bulk_operation, search_index, health_history, or configuration_version table is authorized by this document.

---

## 8. OrganizationSetting

### 8.1 Schema

```sql
organization_setting
--------------------
id UUID PK
organization_id UUID FK
configuration JSONB
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(organization_id)`.

### 8.2 Ownership

OrganizationSetting is the authoritative storage for approved organization-level configuration.

It is not a general secret store, a business transaction store, a repository for user data, or a substitute for BusinessRule.

Other modules consume it through a controlled configuration contract.

### 8.3 JSONB contract

JSONB is permitted for configurable structures.

The application defines a registry of recognized keys.

Correct use.

```text
Display configuration
Approved organization defaults
Registered communication switches
Registered operational configuration
```

Incorrect use.

```text
Users
Attempts
Results
Payments
Grants
Questions
Passwords
Provider secrets
Executable scripts
```

### 8.4 Validation

Update flow.

```text
Authenticate
    |
    v
Permission check
    |
    v
Load current configuration
    |
    v
Validate shape
    |
    v
Validate cross-field rules
    |
    v
Persist
    |
    v
Audit
    |
    v
Invalidate approved cache keys
```

A syntactically valid JSON document is not necessarily a valid organization configuration.

### 8.5 API

```http
GET /api/v1/organization-settings
PUT /api/v1/organization-settings
```

The API is scoped to the current deployment organization.

A normal admin request does not select an arbitrary organization by query parameter.

### 8.6 Organization setting registry

Frozen registry fields.

```text
Approved keys
Types
Defaults
Required values
Mutable versus immutable settings
Cacheability
Security classification
Restart and effective-time behavior
```

No module silently invents configuration keys.

### 8.7 Configuration concurrency

The frozen schema does not define an optimistic-lock or version column.

Frozen concurrency strategy.

```text
Server-side serialization per organization
```

Two concurrent updates to the same organization setting are serialized at the application layer.

Client conflict detection is optional and non-authoritative.

---

## 9. BusinessRule

### 9.1 Schema

```sql
business_rule
-------------
id UUID PK
code VARCHAR(100)
configuration JSONB
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 9.2 Purpose

BusinessRule represents configurable business behaviour.

Known categories.

```text
Scoring rule
Access rule
Notification rule
Operational rule
```

Administration stores and governs these rules. The owning module interprets the configuration.

### 9.3 No executable rules

Rules are data, not arbitrary code.

Reject or ignore unsupported executable constructs.

```text
JavaScript
SQL
Method invocation
Filesystem access
Network calls
Arbitrary expression execution
```

A JSON rule is never a hidden scripting engine.

### 9.4 Registry

Every runtime-consumed rule has:

```text
Stable code
Owner module
Configuration schema
Safe defaults
Valid range and values
Cache policy
Audit level
```

Illustrative concepts.

```text
SCORING_DEFAULTS
ACCESS_RULE
NOTIFICATION_RULE
OPERATIONAL_RULE
```

These are not automatically seeded values.

### 9.5 API

```http
GET  /api/v1/business-rules
GET  /api/v1/business-rules/{id}
POST /api/v1/business-rules
PUT  /api/v1/business-rules/{id}
```

DELETE is not frozen. Use lifecycle state rather than inventing destructive deletion.

### 9.6 Lifecycle

Use the platform lifecycle.

```text
ACTIVE
INACTIVE
ARCHIVED
```

A rule required by active application behavior is not archived without a safe replacement or fallback contract.

### 9.7 Resolution

Consumers use a typed resolver and application contract rather than parsing JSONB throughout the codebase.

Conceptual contract.

```text
BusinessRuleResolver.resolve(ruleCode, configurationType)
```

Administration owns persistence. The consumer module owns business semantics.

### 9.8 Business rule registry

Frozen registry fields.

```text
Configurable rule catalog
Code-only rule catalog
Ownership
Schema validation
Defaults
Allowed ranges
Cache behavior
Effective-time behavior
Audit requirements
```

Not every constant becomes a database rule.

---

## 10. Lookup

### 10.1 Schema

```sql
lookup
------
id UUID PK
category VARCHAR(100)
code VARCHAR(100)
name VARCHAR(150)
display_order INTEGER
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(category, code)`.

Indexes.

```text
idx_lookup_category
idx_lookup_category_display_order
```

### 10.2 Purpose

Lookup represents reusable configurable master values.

Example.

```text
Category: QUESTION_DIFFICULTY

EASY
MEDIUM
HARD
```

Lookup does not replace real domain entities or transactions.

### 10.3 API

```http
GET    /api/v1/lookups
GET    /api/v1/lookups/{id}
POST   /api/v1/lookups
PUT    /api/v1/lookups/{id}
DELETE /api/v1/lookups/{id}
```

Filter: category.

### 10.4 Delete safety

DELETE exists in the API contract, but physical deletion is conservative.

Before delete:

```text
Check active references where discoverable
Check whether the value is an application contract
Prefer INACTIVE and ARCHIVED when deletion could break consumers
```

A lookup value is not deleted solely because a UI no longer displays it.

### 10.5 Display order

`display_order` is INTEGER. Gaps are allowed.

Consumers do not assume contiguous numbering.

---

## 11. AuditLog

### 11.1 Schema

```sql
audit_log
---------
id UUID PK
user_id UUID FK
entity_name VARCHAR(150)
entity_id UUID
action VARCHAR(100)
remarks TEXT
created_on TIMESTAMPTZ
```

Indexes.

```text
idx_audit_log_user_id
idx_audit_log_entity_name
idx_audit_log_entity_id
idx_audit_log_created_on
```

### 11.2 Immutability

AuditLog is immutable.

Allowed.

```text
INSERT
```

Prohibited.

```text
UPDATE
DELETE
```

Generic audit update or delete endpoints are not exposed.

### 11.3 Minimum meaning

Audit answers who, what, which entity, which entity id, when, action, and remarks.

The frozen schema does not contain a request_id, old_values, new_values, IP address, or full payload field. Those are not claimed as historical AuditLog fields.

### 11.4 Audit actor

For authenticated requests: SecurityContext, current user, audit actor.

Background jobs use the system actor.

A client-submitted actor id is not trusted for audit attribution.

### 11.5 System actor identity

Frozen system actor identity.

```text
UUID:  00000000-0000-0000-0000-000000000001
Email: system@platform.internal
```

The system actor is seeded by migration. Login is blocked for the identity.

### 11.6 Audit action vocabulary

Use stable business actions such as CREATE, UPDATE, ACTIVATE, DEACTIVATE, ARCHIVE, PUBLISH, SUBMIT, EVALUATE, RE_EVALUATE, READ, REFUND, GRANT_ACCESS, REVOKE_ACCESS, IMPORT, BULK_ACTIVATE, BULK_ARCHIVE.

The catalog remains controlled and consistent across modules.

### 11.7 Audit remarks

Remarks are concise operational context.

Do not place in remarks.

```text
Passwords
Reset tokens
JWTs
Provider secrets
Payment credentials
Full request payloads
Full stack traces
```

AuditLog is not a payload archive.

### 11.8 Audit API

```http
GET /api/v1/audit-logs
```

Filters: userId, entityName, entityId, action, from, to.

Results are paginated and permission-filtered.

### 11.9 Audit permission

Audit is privileged operational information.

Students receive no audit access.

Support, finance, and staff receive only the scope granted by explicit permissions.

Admin access passes the AuthorizationService rather than using an unconditional bypass.

### 11.10 Audit retention

The source permits controlled retention handling where legally and business appropriate.

P11 does not define a retention table.

Any retention action requires an approved retention policy, permission, operational controls, audit of the retention action, and backup and recovery consideration.

### 11.11 Audit atomicity

Audit insertion participates in the business transaction.

If audit insertion fails, the transaction rolls back.

No important action happens without audit evidence.

---

## 12. User administration

User administration reuses P1 Identity tables and services.

```text
organization
user
role
permission
user_role
role_permission
login_session
```

P11 adds the complete admin UX and control surface. It does not create AdminUser or Student tables.

---

## 13. Student administration

Student is a user plus the STUDENT role and use case.

No student table is created.

Student administration may compose identity, sessions, access, purchases, attempts, results, analytics, and notifications.

Each section uses its owning module.

---

## 14. User lifecycle

Identity owns ACTIVE, INACTIVE, and ARCHIVED.

P11 calls Identity operations.

It does not directly mutate the user table to activate, deactivate, or archive a user.

---

## 15. Role administration

Role remains Identity-owned.

Approved role concepts.

```text
ADMIN
REVIEWER
STAFF
STUDENT
SUPPORT
FINANCE
```

Permissions remain explicit.

A role name is not equated with an unlimited permission set in code.

---

## 16. Permission administration

Permission assignment follows.

```text
User
  |
  v
UserRole
  |
  v
Role
  |
  v
RolePermission
  |
  v
Permission
```

P11 calls Identity APIs and services for role and permission mutation.

It invalidates the P1 permission cache according to the existing security contract.

---

## 17. Privilege escalation

The admin console tests support to admin escalation, finance to admin escalation, staff to admin escalation, student to admin escalation, self-role escalation, and unauthorized permission assignment.

The backend is the security boundary.

---

## 18. Privileged role change policy

Frozen rules.

```text
A user cannot assign themselves a role.
A user cannot modify their own permissions.
ADMIN assignment requires ADMIN_ROLE_ASSIGN.
Removing the last active ADMIN is blocked.
All role and permission changes are audited.
Two-person approval is not required.
```

SUPER_ADMIN rules (ADR-0032, V29):

```text
SUPER_ADMIN cannot be demoted.
SUPER_ADMIN cannot be archived.
The last active SUPER_ADMIN is a protected invariant.
```

Emergency path.

```text
If all ADMINs are locked out (should not be possible given the last-admin rule),
recovery is a documented direct database procedure under change control.
```

---

## 19. Administrative session support

P1 owns LoginSession.

Support and admin may inspect login time, logout time, status, IP address, and device information.

No secret or token values are exposed.

---

## 20. Session revocation

A dedicated administrative session-revoke endpoint is not frozen.

If required by operations, the endpoint is defined through a formal amendment and includes:

```text
Endpoint
Permission
Current session handling
Target session scope
Audit
Interaction with P1 session invalidation
```

---

## 21. Admin dashboard

Admin dashboard is UI composition, not a database entity.

Possible areas.

```text
System health
Operational alerts
Active users
Assessment operations
Delivery state
Evaluation backlog
Commerce state
Communication state
Recent audit activity
```

---

## 22. Dashboard source of truth

```text
Active users           -> Identity and session metrics
Active grants          -> Commerce and AccessGrant
Pending evaluation     -> Evaluation
Communication backlog  -> P10 job and metrics
System health          -> infrastructure and observability
Audit activity         -> AuditLog
```

Dashboard-specific transactional copies are not created merely for cards.

---

## 23. Dashboard API

A consolidated admin dashboard endpoint is not frozen.

Initial implementation may compose authorized module APIs in parallel.

If a composite endpoint becomes necessary, it is introduced through a formal amendment and documented with endpoints, payload, permissions, timeout behavior, and partial failure policy.

---

## 24. Commerce administration

P11 presents operational access to P9 functions.

```text
Package management
Subscription plans
Purchase lookup
Payments
Refunds
Access grants
Access revocation
Coupons
```

Commerce remains source of truth.

---

## 25. Commerce admin rule

Forbidden.

```text
Admin to payment_record direct SQL
Admin to access_grant direct SQL
```

Required.

```text
Admin
  |
  v
Commerce application service
  |
  v
Business validation
  |
  v
Repository and provider
  |
  v
Audit
```

---

## 26. Content administration

P11 exposes operational navigation for Content.

Content remains owner of Question, QuestionVersion, QuestionOption, Asset, Explanation, Hint, PreviousExamHistory, and QuestionTag.

Publishing and review actions use P3 services.

---

## 27. Assessment administration

P11 exposes assessment lookup, version status, publication, scheduling, closure, and collection operations.

Assessment owns the rules. Admin is the operator.

---

## 28. Delivery administration

P11 may inspect schedule, snapshot readiness, attempt status, session status, and operational failure.

P5 owns AssessmentSession, StudentAttempt, StudentResponse, AttemptQuestion, AttemptQuestionOption, and DeliveryPolicy.

---

## 29. Snapshot operations

P5 snapshot states.

```text
PENDING
PREPARING
READY
FAILED
```

Admin reads these through delivery contracts and operational telemetry.

Admin does not repair snapshot state through direct UPDATE statements.

---

## 30. Exam-day support

Operational console makes it possible to investigate eligible attempts, ready or preparing or failed snapshots, start failures, active sessions, response-save issues, submissions, and recovery cases.

Support actions are limited to frozen delivery contracts.

---

## 31. Evaluation administration

Evaluation owns AnswerKey, AnswerKeyDetail, Evaluation, Result, and Scorecard.

P11 may expose evaluation status, re-evaluation action, result publication, and scorecard view.

P11 does not calculate a score or directly edit result data.

---

## 32. Analytics administration

P8 analytics is derived and recalculable.

P11 may provide dashboard access, operational refresh invocation if P8 exposes it, last calculated timestamps, and analytics health.

Analytics tables are not edited manually.

---

## 33. Communication administration

P10 owns Notification, EmailTemplate, and Announcement.

P11 exposes administrative screens and actions through Communication contracts.

An AdminNotification or AdminAnnouncement table is not created.

---

## 34. Support console

Support needs a focused operational view for user lookup, attempt lookup, access lookup, session information, purchase lookup, and permitted support actions.

Support is not a second administrator role.

---

## 35. Support data scope

Frozen policy.

| Permission | Grants |
|---|---|
| SUPPORT_USER_VIEW | Read user identity, masked by default |
| SUPPORT_USER_CONTACT | See unmasked email and mobile |
| SUPPORT_SESSION_VIEW | Read login sessions metadata |
| SUPPORT_ATTEMPT_VIEW | Read attempt metadata |
| SUPPORT_ACCESS_VIEW | Read access grants |
| SUPPORT_PURCHASE_VIEW | Read purchases without payment reference |
| SUPPORT_PAYMENT_REF_VIEW | See payment reference |
| SUPPORT_NOTIFICATION_SEND | Send ad-hoc notification to a single user |
| SUPPORT_SESSION_REVOKE | Force-invalidate a session; audited |
| SUPPORT_ATTEMPT_RECOVER | Trigger session recovery on behalf of a user; audited |

Support cannot.

```text
View password hash
View JWT tokens
View answer keys
Modify scores
Publish results
Refund payments
Modify roles
Modify business rules
Access audit logs by default
```

Default masking.

```text
Email:  j***@example.com
Mobile: +91*****1234
```

Unmasking requires SUPPORT_USER_CONTACT.

---

## 36. Support sensitive data

Support does not see password hashes, reset tokens, JWTs, provider secrets, private payment credentials, unrestricted answer keys, or internal security data.

Safe DTOs are used.

---

## 37. Global search

The API contract defines.

```http
GET /api/v1/search?q=<query>&resource=<optional>&limit=<n>
```

Results are permission-filtered.

Global search is a fully frozen production contract.

---

## 38. Global search contract

Frozen contract.

```text
Searchable resource types
Fields
Pagination
Permissions
PII redaction
Query syntax
Maximum query length
Result ordering
Endpoint status
```

Initial implementation uses PostgreSQL with pg_trgm.

---

## 39. Search resource allow-list

Searchable resources.

| Resource | Fields | Permission |
|---|---|---|
| USER | first_name, last_name, email (exact), mobile (exact) | GLOBAL_SEARCH_USER |
| QUESTION | code, title, content (prefix) | GLOBAL_SEARCH_QUESTION |
| ASSESSMENT | code, name | GLOBAL_SEARCH_ASSESSMENT |
| ACCESS_PACKAGE | code, name | GLOBAL_SEARCH_COMMERCE |
| SUBSCRIPTION_PLAN | code, name | GLOBAL_SEARCH_COMMERCE |
| PURCHASE | purchase reference (exact) | GLOBAL_SEARCH_COMMERCE |
| ANNOUNCEMENT | title | GLOBAL_SEARCH_COMMUNICATION |
| AUDIT_LOG | entity_name, entity_id | GLOBAL_SEARCH_AUDIT |

Only exposed resources have defined safe fields and read permission.

---

## 40. Search security

Search does not become a database browser.

Reject.

```text
SQL
Arbitrary table names
Unrestricted wildcards
Secret fields
Password hashes
Reset tokens
Provider credentials
```

---

## 41. Search performance

Global search is measured against large user table, large question bank, large audit table, and large purchase history.

OpenSearch and Elasticsearch are not introduced merely for architectural fashion.

---

## 42. Bulk operations

The API contract provides.

```http
POST /api/v1/bulk/{resource}/{operation}
```

Examples.

```text
Bulk user activation
Bulk user archive
Bulk question import
Bulk assessment operations
```

Large operations are asynchronous.

---

## 43. Bulk contract

Do not implement an arbitrary dynamic router that accepts any resource and operation combination.

Each supported operation is explicitly allow-listed and documented with request schema, permission, state rules, partial failure semantics, and idempotency.

Frozen allowed combinations.

```text
USER + ACTIVATE
USER + ARCHIVE
QUESTION + IMPORT
```

Additional combinations require a formal amendment.

---

## 44. Bulk response

```http
202 Accepted
```

```json
{
  "success": true,
  "message": "Bulk operation queued",
  "data": {
    "jobId": "<uuid>",
    "status": "QUEUED"
  }
}
```

---

## 45. Bulk processing

Large jobs use bounded batches and periodic commits.

Do not process thousands of mutations in one transaction.

Target-domain services remain responsible for row validation, state transitions, business rules, and referential integrity.

---

## 46. Bulk partial failure

A bulk job distinguishes total records, successful records, failed records, and structured errors.

A failure in one record does not necessarily abort all eligible records.

The exact semantics are domain-specific.

---

## 47. Bulk security

Before enqueue: authenticate, permission, resource allow-list, operation allow-list, input validation, object-scope validation, and queue.

The worker revalidates authoritative state.

---

## 48. Import API

```http
POST /api/v1/imports
```

Multipart upload may be used.

Response for queued work.

```http
202 Accepted
```

```json
{
  "success": true,
  "message": "Import queued",
  "data": {
    "jobId": "<uuid>",
    "status": "QUEUED"
  }
}
```

---

## 49. Import status

```http
GET /api/v1/imports/{jobId}
```

Exposes status, total records, successful records, failed records, and error report.

---

## 50. Import ownership

Administration orchestrates import initiation, but target modules own validation.

Examples.

```text
Admin to Content import service
Admin to Identity student import service
Admin to Academic master-data import service
```

No admin controller writes imported rows directly.

---

## 51. Import targets

| Target code | Source | Required permissions | Validation authority |
|---|---|---|---|
| USER_IMPORT | CSV | USER_CREATE, IMPORT_EXECUTE | Identity |
| ACADEMIC_IMPORT | CSV | ACADEMIC_WRITE, IMPORT_EXECUTE | Academic |
| QUESTION_IMPORT | CSV | QUESTION_CREATE, IMPORT_EXECUTE | Content |
| ACCESS_GRANT_IMPORT | CSV | ACCESS_GRANT_MANAGE, IMPORT_EXECUTE | Commerce |

Additional targets require formal approval.

---

## 52. Import file security

Treat import files as untrusted.

Validate size, type, encoding, headers, row limits, content, and filename handling.

Use a real CSV parser for CSV.

Do not construct filesystem paths from client filenames.

---

## 53. Import error contract

Structured row errors.

```json
{
  "jobId": "<uuid>",
  "status": "COMPLETED_WITH_ERRORS",
  "totalRecords": 1000,
  "successCount": 987,
  "failureCount": 13,
  "errors": [
    {
      "row": 17,
      "field": "email",
      "code": "INVALID_EMAIL",
      "message": "Email address is invalid"
    }
  ]
}
```

---

## 54. Import idempotency

Repeated uploads can duplicate data.

Use common idempotency infrastructure, target-domain business keys, and the frozen import contract.

An import-history table is not invented just to make retries convenient.

---

## 55. System health

Foundation provides health checks, Prometheus, Grafana, and Loki.

P11 aggregates safe operational status for administrators.

System health is not a new database entity.

---

## 56. Health components

```text
Application
PostgreSQL
Redis
PgBouncer
Background workers
Object storage
Email provider
Payment provider
```

External provider checks are optional and provider-specific.

---

## 57. Health status

UI-level states.

```text
HEALTHY
DEGRADED
UNAVAILABLE
UNKNOWN
```

These are monitoring and presentation concepts, not new business database statuses.

---

## 58. Health no-secrets rule

Never expose database passwords, JWT signing secret, Redis credentials, payment provider secrets, email provider secrets, environment dumps, or raw stack traces.

---

## 59. System health API

```http
GET /api/v1/admin/system-health
```

Response.

```json
{
  "success": true,
  "data": {
    "overall": "DEGRADED",
    "checkedOn": "2026-10-07T10:00:00Z",
    "components": [
      { "name": "APPLICATION", "status": "HEALTHY", "latencyMs": 4 },
      { "name": "POSTGRESQL", "status": "HEALTHY", "latencyMs": 7 },
      { "name": "REDIS", "status": "DOWN", "latencyMs": null, "message": "Connection refused" },
      { "name": "PGBOUNCER", "status": "HEALTHY", "latencyMs": 3 },
      { "name": "BACKGROUND_WORKERS", "status": "DEGRADED", "detail": { "reportWorker": "stale 124s" } },
      { "name": "OBJECT_STORAGE", "status": "HEALTHY", "latencyMs": 21 }
    ]
  }
}
```

Overall status rules.

| Condition | Overall |
|---|---|
| Any component DOWN | DOWN |
| Any component DEGRADED | DEGRADED |
| All HEALTHY | HEALTHY |
| Any UNKNOWN and the rest HEALTHY | DEGRADED |

Permission: SYSTEM_HEALTH_VIEW.

---

## 60. Health failure behavior

```text
Redis down      -> DEGRADED if PostgreSQL fallback works
Database down   -> UNAVAILABLE
Worker down     -> background capabilities degraded
Provider unknown -> UNKNOWN
```

HEALTHY is not inferred from missing telemetry.

---

## 61. Health timeouts

External health calls need bounded timeouts.

A slow payment or email provider does not hang the entire admin health screen.

Independent checks run in parallel.

---

## 62. Health refresh

Health is time-sensitive.

Use refresh on page load, manual refresh, and reasonable periodic refresh.

Polling every second is prohibited.

---

## 63. Admin navigation

Permission-driven sections.

```text
Dashboard
Users
Students
Roles
Permissions
Academics
Content
Assessments
Delivery
Evaluation
Analytics
Commerce
Communication
Settings
Business rules
Lookups
Audit
Imports
Bulk operations
Search
System health
```

Hidden menu items are not authorization.

---

## 64. Admin frontend stack

Use the frozen frontend stack.

```text
Next.js
TypeScript
Tailwind
shadcn/ui
TanStack Query
React Hook Form
Zod
Axios
```

A separate frontend architecture for admin is not introduced.

---

## 65. Admin tables

Admin tables provide server pagination, server filtering, allow-listed sorting, loading, empty, error, and permission-aware actions.

An unbounded enterprise dataset is never loaded into the browser.

---

## 66. Admin forms

Forms use explicit DTOs.

Arbitrary JSON is never bound directly into JPA entities.

Protect created_by, created_on, updated_by, updated_on, id, and server-owned status transitions.

---

## 67. Permission-aware navigation

Frontend can call `GET /api/v1/me/permissions` to decide which navigation and actions to display.

Backend authorization remains the real boundary.

---

## 68. Dangerous actions

Require explicit confirmation for archive, refund, revoke access, role and permission changes, business-rule changes, organization-setting changes, publish, bulk operations, and imports.

Confirmation is UX safety, not authorization.

---

## 69. Admin error handling

Use global API error categories.

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

Backend stack traces are not displayed.

---

## 70. Admin request correlation

All admin requests use X-Request-Id.

The UI shows the request id for operational failures when helpful.

The frozen audit table does not store request id.

---

## 71. Configuration UX

Organization Settings screen is driven by the approved settings registry.

Business Rules screen is driven by the approved rule registry.

An unrestricted JSON editor is not used for every setting and rule.

Typed fields and validation are preferred.

---

## 72. Lookup UX

Lookup administration supports filter by category, create, edit, activate, deactivate, archive or delete only where safe, and maintain display order.

Enforce UNIQUE(category, code).

---

## 73. Audit explorer UX

Audit explorer supports actor, entity, entity id, action, and date range.

Results show timestamp, actor, entity, action, and remarks.

No mutation controls.

---

## 74. User detail composition

A student or user detail page can aggregate Identity, roles, sessions, access grants, purchases, attempts, results, analytics, and notifications.

It remains a UI composition. No AdminStudent aggregate or table is introduced.

---

## 75. Composite student detail

A single Admin Student Detail endpoint is not frozen.

Initial implementation may compose module APIs.

A composite endpoint requires endpoints, payload, permissions, timeout behavior, and partial failure policy through a formal amendment.

---

## 76. Finance operations

Finance focuses on purchases, payments, refunds, coupons, subscription plans, and commerce reports.

Finance does not automatically gain question publication or business-rule permissions.

---

## 77. Support operations

Support focuses on user lookup, attempt lookup, access lookup, session information, purchase lookup, and permitted manual support actions.

Support is permission-controlled.

---

## 78. Staff operations

STAFF is generic.

P11 does not infer privileges merely from the label STAFF.

Effective access comes from explicit permissions.

---

## 79. Reviewer operations

REVIEWER remains focused on review and content responsibilities assigned by permissions.

Administration does not grant broad platform mutation rights simply because a user is a reviewer.

---

## 80. Admin permission catalogue

Suggested permission concepts.

```text
ORGANIZATION_SETTING_VIEW
ORGANIZATION_SETTING_UPDATE
BUSINESS_RULE_VIEW
BUSINESS_RULE_CREATE
BUSINESS_RULE_UPDATE
LOOKUP_VIEW
LOOKUP_CREATE
LOOKUP_UPDATE
LOOKUP_DELETE
AUDIT_LOG_VIEW
SYSTEM_HEALTH_VIEW
GLOBAL_SEARCH
GLOBAL_SEARCH_USER
GLOBAL_SEARCH_QUESTION
GLOBAL_SEARCH_ASSESSMENT
GLOBAL_SEARCH_COMMERCE
GLOBAL_SEARCH_COMMUNICATION
GLOBAL_SEARCH_AUDIT
IMPORT_EXECUTE
BULK_EXECUTE
SUPPORT_USER_VIEW
SUPPORT_USER_CONTACT
SUPPORT_SESSION_VIEW
SUPPORT_ATTEMPT_VIEW
SUPPORT_ACCESS_VIEW
SUPPORT_PURCHASE_VIEW
SUPPORT_PAYMENT_REF_VIEW
SUPPORT_NOTIFICATION_SEND
SUPPORT_SESSION_REVOKE
SUPPORT_ATTEMPT_RECOVER
```

Exact permission codes follow the Identity permission catalog.

---

## 81. Permission change cache

After changing user roles, role permissions, or permission state, invalidate the permission cache according to P1.

A security change does not wait for arbitrary cache expiry before becoming effective.

---

## 82. No direct cross-domain repository access

Forbidden.

```text
AdminPurchaseController to PurchaseRepository
AdminQuestionController to QuestionRepository
AdminAttemptController to StudentAttemptRepository
AdminResultController to ResultRepository
```

Use the owning module's application and service contract.

---

## 83. Domain action pattern

Correct.

```text
Admin controller
    |
    v
AuthorizationService
    |
    v
Owning application service
    |
    v
Business validation
    |
    v
State transition
    |
    v
Audit
```

Incorrect.

```text
Admin controller
    |
    v
JdbcTemplate
    |
    v
UPDATE table
```

---

## 84. State transition principle

Use explicit business actions where the source defines them.

```text
publish
close
submit
evaluate
re-evaluate
refund
grant-access
revoke-access
activate
archive
```

Arbitrary status mutation for stateful domain entities is not exposed.

---

## 85. Admin bulk resource allow-list

The application defines supported combinations.

```text
USER + ACTIVATE
USER + ARCHIVE
QUESTION + IMPORT
ASSESSMENT + approved action
```

Any resource and operation pair not explicitly registered is rejected.

---

## 86. Admin search resource handlers

Use explicit handlers.

```text
UserSearch
QuestionSearch
AssessmentSearch
PurchaseSearch
AuditSearch
```

Query parameters are not mapped directly into table names.

---

## 87. Admin health adapter

The health aggregator uses safe component adapters and contracts.

Each checker returns component, status, safe latency, and last checked where available.

Provider secrets and internal configuration are not returned.

---

## 88. Configuration consumer rule

Administration owns storage. Consumer module owns semantics.

Example.

```text
BusinessRule stores scoring configuration.
Evaluation interprets scoring configuration.
```

Evaluation scoring code is not placed in Administration.

---

## 89. Lookup consumer rule

Consumers reference category and code.

Display names are presentation data.

Backend calculations do not depend on mutable lookup names.

---

## 90. Audit versus logs

AuditLog answers who changed what.

Technical logs answer what failed internally, where, and with what stack trace.

Metrics answer how often and how fast.

One does not replace another.

---

## 91. Audit versus history

AuditLog does not provide automatic old and new configuration snapshots.

The current frozen schema lacks old_values, new_values, and version.

Full configuration history is not claimed.

---

## 92. Bulk audit model

AuditLog contains one entity_id.

A bulk job affects many records.

Operation-level audit is appropriate. A single AuditLog row does not prove every affected record unless the implementation explicitly records that evidence elsewhere.

---

## 93. Import audit model

Import processing can involve thousands of rows, but AuditLog does not store full source-file lineage.

Operation-level audit plus job metadata is used unless a future persistence amendment adds richer import history.

---

## 94. Search history

The source does not require storing every search query.

A search_history table is not created unless a security or privacy requirement later demands it.

---

## 95. Configuration history

OrganizationSetting and BusinessRule are not versioned in the frozen database.

Rollback and history cannot be assumed beyond available AuditLog evidence.

---

## 96. Cache management

A generic clear-cache admin action is not frozen.

Redis FLUSHALL is not exposed.

If required, it is introduced through a formal amendment with clear scope and audit.

---

## 97. Emergency access

Break-glass administration is not frozen.

An emergency-access workflow is not built without activation, scope, expiry, approval, audit, and revocation.

---

## 98. Timezone contract

Administrative timestamps are stored as TIMESTAMPTZ where defined.

Frozen contract.

```text
Storage timezone: UTC
UI timezone:    detected from browser locale, with a user override
Filter timezone: UTC for API, converted to UI timezone at render
Audit display timezone: UTC in API, converted at render
```

---

## 99. Critical audit atomicity

For critical mutations, audit loss can create governance gaps.

Frozen policy.

```text
Domain mutation and audit insert commit atomically.
If audit insert fails, the transaction rolls back.
```

Autosave is not audited.

---

## 100. Admin configuration safety

High-impact settings include scoring, access, exam timing, snapshot preparation, security throttling, and communication.

Changes are permission-controlled, validated, and audited.

---

## 101. No generic workflow engine

A generic workflow designer is not created in Administration.

Business workflows remain in their owning application services.

Administration exposes controlled commands.

---

## 102. No generic rule engine

Executable expressions and scripts for BusinessRule are not created.

Rules remain constrained configuration interpreted by known modules.

---

## 103. No direct SQL console

Administration never exposes SQL editor, table selector, raw UPDATE, raw DELETE, or arbitrary database browser.

This would violate the architecture and security model.

---

## 104. No generic admin PATCH

`PATCH /api/v1/admin/{entity}/{id}` with arbitrary field maps is not created.

Use resource-specific DTOs and domain actions.

---

## 105. Reports integration

Reports are business outputs rather than database entities.

Use.

```text
POST /api/v1/reports
GET  /api/v1/reports/jobs/{jobId}
GET  /api/v1/reports/history
```

P11 may provide navigation and permissions for report generation.

---

## 106. Report security

Reports do not expose arbitrary SQL.

Use controlled report definitions and permission checks.

`GET /api/v1/query?sql=...` is prohibited.

---

## 107. File access

Generated and imported files use the existing protected file and artifact mechanism.

Filesystem path, object-storage secret URL, and unbounded direct storage access are never exposed.

---

## 108. Import path safety

A client filename is never used as a path.

Generate safe storage names and use managed storage services.

Validate file size and format before queueing.

---

## 109. Admin configuration secrets

Secret material belongs in deployment secret configuration.

It does not belong in OrganizationSetting, BusinessRule, Lookup, or AuditLog.

Admin UIs never display raw secret values.

---

## 110. Sensitive logging

Never log passwords, password reset tokens, JWT secrets or tokens, payment secrets, email-provider credentials, database credentials, or raw private provider payloads.

Use redacted operational metadata.

---

## 111. Search PII controls

Search follows role-specific redaction.

Support may see limited student identity.

Finance may see finance-safe purchase fields.

Admin sees broader permitted fields.

Global search does not bypass resource permissions.

---

## 112. Student data isolation

A student never obtains another student's purchase, grant, notification, attempt, or result by substituting UUIDs.

P11 tests object-level authorization across every composed panel.

---

## 113. Admin object scope

Even privileged users may have scoped permissions.

Every domain operation documents permission, object scope, state, and audit.

The admin UI is not an authorization shortcut.

---

## 114. Bulk object scope

The server revalidates every selected id.

The browser selection is untrusted input.

Selecting 100 users does not imply 100 authorized and eligible users.

---

## 115. Import object scope

Imported records are validated against organization context, referential data, business rules, and duplicate constraints.

Target modules own detailed validation.

---

## 116. Health endpoint exposure

Raw actuator and metrics endpoints are operational surfaces.

They are not exposed to students.

P11 health UI consumes a safe representation.

---

## 117. Redis failure

If Redis is unavailable, configuration reads fall back to PostgreSQL, lookup reads fall back to PostgreSQL, and business-rule reads fall back to PostgreSQL.

Redis is cache only.

---

## 118. Database failure

On PostgreSQL failure, mutations fail closed, no false success is returned, and domain truth is not inferred from cache.

The admin UI displays a controlled system error.

---

## 119. Worker failure

If the background worker is unavailable, bulk, import, and report jobs may stop or back up, health shows degradation, and synchronous core business operations remain separate.

Large background work is not executed synchronously as a workaround.

---

## 120. Provider failure

Payment provider failure does not make Admin itself unavailable.

Commerce operations show degraded capability while the underlying domain state remains authoritative.

---

## 121. Cross-domain partial failure

Example.

```text
Commerce refund succeeds.
Communication notification fails.
```

The refund remains authoritative.

Communication retries independently.

Admin does not roll back unrelated successful domain work merely because a notification failed.

---

## 122. Admin frontend routes

Suggested route areas.

```text
admin/dashboard
admin/users
admin/students
admin/roles
admin/permissions
admin/settings
admin/business-rules
admin/lookups
admin/audit
admin/imports
admin/bulk
admin/search
admin/system-health
admin/commerce
admin/communication
admin/assessments
admin/delivery
admin/evaluation
```

---

## 123. Admin components

Reusable components.

```text
AdminPageHeader
PermissionGuard
DataTable
FilterBar
StatusBadge
ConfirmDialog
DangerActionDialog
AuditDrawer
JsonConfigEditor
HealthCard
JobStatusPanel
ImportWizard
BulkActionBar
SearchResults
```

---

## 124. Admin query state

Use TanStack Query for lists, details, settings, rules, lookups, audit, health, search, and job status.

Use controlled invalidation after mutations.

---

## 125. Admin form state

Use React Hook Form and Zod.

Client validation improves UX.

Backend validation is authoritative.

---

## 126. Admin loading states

Every page has loading, empty, error, and success.

Long-running jobs show queued, running, completed, and failed.

---

## 127. Admin empty states

Examples.

```text
No business rules found.
No audit events match the filter.
No failed imports.
No active grants.
No matching students.
```

---

## 128. Admin error UX

Show safe error message, retry where applicable, and request id when useful.

Stack traces and secrets are not shown.

---

## 129. Admin accessibility

Support keyboard navigation, accessible labels, error announcements, table semantics, focus management, accessible dialogs, and clear unread and read semantics.

---

## 130. Admin responsive behavior

Primary usage is desktop, but screens remain usable on tablet and mobile browser.

A separate native admin app is not required.

---

## 131. API consistency

P11 continues the platform API standard.

```text
REST
JSON
HTTPS
/api/v1
JWT
UUID
Standard response envelope
Standard errors
Pagination
Filtering
Sorting
OpenAPI
X-Request-Id
```

---

## 132. Admin authorization flow

Every request.

```text
JWT authentication
    |
    v
Permission
    |
    v
Object scope
    |
    v
Domain state validation
    |
    v
Domain action
    |
    v
Audit where required
```

---

## 133. Admin status mutation

For stateful resources, prefer explicit action endpoints.

Examples.

```text
publish
archive
activate
refund
grant-access
revoke-access
```

Arbitrary status values from the client are not accepted.

---

## 134. Admin rate limiting

Potentially expensive endpoints.

```text
Login
Imports
Bulk
Global search
Large report generation
Cache-management actions if ever approved
```

Ordinary exam operations are not rate-limited through Admin-specific policies.

---

## 135. Bulk concurrency

Multiple identical bulk jobs do not corrupt domain state.

Target services remain responsible for idempotent state transitions, unique constraints, and state validation.

---

## 136. Import concurrency

Concurrent imports are controlled through job infrastructure, target-domain uniqueness, bounded worker concurrency, and idempotency.

UI button disabling is not the sole protection.

---

## 137. Role permission concurrency

Concurrent role and permission changes preserve UNIQUE(role_id, permission_id).

Identity transaction and constraints are used.

---

## 138. Lookup concurrency

Concurrent create of the same category and code is rejected by UNIQUE(category, code).

The UI surfaces a conflict rather than creating duplicates.

---

## 139. Business rule concurrency

Concurrent update and activation changes follow the configuration concurrency policy.

Frontend refresh alone is not relied upon.

---

## 140. Audit query performance

Primary access patterns.

```text
By user
By entity
By entity id
By action
By time
```

Frozen indexes and bounded pagination are used.

The entire audit table is never retrieved.

---

## 141. Search performance gate

Benchmark search on realistic datasets before selecting a dedicated search engine.

Initial approved direction: PostgreSQL-backed search.

Dedicated search infrastructure requires evidence and architecture review.

---

## 142. Admin dashboard performance

Dozens of sequential API calls are avoided.

Independent reads are parallelized where practical, bounded composition is used, safe metadata is cached, and N+1 calls are avoided.

Dashboard-specific transactional tables are not created.

---

## 143. Health performance

Health aggregation uses short timeouts, parallelizes independent checks, avoids blocking the admin page indefinitely, and exposes last-checked information when available.

---

## 144. Admin workload isolation

Large admin jobs do not starve exam snapshot workers, evaluation, autosave, submission, analytics, or commerce reconciliation.

Bounded worker and database resources are used.

---

## 145. PgBouncer interaction

Admin workloads use.

```text
Application
    |
    v
PgBouncer
    |
    v
PostgreSQL
```

Pool sizes remain load-test-driven.

---

## 146. PostgreSQL query safety

All admin queries use parameter binding, bounded pagination, allow-listed sort fields, no client SQL fragments, and query plan review for large tables.

---

## 147. Mass assignment prevention

Arbitrary request bodies are never bound directly to entities.

Explicit DTOs protect ids, audit fields, status, ownership, and privileged fields.

---

## 148. Configuration injection tests

Test script-like JSON, executable expression, huge nested JSON, invalid types, unexpected keys, and invalid cross-field combinations.

Rules fail safely.

---

## 149. Stored XSS tests

Test stored XSS in announcement data, lookup names, rule descriptions if introduced, audit remarks, and template UI fields.

Render text safely.

---

## 150. File upload tests

Test oversized file, wrong MIME, malformed CSV, path traversal filename, malicious content, and excessive row count.

No upload writes to arbitrary filesystem paths.

---

## 151. Privilege escalation tests

Explicitly test student to ADMIN, support to ADMIN, finance to ADMIN, staff to ADMIN, and self-assignment of privileged permission.

Every escalation attempt is denied unless policy explicitly permits it.

---

## 152. Audit tamper tests

Attempt audit update, audit delete, actor spoofing, and entity spoofing.

Expected: immutable records, server-derived actor, no generic mutation endpoint.

---

## 153. System health security tests

Verify students cannot access actuator internals, Prometheus internals, environment dumps, or provider credentials.

---

## 154. Unit tests - Settings

Test valid update, invalid JSON and schema, unknown key policy, cross-field validation, duplicate organization prevention, cache invalidation, audit invocation, and concurrency policy.

---

## 155. Unit tests - Business rules

Test unique code, valid config, invalid config, lifecycle, consumer resolution, cache invalidation, permission, and audit.

---

## 156. Unit tests - Lookups

Test unique category and code, update, status, display order, category filter, and delete safety.

---

## 157. Unit tests - Audit

Test insert, filters, pagination, immutable service API, authorization, and invalid entity handling.

---

## 158. Integration - User admin

Use P1 services for user creation, update, activation, archive, role assignment, permission resolution, and session inspection.

Assert no admin repository access.

---

## 159. Integration - Commerce admin

Use P9 for package, plan, purchase, payment, refund, grants, and coupons.

Assert finance and admin scope are respected.

---

## 160. Integration - Assessment admin

Use P4 for assessment, version, schedule, publication, and closure.

Assessment rows are not patched directly.

---

## 161. Integration - Delivery admin

Use P5 for schedule operations, snapshot monitoring, attempt monitoring, session monitoring, and approved recovery and support actions.

Snapshot data is not changed directly.

---

## 162. Integration - Evaluation admin

Use P6 for evaluation, re-evaluation, result publication, and scorecard access.

Result and score fields are not directly updated.

---

## 163. Integration - Communication admin

Use P10 for templates, announcements, notification support, and delivery observability.

No AdminCommunication persistence is created.

---

## 164. End-to-end - Configuration

Admin login, settings, update, validate, save, audit, consumer sees approved configuration.

---

## 165. End-to-end - Role administration

Admin login, role, permissions, save, verify effective permission, audit.

---

## 166. End-to-end - Support

Support login, search student, inspect access, inspect attempt, inspect session, attempt prohibited mutation, denied.

---

## 167. End-to-end - Finance

Finance login, purchase lookup, payment lookup, refund through Commerce, audit, verify authorization follows P9 policy.

---

## 168. End-to-end - Bulk

Admin select records, bulk action, 202 Accepted, job status, results, structured failures, audit.

---

## 169. End-to-end - Import

Admin upload source, 202, job progress, row errors, successful records, target-domain validation, audit.

---

## 170. End-to-end - Health

Admin open health, inspect components, simulate Redis failure, observe DEGRADED, restore, observe recovery.

---

## 171. Mixed workload test

Run admin imports, bulk operations, and search alongside representative student exam traffic.

Measure database CPU, PostgreSQL connections, PgBouncer waiting, API latency, autosave latency, submission latency, and worker saturation.

Administration does not invalidate P7 performance evidence.

---

## 172. Async job safety

Jobs have explicit type, validate parameters, enforce permission at submission, revalidate state during execution, are bounded, and support safe retry.

The client never chooses a worker implementation class.

---

## 173. Job status UX

Typical job states.

```text
QUEUED
RUNNING
COMPLETED
COMPLETED_WITH_ERRORS
FAILED
```

The job status model belongs to common infrastructure, not a new admin domain table.

---

## 174. No job history table

The frozen administration model does not include job history.

The common `background_job` framework is used for bulk, imports, reports, and other approved long-running work.

---

## 175. Admin observability

Metrics.

```text
admin.api.latency
admin.api.error
admin.config.update
admin.business_rule.update
admin.lookup.mutation
admin.audit.query
admin.bulk.queued
admin.import.queued
admin.search.latency
admin.health.query
```

---

## 176. Metrics label safety

High-cardinality PII is never used as metric labels.

Avoid email, mobile, free-form search query, remarks, and payment reference.

Use bounded labels such as operation, resource, status, and error_code.

---

## 177. Admin logging

Structured fields.

```text
requestId
actorId where safe
operation
target entity
outcome
latency
error code
```

Secrets and raw sensitive payloads are never logged.

---

## 178. Tracing

Trace important operations.

```text
Admin
    |
    v
Domain service
    |
    v
Repository or provider
    |
    v
Audit
```

Cross-domain trace examples.

```text
Admin to Commerce to payment provider
Admin to Assessment to Delivery
Admin to Evaluation
```

---

## 179. Configuration cache tests

After changing settings, rules, or lookups, verify the next authorized read sees the intended value under the platform's cache invalidation behavior.

---

## 180. Business rule effective time

The source does not freeze scheduled future rule changes.

`effective_from` and `effective_to` are not added without an explicit schema and API amendment.

---

## 181. Configuration rollback

No automatic point-in-time configuration rollback is guaranteed by the frozen schema.

Where correction is needed, use approved current-value change, audit the change, and observe downstream behavior.

---

## 182. Role change rollback

Use Identity role and permission operations to correct a privilege change.

Do not directly modify user_role or role_permission.

---

## 183. User archive rollback

Use Identity lifecycle services if reactivation is allowed by policy.

Do not restore user rows with direct SQL.

---

## 184. Lookup archive rollback

Reactivation uses Lookup service and API if the lifecycle allows it.

DELETE is not assumed reversible.

---

## 185. Admin data retention

P11 does not create new retention tables.

Retention of audit, lookups, business rules, and settings follows approved organization and platform policy.

---

## 186. Administrative security review

Before production.

```text
RBAC review
Privilege escalation review
IDOR review
Configuration injection review
Import security review
Search privacy review
Audit immutability review
Health endpoint review
Sensitive-log review
```

---

## 187. Admin API contract review

Every P11-owned endpoint is represented in OpenAPI, a permission matrix, request and response DTOs, an error contract, and integration tests.

No hidden admin API.

---

## 188. Admin database review

Every P11 database change uses Flyway, preserves the frozen schema, includes constraints and indexes, includes migration tests, and is reviewed for backward compatibility.

P11 requires no new admin tables.

---

## 189. Seed strategy

Production seeds may include initial organization, approved roles, permissions, lookup defaults, and required business-rule defaults.

Fake users, purchases, payments, attempts, results, grants, and audit history are not seeded.

---

## 190. Seed idempotency

Seed operations are version-controlled, repeatable, and protected by unique constraints or safe checks.

Duplicate role, permission, lookup, or business-rule rows are not created on restart.

---

## 191. OrganizationSetting bootstrap

The unique organization constraint prevents duplicate setting rows.

Bootstrap is idempotent and safe under concurrent application startup.

---

## 192. Audit bootstrap

Fake historical audit records are not created in production.

---

## 193. Deployment

P11 remains part of the modular monolith deployment.

```text
Next.js
Spring Boot
PostgreSQL
Redis
Worker infrastructure
```

A separate admin service is not required.

---

## 194. CI

P11 CI runs backend unit tests, backend integration tests, frontend tests, security tests, OpenAPI validation, lint, build, and migration checks.

---

## 195. Regression gate

P11 does not break student login, exam start, autosave, recovery, submission, evaluation, AccessGrant authorization, analytics, communication, or commerce.

---

## 196. Rollback strategy

Application rollback does not destroy historical data.

Audit, financial records, attempts, results, and grants are not deleted.

Prefer application rollback and safe Flyway-compatible migration strategy.

---

## 197. Admin feature flags

Possible operational flags.

```text
admin.search.enabled
admin.bulk.enabled
admin.import.enabled
admin.system-health.enabled
```

Feature flags control availability. They are not permission substitutes.

---

## 198. Safe disable

If an admin feature is unsafe, disable the affected feature without disabling the entire examination platform.

Examples.

```text
Disable bulk
Disable import
Disable global search
```

---

## 199. Admin search rollout

Global search remains disabled until its resource contract, permission model, PII policy, pagination, and performance are validated.

---

## 200. System health rollout

Health aggregation remains behind a controlled rollout until its component list, timeouts, permissions, safe response, and failure semantics are validated.

---

## 201. Support scope rollout

Support capabilities are enabled only after the data-access scope is explicitly frozen.

Support is not defaulted to ADMIN behavior.

---

## 202. Bulk rollout

Start with a small set of proven operations.

Arbitrary bulk combinations are not exposed.

---

## 203. Import rollout

Start with approved source formats and target resources.

Every target-specific import has mapping, validation, error reporting, and retry semantics.

---

## 204. Audit atomicity

Critical operations are not silently considered complete when required audit persistence fails.

P11 audit uses the shared transactional audit behavior.

---

## 205. No silent architecture change

An implementation stops and reports when safe delivery requires a new table, a new cross-domain foreign key, a new persistent history, a new API pattern, a new service boundary, a new event broker, or a new search infrastructure.

Source gaps are implementation blockers, not invitations to guess.

---

## 206. P11 implementation task breakdown

```text
P11-01  Administration module skeleton
P11-02  Organization settings
P11-03  Business rules
P11-04  Lookups
P11-05  Audit explorer
P11-06  User administration
P11-07  Student administration
P11-08  Role and permission administration
P11-09  Commerce operations
P11-10  Content and assessment operations
P11-11  Delivery operations
P11-12  Evaluation operations
P11-13  Communication operations
P11-14  Admin dashboard
P11-15  Support console
P11-16  Imports
P11-17  Bulk operations
P11-18  Global search
P11-19  System health
P11-20  Security hardening
P11-21  Mixed workload
P11-22  End-to-end and release
```

Execute one task at a time with checkpoints.

---

## 207. P11-01 module skeleton

Create `com.company.exam.administration`.

Packages.

```text
api
application
domain
repository
configuration
security
audit
operations
```

External module calls go behind application and service contracts.

---

## 208. P11-02 settings acceptance

Settings support get, validated update, permission, audit, cache invalidation, concurrency behavior, and safe errors.

---

## 209. P11-03 business rule acceptance

Rules support list, get, create, update, lifecycle, rule-specific validation, cache invalidation, and audit.

---

## 210. P11-04 lookup acceptance

Lookups support category filter, list, get, create, update, delete safety, unique category and code, and audit.

---

## 211. P11-05 audit acceptance

Audit explorer supports search, filters, pagination, permission, immutable records, and safe display.

---

## 212. P11-06 user acceptance

User operations are complete when P1 APIs integrate, permissions are checked, object scope is checked, dangerous actions are audited, and cache invalidation works.

---

## 213. P11-07 student acceptance

Student administration is complete when the admin can safely inspect identity, roles, sessions, access, purchases, attempts, results, and analytics without creating duplicate domain entities.

---

## 214. P11-08 role acceptance

Role and permission management is complete when role CRUD integrates with P1, permission assignments are explicit, self-escalation rules are enforced, cache invalidation works, and audit is present.

---

## 215. P11-09 commerce acceptance

Commerce admin is complete when all mutations call P9 services and follow permission, object scope, state, provider rules, audit, and idempotency.

---

## 216. P11-10 content and assessment acceptance

Content and Assessment administration is complete when admin navigation is available, P3 and P4 APIs are reused, no duplicate admin repositories are introduced, and publication actions remain domain-owned.

---

## 217. P11-11 delivery acceptance

Delivery operations are complete when snapshot status is visible, attempt and session state is visible, operational recovery uses P5 contracts, and no snapshot table is directly modified.

---

## 218. P11-12 evaluation acceptance

Evaluation operations are complete when evaluation status is visible, re-evaluate is controlled, result publication is controlled, and no score is edited from Admin.

---

## 219. P11-13 communication acceptance

Communication administration is complete when templates and announcements use P10, notification operations remain user-scoped, provider secrets are hidden, and delivery state uses P10 observability and job mechanisms.

---

## 220. P11-14 dashboard acceptance

Dashboard is complete when health, operational counts, recent audit, alerts, and links are composed from authoritative sources without a new transactional dashboard model.

---

## 221. P11-15 support acceptance

Support is complete when authorized lookup works, sensitive fields remain protected, support scope is explicit, prohibited actions are denied, and support actions are auditable.

---

## 222. P11-16 import acceptance

Import is complete when file upload is secure, job is asynchronous, validation is target-owned, row errors are structured, retries are safe, and progress and result are visible.

---

## 223. P11-17 bulk acceptance

Bulk is complete when allowed operations are explicit, jobs are asynchronous where large, selected ids are revalidated, partial failures are reported, retries are safe, and audit is recorded.

---

## 224. P11-18 search acceptance

Search is complete when permission filtering passes, PII redaction passes, pagination passes, performance evidence exists, and injection tests pass.

---

## 225. P11-19 health acceptance

Health is complete when safe statuses are returned, outages are represented accurately, secrets are protected, timeouts are bounded, and permissions pass.

---

## 226. P11-20 security acceptance

Security gate.

```text
RBAC
IDOR
Privilege escalation
Mass assignment
XSS
Import security
Search security
Audit immutability
Secret leakage
Health endpoint exposure
```

---

## 227. P11-21 performance acceptance

Performance gate.

```text
Admin p95 measured
Audit query measured
Search measured
Bulk and import measured
Dashboard measured
Health measured
Mixed admin and exam workload validated
```

Theoretical capacity is not a substitute for measurements.

---

## 228. P11-22 release acceptance

Release requires source mapping, tests, migration review, OpenAPI, security review, observability, runbooks, staging verification, and rollback plan.

---

## 229. Recommended implementation prompt

```text
You are implementing P11 Full Administration for the Smart Competitive
Examination Platform.

Read the full specification package before modifying the repository.

P11 owns:
organization_setting
business_rule
lookup
audit_log

P11 does not own the business tables of other modules.

NON-NEGOTIABLE:
1.  Use the owning domain service for every cross-domain operation.
2.  Do not directly use cross-domain repositories.
3.  Use explicit permissions.
4.  Audit important mutations.
5.  Preserve the frozen four-table Administration schema.
6.  Reuse existing APIs, job infrastructure, and observability.
7.  Do not invent search, bulk, import, health, or history tables.
8.  Stop and report unresolved contract gaps.
9.  Do not create generic SQL or admin endpoints.
10. Do not expose secrets.
11. Keep Admin as a control plane, not a replacement domain.
12. Use TanStack Query for admin server state.
13. Use React Hook Form and Zod for admin forms.
14. Global search uses the frozen resource allow-list.
15. Bulk operations use the frozen combination allow-list.
16. Import targets follow the frozen registry.
17. Health aggregation is safe and bounded.
18. Audit is atomic with the business transaction.
19. System actor is used for background operations.
20. Stop and report rather than silently resolving frozen-contract
    gaps.

Task order:
P11-01 through P11-22.

For each task:
- Inspect current repository first.
- Implement the smallest correct change.
- Run focused tests.
- Run PostgreSQL integration tests.
- Report changed files.
- Report API impact.
- Report schema impact.
- Report unresolved contract issues.
- Stop before the next high-risk task.

Before merge:
- Unit tests
- Integration tests
- API tests
- Security tests
- Concurrency tests
- End-to-end tests
- Migration verification
- OpenAPI verification
- Audit verification
- RBAC verification
- Mixed workload validation
- CI green
```

---

## 230. Review questions

```text
Does the implementation match the frozen four-table administration schema?
Does every cross-domain operation go through the owning service?
Are permissions checked for every endpoint?
Is the last-admin rule enforced?
Is self-role modification blocked?
Is global search permission-filtered?
Is PII masked by default?
Is bulk operation a controlled allow-list?
Is import file validation sufficient?
Is system health free of secrets?
Is audit atomic with the business transaction?
Is the system actor used for background operations?
Does the frozen schema remain unchanged?
Are the frozen APIs unchanged?
```

---

## 231. Final engineering principle

> Administration is the organizational control plane, not a business domain. It stores configuration, rules, lookups, and audit. Every cross-domain operation calls the owning service, uses explicit permissions, and produces auditable evidence.

The control plane flow.

```text
Admin UI
    |
    v
Authorization service
    |
    v
Owning application service
    |
    v
Business validation
    |
    v
Domain state transition
    |
    v
Audit
    |
    v
Response
```

---

## 232. P11 handoff

P11 hands the rest of the platform a complete operational control plane.

```text
Configuration management
Business rule governance
Lookup management
Audit explorer
User and role operations
Student support operations
Commerce operations
Content and assessment operations
Delivery and evaluation operations
Communication operations
Bulk and import orchestration
Global search
System health
```

---

## 233. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P11 — Full Administration.

**Next implementation document:** P12 — Production Hardening and Production Readiness.