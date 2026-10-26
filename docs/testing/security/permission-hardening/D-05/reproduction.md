# D-05 — Roles are global; there is no organization scope on `role`

| Field | Value |
|---|---|
| Defect | D-05 |
| Severity | CRITICAL |
| Status | **Confirmed — real, and structural** |
| Reproduced | 2026-10-26, against the running local stack and the live schema |
| Frozen contract | Requirement R2 ("ADMIN can only manage details within their own organization"); `20-P11` §15 ("Role remains Identity-owned"); ADR-0033 (PLATFORM is a second `organization` row); ADR-0037 ("no cross-org leakage") |
| Contract conflict | **`04-Schema` §25.3 does not define `role.organization_id`.** The frozen schema has no org column on `role` or on `permission`. R2 therefore has no schema support at all: organisation scoping of roles is not a bug in the implementation of a contract, it is a missing contract. |

## Reproduction

~~~sql
-- the entire set of tables carrying an organization scope:
SELECT table_name, column_name FROM information_schema.columns
WHERE column_name = 'organization_id' AND table_schema = 'public';
-- organization_analytics | organization_id
-- organization_setting  | organization_id
-- user                 | organization_id
-- (3 rows — role and permission are NOT among them)
~~~

~~~text
GET /api/v1/roles?size=100   (as ADMIN)
  -> 200, 7 roles: ADMIN, FINANCE, REVIEWER, STAFF, STUDENT, SUPER_ADMIN, SUPPORT
  -> role DTO keys: id, code, name, description, status        (no organization field)
  -> SUPER_ADMIN is visible in the ADMIN's role list
~~~

`RoleDtos.RoleSummary` (`RoleDtos.java:18`) has no organization member, so the API cannot express
the scope even if the schema had it.

## Consequence, stated precisely

Because `role` is global, an ADMIN's `PUT /roles/{id}/permissions` mutates the role row that every
user in every organization carries — including the `SUPER_ADMIN` in the `PLATFORM` organization.
That is exactly the mechanism of D-02, and it is why D-02 is a deployment-level defect rather than
an organization-level one. D-05 is the structural cause; D-02 is the visible symptom.

The live deployment currently holds 5,010 users across 2 organizations (5,009 in `DEFAULT`, 1 in
`PLATFORM`) sharing one global role catalogue of 7 roles and one global catalogue of 163
permissions.

## Blast radius

Requirement R2 is unmet at the schema level. Any future second business organization shares its
ADMIN role, its STUDENT role, and every custom role with the first organization, so role edits
leak across tenants by construction.

## Residual if not fixed

R2 cannot be satisfied. Multi-organization (V2) inherits a global role catalogue.

## Fix class
**Schema amendment + ADR (LARGE).** `role.organization_id UUID` is a new column on a frozen table:
`04-Schema` §25.3 must be amended, an expand-and-contract migration pair is required, and every
role API in `05-API-Contract` §24 gains an org-scoping rule.

## Gate
**Gate B.** This is Task 9 and must not start without explicit human approval.