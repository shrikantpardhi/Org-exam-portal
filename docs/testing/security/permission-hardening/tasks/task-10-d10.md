# Task 10 / D-10 — the user list is organization-scoped

**Status:** complete (Gate B approved 2026-10-26)

| Field | Value |
|---|---|
| Defect | D-10 |
| Fix class | Service + test |
| ADR | none — the frozen contract being honoured, not amended |
| Migration | none (`user.organization_id` already existed) |
| Tests | 5 in `UserListOrganizationScopeIntegrationTest` |

## The defect, measured

~~~text
GET /api/v1/users  as demo.admin2@example.test (organization DEFAULT)
  -> 5,010 users across both organizations, including superadmin@platform.test
~~~

This was the clearest observable falsification of R2: one organization's administrator reading every
user in the installation.

## Change set

| File | Change |
|---|---|
| `identity/service/UserService.java` | `list` and `get` take the actor; `userSpecification` gains an organization predicate; `requireUserInActorScope` for detail |
| `identity/controller/UserController.java` | passes `principal.userId()` |

## Three decisions worth recording

1. **The scope comes from the actor, never a request parameter.** A client-supplied organization id
   would be an authorization decision taken by the caller.
2. **Detail returns 404, not 403, for another organization.** 403 confirms the id exists; this is the
   same non-disclosure rule `10-P1` §25 already applies to password reset.
3. **A null organization fails closed.** `cb.disjunction()` matches nothing, so an unattributable
   caller sees an empty page rather than the installation. The alternative — treating null as "no
   filter" — is the original defect wearing a different condition.

## Tests

| Test | Asserts |
|---|---|
| `theUserListReturnsOnlyTheCallersOrganization` | the foreign user is absent from the list |
| `aUserInAnotherOrganizationReturns404RatherThan403` | non-disclosure |
| `aUserInTheCallersOwnOrganizationIsStillReadable` | not a blanket 404 |
| `theListCannotBeWidenedByARequestParameter` | `?organizationId=` does not reintroduce the leak |
| `theListIsNotScopedBySeedAssumptions` | an empty organization gets an empty page, never 5,010 |

Assertions are scoped to specific synthetic users rather than counts, because the suite shares one
non-rollback database.