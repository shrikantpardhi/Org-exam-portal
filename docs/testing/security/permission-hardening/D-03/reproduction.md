# D-03 — ADMIN can assign PLATFORM_* permissions to any role

| Field | Value |
|---|---|
| Defect | D-03 |
| Severity | CRITICAL |
| Status | **Confirmed — real** |
| Reproduced | 2026-10-26, against the running local stack (edge `http://localhost:8081`) |
| Frozen contract | `specs/20-P11-Administration-V1.5.md` §17 ("The admin console tests support to admin escalation ... and unauthorized permission assignment"; "The backend is the security boundary"); §18 (`ADMIN_ROLE_ASSIGN` is a reserved-but-not-granted permission); `10-P1` §65 ("Plus reserved-but-not-granted admin permissions: `ADMIN_ROLE_ASSIGN`") |
| Contract conflict | `specs/04-Database-Schema-V1.5.md` §25.4 gives `permission` no "reserved" attribute, and §25.6 gives `role_permission` no tier marker. ADR-0032 states the platform catalogue is "granted to `SUPER_ADMIN` only", but the *assignability* of those codes was never encoded anywhere. |

## Reproduction

~~~text
# ADMIN baseline for STAFF captured first (63 permission ids)
GET /api/v1/roles/11111111-...-111111111103/permissions         -> 200

# ADMIN adds PLATFORM_ORGANIZATION_VIEW to STAFF
PUT /api/v1/roles/11111111-...-111111111103/permissions
    {"permissionIds": [ <63 baseline ids>, "e6754690-...  PLATFORM_ORGANIZATION_VIEW" ]}
                                                                -> 200 "Role permissions updated"

# STAFF now carries a platform-scope permission. Restored afterwards:
PUT /api/v1/roles/11111111-...-111111111103/permissions  {63 baseline ids}   -> 200, 63 permissions
~~~

## Correction — the catalogue does NOT conceal `PLATFORM_*`

An earlier draft of this record claimed the ADMIN could not *discover* the reserved codes through
`GET /permissions`. **That claim was false and is withdrawn.** Re-verified directly:

~~~text
GET /api/v1/permissions?size=100&page=0   (as ADMIN)  -> 100 rows, PLATFORM_*: 0
GET /api/v1/permissions?size=100&page=1   (as ADMIN)  ->  63 rows, PLATFORM_*: 8
~~~

Scanning every page (163 of 163 permissions) returns all eight `PLATFORM_*` codes, plus
`ADMIN_ROLE_ASSIGN`. The first page simply ends before them because the catalogue sorts by `code`
and `PLATFORM_*` sorts late — the earlier reading was a single-page artefact, not concealment.

This makes D-03 **worse**: the ADMIN can both discover and assign every reserved code. There is no
compensating control anywhere in the permission surface.

## Root cause

`backend/src/main/java/com/company/exam/identity/service/RoleService.java:152-164`

~~~java
private Set<UUID> validatePermissions(List<UUID> permissionIds) {
    List<UUID> distinct = permissionIds.stream().distinct().toList();
    if (distinct.isEmpty()) { return Set.of(); }
    int found = permissionRepository.findAllByIdIn(distinct).size();
    if (found != distinct.size()) { throw new ValidationException(...); }
    return new HashSet<>(distinct);
}
~~~

The only validation is existence. There is no notion of a reserved code, no notion of a
caller-bound permission tier, and `V29` seeds no reserved-set configuration.

## Blast radius

Cross-tier escalation: an organization administrator can move a platform-scope permission onto a
role inside the organization. Combined with any route guarded by a `PLATFORM_*` code, that is an
organization-to-platform escalation (`20-P11` §17, which the spec explicitly says must be tested
and blocked).

At the time of writing the platform routes additionally require a `SUPER_ADMIN` membership in most
paths, so the live exploit is latent rather than immediate — but the permission model itself is
already defeated, and any future route guarded only by a `PLATFORM_*` code becomes exploitable the
moment it ships.

## Residual if not fixed
`ROLE_PERMISSION_ASSIGN` remains unbounded. Reserved codes are reserved by convention only.

## Fix class
Guard + ADR + one seed migration (reserved-set configuration).

## Gate
None. In-scope for Task 3.