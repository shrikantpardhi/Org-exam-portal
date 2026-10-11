# Smart Competitive Examination Platform
## API Contract

**Version:** 1.5
**Status:** FROZEN
**API Style:** REST
**Transport:** HTTPS
**Format:** JSON
**Base Path:** `/api/v1`
**Authentication:** JWT
**Primary Identifier:** UUID
**Documentation:** OpenAPI / Swagger

---

## 1. Purpose

This document defines the REST API contract for the Smart Competitive Examination Platform.

It provides the implementation baseline for frontend integration, backend controllers, service boundaries, authentication, authorization, request validation, response structures, pagination, filtering, sorting, error handling, assessment delivery, evaluation, commerce, administration, and reporting.

The API is designed around business resources and operations rather than database tables.

---

## 2. API design principles

1. RESTful, resource-oriented design.
2. `/api/v1` versioning.
3. JSON request and response.
4. UUID identifiers.
5. Stateless authenticated requests.
6. JWT-based authentication.
7. Consistent success and error envelopes.
8. Pagination for large collections.
9. Filtering and sorting with allow-lists.
10. DTO-based contracts; JPA entities are never exposed.
11. Backend authorization is authoritative.
12. Published examination data cannot be silently modified.
13. Long-running operations use asynchronous jobs.
14. Sensitive operations are auditable.
15. Explicit business action endpoints for state transitions.
16. HTTP conditional requests for stale-write protection on response save.

---

## 3. Base URL

All APIs are rooted at `/api/v1`.

Examples.

```text
/api/v1/auth/login
/api/v1/users
/api/v1/questions
/api/v1/assessments
/api/v1/attempts
/api/v1/results
/api/v1/reports
/api/v1/admin/system-health
/api/v1/search
```

---

## 4. HTTP methods

| Method | Purpose |
|---|---|
| GET | Retrieve resource |
| POST | Create resource or execute action |
| PUT | Replace or update resource |
| PATCH | Partial update where justified |
| DELETE | Archive or delete according to business rules |

DELETE does not imply physical deletion.

For historical entities, the operation normally means archive or inactivate.

---

## 5. Authentication header

Protected APIs use.

```http
Authorization: Bearer <JWT>
```

Example.

```http
GET /api/v1/users
Authorization: Bearer eyJ...
```

---

## 6. Public vs protected APIs

### 6.1 Public

Potentially public endpoints.

```text
POST /api/v1/auth/login
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
GET  /api/v1/public/...
```

### 6.2 Protected

Most business APIs require authentication. Authorization then determines whether the authenticated user may perform the requested operation.

### 6.3 Provider-facing

Payment provider callbacks use provider authenticity, not JWT.

```text
POST /api/v1/payments/callback
```

---

## 7. Standard success response

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": {}
}
```

---

## 8. Standard collection response

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

---

## 9. Pagination

Standard query parameters.

```text
page
size
sort
```

Example.

```text
/api/v1/questions?page=0&size=20&sort=created_on,desc
```

Default values are configured centrally. A maximum page size is enforced to prevent accidental huge responses.

---

## 10. Filtering

Resources expose business-relevant filters.

Example.

```text
/api/v1/questions?status=ACTIVE&difficulty=HARD&templateId=<uuid>
```

Filtering supports only meaningful query fields. Do not create arbitrary dynamic SQL-style filtering through the public API.

---

## 11. Sorting

```text
sort=created_on,desc
```

Multiple sorts may be supported.

```text
sort=status,asc&sort=created_on,desc
```

Only whitelisted sortable fields are accepted.

---

## 12. Standard error response

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

## 13. Error categories

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

---

## 14. HTTP status standards

| HTTP | Usage |
|---|---|
| 200 | Successful retrieval or update |
| 201 | Resource created |
| 202 | Accepted for async processing |
| 204 | Successful operation without response body |
| 400 | Invalid request |
| 401 | Authentication required or invalid |
| 403 | Insufficient permission |
| 404 | Resource not found |
| 409 | Conflict or invalid state |
| 412 | Precondition failed |
| 422 | Business or semantic validation failure |
| 429 | Rate limited |
| 500 | Unexpected server error |
| 503 | Dependency unavailable |

---

## 15. Request validation

Validation occurs at the API boundary.

Examples: required field, maximum length, minimum length, format, range, enum, UUID, date and time.

Business validation occurs inside the service layer.

---

## 16. API authorization model

```text
JWT
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
Endpoint
 |
 v
Business authorization
```

Frontend visibility is not security.

---

## 17. API idempotency

Idempotency is required for operations where duplicate requests can create duplicate business effects.

Important examples.

```text
Payment callbacks
Attempt submission
Response save
Notification delivery
Background job execution
Report request
```

The exact implementation uses an idempotency key where required.

---

## 18. Idempotency header

For applicable operations.

```http
Idempotency-Key: <unique-client-key>
```

The server detects duplicate processing and returns the original logical result where appropriate.

---

## 19. Request correlation

Every request should carry or receive a request identifier.

```http
X-Request-Id: <uuid>
```

The identifier is echoed in the response and appears in structured logs and audit context where available.

---

## 20. API module map

```text
/api/v1
 |
 +-- auth
 +-- users
 +-- roles
 +-- permissions
 +-- login-sessions
 |
 +-- exams
 +-- subjects
 +-- chapters
 +-- topics
 +-- sub-topics
 +-- languages
 +-- tags
 |
 +-- question-templates
 +-- questions
 +-- question-versions
 +-- question-versions/{id}/options
 +-- question-versions/{id}/assets
 +-- question-versions/{id}/explanation
 +-- question-versions/{id}/hint
 +-- questions/{id}/previous-exams
 |
 +-- blueprints
 +-- assessments
 +-- assessment-versions
 +-- assessment-versions/{id}/rules
 +-- assessment-schedules
 +-- assessment-collections
 |
 +-- sessions
 +-- attempts
 +-- responses
 |
 +-- answer-keys
 +-- answer-keys/{id}/details
 +-- evaluations
 +-- results
 +-- scorecards
 |
 +-- analytics
 |
 +-- access-packages
 +-- subscription-plans
 +-- purchases
 +-- payments
 +-- access-grants
 +-- coupons
 |
 +-- notifications
 +-- email-templates
 +-- announcements
 |
 +-- organization-settings
 +-- business-rules
 +-- lookups
 +-- audit-logs
 +-- admin/system-health
 |
 +-- reports
 +-- imports
 +-- bulk
 +-- search
 +-- files
```

---

## 21. Authentication API

### 21.1 Login

```http
POST /api/v1/auth/login
```

Request.

```json
{
  "username": "student@example.com",
  "password": "********"
}
```

Response.

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

The response never contains password hashes, session secrets, or internal security details.

Login is rejected for the system actor identity.

### 21.2 Logout

```http
POST /api/v1/auth/logout
```

Requires authentication. The backend records or invalidates the applicable login session.

Repeated logout is idempotent from the business perspective.

### 21.3 Refresh token

```http
POST /api/v1/auth/refresh-token
```

Returns a new valid access token according to the selected refresh mechanism.

### 21.4 Forgot password

```http
POST /api/v1/auth/forgot-password
```

Request.

```json
{
  "email": "student@example.com"
}
```

Response.

```json
{
  "success": true,
  "message": "If the account exists, password reset instructions have been sent",
  "data": null
}
```

The response does not reveal whether a specific email exists.

### 21.5 Reset password

```http
POST /api/v1/auth/reset-password
```

Request.

```json
{
  "token": "<reset-token>",
  "newPassword": "********"
}
```

---

## 22. Current user API

### 22.1 Get current user

```http
GET /api/v1/me
```

Response.

```json
{
  "success": true,
  "message": "Current user retrieved",
  "data": {
    "id": "<uuid>",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "roles": [],
    "permissions": []
  }
}
```

### 22.2 Get current permissions

```http
GET /api/v1/me/permissions
```

Response.

```json
{
  "success": true,
  "message": "Permissions retrieved successfully",
  "data": {
    "permissions": ["QUESTION_VIEW", "QUESTION_CREATE"]
  }
}
```

Frontend permissions are for UI behaviour. Backend authorization remains authoritative.

---

## 23. User API

### 23.1 List users

```http
GET /api/v1/users
```

Filters: status, roleId, search.

Pagination is supported.

### 23.2 Get user

```http
GET /api/v1/users/{id}
```

Returns user profile and role information. Sensitive authentication fields are never returned.

### 23.3 Create user

```http
POST /api/v1/users
```

Request.

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "mobile": "9999999999",
  "roles": ["<role-uuid>"]
}
```

The API never accepts or returns a plaintext stored password.

### 23.4 Update user

```http
PUT /api/v1/users/{id}
```

Allows permitted profile and account updates.

### 23.5 Archive user

```http
DELETE /api/v1/users/{id}
```

Represents archive or deactivation. Not uncontrolled physical deletion.

---

## 24. Role API

### 24.1 List roles

```http
GET /api/v1/roles
```

### 24.2 Create role

```http
POST /api/v1/roles
```

Request.

```json
{
  "code": "REVIEWER",
  "name": "Reviewer",
  "description": "Content reviewer"
}
```

### 24.3 Update role

```http
PUT /api/v1/roles/{id}
```

### 24.4 Get role permissions

```http
GET /api/v1/roles/{id}/permissions
```

### 24.5 Replace role permissions

```http
PUT /api/v1/roles/{id}/permissions
```

Request.

```json
{
  "permissionIds": ["<uuid>", "<uuid>"]
}
```

The operation is audited and transactional.

---

## 25. Permission API

Permissions are read-only in the administrative API.

```http
GET /api/v1/permissions
```

---

## 26. Login session API

### 26.1 List

```http
GET /api/v1/login-sessions
```

Filters: userId, sessionStatus, from, to.

### 26.2 Get

```http
GET /api/v1/login-sessions/{id}
```

Safe session metadata only. No authentication tokens are returned.

---

## 27. Academic API

Academic resources.

```text
exams
subjects
chapters
topics
sub-topics
languages
tags
```

### 27.1 Exam API

```http
GET    /api/v1/exams
GET    /api/v1/exams/{id}
POST   /api/v1/exams
PUT    /api/v1/exams/{id}
DELETE /api/v1/exams/{id}
```

Create request.

```json
{
  "code": "SSC-CGL",
  "name": "SSC CGL",
  "description": "Combined Graduate Level"
}
```

### 27.2 Subject API

```http
GET    /api/v1/subjects
GET    /api/v1/subjects/{id}
POST   /api/v1/subjects
PUT    /api/v1/subjects/{id}
DELETE /api/v1/subjects/{id}
```

Filter: examId.

### 27.3 Chapter API

```http
GET    /api/v1/chapters
GET    /api/v1/chapters/{id}
POST   /api/v1/chapters
PUT    /api/v1/chapters/{id}
DELETE /api/v1/chapters/{id}
```

Filter: subjectId.

### 27.4 Topic API

```http
GET    /api/v1/topics
GET    /api/v1/topics/{id}
POST   /api/v1/topics
PUT    /api/v1/topics/{id}
DELETE /api/v1/topics/{id}
```

Filter: chapterId.

### 27.5 SubTopic API

```http
GET    /api/v1/sub-topics
GET    /api/v1/sub-topics/{id}
POST   /api/v1/sub-topics
PUT    /api/v1/sub-topics/{id}
DELETE /api/v1/sub-topics/{id}
```

Filter: topicId.

### 27.6 Language API

```http
GET    /api/v1/languages
GET    /api/v1/languages/{id}
POST   /api/v1/languages
PUT    /api/v1/languages/{id}
DELETE /api/v1/languages/{id}
```

### 27.7 Tag API

```http
GET    /api/v1/tags
GET    /api/v1/tags/{id}
POST   /api/v1/tags
PUT    /api/v1/tags/{id}
DELETE /api/v1/tags/{id}
```

---

## 28. Question template API

```http
GET  /api/v1/question-templates
GET  /api/v1/question-templates/{id}
POST /api/v1/question-templates
PUT  /api/v1/question-templates/{id}
```

Templates define supported question formats and their configuration.

---

## 29. Question API

### 29.1 List questions

```http
GET /api/v1/questions
```

Filters.

```text
status
templateId
difficulty
examId
subjectId
chapterId
topicId
tagId
languageId
isPublished
reviewStatus
search
```

### 29.2 Get question

```http
GET /api/v1/questions/{id}
```

Returns logical question information with available version summaries.

### 29.3 Create question

```http
POST /api/v1/questions
```

Request.

```json
{
  "templateId": "<uuid>",
  "examId": "<uuid>",
  "subjectId": "<uuid>",
  "chapterId": "<uuid>",
  "topicId": "<uuid>",
  "title": "Example question",
  "content": "What is ...?",
  "marks": 2,
  "negativeMarks": 0,
  "difficulty": "MEDIUM",
  "languageId": "<uuid>"
}
```

The service creates the logical Question and its initial QuestionVersion.

### 29.4 Update draft question

```http
PUT /api/v1/questions/{id}
```

Only mutable draft-level metadata is updated. Published versions are not overwritten.

### 29.5 Create question version

```http
POST /api/v1/questions/{id}/versions
```

Creates a new QuestionVersion in DRAFT state.

### 29.6 List question versions

```http
GET /api/v1/questions/{id}/versions
```

### 29.7 Get question version

```http
GET /api/v1/questions/{id}/versions/{versionId}
```

---

## 30. Question review workflow API

```http
POST /api/v1/questions/{id}/versions/{versionId}/submit-review
POST /api/v1/questions/{id}/versions/{versionId}/approve
POST /api/v1/questions/{id}/versions/{versionId}/reject
POST /api/v1/questions/{id}/versions/{versionId}/publish
```

Approve request.

```json
{
  "remarks": "Verified content and options."
}
```

Reject request.

```json
{
  "remarks": "Option D duplicate of C. Please fix."
}
```

Rules.

- Only a user with `QUESTION_SUBMIT_REVIEW` may transition DRAFT to IN_REVIEW.
- Only a user with `QUESTION_REVIEW` may transition IN_REVIEW to APPROVED or REJECTED.
- Only a user with `QUESTION_PUBLISH` may transition APPROVED to PUBLISHED.
- When the same user holds both submit and review permissions, the submitting user and the reviewing user must differ.
- Illegal transitions return 409 INVALID_STATE.

---

## 31. Question option API

```http
GET    /api/v1/questions/{questionId}/versions/{versionId}/options
POST   /api/v1/questions/{questionId}/versions/{versionId}/options
PUT    /api/v1/questions/{questionId}/versions/{versionId}/options/{optionId}
DELETE /api/v1/questions/{questionId}/versions/{versionId}/options/{optionId}
```

Create request.

```json
{
  "optionKey": "A",
  "content": "Option text",
  "displayOrder": 1,
  "isCorrect": false
}
```

`isCorrect` is accepted only by authorized content and reviewer APIs. It is never returned by student delivery APIs.

Published QuestionVersions are immutable.

---

## 32. Asset API

```http
POST   /api/v1/question-versions/{versionId}/assets
GET    /api/v1/question-versions/{versionId}/assets
DELETE /api/v1/question-versions/{versionId}/assets/{assetId}
```

Upload is multipart. Removal is subject to publication and immutability rules.

---

## 33. Explanation API

```http
GET  /api/v1/question-versions/{versionId}/explanation
POST /api/v1/question-versions/{versionId}/explanation
PUT  /api/v1/question-versions/{versionId}/explanation
```

Published version rules apply.

---

## 34. Hint API

```http
GET  /api/v1/question-versions/{versionId}/hint
POST /api/v1/question-versions/{versionId}/hint
PUT  /api/v1/question-versions/{versionId}/hint
```

---

## 35. Previous exam history API

```http
GET    /api/v1/questions/{questionId}/previous-exams
POST   /api/v1/questions/{questionId}/previous-exams
PUT    /api/v1/questions/{questionId}/previous-exams/{historyId}
DELETE /api/v1/questions/{questionId}/previous-exams/{historyId}
```

---

## 36. Blueprint API

```http
GET  /api/v1/blueprints
GET  /api/v1/blueprints/{id}
POST /api/v1/blueprints
PUT  /api/v1/blueprints/{id}
```

### 36.1 Blueprint versions

```http
POST /api/v1/blueprints/{id}/versions
GET  /api/v1/blueprints/{id}/versions
GET  /api/v1/blueprints/{id}/versions/{versionId}
POST /api/v1/blueprints/{id}/versions/{versionId}/publish
```

Create version request.

```json
{
  "configuration": {}
}
```

The server validates the configuration before publishing.

---

## 37. Assessment API

```http
GET  /api/v1/assessments
GET  /api/v1/assessments/{id}
POST /api/v1/assessments
PUT  /api/v1/assessments/{id}
```

Filters: status, examId, blueprintId, search.

Create request.

```json
{
  "code": "SSC-MOCK-001",
  "name": "SSC Mock Test 1",
  "description": "Full-length practice test"
}
```

Only logical and master data is changed here. Published version data remains immutable.

---

## 38. Assessment version API

```http
GET  /api/v1/assessments/{id}/versions
POST /api/v1/assessments/{id}/versions
GET  /api/v1/assessments/{id}/versions/{versionId}
```

Create request.

```json
{
  "blueprintVersionId": "<uuid>",
  "durationMinutes": 60,
  "engineType": "CUSTOM",
  "deliveryPolicyId": "<uuid>"
}
```

---

## 39. Assessment question API

### 39.1 Add question

```http
POST /api/v1/assessment-versions/{versionId}/questions
```

Request.

```json
{
  "questionVersionId": "<uuid>",
  "displayOrder": 1,
  "sectionName": "General Intelligence",
  "marks": 2,
  "negativeMarks": 0.5
}
```

### 39.2 Update question

```http
PUT /api/v1/assessment-versions/{versionId}/questions/{assessmentQuestionId}
```

Allowed only while the assessment version remains mutable.

### 39.3 Remove question

```http
DELETE /api/v1/assessment-versions/{versionId}/questions/{assessmentQuestionId}
```

Not allowed after publication.

### 39.4 Reorder questions

```http
PUT /api/v1/assessment-versions/{versionId}/questions/order
```

Request.

```json
{
  "questionOrders": [
    { "assessmentQuestionId": "<uuid>", "displayOrder": 1 },
    { "assessmentQuestionId": "<uuid>", "displayOrder": 2 }
  ]
}
```

---

## 40. Assessment selection rule API

```http
GET    /api/v1/assessment-versions/{versionId}/rules
POST   /api/v1/assessment-versions/{versionId}/rules
PUT    /api/v1/assessment-versions/{versionId}/rules/{ruleId}
DELETE /api/v1/assessment-versions/{versionId}/rules/{ruleId}
POST   /api/v1/assessment-versions/{versionId}/rules/materialize
```

Create request.

```json
{
  "sectionName": "Physics",
  "targetCount": 30,
  "displayOrder": 1,
  "ruleConfiguration": {
    "templateCode": "SINGLE_CHOICE",
    "examId": "<uuid>",
    "subjectId": "<uuid>",
    "difficulty": ["EASY", "MEDIUM"],
    "publishedOnly": true
  }
}
```

Materialize request.

```json
{
  "mode": "REPLACE_UNMATERIALIZED"
}
```

Shortage response.

```json
{
  "success": false,
  "message": "Question pool shortage for section: Physics",
  "errors": [
    {
      "code": "QUESTION_POOL_SHORTAGE",
      "sectionName": "Physics",
      "requested": 30,
      "available": 24,
      "deficit": 6
    }
  ]
}
```

---

## 41. Assessment validation and preview API

### 41.1 Validate assessment

```http
POST /api/v1/assessment-versions/{versionId}/validate
```

Response.

```json
{
  "success": true,
  "message": "Assessment validation completed",
  "data": {
    "valid": false,
    "errors": [
      {
        "code": "QUESTION_COUNT_MISMATCH",
        "message": "Assessment requires 100 questions but only 98 are configured"
      }
    ]
  }
}
```

### 41.2 Preview assessment

```http
GET /api/v1/assessment-versions/{versionId}/preview
```

Preview does not create runtime rows.

### 41.3 Publish assessment version

```http
POST /api/v1/assessment-versions/{versionId}/publish
```

Server validates blueprint, questions, question versions, marks, negative marks, duration, engine type, delivery policy, and required configuration before publication.

---

## 42. Assessment schedule API

```http
GET  /api/v1/assessment-schedules
POST /api/v1/assessment-schedules
PUT  /api/v1/assessment-schedules/{id}
POST /api/v1/assessment-schedules/{id}/close
```

Filters: assessmentVersionId, status, from, to.

Create request.

```json
{
  "assessmentVersionId": "<uuid>",
  "mode": "EXAM",
  "startTime": "2026-10-10T10:00:00Z",
  "endTime": "2026-10-10T12:00:00Z"
}
```

---

## 43. Assessment collection API

```http
GET  /api/v1/assessment-collections
GET  /api/v1/assessment-collections/{id}
POST /api/v1/assessment-collections
PUT  /api/v1/assessment-collections/{id}
```

### 43.1 Add assessment to collection

```http
POST /api/v1/assessment-collections/{collectionId}/assessments
```

### 43.2 Remove assessment from collection

```http
DELETE /api/v1/assessment-collections/{collectionId}/assessments/{assessmentId}
```

---

## 44. Delivery API

### 44.1 Prepare attempts for schedule

```http
POST /api/v1/assessment-schedules/{scheduleId}/prepare-attempts
```

Response.

```http
202 Accepted
```

```json
{
  "success": true,
  "message": "Attempt preparation queued",
  "data": {
    "jobId": "<uuid>",
    "status": "QUEUED",
    "scheduleId": "<uuid>"
  }
}
```

Restricted to authorized operational roles. The operation is idempotent.

### 44.2 Start assessment

```http
POST /api/v1/assessment-schedules/{scheduleId}/start
```

The server validates authentication, authorization, AccessGrant validity, schedule window, attempt eligibility, assessment status, attempt existence, attempt status NOT_STARTED, and snapshot status READY.

Start Exam must not create AttemptQuestion or AttemptQuestionOption records.

Response.

```json
{
  "success": true,
  "message": "Assessment started",
  "data": {
    "sessionId": "<uuid>",
    "attemptId": "<uuid>",
    "assessmentVersionId": "<uuid>",
    "mode": "EXAM",
    "startedOn": "2026-10-10T10:00:00Z",
    "serverTime": "2026-10-10T10:00:00.184Z",
    "timer": {
      "mode": "GLOBAL",
      "durationSeconds": 3600
    },
    "deliveryConfiguration": {}
  }
}
```

Snapshot not ready.

```http
409 Conflict
```

```json
{
  "success": false,
  "message": "Assessment preparation is not complete",
  "errors": [
    {
      "field": "snapshotStatus",
      "code": "PREPARATION_NOT_READY",
      "message": "Please retry when the prepared attempt is ready"
    }
  ]
}
```

### 44.3 Get active session

```http
GET /api/v1/sessions/active
```

Used for reconnect and recovery. Does not create a new session or attempt.

### 44.4 Get session

```http
GET /api/v1/sessions/{sessionId}
```

### 44.5 Recover session

```http
POST /api/v1/sessions/{sessionId}/recover
```

Recovery does not reconstruct the attempt snapshot.

### 44.6 End session

```http
POST /api/v1/sessions/{sessionId}/end
```

Ending a session does not automatically submit the attempt unless a configured rule explicitly requires it.

---

## 45. Attempt API

### 45.1 List attempts

```http
GET /api/v1/attempts
```

Filters: assessmentId, assessmentVersionId, status, from, to.

Students see only their own attempts unless authorized staff access the endpoint.

### 45.2 Get attempt

```http
GET /api/v1/attempts/{attemptId}
```

Returns runtime state.

### 45.3 Get attempt questions

```http
GET /api/v1/attempts/{attemptId}/questions
```

Returns the prepared attempt snapshot.

Example.

```json
{
  "success": true,
  "data": [
    {
      "attemptQuestionId": "<uuid>",
      "questionVersionId": "<uuid>",
      "displayOrder": 1,
      "sectionName": "Physics",
      "marks": 4,
      "negativeMarks": 1,
      "requiresLatexParsing": true,
      "contentFormat": "MARKDOWN_LATEX_V1",
      "content": "Solve \\(x^2 = 4\\).",
      "options": [
        {
          "optionId": "<uuid>",
          "optionKey": "A",
          "displayOrder": 1,
          "content": "\\(x = 1\\)"
        }
      ]
    }
  ]
}
```

The response never includes `isCorrect`, answer keys, or raw asset paths.

### 45.4 Get attempt question

```http
GET /api/v1/attempts/{attemptId}/questions/{questionVersionId}
```

The backend verifies that the QuestionVersion is assigned to the attempt.

---

## 46. Response API

### 46.1 Get response

```http
GET /api/v1/attempts/{attemptId}/responses/{questionVersionId}
```

Response includes ETag header.

```text
ETag: "resp-{updatedOnMillis}-{crc32OfResponse}"
Cache-Control: no-store
```

### 46.2 Save response

```http
PUT /api/v1/attempts/{attemptId}/responses/{questionVersionId}
If-Match: "<etag>"
Content-Type: application/json
```

Request.

```json
{
  "response": "B",
  "timeSpentSeconds": 32,
  "markedForReview": false,
  "visited": true
}
```

Save behaviour.

| Condition | Result |
|---|---|
| If-Match matches current ETag | Accept, return new ETag |
| If-Match does not match | 412 Precondition Failed |
| If-Match: * | Accept only if row exists |
| No If-Match | Accept (first save) |

Precondition failed.

```json
{
  "success": false,
  "message": "Response was modified by another request",
  "errors": [
    {
      "code": "STALE_RESPONSE",
      "message": "The stored response is newer than your request."
    }
  ]
}
```

### 46.3 Get all responses

```http
GET /api/v1/attempts/{attemptId}/responses
```

Used for recovery and review.

### 46.4 Submit attempt

```http
POST /api/v1/attempts/{attemptId}/submit
```

Request.

```json
{
  "confirmed": true
}
```

Server performs session validation, attempt state validation, final persistence, and submission state transition.

Submission is idempotent.

Response.

```json
{
  "success": true,
  "message": "Assessment submitted successfully",
  "data": {
    "attemptId": "<uuid>",
    "status": "SUBMITTED",
    "submittedOn": "2026-10-10T11:00:00Z"
  }
}
```

---

## 47. Answer key API

### 47.1 List answer keys

```http
GET /api/v1/answer-keys
```

### 47.2 Create answer key

```http
POST /api/v1/answer-keys
```

Request.

```json
{
  "assessmentVersionId": "<uuid>"
}
```

### 47.3 Get answer key

```http
GET /api/v1/answer-keys/{id}
```

### 47.4 Publish answer key

```http
POST /api/v1/answer-keys/{id}/publish
```

Publication requires exactly one answer key detail row per AssessmentQuestion in the bound AssessmentVersion.

Published answer keys are immutable.

---

## 48. Answer key detail API

```http
GET    /api/v1/answer-keys/{id}/details
POST   /api/v1/answer-keys/{id}/details
PUT    /api/v1/answer-keys/{id}/details/{detailId}
DELETE /api/v1/answer-keys/{id}/details/{detailId}
```

Create request.

```json
{
  "questionVersionId": "<uuid>",
  "correctOptionId": "<uuid>",
  "questionOutcome": "NORMAL",
  "awardedMarks": null,
  "awardedNegativeMarks": null,
  "remarks": null
}
```

Mutations are blocked after `answer_key.is_published = true`.

---

## 49. Evaluation API

### 49.1 Evaluate attempt

```http
POST /api/v1/attempts/{attemptId}/evaluate
```

Request.

```json
{
  "answerKeyId": "<uuid>"
}
```

Missing `answerKeyId` returns 400. Unpublished answer key returns 409. Mismatched AssessmentVersion returns 409.

### 49.2 Re-evaluate attempt

```http
POST /api/v1/attempts/{attemptId}/re-evaluate
```

Request.

```json
{
  "answerKeyId": "<uuid>",
  "reason": "Corrected answer key v2"
}
```

Response.

```json
{
  "success": true,
  "message": "Re-evaluation completed",
  "data": {
    "attemptId": "<uuid>",
    "newEvaluationId": "<uuid>",
    "newResultId": "<uuid>",
    "previousEvaluationId": "<uuid>",
    "newVersion": 2
  }
}
```

### 49.3 Get evaluation

```http
GET /api/v1/evaluations/{id}
```

Student access to evaluation internals is restricted.

### 49.4 Attempt evaluation history

```http
GET /api/v1/attempts/{attemptId}/evaluations
```

Returns all evaluation versions for the attempt, newest first, including superseded metadata.

Restricted to `EVALUATION_VIEW_HISTORY` permission.

---

## 50. Result API

### 50.1 List results

```http
GET /api/v1/results
```

Filters: assessmentId, assessmentVersionId, studentId, status, from, to.

### 50.2 Get result

```http
GET /api/v1/results/{id}
```

### 50.3 Get attempt result

```http
GET /api/v1/attempts/{attemptId}/result
```

### 50.4 Publish result

```http
POST /api/v1/results/{resultId}/publish
```

Publication is audited.

Published results are immutable by normal CRUD. Corrections use controlled re-evaluation.

### 50.5 Rankings

```http
GET /api/v1/assessments/{assessmentId}/rankings
```

Filters: assessmentVersionId, scheduleId.

Response.

```json
{
  "success": true,
  "data": [
    {
      "rank": 1,
      "userId": "<uuid>",
      "displayName": "J*** D***",
      "obtainedMarks": 98.00,
      "percentile": 99.9820
    }
  ],
  "pagination": { "page": 0, "size": 50, "totalElements": 5000, "totalPages": 100 }
}
```

Privacy rules.

| Caller | Name | User id |
|---|---|---|
| Student | Masked | Hidden |
| Staff or admin | Full | Included |

Public leaderboard requires `rankingsPublic = true` in blueprint configuration.

---

## 51. Scorecard API

```http
GET  /api/v1/results/{resultId}/scorecard
POST /api/v1/results/{resultId}/scorecard
```

Generation of large files may return 202 Accepted with a job reference.

---

## 52. Analytics API

Read-oriented endpoints.

```text
/api/v1/analytics/student
/api/v1/analytics/assessments/{assessmentId}
/api/v1/analytics/questions/{questionId}
/api/v1/analytics/topics/{topicId}
/api/v1/analytics/organization
```

### 52.1 Student analytics

```http
GET /api/v1/analytics/student
```

A student receives their own analytics. Authorized staff may retrieve analytics for permitted students.

### 52.2 Assessment analytics

```http
GET /api/v1/analytics/assessments/{assessmentId}
```

### 52.3 Question analytics

```http
GET /api/v1/analytics/questions/{questionId}
```

### 52.4 Topic analytics

```http
GET /api/v1/analytics/topics/{topicId}
```

### 52.5 Organization analytics

```http
GET /api/v1/analytics/organization
```

Restricted to authorized operational and administrative users.

---

## 53. Commerce API

### 53.1 Access package

```http
GET    /api/v1/access-packages
GET    /api/v1/access-packages/{id}
POST   /api/v1/access-packages
PUT    /api/v1/access-packages/{id}
DELETE /api/v1/access-packages/{id}
```

### 53.2 Package to collection mapping

```http
GET    /api/v1/access-packages/{packageId}/collections
POST   /api/v1/access-packages/{packageId}/collections
DELETE /api/v1/access-packages/{packageId}/collections/{collectionId}
```

Add collection request.

```json
{
  "collectionId": "<uuid>"
}
```

### 53.3 Subscription plan

```http
GET    /api/v1/subscription-plans
GET    /api/v1/subscription-plans/{id}
POST   /api/v1/subscription-plans
PUT    /api/v1/subscription-plans/{id}
DELETE /api/v1/subscription-plans/{id}
```

Filter: accessPackageId, status.

Create request.

```json
{
  "accessPackageId": "<uuid>",
  "code": "SSC-90-DAYS",
  "name": "SSC 90 Days",
  "durationDays": 90,
  "price": 999.00
}
```

### 53.4 Purchase

```http
POST /api/v1/purchases
GET  /api/v1/purchases
GET  /api/v1/purchases/{id}
```

Create request.

```json
{
  "subscriptionPlanId": "<uuid>",
  "couponCode": "WELCOME10"
}
```

The server calculates the final amount. The client does not determine the payable amount.

### 53.5 Payment

```http
POST /api/v1/purchases/{purchaseId}/payment
GET  /api/v1/purchases/{purchaseId}/payment
POST /api/v1/payments/callback
```

The callback must be authenticated by provider signature, not JWT. It must be idempotent.

### 53.6 Refund

```http
POST /api/v1/purchases/{purchaseId}/refund
```

Requires appropriate finance or administration permission.

### 53.7 Access grant

```http
GET  /api/v1/access-grants
GET  /api/v1/access-grants/{id}
POST /api/v1/access-grants
```

Filters: userId, accessPackageId, status, active.

Manual grant request.

```json
{
  "userId": "<uuid>",
  "accessPackageId": "<uuid>",
  "startDate": "2026-10-01T00:00:00Z",
  "endDate": "2026-12-30T23:59:59Z"
}
```

The action is audited.

### 53.8 Coupon

```http
GET    /api/v1/coupons
GET    /api/v1/coupons/{id}
POST   /api/v1/coupons
PUT    /api/v1/coupons/{id}
DELETE /api/v1/coupons/{id}
POST   /api/v1/coupons/validate
```

Validate request.

```json
{
  "code": "WELCOME10",
  "subscriptionPlanId": "<uuid>"
}
```

The server calculates applicability.

---

## 54. Communication API

### 54.1 Notification

```http
GET  /api/v1/notifications
POST /api/v1/notifications/{id}/read
POST /api/v1/notifications/read-all
```

### 54.2 Email template

```http
GET    /api/v1/email-templates
GET    /api/v1/email-templates/{id}
POST   /api/v1/email-templates
PUT    /api/v1/email-templates/{id}
DELETE /api/v1/email-templates/{id}
```

### 54.3 Announcement

```http
GET    /api/v1/announcements
GET    /api/v1/announcements/{id}
POST   /api/v1/announcements
PUT    /api/v1/announcements/{id}
DELETE /api/v1/announcements/{id}
POST   /api/v1/announcements/{id}/publish
```

Publishing may generate notifications. Publishing must not perform synchronous fan-out for large audiences.

---

## 55. Administration API

### 55.1 Organization settings

```http
GET /api/v1/organization-settings
PUT /api/v1/organization-settings
```

Configuration changes are audited.

### 55.2 Business rules

```http
GET  /api/v1/business-rules
GET  /api/v1/business-rules/{id}
POST /api/v1/business-rules
PUT  /api/v1/business-rules/{id}
```

### 55.3 Lookup

```http
GET    /api/v1/lookups
GET    /api/v1/lookups/{id}
POST   /api/v1/lookups
PUT    /api/v1/lookups/{id}
DELETE /api/v1/lookups/{id}
```

Filter: category.

### 55.4 Audit logs

```http
GET /api/v1/audit-logs
```

Filters: userId, entityName, entityId, action, from, to.

Audit logs are read-only.

---

## 56. System health API

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

Permission: `SYSTEM_HEALTH_VIEW`.

---

## 57. Global search API

```http
GET /api/v1/search?q=<query>&resource=<optional>&limit=<n>
```

Constraints.

- `q` between 2 and 100 characters.
- `limit` default 10, maximum 25.
- Query timeout 2 seconds.
- Rate limit 30 requests per minute per user.

Response.

```json
{
  "success": true,
  "data": {
    "results": [
      {
        "resource": "QUESTION",
        "id": "<uuid>",
        "title": "Algebra — Quadratic Equations",
        "excerpt": "Solve for x when 2x + 5 = 15...",
        "score": 0.92
      }
    ]
  }
}
```

Searchable resources.

| Resource | Permission |
|---|---|
| USER | GLOBAL_SEARCH_USER |
| QUESTION | GLOBAL_SEARCH_QUESTION |
| ASSESSMENT | GLOBAL_SEARCH_ASSESSMENT |
| ACCESS_PACKAGE | GLOBAL_SEARCH_COMMERCE |
| SUBSCRIPTION_PLAN | GLOBAL_SEARCH_COMMERCE |
| PURCHASE | GLOBAL_SEARCH_COMMERCE |
| ANNOUNCEMENT | GLOBAL_SEARCH_COMMUNICATION |
| AUDIT_LOG | GLOBAL_SEARCH_AUDIT |

PII is masked by default. Unmasking requires `USER_VIEW_PII`.

---

## 58. Reports API

### 58.1 Generate report

```http
POST /api/v1/reports
```

Request.

```json
{
  "reportType": "ASSESSMENT_ATTEMPTS",
  "filters": {
    "assessmentId": "<uuid>",
    "from": "2026-10-01",
    "to": "2026-10-31"
  },
  "format": "PDF"
}
```

Large reports return 202 Accepted.

```json
{
  "success": true,
  "message": "Report generation started",
  "data": {
    "jobId": "<uuid>",
    "status": "QUEUED"
  }
}
```

### 58.2 Report job status

```http
GET /api/v1/reports/jobs/{jobId}
```

Response.

```json
{
  "success": true,
  "message": "Report job status retrieved",
  "data": {
    "jobId": "<uuid>",
    "reportType": "ASSESSMENT_ATTEMPTS",
    "status": "COMPLETED",
    "queuedOn": "2026-10-07T09:00:00Z",
    "completedOn": "2026-10-07T09:00:42Z",
    "resultReference": "<opaque-ref>",
    "downloadAvailable": true
  }
}
```

### 58.3 Report history

```http
GET /api/v1/reports/history
```

Backed by `background_job`.

```json
{
  "success": true,
  "data": [
    {
      "jobId": "<uuid>",
      "reportType": "ASSESSMENT_ATTEMPTS",
      "status": "COMPLETED",
      "queuedOn": "2026-10-07T09:00:00Z",
      "completedOn": "2026-10-07T09:00:42Z",
      "downloadAvailable": true
    }
  ],
  "pagination": { "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

---

## 59. Imports API

```http
POST /api/v1/imports
GET  /api/v1/imports/{jobId}
```

Upload is multipart. Response.

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

Import status may include structured row errors.

```json
{
  "jobId": "<uuid>",
  "status": "COMPLETED_WITH_ERRORS",
  "totalRecords": 1000,
  "successCount": 987,
  "failureCount": 13,
  "errors": [
    { "row": 17, "field": "email", "code": "INVALID_EMAIL", "message": "Email address is invalid" }
  ]
}
```

---

## 60. Bulk operations API

```http
POST /api/v1/bulk/{resource}/{operation}
```

Supported resource and operation pairs are explicitly allow-listed.

Response.

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

## 61. File download API

```http
GET /api/v1/files/{reference}
```

Authorization is verified against the bound owner or permission before streaming. Response headers.

```text
Content-Type: application/pdf | text/csv | image/png | image/jpeg
Content-Disposition: attachment; filename="..."
Cache-Control: private, no-store
```

Object storage credentials, filesystem paths, and signed URLs are never exposed.

---

## 62. Complete endpoint inventory

### Authentication

```text
POST /api/v1/auth/login
POST /api/v1/auth/logout
POST /api/v1/auth/refresh-token
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
```

### Current user

```text
GET /api/v1/me
GET /api/v1/me/permissions
```

### Identity

```text
GET    /api/v1/users
GET    /api/v1/users/{id}
POST   /api/v1/users
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}

GET    /api/v1/roles
POST   /api/v1/roles
PUT    /api/v1/roles/{id}
GET    /api/v1/roles/{id}/permissions
PUT    /api/v1/roles/{id}/permissions

GET    /api/v1/permissions

GET    /api/v1/login-sessions
GET    /api/v1/login-sessions/{id}
```

### Academic

```text
GET/POST/PUT/DELETE /api/v1/exams
GET/POST/PUT/DELETE /api/v1/subjects
GET/POST/PUT/DELETE /api/v1/chapters
GET/POST/PUT/DELETE /api/v1/topics
GET/POST/PUT/DELETE /api/v1/sub-topics
GET/POST/PUT/DELETE /api/v1/languages
GET/POST/PUT/DELETE /api/v1/tags
```

### Content

```text
GET/POST/PUT /api/v1/question-templates

GET    /api/v1/questions
GET    /api/v1/questions/{id}
POST   /api/v1/questions
PUT    /api/v1/questions/{id}

GET    /api/v1/questions/{id}/versions
POST   /api/v1/questions/{id}/versions
GET    /api/v1/questions/{id}/versions/{versionId}
POST   /api/v1/questions/{id}/versions/{versionId}/submit-review
POST   /api/v1/questions/{id}/versions/{versionId}/approve
POST   /api/v1/questions/{id}/versions/{versionId}/reject
POST   /api/v1/questions/{id}/versions/{versionId}/publish

GET    /api/v1/questions/{questionId}/versions/{versionId}/options
POST   /api/v1/questions/{questionId}/versions/{versionId}/options
PUT    /api/v1/questions/{questionId}/versions/{versionId}/options/{optionId}
DELETE /api/v1/questions/{questionId}/versions/{versionId}/options/{optionId}

POST   /api/v1/question-versions/{versionId}/assets
GET    /api/v1/question-versions/{versionId}/assets
DELETE /api/v1/question-versions/{versionId}/assets/{assetId}

GET    /api/v1/question-versions/{versionId}/explanation
POST   /api/v1/question-versions/{versionId}/explanation
PUT    /api/v1/question-versions/{versionId}/explanation

GET    /api/v1/question-versions/{versionId}/hint
POST   /api/v1/question-versions/{versionId}/hint
PUT    /api/v1/question-versions/{versionId}/hint

GET    /api/v1/questions/{questionId}/previous-exams
POST   /api/v1/questions/{questionId}/previous-exams
PUT    /api/v1/questions/{questionId}/previous-exams/{historyId}
DELETE /api/v1/questions/{questionId}/previous-exams/{historyId}
```

### Assessment

```text
GET/POST/PUT /api/v1/blueprints
GET    /api/v1/blueprints/{id}/versions
POST   /api/v1/blueprints/{id}/versions
GET    /api/v1/blueprints/{id}/versions/{versionId}
POST   /api/v1/blueprints/{id}/versions/{versionId}/publish

GET/POST/PUT /api/v1/assessments
GET    /api/v1/assessments/{id}/versions
POST   /api/v1/assessments/{id}/versions
GET    /api/v1/assessments/{id}/versions/{versionId}

POST   /api/v1/assessment-versions/{versionId}/questions
PUT    /api/v1/assessment-versions/{versionId}/questions/{assessmentQuestionId}
DELETE /api/v1/assessment-versions/{versionId}/questions/{assessmentQuestionId}
PUT    /api/v1/assessment-versions/{versionId}/questions/order

GET    /api/v1/assessment-versions/{versionId}/rules
POST   /api/v1/assessment-versions/{versionId}/rules
PUT    /api/v1/assessment-versions/{versionId}/rules/{ruleId}
DELETE /api/v1/assessment-versions/{versionId}/rules/{ruleId}
POST   /api/v1/assessment-versions/{versionId}/rules/materialize

POST   /api/v1/assessment-versions/{versionId}/validate
GET    /api/v1/assessment-versions/{versionId}/preview
POST   /api/v1/assessment-versions/{versionId}/publish

GET    /api/v1/assessment-schedules
POST   /api/v1/assessment-schedules
PUT    /api/v1/assessment-schedules/{id}
POST   /api/v1/assessment-schedules/{id}/close

GET    /api/v1/assessment-collections
GET    /api/v1/assessment-collections/{id}
POST   /api/v1/assessment-collections
PUT    /api/v1/assessment-collections/{id}
POST   /api/v1/assessment-collections/{collectionId}/assessments
DELETE /api/v1/assessment-collections/{collectionId}/assessments/{assessmentId}
```

### Delivery

```text
POST /api/v1/assessment-schedules/{scheduleId}/prepare-attempts
POST /api/v1/assessment-schedules/{scheduleId}/start

GET  /api/v1/sessions/active
GET  /api/v1/sessions/{sessionId}
POST /api/v1/sessions/{sessionId}/recover
POST /api/v1/sessions/{sessionId}/end

GET  /api/v1/attempts
GET  /api/v1/attempts/{attemptId}
GET  /api/v1/attempts/{attemptId}/questions
GET  /api/v1/attempts/{attemptId}/questions/{questionVersionId}

GET  /api/v1/attempts/{attemptId}/responses/{questionVersionId}
PUT  /api/v1/attempts/{attemptId}/responses/{questionVersionId}
GET  /api/v1/attempts/{attemptId}/responses

POST /api/v1/attempts/{attemptId}/submit
```

### Evaluation

```text
GET    /api/v1/answer-keys
POST   /api/v1/answer-keys
GET    /api/v1/answer-keys/{id}
POST   /api/v1/answer-keys/{id}/publish
GET    /api/v1/answer-keys/{id}/details
POST   /api/v1/answer-keys/{id}/details
PUT    /api/v1/answer-keys/{id}/details/{detailId}
DELETE /api/v1/answer-keys/{id}/details/{detailId}

POST /api/v1/attempts/{attemptId}/evaluate
POST /api/v1/attempts/{attemptId}/re-evaluate
GET  /api/v1/attempts/{attemptId}/evaluations

GET  /api/v1/evaluations/{id}

GET  /api/v1/results
GET  /api/v1/results/{id}
GET  /api/v1/attempts/{attemptId}/result
POST /api/v1/results/{resultId}/publish

GET  /api/v1/results/{resultId}/scorecard
POST /api/v1/results/{resultId}/scorecard

GET  /api/v1/assessments/{assessmentId}/rankings
```

### Analytics

```text
GET /api/v1/analytics/student
GET /api/v1/analytics/assessments/{assessmentId}
GET /api/v1/analytics/questions/{questionId}
GET /api/v1/analytics/topics/{topicId}
GET /api/v1/analytics/organization
```

### Commerce

```text
GET/POST/PUT/DELETE /api/v1/access-packages
GET    /api/v1/access-packages/{packageId}/collections
POST   /api/v1/access-packages/{packageId}/collections
DELETE /api/v1/access-packages/{packageId}/collections/{collectionId}

GET/POST/PUT/DELETE /api/v1/subscription-plans

POST /api/v1/purchases
GET  /api/v1/purchases
GET  /api/v1/purchases/{id}

POST /api/v1/purchases/{purchaseId}/payment
GET  /api/v1/purchases/{purchaseId}/payment
POST /api/v1/payments/callback
POST /api/v1/purchases/{purchaseId}/refund

GET  /api/v1/access-grants
GET  /api/v1/access-grants/{id}
POST /api/v1/access-grants

GET/POST/PUT/DELETE /api/v1/coupons
POST /api/v1/coupons/validate
```

### Communication

```text
GET  /api/v1/notifications
POST /api/v1/notifications/{id}/read
POST /api/v1/notifications/read-all

GET/POST/PUT/DELETE /api/v1/email-templates

GET/POST/PUT/DELETE /api/v1/announcements
POST /api/v1/announcements/{id}/publish
```

### Administration

```text
GET /api/v1/organization-settings
PUT /api/v1/organization-settings

GET/POST/PUT /api/v1/business-rules
GET /api/v1/business-rules/{id}

GET/POST/PUT/DELETE /api/v1/lookups

GET /api/v1/audit-logs

GET /api/v1/admin/system-health
```

### Reports, imports, bulk, search, files

```text
POST /api/v1/reports
GET  /api/v1/reports/jobs/{jobId}
GET  /api/v1/reports/history

POST /api/v1/imports
GET  /api/v1/imports/{jobId}

POST /api/v1/bulk/{resource}/{operation}

GET /api/v1/search

GET /api/v1/files/{reference}
```

---

## 63. Error catalogue

| Code | HTTP | Meaning |
|---|---|---|
| VALIDATION_ERROR | 400 | Request failed field validation |
| AUTHENTICATION_ERROR | 401 | Missing, invalid, or expired credentials |
| AUTHORIZATION_ERROR | 403 | Authenticated but not permitted |
| NOT_FOUND | 404 | Resource does not exist or is outside scope |
| CONFLICT | 409 | Duplicate or conflicting state |
| INVALID_STATE | 409 | State transition is not permitted |
| BUSINESS_ERROR | 422 | Business rule rejected the operation |
| STALE_RESPONSE | 412 | If-Match precondition failed |
| PREPARATION_NOT_READY | 409 | Attempt snapshot not yet READY |
| QUESTION_POOL_SHORTAGE | 422 | Rule-based selection lacks eligible questions |
| REPORT_GENERATION_FAILED | 500 | Report generation failed |
| RATE_LIMITED | 429 | Rate limit exceeded |
| SYSTEM_ERROR | 500 | Unexpected server error |

Domain-specific error codes are namespaced within these categories.

---

## 64. Authorization matrix summary

| Module | Typical access |
|---|---|
| Authentication | Public or authenticated |
| Identity | Admin, support, and authorized users |
| Academic | Staff, reviewer, admin |
| Content | Reviewer, staff, admin |
| Assessment | Staff, reviewer, admin |
| Delivery | Student and authorized operations |
| Evaluation | Staff, admin |
| Analytics | Student and authorized staff or admin |
| Commerce | Student and finance or admin |
| Communication | Authorized users |
| Administration | Admin |
| Audit | Admin and authorized operations |
| Reporting | Permission-scoped |
| Bulk and import | Permission-scoped |
| Global search | Permission-scoped per resource |
| System health | SYSTEM_HEALTH_VIEW |

Exact permission mappings are controlled by RBAC.

---

## 65. Async job contract

Long-running operations use the following pattern.

```text
POST operation
      |
      v
202 Accepted
      |
      v
jobId
      |
      v
GET job status
```

Applicable to.

- Large reports
- Imports
- Exports
- Analytics recalculation
- Large notifications
- Scorecard generation
- Attempt snapshot preparation

Job statuses.

```text
QUEUED
RUNNING
COMPLETED
COMPLETED_WITH_ERRORS
FAILED
CANCELLED
```

---

## 66. Security boundaries

Students never receive.

- Correct answers
- Internal scoring rules
- Other students' responses
- Internal evaluation details before publication
- Administrative configuration
- Private audit data
- Payment secrets
- Raw asset file paths

Reviewers receive question and content metadata. They do not automatically receive financial or administrative privileges.

Finance users receive purchase, payment, refund, coupon, and subscription data. They do not automatically receive question publication rights.

Support users receive scoped operational visibility. They do not automatically receive administrative or financial mutation rights.

---

## 67. Rate limiting

Rate limiting is considered for the following.

- Login
- Password reset
- Public APIs
- Payment callbacks
- Global search
- Bulk and import operations
- Report generation
- Coupon validation

Assessment response APIs are designed so that rate limiting does not interfere with legitimate examination traffic.

---

## 68. Performance boundaries

Critical runtime APIs.

```text
POST /assessment-schedules/{scheduleId}/start
GET  /attempts/{attemptId}
GET  /attempts/{attemptId}/questions
PUT  /attempts/{attemptId}/responses/{questionVersionId}
POST /attempts/{attemptId}/submit
```

These are optimized separately from administrative APIs.

The architecture requires Start Exam to remain lightweight and to perform no snapshot child-row creation.

---

## 69. Logging

Every API request logs.

```text
Request id
User id
HTTP method
Path
Status
Duration
Error code
```

The API never logs passwords, JWTs, payment secrets, or sensitive request bodies.

---

## 70. API contract freeze

The following are frozen.

1. REST.
2. JSON.
3. HTTPS.
4. `/api/v1`.
5. UUID identifiers.
6. JWT authentication.
7. RBAC authorization.
8. Standard success envelope.
9. Standard error envelope.
10. Pagination.
11. Filtering.
12. Sorting.
13. OpenAPI and Swagger documentation.
14. DTO-based contracts.
15. Explicit business action endpoints.
16. Idempotency for sensitive operations.
17. Async job pattern for long-running operations.
18. Backend as authority for examination state.
19. Backend as authority for commerce access.
20. Versioned and published data cannot be silently changed.
21. HTTP conditional requests for stale-write protection on response save.
22. Explicit `answerKeyId` on evaluate and re-evaluate.
23. System health aggregation contract.
24. Global search permission and resource contract.
25. Report history and job status served by `background_job`.
26. Report and import artifact delivery through the protected file endpoint.

---

## 71. Document status

**Status:** FROZEN API baseline.

This document is the API implementation reference for Spring Boot controllers, DTOs, validation, security, frontend integration, OpenAPI generation, API testing, and integration testing.