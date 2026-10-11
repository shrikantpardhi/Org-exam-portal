# Smart Competitive Examination Platform
## P1 — Identity & Security Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P1 — Identity & Security
**Depends on:** P0 Foundation
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 preferred, Spring Boot 4
**Security:** Spring Security + JWT + RBAC
**Database:** PostgreSQL
**Migration:** Flyway
**Cache:** Redis cache-only
**API:** REST + JSON, `/api/v1`
**Identifiers:** UUID
**Audience:** Backend Engineers, Frontend Engineers, QA, Security, DevOps, Architects

---

## 1. Purpose

This document converts the Identity domain, security architecture, API contract, database schema, P0 engineering foundation, and pilot-first roadmap into an executable implementation specification for P1.

P1 is responsible for:

```text
Organization
User
Role
Permission
UserRole
RolePermission
LoginSession
Authentication
Authorization
Password handling
Password reset
Session lifecycle
Current-user APIs
Permission APIs
System actor isolation
Security audit foundation
```

P1 makes the platform safe enough for subsequent development in Academic, Content, Assessment, Delivery, Evaluation, and Pilot operations.

P1 does not implement downstream business modules.

---

## 2. Source-of-truth hierarchy

Read these documents before P1 implementation:

- Master PRD
- Domain Model
- Technical Design Document
- Database Standards and Schema
- API Contract
- Architecture Freeze
- Development Roadmap
- ExamEngine and CDUI Contract
- P0 Foundation Implementation Specification
- This document

The source documents establish:

- JWT authentication
- RBAC authorization
- Secure password hashing
- Input validation
- Audit logging
- API authorization
- HTTPS in production
- Persisted login sessions

Session states:

```text
ACTIVE
EXPIRED
LOGGED_OUT
```

The API contract establishes these endpoints.

```text
POST /api/v1/auth/login
POST /api/v1/auth/logout
POST /api/v1/auth/refresh-token
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password

GET /api/v1/me
GET /api/v1/me/permissions

GET / POST / PUT / DELETE /api/v1/users
GET / POST / PUT /api/v1/roles
GET / PUT /api/v1/roles/{id}/permissions
GET /api/v1/permissions
GET /api/v1/login-sessions
GET /api/v1/login-sessions/{id}
```

The database contract establishes the seven Identity tables.

```text
organization
user
role
permission
user_role
role_permission
login_session
```

---

## 3. P1 objective

P1 is complete when an authenticated user can safely:

```text
Login
 |
 v
Receive authenticated session context
 |
 v
Call protected API
 |
 v
Backend resolves roles
 |
 v
Backend resolves permissions
 |
 v
Backend authorizes operation
 |
 v
User can logout
 |
 v
Session becomes LOGGED_OUT
```

And when the following works without exposing whether an arbitrary account exists:

```text
Forgot password
 |
 v
Reset password
 |
 v
Login with new password
```

P1 also provides the foundation for subsequent domain-level authorization such as `QUESTION_CREATE`, `QUESTION_PUBLISH`, `ASSESSMENT_CREATE`, and `ASSESSMENT_PUBLISH` without hard-coding role names throughout the business modules.

---

## 4. P1 non-goals

P1 does not implement:

```text
Academic
Question Bank
Assessment
Delivery
Evaluation
Analytics
Commerce
Communication
Full Administration
SSO
OAuth
2FA
Biometric authentication
Advanced proctoring
Device trust
Multi-tenant authorization
```

P1 does not implement full Administration screens.

P1 does implement the minimum user and role management APIs already defined by the API contract because those APIs are part of Identity.

---

## 5. Identity domain model

The Identity model is:

```text
Organization
     |
     v
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

User
 |
 v
LoginSession
```

Relationships:

```text
Organization 1:N User
User M:N Role through UserRole
Role M:N Permission through RolePermission
User 1:N LoginSession
```

A user is not permanently tied to one business function. Role assignment determines what the user can do.

---

## 6. Identity aggregate boundaries

P1 treats these as primary aggregates:

```text
Organization
User
Role
Permission
LoginSession
```

`UserRole` and `RolePermission` are relationship entities owned by the applicable aggregate or application operation.

Avoid a design where every controller directly manipulates relationship rows.

Prefer:

```text
UserService
RoleService
AuthorizationService
SessionService
AuthenticationService
```

and corresponding repositories.

---

## 7. Organization

Entity: `organization`.

Purpose: the organization operating this deployment.

The current product is scoped to one organization per deployment.

Organization is modeled as a real domain entity because it is part of the domain model and is referenced by users and organization-level configuration.

Frozen fields:

| Field | Type |
|---|---|
| id | UUID primary key |
| code | VARCHAR(50) |
| name | VARCHAR(150) |
| description | TEXT |
| status | ACTIVE / INACTIVE / ARCHIVED |
| created_by | UUID |
| created_on | TIMESTAMPTZ |
| updated_by | UUID |
| updated_on | TIMESTAMPTZ |

Do not add `tenant_id`, `subscription_tier`, `billing_plan`, `region_id`, or `parent_organization_id` without an approved future requirement.

Organization rules:

- One active organization per deployment.
- The application derives the active organization from persisted identity and configuration.
- The organization name is not compiled into Java classes.

Organization lifecycle:

```text
ACTIVE
INACTIVE
ARCHIVED
```

The effects of organization inactivity on authenticated users are enforced centrally by Identity rather than separately by every business module.

---

## 8. User

Entity: `user`.

Purpose: every authenticated platform user.

Roles include: `ADMIN`, `REVIEWER`, `STAFF`, `STUDENT`, `SUPPORT`, `FINANCE`.

A user can have multiple roles.

Frozen fields:

| Field | Type |
|---|---|
| id | UUID primary key |
| organization_id | UUID FK |
| first_name | VARCHAR(100) |
| last_name | VARCHAR(100) |
| email | VARCHAR(255) |
| mobile | VARCHAR(20) |
| password | VARCHAR(255) |
| status | ACTIVE / INACTIVE / ARCHIVED |
| created_by | UUID |
| created_on | TIMESTAMPTZ |
| updated_by | UUID |
| updated_on | TIMESTAMPTZ |

The `password` field stores only a secure password hash.

Constraints:

```text
Primary key on id
Foreign key on organization_id
Unique on email
Unique on mobile
Not null on organization_id
Not null on email
Not null on password
Not null on status
```

Email uniqueness may be normalized according to the implementation strategy. The normalization strategy is documented.

Do not silently change the schema to introduce multiple-email identities.

User status semantics:

- ACTIVE is eligible for login.
- INACTIVE is rejected.
- ARCHIVED is rejected.

The rejection uses a safe authentication error without exposing unnecessary account state.

---

## 9. Role

Entity: `role`.

Purpose: a named set of permissions.

Frozen fields:

| Field | Type |
|---|---|
| id | UUID primary key |
| code | VARCHAR(50) |
| name | VARCHAR(100) |
| description | TEXT |
| status | Lifecycle |
| created_by | UUID |
| created_on | TIMESTAMPTZ |
| updated_by | UUID |
| updated_on | TIMESTAMPTZ |

Unique: `UNIQUE(code)`.

Initial role concepts:

```text
ADMIN
REVIEWER
STAFF
STUDENT
SUPPORT
FINANCE
```

These are role concepts, not permission definitions.

Do not encode business authorization as `if role == ADMIN` except for truly role-specific platform behavior approved by the authorization design. Prefer permission checks.

---

## 10. Permission

Entity: `permission`.

Purpose: one atomic authorization capability.

Frozen fields:

| Field | Type |
|---|---|
| id | UUID primary key |
| code | VARCHAR(100) |
| name | VARCHAR(150) |
| description | TEXT |
| status | Lifecycle |
| created_by | UUID |
| created_on | TIMESTAMPTZ |
| updated_by | UUID |
| updated_on | TIMESTAMPTZ |

Unique: `UNIQUE(code)`.

Permissions represent operations and capabilities rather than UI labels.

Good conceptual examples:

```text
USER_VIEW
USER_CREATE
USER_UPDATE
USER_ARCHIVE

ROLE_VIEW
ROLE_CREATE
ROLE_UPDATE
ROLE_PERMISSION_ASSIGN

QUESTION_VIEW
QUESTION_CREATE
QUESTION_SUBMIT_REVIEW
QUESTION_REVIEW
QUESTION_PUBLISH

ASSESSMENT_VIEW
ASSESSMENT_CREATE
ASSESSMENT_PUBLISH

SESSION_VIEW
PERMISSION_VIEW
SYSTEM_HEALTH_VIEW
GLOBAL_SEARCH_USER
GLOBAL_SEARCH_QUESTION
GLOBAL_SEARCH_COMMERCE
GLOBAL_SEARCH_AUDIT
```

The final permission catalog is controlled master data.

Do not create thousands of permissions prematurely.

---

## 11. Permission evaluation

The backend evaluates:

```text
Authenticated user
      |
      v
Active user
      |
      v
User roles
      |
      v
Role permissions
      |
      v
Required permission
```

A role itself is not the ultimate authorization decision when an endpoint is protected by a permission.

---

## 12. UserRole

Entity: `user_role`.

Frozen fields:

| Field | Type |
|---|---|
| id | UUID primary key |
| user_id | UUID FK |
| role_id | UUID FK |
| status | Lifecycle |
| created_by | UUID |
| created_on | TIMESTAMPTZ |
| updated_by | UUID |
| updated_on | TIMESTAMPTZ |

Unique: `UNIQUE(user_id, role_id)`.

Indexes:

```text
idx_user_role_user_id
idx_user_role_role_id
```

A user-role assignment is active only when:

```text
user.status = ACTIVE
AND role.status = ACTIVE
AND user_role.status = ACTIVE
```

An inactive relationship does not grant permissions.

Relationship rows are not physically deleted simply to revoke access when historical auditability matters. Prefer `status = INACTIVE`.

---

## 13. RolePermission

Entity: `role_permission`.

Frozen fields:

| Field | Type |
|---|---|
| id | UUID primary key |
| role_id | UUID FK |
| permission_id | UUID FK |
| status | Lifecycle |
| created_by | UUID |
| created_on | TIMESTAMPTZ |
| updated_by | UUID |
| updated_on | TIMESTAMPTZ |

Unique: `UNIQUE(role_id, permission_id)`.

Indexes:

```text
idx_role_permission_role_id
idx_role_permission_permission_id
```

A permission is effective only when:

```text
role.status = ACTIVE
AND permission.status = ACTIVE
AND role_permission.status = ACTIVE
```

Permission resolution does not depend on frontend configuration.

---

## 14. LoginSession

Entity: `login_session`.

Frozen fields:

| Field | Type |
|---|---|
| id | UUID primary key |
| user_id | UUID FK |
| login_time | TIMESTAMPTZ |
| logout_time | TIMESTAMPTZ |
| session_status | ACTIVE / EXPIRED / LOGGED_OUT |
| ip_address | VARCHAR(100) |
| device_info | TEXT |

Indexes:

```text
idx_login_session_user_id
idx_login_session_login_time
idx_login_session_session_status
```

Session lifecycle:

```text
Login
  |
  v
ACTIVE

ACTIVE
  +-- logout  -> LOGGED_OUT
  +-- timeout -> EXPIRED
```

No new session state is introduced in P1.

---

## 15. System actor

A well-known system user represents background and scheduled operations.

```text
UUID:  00000000-0000-0000-0000-000000000001
Email: system@platform.internal
```

The system actor is created through a Flyway migration that seeds the identity if it does not already exist.

Login is blocked for this identity. All background jobs, scheduled tasks, and data migrations use the system actor. Audit attribution reflects this.

Authentication rejects login attempts where:

```text
email = 'system@platform.internal'
OR user.id = '00000000-0000-0000-0000-000000000001'
```

Response is a generic 401.

---

## 16. Authentication flow

Main login flow:

```text
Client
  |
  v
POST /api/v1/auth/login
  |
  v
Validate request
  |
  v
Find user by login identity
  |
  v
Verify user is eligible
  |
  v
Verify password hash
  |
  v
Resolve roles
  |
  v
Create LoginSession
  |
  v
Issue JWT access token
  |
  v
Return authenticated response
```

The exact refresh-token mechanism follows the P1 security decision record.

---

## 17. Login request

```http
POST /api/v1/auth/login
```

Request:

```json
{
  "username": "student@example.com",
  "password": "********"
}
```

The `username` field is the API login identifier.

The backend may resolve the identifier according to the approved identity rules. Arbitrary alternate identifiers are not silently allowed without documenting the behavior.

---

## 18. Login response

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "<uuid>",
      "firstName": "John",
      "lastName": "Doe",
      "roles": ["STUDENT"]
    }
  }
}
```

The response never contains password hashes, password, internal session secrets, permission implementation details, security configuration, or database identifiers beyond intended public UUIDs.

---

## 19. Authentication failure

Invalid authentication returns the standardized authentication error.

The response does not reveal whether the user does not exist, whether the user exists but password is wrong, or whether the user exists but was archived, in a way that enables account enumeration.

---

## 20. Login query discipline

The login operation avoids broad entity loading.

Load only user authentication fields, user status, organization status, and required role relationships.

Do not load attempts, questions, analytics, commerce, or notifications.

---

## 21. Password verification

Password verification occurs through the chosen secure Spring Security password hashing mechanism.

Password text is never compared directly.

Password inputs are never logged.

Test passwords are not placed into production fixtures.

---

## 22. Password hash storage

The database contains `password = secure password hash`.

It never contains plaintext, encrypted-but-reversible passwords, or temporary plaintext passwords.

The implementation uses an adaptive password hashing mechanism appropriate to the approved Spring Security baseline.

---

## 23. Password policy

The source design requires a password policy but does not freeze exact strength rules.

P1 defines and documents:

```text
Minimum length
Maximum length
Disallowed obvious patterns where appropriate
```

without creating a requirement that conflicts with the API contract.

Long secure passwords are not rejected merely because of an arbitrary implementation limit unless required by storage or technical constraints.

---

## 24. Password reset flow

```text
Forgot password
      |
      v
Accept email
      |
      v
Safe generic response
      |
      v
Secure reset workflow
      |
      v
User submits reset token
      |
      v
Validate token
      |
      v
Validate new password
      |
      v
Replace password hash
      |
      v
Apply session and security policy
```

The exact token persistence mechanism is part of the P1 security implementation decision if not already represented by the frozen schema.

Do not introduce a persistent reset-token table without formal review.

---

## 25. Forgot password request

```json
{
  "email": "student@example.com"
}
```

The API response does not reveal whether the account exists.

Frozen semantic:

```text
If the account exists, password reset instructions have been sent.
```

---

## 26. Reset password request

```json
{
  "token": "<reset-token>",
  "newPassword": "********"
}
```

Validation:

```text
Token present
Token valid
Token unexpired
Token unused according to the selected implementation
Password policy satisfied
```

The exact persistence and rotation strategy is documented.

---

## 27. Password reset security

A reset implementation addresses:

- Token confidentiality
- Token expiry
- Single-use semantics
- Brute-force resistance
- Non-enumerating responses
- Session invalidation policy
- Auditability

Do not log reset tokens.

---

## 28. Session invalidation after password reset

The implementation defines whether existing sessions are all invalidated or left valid.

The chosen behavior is explicit and consistent.

Because the schema persists LoginSession records, the implementation uses that session model rather than creating an unrelated authentication-state store without architectural approval.

---

## 29. Logout API

```http
POST /api/v1/auth/logout
```

Requires authentication.

Expected business effect.

```text
Applicable LoginSession
        |
        v
LOGGED_OUT
```

and, as applicable:

```text
logout_time = current timestamp
```

The endpoint is idempotent from the business perspective where repeated logout requests occur.

---

## 30. Logout response

The endpoint uses the standard success envelope.

Repeated logout of an already logged-out session does not create another session or a new state transition.

---

## 31. Refresh token API

```http
POST /api/v1/auth/refresh-token
```

The implementation:

- Validates refresh capability
- Validates applicable session
- Validates user status
- Issues new valid access token
- Applies rotation and revocation rules

The exact representation of the refresh credential is a P1 implementation decision.

---

## 32. Current user API

```http
GET /api/v1/me
```

The endpoint returns the authenticated user's safe profile context.

It does not return password hash, reset tokens, security secrets, full session history, or private audit records.

---

## 33. Current user permission API

```http
GET /api/v1/me/permissions
```

The endpoint returns effective permissions according to active user, active roles, active role-permission assignments, and active permissions.

The frontend may use this for UI visibility. The backend still rechecks permissions.

Example response:

```json
{
  "success": true,
  "message": "Permissions retrieved successfully",
  "data": {
    "permissions": [
      "QUESTION_VIEW",
      "QUESTION_CREATE"
    ]
  }
}
```

---

## 34. Authorization architecture

Authorization occurs through:

```text
HTTP request
      |
      v
Authenticated principal
      |
      v
Required permission
      |
      v
Effective permission resolution
      |
      v
Allow or deny
```

Where business rules require more than a simple permission, permission is combined with resource ownership, resource state, and business rule in the relevant service.

---

## 35. Permission versus business authorization

Example: `QUESTION_PUBLISH` does not by itself mean every QuestionVersion can be published.

The Content service additionally verifies question version state, review status, required fields, and publishing constraints.

P1 provides generic authorization infrastructure without moving downstream business validation into Identity.

---

## 36. Method-level authorization

Use method-level authorization where appropriate at the service or application boundary rather than relying exclusively on controller annotations.

Controllers remain thin.

Business authorization remains in the application or service layer.

---

## 37. Authentication principal

The security principal carries only the minimum runtime identity needed for authorization.

Possible conceptual identity:

```text
userId
sessionId where applicable
roles or authority context
```

Large role and permission graphs do not go into every token.

The JWT does not contain all permissions, all organization configuration, question data, or assessment data.

---

## 38. JWT payload principle

The final claim catalog is minimal.

Sufficient claims:

```text
Subject / user identity
Token identity if required
Issued time
Expiry
Session reference if selected
```

The final claims are documented in the security decision record and OpenAPI security documentation.

---

## 39. JWT expiration

The API example uses `expiresIn = 3600`.

This is an API example and does not lock the security policy. The selected expiration policy is documented, environment-aware where appropriate, and consistent with refresh behavior.

---

## 40. Clock handling

Security timestamps are generated from server time.

Client timestamp, client expiry, and client session state are not trusted.

Timezone-aware server timestamps are used.

---

## 41. Session timeout

The system supports session timeout.

The implementation defines idle timeout semantics, absolute session lifetime if used, what activity updates, and when session becomes EXPIRED.

These details are centralized in SessionService and security infrastructure.

---

## 42. LoginSession activity

The `login_session` schema contains login time, logout time, and session status. It does not contain a last-activity field.

Do not add `last_activity_on`, `refresh_token`, `token_hash`, or `expires_on` to the table without a formal schema change.

Session timeout behavior is implemented with the available model or through a documented schema-change proposal.

---

## 43. Session status enforcement

The backend determines whether a session is ACTIVE, EXPIRED, or LOGGED_OUT and uses that status appropriately in authentication and session operations.

---

## 44. Session listing API

```http
GET /api/v1/login-sessions
GET /api/v1/login-sessions/{id}
```

The authenticated student sees only sessions they are permitted to view.

Administrative users may see broader session information according to permissions.

---

## 45. Session data exposure

Login session APIs do not expose password, JWT access token, refresh token, or internal cryptographic material.

Safe operational metadata such as login time, logout time, status, IP address, and device information is exposed according to privacy and security requirements.

---

## 46. IP address handling

The `ip_address` field is part of the frozen schema.

The application records the correct client or network source according to the trusted reverse-proxy configuration.

Arbitrary forwarding headers from an untrusted Internet client are not blindly trusted.

Nginx establishes the trusted proxy boundary.

---

## 47. Device information

`device_info` is free-form text in the frozen schema.

P1 stores sanitized, bounded device information.

Full request headers, authentication tokens, cookies, and passwords are not stored inside `device_info`.

---

## 48. Login audit

Successful and security-significant authentication events are auditable.

Minimum logical events:

```text
LOGIN_SUCCESS
LOGIN_FAILURE where appropriate
LOGOUT
PASSWORD_RESET_REQUEST
PASSWORD_RESET_SUCCESS
SESSION_EXPIRED
```

The `audit_log` schema is immutable.

P1 uses the existing audit mechanism rather than inventing a second audit table.

Audit insertion participates in the business transaction. Audit failure rolls back the business mutation.

---

## 49. Audit event content

Audit records identify the operation without storing sensitive authentication material.

Audit records do not store password, JWT, reset token, refresh token, authorization header, or full request body.

---

## 50. Audit failure handling

Security audit events are not silently lost.

The chosen behavior is documented before implementation of security-critical audit operations.

Default policy: audit insertion participates in the transaction and failure rolls back the business mutation.

---

## 51. Role management APIs

```http
GET    /api/v1/roles
POST   /api/v1/roles
PUT    /api/v1/roles/{id}
GET    /api/v1/roles/{id}/permissions
PUT    /api/v1/roles/{id}/permissions
```

The implementation enforces permissions on these endpoints.

---

## 52. Permission management API

```http
GET /api/v1/permissions
```

This is primarily a controlled permission catalog endpoint.

An unrestricted API for arbitrary permission mutation is not exposed unless explicitly present in the API contract.

---

## 53. User management APIs

```http
GET    /api/v1/users
GET    /api/v1/users/{id}
POST   /api/v1/users
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}
```

DELETE represents archive or deactivation according to the API contract. It does not become uncontrolled physical deletion.

---

## 54. User list API

Filters:

```text
status
roleId
search
```

Pagination is required.

Sort behavior follows the standard API contract.

The query avoids loading all users into application memory.

---

## 55. User search

Search is implemented through indexed and database-backed queries.

Do not load every user and then filter in Java for production paths.

The database performs the filtering.

---

## 56. User creation

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "mobile": "9999999999",
  "roles": ["<role-uuid>"]
}
```

The service:

```text
Validates organization context
Validates duplicate email
Validates duplicate mobile according to schema rules
Validates requested roles
Creates user
Hashes password according to onboarding policy
Creates role assignments
Audits
```

The exact onboarding-password flow is an implementation decision because the source API notes that password handling depends on the final onboarding flow.

---

## 57. User update

Only permitted profile and account fields may be changed.

The service does not allow a generic payload to mutate id, organization_id, password hash, status, or audit fields unless the operation explicitly represents that action.

---

## 58. Password change versus user update

Password changes are not hidden inside generic `PUT /api/v1/users/{id}` unless the API contract explicitly defines that behavior.

Use the frozen authentication and reset APIs for password recovery.

If an authenticated password-change operation is required later, it receives its own explicit API design.

---

## 59. User archive

DELETE is semantically archive or deactivate.

The business effect uses `status = ARCHIVED` or the approved deactivation behavior.

LoginSession, AuditLog, and historical attempts are not cascade-deleted.

---

## 60. Role creation

Role creation requires:

```text
Unique code
Name
Description
Status
Audit metadata
```

The service normalizes role codes to the project's agreed format. Aliases that cause authorization ambiguity are not silently created.

---

## 61. Role permission replacement

```http
PUT /api/v1/roles/{id}/permissions
```

The operation is a controlled replacement of the role's effective permission assignments.

Transaction:

```text
Load role
 |
 v
Validate role state
 |
 v
Validate permission IDs
 |
 v
Update role-permission relationships
 |
 v
Audit
 |
 v
Commit
```

Partially updated permission state is not exposed if the transaction fails.

---

## 62. Permission replacement safety

The implementation avoids a state in which permissions are partially applied because one invalid permission causes a mid-operation failure.

Use one transactional business operation.

---

## 63. Privileged role change policy

Rules:

| Rule | Enforcement |
|---|---|
| A user cannot assign themselves a role | 403 SELF_ROLE_MODIFICATION |
| A user cannot modify their own permissions | 403 SELF_PERMISSION_MODIFICATION |
| ADMIN assignment requires `ADMIN_ROLE_ASSIGN` | Backend permission check |
| Removing the last active ADMIN is blocked | 409 LAST_ADMIN |
| Assigning ADMIN to a STUDENT-only user requires `ADMIN_ROLE_ASSIGN` | Backend permission check |
| All role and permission changes are audited | audit_log entry |
| Two-person approval | Not required |

Last admin query:

```sql
SELECT COUNT(*) FROM user_role ur
JOIN role r ON r.id = ur.role_id
WHERE r.code = 'ADMIN' AND ur.status = 'ACTIVE';
```

Audit requirements:

```text
action        = ROLE_ASSIGN or ROLE_REVOKE
entity_name   = UserRole
entity_id     = target user id
remarks       = "Added role ADMIN (<role-uuid>)" or similar
```

Emergency path: if all admins are locked out (should not be possible given the last-admin rule), recovery is a documented direct database procedure operated by a DBA under change control, not a UI feature.

---

## 64. Default role seed data

Initial role concepts:

```text
ADMIN
REVIEWER
STAFF
STUDENT
SUPPORT
FINANCE
```

Seed these through Flyway or the approved master-data mechanism.

Seed operations are idempotent or protected by unique constraints.

---

## 65. Permission seed strategy

Seed only the permissions necessary for P1 Identity plus the minimum foundational permissions required by the next immediate feature slice.

Do not seed the entire future platform permission catalog without a clear requirement.

Suggested initial identity permissions:

```text
USER_VIEW
USER_CREATE
USER_UPDATE
USER_ARCHIVE

ROLE_VIEW
ROLE_CREATE
ROLE_UPDATE
ROLE_PERMISSION_VIEW
ROLE_PERMISSION_ASSIGN

PERMISSION_VIEW

SESSION_VIEW
```

Plus reserved-but-not-granted admin permissions:

```text
ADMIN_ROLE_ASSIGN
```

---

## 66. Permission mapping strategy

Seed role assignments through explicit data.

Example mapping:

```text
ADMIN
    -> identity management permissions

STUDENT
    -> self-service session and current-user permissions only

REVIEWER
    -> identity permissions only if explicitly assigned
```

Do not assume a role receives permissions merely because its name sounds privileged.

---

## 67. Current user versus admin user access

Two different categories:

```text
Self-service
```

and:

```text
Administrative management
```

Example: `GET /api/v1/me` is self-context.

Example: `GET /api/v1/users` is broader identity management.

Do not accidentally grant all users the broader endpoint because both are in the Identity module.

---

## 68. Authorization matrix

| Resource | Operation | Required permission |
|---|---|---|
| `/me` | GET | authenticated user |
| `/me/permissions` | GET | authenticated user |
| users | LIST | USER_VIEW |
| users | GET | USER_VIEW |
| users | CREATE | USER_CREATE |
| users | UPDATE | USER_UPDATE |
| users | ARCHIVE | USER_ARCHIVE |
| roles | LIST | ROLE_VIEW |
| roles | CREATE | ROLE_CREATE |
| roles | UPDATE | ROLE_UPDATE |
| role permissions | GET | ROLE_PERMISSION_VIEW |
| role permissions | PUT | ROLE_PERMISSION_ASSIGN |
| permissions | GET | PERMISSION_VIEW |
| sessions | LIST | SESSION_VIEW |
| sessions | GET | SESSION_VIEW |

The actual permission codes remain aligned with seeded master data.

---

## 69. Authorization decision service

Create a focused service such as `AuthorizationService`.

Responsibilities:

```text
hasPermission(userId, permission)
requirePermission(userId, permission)
```

It does not own question validation, assessment rules, payment logic, or evaluation.

---

## 70. Permission resolution queries

The effective permission query conceptually joins:

```text
user
 |
 v
user_role
 |
 v
role
 |
 v
role_permission
 |
 v
permission
```

with active-status filtering.

The implementation returns distinct permission codes.

Avoid N+1 query behavior.

---

## 71. Permission caching

The technical design permits caching roles and permissions in Redis.

Redis is cache only. Authorization has a reliable PostgreSQL-backed fallback.

P1 defines cache invalidation when:

```text
UserRole changes
RolePermission changes
Role status changes
Permission status changes
User status changes
```

---

## 72. Cache invalidation rule

After a permission-affecting transaction commits:

```text
Database commit
    |
    v
Invalidate affected permission cache
```

Cache invalidation does not precede a successful transaction commit.

---

## 73. Security-critical cache rule

A cache outage does not grant access.

Safe failure mode:

```text
Redis unavailable
    |
    v
Resolve authorization from PostgreSQL
```

Never:

```text
Redis unavailable
    |
    v
Assume allow
```

---

## 74. Role and permission cache freshness

The implementation prefers correctness over cache freshness.

If invalidation cannot be guaranteed, the application temporarily falls back to authoritative database resolution rather than risking stale privilege grants.

---

## 75. JWT and permission cache separation

The JWT does not become the permanent authority for mutable permissions.

A user's permissions may change after the JWT is issued.

The application has a defined strategy for reconciling token identity with current permissions.

---

## 76. Permission changes and existing JWTs

When a role or permission changes:

```text
Existing access token
        |
        v
Still identifies the user
```

But authorization reflects current effective permissions according to the selected implementation.

Permissions are not assumed to be baked into the JWT forever.

---

## 77. Organization inactive security

If the organization is inactive, the implementation prevents affected users from continuing to access protected business functions where organizational activation is a prerequisite.

Handling is centralized in the identity and security context.

---

## 78. Cross-module authorization contract

Future modules ask the AuthorizationService or an equivalent explicit identity service contract.

They do not query `role_permission`, `permission`, or `user_role` directly from their repositories.

This preserves module boundaries.

---

## 79. Identity module package structure

```text
identity/
  controller/
  service/
  repository/
  entity/
  dto/
  mapper/
  validation/
  exception/
  security/
```

Responsibilities:

```text
controller    -> HTTP boundary
service       -> business workflows
repository    -> persistence
dto           -> API contracts
mapper        -> entity and DTO conversions
validation    -> input and business validation helpers
security      -> identity-specific security adapters
```

---

## 80. Security package boundary

Global Spring Security infrastructure belongs under `config/security`.

Identity-specific operations such as AuthenticationService, AuthorizationService, and SessionService belong to Identity.

All domain security logic is not moved into a giant global SecurityService.

---

## 81. Authentication service

Responsibilities:

```text
Authenticate
Issue access credential
Establish LoginSession
Refresh according to selected policy
Logout according to selected policy
```

It coordinates but does not directly expose repository internals to controllers.

---

## 82. User service

Responsibilities:

```text
Create
Read
Update
Archive
Resolve current user
```

It does not generate JWTs.

---

## 83. Role service

Responsibilities:

```text
List
Create
Update
Replace permissions
```

It coordinates RolePermission transactions.

---

## 84. Permission service

Responsibilities:

```text
List controlled permission catalog
Resolve effective permission codes where useful
```

It does not expose raw persistence details to controllers.

---

## 85. Session service

Responsibilities:

```text
Create LoginSession
Resolve session
Logout
Expire
List
Get detail
```

It owns session state transitions.

---

## 86. Security context resolver

Create a focused component that translates authenticated credentials into the application security principal.

It does not load the entire User aggregate graph on every request.

---

## 87. Authentication middleware

The authentication filter or interceptor:

```text
Extracts Bearer token
 |
 v
Validates token
 |
 v
Resolves principal identity
 |
 v
Attaches SecurityContext
```

Authorization is a separate concern.

---

## 88. Bearer token handling

Only the `Authorization: Bearer <JWT>` pattern is approved for protected APIs.

A second undocumented authentication scheme is not accepted merely for convenience.

---

## 89. Invalid token handling

Return 401 `AUTHENTICATION_ERROR`.

Do not reveal signature mismatch details, expired exact timestamp, or internal cryptographic configuration to an untrusted client.

Detailed reasons belong in sanitized security logs.

---

## 90. Missing token handling

A protected API without credentials returns 401, not 403, unless the framework or security policy deliberately maps the condition otherwise.

The project standardizes one behavior.

---

## 91. Forbidden handling

A valid authenticated user without permission receives 403 `AUTHORIZATION_ERROR`.

The frontend may use the error to display a safe access-denied state.

---

## 92. Error response consistency

Authentication and authorization failures use the same standard API error envelope established in P0.

Framework-generated HTML error pages are not returned from JSON APIs.

---

## 93. Security logging

Security logs capture requestId, userId when known, operation, success or failure, reason category, timestamp, and source network metadata where appropriate.

Credentials and secrets are not logged.

---

## 94. Login failure logging

A failed login is logged as a security event.

The event does not store password or the full authentication request.

Sanitized metadata is used.

---

## 95. Rate limiting

The API contract requires a 429 `RATE_LIMITED` response category but does not freeze a specific identity rate-limiter implementation.

P1 establishes rate-limiting policy for sensitive endpoints at the infrastructure or application layer where practical, especially:

```text
login
forgot-password
reset-password
```

The exact algorithm and thresholds require implementation and security review.

---

## 96. Brute-force protection

P1 ensures repeated authentication failures cannot trivially become an unlimited password-guessing channel.

Possible mechanisms:

```text
Rate limiting
Progressive throttling
Temporary controls
```

The chosen mechanism is documented and does not create an account-enumeration side channel.

---

## 97. CORS and identity

P0 created the CORS boundary.

P1 verifies allowed frontend origins, credential behavior, Authorization header support, and preflight behavior.

Broad wildcard production configuration is not used.

---

## 98. CSRF boundary

The P0 decision record established the JWT transport and CSRF interaction.

P1 implements the selected strategy consistently.

Authentication state is not partly cookie-based and partly bearer-based without documenting the security implications.

---

## 99. Browser storage of access token

The source documents do not prescribe the exact browser storage mechanism.

The implementation selects and documents a strategy considering XSS exposure, token persistence, refresh behavior, logout behavior, and multi-tab behavior.

This decision is not made in one frontend component and duplicated elsewhere.

---

## 100. Frontend authentication state

The frontend treats authentication state as:

```text
authenticated
unauthenticated
loading
error
```

It obtains current-user data from the backend.

It does not reconstruct effective permissions from local role labels alone.

---

## 101. Frontend permission handling

Frontend permissions are for UI behavior only.

Example:

```tsx
if (can("QUESTION_CREATE")) {
    showCreateButton();
}
```

But the backend independently enforces `QUESTION_CREATE` on the API.

---

## 102. No frontend security boundary

Hidden button is not protected operation.

The API remains authoritative.

---

## 103. Protected route behavior

Protected frontend routes:

```text
Check authentication state
    |
    v
Load current user
    |
    v
Render or redirect
```

This is UX behavior. Backend remains the security boundary.

---

## 104. Login screen

P1 frontend implements the login surface required by the API contract.

It includes username or email input, password input, submit, loading, invalid-credential state, generic failure state, and forgot-password entry.

Detailed account-enumeration errors are not exposed.

---

## 105. Logout UX

Logout:

```text
Call backend logout
Clear local auth context
Clear relevant cached user data
Redirect to login or public state
```

The frontend does not simply delete a token locally without notifying the backend.

---

## 106. Forgot password UX

The frontend always presents the generic outcome:

```text
If the account exists, reset instructions have been sent.
```

This remains consistent regardless of account existence.

---

## 107. Reset password UX

The frontend provides reset token, new password, confirmation if desired, validation feedback, success state, and failure state.

The reset token is not logged in browser analytics.

---

## 108. Current user query

The frontend centrally queries `GET /api/v1/me` rather than duplicating user state across unrelated components.

---

## 109. Current permission query

The frontend may query `GET /api/v1/me/permissions` and expose a helper `can(permissionCode)`.

This helper is a UI affordance only.

---

## 110. Query cache invalidation

On logout:

```text
Clear current-user cache
Clear permission cache
Clear authenticated-user queries
```

Do not clear unrelated public caches unnecessarily.

---

## 111. User role UI

P1 does not need a complete administration UI.

P1 does need permission-aware UI infrastructure so later modules can safely add staff, reviewer, admin, support, and finance views.

---

## 112. Identity API DTOs

Create explicit DTOs for:

```text
LoginRequest
LoginResponse
UserSummary
UserDetail
CreateUserRequest
UpdateUserRequest
RoleSummary
CreateRoleRequest
UpdateRoleRequest
PermissionSummary
LoginSessionSummary
LoginSessionDetail
```

The exact fields follow the API contract and schema.

JPA entities are not exposed.

---

## 113. UserSummary versus UserDetail

List endpoints return smaller data.

UserSummary contains id, firstName, lastName, email, mobile, status, and roles.

Detail endpoints may return additional safe profile context.

Passwords and security secrets are never exposed.

---

## 114. Role DTOs

Role DTOs expose id, code, name, description, and status.

Role permission APIs expose effective permission catalog information without exposing persistence internals.

---

## 115. Login session DTOs

Safe fields may include id, loginTime, logoutTime, sessionStatus, ipAddress, and deviceInfo.

Authentication tokens are not exposed.

---

## 116. Validation rules

P1 DTO validation covers required values, field length, email format, mobile format where required, role identifiers, and permission identifiers.

Complex business rules remain in services.

---

## 117. Email normalization

The schema uses unique email.

The implementation defines whether emails are normalized to lowercase for identity matching.

The normalization strategy is documented because `John@example.com` and `john@example.com` must not unexpectedly become two identities if normalization is intended.

Stored data semantics are not changed casually.

---

## 118. Mobile normalization

The frozen schema uses unique mobile.

P1 documents the chosen normalization behavior for the supported input format.

Multiple country formats are not silently supported without defining identity semantics.

---

## 119. Duplicate user prevention

Database level:

```text
UNIQUE(email)
UNIQUE(mobile)
```

Application level: pre-validation plus database constraint.

Race conditions are safely translated into 409 CONFLICT rather than an internal database exception.

---

## 120. Role code uniqueness

Database: `UNIQUE(code)`.

Application: validate before insert.

If a race creates a uniqueness collision, convert it to a controlled conflict response.

---

## 121. Permission code uniqueness

Same pattern.

---

## 122. UserRole uniqueness

Database protects `UNIQUE(user_id, role_id)`.

A duplicate assignment results in a controlled business conflict rather than duplicated authorization.

---

## 123. RolePermission uniqueness

Database protects `UNIQUE(role_id, permission_id)`.

Replacing a role's permission set operates transactionally.

---

## 124. User creation transaction

```text
Validate request
    |
    v
Validate organization
    |
    v
Validate roles
    |
    v
Create user
    |
    v
Create role assignments
    |
    v
Audit
    |
    v
Commit
```

If any required operation fails, the transaction rolls back. No partially created user remains.

---

## 125. Role-permission transaction

```text
Load role
 |
 v
Validate target permissions
 |
 v
Replace relationship state
 |
 v
Audit
 |
 v
Commit
 |
 v
Invalidate authorization cache
```

Cache invalidation does not precede a failed transaction's state.

---

## 126. Login transaction

Login is designed carefully because token and session issuance crosses authentication, database session record, and credential response.

The exact ordering and failure semantics are documented in the AuthenticationService implementation.

---

## 127. Login session creation failure

If authentication succeeds but the LoginSession cannot be safely created, the service does not return a partially established authenticated state without a defined policy.

Default principle: security state incomplete means do not issue a business-valid session.

The exact implementation is tested.

---

## 128. Logout idempotency

Repeated `POST /api/v1/auth/logout` does not create duplicate state changes.

```text
ACTIVE     -> LOGGED_OUT
LOGGED_OUT -> LOGGED_OUT
```

Logout does not create a new session.

---

## 129. Refresh concurrency

The selected refresh-token strategy defines behavior for two concurrent refresh requests.

The implementation prevents unexpected token or session duplication.

This is part of the P1 security test suite.

---

## 130. Password reset concurrency

A reset token intended as single-use does not succeed twice under concurrent requests.

The chosen implementation provides atomic single-use semantics.

---

## 131. Session concurrency

Concurrent logout and timeout requests result in one valid terminal session state.

The system does not allow:

```text
ACTIVE -> LOGGED_OUT -> ACTIVE
```

through race conditions.

---

## 132. Security transactions

Security-critical state changes are transactional:

```text
Role assignment
Permission assignment
Logout session state
Password reset
User archive
```

Service-level transaction boundaries are used.

---

## 133. Session repository queries

Required access patterns include:

```text
findByUserId
findBySessionStatus
findById
```

and combinations needed for secure authorization and session operations.

Use the approved indexes:

```text
user_id
login_time
session_status
```

Additional indexes require query justification.

---

## 134. Authorization query performance

The effective-permission query is a high-frequency security path.

It is measured.

The implementation uses joins and projections rather than loading complete role and permission object graphs.

---

## 135. N+1 prevention

Do not implement user to user roles to per-role permissions through unbounded lazy iteration.

Prefer controlled queries or projections.

---

## 136. Identity API pagination

All collection endpoints that can grow use standard pagination.

At minimum:

```text
users
roles
permissions where applicable
login-sessions
```

Unbounded lists are not returned.

---

## 137. Sorting

Use controlled sort fields.

Arbitrary database column names from the client are not passed directly to query construction.

Create an allowlist of sortable fields per endpoint.

---

## 138. Filtering

Filters are validated.

Client filter input is not converted into arbitrary SQL.

Repository and query abstractions are used.

---

## 139. Search security

Search values are data.

SQL is never constructed via string concatenation.

Parameterized queries and JPA specifications or equivalent safe query mechanisms are used.

---

## 140. API rate limiting

The API foundation provides 429 `RATE_LIMITED`.

P1 applies it to high-risk authentication endpoints according to the security design.

The exact thresholds are environment and security configuration, not hard-coded in controllers.

---

## 141. Security headers

The infrastructure establishes appropriate security headers through the approved Nginx and Spring Security boundary.

P1 verifies HTTPS, content-type handling, frame restrictions where appropriate, and referrer policy where appropriate.

Security headers that break legitimate exam delivery are not introduced without testing.

---

## 142. Clickjacking consideration

Authenticated assessment and admin surfaces are not casually embeddable in arbitrary external frames.

The chosen policy is compatible with the frontend deployment.

---

## 143. HTTPS

Production authentication travels over HTTPS.

Credentials and JWTs are not allowed over unsecured production HTTP.

Local development may use HTTP according to the environment strategy.

---

## 144. Password reset links

Reset links use the configured trusted frontend origin.

Links are not generated from an unvalidated request Host header.

---

## 145. Security configuration source

Security configuration is environment-driven.

Examples:

```text
JWT issuer and configuration
JWT verification secret or public key strategy
Allowed frontend origins
Password policy
Rate limits
Session policy
```

Production keys are not hardcoded in source.

---

## 146. Secret management

Secrets include JWT signing material, database password, Redis password if any, and mail credentials if later used.

P1 ensures none are committed.

---

## 147. JWT secret rotation

The source does not freeze a key-rotation strategy.

P1 does not invent a full key rotation system.

The operational requirement is documented as a future hardening item if the selected deployment model does not yet provide it.

---

## 148. Authentication and Redis

Redis remains cache-only.

Do not introduce a session blacklist cache and call it the source of truth.

If Redis participates in authentication performance, PostgreSQL remains the authoritative transactional state.

---

## 149. Redis failure during authorization

Safe behavior:

```text
Redis unavailable
   |
   v
Database-backed permission resolution
```

Unsafe behavior:

```text
Redis unavailable
   |
   v
Allow
```

---

## 150. Redis failure during login

If Redis is used only as optional cache, login continues through the authoritative database path.

Identity correctness is not dependent on cache availability.

---

## 151. Organization context

Even though the deployment is single-organization, every authenticated user belongs to an organization through `organization_id`.

P1 establishes a controlled organization context abstraction.

It does not become an accidental multi-tenant security layer.

---

## 152. Organization context use

Future services may ask for `CurrentUserContext` and `CurrentOrganizationContext`, but do not bypass Identity repositories.

The current deployment expects one organization to be active.

---

## 153. Future multi-organization compatibility

P1 does not implement multi-tenant routing, tenant database isolation, tenant-specific database schemas, RLS tenant policy, or a SaaS control plane.

However, P1 avoids source code assumptions such as `ORGANIZATION_NAME = "ABC"` that would make later expansion unnecessarily difficult.

---

## 154. Frontend auth module structure

```text
frontend/features/auth/
    components/
    hooks/
    api/
    schemas/
    types/
```

Shared authenticated context:

```text
frontend/lib/auth/
```

---

## 155. Frontend auth API layer

Create explicit API functions:

```text
login()
logout()
refreshToken()
forgotPassword()
resetPassword()
getCurrentUser()
getCurrentPermissions()
```

Axios calls are not scattered throughout page components.

---

## 156. Frontend auth state

The auth state contains safe runtime information such as authenticated flag, current user, and effective UI permissions.

Raw token handling logic is not duplicated across components.

---

## 157. Frontend token handling

Centralize token handling in one location.

The agent does not create token in localStorage plus token in sessionStorage plus token in cookies simultaneously unless the security design explicitly requires it.

One documented strategy is preferred.

---

## 158. Axios 401 handling

The API client handles 401 through the defined authentication flow.

Do not blindly retry every failed request.

Avoid automatic retry loops around `POST /logout` and `POST /reset-password`.

---

## 159. Refresh coordination

If refresh tokens are used, multiple simultaneous 401 responses do not trigger uncontrolled concurrent refresh calls.

The frontend centralizes refresh coordination.

The exact mechanism depends on the final refresh-token design.

---

## 160. Logout during refresh race

The auth layer defines behavior when logout occurs while a refresh request is in flight.

The expected result is deterministic and safe.

---

## 161. Permission helper

Create a frontend helper conceptually `can("QUESTION_CREATE")`.

It returns whether the current UI permission context contains the capability.

It is never treated as server authorization.

---

## 162. Protected UI

Later business modules may use:

```tsx
{can("QUESTION_CREATE") && <CreateQuestionButton />}
```

This is correct as a UX pattern. The corresponding API still performs backend authorization.

---

## 163. Login loading state

The UI prevents accidental multiple login submissions.

The backend also tolerates duplicate login requests safely.

---

## 164. Login error UX

The UI uses a safe generic authentication message.

Raw server or database exceptions are not displayed.

---

## 165. Logout error UX

Even when backend logout fails because the session is already invalid, the frontend safely clears its local authentication state according to the security design.

The operation does not leave the browser appearing authenticated when the server has determined the session is no longer valid.

---

## 166. Forgot password anti-enumeration

The frontend presents the same general result for known email and unknown email.

`User not found` is not shown.

---

## 167. Reset password token handling

Reset tokens are never written to application analytics, console logs, client telemetry, or error reports.

---

## 168. Security test categories

P1 includes authentication, authorization, password handling, session handling, enumeration resistance, rate limiting, input validation, CORS and CSRF, secret leakage, audit, and concurrency tests.

---

## 169. Authentication tests

Test valid credentials, invalid credentials, inactive user, archived user, inactive organization, missing token, malformed token, expired token, and system actor login rejection.

---

## 170. Authorization tests

Test authenticated but no permission, correct permission, inactive role, inactive permission, inactive user_role, and inactive role_permission.

---

## 171. User API authorization tests

Verify student cannot list all users, student cannot create users, student cannot modify roles, unauthorized user receives 403, and authorized administrative user receives expected response.

---

## 172. Self-service tests

Verify `GET /me`, `GET /me/permissions`, and logout work for authenticated users without granting unrelated administrative permissions.

---

## 173. Password tests

Test password hash created, plaintext never persisted, wrong password rejected, password reset accepted with valid token, invalid token rejected, expired token rejected, and reuse rejected if single-use is selected.

---

## 174. Session tests

Test login creates session, logout changes state, expired session is not treated as active, repeated logout is safe, and multiple sessions handled according to policy.

---

## 175. Account enumeration tests

Test forgot-password existing email, forgot-password unknown email, login unknown user, and login wrong password.

Responses do not materially reveal account existence where prohibited.

---

## 176. Role and permission transaction tests

Test valid assignment, duplicate assignment, invalid permission, partial failure rollback, and cache invalidation after commit.

---

## 177. Concurrency tests

P1 includes concurrent tests for duplicate user creation, duplicate role assignment, duplicate permission assignment, concurrent logout, and concurrent role-permission replacement.

The database constraints prevent invalid duplicates even under race conditions.

---

## 178. API tests

For each Identity API test success, validation failure, 401, 403, 404, 409, invalid state, pagination where applicable, filtering where applicable, and sorting where applicable.

---

## 179. Repository and database tests

Verify foreign keys, unique constraints, indexes, status handling, Flyway migrations, and transaction rollback.

---

## 180. Integration test environment

Use the P0 integration infrastructure.

Prefer PostgreSQL-compatible real integration tests rather than mocking database behavior when testing unique constraints, foreign keys, transactions, and query behavior.

---

## 181. Security test data

Use clearly synthetic test accounts.

Production passwords and real user credentials are never used in tests.

---

## 182. Frontend tests

P1 frontend tests cover login form, login validation, loading state, generic authentication error, logout, forgot-password generic state, reset-password validation, protected route, permission-aware UI, and 401 handling.

---

## 183. End-to-end authentication flow

At minimum:

```text
Open login
 |
 v
Login
 |
 v
Current user loads
 |
 v
Protected page opens
 |
 v
Logout
 |
 v
Protected page redirects or denies
```

---

## 184. End-to-end password reset flow

Where mail delivery is not yet implemented, use a controlled test mechanism.

Do not build the production email platform in P1 merely to test reset flow.

The test still validates the backend reset contract.

---

## 185. Audit tests

Verify security events are written with correct actor, correct action, and correct target where applicable, and without secrets.

---

## 186. Security regression corpus

Create `docs/testing/security/p1/` with scenarios for authentication, authorization, session lifecycle, reset, enumeration, and concurrency.

---

## 187. Performance expectations

P1 identity APIs contribute to the later system-wide target of concurrent users and applicable p95 under 100 ms.

P1 does not claim the final load target is met.

It ensures identity queries and indexes are designed for later scale testing.

---

## 188. Login performance

Measure authentication query time, permission resolution time, session creation time, and total login API latency.

Password security is not weakened for optimization.

---

## 189. Permission performance

The authorization path is called frequently.

Measure database-backed resolution, cached resolution, cache miss, and cache invalidation.

Cache hit performance is measured, not assumed.

---

## 190. Database index review

P1 verifies existing Identity indexes:

```text
idx_user_organization_id
uk_user_email
uk_user_mobile

idx_user_role_user_id
idx_user_role_role_id

idx_role_permission_role_id
idx_role_permission_permission_id

idx_login_session_user_id
idx_login_session_login_time
idx_login_session_session_status
```

Additional indexes are added only after query-plan evidence.

---

## 191. Query plan discipline

For important Identity queries (user lookup, role resolution, permission resolution, session lookup), capture representative query plans in engineering notes when tuning is required.

---

## 192. Nginx and auth boundary

Nginx may handle TLS, routing, request limits, and request ID forwarding.

Spring Security remains responsible for JWT, authentication, and authorization.

Identity business logic is not moved into Nginx.

---

## 193. Observability

Identity metrics include login success count, login failure count, logout count, refresh count where implemented, password-reset request count, password-reset success and failure count, 401 count, and 403 count.

Credentials are not logged.

---

## 194. Security metrics

Track suspicious operational patterns such as repeated authentication failures, unusual 401 and 403 spikes, and reset attempts.

Thresholding and alerting policy is added during hardening.

---

## 195. Sensitive data masking

Logs and errors mask email where policy requires, mobile where policy requires, password, JWT, reset token, and refresh token.

The masking policy is documented rather than randomly implemented by individual services.

---

## 196. Audit versus log

Application and security log is operational diagnostic data.

AuditLog is immutable business and security history.

Ordinary logs do not replace the audit table.

---

## 197. Identity documentation

P1 updates `docs/implementation-status.md` and documents P1 completion, authentication status, authorization status, test counts, security decisions, and known limitations.

---

## 198. Architecture decision records

P1 creates or updates decision records for authentication transport, JWT claim policy, refresh-token strategy, session and revocation strategy, password hashing policy, password reset strategy, authorization resolution strategy, permission cache strategy, system actor identity, and privileged role change policy.

Any decision record that requires a frozen schema change identifies that explicitly.

---

## 199. Security review gate

A second, read-oriented security review inspects authentication filter, JWT validation, authorization, session state, password reset, user archive, role-permission replacement, and frontend token handling.

The reviewer does not silently change architecture.

---

## 200. Implementation rules for P1

The implementation:

- Reads P0
- Reads the Identity source sections
- Inspects existing repository code
- Implements one bounded task at a time
- Runs tests after each task
- Reviews diff
- Commits coherently

It does not:

- Invent an auth datastore
- Invent token tables
- Invent 2FA
- Invent SSO
- Invent multi-tenancy
- Expose passwords
- Put permissions into arbitrary JWT blobs
- Bypass backend authorization
- Modify frozen API paths silently

---

## 201. P1 task breakdown

```text
P1-01  Identity Flyway migration
P1-02  Organization entity and repository
P1-03  User entity and repository
P1-04  Role and Permission entities and repositories
P1-05  UserRole and RolePermission
P1-06  LoginSession
P1-07  Identity services
P1-08  Password hashing
P1-09  JWT authentication
P1-10  Login
P1-11  Logout
P1-12  Current user
P1-13  Permissions
P1-14  Users API
P1-15  Roles API
P1-16  Sessions API
P1-17  Forgot password
P1-18  Reset password
P1-19  Authorization tests
P1-20  Frontend authentication
P1-21  End-to-end authentication
P1-22  Security review
P1-23  P1 acceptance
```

Do not give all tasks to an implementation agent in one unbounded request.

---

## 202. P1-01 Identity migration

Create the Identity schema migration from the frozen database specification.

Tables:

```text
organization
user
role
permission
user_role
role_permission
login_session
```

Plus seeds for roles, permissions, and the system actor.

Migration includes primary keys, foreign keys, unique constraints, required indexes, and required not-null constraints.

Migration ordering respects foreign keys:

```text
organization
    |
    v
user
    |
    v
role
permission
    |
    v
user_role
role_permission
    |
    v
login_session
```

---

## 203. P1-02 Organization

Implement entity, repository, service, and DTO foundation if needed.

No organization CRUD UI is required in P1.

---

## 204. P1-03 User

Implement entity, repository, service, mapper, DTOs, and validation.

Add database integration tests.

---

## 205. P1-04 Role and Permission

Implement Role, Permission, repositories, services, and DTOs.

Keep role and permission definitions separate.

---

## 206. P1-05 Relationship entities

Implement UserRole and RolePermission with proper unique constraints and transaction-safe operations.

---

## 207. P1-06 LoginSession

Implement LoginSession, SessionService, repository, and state transitions.

Do not add fields beyond the frozen schema without review.

---

## 208. P1-07 Identity services

Build UserService, RoleService, PermissionService, SessionService, AuthorizationService, and AuthenticationService.

Keep responsibilities focused.

---

## 209. P1-08 Password hashing

Wire the secure Spring Security password encoder.

Test raw password to hash to verify.

Never persist raw input.

---

## 210. P1-09 JWT security

Implement JWT decoder and validator, authentication filter, SecurityContext, and 401 and 403 handlers.

The exact signing and verification configuration is documented.

---

## 211. P1-10 Login

Implement `POST /api/v1/auth/login` with full integration testing.

---

## 212. P1-11 Logout

Implement `POST /api/v1/auth/logout` using LoginSession state.

The token and session interaction follows the approved security decision record.

---

## 213. P1-12 Current user

Implement `GET /api/v1/me` with safe user DTO only.

---

## 214. P1-13 Permissions

Implement `GET /api/v1/me/permissions` and the backend authorization service.

---

## 215. P1-14 Users API

Implement list users, get user, create user, update user, and archive user with permission enforcement.

---

## 216. P1-15 Roles API

Implement list roles, create role, update role, get role permissions, and replace role permissions with transactional permission replacement.

---

## 217. P1-16 Sessions API

Implement list login sessions and get login session by id with authorization.

A normal student does not automatically receive other users' session data.

---

## 218. P1-17 Forgot password

Implement `POST /api/v1/auth/forgot-password` with non-enumerating response.

The delivery mechanism may be represented through the communication boundary only when available.

The full EmailTemplate and notification platform is not built in P1.

---

## 219. P1-18 Reset password

Implement `POST /api/v1/auth/reset-password` with secure reset-token lifecycle.

The exact persistence mechanism follows the security decision record and does not silently add schema.

---

## 220. P1-19 Authorization tests

Build a complete Identity authorization matrix.

---

## 221. P1-20 Frontend authentication

Implement login, logout, forgot password, reset password, current user, permissions, and protected routes.

---

## 222. P1-21 End-to-end

At minimum:

```text
Login -> protected page -> logout
```

and:

```text
Forgot password -> reset -> login
```

using a controlled test mechanism.

---

## 223. P1-22 Security review

Second review pass is read-only.

---

## 224. P1-23 Acceptance

Run backend clean verify, frontend lint, frontend type-check, frontend test, frontend build, integration tests, security tests, migration validation, and end-to-end authentication.

---

## 225. P1 acceptance criteria

P1 passes only when:

```text
[ ] Identity migrations applied
[ ] System actor seeded and login blocked
[ ] Organization works
[ ] User works
[ ] Role works
[ ] Permission works
[ ] UserRole works
[ ] RolePermission works
[ ] LoginSession works
[ ] Login works
[ ] Logout works
[ ] Refresh strategy implemented and documented
[ ] Forgot-password works
[ ] Reset-password works
[ ] /me works
[ ] /me/permissions works
[ ] Users APIs work
[ ] Roles APIs work
[ ] Sessions APIs work
[ ] Backend authorization works
[ ] Privileged role change policy enforced
[ ] Frontend authentication works
[ ] End-to-end works
[ ] No password leakage
[ ] No token leakage
[ ] No secret leakage
[ ] CI passes
[ ] Security review passes
```

---

## 226. P1 security exit criteria

The project proves:

```text
Unauthenticated request -> 401
Authenticated without permission -> 403
Valid user with permission -> success
Inactive user -> rejected
Inactive role, relationship, or permission -> not authorized
Password reset -> secure
Logout -> terminal session state
Authorization cache failure -> database fallback
System actor login -> rejected
Privileged role change -> enforced
```

---

## 227. P1 performance exit criteria

P1 does not need the full concurrent-user test.

It must prove user lookup indexed, role lookup indexed, permission resolution query measured, session lookup indexed, pagination implemented, and no N+1 authorization query.

---

## 228. P1 security exit criteria for implementation agents

An implementation cannot mark P1 complete if tests are disabled, security exceptions are ignored, JWT validation is bypassed, authorization is performed only in React, database constraints are removed, reset tokens are logged, or passwords appear in fixtures or logs.

---

## 229. P1 documentation exit criteria

Update `docs/implementation-status.md`, `docs/decisions/`, and `docs/testing/security/p1/` with implemented features, security decisions, test evidence, known limitations, and unresolved issues.

---

## 230. P1 integration with future modules

P1 exposes stable identity and application contracts to Academic, Content, Assessment, Delivery, and Evaluation.

These modules depend on CurrentUserContext and the authorization contract rather than directly querying Identity repositories.

---

## 231. Assessment future security example

When Delivery is implemented later:

```text
Student
 |
 v
POST /assessment-schedules/{id}/start
 |
 v
Authentication
 |
 v
Authorization
 |
 v
AccessGrant validation
 |
 v
Schedule validation
```

The Identity module provides the authentication and authorization context. Delivery owns assessment-specific authorization.

---

## 232. Student response future security example

When Delivery later handles `PUT /attempts/{attemptId}/responses/{questionVersionId}`, Identity proves who is calling and Delivery proves whether that caller may mutate that attempt.

This separation is mandatory.

---

## 233. Audit future integration

Later modules call the common audit mechanism with actor, action, entity, entityId, and remarks.

They do not create custom audit tables.

---

## 234. Identity anti-patterns

Reject:

```text
Role name as security boundary
Frontend permission as authorization
JWT containing entire permission graph
Plaintext password
Password in logs
Reset token in logs
Repository access from controller
Cross-module direct repository calls
Unbounded user list
Generic SQL filter
Redis as identity source of truth
System actor login allowed
```

---

## 235. Final engineering principle

> Identity proves who the caller is; authorization proves what the caller may do; business modules prove whether the requested operation is valid.

This keeps Identity small, reusable, and safe while allowing Delivery, Content, Assessment, and Evaluation to own their respective domain rules.

---

## 236. Document status

**Status:** IMPLEMENTATION-READY Identity and Security specification.

**Previous:** P0 Foundation.

**Current:** P1 Identity and Security.

**Next:** ExamEngine and CDUI implementation, then Academic and Content according to the roadmap.

Before P2 business implementation begins:

```text
P0 PASS
   |
   v
P1 PASS
   |
   v
ExamEngine and CDUI contract implementation
   |
   v
Academic
   |
   v
Content
```

The implementation preserves the frozen architecture throughout this sequence.