# Smart Competitive Examination Platform
## P10 — Communication & Notification Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P10 — Communication & Notification
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, P4 Blueprint and Assessment Management, P5 Assessment Delivery and Runtime Examination Engine, P6 Evaluation and Results, P7 Scale Validation and Live Pilot, P8 Full Analytics and Insights, P9 Commerce and Access Management
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**API:** REST + JSON, `/api/v1`
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Operations, Architects

---

## 1. Purpose

This document converts the Communication domain into an implementation-ready engineering specification.

The Communication module is responsible for informational user communication through:

```text
Notification
EmailTemplate
Announcement
```

The module supports:

```text
Notifications
Notification center
Email templates
Announcements
Publishing
Delivery status
Retry mechanism
```

The design keeps Communication independent from the business module that creates the business event.

The core architectural flow.

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

Long-running and external communication delivery does not block the originating business transaction.

---

## 2. Source-of-truth hierarchy

Read these documents before P10 implementation:

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
- This document

The earlier documents already freeze the notification, template, and announcement schema, and the asynchronous delivery principle. This document does not modify those decisions.

---

## 3. Scope

In scope.

```text
Notification
Notification center
EmailTemplate
Announcement
Announcement publishing
In-app notification creation
Transactional communication triggers
Email rendering
Email provider abstraction
Background communication delivery
Delivery retry
Delivery status handling
Communication preferences only where already supported by configuration
Security
Authorization
Audit
Metrics
Logging
Operational dashboards
Communication tests
Commerce event integration
Evaluation and result event integration
Assessment notification integration
```

Out of scope.

```text
SMS provider or domain
WhatsApp provider or domain
Push notification provider or domain
Marketing campaign engine
User segmentation engine
CRM
Newsletter automation
Rich template designer
Visual drag-and-drop email builder
Email analytics warehouse
Open and click tracking
A/B testing
Subscriber lists
Contact management
Communication preference table
Message delivery history table
Notification delivery attempt table
Persistent event store
External event broker
AI-generated communication content
Bulk campaign scheduler
Complex localization management
```

---

## 4. Communication domain model

The Communication entities are:

```text
Notification
EmailTemplate
Announcement
```

Relationships.

```text
User 1:N Notification

EmailTemplate    -> reusable email content
Announcement     -> organization-level informational message
```

The database does not contain a direct relationship from Notification to EmailTemplate.

No such foreign key is added without an explicit amendment.

---

## 5. Responsibilities

### 5.1 Communication owns

```text
Notification records
Email templates
Announcements
Announcement publication
Notification rendering
Email rendering
Communication delivery orchestration
Retry orchestration
Delivery status normalization at the application and provider boundary
Communication observability
Communication audit
```

### 5.2 Business modules own

```text
Commerce      -> Purchase, PaymentRecord, AccessGrant
Assessment    -> AssessmentSchedule and publication
Delivery      -> Attempt and session state
Evaluation    -> Result publication
Identity      -> User identity
```

Communication reacts to business events. It does not become the source of truth for those business states.

---

## 6. Core architectural rule

Business modules do not directly implement email delivery logic.

Forbidden.

```text
PurchaseService
    |
    v
SMTP client
```

Preferred.

```text
PurchaseService
    |
    v
Application event
    |
    v
Notification service
    |
    v
Delivery job
    |
    v
Email provider
```

Likewise.

```text
ResultPublished
    |
    v
Communication
    |
    v
Email or notification
```

---

## 7. Frozen database schema

### 7.1 Table: notification

```sql
notification
------------
id UUID PK
user_id UUID FK
title VARCHAR(255)
message TEXT
notification_status
created_on TIMESTAMPTZ
```

Indexes.

```text
idx_notification_user_id
idx_notification_notification_status
```

The frozen table intentionally does not contain `updated_on`, `created_by`, `updated_by`, `delivery_status`, `event_type`, `template_id`, `read_on`, or `expires_on`.

These fields are not added without a formal database amendment.

### 7.2 Table: email_template

```sql
email_template
--------------
id UUID PK
code VARCHAR(50)
name VARCHAR(150)
subject VARCHAR(255)
body TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 7.3 Table: announcement

```sql
announcement
------------
id UUID PK
title VARCHAR(255)
description TEXT
start_date TIMESTAMPTZ
end_date TIMESTAMPTZ
created_by UUID FK
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Indexes.

```text
idx_announcement_created_by
idx_announcement_start_date
idx_announcement_end_date
idx_announcement_status
```

---

## 8. Notification

Purpose: in-app notification for a user.

Frozen relationship: `User 1:N Notification`.

Notification status.

```text
UNREAD
READ
```

Lifecycle.

```text
UNREAD
   |
   v
READ
```

Reading is a state transition. There is no frozen unread to archived state.

---

## 9. Notification read semantics

When the user calls `POST /api/v1/notifications/{id}/read`, the backend authenticates, authorizes ownership, verifies notification exists, and changes UNREAD to READ.

A repeated read operation is idempotent from the user's perspective.

Example.

```text
UNREAD -> READ
READ   -> READ
```

A harmless repeat read does not return an invalid-state error unless the global action policy requires one.

---

## 10. Notification center

The notification center is a frontend presentation over the notification table.

Expected views.

```text
All
Unread
Read
```

The backend is responsible for filtering.

The frontend does not fetch all notifications and filter locally when the data volume can grow.

---

## 11. Notification center UX

Student-facing experience.

```text
Bell icon
   |
   v
Unread count
   |
   v
Notification list
   |
   v
Open notification
   |
   v
Read state
```

Minimum information.

```text
title
message
notification status
created_on
```

No extra field is assumed to exist in the frozen database.

---

## 12. Notification API

```http
GET /api/v1/notifications
```

Returns the authenticated student's notifications.

Cross-user notification access is not permitted.

```http
POST /api/v1/notifications/{id}/read
```

Requires notification belongs to current user.

```http
POST /api/v1/notifications/read-all
```

Operation.

```text
Current user's UNREAD notifications
        |
        v
READ
```

Scoped by authenticated user id. Not updating all records in the organization.

---

## 13. Notification list contract

Recommended response.

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": {
    "content": [
      {
        "id": "<uuid>",
        "title": "Result Published",
        "message": "Your assessment result is now available.",
        "notificationStatus": "UNREAD",
        "createdOn": "2026-10-07T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

The exact pagination envelope follows the shared platform API convention.

---

## 14. Notification creation contract

Notification creation is an application-level contract rather than a public student CRUD API.

Suggested interface.

```java
public interface NotificationService {

    NotificationResult notifyUser(
        UUID userId,
        NotificationCommand command
    );

    NotificationResult notifyUsers(
        Collection<UUID> userIds,
        NotificationCommand command
    );

    Page<NotificationView> listForUser(
        UUID userId,
        NotificationQuery query
    );

    void markRead(
        UUID userId,
        UUID notificationId
    );

    void markAllRead(
        UUID userId
    );
}
```

There is no frozen public `POST /api/v1/notifications` endpoint.

Public arbitrary notification creation for students is not provided.

---

## 15. Notification message contract

A notification command may contain title, message, and recipient.

The source does not freeze a persistent notification template relationship.

Therefore `templateId`, `eventId`, and `sourceEntityId` are not added to `notification` without amendment.

---

## 16. Business event to notification

The common model.

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
```

Examples of events.

```text
PurchaseSucceeded
PaymentFailed
PaymentRefunded
AccessGranted
AccessRevoked
AssessmentScheduled
AssessmentStarting
ResultPublished
PasswordResetRequested
```

The event catalog is documented and versioned. Dozens of unrelated business conditions are not hard-coded into controllers.

---

## 17. Application events

Initially, events may remain internal to the process using in-process application events and background jobs.

No distributed broker is required by the frozen architecture.

Kafka, RabbitMQ, or another broker is not introduced merely because Communication has asynchronous work.

---

## 18. Event handling rule

The producer owns business truth.

For example.

```text
Commerce
    |
    v
PaymentSucceeded
```

Communication may send notification and email but does not change payment status, create AccessGrant, or refund purchase.

---

## 19. Event transaction boundary

A business transaction does not depend on successful email delivery.

Preferred.

```text
Commerce transaction
    |
    v
Commit Purchase, Payment, and Grant
    |
    v
Publish or queue communication work
```

Not.

```text
BEGIN
  purchase
  payment state
  send email over network
  commit
```

This keeps external latency and failures out of the core business transaction.

---

## 20. Exactly-once business versus at-least-once delivery

Communication delivery may be retried.

Therefore.

```text
Business operation   -> authoritative once
Communication send   -> safely retryable
```

An email may be attempted more than once unless provider or application idempotency prevents duplication.

A notification record does not cause business state to be duplicated.

---

## 21. EmailTemplate

Purpose: reusable email content.

Frozen examples and categories.

```text
Welcome
Password Reset
Purchase Confirmation
Assessment Notification
Result Published
```

The exact template catalog is configurable.

Frozen schema fields.

```text
id
code
name
subject
body
status
created_by
created_on
updated_by
updated_on
```

---

## 22. EmailTemplate lifecycle

Standard lifecycle.

```text
ACTIVE
INACTIVE
ARCHIVED
```

The frozen schema has no dedicated publication field.

Do not create `is_published`, `version`, or `published_on` unless the requirement is formally amended.

---

## 23. Template code

`code` is unique, stable, machine-readable.

Examples.

```text
WELCOME
PASSWORD_RESET
PURCHASE_CONFIRMATION
ASSESSMENT_NOTIFICATION
RESULT_PUBLISHED
```

Do not use user-visible translated text as the identifier.

---

## 24. Template subject

`subject` stores the email subject.

The subject may contain template variables such as `{{studentName}}`.

The templating engine is fixed as follows.

```text
Variables use {{variableName}} syntax.
Rendering is a pure string replacement against a controlled model.
No expression evaluation, method invocation, or scripting.
```

---

## 25. Template body

`body` stores the reusable email content as HTML.

The body may contain the same `{{variableName}}` placeholders.

The body is sanitized before rendering using the platform's HTML allow-list sanitizer.

---

## 26. Email rendering security

Template rendering prevents template injection, arbitrary code execution, and server-side template execution beyond approved syntax.

Forbidden.

```text
JavaScript
Arbitrary script
Server-side method invocation
Filesystem access
Network access
```

The body renderer exposes an allow-listed variable model.

---

## 27. Email template variables

Recommended logical context.

```text
studentName
organizationName
assessmentName
resultSummary
purchaseSummary
accessPackageName
grantStartDate
grantEndDate
```

The exact variables are event and template specific.

JPA entities are not sent to a template engine. Explicit DTOs are built for the template model.

---

## 28. Missing template behavior

If a business event requires an email but the corresponding template is missing, inactive, or invalid, the business transaction does not fail merely because communication failed.

Instead.

```text
Business transaction succeeds
Communication job fails or is skipped
Operational alert is raised
Retry or remediation is available
```

This is essential separation of concerns.

---

## 29. Email provider boundary

The exact email provider is not frozen.

Create an abstraction.

```java
public interface EmailGateway {

    EmailProviderId provider();

    EmailSendResult send(
        RenderedEmail email
    );
}
```

Provider-specific code stays below this boundary.

The Communication module does not depend directly on a provider SDK outside the adapter.

---

## 30. Email delivery flow

```text
Business event
      |
      v
Notification engine
      |
      v
Resolve recipients
      |
      v
Resolve template
      |
      v
Render content
      |
      v
Enqueue delivery work
      |
      v
EmailGateway
      |
      v
Provider
      |
      v
Normalize result
      |
      v
Delivery status, metrics, logs
```

External provider interaction is asynchronous.

---

## 31. Email delivery status

The roadmap requires delivery status and retry.

The frozen database does not contain an email delivery record.

The frozen `notification` table only represents in-app notification status (UNREAD or READ).

It does not represent EMAIL_PENDING, EMAIL_SENT, EMAIL_FAILED, attempt count, provider reference, or retry time.

Therefore a new `notification_delivery`, `email_delivery`, or similar table is not added silently.

The operational delivery status is represented through:

```text
background_job state
structured logs
metrics
provider response
```

This is not claimed as durable historical database truth.

---

## 32. Background delivery queue

The roadmap requires a delivery queue conceptually.

This does not necessarily mean a new database table.

The implementation reuses the existing `background_job` infrastructure.

```text
Business event
   |
   v
background_job insert
   |
   v
Worker claims job
   |
   v
EmailGateway
   |
   v
Update job status
```

A Commerce or Communication-specific queue table is not created.

---

## 33. Retry strategy

Communication delivery uses bounded retry.

Conceptual model.

```text
Attempt 1
   |
   v failure
Retry 1
   |
   v failure
Retry 2
   |
   v failure
Retry N
   |
   v
Permanent failure and operational alert
```

Frozen defaults.

```text
Maximum attempts: 5
Backoff: exponential starting at 60 seconds
Jitter: plus or minus 20 percent
Cap: 30 minutes between attempts
```

The values are configurable at the deployment level via BusinessRule where the platform supports it.

---

## 34. Retryable errors

Retryable conditions.

```text
Provider timeout
Network failure
Temporary 5xx
Rate limiting
Temporary provider unavailable
```

Non-retryable conditions.

```text
Invalid recipient format
Invalid template
Authentication permanently rejected
Content rejected permanently
Unknown provider configuration
```

The exact mapping is provider-specific and is documented in the adapter.

---

## 35. Retry backoff

Use exponential backoff with bounded maximum delay and jitter to avoid synchronized retry storms.

Example schedule.

```text
1 minute
5 minutes
15 minutes
30 minutes
```

The exact production values follow the deployment configuration and load-test result.

---

## 36. Retry idempotency

Retrying the same communication job does not trigger duplicate business effects.

Communication is designed around idempotent business event handling, provider-side send idempotency where supported, and job deduplication.

The exact persistent idempotency mechanism reuses the common platform `background_job` infrastructure.

---

## 37. Email provider timeout

If the provider request times out, the application does not assume success.

The operation is set to an unresolved or pending operational state and follows the retry and reconciliation policy.

The email is not automatically sent again if the provider may already have accepted it and has no idempotency support.

Provider idempotency behavior is documented per adapter.

---

## 38. Email send ambiguity

For a request sent but response lost scenario, the implementation follows this policy.

```text
If the provider supports idempotency keys:
    Retry with the same key.

If the provider exposes a query API:
    Query the provider status before retrying.

If neither is available:
    Log the ambiguity and require operational review before retry.
```

Duplicate-email tolerance is documented per template and per event type.

---

## 39. Announcement

Purpose: organization-level informational message.

Announcements may target appropriate user groups.

The frozen schema does not contain a target-group relation.

Frozen schema.

```sql
announcement
------------
id UUID PK
title VARCHAR(255)
description TEXT
start_date TIMESTAMPTZ
end_date TIMESTAMPTZ
created_by UUID FK
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

---

## 40. Announcement targeting

The frozen schema does not contain a targeting table or audience field.

Do not invent `announcement_target`, `announcement_audience`, `role_id`, or `user_group_id`.

The approved targeting model is.

```text
All active users in the organization
or
All users with a specific role (resolved via Identity at fan-out time)
```

No additional targeting modes are introduced without a formal amendment.

---

## 41. Announcement lifecycle

Frozen API provides create, update, delete, and publish.

The schema contains only the standard `status`.

State.

```text
DRAFT or INACTIVE
       |
       v
PUBLISHED or ACTIVE
       |
       v
INACTIVE or ARCHIVED
```

The exact status vocabulary uses the standard lifecycle values.

A second hidden state machine is not introduced.

---

## 42. Announcement API

```http
GET    /api/v1/announcements
GET    /api/v1/announcements/{id}
POST   /api/v1/announcements
PUT    /api/v1/announcements/{id}
DELETE /api/v1/announcements/{id}
POST   /api/v1/announcements/{id}/publish
```

Publishing may generate notifications.

---

## 43. Announcement publication

Publishing validates title present, description present where required, date range valid, status allows publication, and targeting configuration valid if defined.

Then.

```text
Publish action
   |
   v
Announcement state
   |
   v
Publish event
   |
   v
Notification generation if configured
```

---

## 44. Announcement date window

Frozen fields.

```text
start_date
end_date
```

Semantics.

```text
start_date inclusive
end_date inclusive
Both may be NULL, meaning the announcement is visible until further notice
Server time is authoritative
```

---

## 45. Announcement visibility

The student-facing announcement view shows only announcements that satisfy the approved status, start_date, end_date, and targeting rules.

Unpublished administrative announcements are not exposed.

---

## 46. Announcement notification behavior

Publishing may generate notifications.

This is implemented as.

```text
Publish transaction
    |
    v
Announcement published
    |
    v
Application event
    |
    v
Notification processing
```

Not.

```text
AnnouncementController
    |
    v
Loop over 50,000 students
    |
    v
Insert notifications synchronously
```

---

## 47. Large announcement audience

For large audiences.

```text
Publish
   |
   v
Normal action response
   |
   v
Background job
   |
   v
Resolve audience
   |
   v
Create notifications in batches
```

Long-running fan-out does not block the request.

---

## 48. Notification fan-out

Potential audience sizes.

```text
10
100
1,000
10,000
100,000+
```

The implementation batches inserts.

One HTTP or database transaction per recipient is avoided where batching is practical.

---

## 49. Notification insert efficiency

Use batch insert, JDBC or JPA batch configuration where safe, and periodic commits while keeping transactions bounded.

Exact batch size is a load-test value.

---

## 50. Notification duplicate prevention

The frozen database does not define `UNIQUE(user_id, event_id)` and Notification has no event id.

Therefore a deduplication constraint is not invented.

Instead, the existing application and job idempotency framework is used.

If durable exactly-once notification fan-out is required, that becomes a persistence amendment.

---

## 51. Notification fan-out idempotency

The fan-out job is identified by the `background_job` id.

A retry of the same job detects the existing job state and does not create duplicate notifications for the same recipient scope.

If notification duplication tolerance is required for the pilot, it is documented and accepted.

---

## 52. Commerce integration

P9 introduced business events such as PurchaseCreated, PaymentSucceeded, PaymentFailed, PaymentRefunded, AccessGranted, and AccessRevoked.

Communication may subscribe to these.

Example.

```text
PaymentSucceeded
      |
      v
Notification
      +
Purchase confirmation email
```

---

## 53. Commerce event responsibility

Commerce remains the source of truth.

Communication only consumes the normalized event.

Communication does not query payment-provider payloads to determine whether payment succeeded.

---

## 54. Assessment integration

Potential informational events.

```text
AssessmentPublished
AssessmentScheduled
AssessmentStarting
```

Communication may notify students.

Delivery timing remains owned by AssessmentSchedule and business rules, not by email templates.

---

## 55. Evaluation integration

Potential event.

```text
ResultPublished
```

Flow.

```text
Result service
    |
    v
ResultPublished
    |
    v
Communication
    |
    v
Notification
    |
    v
Email
```

Communication does not calculate results.

---

## 56. Identity integration

Potential events.

```text
UserRegistered
PasswordResetRequested
```

The exact password-reset flow remains under Identity.

Communication sends a message. Identity remains authoritative for reset token creation and validation.

Password reset domain logic is not moved into Communication.

---

## 57. Template catalog

The source lists examples: Welcome, Password Reset, Purchase Confirmation, Assessment Notification, Result Published.

A controlled template registry is maintained in code and configuration.

The `email_template` table stores active template content. The application maps event to template code.

Event mappings are not stored in an ad-hoc JSON structure unless formally approved.

---

## 58. Template selection

Example.

```text
PaymentSucceeded
   |
   v
templateCode = PURCHASE_CONFIRMATION
   |
   v
EmailTemplate
```

The event-to-template catalog is explicit and testable.

---

## 59. Template fallback

A random template is not substituted silently.

Expected behavior.

```text
Template found  -> render
Template missing -> communication failure and alert
```

The business transaction remains successful.

---

## 60. Template inactivation

When an email template is INACTIVE, a communication job requiring it fails safely.

A stale cached inactive template does not continue sending without a controlled invalidation strategy.

---

## 61. Template cache

Redis may cache ACTIVE EmailTemplate with invalidation on create, update, activate, deactivate, and archive.

The database remains the source of truth.

---

## 62. Redis failure

If Redis is unavailable, the template is loaded from PostgreSQL.

Business operations do not fail solely because template cache is down.

---

## 63. Notification query cache

Default: student notification lists are not cached in Redis because notification read state is transactional and user-specific.

PostgreSQL remains authoritative.

---

## 64. Announcement cache

Published announcement reads may be cached if load tests demonstrate the need.

Cache invalidation occurs on publish, update, activate, inactivate, and archive.

No stale unpublished announcement becomes visible to students.

---

## 65. API authorization - Notifications

Student.

```text
GET  own notifications
POST own notification read
POST own read-all
```

Staff, support, and admin access to another user's notifications is not defined by the frozen Communication API.

Broad notification-inspection APIs are not added without an explicit requirement.

---

## 66. API authorization - EmailTemplates

Email templates are administrative content.

Students cannot create, update, or delete templates.

Authorized operational and admin users require explicit permissions.

Suggested permission codes.

```text
COMMUNICATION_TEMPLATE_READ
COMMUNICATION_TEMPLATE_WRITE
COMMUNICATION_TEMPLATE_DELETE
```

The actual permission catalog uses the platform's existing permission model.

---

## 67. API authorization - Announcements

Suggested permissions.

```text
ANNOUNCEMENT_READ
ANNOUNCEMENT_WRITE
ANNOUNCEMENT_PUBLISH
ANNOUNCEMENT_DELETE
```

The actual permission catalog uses the platform's existing permission model.

---

## 68. Announcement student read

The existing API.

```http
GET /api/v1/announcements
```

is available for announcement views.

The server applies visibility, publication, date range, and targeting before returning student-visible rows.

---

## 69. Email template admin UI

Required functions.

```text
List
View
Create
Edit
Activate or deactivate
Archive
Preview
```

Raw server-side template execution is not exposed.

---

## 70. Email preview

A template preview feature is useful.

If implemented, it uses a preview endpoint, sample context, safe rendering, and no actual send.

The preview does not call the production provider.

A preview is a read-only application feature and does not add database tables.

---

## 71. Template test context

Preview sample.

```json
{
  "studentName": "Sample Student",
  "assessmentName": "SSC Mock Test 01",
  "organizationName": "Demo Organization"
}
```

Static and synthetic data only.

Real customer payment data is not injected into preview pages.

---

## 72. Email HTML security

Admin-authored HTML is sanitized using the platform's HTML allow-list sanitizer.

Disallowed content.

```text
script
iframe
javascript: URLs
event-handler attributes
arbitrary external executable content
```

Image URLs follow the approved email provider and content policy.

---

## 73. Announcement content security

Announcement description is sanitized according to the platform's content policy.

HTML is not assumed safe because it came from an admin.

---

## 74. Content length

Respect frozen column limits.

```text
notification.title         <= 255
email_template.code        <= 50
email_template.name        <= 150
email_template.subject     <= 255
announcement.title         <= 255
```

Body and description use TEXT with practical application limits to prevent abuse.

---

## 75. Input validation

Validate required fields, string lengths, date ranges, status transitions, template code uniqueness, announcement timing, and recipient resolution.

Backend validation is authoritative. Frontend Zod is supplementary.

---

## 76. Notification list pagination

Notification list is paginated.

Recommended default: 20 to 50 rows.

The exact default follows the common platform pagination policy.

---

## 77. Notification ordering

The natural ordering is `created_on DESC`.

An index strategy is validated against the query plan before adding additional indexes.

---

## 78. Notification unread count

Conceptual query.

```sql
SELECT COUNT(*)
FROM notification
WHERE user_id = :userId
  AND notification_status = 'UNREAD';
```

The existing indexes support the query.

If the UI requires extremely high-frequency unread counts, a measured cache is considered rather than prematurely introducing another table.

---

## 79. Mark-all-read query

One scoped update.

```sql
UPDATE notification
SET notification_status = 'READ'
WHERE user_id = :userId
  AND notification_status = 'UNREAD';
```

Transactional, user-scoped, and idempotent.

---

## 80. Notification creation transaction

Notification creation is part of the communication delivery workflow.

Example.

```text
PaymentSucceeded
   |
   v
Communication handler
   |
   v
Create notification
   |
   v
Enqueue email
```

If notification creation fails, the communication handler records a retryable failure without rolling back the already-committed payment transaction.

---

## 81. Communication jobs

Jobs support notification fan-out, email delivery, retry, announcement publication fan-out, template refresh, and communication reconciliation where provider supports it.

The platform's `background_job` infrastructure is reused.

---

## 82. Job isolation

Communication workload does not starve snapshot preparation, evaluation, analytics refresh, or commerce reconciliation.

Worker concurrency controls are used.

---

## 83. Queue backpressure

Monitor queued communication jobs, processing rate, failure rate, retry backlog, and oldest queued job age.

Alert if backlog growth and delivery latency exceed approved operational thresholds.

---

## 84. Communication SLA

The source does not freeze a specific notification or email delivery SLA.

Therefore a contractual promise such as email delivered in less than ten seconds is not invented.

Measure event-to-job latency, job-to-provider latency, and provider response latency. Establish operational targets after load testing.

---

## 85. Email provider configuration

Provider credentials come from environment configuration or secret store.

They are not stored in `email_template`, `notification`, or `announcement`.

The exact configuration mechanism follows the platform configuration.

---

## 86. Provider sandbox

Staging uses provider sandbox or test mode when available.

Production credentials do not exist in developer machines or CI logs.

---

## 87. Provider rate limits

Communication workers respect provider requests per second, daily limits, recipient limits, and payload limits.

The exact values are provider-specific.

The adapter exposes rate-limit signals to the retry system.

---

## 88. Provider error normalization

Map provider-specific responses to application categories.

```text
SUCCESS
RETRYABLE_FAILURE
PERMANENT_FAILURE
RATE_LIMITED
UNKNOWN
```

These are application-level adapter outcomes, not database statuses.

---

## 89. Email send result

Suggested.

```java
public record EmailSendResult(
    EmailSendOutcome outcome,
    String providerReference,
    String errorCode
) {}
```

Provider references are not persisted into the frozen communication tables.

They may be present in logs and metrics where safe.

---

## 90. Provider reference security

A provider reference is operational metadata.

It is not exposed to students unless required.

Provider authentication tokens are never logged.

---

## 91. Communication audit

Audit important actions.

```text
EmailTemplate create
EmailTemplate update
EmailTemplate activate or deactivate
EmailTemplate archive
Announcement create
Announcement update
Announcement publish
Announcement delete
Communication configuration changes
```

Sending each individual email does not require a full AuditLog row. Operational delivery uses metrics and logs unless a persisted delivery history is formally added.

---

## 92. Audit correlation

The `X-Request-Id` and common audit framework are used.

For announcement publish, the audit chain makes the event traceable to the resulting communication job.

---

## 93. Notification privacy

Notifications may contain assessment result availability, purchase information, or access information.

Do not include payment secrets, complete personal identifiers, other users' data, or internal provider payloads.

---

## 94. Email privacy

Emails follow minimum-necessary data principles.

Example: "Your result is available." is preferable to embedding sensitive answer-level information unless the approved template requires it.

---

## 95. Security - IDOR

Test notification ID substitution, announcement ID substitution, and email template ID substitution.

Students never mutate administrative communication records.

---

## 96. Security - Template injection

Test malicious template syntax, expression injection, HTML injection, script injection, and URL scheme injection.

---

## 97. Security - Announcement XSS

Test stored XSS in title, stored XSS in description, malicious links, and script and event attributes.

---

## 98. Security - Email abuse

Communication endpoints do not allow a low-privilege user to trigger arbitrary emails, unbounded recipient fan-out, template spam, or organization-wide notices.

---

## 99. Security - Announcement abuse

Only authorized users may publish and target large audiences.

The backend enforces permissions even when the frontend hides the controls.

---

## 100. Rate limiting

Potentially expensive endpoints.

```text
Announcement publish
Email preview
Template operations
```

may require rate limiting.

Notification read operations are not so aggressively limited that the notification center becomes unusable.

---

## 101. Communication API error handling

Use global categories.

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

Example: inactive template for publish or send returns INVALID_STATE or BUSINESS_ERROR according to the global API convention.

---

## 102. Email template conflict

Duplicate code returns 409 CONFLICT.

---

## 103. Announcement publish conflict

Publishing an already-published announcement is idempotent or returns a defined invalid-state response according to the state transition policy.

---

## 103.1 Notification read conflict

Reading an already-read notification remains harmless.

Normal UI retries are not turned into operational errors.

---

## 104. Frontend communication structure

Recommended.

```text
app/
  student/
    notifications/
    announcements/
  admin/
    communication/
      templates/
      announcements/
  shared/
    notification-center/
```

---

## 105. Notification center components

```text
NotificationBell
UnreadBadge
NotificationPopover
NotificationList
NotificationItem
NotificationFilters
MarkReadAction
MarkAllReadAction
```

TanStack Query is used for server state.

---

## 106. Notification center query strategy

Queries.

```text
notifications
notifications?status=UNREAD
```

An unread count is derived from the paginated notification response metadata where practical.

A separate API endpoint is not added solely for unread count unless measured need exists.

---

## 107. Optimistic read UX

When marking read.

```text
Optimistic UI
   |
   v
Backend mutation
   |
   v
Success  -> keep
Failure  -> rollback
```

The mutation is not assumed to have succeeded permanently.

---

## 108. Announcement student UI

Display title, description, and start and end visibility only for published and visible announcements.

Creator and edit controls are not exposed to students.

---

## 109. Announcement admin UI

Required.

```text
List
Create
Edit
Publish
Deactivate or archive
View audience if supported
```

Targeting UI does not expose unsupported targeting modes.

---

## 110. EmailTemplate admin UI

Required.

```text
Template list
Template search
Template detail
Create
Edit
Activate or deactivate
Archive
Preview
```

Preview uses synthetic data.

---

## 111. Template editing

Provide separate inputs for code, name, subject, body, and status.

Code becomes immutable after activation because the business contract requires stable integration keys.

Changing a code used by event mappings is a breaking configuration change requiring explicit review.

---

## 112. Template validation UI

Before save.

```text
Required fields
Length limits
Template syntax
HTML sanitization
Known variable references
```

The exact templating language follows the frozen contract.

---

## 113. Communication events from Commerce

P9 produces normalized events.

```text
PurchaseCreated
PaymentSucceeded
PaymentFailed
PaymentRefunded
AccessGranted
AccessRevoked
```

P10 defines handlers.

```text
onPaymentSucceeded
onPaymentFailed
onPaymentRefunded
onAccessGranted
onAccessRevoked
```

Each handler is independently testable.

---

## 114. Communication events from Evaluation

Recommended.

```text
ResultPublished
```

Handler.

```text
ResultPublished
   |
   v
Notification
   |
   v
Result email
```

Result calculation remains Evaluation-owned.

---

## 115. Communication events from Assessment

Potential events.

```text
AssessmentScheduled
AssessmentStarting
```

The exact event catalog is documented. A schedule-specific notification table is not created.

---

## 116. Event payload design

Event payloads contain stable identifiers: userId, assessmentId where needed, purchaseId where needed, resultId where needed, and accessPackageId where needed.

Large nested JPA entities are not embedded.

---

## 117. Event versioning

Because communication consumers can outlive producer internals, application events are versioned as contracts when they cross a durable boundary.

Example: `PaymentSucceededV1`.

For purely in-process events, the common application event conventions apply.

---

## 118. Communication handler isolation

Each event handler validates event, resolves recipient, resolves template, builds model, creates notification if configured, enqueues email if configured, and records outcome.

A failure for one event does not stop unrelated events.

---

## 119. Batch event handling

A batch of ResultPublished events is processed independently.

One malformed record does not fail the entire batch.

Per-event failure handling and bounded retry apply.

---

## 120. Business event delivery reliability

Reliable event publication uses the existing `background_job` infrastructure.

Business transactions that must reliably trigger communication insert a `background_job` row within the same transaction. Workers process the job and record outcome.

This ensures that a committed business transaction reliably produces the communication side effect even if the application restarts.

---

## 121. Event loss risk

Without a durable outbox table, a possible window exists between business transaction commit and application crash.

The `background_job` row is inserted in the same transaction as the business mutation, which closes the window without introducing a new outbox table.

---

## 122. Notification persistence scope

The `notification` table stores user, title, message, read state, and created time.

It does not answer which business event created the notification, whether email was also sent, which template was used, how many retries occurred, or which provider accepted it.

These are not frozen capabilities.

The implementation does not imply that the current schema supports them.

---

## 123. Communication traceability

Operational traceability uses requestId, event logs, metrics, and job metadata without adding transactional communication tables.

If durable event to notification to email lineage becomes a formal requirement, that amendment is handled separately.

---

## 124. Email delivery status for operations

Operational status is represented through job state, metrics, structured logs, and provider response.

It is not claimed as historical database truth.

---

## 125. Communication reconciliation

If the selected email provider supports status lookup, a reconciliation job may compare local job state with provider status.

This is optional and provider-specific.

No communication reconciliation table is required by the frozen schema.

---

## 126. Email retry exhaustion

When retries are exhausted, mark the job permanently failed and alert operations.

Do not retry indefinitely.

---

## 127. Notification delivery failure

In-app notification insertion is a local database operation.

If it fails, the transaction fails and the retry job attempts again.

The job is idempotent enough to avoid harmful duplicate notifications if a previous insert succeeded but the caller lost the response.

---

## 128. Notification idempotency options

The common job idempotency store is used.

Durable exactly-once notification delivery is documented per event type. Where duplicate risk exists, it is tolerated operationally or prevented by job-level idempotency.

Duplicate delivery is not silently accepted for user-facing messages if duplicate delivery would materially harm UX.

---

## 129. Announcement publish idempotency

Repeated `POST /announcements/{id}/publish` does not generate uncontrolled duplicate notification fan-out.

The publish state transition and associated job creation are idempotent.

---

## 130. Announcement publish transaction

Preferred.

```text
BEGIN
  Lock announcement
  Validate unpublished state
  Update publication state
  Insert background_job for fan-out
COMMIT
```

Fan-out to thousands of users is not executed inside the same transaction.

---

## 131. Announcement unpublish

The frozen API does not define an explicit unpublish action.

It is not added without API amendment.

Only approved lifecycle operations are used.

---

## 132. Communication preferences

No frozen table exists for email opt-in, notification preference, channel preference, or marketing consent.

A full preferences center is not built in P10.

Transactional communications follow organizational and business rules already frozen elsewhere.

---

## 133. Communication localization

The source says email templates are reusable and configurable but does not freeze a multi-language template model.

Tables such as `email_template_translation`, `language_id`, or `locale` are not added without amendment.

A future localization phase adds versioned support.

---

## 134. Organization scope

The platform is single-organization.

Communication is not redesigned into multi-tenant notification infrastructure.

Organization boundaries remain visible in service contracts when available through authenticated user context.

---

## 135. Background job observability

Minimum metrics.

```text
communication.jobs.queued
communication.jobs.started
communication.jobs.succeeded
communication.jobs.failed
communication.jobs.retried
communication.jobs.exhausted
```

Email metrics.

```text
communication.email.sent
communication.email.failed
communication.email.rate_limited
communication.email.timeout
```

Notification metrics.

```text
communication.notification.created
communication.notification.read
communication.notification.fanout
```

Announcement metrics.

```text
communication.announcement.published
communication.announcement.fanout
```

---

## 136. Latency metrics

Measure event-to-job latency, job-to-provider latency, template render latency, notification insert latency, fanout batch latency, and announcement publish latency.

Provider latency is not treated as local database and API latency.

---

## 137. Queue metrics

Monitor queue depth, oldest item age, processing throughput, retry backlog, worker concurrency, and provider throttle responses.

Alert thresholds are environment-specific.

---

## 138. Logging

Structured communication logs contain requestId, eventType, userId when safe, notificationId, announcementId, templateCode, channel, jobId if available, outcome, retryCount if the job framework exposes it, latency, and errorCode.

Do not log password reset tokens, email provider secrets, provider authentication headers, or raw sensitive personal data.

---

## 139. PII protection

Email addresses and mobile numbers are personal data.

Where logs require recipient information, prefer masked, hashed, or omitted values according to the existing security and logging policy.

---

## 140. Email address validation

Validate recipient email before sending.

A malformed address is a permanent failure, not an infinite retry.

---

## 141. Bounce handling

The source does not define a bounce-management domain.

Bounce tables, suppression lists, and complaint domains are not introduced in P10.

Provider bounce information remains provider-specific operational data until a future requirement exists.

---

## 142. Email complaint handling

Not in frozen scope.

Unsubscribe and complaint workflows are not implemented as hidden features.

---

## 143. Bulk email safety

A normal business event does not accidentally send organization-wide emails.

Audience resolution is explicit: one user, specific user ids, or approved announcement audience.

No generic send-to-everyone API.

---

## 144. Student safety from over-notification

The source does not freeze notification throttling or quiet hours.

User preference tables are not invented.

Operationally, duplicate event triggers are avoided.

---

## 145. Event deduplication

Deduplicate at the event and job boundary where possible.

Example.

```text
PaymentSucceeded received twice
   |
   v
One logical communication job
```

This depends on the common idempotency mechanism.

---

## 146. Communication test pyramid

Required.

```text
Unit
Integration
API
Security
Event-handler
Provider-adapter
Retry
Concurrency
Frontend
End-to-end
```

---

## 147. Unit tests - Notification

Minimum.

```text
Create notification
List user's notifications
Read notification
Read already-read notification
Read-all
User isolation
Pagination
Ordering
```

---

## 148. Unit tests - EmailTemplate

Minimum.

```text
Create
Duplicate code
Update
Activate and deactivate
Archive
Template syntax
Sanitization
Length validation
```

---

## 149. Unit tests - Announcement

Minimum.

```text
Create
Update
Publish
Invalid dates
Visibility window
Inactive publication
Permission checks
```

---

## 150. Unit tests - EmailGateway

Minimum.

```text
Success
Temporary failure
Permanent failure
Timeout
Rate limited
Provider mapping
Redaction
```

---

## 151. Unit tests - Retry

Minimum.

```text
Retryable failure retries
Non-retryable failure stops
Max attempts respected
Backoff applied
Jitter bounded
Duplicate retry protected
```

---

## 152. Integration test - Result published

```text
ResultPublished
     |
     v
Communication handler
     |
     v
Notification
     |
     v
Email job
     |
     v
Fake provider
```

Assert notification exists, email adapter invoked, and business result unchanged.

---

## 153. Integration test - Payment success

```text
PaymentSucceeded
     |
     v
Notification
     |
     v
Purchase confirmation email
```

Assert AccessGrant unchanged by Communication, Purchase unchanged by Communication, notification created, and provider adapter invoked.

---

## 154. Integration test - Payment failure

```text
PaymentFailed
     |
     v
Notification
     +
Failure email
```

Assert no business-state mutation by Communication.

---

## 155. Integration test - Announcement publish

```text
Announcement draft
     |
     v
Publish
     |
     v
Event and job
     |
     v
Target audience
     |
     v
Notification fan-out
```

The exact audience resolution follows the frozen targeting model.

---

## 156. Integration test - Duplicate publish

Call publish twice concurrently.

Expected one effective publication and no uncontrolled duplicate fan-out.

---

## 157. Integration test - Duplicate event

Send the same PaymentSucceeded event twice.

The approved idempotency contract applies. No duplicate business transaction is created.

---

## 158. Security test - Notification IDOR

User A attempts to mark User B's notification as read.

Expected 403 or 404 according to global object-authorization convention.

---

## 159. Security test - Template access

Student attempts GET, POST, PUT, or DELETE on `/email-templates`.

Expected 403.

---

## 160. Security test - Announcement publish

User without publish permission attempts `POST /announcements/{id}/publish`.

Expected 403.

---

## 161. Security test - Stored XSS

Test malicious announcement title, announcement description, email template subject, email template body, notification title, and notification message.

Rendering remains safe.

---

## 162. Concurrency test - Read

Run concurrent mark-read requests.

Expected READ with no corruption.

---

## 163. Concurrency test - Mark all read

Run concurrent read-all and read-individual requests.

Expected consistent READ state.

---

## 164. Concurrency test - Announcement publish

Run 100 concurrent publish requests.

Expected one effective publish transition and no uncontrolled audience fan-out duplication.

---

## 165. Concurrency test - Event handling

Run repeated identical events.

Expected idempotent or contract-approved deduplicated communication.

---

## 166. Frontend tests - Notification center

Minimum.

```text
List
Unread count behavior
Open item
Mark read
Mark all read
Pagination
Empty state
API failure
Retry
```

---

## 167. Frontend tests - Announcements

Minimum.

```text
List
Visibility
Detail
Publish state display
Empty state
API failure
```

---

## 168. Frontend tests - Template admin

Minimum.

```text
List
Create
Edit
Preview
Activate or deactivate
Validation
Permission denial
```

---

## 169. End-to-end test

Scenario one.

```text
Admin publishes announcement
        |
        v
Communication job
        |
        v
Student notification center
        |
        v
Student sees announcement
```

Scenario two.

```text
Payment success
        |
        v
Communication event
        |
        v
Notification
        |
        v
Email test gateway
```

Scenario three.

```text
Result published
        |
        v
Notification
        |
        v
Email
```

---

## 170. Failure injection

Simulate email provider timeout, email provider 5xx, email rate limit, Redis outage, worker restart, database restart, job retry, duplicate event, duplicate publish, template missing, and template invalid.

The system preserves business correctness.

---

## 171. Redis failure acceptance

Expected.

```text
Notifications still readable
Announcements still readable
Templates load from database
Business events still process
```

provided the underlying PostgreSQL and job infrastructure is healthy.

---

## 172. Worker restart acceptance

If a worker crashes, the in-flight job is recovered or safely retried according to the `background_job` framework.

Process memory is not assumed to be durable.

---

## 173. Database restart acceptance

On database restart, communication API recovers, jobs retry according to the framework, and no business-state corruption occurs.

---

## 174. Provider outage acceptance

If the provider is unavailable, business transaction succeeds, communication enters retryable failure, and operational alert is raised.

The student is not told that the purchase or result itself failed just because the email service failed.

---

## 175. Template missing acceptance

If a required template is missing, the business action succeeds, the communication job fails, and an alert is raised.

This is an important cross-domain decoupling test.

---

## 176. Announcement fan-out capacity test

Use a large synthetic audience.

Measure publication latency, job enqueue rate, notification batch throughput, database CPU, database connections, worker CPU, and queue backlog.

No exam delivery workload is starved.

---

## 177. Email load test

Use a fake provider for CI and staging.

Scenarios.

```text
1,000 emails
10,000 emails
Burst
Steady rate
Provider throttling
Provider failures
```

Measure throughput, latency, retry rate, worker utilization, and queue growth.

---

## 178. API performance

Applicable read APIs.

```text
GET /notifications
GET /announcements
GET /email-templates
```

follow the platform performance objective where applicable: p95 under 100 ms.

Provider delivery time is measured separately.

---

## 179. Database performance

Monitor notification list query, unread count, mark read, mark all read, announcement list, and template lookup.

Query plans are reviewed before adding indexes.

---

## 180. Index expectations

Frozen indexes.

```text
idx_notification_user_id
idx_notification_notification_status

uk_email_template_code

idx_announcement_created_by
idx_announcement_start_date
idx_announcement_end_date
idx_announcement_status
```

Frozen indexes are not replaced or broadened without evidence.

---

## 181. Notification retention

The source does not define notification retention.

Do not automatically delete old notifications.

A retention and cleanup policy requires explicit business approval.

---

## 182. Announcement retention

Historical announcements may be archived.

Published announcement history is not hard-deleted without a business rule.

---

## 183. Template history

The schema does not version EmailTemplate.

Updating a template changes the current stored content.

If historical exact-template reproducibility becomes a requirement, add versioning through formal amendment.

---

## 184. Communication reproducibility

The current frozen model cannot fully prove which template body was used for an old email.

Historical template reproducibility is not claimed without a versioned or persisted delivery model.

---

## 185. Source-of-truth summary

```text
User identity       -> Identity
Business truth      -> Originating domain
Notification read   -> notification
Email template      -> email_template
Announcement        -> announcement
Delivery execution  -> background_job and provider boundary
Financial access    -> Commerce and AccessGrant
```

---

## 186. No cross-domain repository access

Forbidden.

```text
CommerceService
   |
   v
NotificationRepository
```

Preferred.

```text
CommerceService
   |
   v
Application event
   |
   v
Communication
```

Likewise `EvaluationRepository` and `EmailTemplateRepository` are not combined.

---

## 187. No communication business logic leakage

Forbidden.

```text
CommunicationService
   |
   v
UPDATE payment_record
```

Communication does not repair or modify Commerce, Evaluation, or Assessment state.

---

## 188. No email in controllers

Forbidden.

```java
@PostMapping("/publish")
void publish() {
    repository.save(...);
    emailService.send(...);
}
```

Preferred.

```text
Controller
  |
  v
Application service
  |
  v
Business state change
  |
  v
Event or job
  |
  v
Communication worker
```

---

## 189. No synchronous fan-out

Forbidden.

```text
Publish
  |
  v
for every student:
    insert notification
    send email
```

Preferred.

```text
Publish
  |
  v
Commit
  |
  v
Background job
  |
  v
Batched fan-out
```

---

## 190. No provider SDK in domain

Forbidden.

```text
Notification domain
   |
   v
SendGridClient, SESClient, or SMTP client
```

Preferred.

```text
EmailGateway
   |
   v
Provider adapter
```

The provider remains replaceable.

---

## 191. Email channel versus in-app channel

The channels are conceptually separate.

```text
IN_APP
EMAIL
```

The frozen database only explicitly models in-app notification state and reusable email templates.

A `channel` column is not added to Notification unless formally required.

---

## 192. Communication configuration

Possible configurable rules include which events produce email and which events produce in-app notification.

The source does not define a dedicated communication preference entity.

Therefore existing `BusinessRule` or `OrganizationSetting` are used only where their contracts support this safely.

A CommunicationConfig table is not added.

---

## 193. Notification trigger configuration

If the organization wants an admin screen such as disabling result emails, the exact persistence contract is not frozen.

This requires an Administration and BusinessRule contract decision.

P10 does not create a hidden settings table.

---

## 194. Communication permission model

Permissions are granular.

```text
communication.notification.read
communication.template.read
communication.template.write
communication.template.delete
communication.announcement.read
communication.announcement.write
communication.announcement.publish
communication.announcement.delete
```

These are candidate permission codes. They use the Identity permission conventions and do not hard-code role names.

---

## 195. Role boundaries

Students read own notifications and visible announcements.

Staff receive communication operations if granted.

Support receive limited communication visibility and actions if granted.

Finance do not automatically receive communication administration.

Admin receive communication administration subject to permission.

---

## 196. Operational support

Support users may need to investigate notification presence, announcement visibility, and communication failure.

The frozen API does not define an operational delivery-history API.

It is not added without the persistence contract.

---

## 197. Reporting

The reports framework supports categories such as Student, Assessment, Content, Commerce, Operations, and Executive.

Communication reporting is an Operations report.

P10 does not create a Communication reporting schema.

---

## 198. Communication dashboard

Recommended operations dashboard.

```text
Notification throughput
Email throughput
Success and failure
Retry backlog
Provider latency
Oldest queued job
Template errors
Announcement fan-out
```

This is read from metrics and job infrastructure.

---

## 199. Alerts

Recommended alerts.

```text
Email failure spike
Provider unavailable
Retry backlog growth
Queue age threshold
Template rendering errors
Announcement fan-out failure
Notification insert failures
Communication worker unavailable
```

---

## 200. Runbook - Email failures

```text
Check provider status
   |
   v
Check adapter metrics
   |
   v
Check retry backlog
   |
   v
Check template rendering
   |
   v
Check provider rate limit
   |
   v
Re-run or retry through job infrastructure
   |
   v
Verify business state is unchanged
```

Business state is never repaired by altering communication data.

---

## 201. Runbook - Missing template

```text
Identify event
   |
   v
Identify expected template code
   |
   v
Check template status
   |
   v
Restore or activate valid template
   |
   v
Retry communication job
```

The originating payment, result, or assessment operation is not re-run.

---

## 202. Runbook - Duplicate email risk

When a provider timeout leaves ambiguous outcome.

```text
Check provider query or idempotency capability
   |
   v
Determine whether delivery was accepted
   |
   v
Follow the provider ambiguity policy
```

Blindly retrying indefinitely is prohibited.

---

## 203. Runbook - Announcement publish

```text
Check announcement state
   |
   v
Check publish audit
   |
   v
Check communication job
   |
   v
Check audience resolution
   |
   v
Check notification creation throughput
```

---

## 204. Deployment strategy

Deploy P10 in stages.

```text
1.  Communication backend contracts
2.  Database readiness
3.  Notification APIs
4.  Announcement APIs
5.  Template APIs
6.  Event handlers
7.  Background workers
8.  Email provider adapter
9.  Frontend
10. Observability
11. Staging validation
12. Controlled production enablement
```

---

## 205. Feature flags

Logical flags.

```text
communication.notifications.enabled
communication.announcements.enabled
communication.email.enabled
communication.result-email.enabled
communication.commerce-email.enabled
```

These are configuration controls, not new database entities.

---

## 206. Safe rollout

During staged rollout, notifications can be enabled first, email can remain disabled, a test provider can be enabled, and production email can be gradually enabled.

A student action that calls a disabled backend feature is not exposed.

---

## 207. Rollback

If the email service fails after release, disable email, keep in-app notifications, and keep business modules operational.

Business transactions are not rolled back merely because Communication is unhealthy.

---

## 208. Schema rollback

P10 requires no schema migration beyond the frozen baseline.

If a future communication persistence amendment is approved, it uses expand and contract migrations.

Production schema is never manually edited.

---

## 209. Database migration policy

The notification, email_template, and announcement tables are existing tables. No new required tables are authorized by this document.

Any proposed new table includes business reason, migration, indexes, retention, security, API impact, and rollback, and receives formal approval.

---

## 210. Repository layer

Communication repositories.

```java
NotificationRepository
EmailTemplateRepository
AnnouncementRepository
```

Only Communication directly uses them.

---

## 211. Service layer

Suggested services.

```text
NotificationService
EmailTemplateService
AnnouncementService
CommunicationOrchestrator
EmailDeliveryService
TemplateRenderService
AudienceResolutionService
CommunicationJobService
```

The exact decomposition may vary while preserving responsibilities.

---

## 212. Application commands

Suggested.

```text
CreateNotificationCommand
MarkNotificationReadCommand
CreateEmailTemplateCommand
UpdateEmailTemplateCommand
CreateAnnouncementCommand
UpdateAnnouncementCommand
PublishAnnouncementCommand
SendCommunicationCommand
```

Entities are not exposed directly.

---

## 213. Event handlers

Suggested.

```text
PurchaseSucceededHandler
PaymentFailedHandler
PaymentRefundedHandler
AccessGrantedHandler
AccessRevokedHandler
ResultPublishedHandler
AssessmentScheduledHandler
```

Only events that exist in the upstream module contracts are implemented.

---

## 214. Event handler test isolation

Every handler is testable with synthetic event, fake repositories, and fake EmailGateway.

No external provider is required.

---

## 215. Fake email provider

Test implementation.

```java
class FakeEmailGateway implements EmailGateway
```

Outcomes.

```text
SUCCESS
RETRYABLE_FAILURE
PERMANENT_FAILURE
TIMEOUT
RATE_LIMITED
```

The fake provider captures recipient, subject, body, and template code for assertions.

---

## 216. Contract tests - EmailGateway

Provider adapter satisfies success normalization, failure normalization, timeout behavior, rate limit behavior, redaction, and idempotency behavior where supported.

---

## 217. API contract tests

Verify frozen paths.

```http
GET /api/v1/notifications
POST /api/v1/notifications/{id}/read
POST /api/v1/notifications/read-all

GET, POST, PUT, DELETE /api/v1/email-templates

GET, POST, PUT, DELETE /api/v1/announcements
POST /api/v1/announcements/{id}/publish
```

Path renaming does not occur.

---

## 218. HTTP status expectations

General.

```text
200
201
202
204
400
401
403
404
409
422
429
500
```

Follow the shared API contract.

Announcement publish may use 202 Accepted only if the actual business action is intentionally asynchronous under the established API semantics.

---

## 219. OpenAPI requirements

Document authentication, authorization, request schema, response schema, pagination, error codes, and action endpoints.

Internal provider adapter endpoints are not exposed as public API.

---

## 220. Frontend permission helper

Use the established permission helper.

```ts
can("communication.template.write")
can("communication.announcement.publish")
```

Frontend visibility is not authorization.

---

## 221. TanStack Query keys

Suggested.

```text
["notifications", filters]
["announcements", filters]
["email-templates", filters]
```

Mutation invalidation.

```text
Mark read  -> notifications, unread metadata
Publish announcement -> announcements
```

---

## 222. UI failure states

Notification center.

```text
Loading
Empty
Error
Retry
```

Announcement.

```text
Loading
Empty
Error
```

Template admin.

```text
Loading
Validation error
Save error
Permission error
```

---

## 223. Accessibility

Communication UIs provide keyboard navigation, focus management, screen-reader labels, read and unread semantics, and accessible dialogs.

The source does not define a specific accessibility standard number. The frontend platform baseline applies.

---

## 224. Mobile and responsive behavior

Notification center and announcement views remain usable on desktop, tablet, and mobile browser.

No native mobile-only Communication architecture is required.

---

## 225. No native push dependency

Push notifications are not part of frozen Communication scope.

Device token, push subscription, FCM topic, and APNs token tables or entities are not added.

---

## 226. No SMS dependency

SMS is not part of frozen Communication scope.

SMS provider abstraction is not introduced until a requirement exists.

---

## 227. No marketing email

P10 handles transactional and informational email.

Communication is not a marketing automation platform.

---

## 228. Transactional email categories

Frozen examples support transactional categories.

```text
Welcome
Password Reset
Purchase Confirmation
Assessment Notification
Result Published
```

Only approved event-to-template mappings are used.

---

## 229. Password reset integration

Identity remains responsible for reset token, expiration, verification, and password mutation.

Communication may send a password reset email using a secure, limited context.

Reset tokens are not stored in `email_template` or `notification`.

---

## 230. Secure reset email

If the password-reset flow contains a link, the link is generated by Identity.

Communication only renders the already-authorized link parameter.

The link and token are never logged.

---

## 231. Purchase confirmation

P9 Commerce event PaymentSucceeded may trigger a purchase confirmation email.

The email derives amounts from authoritative Purchase state, not client-submitted values.

---

## 232. Assessment notification

Assessment-related emails derive assessment, schedule, and timing from authoritative Assessment and Schedule data.

Communication does not calculate the schedule.

---

## 233. Result published notification

The message exposes only approved result availability information.

Answer key, internal evaluation trace, and other students' ranks are not leaked unless explicitly authorized by the result visibility contract.

---

## 234. Announcement publication notification

Content comes from the announcement before fan-out.

If the announcement is changed after scheduling but before fan-out, the approved job snapshot or reference model applies.

---

## 235. Communication job payload

Where the common job infrastructure supports payloads, prefer stable ids: event type, user id, announcement id, template code.

Large HTML bodies are not copied into job payloads.

---

## 236. Job security

Job payloads may contain personal information.

Therefore job storage is encrypted where applicable, sensitive data is minimized, and transient payloads expire when possible.

The exact job-storage security comes from the common platform job framework.

---

## 237. Communication job authorization

Background workers are trusted system actors.

They still validate record exists, status valid, template active, and recipient valid.

Arbitrary payload data is not blindly executed.

---

## 238. Communication retry dead-letter

A dead-letter concept may exist in the common job platform.

A communication_dead_letter table is not created unless the shared infrastructure lacks one and a formal change is approved.

---

## 239. Communication worker scaling

Workers may scale horizontally.

They ensure no harmful duplicate processing, idempotent handlers, and provider rate-limit awareness.

---

## 240. Worker concurrency

An enormous email worker pool is not hard-coded.

Tuning uses provider limits, CPU, network, database throughput, and queue depth.

---

## 241. Email rendering performance

Template rendering is measured separately from provider send latency.

Compiled or static template representation is cached only if the chosen rendering engine supports it safely.

---

## 242. Template cache invalidation race

On update.

```text
Database commit
   |
   v
Invalidate cache
```

If using eventual cache invalidation, an inactive template does not continue being used beyond the approved policy.

---

## 243. Notification database contention

High fan-out can cause large INSERT volume, index updates, and connection pressure.

Batching and bounded workers are used.

Announcement audience fan-out is not processed on the HTTP request thread.

---

## 244. Communication load interaction with exam

Communication does not degrade Start Exam, response save, submission, recovery, or evaluation.

The worker pool and database capacity are tested alongside representative non-communication workload.

---

## 245. Production capacity test

At minimum large notification fan-out, email retry burst, and exam autosave baseline.

Measure cross-system contention.

---

## 246. Backup and restore

The three communication tables are ordinary PostgreSQL data and participate in normal backup, restore, and migration.

No special separate backup model is needed unless a future delivery-history table is added.

---

## 247. Data recovery

After restore, notifications, templates, and announcements remain consistent.

Background communication jobs recover according to job infrastructure behavior.

---

## 248. Historical integrity

Published announcements and email templates may participate in historical operational context.

Communication records referenced by application workflows are not cascade-deleted arbitrarily.

---

## 249. Deletion policy

### 249.1 Notification

No DELETE API is defined. User-delete behavior is not added without requirement.

### 249.2 EmailTemplate

Delete endpoint is frozen.

Historical dependencies are reviewed.

Prefer ARCHIVED where deletion would damage auditability or configuration lineage.

### 249.3 Announcement

Delete endpoint is frozen.

Published historical records are treated carefully.

---

## 250. EmailTemplate delete safety

If an active event mapping depends on a template code, deleting the template may break transactional communications.

The service prevents unsafe deletion or requires deactivation and archive first.

---

## 251. Template deletion policy

Approved policy.

```text
Hard delete is allowed only for templates that are
INACTIVE or ARCHIVED and that are not referenced by any active
event-to-template mapping.

Active templates must be deactivated first.
```

Code reuse after deletion is prohibited because historical communications may reference the code.

---

## 252. Announcement delete safety

If published or currently visible, delete may alter student-visible communication.

The service applies the approved lifecycle rule.

A published announcement is archived rather than physically deleted.

---

## 253. Announcement deletion policy

Approved policy.

```text
DRAFT announcements may be hard-deleted.
PUBLISHED announcements are archived.
Students see archived announcements only through
historical audit paths, not through normal views.
```

---

## 254. Notification read audit

Marking an individual notification read is ordinary user activity.

The frozen schema has no `read_on` or `read_by`.

Per-read audit records are not added.

---

## 255. Notification security boundary

Notification message content may be sensitive even though it is stored as ordinary text.

Apply the same access controls as any user-specific data.

---

## 256. Email address ownership

The recipient comes from authoritative Identity data.

A browser-provided arbitrary recipient address is not trusted for transactional emails.

---

## 257. Announcement audience resolution

Audience resolution uses authoritative user data, roles and permissions, and approved business rules.

Client-submitted user id lists are not trusted for privileged announcements.

---

## 258. Admin bulk selection

If an admin UI supports selecting recipients for an announcement, the server validates recipient belongs to organization, recipient is eligible, and admin has permission.

The frozen schema has no dedicated audience persistence.

---

## 259. Communication service boundary

External contract.

```java
public interface CommunicationPort {

    void handle(BusinessEvent event);
}
```

Internal implementations may use NotificationService, EmailDeliveryService, AnnouncementService.

---

## 260. Module package layout

```text
com.company.exam.communication
  api
  application
  domain
  repository
  event
  email
  notification
  announcement
  template
  job
  security
  audit
```

---

## 261. Frontend package layout

```text
src/
  features/
    notifications/
    announcements/
    communication-admin/
  components/
    communication/
  lib/
    communication-api/
```

---

## 262. Coding rules

Follow existing project standards.

```text
DTO-based APIs
Service layer
Validation
Global exception handling
OpenAPI
Structured logging
Tests
No direct repository cross-domain access
```

---

## 263. P10 implementation task breakdown

```text
P10-01  Communication module skeleton
P10-02  Notification entity, repository, service
P10-03  Notification list API
P10-04  Mark notification read
P10-05  Mark all read
P10-06  Notification center frontend
P10-07  EmailTemplate entity, repository, service
P10-08  EmailTemplate CRUD APIs
P10-09  EmailTemplate admin UI
P10-10  Template rendering contract
P10-11  EmailGateway abstraction
P10-12  Fake email provider
P10-13  Background email delivery
P10-14  Retry policy
P10-15  Announcement entity, repository, service
P10-16  Announcement CRUD APIs
P10-17  Announcement publish action
P10-18  Announcement UI
P10-19  Event-to-Communication handlers
P10-20  Commerce integrations
P10-21  Result and Assessment integrations
P10-22  Metrics, logging, alerts
P10-23  Security tests
P10-24  Concurrency and idempotency tests
P10-25  Fan-out and load tests
P10-26  Staging rollout
P10-27  Production enablement
```

Execute one task at a time with checkpoints.

---

## 264. P10-01 to P10-06 acceptance

```text
[ ] Module created
[ ] Notification entity matches frozen schema
[ ] Notification list API
[ ] Notification read API
[ ] Notification read-all API
[ ] Notification center frontend
[ ] RBAC enforced
[ ] Tests pass
```

---

## 265. P10-07 to P10-14 acceptance

```text
[ ] EmailTemplate entity matches frozen schema
[ ] CRUD APIs
[ ] Admin UI
[ ] Template rendering contract
[ ] HTML sanitization
[ ] EmailGateway abstraction
[ ] Fake provider
[ ] Background email delivery
[ ] Retry policy applied
```

---

## 266. P10-15 to P10-18 acceptance

```text
[ ] Announcement entity matches frozen schema
[ ] CRUD APIs
[ ] Publish action
[ ] Announcement UI
[ ] Fan-out via background job
[ ] Duplicate publish prevented
[ ] Audience resolution via approved model
```

---

## 267. P10-19 to P10-27 acceptance

```text
[ ] Event handlers for Commerce
[ ] Event handlers for Evaluation
[ ] Event handlers for Assessment where approved
[ ] Metrics, logging, alerts
[ ] Security tests
[ ] Concurrency and idempotency tests
[ ] Fan-out and load tests
[ ] Staging rollout complete
[ ] Production enablement
[ ] CI green
```

---

## 268. Recommended implementation prompt

```text
You are implementing P10 Communication and Notification for the Smart
Competitive Examination Platform.

Read the full specification package before modifying the repository.

P10 owns:
Notification
EmailTemplate
Announcement
Delivery orchestration
Retry

Frozen Communication tables:
notification
email_template
announcement

Frozen APIs:
GET /api/v1/notifications
POST /api/v1/notifications/{id}/read
POST /api/v1/notifications/read-all

GET, POST, PUT, DELETE /api/v1/email-templates

GET, POST, PUT, DELETE /api/v1/announcements
POST /api/v1/announcements/{id}/publish

NON-NEGOTIABLE:
1.  Business modules do not send email directly.
2.  Communication reacts to business events.
3.  Long-running and external delivery is asynchronous.
4.  Notification status is only UNREAD or READ in the frozen
    database.
5.  Access, payment, assessment, evaluation, and identity state remain
    owned by their originating modules.
6.  Email provider is hidden behind EmailGateway.
7.  Do not add communication delivery tables without formal amendment.
8.  Do not add notification event ids without formal amendment.
9.  Do not create a communication preference table.
10. Do not add SMS, push, or marketing entities.
11. Students only access their own notifications.
12. Template and announcement administration requires backend
    permission checks.
13. Announcement publishing does not synchronously fan out to
    thousands of users.
14. Retry is bounded and idempotent.
15. Business transactions succeed independently from email delivery.
16. Provider secrets are never logged.
17. Password reset tokens remain Identity-owned.
18. Existing AccessGrant, Purchase, and Result truth is not modified by
    Communication.
19. Redis is cache only.
20. Existing background_job infrastructure is reused.
21. Do not invent APIs outside the frozen contract.
22. Do not invent tables outside the frozen schema.
23. Stop and report rather than silently resolving frozen-contract
    gaps.

Task order:
P10-01 through P10-27.

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
- Security tests
- Concurrency tests
- Fan-out and load tests
- Migration verification
- OpenAPI verification
- Audit verification
- Retry policy verification
- Idempotency verification
- CI green
```

---

## 269. Review questions

```text
Does the implementation match the frozen communication schema?
Does the business transaction remain independent from email delivery?
Are notification records user-scoped?
Are templates sanitized?
Is the provider abstracted?
Is fan-out asynchronous?
Is retry bounded and idempotent?
Is the system resilient to provider outage?
Is Redis used only as cache?
Are correct-answer and payment secrets protected?
Does announcement publish avoid synchronous mass fan-out?
Is the job created in the same transaction as the publish?
Is the frozen schema unchanged?
```

---

## 270. Final engineering principle

> Communication consumes business events, delivers informational messages through an isolated provider boundary, and never becomes the source of truth for business state.

The flow.

```text
Business module
      |
      v
Committed business transaction
      |
      v
background_job insert
      |
      v
Communication worker
      |
      v
Template render
      |
      v
Notification insert and EmailGateway
      |
      v
Job state and metrics
```

---

## 271. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P10 — Communication and Notification.

**Next implementation document:** P11 — Full Administration.