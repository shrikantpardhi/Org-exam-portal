# D-04 — ADMIN can assign ADMIN_ROLE_ASSIGN

| Field | Value |
|---|---|
| Defect | D-04 |
| Severity | CRITICAL |
| Status | **Confirmed — real** |
| Reproduced | 2026-10-26, against the running local stack |
| Frozen contract | `10-P1` §65 ("Plus reserved-but-not-granted admin permissions: `ADMIN_ROLE_ASSIGN`"); `10-P1` §63 ("ADMIN assignment requires `ADMIN_ROLE_ASSIGN`"); ADR-0014 (privileged role change policy); ADR-0023 decision 2 ("`ADMIN_ROLE_ASSIGN` is deliberately *not* granted to ADMIN: P1 froze the invariant that assigning ADMIN requires a permission the ADMIN role does not hold") |
| Contract conflict | ADR-0023 decision 2 states the invariant as a property of the *seed*. Nothing enforces it as a property of the *system*. The invariant is therefore defeated by assigning the permission to a role. |

## Reproduction

~~~text
# ADMIN does not hold ADMIN_ROLE_ASSIGN
GET /api/v1/me   -> permissions: 144 codes, isSubsetOf("ADMIN_ROLE_ASSIGN"): false

# ADMIN grants the permission it does not hold to STAFF
PUT /api/v1/roles/11111111-...-111111111103/permissions
    {"permissionIds": [ <63 baseline ids>, "22222222-2222-2222-2222-222222222212" ]}
                                                                -> 200 "Role permissions updated"

# Restored afterwards -> 200, 63 permissions
~~~

## Why this is the same defect as D-09

D-04 has no independent root cause. It exists because nothing requires the assigner to hold what
it assigns (D-09). The moment bounded delegation is enforced, D-04 closes as a corollary: an ADMIN
cannot grant `ADMIN_ROLE_ASSIGN` because an ADMIN does not hold it.

That is also why the task brief's Task 4 acceptance requires the refusal to be `PERMISSION_NOT_HELD`
(403) and **not** the reserved-set code — the two rules must be distinguishable in tests, or a
future regression in either one is invisible.

## Blast radius

`ADMIN_ROLE_ASSIGN` gates the grant and revocation of the ADMIN role (`UserService.java:226-230`).
An ADMIN who can grant it to any role can then have a colleague place that role on a user, creating
a second administrator — the deployment-wide `MAX_ADMINS_PER_ORG = 1` cap is the only remaining
brake, and it is a business rule (data), not a security invariant.

## Residual if not fixed
`ADMIN_ROLE_ASSIGN` is reserved by documentation only. The ADR-0014 invariant is advisory.

## Fix class
Guard + ADR. Closed as a consequence of Task 4 (bounded delegation); Task 3's reserved set provides
defence in depth.

## Gate
None. In-scope for Task 4.