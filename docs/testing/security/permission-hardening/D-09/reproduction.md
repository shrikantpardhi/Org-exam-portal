# D-09 — No bounded delegation: a caller may grant permissions it does not hold

| Field | Value |
|---|---|
| Defect | D-09 |
| Severity | MEDIUM as written / **CRITICAL as the root cause** |
| Status | **Confirmed — real** |
| Reproduced | 2026-10-26, against the running local stack |
| Frozen contract | `10-P1` §52 ("An unrestricted API for arbitrary permission mutation is not exposed unless explicitly present in the API contract"); `20-P11` §17 (backend is the security boundary); ADR-0023 decision 2 ("No god mode") |
| Contract conflict | The frozen contract says an *unrestricted* permission-mutation API must not exist. `PUT /roles/{id}/permissions` is that API: it is bounded only by `ROLE_PERMISSION_ASSIGN`, which grants full authority over the entire 163-code catalogue. |

## Reproduction

This is the mechanism observed in D-03 and D-04, restated as its own defect because the fix is
structural rather than a special case:

~~~text
GET /api/v1/me  (as ADMIN)
  -> 144 permission codes
  -> holds ADMIN_ROLE_ASSIGN: false
  -> holds PLATFORM_SYSTEM_VIEW: false

PUT /api/v1/roles/<STAFF>/permissions  { baseline + ADMIN_ROLE_ASSIGN }        -> 200
PUT /api/v1/roles/<STAFF>/permissions  { baseline + PLATFORM_ORGANIZATION_VIEW } -> 200
~~~

Both grants succeeded while the caller held neither code. The caller granted 2 of the 19 codes it
does not hold, through the ordinary role-permission API.

## Why it is listed separately

D-03 and D-04 can each be patched by naming the codes that matter (`PLATFORM_*`, then
`ADMIN_ROLE_ASSIGN`, then the next one discovered). That is an allow-list that must be maintained
against every future permission. D-09 inverts it: the default becomes "you may grant what you
hold", and every new permission is safe without a list edit. `20-P11` §17 calls this the
`backend is the security boundary` property; an enumeration of dangerous codes is a client-side
boundary wearing a server-side costume.

## Blast radius

The same as D-03 and D-04 combined, plus every future permission that ships without being added
to a reserved list. It is the defect that keeps re-opening.

## Residual if not fixed
`ROLE_PERMISSION_ASSIGN` is, in practice, the `SUPER_ADMIN` permission — held by ADMIN.

## Fix class
Guard + ADR (Task 4). No migration (the bypass permission is a seed row, not schema).

## Gate
None. In-scope for Task 4.