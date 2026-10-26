# Config & System-Info Surfaces — Design

Two config surfaces exist, scoped by role. Every field below is read-only display of data
the backend already owns; no screen invents a value or a setting.

## SUPER_ADMIN surface (`/platform/*`)

### System information — `/platform/system`

| Field | Source |
|---|---|
| App version | `GET /platform/system` → `appVersion` (from `app.info.frontend-version` / build info) |
| Git commit | `GET /platform/system` → `gitCommit` |
| Build date | `GET /platform/system` → `buildDate` |
| Migration version | `GET /platform/system` → `lastMigrationVersion` (Flyway) |
| Uptime | `GET /platform/system` → `uptime` |
| Dependency health | `GET /platform/system` → `dependencies` (name → status/version) |

### Platform status — `/platform/status`

| Field | Source |
|---|---|
| Feature flag state | `GET /platform/status` → `featureFlags` |
| Overall health | `GET /platform/status` → `status` |
| Component status | `GET /platform/status` → `components` |

### Provider configuration — `/platform/providers`

| Field | Source |
|---|---|
| Active provider (email / payment) | `GET /platform/providers` → `email`, `payment` |
| Provider name | `providerName` |
| Configured flag / from-address / updatedAt | `configured`, `fromAddress`, `updatedAt` |
| Webhook secret set? | `webhookSecretSet` (boolean only — never the secret) |

### Deliberately hidden from SUPER_ADMIN

Nothing is hidden from SUPER_ADMIN at the platform level **except** provider secrets: the
surface reports `webhookSecretSet: true|false` and never echoes the secret value
(ADR-0033 C5, GAP-167).

## ADMIN surface (`/admin/*`)

### Organization-scoped configuration — `/admin/organization-config`

| Field | Source |
|---|---|
| Organization name / code / timezone | `GET /admin/organization-config` → `organization` |
| Feature flags | `featureFlags` |
| Provider status | `providerStatus` (`emailConfigured`, `paymentConfigured` booleans) |
| Last migration version | `lastMigrationVersion` |
| App version | `appVersion` |

### Organization settings — `/admin/settings`

Backed by the existing `GET /api/v1/organization-settings` (P11). Settings names and
values from `organization_setting`; no secrets.

### Business rules — `/admin/business-rules`

Active business rules from `GET /api/v1/business-rules` (P11).

### Lookups — `/admin/lookups`

Active lookups from `GET /api/v1/lookups` (P11).

### Audit summary — `/admin/audit`

Summary + filtered list from `GET /api/v1/audit-logs` (P11). The ADMIN sees only
their organization's audit trail.

### Deliberately hidden from ADMIN

Containers, database internals, git commit, dependency health, and the platform organization
itself are **not** surfaced to ADMIN. The ADMIN config surface stops at the organization
boundary; `GET /platform/system` and `GET /platform/status` are SUPER_ADMIN-only
(PLATFORM_SYSTEM_VIEW / PLATFORM_STATUS_VIEW). Provider secrets are never returned to any role.

## Routing / permissions

| Surface | Route | Permission |
|---|---|---|
| System information | `/platform/system` | PLATFORM_SYSTEM_VIEW |
| Platform status | `/platform/status` | PLATFORM_STATUS_VIEW |
| Provider configuration | `/platform/providers` | PLATFORM_PROVIDER_VIEW |
| Org-scoped config | `/admin/organization-config` | ADMIN (role) |
| Settings / rules / lookups / audit | `/admin/...` | ORGANIZATION_SETTING_VIEW, BUSINESS_RULE_VIEW, LOOKUP_VIEW, AUDIT_LOG_VIEW |

## Open questions

- None.
