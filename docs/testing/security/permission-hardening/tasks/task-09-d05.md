# Task 9 / D-05 — organization-owned roles

**Status:** complete (Gate B approved 2026-10-26, shape **S2**)

| Field | Value |
|---|---|
| Defect | D-05 |
| Fix class | Schema + service + ADR |
| ADR | ADR-0045 |
| Migration | `V35__organization_scoped_roles.sql` |
| Tests | 7 in `OrganizationScopedRoleIntegrationTest` |

## The chosen shape, and the one I recommended

Gate B offered S1 (per-organization copies), S2 (platform roles stay global) and S3 (template + copy).
I recommended **S1**; **S2 was chosen**. The difference is recorded here rather than smoothed over,
because it decides what the project may claim about R2.

## Change set

| File | Change |
|---|---|
| `V35__organization_scoped_roles.sql` | **new** — `role.organization_id`, FK, two partial unique indexes, index |
| `identity/entity/Role.java` | `organizationId`, `isPlatformWide()` |
| `identity/repository/RoleRepository.java` | `findByCodeAndOrganizationIdIsNull`, `findByCodeAndOrganizationId`, `findByOrganizationIdIsNullOrOrganizationId`; **the unscoped `findByCode` was deleted** |
| `identity/service/RoleService.java` | `create` stamps the actor's organization; `list` returns own + platform-wide |
| 8 services | 13 `findByCode` call sites → `findByCodeAndOrganizationIdIsNull` |
| `identity/controller/RoleController.java` | passes the principal |
| `docs/decisions/ADR-0045-organization-scoped-roles.md` | **new** |

## Why the unscoped lookup was deleted rather than deprecated

Leaving `findByCode(code)` available would let a future caller silently resolve a tenant's role where
a platform role was meant. Once codes are unique per scope, the unscoped question has no single
answer — so it should not be askable. The compiler then finds every site, which is how all 13 were
found.

## Stale-class-file trap hit during this task

After deleting `findByCode`, `mvnw -o test` failed with 98 errors of the form
`NoSuchMethodError: RoleRepository.findByCode(String)` — the test classes had not been recompiled
against the new interface. `mvnw -o clean test-compile` fixed it. The failure looked like a runtime
regression and was a build-ordering artefact; recorded because the natural reaction is to go hunting
in the service layer.

## Tests

| Test | Asserts |
|---|---|
| `aCreatedRoleBelongsToTheCreatorsOrganization` | the column is stamped, and `isPlatformWide()` is false |
| `theRoleCatalogueShowsOwnRolesPlusThePlatformWideOnes` | own role visible, seeds visible |
| `anotherOrganizationsPrivateRoleIsNotVisible` | **D-05 itself** — the leak is closed |
| `twoOrganizationsMayEachOwnARoleWithTheSameCode` | the partial-unique design works |
| `theSameCodeCannotBeCreatedTwiceWithinOneOrganization` | the other half of the constraint |
| `aSharedSeededRoleIsStillEditableAcrossOrganizations` | **the accepted residual of S2** — see below |
| `thePlatformWideSeededRolesRemainResolvableByCode` | all seven seeds still resolve, or authorization would fail closed everywhere |

## The test that asserts a known defect

`aSharedSeededRoleIsStillEditableAcrossOrganizations` asserts that an ADMIN of one organization can
rename `STAFF` and every other organization sees it. That is S2's accepted consequence, not desired
behaviour.

It is written as a positive assertion deliberately. When the project moves to S1, this test fails —
and that failure is the signal to re-grade R2 from PARTIALLY met to met. A comment would decay; a
failing test does not.

## The regression this task introduced — and how it was caught

The first version of `list` scoped **every** caller to its own organization, including `SUPER_ADMIN`.
Live probing then showed:

~~~text
ADMIN       visible roles: ... ,LIVE_T9_PROBE2
SUPER_ADMIN visible roles: ...            <- the tenant's role is missing
~~~

Before `V35` every role was global, so the system administrator saw them all. This was a **regression
against R1**, created by the fix for D-05.

**The suite was green with the bug present.** Every role in the fixture was platform-wide, and in that
state the buggy and the correct code return the same list. A scoping change needs data on *both* sides
of the boundary; this fixture had data on one.

Fixed by `RoleService.isSystemAdministrator`, keyed on holding the `SUPER_ADMIN` role rather than on
`PLATFORM`-organization residence — membership is what R1 is about, and a residency rule is untestable
without violating ADR-0032's one-owner invariant. Pinned by `PlatformRoleVisibilityIntegrationTest`
(2 tests, both confirmed to fail against the buggy version). GAP-186, Closed.

## Residual

**R2 is partially met.** Roles an organization creates are isolated; the seven seeded roles remain
shared. See ADR-0045 and `docs/known-gaps.md` GAP-184.