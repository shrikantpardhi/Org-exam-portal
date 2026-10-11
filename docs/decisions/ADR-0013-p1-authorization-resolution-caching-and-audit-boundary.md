# ADR-0013: P1 authorization resolution, permission caching, and security audit boundary

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P1 — Identity and Security
- **Frozen source:** `specs/10-P1-Identity-V1.5.md §11, §34–§36, §68–§76, §78, §93, §94, §149, §196`

## Context

Authorization must be backend-authoritative and reflect current permissions even after a token is
issued. Effective permission resolution is a high-frequency security path. Redis is cache-only and
may be unavailable. Audit tables (`audit_log`) belong to the Administration phase and do not exist
yet.

## Decision

**Resolution.** `AuthorizationService` answers permission questions only. Effective permissions are
resolved by a single join query over `permission -> role_permission -> role -> user_role -> user`
with a five-way `status = ACTIVE` filter, returning distinct codes. No lazy object graphs, no N+1.

**Caching.** Results are cached in Redis under `authz:perms:{userId}` for a short TTL (5 minutes).
The cache is invalidated after a permission-affecting transaction **commits**, via
`TransactionSynchronization.afterCommit`, for every affected user (role-permission change, role
status change, user role change, archive). A Redis error degrades to a cache miss and the query
runs against PostgreSQL — a cache outage never grants access (`§73, §149`).

**Enforcement.** Endpoints are protected with method security:
`@PreAuthorize("@authorizationService.hasPermission(authentication, 'CODE')")`, where the matrix is
`§68`. The controller stays thin; business authorization stays in services.

**Principal.** The security context carries only `(userId, sessionId)`. Authorities are not baked
into the principal or the token; they are resolved per request.

**Audit boundary.** P1 emits sanitized structured security events (login success/failure, logout,
reset request/success, role change) through a dedicated `SECURITY` logger with masked identifiers —
never passwords, tokens, or full request bodies. The immutable `audit_log` table is delivered by the
Administration phase (frozen migration `V11`); **P1 does not invent a second audit table**
(`§48, §196`). This is a documented cross-phase boundary, not a silent omission: the security-event
contract exists now and is wired to persistence when Administration lands.

## Consequences

- Permission changes take effect within seconds (invalidation on commit) and immediately when Redis
  is unavailable.
- The authorization path is measured and index-backed
  (`idx_user_role_user_id`, `idx_role_permission_role_id`, `uk_*_code`).
- Persistent, immutable audit evidence is deferred to P11 while the P1 security trail is emitted as
  structured logs. This is recorded here so it is not mistaken for a gap.
