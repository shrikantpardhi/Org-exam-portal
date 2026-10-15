# P11 — Full Administration: Testing Notes

Phase specification: `specs/20-P11-Administration-V1.5.md`.

## Scope verified

- The four frozen administration tables and their boundaries.
- Configuration validation, rules, lookups, and the durable audit trail.
- The admin dashboard, support console masking, global search permission filtering, and system health.

## Integration tests

| Test | What it proves |
|---|---|
| `P11MigrationIntegrationTest.exactlyTheFourAdministrationTablesExist` | the four tables exist and none of the forbidden support/import/bulk/search/health/configuration tables does |
| `P11MigrationIntegrationTest.auditLogHasExactlyTheFrozenColumns` | no request_id / old_values / new_values / ip_address / payload column |
| `P11MigrationIntegrationTest.administrationPermissionsAreSeededOntoAdminAndSupportOnly` | ADMIN and SUPPORT scopes; SUPPORT has no audit/settings/rule/delete capability; students have none; **ADMIN does not hold ADMIN_ROLE_ASSIGN** |
| `P11AdministrationIntegrationTest.settingsAreValidatedAgainstTheRegistryAndAudited` | unknown key rejected, cross-field rule enforced, defaults overlaid, update audited |
| `P11AdministrationIntegrationTest.businessRulesAreDataOnlyAndCodeIsUnique` | executable constructs rejected, code unique, no DELETE |
| `P11AdministrationIntegrationTest.lookupDeleteIsConservative` | an ACTIVE lookup is archived, not physically deleted |
| `P11AdministrationIntegrationTest.auditTrailIsDurableSearchableAndReadOnly` | **audit atomicity and immutability**: a mutation produces a searchable row; no update/delete endpoint; students refused |
| `P11AdministrationIntegrationTest.searchRequiresPermissionAndOmitUnpermittedResources` | **search permission filtering**: students get 403; an unpermitted resource is omitted entirely |
| `P11AdministrationIntegrationTest.healthExposesNoSecrets` | **health secrecy**: 8 components, valid overall, and no password/secret/jdbc:/token/exception text |
| `P11AdministrationIntegrationTest.supportScopeMasksContactByDefault` + `maskingRulesFollowTheFrozenExamples` | **support scope masking**: `j***@example.com`, `+91*****1234`; unmasking only with the permission |
| `P11AdministrationIntegrationTest.supportNotificationGoesThroughTheOwningCommunicationService` | **cross-domain calls go through the owning service**: the notification is created by Communication and audited as a support action |
| `P11AdministrationIntegrationTest.administrationEndpointsArePermissionGated` | every administration endpoint refuses a student |

Run: `cd backend && ./mvnw test -Dtest="P11*"`.

## Manual/curl smoke

```bash
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/organization-settings
curl -X PUT -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"configuration":{"display.timeZone":"Asia/Kolkata"}}' $BASE/api/v1/organization-settings
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/business-rules
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/lookups?category=QUESTION_DIFFICULTY
curl -H "Authorization: Bearer <admin>" "$BASE/api/v1/audit-logs?entityName=Lookup&action=CREATE"
curl -H "Authorization: Bearer <admin>" "$BASE/api/v1/search?q=john&resource=USER"
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/admin/dashboard
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/admin/system-health
curl -H "Authorization: Bearer <support>" $BASE/api/v1/support/users/<userId>
```

## Not implemented in this slice (recorded as gaps)

- Imports and the frozen target registry (P11-16, GAP-119); bulk operations and the frozen operation
  allow-list (P11-17, GAP-120).
- Search handlers for QUESTION, ASSESSMENT, ACCESS_PACKAGE, SUBSCRIPTION_PLAN, PURCHASE and
  ANNOUNCEMENT — only USER and AUDIT_LOG are backed (GAP-118).
- Support session/attempt/access/purchase views and the revoke/recover actions (GAP-121).
- The administration frontend (GAP-123) and the mixed-workload and release tasks (P11-21, P11-22).
