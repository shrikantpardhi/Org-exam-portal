# Smart Competitive Examination Platform
## Exam Engine & Configuration-Driven UI (CDUI) Contract

**Version:** 1.5
**Status:** FROZEN
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Connection Pooling:** PgBouncer
**Cache:** Redis
**Content Contract:** MARKDOWN_LATEX_V1
**Math Renderer:** KaTeX SSR
**API:** REST + JSON
**API Base:** /api/v1
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Architects

---

## 1. Purpose

This document defines the implementation contract between the backend examination engine and the exam delivery frontend.

It answers one central question.

> How can the platform support different examination frameworks without hard-coding exam names into backend services or the Next.js UI?

The answer.

```text
Exam-specific rules
        |
        v
ExamEngine
        |
        v
Normalized delivery policies
        |
        v
Configuration-driven UI (CDUI)
        |
        v
Capability-based React components
```

The backend provides the authoritative examination behavior. The frontend provides reusable presentation and interaction capabilities.

The frontend never needs to know whether the current examination is called TCS iON, IBPS, NTA, UPSC, Railway, or Custom.

The frontend understands normalized capabilities such as:

```text
allowBackNavigation
allowSectionSwitch
questionReview
calculator
sectionWiseTimer
autoSubmitOnExpiry
palettePosition
```

---

## 2. Relationship to the rest of the package

This document is downstream of:

- Master PRD
- Domain Model
- Technical Design Document
- Database Standards and Schema
- API Contract
- Architecture Freeze
- Development Roadmap

The earlier documents already freeze:

- Modular monolith architecture
- Java 25 and Spring Boot 4
- Next.js and TypeScript
- PostgreSQL
- Redis as cache only
- PgBouncer for production connection management
- Flyway
- Immutable published examination artifacts
- Pre-generated attempt snapshots
- PRACTICE and EXAM modes
- MARKDOWN_LATEX_V1
- KaTeX server-side rendering
- Single-organization pilot boundary
- 5,000 concurrent-user performance acceptance

This document does not replace those decisions. It converts the exam engine and delivery-policy concepts into a concrete cross-layer contract.

---

## 3. Core architectural principle

The platform follows one primary rule.

> Configuration drives examination behavior; code provides reusable capabilities.

The implementation must not create exam-specific logic such as.

```java
if (exam == "NTA") {
    ...
}

if (exam == "IBPS") {
    ...
}
```

Or frontend logic such as.

```tsx
if (examName === "UPSC") {
    ...
}
```

Instead.

```text
AssessmentVersion
        |
        v
ExamEngine resolution
        |
        v
Normalized DeliveryConfiguration
        |
        v
Frontend capabilities
```

This allows a new examination framework to reuse existing platform capabilities.

---

## 4. Scope of the ExamEngine contract

The ExamEngine is responsible for interpreting examination rules.

It does not own all delivery persistence and it does not render UI.

The ExamEngine determines:

- Navigation behavior
- Timer behavior
- Submission behavior
- Validation behavior
- Presentation capabilities
- Mode constraints

The ExamEngine does not directly perform:

- HTTP handling
- JPA persistence
- Student authentication
- Response database writes
- HTML rendering
- Payment processing
- Frontend state management

Those responsibilities remain in their respective modules.

---

## 5. Responsibility boundaries

### 5.1 ExamEngine

Responsible for:

- Resolving the configured examination framework
- Returning normalized delivery policies
- Determining supported capabilities
- Validating engine-specific rules
- Defining timing semantics
- Defining navigation semantics
- Defining submission semantics
- Defining mode semantics
- Defining presentation capabilities where they are genuinely part of examination behavior

### 5.2 Delivery module

Responsible for:

- Sessions
- Attempts
- Prepared snapshots
- Response persistence
- Authorization checks
- Backend state transitions
- Server-side timing enforcement
- Navigation and request validation
- Submission
- Session recovery
- Concurrency controls
- Idempotency

### 5.3 Frontend

Responsible for:

- Displaying the assessment
- Presenting capabilities
- Maintaining temporary UI state
- Rendering canonical question content
- Rendering math using the approved rendering boundary
- Sending user actions to the backend
- Handling network failures
- Displaying server-authoritative state

The frontend is never authoritative for examination rules.

---

## 6. ExamEngine lifecycle

The lifecycle is:

```text
Assessment or schedule context
        |
        v
Resolve ExamEngine
        |
        v
Resolve exam mode
        |
        v
Resolve delivery policy
        |
        v
Validate configuration
        |
        v
Persist or activate attempt
        |
        v
Return normalized DeliveryConfiguration
        |
        v
Frontend renders capabilities
```

For a prepared attempt.

```text
AssessmentSchedule
        |
        v
Prepared StudentAttempt
        |
        v
Attempt snapshot READY
        |
        v
Start Exam
        |
        v
ExamEngine resolves policy
        |
        v
DeliveryConfiguration returned
```

The ExamEngine must not create the attempt snapshot. Snapshot creation is owned by the asynchronous preparation worker.

---

## 7. Frozen ExamEngine types

Supported identifiers.

```text
TCS_ION
IBPS
NTA
UPSC
RAILWAY
CUSTOM
```

The identifiers are implementation-level framework types. The frontend does not branch on them. The frontend receives only normalized policies and capabilities.

---

## 8. ExamEngine resolution

The backend resolves the engine from persisted assessment context.

```text
AssessmentVersion.engine_type
        |
        v
ExamEngineRegistry
        |
        v
ExamEngine implementation
```

Recommended implementation.

```java
public interface ExamEngineRegistry {
    ExamEngine resolve(AssessmentContext context);
}
```

Example.

```java
@Component
public class DefaultExamEngineRegistry implements ExamEngineRegistry {

    private final Map<ExamEngineType, ExamEngine> engines;

    @Override
    public ExamEngine resolve(AssessmentContext context) {
        ExamEngineType type = context.engineType();
        ExamEngine engine = engines.get(type);

        if (engine == null) {
            throw new InvalidStateException("Unsupported exam engine");
        }

        return engine;
    }
}
```

The persistence source is `assessment_version.engine_type`.

---

## 9. Core Java contract

Minimum contract.

```java
public interface ExamEngine {

    ExamEngineType type();

    DeliveryConfiguration resolveConfiguration(
        AssessmentContext context,
        StudentAttemptContext attemptContext
    );

    NavigationPolicy navigationPolicy(
        AssessmentContext context,
        StudentAttemptContext attemptContext
    );

    TimerPolicy timerPolicy(
        AssessmentContext context,
        StudentAttemptContext attemptContext
    );

    SubmissionPolicy submissionPolicy(
        AssessmentContext context,
        StudentAttemptContext attemptContext
    );

    ValidationPolicy validationPolicy(
        AssessmentContext context,
        StudentAttemptContext attemptContext
    );
}
```

A `PresentationPolicy` may be included within `DeliveryConfiguration` when presentation behavior is driven by examination rules.

The ExamEngine is not responsible for arbitrary UI instructions.

---

## 10. Policy object model

The normalized policy model consists of the following.

```text
DeliveryConfiguration
  - Capabilities
  - NavigationPolicy
  - TimerPolicy
  - SubmissionPolicy
  - ValidationPolicy
  - PresentationPolicy
```

Conceptual Java model.

```java
public record DeliveryConfiguration(
    DeliveryCapabilities capabilities,
    NavigationPolicy navigation,
    TimerPolicy timer,
    SubmissionPolicy submission,
    ValidationPolicy validation,
    PresentationPolicy presentation
) {}
```

All policies are immutable response objects. They represent the rules applicable to the current attempt.

---

## 11. Delivery capabilities

Capabilities answer the question: what can the user interface offer?

Recommended fields.

```text
backNavigation
sectionSwitching
questionReview
calculator
pause
languageSwitch
explanationVisibility
hintVisibility
questionPalette
markForReview
clearResponse
```

Example.

```json
{
  "backNavigation": false,
  "sectionSwitching": false,
  "questionReview": true,
  "calculator": true,
  "pause": false,
  "languageSwitch": true,
  "explanationVisibility": false,
  "hintVisibility": false,
  "questionPalette": true,
  "markForReview": true,
  "clearResponse": true
}
```

Capabilities are a UI affordance contract, not the security boundary. Every action is still validated server-side.

---

## 12. Navigation policy

Navigation policy defines the allowed logical movement inside the assessment.

Recommended fields.

```text
mode
allowBackNavigation
allowForwardNavigation
allowSectionSwitch
allowQuestionReview
allowDirectQuestionJump
allowClearResponse
```

Example.

```json
{
  "mode": "SEQUENTIAL",
  "allowBackNavigation": false,
  "allowForwardNavigation": true,
  "allowSectionSwitch": false,
  "allowQuestionReview": true,
  "allowDirectQuestionJump": false,
  "allowClearResponse": true
}
```

Supported conceptual navigation modes.

```text
FREE
SEQUENTIAL
SECTION_SEQUENTIAL
CUSTOM
```

---

## 13. Timer policy

The timer policy describes how time is applied.

Recommended fields.

```text
mode
serverAuthoritative
autoSubmitOnExpiry
sectionExpiryBehavior
showRemainingTime
```

Example.

```json
{
  "mode": "SECTION_WISE",
  "serverAuthoritative": true,
  "autoSubmitOnExpiry": true,
  "sectionExpiryBehavior": "LOCK_SECTION",
  "showRemainingTime": true
}
```

Supported conceptual timer modes.

```text
NONE
GLOBAL
SECTION_WISE
```

The timer mode alone does not determine the authoritative deadline.

---

## 14. Server-authoritative timing contract

The backend is the only authority for timing. The browser is a display client.

The backend provides sufficient timing metadata for the frontend to render a countdown without making the browser the source of truth.

Recommended payload.

```json
{
  "serverTime": "2026-10-10T10:00:03.412Z",
  "startedOn": "2026-10-10T10:00:00.000Z",
  "deadline": "2026-10-10T11:00:00.000Z"
}
```

For section-wise timing.

```json
{
  "timer": {
    "mode": "SECTION_WISE",
    "serverAuthoritative": true,
    "currentSection": {
      "sectionName": "Physics",
      "startedOn": "2026-10-10T10:20:00.000Z",
      "deadline": "2026-10-10T10:40:00.000Z"
    }
  }
}
```

The frontend calculates an approximate local countdown from the authoritative timestamps. At every important action the backend re-evaluates the true deadline.

The client never submits remaining time as a trusted value.

---

## 15. Submission policy

Submission policy defines manual submission, automatic submission, section expiry, full expiry, and retry semantics.

Example.

```json
{
  "allowManualSubmit": true,
  "autoSubmitOnExpiry": true,
  "autoSubmitOnSectionExpiry": false,
  "requireConfirmation": true
}
```

The frontend may display a confirmation dialog. The backend still controls whether submission is valid.

---

## 16. Validation policy

Validation policy describes constraints the backend applies to a delivery request.

May include.

```text
responseRequiredBeforeNext
sectionCompletionRequired
singleResponseOnly
allowEmptyResponse
```

Example.

```json
{
  "responseRequiredBeforeNext": false,
  "sectionCompletionRequired": false,
  "singleResponseOnly": true,
  "allowEmptyResponse": true
}
```

Values are informational to the frontend and authoritative only when enforced by backend services.

---

## 17. Presentation policy

Presentation policy is limited to declarative display behavior that is required by the examination experience.

Recommended fields.

```text
palettePosition
showQuestionTypeBadge
showSectionName
showProgress
showCalculator
layoutDensity
```

Example.

```json
{
  "palettePosition": "RIGHT",
  "showQuestionTypeBadge": true,
  "showSectionName": true,
  "showProgress": true,
  "showCalculator": true,
  "layoutDensity": "COMPACT"
}
```

Presentation policy never contains executable code.

Forbidden.

```json
{
  "customComponent": "..."
}
```

Forbidden.

```json
{
  "javascript": "..."
}
```

Forbidden.

```json
{
  "renderScript": "..."
}
```

CDUI is declarative, not a code execution mechanism.

---

## 18. Complete DeliveryConfiguration example

```json
{
  "engineType": "NTA",
  "mode": "EXAM",
  "capabilities": {
    "backNavigation": false,
    "sectionSwitching": false,
    "questionReview": true,
    "calculator": true,
    "pause": false,
    "languageSwitch": true,
    "explanationVisibility": false,
    "hintVisibility": false,
    "questionPalette": true,
    "markForReview": true,
    "clearResponse": true
  },
  "navigation": {
    "mode": "SECTION_SEQUENTIAL",
    "allowBackNavigation": false,
    "allowForwardNavigation": true,
    "allowSectionSwitch": false,
    "allowQuestionReview": true,
    "allowDirectQuestionJump": false,
    "allowClearResponse": true
  },
  "timer": {
    "mode": "SECTION_WISE",
    "serverAuthoritative": true,
    "autoSubmitOnExpiry": true,
    "sectionExpiryBehavior": "LOCK_SECTION",
    "showRemainingTime": true
  },
  "submission": {
    "allowManualSubmit": true,
    "autoSubmitOnExpiry": true,
    "autoSubmitOnSectionExpiry": false,
    "requireConfirmation": true
  },
  "validation": {
    "responseRequiredBeforeNext": false,
    "sectionCompletionRequired": false,
    "singleResponseOnly": true,
    "allowEmptyResponse": true
  },
  "presentation": {
    "palettePosition": "RIGHT",
    "showQuestionTypeBadge": true,
    "showSectionName": true,
    "showProgress": true,
    "showCalculator": true,
    "layoutDensity": "COMPACT"
  }
}
```

The `engineType` field may be returned for diagnostics or support tools but must not become a frontend branching key. The student-facing React component tree remains engine-agnostic.

---

## 19. Practice vs Exam mode

The delivery modes.

```text
PRACTICE
EXAM
```

Mode is determined server-side and persisted immutably on `StudentAttempt.attempt_mode`.

### Practice

May permit flexible navigation, explanation visibility, hint visibility, no enforced duration where configured, and repeated interaction according to assessment rules.

### Exam

Applies the configured official examination rules.

The frontend does not allow the user to switch between PRACTICE and EXAM during an attempt. The server rejects an attempt-mode mismatch.

---

## 20. Attempt snapshot interaction

The ExamEngine operates on the already prepared attempt.

The snapshot hierarchy is:

```text
AssessmentVersion
      |
      v
StudentAttempt
      |
      v
AttemptQuestion
      |
      v
AttemptQuestionOption
```

The snapshot preserves:

- Exact QuestionVersion
- Exact question display order
- Exact section placement
- Exact marks
- Exact negative marks
- Exact QuestionOption ordering

The ExamEngine must not reorder the snapshot after the attempt begins. Randomization, where supported, occurs during preparation before the exam window.

---

## 21. Snapshot readiness contract

Start Exam requires.

```text
StudentAttempt.attempt_status = NOT_STARTED
AND
StudentAttempt.snapshot_status = READY
```

The public Start Exam request must not generate missing snapshot rows.

When the snapshot is not ready.

```text
HTTP 409
PREPARATION_NOT_READY
```

This preserves the low-write Start Exam path.

---

## 22. Start Exam API contract

Endpoint.

```http
POST /api/v1/assessment-schedules/{scheduleId}/start
```

The backend performs.

```text
1.  Authenticate.
2.  Authorize.
3.  Verify access.
4.  Verify schedule window.
5.  Resolve prepared StudentAttempt.
6.  Verify attempt is eligible.
7.  Verify snapshot_status = READY.
8.  Resolve ExamEngine.
9.  Resolve DeliveryConfiguration.
10. Transition attempt to IN_PROGRESS.
11. Stamp started_on.
12. Create or activate lightweight AssessmentSession.
13. Return session context and normalized configuration.
```

The endpoint does not perform AttemptQuestion insertion, AttemptQuestionOption insertion, question randomization, or bulk snapshot generation.

---

## 23. Start Exam response

Recommended response.

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
      "serverAuthoritative": true,
      "deadline": "2026-10-10T11:00:00Z"
    },
    "deliveryConfiguration": {
      "capabilities": {},
      "navigation": {},
      "timer": {},
      "submission": {},
      "validation": {},
      "presentation": {}
    }
  }
}
```

The API may return empty or defaulted objects only where the backend guarantees the frontend can safely interpret them.

---

## 24. Recovery contract

The existing active-session and recovery APIs return the same normalized delivery contract used by Start Exam.

Conceptual recovery flow.

```text
GET active session
      |
      v
Resolve StudentAttempt
      |
      v
Load snapshot
      |
      v
Resolve ExamEngine
      |
      v
Resolve DeliveryConfiguration
      |
      v
Return authoritative runtime state
```

Recovery does not reconstruct a new snapshot. The snapshot is permanent for the attempt.

---

## 25. Attempt Questions API

Endpoint.

```http
GET /api/v1/attempts/{attemptId}/questions
```

The endpoint returns the prepared snapshot.

Recommended shape.

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

The student API never returns `isCorrect`, `answerKey`, internal scoring metadata not required for delivery, reviewer notes, or private audit fields.

---

## 26. Backend enforcement of navigation

The frontend may hide or show buttons based on configuration. The backend independently validates every navigation-sensitive request.

Example: the client claims `allowBackNavigation = true`. The backend ignores the client claim. The backend loads the real policy from the ExamEngine and either allows or rejects the operation.

Possible errors.

```text
INVALID_STATE
AUTHORIZATION_ERROR
NAVIGATION_NOT_ALLOWED
```

A frontend state mutation never bypasses backend rules.

---

## 27. Backend enforcement of section switching

If `allowSectionSwitch = false`, the frontend does not render arbitrary section navigation. Hiding the section control is not security.

The backend rejects a request attempting to access a prohibited section.

The server also verifies current section, attempt state, section expiry, and navigation policy before accepting the request.

---

## 28. Backend enforcement of question access

A client cannot request an arbitrary QuestionVersion merely by changing an ID.

The backend resolves access through Attempt, then AttemptQuestion, then QuestionVersion.

The authoritative question universe for an attempt is the persisted snapshot. A question that is not part of the attempt snapshot is not a valid delivery question for that attempt.

---

## 29. Backend enforcement of response save

Existing endpoint.

```http
PUT /api/v1/attempts/{attemptId}/responses/{questionVersionId}
```

The backend resolves AttemptQuestion from the attempt and question version.

It verifies:

```text
attempt exists
attempt belongs to current user
attempt is IN_PROGRESS
question belongs to attempt
section is available
attempt is not expired
response format is valid
If-Match precondition, where provided
```

The client-provided score, marks, negativeMarks, remainingTime, and attemptStatus are ignored if supplied.

---

## 30. Autosave concurrency contract

Autosave is expected to be highly concurrent.

The backend protects against delayed network requests overwriting newer responses using HTTP conditional requests.

### Save cycle

GET returns `ETag: "resp-{updatedOnMillis}-{crc32OfResponse}"`.

PUT accepts `If-Match: "<etag>"`.

Rules.

| Condition | Behaviour |
|---|---|
| If-Match matches current ETag | Accept, return new ETag |
| If-Match does not match | 412 Precondition Failed with STALE_RESPONSE |
| If-Match: * | Accept only if row exists |
| No If-Match | Accept (first save) |

### On 412

The client re-fetches the current response, merges local state, and retries with the new ETag.

The important invariant.

> An older delayed request never silently overwrites a newer accepted response.

The database uniqueness rule `UNIQUE(attempt_id, question_version_id)` prevents duplicate response records. HTTP conditional requests solve stale-write ordering.

---

## 31. Submission contract

Existing endpoint.

```http
POST /api/v1/attempts/{attemptId}/submit
```

The backend validates attempt ownership, attempt status, session status, deadline, submission eligibility, and exam engine submission policy.

Submission is idempotent. Concurrent submission requests resolve to one valid state transition.

```text
IN_PROGRESS
     |
     v
SUBMITTED
```

Never.

```text
IN_PROGRESS
     |
     v
SUBMITTED
     |
     v
IN_PROGRESS
```

---

## 32. Auto-submission contract

If the timer expires.

```text
Backend detects expiry
        |
        v
Apply submission policy
        |
        v
Finalize response state
        |
        v
Submit attempt
        |
        v
Trigger evaluation
```

The browser timer reaching zero is not sufficient to establish official submission. The frontend may proactively request submission. The backend still decides the final state.

---

## 33. AssessmentConfigProvider

The Next.js student delivery application centralizes delivery configuration.

Recommended structure.

```text
AssessmentPage
   |
   v
AssessmentSessionProvider
   |
   v
AssessmentConfigProvider
   |
   v
AssessmentLayout
   +-- Timer
   +-- QuestionRenderer
   +-- QuestionPalette
   +-- NavigationControls
   +-- SectionNavigator
   +-- SubmissionControl
```

The provider exposes normalized policies.

```ts
interface AssessmentConfig {
  capabilities: DeliveryCapabilities;
  navigation: NavigationPolicy;
  timer: TimerPolicy;
  submission: SubmissionPolicy;
  validation: ValidationPolicy;
  presentation: PresentationPolicy;
}
```

No component fetches raw engine information directly.

---

## 34. React context responsibilities

`AssessmentConfigProvider` is responsible for:

- Validating the API payload with Zod
- Exposing normalized configuration
- Providing safe defaults only where explicitly allowed
- Exposing current section information
- Exposing mode
- Exposing server timing metadata
- Preventing components from depending on raw API response structures

Example.

```tsx
const {
  navigation,
  timer,
  submission,
  presentation,
  capabilities
} = useAssessmentConfig();
```

---

## 35. Zod contract

The frontend validates the normalized delivery configuration.

Conceptual schema.

```ts
const DeliveryCapabilitiesSchema = z.object({
  backNavigation: z.boolean(),
  sectionSwitching: z.boolean(),
  questionReview: z.boolean(),
  calculator: z.boolean(),
  pause: z.boolean(),
  languageSwitch: z.boolean(),
  explanationVisibility: z.boolean(),
  hintVisibility: z.boolean(),
  questionPalette: z.boolean(),
  markForReview: z.boolean(),
  clearResponse: z.boolean(),
});
```

The TypeScript schema matches the approved OpenAPI definition.

Unknown values are not silently coerced into permissive behavior. For example, an unknown timer mode produces a controlled configuration error rather than defaulting to a more permissive timer.

---

## 36. Configuration-driven navigation UI

Example.

```tsx
const { navigation } = useAssessmentConfig();

return (
  <div className="flex gap-4">
    {navigation.allowBackNavigation && (
      <Button onClick={handlePrevious}>
        Previous
      </Button>
    )}

    <Button onClick={handleSaveAndNext}>
      Save & Next
    </Button>

    {navigation.allowQuestionReview && (
      <Button
        variant="outline"
        onClick={handleMarkForReview}
      >
        Mark for Review
      </Button>
    )}
  </div>
);
```

This is presentation logic. The backend remains authoritative.

---

## 37. Configuration-driven layout

Example.

```tsx
const { presentation } = useAssessmentConfig();

return (
  <div className="flex h-screen w-full">
    <main className="flex-1 overflow-auto">
      <QuestionRenderer currentQuestion={question} />
    </main>

    {presentation.palettePosition === "RIGHT" && (
      <aside className="w-80 border-l">
        <QuestionPalette />
      </aside>
    )}
  </div>
);
```

Other values may support.

```text
LEFT
RIGHT
DRAWER
HIDDEN
```

Only controlled enum values are permitted.

---

## 38. Configuration-driven timer UI

The timer component reads.

```text
timer.mode
timer.serverAuthoritative
timer.deadline
```

The frontend calculates display time.

Example.

```text
serverTime = 10:00:00
localTime  = 10:00:01
server deadline = 11:00:00
```

The UI displays an approximate remaining value. The backend remains authoritative.

When an action occurs such as save response, submit, navigate, or recover, the backend re-evaluates timing.

---

## 39. Network time drift handling

The frontend estimates clock drift using server time, local receipt time, and round-trip estimate.

The implementation avoids blindly trusting the device clock.

For example.

```text
Response received at local 10:00:01
Server says 10:00:00
```

The frontend estimates an offset and uses it for visual countdown accuracy. This offset is never a security authority.

---

## 40. Question rendering contract

Question rendering is a separate concern from exam navigation.

The renderer receives a QuestionViewModel.

```ts
interface QuestionViewModel {
  attemptQuestionId: string;
  questionVersionId: string;
  displayOrder: number;
  sectionName?: string;
  marks: number;
  negativeMarks: number;
  requiresLatexParsing: boolean;
  contentFormat: "MARKDOWN_LATEX_V1";
  content: string;
  options: OptionViewModel[];
}
```

The renderer does not know the exam framework.

---

## 41. Canonical content rendering

The frozen content contract is MARKDOWN_LATEX_V1.

Governed fields.

```text
QuestionVersion.content
QuestionOption.content
Explanation.content
Hint.content
```

The approved pipeline.

```text
Canonical content
      |
      v
Markdown AST
      |
      v
LaTeX delimiter handling
      |
      v
KaTeX SSR
      |
      v
Asset shortcode resolution
      |
      v
Sanitization
      |
      v
Safe HTML
```

Raw HTML is not accepted. Executable MDX is not accepted.

---

## 42. LaTeX delimiter contract

The only mathematical delimiters accepted.

Inline.

```text
\( ... \)
```

Block.

```text
\[
...
\]
```

The following are not mathematical delimiters.

```text
$x$
$$x$$
```

This rule prevents currency text such as `$500` from being misinterpreted as mathematical content.

---

## 43. requiresLatexParsing

Question templates may define.

```json
{
  "requires_latex_parsing": true
}
```

or.

```json
{
  "requires_latex_parsing": false
}
```

The API exposes this as `requiresLatexParsing`.

The signal is an optimization hint for the rendering pipeline. It does not change the security contract.

If a content payload claims `requiresLatexParsing = false` but actually contains approved math delimiters, the server-side renderer still applies the correct content rules rather than trusting the client.

---

## 44. Asset shortcode contract

Canonical asset syntax.

```text
[asset: <uuid>]
```

Example.

```text
A resistor of value \(R=10\Omega\) is shown below.

[asset: 550e8400-e29b-41d4-a716-446655440000]

Calculate the current.
```

The renderer resolves the UUID against authorized assets belonging to the same QuestionVersion.

The frontend never constructs an asset URL from raw `file_path` values.

---

## 45. Asset authorization

Asset resolution validates:

```text
asset exists
asset is ACTIVE
asset belongs to QuestionVersion
QuestionVersion belongs to AttemptQuestion
AttemptQuestion belongs to StudentAttempt
StudentAttempt belongs to current user
```

Only then is a protected asset URL generated. The asset path is an implementation detail and is not exposed.

---

## 46. Rendering failure behavior

Malformed content must never crash the entire examination page.

Possible content errors.

```text
INVALID_CONTENT_FORMAT
RAW_HTML_NOT_ALLOWED
INVALID_MATH_DELIMITER
UNSUPPORTED_MATH_DELIMITER
INVALID_ASSET_SHORTCODE
ASSET_NOT_FOUND
ASSET_VERSION_MISMATCH
CONTENT_TOO_LARGE
INVALID_MARKDOWN
```

Question delivery fails safely and observably. The application prefers a controlled content error over an entire React tree crash.

The user-facing fallback does not reveal internal file paths, stack traces, or internal parser details.

---

## 47. Deterministic rendering

Given identical inputs.

```text
QuestionVersion
QuestionOption versions
Asset references
content contract version
renderer version
```

The renderer produces semantically equivalent output.

The rendering contract itself is versioned. Future interpretation changes use `MARKDOWN_LATEX_V2` rather than silently changing V1 meaning.

---

## 48. Explanation and hint visibility

The ExamEngine and DeliveryConfiguration may indicate.

```text
explanationVisibility
hintVisibility
```

For practice mode these may be true. For exam mode they are normally false during the active attempt unless the configured examination rules explicitly allow them.

The backend enforces visibility. It is not sufficient to hide an explanation button in React.

---

## 49. Language switching

Language switching, when enabled, is a capability.

The frontend shows a language selector only when `capabilities.languageSwitch = true`.

The backend validates the selected language against the supported assessment and question versions. Language content remains tied to the correct historical question version.

A language switch does not change QuestionVersion identity, AttemptQuestion identity, marks, negative marks, question order, or option order unless the approved assessment contract explicitly models language-specific delivery behavior.

---

## 50. Calculator capability

The calculator capability is declarative.

```json
"calculator": true
```

The frontend may render a calculator. The backend does not trust any calculator result supplied by the client. Student responses and scoring remain authoritative server-side.

The calculator is not a code execution environment.

---

## 51. Question palette contract

The palette is an interaction aid, not a source of truth.

It may display states such as.

```text
NOT_VISITED
VISITED
ANSWERED
UNANSWERED
MARKED_FOR_REVIEW
ANSWERED_AND_MARKED
LOCKED
```

Palette state is derived from the current attempt state. The frontend does not invent a question status that conflicts with backend state.

---

## 52. Section state contract

For section-based examinations, the backend exposes enough information for the frontend to render current section, section order, section availability, section deadline, and section status.

Conceptual example.

```json
{
  "currentSection": {
    "name": "Physics",
    "displayOrder": 2,
    "status": "ACTIVE",
    "deadline": "2026-10-10T10:40:00Z"
  },
  "sections": [
    {
      "name": "Chemistry",
      "displayOrder": 1,
      "status": "LOCKED"
    },
    {
      "name": "Physics",
      "displayOrder": 2,
      "status": "ACTIVE"
    }
  ]
}
```

---

## 53. Section expiry

When a section expires, the backend applies the engine timer policy.

Possible behaviors.

```text
LOCK_SECTION
AUTO_SUBMIT_SECTION
MOVE_TO_NEXT_SECTION
END_ATTEMPT
```

The frontend reacts to the resulting server state rather than inventing a local transition.

---

## 54. Session and attempt separation

The architecture distinguishes.

```text
AssessmentSession
        is not the same as
StudentAttempt
```

The session is runtime state. The attempt is academic history.

The CDUI configuration is attached to the active runtime context, but the authoritative examination definition remains AssessmentVersion, StudentAttempt, and the prepared snapshot.

---

## 55. Configuration versioning

The delivery contract exposes a contract version.

```text
configurationVersion: "1.0"
```

The frontend rejects unsupported major versions.

```text
1.x is compatible within defined contract rules
2.x requires explicit frontend support
```

This prevents backend configuration changes from silently breaking older clients.

---

## 56. Unknown configuration handling

Unknown capability values fail closed where the capability would make the system more permissive.

Example: an unknown navigation mode does not become FREE navigation.

Unknown presentation values may fall back only to a safe visual default if that does not alter business behavior.

---

## 57. Cache strategy

The following may be cached when safe.

```text
ExamEngine definitions
Static capability mappings
Published assessment configuration
Question rendering metadata
```

The following are not treated as cache-authoritative.

```text
Attempt status
Time remaining
Submission state
Student response
Score
Result
AccessGrant authorization
```

Redis remains cache-only.

---

## 58. ExamEngine immutability

An engine implementation behaves deterministically for immutable inputs.

It does not mutate AssessmentVersion, BlueprintVersion, QuestionVersion, AnswerKey, or StudentAttempt snapshot.

The engine produces policy objects. It does not silently change persistence state as a side effect of policy resolution.

---

## 59. Engine-specific behavior location

Allowed.

```text
NtaExamEngine
IbpsExamEngine
TcsIonExamEngine
UpscExamEngine
RailwayExamEngine
CustomExamEngine
```

Each implementation may encode the differences required by its framework.

Not allowed.

```text
AssessmentController
   |
   v
if NTA
if IBPS
if UPSC
```

Not allowed.

```text
StudentResponseService
   |
   v
if NTA
if IBPS
```

Not allowed.

```tsx
QuestionPalette
   |
   v
if examName === "NTA"
```

Exam-specific interpretation belongs behind the engine boundary.

---

## 60. Policy resolution is side-effect free

Recommended rule.

```text
resolveConfiguration()
        |
        v
read and derive
        |
        v
return policy
```

Not.

```text
resolveConfiguration()
        |
        v
write database
        |
        v
change attempt
```

Any state transition occurs in an explicit Delivery service operation.

---

## 61. Frontend architecture

Recommended folder structure.

```text
frontend/
  app/
    assessment/
      [scheduleId]/
        page.tsx
      runtime/
        page.tsx
  features/
    assessment/
      api/
      components/
      config/
        schema.ts
        provider.tsx
        selectors.ts
      hooks/
      models/
      rendering/
        markdown/
        math/
        assets/
      state/
      timer/
      validation/
```

The feature does not contain exam-name-specific directories.

Avoid.

```text
features/assessment/nta/
features/assessment/ibps/
features/assessment/upsc/
```

Unless a future requirement proves a genuine UI capability that cannot be normalized.

---

## 62. When a framework-specific UI component is allowed

A framework-specific component is allowed only when the difference cannot reasonably be expressed through normalized capabilities.

Before introducing such a component, engineering documents the following.

```text
1. Why existing capabilities are insufficient
2. Why normalization is not practical
3. Security implications
4. Testing implications
5. Future engine impact
```

The default assumption remains that the difference belongs in policy configuration rather than custom UI code.

---

## 63. Delivery event model

The frontend thinks in terms of user actions and events.

```text
START
VIEW_QUESTION
SELECT_OPTION
SAVE_RESPONSE
MARK_REVIEW
CLEAR_RESPONSE
NAVIGATE_NEXT
NAVIGATE_PREVIOUS
SWITCH_SECTION
SUBMIT
RECOVER
```

Each event maps to an API operation or controlled local UI update.

The backend remains authoritative for business state.

---

## 64. Optimistic UI rules

Optimistic UI may be used only for harmless presentation state.

Examples allowed.

```text
Button loading state
Local palette highlight
Pending-save indicator
```

Examples not authoritative.

```text
submitted = true
score = 100
sectionUnlocked = true
attemptFinished = true
```

The UI reconciles with backend responses.

---

## 65. Save response UX

Recommended flow.

```text
Student action
      |
      v
Local pending state
      |
      v
PUT response
      |
      v
Server success
      |
      v
Mark synchronized
```

On network failure, the frontend retries according to a safe idempotency strategy. The UI does not fabricate success.

The user receives an understandable synchronization indicator.

---

## 66. Offline and connectivity resilience

The pilot does not make offline mode an independent source of truth.

The frontend may retain temporary unsent state to protect against transient network outages. However.

```text
Local state
   is not
Official response
```

A response becomes official only when accepted by the backend.

---

## 67. Error taxonomy

The frontend understands business errors such as.

```text
PREPARATION_NOT_READY
INVALID_STATE
AUTHORIZATION_ERROR
NOT_FOUND
CONFLICT
RATE_LIMITED
SYSTEM_ERROR
```

Assessment-specific errors may include.

```text
NAVIGATION_NOT_ALLOWED
SECTION_LOCKED
ATTEMPT_EXPIRED
ATTEMPT_ALREADY_SUBMITTED
QUESTION_NOT_IN_ATTEMPT
SUBMISSION_NOT_ALLOWED
STALE_RESPONSE
```

The frontend maps errors to actions without exposing internal implementation details.

---

## 68. PREPARATION_NOT_READY

If a start request returns.

```text
HTTP 409
PREPARATION_NOT_READY
```

The frontend does not automatically trigger synchronous snapshot generation.

It shows a controlled state, retries only according to approved UX behavior, and provides useful messaging.

Operational staff monitor why preparation was not ready.

---

## 69. Security boundary

The following are never trusted from the frontend.

```text
engineType
attemptStatus
snapshotStatus
remainingTime
score
marks
negativeMarks
access status
permissions
submission status
section authorization
```

The frontend may receive these values for display, but the server determines their official values.

---

## 70. No correct answer leakage

The question delivery payload never includes.

```text
isCorrect
correctOptionId
answerKey
explanation during exam when prohibited
internal evaluation rule details
```

This is tested through API contract tests and security tests.

---

## 71. API response minimization

The delivery APIs return only data necessary for the current user experience.

The payload does not return all QuestionVersion columns, all asset metadata, all audit fields, all template internals, or all engine internals.

This reduces payload size and reduces attack surface.

---

## 72. Performance contract

Performance requirements.

```text
5,000 concurrent users
p95 under 100 ms for applicable APIs
Greater than 99% successful submissions
Less than 1% exam failure rate
99.9% availability target
```

This document adds specific delivery-path acceptance scenarios.

---

## 73. Start burst performance test

The test models a schedule opening to thousands of users.

Required behavior.

```text
Snapshot already READY
        |
        v
5,000 Start requests
        |
        v
No snapshot child-row inserts
```

Measure p50, p95, p99, error rate, database connections, database CPU, PgBouncer pool utilization, application CPU, and application memory.

The test demonstrates that Start Exam remains a low-write runtime operation.

---

## 74. Autosave burst performance test

Simulate concurrent saves such as hundreds or thousands of responses per second.

Test normal requests, retry requests, delayed requests, duplicate requests, and out-of-order requests.

Measure save latency, conflict rate, stale-write rate, database connection usage, PgBouncer queueing, and database CPU.

---

## 75. Submission burst performance test

Simulate a large group of students submitting in a short interval.

Test single submit, duplicate submit, concurrent submit, expired submit, late submit, and network retry.

Verify one valid state transition, no duplicate evaluation, no duplicate result, and greater than 99% successful submissions.

---

## 76. Exam rendering performance test

A 100-question technical assessment is tested with Markdown, inline LaTeX, block LaTeX, assets, and multiple options.

Measure separately API response time, server-side rendering time, HTML payload size, browser hydration time, browser main-thread work, time to interactive, and visual stability.

The API performance target alone is insufficient.

---

## 77. KaTeX performance strategy

KaTeX is the approved renderer.

The rendering layer parses only the content required for the active assessment payload, avoids unnecessary MathJax runtime work, avoids compiling the same content repeatedly when safe to cache, bounds content size, bounds mathematical expression size, reuses static renderer configuration, and avoids synchronous client-side TeX compilation for already server-rendered content.

---

## 78. Content caching

Rendered output may be cached by immutable content identity.

```text
QuestionVersion id
Content contract version
Renderer version
```

Example key.

```text
render-cache-key =
questionVersionId
+
MARKDOWN_LATEX_V1
+
rendererVersion
```

Do not cache a representation that bypasses authorization for protected assets.

---

## 79. PgBouncer interaction

The request path.

```text
Browser
   |
   v
Nginx
   |
   v
Spring Boot
   |
   v
PgBouncer
   |
   v
PostgreSQL
```

The exam engine and CDUI design does not assume a dedicated PostgreSQL connection per user. Transaction scope remains short and explicit.

---

## 80. Transaction rules

Database transactions are not held open while waiting for frontend interaction, network calls, asset downloads, KaTeX rendering, or external APIs.

A Start Exam transaction remains small. An autosave transaction remains small. A submission transaction is carefully bounded.

Snapshot preparation runs separately in background worker transactions.

---

## 81. Snapshot worker interaction

The CDUI contract assumes snapshots are ready before the exam starts.

The worker pipeline.

```text
Schedule
  |
  v
Eligible users
  |
  v
Create or find StudentAttempt
  |
  v
Generate deterministic order
  |
  v
Insert AttemptQuestion
  |
  v
Insert AttemptQuestionOption
  |
  v
Validate completeness
  |
  v
Mark READY
```

The ExamEngine may provide the deterministic rules necessary for ordering and selection. The actual batch persistence belongs to Delivery and Preparation services.

---

## 82. Deterministic randomization

If the configured engine requires randomized order, randomization is deterministic for a prepared attempt.

A suitable implementation may derive a deterministic seed from controlled attempt context.

The seed mechanism satisfies.

```text
same prepared attempt
        |
        v
same question order
same option order
```

The seed does not depend on browser randomness.

After the snapshot is finalized, runtime randomization is prohibited.

---

## 83. Engine contract and question selection

The ExamEngine may answer which ordering and selection rules apply.

The assessment preparation service answers how to persist the resulting snapshot safely.

This separation prevents the engine from becoming a giant orchestration service.

---

## 84. ExamEngine and evaluation boundary

The same engine framework can influence delivery rules and may inform evaluation configuration, but scoring remains a deterministic evaluation concern.

Recommended separation.

```text
ExamEngine
   |
   v
Delivery policies

Assessment and AnswerKey
   |
   v
Evaluation rules

Evaluation service
   |
   v
Official result
```

Do not make an engine implementation directly calculate official scores unless the architecture explicitly assigns that responsibility through the Evaluation module contract.

---

## 85. Test matrix — engine contract

Every engine implementation has tests for type resolution, configuration resolution, navigation policy, timer policy, submission policy, validation policy, presentation policy, practice mode, exam mode, unsupported configuration, and invalid state.

For each supported engine.

```text
TCS_ION
IBPS
NTA
UPSC
RAILWAY
CUSTOM
```

At minimum, one happy path, one restrictive policy case, one invalid configuration case, and one mode-specific case.

---

## 86. Test matrix — backend enforcement

For every configurable capability, test both.

```text
Frontend allowed and backend allowed
```

And.

```text
Frontend forced but backend rejected
```

Examples: back navigation, section switch, question jump, submit, clear response, language switch.

This verifies that CDUI is not being mistaken for authorization.

---

## 87. Test matrix — frontend CDUI

Test that the configuration parses, unknown values are rejected, buttons appear and disappear correctly, layout changes correctly, timer renders correctly, section state renders correctly, error states render correctly, and the configuration version is checked.

Tests do not use exam names as selectors. Prefer `allowBackNavigation = false` over `engineType = NTA`.

---

## 88. Test matrix — content rendering

Required regression cases.

Plain currency.

```text
An item costs $500.
```

Expected: `$500` remains plain text.

Inline math.

```text
Solve \(x^2 = 4\).
```

Block math.

```text
\[
E = mc^2
\]
```

Mixed content.

```text
The cost is $500 and the area is \(A = \pi r^2\).
```

Asset.

```text
[asset: <uuid>]
```

Invalid raw HTML.

```html
<script>alert(1)</script>
```

Expected: REJECTED.

Invalid delimiter.

```text
$x$
```

Expected: not parsed as math.

---

## 89. Security test matrix

The security suite attempts to.

```text
modify engine type from browser
modify attempt mode
change deadline
skip section locks
submit another attempt
read another user's questions
read correct options
read protected assets
inject raw HTML
inject JavaScript
inject unsafe Markdown
inject malformed asset shortcode
supply a stale If-Match
```

Every attack fails at the appropriate boundary.

---

## 90. Observability contract

Metrics include.

```text
exam_engine_configuration_resolution_total
exam_engine_configuration_resolution_failure_total
assessment_start_total
assessment_start_success_total
assessment_start_preparation_not_ready_total
assessment_navigation_rejection_total
assessment_response_save_total
assessment_response_save_conflict_total
assessment_submit_total
assessment_submit_duplicate_total
assessment_session_recovery_total
```

Metric names are adjusted for project naming convention while preserving stable semantic meaning.

---

## 91. Logging contract

Structured logs include.

```text
requestId
userId
attemptId
assessmentVersionId
assessmentScheduleId
engineType where safe
mode
action
result
latency
errorCode
```

Do not log passwords, JWTs, payment secrets, correct answers, or full student responses unless explicitly sanitized and required for diagnostics.

---

## 92. Audit contract

Audit important administrative changes to examination behavior.

```text
Engine configuration change
Policy configuration change
Assessment publish
Schedule publish or update
Delivery configuration change
Manual snapshot preparation trigger
Snapshot repair
```

Runtime student responses remain part of assessment history and are not replaced by generic audit events.

---

## 93. Failure isolation

A failure in one concern does not unnecessarily destroy unrelated functionality.

Examples.

```text
KaTeX rendering issue
        |
        v
Content render failure
        |
        v
Controlled question-level fallback or error
```

Rather than an entire assessment page crash.

Similarly.

```text
ExamEngine configuration resolution failure
        |
        v
Controlled 500 or 409 business error
```

Rather than a server process termination.

---

## 94. Engine registration rules

Each engine is registered once. Avoid dynamic runtime class loading from client input.

Forbidden.

```java
Class.forName(clientProvidedEngine)
```

Allowed.

```text
Controlled enum
        |
        v
Server-side registry
        |
        v
Known engine implementation
```

---

## 95. Configuration schema governance

Configuration objects are versioned and validated.

Do not create a single unrestricted JSON structure such as.

```json
{
  "anything": "anything"
}
```

Instead, define controlled schemas for NavigationPolicy, TimerPolicy, SubmissionPolicy, PresentationPolicy.

The backend validates configuration before publication.

---

## 96. Publication rules

A published assessment has a valid delivery policy.

Before assessment publication, validate engine type supported, mode supported, navigation valid, timing valid, submission valid, question content valid, content format valid, and assets valid.

A published assessment cannot later be modified in a way that changes the historical meaning of its delivery contract. A new AssessmentVersion is required for a material rule change.

---

## 97. Historical reproducibility

Years later, the platform reconstructs.

```text
Which AssessmentVersion
Which QuestionVersion
Which QuestionOption ordering
Which AttemptQuestion ordering
Which mode
Which delivery configuration
Which engine rules applied
Which answer key
Which responses
```

The authoritative reconstruction chain.

```text
AssessmentVersion
      |
      v
StudentAttempt
      |
      v
AttemptQuestion
      |
      v
AttemptQuestionOption
      |
      v
StudentResponse
      |
      v
AnswerKey
      |
      v
AnswerKeyDetail
      |
      v
Evaluation
      |
      v
Result
```

The CDUI configuration does not become a hidden mutable dependency that makes historical reconstruction impossible.

---

## 98. Delivery configuration persistence strategy

Not every runtime presentation value requires a new database column.

The authoritative historical inputs are already represented by AssessmentVersion, BlueprintVersion, AssessmentQuestion, QuestionVersion, QuestionOption, StudentAttempt, and DeliveryPolicy.

Where a normalized runtime configuration is derived from these immutable or controlled sources, the system reconstructs it deterministically.

If a future business requirement introduces a policy that materially affects historical reproducibility and cannot be reconstructed from existing versioned data, that requirement triggers a formal schema and domain review.

Do not solve this by silently storing arbitrary runtime JSON in `StudentAttempt`.

---

## 99. API contract governance

The authoritative API definition is OpenAPI.

Any addition to the DeliveryConfiguration contract updates OpenAPI, TypeScript types, Zod schema, backend DTOs, contract tests, frontend tests, and implementation documentation.

A backend field is not introduced casually without updating the contract.

---

## 100. Backward compatibility

When adding optional capability fields.

```text
Old client
   |
   v
Server response
   |
   v
Safe compatible interpretation
```

When changing the meaning of an existing field.

```text
New contract version
```

Do not silently reinterpret `allowBackNavigation`, `timer mode`, or `submission semantics`.

---

## 101. Implementation guidance

An implementation of the ExamEngine and CDUI follows this document as a contract.

The implementation:

1. Reads the frozen baseline documents.
2. Reads this document.
3. Inspects existing classes before creating new abstractions.
4. Reuses existing module boundaries.
5. Avoids adding tables unless explicitly approved.
6. Avoids changing frozen API semantics.
7. Implements engine behavior behind the ExamEngine boundary.
8. Implements normalized policies.
9. Implements backend enforcement independently of frontend configuration.
10. Adds unit tests.
11. Adds integration tests.
12. Adds API contract tests.
13. Adds frontend tests.
14. Runs the complete relevant build.
15. Reports all assumptions.

The implementation does not:

```text
invent exam-specific UI logic
invent database tables
hard-code exam names into components
trust browser timer values
send correct answers to the browser
create synchronous snapshots from Start Exam
bypass AccessGrant authorization
modify published artifacts
```

---

## 102. Task breakdown

Recommended implementation sequence.

```text
Task 01  Create core ExamEngine interfaces
Task 02  Create normalized policy models
Task 03  Create ExamEngineType enum
Task 04  Create engine registry
Task 05  Implement CUSTOM engine
Task 06  Implement delivery configuration resolution
Task 07  Integrate Start Exam
Task 08  Integrate session recovery
Task 09  Implement backend navigation enforcement
Task 10  Implement backend timer enforcement
Task 11  Implement submission policy enforcement
Task 12  Implement AssessmentConfigProvider
Task 13  Implement Zod validation
Task 14  Implement configuration-driven navigation
Task 15  Implement configuration-driven timer
Task 16  Implement layout capability handling
Task 17  Integrate MARKDOWN_LATEX_V1 renderer
Task 18  Integrate KaTeX SSR
Task 19  Integrate asset shortcode renderer
Task 20  Write security tests
Task 21  Write concurrency tests
Task 22  Write 5,000-user delivery load tests
Task 23  Run full pilot acceptance suite
```

Execute in dependency order with checkpoints.

---

## 103. Definition of done — ExamEngine

```text
[ ] Interface implemented
[ ] Registry implemented
[ ] All six engine types represented
[ ] CUSTOM engine works end-to-end
[ ] Policies normalized
[ ] Configuration validated
[ ] No scattered exam-name conditionals
[ ] Unit tests pass
[ ] Integration tests pass
[ ] Unsupported configuration handled
[ ] Practice mode tested
[ ] Exam mode tested
[ ] OpenAPI reflected
```

---

## 104. Definition of done — CDUI

```text
[ ] AssessmentConfigProvider implemented
[ ] Zod schema implemented
[ ] Navigation reacts to capabilities
[ ] Timer reacts to timer policy
[ ] Layout reacts to presentation policy
[ ] Section controls react to normalized state
[ ] No exam-name branches in assessment components
[ ] Backend remains enforcement authority
[ ] Network errors handled
[ ] Invalid configuration handled safely
[ ] Frontend tests pass
[ ] End-to-end test passes
```

---

## 105. Definition of done — content rendering

```text
[ ] MARKDOWN_LATEX_V1 enforced
[ ] Raw HTML rejected
[ ] Dollar delimiters not treated as math
[ ] \( ... \) supported
[ ] \[ ... \] supported
[ ] KaTeX SSR integrated
[ ] Output sanitized
[ ] Asset shortcode supported
[ ] Asset authorization verified
[ ] Malformed math handled
[ ] Rendering regression corpus passes
[ ] 100-question technical assessment tested
```

---

## 106. Definition of done — delivery integration

```text
[ ] Start uses prepared snapshot
[ ] Start performs no snapshot writes
[ ] Snapshot READY required
[ ] Attempt mode immutable
[ ] Session created or activated
[ ] Delivery configuration returned
[ ] Attempt questions returned from snapshot
[ ] Responses saved idempotently with ETag
[ ] Stale response protection tested
[ ] Submission idempotent
[ ] Timer server authoritative
[ ] Recovery works
[ ] 5,000-user load scenario tested
```

---

## 107. Architecture anti-patterns specific to exam engine and CDUI

### Exam name in React

Reject.

```tsx
if (exam === "NTA") {
   ...
}
```

### Exam name in controller

Reject.

```java
if (assessment.getExam().equals("UPSC")) {
   ...
}
```

### Browser-controlled timer

Reject.

```text
remainingSeconds from client
        |
        v
backend trusts it
```

### Browser-controlled submission

Reject.

```text
frontend status = SUBMITTED
        |
        v
backend accepts
```

### Executable CDUI

Reject configuration that contains code.

### Snapshot on Start

Reject Start request that triggers a 2.5M-row burst.

### Renderer-specific database fields

Reject adding tables solely for `html_content`, `rendered_html`, or `math_fragment` unless a future requirement explicitly proves they are needed.

---

## 108. Architecture review checklist

Before accepting a new ExamEngine capability.

```text
[ ] Can this be represented as normalized configuration?
[ ] Is the capability declarative?
[ ] Is backend enforcement implemented?
[ ] Does it affect timing?
[ ] Does it affect navigation?
[ ] Does it affect submission?
[ ] Does it affect historical reproducibility?
[ ] Does it affect performance?
[ ] Does it affect security?
[ ] Does OpenAPI need updating?
[ ] Does the Zod schema need updating?
[ ] Do tests cover permissive and restrictive cases?
```

---

## 109. Future multi-organization compatibility

The current deployment is single-organization.

This contract nevertheless avoids hard-coding one organization id, one exam catalog, or one engine type.

The engine resolution interface accepts an explicit assessment and organization context where required. Future multi-organization expansion evolves the resolution boundary without requiring every React component to change.

Multi-tenancy itself remains deferred.

---

## 110. Future engine expansion

Adding a future examination framework follows this sequence.

```text
1.  Define framework behavior
2.  Map behavior to normalized capabilities
3.  Implement backend ExamEngine
4.  Add engine tests
5.  Validate assessment configuration
6.  Validate delivery policies
7.  Run end-to-end tests
8.  Run concurrency tests if behavior affects runtime
9.  Do not add framework-specific UI unless normalization is impossible
```

The expected cost of adding an engine is primarily backend policy mapping and testing rather than a rewrite of the student UI.

---

## 111. Future UI capability expansion

A future UI capability is added by.

```text
Backend policy field
      |
      v
OpenAPI
      |
      v
Zod schema
      |
      v
AssessmentConfigProvider
      |
      v
Reusable React capability
```

Example: `showFormulaToolbar` could be introduced once as a reusable capability.

Do not add it separately for every exam framework.

---

## 112. Future AI integration

AI remains outside the official examination-state authority.

Future AI may assist with question generation, question explanation, difficulty estimation, content validation, natural language search, and student insights.

AI does not become authoritative for exam timing, attempt status, submission, score, official evaluation, or access authorization.

The core exam engine remains deterministic and server controlled.

---

## 113. Final cross-layer architecture

```text
                 +---------------------------+
                 | AssessmentVersion         |
                 | BlueprintVersion          |
                 | QuestionVersion           |
                 | AssessmentSchedule        |
                 +-------------+-------------+
                               |
                               v
                      +----------------+
                      | ExamEngine     |
                      | Registry       |
                      +-------+--------+
                              |
                              v
                   +--------------------+
                   | Delivery policies  |
                   +--------------------+
                   | Capabilities       |
                   | Navigation         |
                   | Timer              |
                   | Submission         |
                   | Validation         |
                   | Presentation       |
                   +----------+---------+
                              |
                              v
                      +----------------+
                      | Delivery       |
                      | Service        |
                      +-------+--------+
                              |
          +-------------------+---------------------+
          v                   v                     v
   StudentAttempt      AttemptQuestion      AssessmentSession
          |                   |
          |                   v
          |          AttemptQuestionOption
          |
          v
   StudentResponse
          |
          v
      Evaluation

                              |
                              v
                     Next.js Assessment UI
                              |
                +-------------+-------------+
                v             v             v
             Layout        Timer        Question UI
                |             |             |
                +-------------+-------------+
                              v
                  Configuration-Driven UI
```

---

## 114. Final security boundary

```text
Frontend
   |
   v
Request
   |
   v
Backend
   |
   v
Authoritative business validation
   |
   v
PostgreSQL
```

The frontend configuration is an experience contract. The backend policy is the enforcement contract. The database is the integrity boundary.

---

## 115. Final performance boundary

The Start Exam path is intentionally designed as:

```text
Read prepared attempt
       |
       v
Validate
       |
       v
State transition
       |
       v
Lightweight session operation
       |
       v
Return configuration
```

Rather than:

```text
Generate snapshot
       |
       v
Insert hundreds of rows
       |
       v
Lock database
       |
       v
Return
```

The architecture therefore separates preparation workload from runtime workload. This separation is mandatory.

---

## 116. Final engineering principle

The ExamEngine and CDUI architecture is understood as:

> The backend decides what examination behavior is valid; the normalized policy describes the capabilities available to the experience; the frontend renders those capabilities without knowing the exam brand; and every critical action is revalidated server-side.

The goal is not merely to make multiple exams look different. The goal is to make multiple examination frameworks share one reliable runtime engine while preserving their distinct rules.

---

## 117. Implementation freeze

Before this document is treated as frozen for implementation, engineering confirms.

```text
[ ] ExamEngine interface accepted
[ ] Policy object model accepted
[ ] DeliveryConfiguration JSON accepted
[ ] OpenAPI shape accepted
[ ] Frontend Zod shape accepted
[ ] Backend enforcement rules accepted
[ ] Timer contract accepted
[ ] Snapshot interaction accepted
[ ] Content rendering boundary accepted
[ ] Performance test plan accepted
[ ] Security test plan accepted
[ ] Implementation sequence accepted
```

After this confirmation, the document is the implementation source of truth for the exam engine and CDUI boundary.

---

## 118. Final decision summary

| Area | Frozen direction |
|---|---|
| Backend architecture | Modular monolith |
| Exam abstraction | ExamEngine |
| Supported engine types | TCS_ION, IBPS, NTA, UPSC, RAILWAY, CUSTOM |
| Frontend approach | Configuration-driven UI |
| Frontend exam-name branching | Prohibited |
| Policy model | Capabilities, navigation, timer, submission, validation, presentation |
| Timer authority | Backend |
| Submission authority | Backend |
| Navigation authority | Backend |
| Snapshot creation | Async preparation worker |
| Start Exam | Lightweight activation only |
| Snapshot source | AttemptQuestion and AttemptQuestionOption |
| Math format | MARKDOWN_LATEX_V1 |
| Math delimiters | `\( ... \)` and `\[ ... \]` |
| `$...$` math | Not supported |
| Raw HTML | Rejected |
| Executable MDX | Rejected |
| Math renderer | KaTeX SSR |
| Asset references | `[asset: uuid]` |
| Asset authorization | Backend |
| Correct answers in student API | Prohibited |
| Redis | Cache only |
| PostgreSQL | Transactional source of truth |
| PgBouncer | Production connection pooler |
| Performance target | 5,000 concurrent users, applicable p95 under 100 ms |
| Historical reproducibility | Mandatory |
| Contract versioning | Required |
| Stale-write protection | HTTP ETag |

---

## 119. Document status

**Status:** FROZEN implementation contract for the ExamEngine and Configuration-Driven UI boundary.

This document is the implementation reference for the exam engine abstraction, normalized delivery policy, configuration-driven frontend, backend enforcement, timer authority, navigation authority, submission authority, attempt snapshot interaction, content rendering contract, and performance and security test plans.