# D-02 — ADMIN can strip the SUPER_ADMIN role's permissions

| Field | Value |
|---|---|
| Defect | D-02 |
| Severity | CRITICAL |
| Status | **Confirmed — real, and exploited on the live stack** |
| Reproduced | 2026-10-26, against the running local stack (edge `http://localhost:8081`) |
| Frozen contract | `specs/20-P11-Administration-V1.5.md` §18 ("SUPER_ADMIN cannot be demoted"; "The last active SUPER_ADMIN is a protected invariant"); ADR-0032 ("Non-demotable", "Non-inactivatable"); ADR-0033 (SUPER_ADMIN is the only cross-org principal) |
| Contract conflict | `10-P1` §63 `SELF_PERMISSION_MODIFICATION` is scoped to "a role the caller BELONGS to". ADMIN does not belong to `SUPER_ADMIN`, so the rule does not fire and no other rule covers the case. ADR-0032's invariants are asserted only for the SUPER_ADMIN **user** (`guardSuperAdminImmutability` in `UserService`), never for the SUPER_ADMIN **role**. |

## Reproduction

~~~text
# 1. ADMIN reads the SUPER_ADMIN role's permissions (allowed)
GET /api/v1/roles/ec235f68-...-11b3b36f82ce/permissions   -> 200, 8 permissions

# 2. ADMIN replaces them with an arbitrary 3-permission subset
PUT /api/v1/roles/ec235f68-...-11b3b36f82ce/permissions
    {"permissionIds": [<3 ids>]}
                                                          -> 200 "Role permissions updated"
~~~

Resulting database state immediately after the call:

~~~sql
SELECT p.code FROM permission p
JOIN role_permission rp ON rp.permission_id = p.id AND rp.status = 'ACTIVE'
JOIN role r ON r.id = rp.role_id WHERE r.code = 'SUPER_ADMIN';
-- PLATFORM_ADMIN_CREATE
-- PLATFORM_ORGANIZATION_CREATE
-- PLATFORM_ORGANIZATION_UPDATE
-- (5 of the 8 codes now INACTIVE)
~~~

`SUPER_ADMIN` can no longer view organizations, view platform status, view system info, or read
or update providers. The install owner is still able to log in (authentication does not depend on
the catalogue), so the failure is silent: the portal loads and every platform query 403s.

## Restoration performed

The reproduction damaged the running stack. Both rows were restored before continuing:

~~~sql
UPDATE role_permission rp SET status = 'ACTIVE'
FROM role r, permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND r.code = 'SUPER_ADMIN' AND p.code LIKE 'PLATFORM\_%';
-- UPDATE 8
~~~

`GET /api/v1/platform/organizations` as SUPER_ADMIN returned 200 again after the restore.

## Root cause

`backend/src/main/java/com/company/exam/identity/service/RoleService.java:114-124`

~~~java
if (actorId != null && userRoleRepository.findByUserIdAndRoleId(actorId, roleId)
        .filter(userRole -> userRole.getStatus() == Status.ACTIVE).isPresent()) {
    throw new SelfPermissionModificationException();
}
~~~

The only guard is membership-based. There is no code-path check, no reserved-role check, and no
"this role is the deployment's last resort" check. `RoleService.update` (line 91-106) has no guard
at all, which is D-07's vector against the same role.

## Blast radius

**Deployment-level lockout with no recovery path.** `10-P1` §63's emergency path is "a documented
direct database procedure operated by a DBA under change control". A compromised or careless ADMIN
therefore forces a DBA intervention on the production database to restore the install owner.

## Residual if not fixed

Any ADMIN can permanently disable the installation owner's platform capability through a normal,
fully-authorized-looking API call. The `SUPER_ADMIN` role's "non-demotable" invariant is
unenforced for the role object itself.

## Fix class
Guard + ADR (no migration). The guard belongs in `RoleService` so it covers `PUT /roles/{id}` and
`PUT /roles/{id}/permissions` and any future caller.

## Gate

None. In-scope for Task 2.