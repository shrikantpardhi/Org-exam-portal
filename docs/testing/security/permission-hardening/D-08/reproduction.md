# D-08 — Two-step role escalation via a created role

| Field | Value |
|---|---|
| Defect | D-08 |
| Severity | HIGH |
| Status | **Confirmed — real; step 1 executed live** |
| Reproduced | 2026-10-26, against the running local stack |
| Frozen contract | `10-P1` §63 ("A user cannot assign themselves a role → 403 SELF_ROLE_MODIFICATION"); `20-P11` §17 (privilege escalation must be blocked) |
| Contract conflict | The frozen rule blocks *self-assignment*. It does not block *preparing a role* that a third party then assigns. The two-step path is outside the letter of the rule and inside its intent. |

## Reproduction — step 1 (executed)

The attacker is a normal ADMIN: holds `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN`,
`USER_UPDATE`, and does **not** hold `ADMIN_ROLE_ASSIGN`.

~~~text
POST /api/v1/roles  {"code":"ELEV_TEST","name":"Elevation Test"}      -> 201, role created

PUT  /api/v1/roles/<ELEV_TEST>/permissions
     {"permissionIds": ["22222222-2222-2222-2222-222222222212"]}       -> 200
     response: ["ADMIN_ROLE_ASSIGN"]
~~~

The attacker has now created a role carrying `ADMIN_ROLE_ASSIGN` — a permission the attacker does
not hold — using only permissions the attacker does hold. Verified after cleanup: the role was
deleted and the live catalogue returned to its 7 seeded roles.

## Reproduction — step 2 (not executed; it would require a second live account and would change deployment state)

~~~text
# colleague V, holding USER_UPDATE, assigns ELEV_TEST to the attacker U
PUT /api/v1/users/<U>  {"roles": ["<ELEV_TEST>"]}                     -> 200
# U is now a member of a role carrying ADMIN_ROLE_ASSIGN, which U does not hold by design
~~~

`UserService.replaceRoles` (`UserService.java:210-214`) refuses only when `actorId == targetId`.
When V is the actor and U is the target, the check passes. V is not escalating anything — V is
performing an ordinary, authorized role assignment.

## Why step 2 is asserted rather than performed

Executing it requires a second administrator account, which the one-ADMIN-per-org cap
(`MAX_ADMINS_PER_ORG = 1`) does not permit on this deployment. Step 1 is the whole vulnerability:
once a role exists carrying an escalated permission, any holder of `USER_UPDATE` can complete the
chain without knowing that it is escalated. Task 8's Rule B closes step 1; Rule A covers the case
where the escalation is added to the role *after* creation.

## Blast radius

Escalation from `ROLE_PERMISSION_ASSIGN` to `ADMIN_ROLE_ASSIGN`, which then permits creating a
second administrator — the only deployment-wide brake against which is a business rule
(`MAX_ADMINS_PER_ORG`), not a security invariant.

## Residual if not fixed
Enforcement of §63 is letter-only. Privilege escalation is a two-account operation away.

## Fix class
ADR + two service guards (Task 8), layered on Tasks 2 and 4.  No migration.

## Gate
**Gate A.** Task 8.