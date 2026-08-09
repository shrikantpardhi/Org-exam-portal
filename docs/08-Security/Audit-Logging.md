# Audit Logging

**Document Version:** 1.0  
**Status:** Approved  
**Parent Document:** Security Architecture

---

# 1. Purpose

This document defines the audit logging architecture and requirements for the Configurable Examination Platform.

Audit logging provides a reliable historical record of significant security, administrative, academic, examination, evaluation, and configuration activities.

The primary objectives are:

- Accountability
- Traceability
- Examination integrity
- Security investigation
- Operational support
- Compliance support
- Change history
- Incident investigation

Audit logs are distinct from application logs.

---

# 2. Audit Logging vs Application Logging

The platform must maintain a clear distinction between application logs and audit logs.

## Application Logs

Application logs are intended for:

- Debugging
- Error investigation
- Performance analysis
- Operational monitoring

Examples:

```text
Database connection timeout
HTTP request duration
Unexpected application exception
Background job failure
```

Application logs may have retention and rotation policies.

---

## Audit Logs

Audit logs record significant actions that affect:

- Security
- Users
- Permissions
- Examination content
- Assessment configuration
- Examination delivery
- Evaluation
- Results
- Organization configuration

Audit records must be treated as business/security evidence and must not be casually deleted or modified.

---

# 3. Audit Principles

The audit system follows these principles:

## Immutability

Once an audit event is recorded, application users must not modify it.

---

## Attribution

Every user-generated event should identify the actor where an authenticated identity exists.

---

## Traceability

Events should be traceable across related operations using correlation identifiers.

---

## Completeness

High-risk business operations must produce audit events consistently.

---

## Minimum Necessary Data

Audit records must contain enough information to explain an action without storing unnecessary sensitive information.

---

## Fail-Safe Behavior

The platform must define which operations are allowed to continue when audit persistence is temporarily unavailable.

Security-critical operations should not silently proceed without required audit evidence.

---

# 4. Audit Event Lifecycle

```text
Business Operation
        |
        v
Authorization
        |
        v
Business Action
        |
        v
Audit Event Construction
        |
        v
Audit Persistence
        |
        v
Immutable Audit Record
```

For operations where audit evidence is mandatory, the audit transaction must be coordinated with the business transaction.

---

# 5. Audit Event Structure

A conceptual audit event should contain:

```text
AuditEvent
 ├── id
 ├── timestamp
 ├── actor
 ├── organization
 ├── action
 ├── resource
 ├── resourceId
 ├── outcome
 ├── correlationId
 ├── requestId
 ├── source
 ├── metadata
 └── securityContext
```

The exact database schema is defined in `04-Database`.

---

# 6. Required Audit Fields

The following fields should be available for significant events.

| Field | Purpose |
|---|---|
| `id` | Unique audit-event identifier |
| `timestamp` | Time at which the event occurred |
| `actorId` | User or system actor |
| `organizationId` | Organization context |
| `action` | Action performed |
| `resourceType` | Type of affected resource |
| `resourceId` | Affected resource identifier |
| `outcome` | Success or failure |
| `correlationId` | End-to-end operation correlation |
| `requestId` | Request-level traceability |
| `source` | API, system job, admin, etc. |
| `metadata` | Approved contextual information |

Not every event requires every field.

---

# 7. Actor Types

Audit events may originate from:

```text
USER
SYSTEM
SCHEDULED_JOB
ADMINISTRATOR
INTEGRATION
```

The actor type must distinguish automated activity from human activity.

Example:

```text
actorType = SYSTEM
actorId = assessment-publishing-job
```

when an automated process publishes an authorized scheduled assessment.

---

# 8. Action Naming

Audit actions should use a consistent naming convention.

Recommended format:

```text
<RESOURCE>_<ACTION>
```

Examples:

```text
USER_CREATED
USER_UPDATED
USER_DISABLED

QUESTION_CREATED
QUESTION_UPDATED
QUESTION_SUBMITTED
QUESTION_APPROVED
QUESTION_REJECTED
QUESTION_PUBLISHED

BLUEPRINT_CREATED
BLUEPRINT_UPDATED
BLUEPRINT_PUBLISHED

ASSESSMENT_CREATED
ASSESSMENT_PUBLISHED
ASSESSMENT_CANCELLED

ATTEMPT_STARTED
ATTEMPT_SUBMITTED

EVALUATION_CREATED
EVALUATION_UPDATED
EVALUATION_REVIEWED

RESULT_PUBLISHED
RESULT_REPUBLISHED
```

Security events may use:

```text
LOGIN_SUCCESS
LOGIN_FAILED
LOGOUT
TOKEN_REVOKED
PASSWORD_CHANGED
PASSWORD_RESET
```

---

# 9. Audit Categories

Audit events should be categorized.

## Security

- Login
- Logout
- Password changes
- Password reset
- Token events
- Permission failures
- Account lockouts

## Identity

- User creation
- User modification
- User activation
- User suspension
- Role assignment
- Role removal

## Academic

- Exam creation
- Subject changes
- Topic changes
- Academic configuration

## Question Bank

- Question creation
- Question modification
- Review
- Approval
- Rejection
- Publication
- Archival

## Blueprint

- Blueprint creation
- Rule changes
- Validation
- Publication
- Version creation

## Assessment

- Creation
- Configuration changes
- Scheduling
- Publishing
- Cancellation

## Delivery

- Attempt start
- Attempt resume
- Response submission
- Attempt submission
- Attempt termination

## Evaluation

- Evaluation
- Manual score changes
- Grace marks
- Re-evaluation
- Evaluation approval

## Results

- Result calculation
- Result publication
- Result correction
- Result republication

## Administration

- Configuration changes
- Organization settings
- Permission changes
- Feature configuration

---

# 10. Question Bank Audit

Question-related changes are highly significant.

The platform should record events such as:

```text
QUESTION_CREATED
QUESTION_UPDATED
QUESTION_SUBMITTED
QUESTION_REVIEW_STARTED
QUESTION_APPROVED
QUESTION_REJECTED
QUESTION_PUBLISHED
QUESTION_ARCHIVED
QUESTION_VERSION_CREATED
```

For important changes, the audit record should identify the affected question version.

---

# 11. Question Content Protection

Audit records must not automatically contain the complete question content.

For example, an audit record should preferably contain:

```text
resourceType = QUESTION
resourceId = <question-id>
versionId = <version-id>
action = QUESTION_PUBLISHED
```

rather than copying the entire question statement, options, images, and explanation into the audit table.

This prevents audit storage from becoming an uncontrolled duplicate of sensitive examination content.

---

# 12. Blueprint Audit

Blueprint modifications should be traceable.

Examples:

```text
BLUEPRINT_CREATED
BLUEPRINT_RULE_UPDATED
BLUEPRINT_VALIDATED
BLUEPRINT_PUBLISHED
BLUEPRINT_VERSION_CREATED
```

Where relevant, metadata should identify:

- Previous version
- New version
- Changed configuration area
- Actor
- Timestamp

---

# 13. Assessment Audit

Assessment lifecycle events must be audited.

Example:

```text
ASSESSMENT_CREATED
ASSESSMENT_UPDATED
ASSESSMENT_SCHEDULED
ASSESSMENT_PUBLISHED
ASSESSMENT_UNPUBLISHED
ASSESSMENT_CANCELLED
ASSESSMENT_VERSION_CREATED
```

Publishing an assessment should always create an auditable event.

---

# 14. Examination Delivery Audit

Candidate examination activity must be auditable at an appropriate level.

Examples:

```text
ATTEMPT_STARTED
ATTEMPT_RESUMED
ATTEMPT_SUBMITTED
ATTEMPT_AUTO_SUBMITTED
ATTEMPT_TERMINATED
```

The audit system should not necessarily record every keystroke or every UI interaction.

High-volume candidate telemetry belongs in the appropriate delivery/analytics architecture.

Audit logging should focus on significant state changes.

---

# 15. Candidate Response Audit

Important response lifecycle changes may include:

```text
RESPONSE_CREATED
RESPONSE_UPDATED
RESPONSE_SUBMITTED
RESPONSE_FINALIZED
```

The system must carefully consider the volume of response events.

Audit logging must not become a performance bottleneck during large examinations.

Where detailed response history is required, a dedicated response-history model may be preferable to the general audit table.

---

# 16. Evaluation Audit

Evaluation changes are high-risk and must be traceable.

Events include:

```text
EVALUATION_STARTED
EVALUATION_SUBMITTED
EVALUATION_UPDATED
EVALUATION_APPROVED
EVALUATION_REJECTED
RE_EVALUATION_REQUESTED
RE_EVALUATION_COMPLETED
```

For score changes, audit metadata should capture:

- Previous score
- New score
- Reason
- Actor
- Timestamp
- Relevant evaluation/version identifier

---

# 17. Grace Marks

Grace marks are a sensitive examination operation.

Every grace-mark action must be audited.

Example:

```text
GRACE_MARK_APPLIED
```

Metadata should include:

```text
candidateId
assessmentId
previousScore
graceMarks
newScore
reason
actorId
```

The actual candidate information must follow the platform's data-protection rules.

---

# 18. Result Audit

Result lifecycle changes must be traceable.

Examples:

```text
RESULT_CALCULATED
RESULT_RECALCULATED
RESULT_PUBLISHED
RESULT_WITHDRAWN
RESULT_CORRECTED
RESULT_REPUBLISHED
```

Any manual correction to a published result must require an appropriate permission and generate an audit event.

---

# 19. Role and Permission Audit

Changes to authorization must always be audited.

Examples:

```text
ROLE_CREATED
ROLE_UPDATED
ROLE_ASSIGNED
ROLE_REMOVED

PERMISSION_GRANTED
PERMISSION_REVOKED
```

The audit event should identify:

- Actor
- Target user/role
- Organization
- Previous state
- New state
- Timestamp

---

# 20. Authentication Audit

Authentication events should include:

```text
LOGIN_SUCCESS
LOGIN_FAILED
LOGOUT
TOKEN_REFRESHED
TOKEN_REVOKED
PASSWORD_CHANGED
PASSWORD_RESET_REQUESTED
PASSWORD_RESET_COMPLETED
ACCOUNT_LOCKED
ACCOUNT_UNLOCKED
```

Failed authentication events should contain enough information for security investigation without recording credentials.

---

# 21. Authorization Failure Audit

Repeated or security-relevant authorization failures should be auditable.

Example:

```text
AUTHORIZATION_DENIED
```

Useful metadata may include:

```text
actorId
organizationId
requestedResource
requestedAction
result
correlationId
timestamp
```

Do not store sensitive request payloads merely to investigate authorization failures.

---

# 22. Before and After Values

For configuration changes, storing before/after information can be useful.

Example:

```text
ASSESSMENT_CONFIGURATION_UPDATED

Before:
duration = 60
negativeMarking = 0.25

After:
duration = 90
negativeMarking = 0.50
```

However, before/after data must be subject to sensitive-data filtering.

Do not record:

- Passwords
- Tokens
- Secret keys
- Private credentials

---

# 23. Audit Metadata

Metadata should be structured rather than embedding arbitrary text whenever possible.

Example:

```json
{
  "previousStatus": "DRAFT",
  "newStatus": "PUBLISHED",
  "versionId": "version-id"
}
```

The metadata model must prevent uncontrolled storage growth.

---

# 24. Correlation IDs

Every significant request should have a correlation identifier.

Example:

```text
HTTP Request
    |
    +--> correlationId = abc-123
          |
          +--> Service A
          |
          +--> Service B
          |
          +--> Database operation
          |
          +--> Audit event
```

This enables support and security teams to reconstruct the path of an operation.

---

# 25. Request IDs

A request identifier may identify a single HTTP request.

Correlation IDs may span multiple operations.

The two should not be treated as interchangeable unless the implementation explicitly defines them as such.

---

# 26. System-Generated Events

Automated processes must also produce audit events when they perform significant business operations.

Examples:

```text
SCHEDULED_ASSESSMENT_PUBLISHED
RESULT_CALCULATION_COMPLETED
AUTOMATIC_ATTEMPT_SUBMITTED
ACCOUNT_EXPIRATION_PROCESSED
```

The actor should identify the responsible system process.

---

# 27. Transactional Consistency

For critical operations, the business state change and audit event should have appropriate transactional consistency.

Example:

```text
Publish Assessment
       |
       +--> Update assessment state
       |
       +--> Create audit event
       |
       +--> Commit
```

The system must avoid situations where:

```text
Assessment = PUBLISHED
Audit Event = Missing
```

for operations where the audit event is mandatory.

---

# 28. Audit Failure Strategy

The platform must explicitly define behavior when audit persistence fails.

For high-risk operations such as:

- Result publication
- Assessment publication
- Permission changes
- Evaluation changes
- Administrative security changes

the preferred behavior is to fail the operation if mandatory audit evidence cannot be persisted.

For lower-risk telemetry, asynchronous handling may be acceptable.

---

# 29. Synchronous vs Asynchronous Auditing

## Synchronous

Use when the audit record is part of the business transaction.

Suitable for:

- Role changes
- Permission changes
- Assessment publication
- Result publication
- Evaluation changes
- Security configuration

## Asynchronous

May be used for high-volume events where eventual audit persistence is acceptable.

Suitable candidates may include:

- Non-critical activity telemetry
- Analytics events
- High-volume delivery events

The decision must be made per event category.

---

# 30. Immutability

Application users must never have APIs that allow:

```text
UPDATE audit event
DELETE audit event
```

Audit records should be append-only.

If a correction to an audit-related interpretation is required, a new event should explain the correction rather than modifying the historical event.

---

# 31. Database Protection

The database must protect audit records using appropriate:

- Permissions
- Constraints
- Indexes
- Retention controls
- Backup mechanisms

Application database users should have only the permissions required to insert and query audit records.

Direct administrative modification must be restricted.

---

# 32. Audit Access

Audit access is itself sensitive.

Only authorized roles should access audit information.

Potential access levels include:

```text
Platform Security Administrator
Organization Administrator
Auditor
Support Administrator
```

Users must not automatically be able to inspect audit records merely because they can perform the corresponding business operation.

---

# 33. Student Access

Students should not have access to internal security audit records.

They may see user-facing history where explicitly required, but internal audit metadata must remain protected.

---

# 34. Privacy

Audit logging must follow data-minimization principles.

The system should avoid storing unnecessary:

- Personal data
- Request payloads
- IP information beyond operational requirements
- Device information
- Authentication secrets

Where personal information is required for auditability, retention and access must be controlled.

---

# 35. IP Address Logging

IP address logging may be useful for security investigation.

If collected, the platform must define:

- Purpose
- Storage format
- Retention
- Access permissions
- Privacy considerations

IP information must not be collected merely because it is technically available.

---

# 36. User-Agent and Device Information

User-agent or device information may be recorded for security events when useful.

It should not become a default dumping ground for arbitrary request information.

---

# 37. Audit Retention

Audit retention must be configurable according to:

- Business requirements
- Security requirements
- Organizational policy
- Applicable legal/regulatory requirements
- Storage capacity

Audit records related to examination integrity may require longer retention than ordinary operational logs.

The exact retention period must be established before production deployment.

---

# 38. Audit Archival

For large deployments, older audit records may be moved to archival storage.

Archival must preserve:

- Integrity
- Searchability where required
- Original timestamps
- Event identifiers
- Referential context

Archived records must remain protected from unauthorized modification.

---

# 39. Audit Search

Authorized administrators should be able to search audit records by fields such as:

- Event type
- Actor
- Organization
- Resource type
- Resource ID
- Date/time range
- Outcome
- Correlation ID

Search functionality must enforce the same authorization boundaries as ordinary audit access.

---

# 40. Audit Reporting

The platform may provide reports such as:

- User activity
- Administrative changes
- Security events
- Question lifecycle history
- Assessment publication history
- Evaluation changes
- Result changes

Reports must be generated from the audit model without modifying audit records.

---

# 41. Examination Integrity Requirements

The audit system must support reconstruction of critical examination events.

At minimum, authorized investigators should be able to establish:

```text
Who
  |
  v
Created/changed content
  |
  v
Approved content
  |
  v
Published assessment
  |
  v
Candidate attempted assessment
  |
  v
Responses evaluated
  |
  v
Results calculated
  |
  v
Results published
  |
  v
Any later correction
```

This traceability is a core platform requirement.

---

# 42. Example Audit Timeline

```text
10:00:01
QUESTION_CREATED

10:05:12
QUESTION_SUBMITTED

11:20:44
QUESTION_APPROVED

11:45:03
QUESTION_PUBLISHED

13:00:00
ASSESSMENT_PUBLISHED

14:00:08
ATTEMPT_STARTED

14:52:17
ATTEMPT_SUBMITTED

15:10:42
EVALUATION_COMPLETED

15:15:09
RESULT_CALCULATED

16:00:00
RESULT_PUBLISHED
```

The exact events depend on the platform workflow.

---

# 43. Audit Security

Audit infrastructure must itself be protected against:

- Unauthorized access
- Modification
- Deletion
- Injection
- Data leakage
- Excessive exposure
- Storage exhaustion

Audit logging must not become a security vulnerability.

---

# 44. Performance

Audit operations must not significantly degrade examination performance.

Particular attention is required during:

- Large examination starts
- High-volume response submission
- Assessment submission
- Result processing

High-frequency events should be carefully classified before making them synchronous audit transactions.

---

# 45. Failure and Recovery

The system must define recovery behavior for:

- Audit database outage
- Storage exhaustion
- Database transaction rollback
- Application restart
- Message delivery failure
- Partial processing

Critical audit events must not silently disappear.

---

# 46. Testing Requirements

## Functional

Test:

- Audit event creation
- Correct actor
- Correct organization
- Correct resource
- Correct action
- Correct outcome
- Correct timestamp
- Correlation ID propagation

## Security

Test:

- Unauthorized audit access
- Audit modification attempts
- Audit deletion attempts
- Sensitive data leakage
- Cross-organization audit access

## Integrity

Test:

- Business action + audit transaction consistency
- Failed transaction behavior
- Retry behavior
- Duplicate event handling

## Performance

Test:

- High-volume audit generation
- Examination peak load
- Search performance
- Archival performance

---

# 47. Acceptance Criteria

Audit logging is considered complete when:

- Critical security events are recorded.
- Critical examination lifecycle events are recorded.
- Audit records identify the responsible actor or system.
- Organization context is available where applicable.
- Critical changes are traceable to resources and versions.
- Audit records are immutable.
- Unauthorized users cannot access audit data.
- Sensitive credentials never enter audit records.
- Correlation IDs support investigation.
- Critical business operations have defined audit-failure behavior.
- Retention and archival policies are defined.
- Automated audit integrity tests pass.

---

# 48. Related Documents

- `Security Architecture`
- `Authentication.md`
- `Authorization-RBAC.md`
- `JWT-Strategy.md`
- `Password-Policy.md`
- `Data-Protection.md`
- `OWASP-Checklist.md`
- `02-Domain-Model`
- `04-Database`
- `05-API`
- `06-Implementation`
- `07-Deployment`