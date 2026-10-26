# D-11 — `audit_log` has no `organization_id`

| Field | Value |
|---|---|
| Defect | D-11 |
| Severity | MEDIUM |
| Status | **Confirmed — real, structural** |
| Reproduced | 2026-10-26, against the live schema and the running stack |
| Frozen contract | `04-Schema` §34.4 (`audit_log` columns); `docs/frontend/admin-flow.md` §15 ("only their organization's audit trail"); ADR-0023 decision 3-4 (audit is a shared boundary, atomic with the mutation) |
| Contract conflict | `04-Schema` §34.4 defines `audit_log` without an organization attribute. The admin-flow promise is a *join* (`audit_log.user_id → user.organization_id`), never an attribute. |

## Reproduction

~~~sql
SELECT column_name FROM information_schema.columns
WHERE table_name = 'audit_log' ORDER BY ordinal_position;
-- id, user_id, entity_name, entity_id, action, remarks, created_on
~~~

~~~text
GET /api/v1/audit-logs?size=5   (as ADMIN)
  -> 200, pagination.totalElements = 5867
  -> row keys: id, userId, entityName, entityId, action, remarks, createdOn
  -> (no organization attribute on the response either)
~~~

5,867 rows are returned to the DEFAULT-organization ADMIN. That total includes rows written by
users of the PLATFORM organization (e.g. `SUPER_ADMIN_BOOTSTRAP`, `PASSWORD_CHANGE`, and every
`ORGANIZATION_CREATE` the install owner performed).

## The attribution problem, precisely

Because the only link is `user_id`, an audit row's organization is *derived*:

```sql
-- the join admin-flow.md §15 implies
SELECT a.* FROM audit_log a JOIN "user" u ON u.id = a.user_id
WHERE u.organization_id = :callerOrgId;
```

Three failures follow, and none of them is hypothetical:

1. **The join is mutable.** Moving a user between organizations rewrites the historical
   attribution of every audit row they ever wrote, because the attribute lives on the user, not on
   the event.
2. **The system actor has no organization at all.** `SecurityEventLogger` writes with
   `SYSTEM_ACTOR` (`00000000-0000-0000-0000-000000000001`) for bootstrap and scheduled work. If that
   user row does not exist, the join drops the row entirely; if it does, the row's organization is
   whatever the system user happens to be in. Rows written by the bootstrap are the ones an auditor
   most wants to attribute.
3. **`entity_id` does not help.** An audit row about an `Organization` or a `BusinessRule` carries
   the entity UUID, but `idx_audit_log_entity_id` is an index on a UUID column with no
   organization partition — it answers "what happened to this object", never "what happened in
   this organization".

## Blast radius

The ADMIN audit explorer returns the whole deployment's audit trail. The screen's stated purpose
("only their organization's audit trail") is not implemented and cannot be implemented correctly
without the attribute.

## Residual if not fixed
Audit isolation is a join that depends on current user data, not on the recorded event.

## Fix class
**Schema amendment + ADR (LARGE).** `audit_log.organization_id UUID NULLABLE` — nullable because
historic rows and SYSTEM_ACTOR rows have no knowable organization.

## Gate
**Gate B.** Task 11.