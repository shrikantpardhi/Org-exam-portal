# Authorization and RBAC

**Document Version:** 1.0  
**Status:** Approved  
**Parent Document:** Security Architecture

---

# 1. Purpose

This document defines the authorization model for the Configurable Examination Platform.

Authorization determines whether an authenticated user is permitted to perform a requested action on a specific resource within a specific organizational context.

Authentication answers:

> Who is the user?

Authorization answers:

> What is this user allowed to do?

The platform uses **Role-Based Access Control (RBAC)** as the primary authorization model.

---

# 2. Authorization Objectives

The authorization system must:

- Enforce least privilege.
- Prevent unauthorized resource access.
- Support organization isolation.
- Support configurable roles and permissions.
- Protect administrative functions.
- Protect examination content.
- Protect examination attempts and results.
- Enforce permissions consistently across APIs and services.
- Provide auditable authorization decisions.
- Prevent privilege escalation.

---

# 3. Core Authorization Model

The authorization model is:

```text
User
  |
  +---- Organization Membership
  |
  +---- Role
          |
          +---- Permission
                    |
                    +---- Resource / Action
```

A simplified authorization decision is:

```text
Authenticated User
        |
        v
Organization Context
        |
        v
Assigned Role
        |
        v
Required Permission
        |
        v
Resource Access Rules
        |
        +---- Allowed
        |
        +---- Denied
```

A valid role alone does not automatically provide access to every resource.

---

# 4. Authorization Components

## 4.1 User

A user represents an authenticated identity.

A user may have different roles in different organizational contexts.

---

## 4.2 Organization

An organization represents the tenant or institutional boundary within which users and business data operate.

Examples:

- University
- Coaching Institute
- Certification Organization
- Corporate Training Organization

Organization boundaries must be enforced at the application/service layer.

---

## 4.3 Role

A role is a named collection of permissions.

Examples:

- Super Administrator
- Organization Administrator
- Academic Administrator
- Question Author
- Reviewer
- Examiner
- Evaluator
- Student
- Support User

Roles should describe responsibility rather than individual API endpoints.

---

## 4.4 Permission

A permission represents an allowed business capability.

Examples:

```text
QUESTION_CREATE
QUESTION_UPDATE
QUESTION_REVIEW
QUESTION_PUBLISH

BLUEPRINT_CREATE
BLUEPRINT_UPDATE
BLUEPRINT_PUBLISH

ASSESSMENT_CREATE
ASSESSMENT_SCHEDULE
ASSESSMENT_PUBLISH

EVALUATION_VIEW
EVALUATION_PERFORM
EVALUATION_REVIEW

RESULT_VIEW
RESULT_PUBLISH
```

Permission naming should remain consistent throughout the platform.

---

# 5. RBAC Relationship

The standard relationship is:

```text
User
 |
 +--> User Role Assignment
          |
          +--> Role
                 |
                 +--> Role Permission
                         |
                         +--> Permission
```

A user receives permissions through role assignment.

Direct user-to-permission assignment should generally be avoided because it makes permission management difficult to audit.

---

# 6. Organization Context

Authorization must consider organization context.

For a multi-organization deployment:

```text
User A
 |
 +--> Organization A
       |
       +--> Role: Question Author
```

User A should not automatically access:

```text
Organization B
```

unless an explicit authorization relationship exists.

---

# 7. Resource-Level Authorization

Permissions alone are not always sufficient.

Example:

A Question Author may have:

```text
QUESTION_UPDATE
```

but that does not necessarily mean the author can modify every question in the organization.

Additional rules may restrict access to:

- Questions created by the user
- Questions assigned to the user
- Questions belonging to a specific subject
- Questions in a permitted organization
- Questions in an editable lifecycle state

Therefore authorization may combine:

```text
Role Permission
+
Organization Scope
+
Resource Ownership
+
Resource State
```

---

# 8. Role Model

## Super Administrator

Platform-level administrative access.

Typical responsibilities:

- Organization management
- Platform configuration
- User administration
- Security administration
- System monitoring

This role should be tightly controlled.

---

## Organization Administrator

Manages an organization.

Typical permissions:

- User management
- Organization settings
- Role assignment
- Academic configuration
- Assessment administration

---

## Academic Administrator

Manages academic structures.

Typical permissions:

- Exams
- Subjects
- Chapters
- Topics
- Difficulty levels
- Academic configuration

---

## Question Author

Creates and maintains questions within assigned scope.

Typical permissions:

- Create questions
- Edit draft questions
- Submit questions for review
- View permitted question content

Publication permissions should not automatically be granted.

---

## Reviewer

Reviews question content.

Typical permissions:

- View submitted questions
- Review questions
- Request changes
- Approve questions where workflow permits

---

## Examiner

Manages examination configuration and execution according to assigned responsibilities.

---

## Evaluator

Evaluates candidate responses where manual evaluation is required.

Typical permissions:

- View assigned responses
- Submit evaluations
- Update permitted evaluations
- Participate in re-evaluation workflows

---

## Student

Consumes examination functionality.

Typical permissions:

- View assigned assessments
- Start permitted attempts
- Submit responses
- View permitted results

Students must never receive administrative permissions.

---

## Support User

Provides operational support without automatically receiving unrestricted business access.

Support access should follow least privilege and may require additional audit controls.

---

# 9. Permission Naming Convention

Permissions should follow a predictable structure:

```text
<RESOURCE>_<ACTION>
```

Examples:

```text
USER_VIEW
USER_CREATE
USER_UPDATE
USER_DISABLE

QUESTION_VIEW
QUESTION_CREATE
QUESTION_UPDATE
QUESTION_REVIEW
QUESTION_PUBLISH

ASSESSMENT_VIEW
ASSESSMENT_CREATE
ASSESSMENT_UPDATE
ASSESSMENT_PUBLISH

RESULT_VIEW
RESULT_PUBLISH
```

For more complex systems, permissions may use:

```text
<DOMAIN>_<RESOURCE>_<ACTION>
```

Example:

```text
ACADEMIC_SUBJECT_CREATE
QUESTION_BANK_QUESTION_PUBLISH
ASSESSMENT_RESULT_PUBLISH
```

The final convention must remain consistent across API, database, UI, and documentation.

---

# 10. Permission Categories

Permissions should generally fall into categories such as:

### Read

```text
VIEW
LIST
SEARCH
```

### Create

```text
CREATE
```

### Modify

```text
UPDATE
```

### Lifecycle

```text
SUBMIT
APPROVE
REJECT
PUBLISH
ARCHIVE
```

### Sensitive Operations

```text
EXPORT
DELETE
REVOKE
RESET
RE_EVALUATE
```

Sensitive operations require additional authorization and auditing.

---

# 11. Lifecycle-Aware Authorization

Authorization must consider entity state.

Example:

A question may be:

```text
DRAFT
  |
  v
SUBMITTED
  |
  v
UNDER_REVIEW
  |
  +---- REJECTED
  |       |
  |       v
  |     DRAFT
  |
  v
APPROVED
  |
  v
PUBLISHED
  |
  v
ARCHIVED
```

A user may have `QUESTION_UPDATE`, but that permission must not allow modification of a published immutable version.

Therefore:

```text
Permission != Automatic State Transition
```

The business workflow must validate both permission and current state.

---

# 12. Service-Layer Enforcement

Authorization must be enforced at the service/business layer.

The system must not rely solely on frontend controls.

Incorrect:

```text
Frontend hides Delete button
        ↓
API allows DELETE
```

Correct:

```text
Frontend hides Delete button
        ↓
API receives request
        ↓
Authentication
        ↓
Authorization
        ↓
Business Rule
        ↓
Resource State
        ↓
Operation
```

The backend is the final security boundary.

---

# 13. Controller Responsibilities

Controllers may enforce coarse-grained access requirements where appropriate.

However, controllers must not contain complex authorization logic.

Example:

```text
Controller
    |
    +--> Authentication context
    |
    +--> Service
            |
            +--> Permission check
            +--> Organization check
            +--> Resource check
            +--> Business rules
```

---

# 14. Organization Isolation

Every organization-scoped operation must validate organization context.

The application must prevent:

```text
Organization A user
        |
        v
Organization B resource
```

unless explicitly authorized.

Organization identifiers supplied by clients must never be blindly trusted.

The server should derive or validate organizational context from the authenticated identity and authorized resource relationships.

---

# 15. Object-Level Authorization

Object-level authorization is required for sensitive resources.

Examples:

- Questions
- Assessments
- Examination attempts
- Candidate responses
- Evaluation records
- Results
- Audit records

Example:

```text
GET /assessments/{assessmentId}
```

The system must verify:

1. User is authenticated.
2. User has required permission.
3. Assessment belongs to an accessible organization.
4. Assessment is accessible to the user's role.
5. Assessment state permits the requested operation.

---

# 16. Student Authorization

Student authorization requires additional restrictions.

A student should only access:

- Assessments assigned to the student.
- Valid examination windows.
- Their own attempts.
- Their own responses.
- Results explicitly made available to them.

A student must never be able to access:

- Answer keys before permitted release.
- Other students' responses.
- Other students' results.
- Evaluation notes unless explicitly permitted.
- Administrative question metadata.

---

# 17. Question Author Authorization

Question Authors may create and edit questions within their permitted scope.

They must not automatically:

- Publish questions.
- Approve their own review.
- Modify immutable published versions.
- Access restricted answer keys.
- Modify organization configuration.

Separation of duties should be maintained where examination integrity requires it.

---

# 18. Reviewer Authorization

Reviewers may review content assigned to them or within their authorized scope.

The system should prevent unauthorized self-approval where workflow rules require independent review.

Example:

```text
Author
  |
  v
Submit
  |
  v
Reviewer
  |
  v
Approve
```

The author should not bypass the review workflow simply because they possess a general update permission.

---

# 19. Evaluator Authorization

Evaluators should only access responses assigned to them or within their authorized evaluation scope.

They must not automatically access:

- Unassigned candidate responses
- Restricted answer keys
- Administrative configuration
- Other evaluators' private notes

Re-evaluation permissions should be separately controlled.

---

# 20. Result Authorization

Results are highly sensitive.

Access should depend on:

- User role
- Organization
- Assessment
- Candidate relationship
- Result publication state

Example:

```text
Student
  |
  +--> Own Published Result

Evaluator
  |
  +--> Assigned Evaluation Scope

Administrator
  |
  +--> Authorized Organization Results
```

---

# 21. Administrative Authorization

Administrative operations require elevated permissions.

Examples:

- Assigning roles
- Changing permissions
- Publishing assessments
- Publishing results
- Changing examination configuration
- Managing organization settings
- Viewing sensitive audit information

Administrative actions must be audited.

---

# 22. Privilege Escalation Prevention

The platform must prevent users from granting themselves additional permissions.

Examples of prohibited behavior:

```text
Student
  -> modifies own role
```

```text
Question Author
  -> grants PUBLISH permission to self
```

```text
Organization Admin
  -> modifies platform-level administrator permissions
```

Permission-management APIs must enforce administrative boundaries.

---

# 23. Role Assignment Rules

Role assignment should follow these principles:

- Only authorized administrators may assign roles.
- Users cannot assign roles to themselves.
- Organization-scoped administrators cannot grant platform-level privileges.
- Role changes must be audited.
- Permission changes should take effect according to the defined session/token strategy.

---

# 24. Authorization Failure

Unauthorized operations should return an appropriate security response.

Typical cases:

```text
401 Unauthorized
```

when authentication is missing or invalid.

```text
403 Forbidden
```

when authentication exists but authorization fails.

The application must not reveal unnecessary resource information through authorization errors.

---

# 25. Avoiding Resource Enumeration

The platform should avoid exposing whether protected resources exist when the requester has no permission to access them.

This is particularly important for:

- Assessments
- Questions
- Candidate records
- Results
- Audit records

Responses should be designed to minimize information leakage.

---

# 26. Audit Requirements

The following authorization events should be considered for audit:

- Permission denied
- Role assignment
- Role removal
- Permission changes
- Administrative access
- Sensitive resource access
- Privilege changes
- Security policy changes

Audit requirements are detailed in `Audit-Logging.md`.

---

# 27. Frontend Authorization

The frontend may use permissions to:

- Hide unavailable navigation.
- Disable actions.
- Prevent unnecessary API calls.
- Display appropriate UI states.

However:

> Frontend authorization is a user-experience control, not a security boundary.

Every protected operation must be enforced by the backend.

---

# 28. API Authorization

Every protected API must define:

- Required authentication
- Required permission
- Organization scope
- Resource scope
- Lifecycle restrictions

The API documentation should identify authorization requirements for each endpoint.

---

# 29. Testing Requirements

Authorization tests must cover:

### Role Tests

- Correct role receives permission.
- Incorrect role is denied.
- Removed role loses access.

### Organization Tests

- Organization A cannot access Organization B.
- Cross-organization resource IDs are rejected.
- Organization context cannot be spoofed.

### Resource Tests

- Unauthorized resource access is denied.
- Resource ownership is enforced.
- Lifecycle restrictions are enforced.

### Privilege Tests

- Users cannot modify their own roles.
- Users cannot grant themselves permissions.
- Lower-level administrators cannot perform platform-level actions.

### Sensitive Resource Tests

- Students cannot access other students' results.
- Students cannot access answer keys prematurely.
- Evaluators cannot access unauthorized responses.
- Authors cannot bypass review workflows.

---

# 30. Acceptance Criteria

Authorization is considered complete when:

- Every protected operation requires authentication.
- Every protected operation has an authorization rule.
- RBAC is consistently enforced.
- Organization isolation is enforced.
- Resource-level authorization is implemented where required.
- Lifecycle rules are enforced.
- Sensitive operations require appropriate permissions.
- Privilege escalation is prevented.
- Frontend controls are not relied upon for security.
- Authorization failures are handled consistently.
- Security-sensitive authorization events are auditable.
- Automated authorization tests pass.

---

# 31. Related Documents

- `Security Architecture`
- `Authentication.md`
- `JWT-Strategy.md`
- `Password-Policy.md`
- `Audit-Logging.md`
- `Data-Protection.md`
- `OWASP-Checklist.md`
- `02-Domain-Model`
- `05-API`
- `06-Implementation`