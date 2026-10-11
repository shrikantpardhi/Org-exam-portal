# ADR-0010: P1 authentication transport, token storage, and JWT claim policy

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P1 — Identity and Security
- **Frozen source:** `specs/10-P1-Identity-V1.5.md §37–§40, §88, §99, §157`,
  `specs/05-API-Contract-V1.5.md §5, §18`

## Context

P1 must issue and verify access credentials. The API contract fixes `Authorization: Bearer <JWT>`
as the only approved scheme and requires that credentials and JWTs never travel over unsecured
production HTTP. The source documents do not prescribe the browser storage mechanism or the exact
claim catalogue; both are explicit P1 decisions.

## Decision

**Transport.** Only `Authorization: Bearer <JWT>` is accepted. No second authentication scheme is
introduced.

**Signing and lifetime.** Access tokens are signed with HS256 using the environment-supplied
`app.security.jwt.secret` (minimum 32 bytes). The issuer is `app.security.jwt.issuer`. Lifetime is
`app.security.jwt.access-token-ttl` (default `PT1H`). Secrets come only from environment
configuration; none are committed.

**Claim catalogue (minimal).**

| Claim | Meaning |
|---|---|
| `sub` | User id (UUID) |
| `sid` | Login session id (UUID) |
| `iss` | Configured issuer |
| `iat` / `exp` | Issued-at and expiry (server clock, UTC) |

Permissions, roles, organization configuration, and business data are **never** placed in the
token. Permissions are mutable and are resolved per request, so authorization always reflects
current state (`§75, §76`).

**Browser storage.** The frontend keeps the access token in `sessionStorage`, in exactly one
module (`frontend/lib/auth/token-storage.ts`). This is one documented strategy — not a token
duplicated across localStorage, sessionStorage, and cookies. Tokens are never written to logs,
analytics, or error reports.

## Consequences

- A stolen token is usable only until expiry; session state is enforced on every request
  (see ADR-0011), so logout and password reset take effect immediately.
- `sessionStorage` is readable by scripts on the page, so XSS remains a threat. Moving to an
  httpOnly cookie is the documented hardening step; it requires an API transport change and is
  not part of P1.
- Because permissions are not in the token, permission changes need no token reissue
  (see ADR-0013).
