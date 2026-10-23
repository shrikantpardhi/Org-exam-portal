# ADR-0030 — Student reporting is permission-based, not role-based

- **Status:** Accepted
- **Phase:** P13 — Reporting (authorization decision raised by GAP-142)
- **Spec:** `specs/22-P13-Reporting-V1.5.md` §74 (catalog visibility), §76 (self-scoping), §109
  (student reporting), §112 (student reports); `specs/10-P1-Identity-V1.5.md` §9 (prefer permission
  checks over role branching)
- **Closes:** GAP-142
- **Migration:** `V28__grant_student_report_permission.sql`

## Context

The student self-service reporting screen exists at `frontend/app/student/reports/page.tsx` and is
wrapped in `<AuthenticatedGuard permission="REPORT_STUDENT_READ">`. Students hold no report
permission, so the guard never passes and the screen is present but unreachable.

`V24` recorded the reason:

> STUDENT deliberately receives no report permission: P1 froze the invariant that a student's
> permission set is empty (students are authorized by role and self-scope, not by permission codes).

That comment stated the constraint more broadly than the frozen contracts do. Reading the actual
evidence:

1. **`10-P1 §9` says the opposite of role branching**: "Do not encode business authorization as
   `if role == ADMIN` except for truly role-specific platform behavior approved by the authorization
   design. Prefer permission checks."
2. **`10-P1 §66` defines what a student does receive**: "STUDENT -> self-service session and
   current-user permissions only". A self-service report read is exactly that class of capability. The
   contract does not say the student permission set is empty; it says it is limited to self-service.
3. **The gap tests forbid specific codes, not an empty set.** `P11MigrationIntegrationTest` asserts a
   `STUDENT` holds no `SUPPORT_%`, `GLOBAL_SEARCH%` or `AUDIT_LOG_VIEW` permission.
   `P13MigrationIntegrationTest` asserts a `STUDENT` holds no `REPORT_OPERATIONS_READ`,
   `REPORT_EXECUTIVE_READ`, `REPORT_COMMERCE_READ`, `REPORT_ASSESSMENT_READ` or
   `REPORT_CONTENT_READ`. Neither mentions `REPORT_STUDENT_READ`.
4. **One test did assert the stronger claim.** `IdentityAuthenticationIntegrationTest
   .aStudentHasNoAdministrativePermissions` asserted `$.data.permissions` `isEmpty()`. That assertion
   is **modified** by this ADR, deliberately and with the reason recorded here — not deleted, and not
   weakened into silence. Its name states its real intent ("no *administrative* permissions"), and the
   rewritten assertion is both faithful to that name and stricter than an emptiness check would have
   been had the set grown for any other reason: it pins the exact set to `["REPORT_STUDENT_READ"]` and
   additionally asserts the absence of every `USER_*`, `ROLE_*`, `AUDIT_*` permission and of
   `ADMIN_ROLE_ASSIGN`. See "Changed test assertions" below.
5. **`22-P13 §74` and §76 already assume the permission exists.** The catalog filters entries by
   `authorizationService.hasPermission(...)` on `entry.permission()`; the student-scoped report types
   declare `REPORT_STUDENT_READ`. The permission code was seeded in `V24` for exactly this purpose —
   it is only the role grant that is missing.

Read together, the frozen contracts protect two things: a student receives no cross-user, operational,
executive, assessment, content or commerce reporting capability, **and** a student's capabilities are
confined to self-service over their own data. Granting one self-service report permission satisfies
both. The earlier "empty permission set" comment expressed a stricter rule than any frozen contract
states, and this ADR is where that is corrected.

## Decision

**Student reporting is permission-based.** `REPORT_STUDENT_READ` is granted to the `STUDENT` role.
The role-based alternative — gating the UI on `user.roles.includes("STUDENT")` — is rejected.

Rationale, in order of weight:

1. **`10-P1 §9` forbids it.** A role check in the UI and a role check in the backend is precisely the
   `if role == …` pattern the spec tells us not to encode.
2. **The backend already self-scopes**, so a permission is not a widening. `ReportController.isStudent`
   treats any caller lacking `REPORT_OPERATIONS_READ` and `REPORT_EXECUTIVE_READ` as a student, and
   `ReportService.scopedFilters` forces `studentId = callerId` for such a caller before any provider
   runs. `ReportCriteria.scopedSubject` applies the same rule again at projection time. Granting
   `REPORT_STUDENT_READ` lets the existing self-scope do its job; it does not let a student reach
   another student's data, because no path exists that would honour such a filter.
3. **The role check would be unenforceable where it matters.** Hiding a screen in the UI is not
   authorization. The backend must gate the request regardless, and it gates on the permission — so a
   role-based UI with a permission-based backend is two different rules for one capability.

**What is granted:** exactly one permission, `REPORT_STUDENT_READ`, to `STUDENT` only.

**What is explicitly not granted**, and is now pinned by tests rather than by a comment: a student
gains no `REPORT_ASSESSMENT_READ`, `REPORT_CONTENT_READ`, `REPORT_COMMERCE_READ`,
`REPORT_OPERATIONS_READ`, `REPORT_EXECUTIVE_READ`, `REPORT_HISTORY_READ` or `REPORT_GENERATE`.

**Catalog visibility consequence.** A student now sees the four `STUDENT`-category reports in
`GET /reports/catalog` — `STUDENT_PROFILE`, `STUDENT_ATTEMPTS`, `STUDENT_PERFORMANCE`,
`STUDENT_TOPIC_PERFORMANCE` — and nothing else. That is the intended outcome: §74 says the catalog
advertises what the caller may request. It does **not** grant `REPORT_HISTORY_READ`, so the "my report
history" affordance remains unavailable to students; `ReportService.history` self-scopes by user id
already, but history visibility is a separate capability and is left as it is. Recorded in
`docs/known-gaps.md`.

## Changed test assertions

One existing assertion contradicts this decision and is changed rather than deleted:

| Test | Before | After | Why |
|---|---|---|---|
| `IdentityAuthenticationIntegrationTest.aStudentHasNoAdministrativePermissions` | `$.data.permissions` is empty | Exactly `["REPORT_STUDENT_READ"]`, plus absence of every `USER_*`, `ROLE_*`, `AUDIT_*` permission and `ADMIN_ROLE_ASSIGN` | The test's name states its intent — no *administrative* permissions. An empty-set assertion encoded a stricter rule than the contracts state, and would have failed for the wrong reason. The replacement is narrower on administration and exact on the one granted capability |

`P13ReportingIntegrationTest.catalogAdvertisesOnlyPermittedReportTypes` previously asserted the
student catalog `.doesNotContain("STUDENT_PROFILE")`. That assertion directly contradicts §74 once a
student holds the permission, so it now asserts the positive (the four student types are advertised)
and keeps the negative that still holds (no OPERATIONS, EXECUTIVE, COMMERCE, ASSESSMENT or CONTENT
type). The test is strengthened, not relaxed: it now checks both directions.

## Consequences

- The student reporting screen becomes reachable, which is the point of GAP-142.
- `V24`'s closing comment is corrected by `V28` rather than being silently contradicted. `V24` is
  already applied in every environment, so its comment is left in place and `V28` records the
  supersession — migrations are append-only.
- The student permission set is no longer empty. The two invariant tests still pass, because both
  assert absence of *specific* codes that remain ungranted.
- A future reader who finds "students hold no permission" in an older comment must read this ADR. The
  accurate statement is: students hold `REPORT_STUDENT_READ` and no other report capability, and every
  student report is self-scoped by the backend twice over.

## Rollback

```sql
DELETE FROM role_permission rp
 USING role r, permission p
 WHERE rp.role_id = r.id AND rp.permission_id = p.id
   AND r.code = 'STUDENT' AND p.code = 'REPORT_STUDENT_READ';
```

Through Flyway this is a forward migration. The grant is a single row with no FK dependants, so
removal is clean and returns the student set to empty.

## Verification

- `P13MigrationIntegrationTest` gains a case asserting `STUDENT` holds `REPORT_STUDENT_READ` **and**
  still holds none of the seven capabilities above.
- `IdentityAuthenticationIntegrationTest.aStudentHasNoAdministrativePermissions` is updated as
  described in "Changed test assertions": the student's effective permission set is exactly
  `["REPORT_STUDENT_READ"]` and contains no administrative capability.
- `P13ReportingIntegrationTest` gains a case where a student lists the catalog, sees the four
  `STUDENT` types, and sees no `COMMERCE`, `OPERATIONS`, `EXECUTIVE` or `CONTENT` type — then requests
  a student report successfully and is refused a commerce one.
- A test proves the self-scope holds: a student requesting `STUDENT_ATTEMPTS` with
  `studentId` set to another user's id still receives only their own rows.
