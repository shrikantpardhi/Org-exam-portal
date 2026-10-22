# ADR-0026 — The free-assessment rule, and paid-content enforcement at Start Exam

- **Status:** Accepted
- **Phase:** P9 — Commerce & Access Management (rule), enforced in P5 — Delivery (Start Exam)
- **Spec:** `specs/18-P9-Commerce-V1.5.md` §3.2, §9.1, §9.2, §14.1–§14.3, §16, §28.1, §45.3, §99, §100;
  `specs/14-P5-Delivery-V1.5.md` §19, §26–§29
- **Amends:** ADR-0021 §8 (entitlement resolution) and its "not yet enforced" consequence
- **Closes:** GAP-100

## Context

GAP-100: the Commerce entitlement contract exists and is implemented
(`commerce.api.EntitlementResolver`, `EntitlementResolverService`), but Delivery does not consult
it, so **every assessment is startable by any eligible student regardless of purchase**. The
frozen schema has no "free assessment" configuration field, so no rule exists that tells the
platform which assessments are paid and which are free. Until such a rule is approved, paid
content cannot be gated at all.

Two things must therefore be decided here:

1. **Which assessments are free** (the rule itself).
2. **Where and how the rule is enforced** (the Start Exam path), without weakening the P5 Start
   Exam latency budget and without touching the frozen schema or API.

## Decision 1 — The free-assessment rule (derived, not stored)

> An assessment is **free** if and only if **no AccessPackage grants access to it**. Every other
> assessment is **paid** and requires a valid AccessGrant to start.

Formally, for assessment `A` and its assessment version `V` (`V.assessment_id = A`):

| Symbol | Meaning |
|---|---|
| `C(A)` | the assessment collections that contain `A` (via `collection_assessment`) |
| `P(A)` | the access packages mapped to any collection in `C(A)` (via `access_package_collection`) |

```text
A is free   <=>  P(A) is empty
A is paid   <=>  P(A) is not empty
```

Two properties follow directly and are relied on by the implementation:

- **Package entitlement is an assessment-level property.** The frozen mapping binds a package to a
  collection and a collection to an *assessment*; there is no version-level package link
  (`18-P9 §9.2`: "AssessmentCollection references Assessment, not an assessment version. The
  current-version semantics of a package's collection entitlement follow the assessment and
  collection contract"). Every AssessmentVersion of `A` therefore inherits `A`'s free/paid status,
  and the resolver is keyed on `assessmentId`.
- **Free and paid are mutually exclusive and exhaustive.** The same package set decides freeness
  *and* which packages can grant access, so an assessment can never be both free and grantable,
  nor paid and unreachable.

Only `ACTIVE` package-to-collection mappings count, exactly as in the existing entitlement
resolution, so the two never disagree.

### Why this rule rather than an explicit `is_free` flag on the assessment

The alternative — an explicit `is_free` column on `assessment` — was considered and **rejected**:

| Criterion | Derived rule (chosen) | Explicit `is_free` flag (rejected) |
|---|---|---|
| Frozen schema | Unchanged; no column, no table, no endpoint | Needs a new column on a frozen table — a schema amendment through the architecture-change process (`06-Architecture-Freeze §19`, `specs/01 §4`) |
| Single source of truth | The package→collection→assessment graph *is* the commercial truth; a package mapping both sells the assessment and authorizes it | A second, independent statement of paid-ness that can contradict the package mapping (an assessment sold by a package yet flagged free, or unlocked by a grant yet flagged paid) |
| Consistency with `18-P9 §3.2` / `§9.1` | AccessGrant stays the only authorization source; a purchase never authorizes | A per-assessment flag would let freeness be granted without any grant, weakening "AccessGrant is the authorization source of truth" |
| Operational cost | None. Selling an assessment = mapping its collection to a package; unstopselling = removing the mapping | Every new assessment needs a second, manually maintained field, and every package/collection change needs a flag audit |
| Migration / rollout | None. Existing content is free by construction, so nothing is locked out at upgrade time | A backfill and a default value for existing rows, with the resulting lockout of wrongly-flagged content |

The derived rule also degrades safely: content that is not deliberately sold is open, and content
that is sold is closed. The one case it cannot express is "this assessment is free even though it
sits in a sold collection" (a free sample inside a paid bundle). That case needs a deliberate
business rule and, if it ever arises, a schema amendment — it is not a reason to carry a
second source of truth today.

## Decision 2 — Enforcement point and error contract

The rule is enforced **in the Start Exam path**, in Delivery, at the admission boundary:

```text
POST /api/v1/assessment-schedules/{scheduleId}/start
  |
  +-- schedule validation (active, open window)      [P5 §19]
  +-- published assessment version resolved
  +-- ENTITLEMENT GATE  <-- new
  |     EntitlementResolver.checkAssessmentAccess(userId, assessmentId, serverNow)
  |       ALLOW (free)            -> continue
  |       ALLOW (active grant)     -> continue
  |       DENY                     -> HTTP 403, code ACCESS_DENIED_PAID_CONTENT
  +-- attempt resolution and NOT_STARTED -> IN_PROGRESS transition
```

The gate is implemented by `delivery/application/EntitlementAccessGate`, which is the single place
where the access decision is turned into a request outcome. It sits after schedule validation and
before the attempt transition, so a denied start mutates no attempt state: the attempt stays
`NOT_STARTED` and no session is created.

Properties of the gate:

- **Server-side and unbypassable.** The decision is derived only from the authenticated principal
  and persisted state (the grant and the package→collection→assessment mapping). The request
  carries no access input, and the client cannot switch a paid assessment into an ungated mode
  (`P5 §94`: the client cannot request a different mode; the mode is copied from the schedule by
  the preparation worker).
- **The grant path is unchanged.** AccessGrant remains the only authorization source
  (`18-P9 §3.2`); a purchase or a payment record never authorizes (`§3.1`). The gate only asks
  Commerce whether a grant is currently valid.
- **Time-aware.** Validity uses the server clock (`§14.1`: start inclusive, end inclusive, a null
  end never expires), so the resolver takes `serverNow` as a parameter rather than reading the
  clock itself, and the decision is reproducible.
- **Bounded read.** The gate adds three indexed reads and no writes: the assessment's collections,
  the `ACTIVE` package mappings for those collections, and the valid-grant count over the covering
  packages (`idx_collection_assessment_assessment_id`,
  `idx_access_package_collection_assessment_collection_id`, `idx_access_grant_user_id`). It is
  evaluated once per start, before the transition, and does not scale with the number of questions.
  No decision is cached: `18-P9 §57` caches the catalog, not ALLOW/DENY decisions, and Redis is
  never authoritative for entitlement (`§55`).

The denial is a controlled `403` with the stable error code `ACCESS_DENIED_PAID_CONTENT`, carried
in the standard error envelope (`errors[0].code`), alongside the `AUTHORIZATION_ERROR` category
(`06-Architecture-Freeze §52`).

### One decision, two contract methods

`EntitlementResolver.canAccessAssessment` (`§9.1`) now delegates to the same
`checkAssessmentAccess` decision instead of computing its own, so the boolean and the rich decision
cannot drift apart. One consequence is deliberate and observable: **a free assessment now reports
accessible** through `canAccessAssessment`, where the previous implementation returned `false` for
any assessment with no package mapping. That is the correct reading of `§16` ("Students access free
assessments without a paid purchase when the assessment is configured as free") and of `§9.1`; it
is a behaviour change to the boolean, and it is called out here so a reviewer can see it.


## Decision 3 — Mode scope: PRACTICE schedules are not entitlement-gated

The gate is applied to **EXAM-mode** schedules. **PRACTICE-mode** schedules are not
entitlement-gated: a practice attempt is a rehearsal of published content, not a scored sitting,
and the entitlement in this platform attaches to the official exam attempt. The mode is server-owned
(it is copied from the schedule by the preparation worker, `P5 §94`, §31), so this exemption is
not a client-side bypass: a student cannot move a paid EXAM assessment into an ungated mode.

**Residual, recorded deliberately:** a paid assessment that an operator also schedules in
PRACTICE mode stays reachable by any eligible candidate in that mode. The gate cannot close that
without a per-mode commercial rule, which the frozen schema does not carry. Operators who must
protect a paper schedule it EXAM-only.

## Decision 4 — What the gate deliberately does not do

- It does not narrow the **candidate population**. Attempts are still prepared for the operational
  candidate set (`P5 §19`; GAP-057 defers the AccessGrant-driven population to a later
  amendment). The gate is the *access* control; preparation is unchanged.
- It does not gate the prepared-snapshot read path. `GET /attempts/{id}/questions` remains
  available to the owner of a prepared attempt, as before this change. Closing that read is a
  separate change with its own contract decision; it is recorded here as a known residual, and the
  commercial exposure is bounded by the same operator control as the PRACTICE residual above.
- It does not change any endpoint, DTO, or status vocabulary, and it adds no table, column,
  migration, or configuration value.
- It does not re-authorize an attempt that is already running, and it does not lock a student out of
  a running attempt. The gate is evaluated on every Start Exam request, so if a grant is revoked
  *after* the attempt started, a later Start is denied — but the student is not trapped: autosave,
  session recovery and submission are separate paths that verify ownership and the deadline, not
  the entitlement, and they remain available. Only re-issuing the Start context is denied.

## Consequences

- Paid content is now actually gated at the only boundary a student passes through to sit an
  exam, and the denial is a controlled, documented 403 rather than an implicit 409.
- The free/paid decision lives with the module that owns packages and grants, so it cannot drift
  from the grant resolution that enforces it.
- Entitlements remain reproducible: same attempt, same grant state, same server time, same
  decision.

## Implementation and evidence

| Concern | Where |
|---|---|
| The rule | `commerce/service/EntitlementResolverService.checkAssessmentAccess` |
| The contract | `commerce/api/EntitlementResolver`, `commerce/api/AccessDecision` |
| The gate | `delivery/application/EntitlementAccessGate` |
| The denial | `delivery/exception/PaidContentAccessDeniedException` (HTTP 403, `ACCESS_DENIED_PAID_CONTENT`) |
| The call site | `delivery/application/StartAssessmentService.startAttempt` — after schedule validation, before the attempt transition |
| The evidence | `backend/src/test/java/com/company/exam/integration/delivery/EntitlementGateIntegrationTest.java` — 8 tests |

Full backend suite after this change: **388 tests, 0 failures**.

## Related

- ADR-0021 §8 — entitlement resolution; amended by this record.
- ADR-0025 — multi-correct answer keys (the other closed evaluation-side gap).
- `docs/known-gaps.md` — GAP-100 (closed by this record); GAP-057 (candidate population, still
  deferred).
