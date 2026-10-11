# ADR-0003: JWT and RBAC security foundation

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/03-Technical-Design-Document-V1.5.md §23–§31`,
  `specs/06-Architecture-Freeze-V1.5.md §83, §84`

## Context

The API is a stateless REST API. Authorization must be backend-authoritative; the frontend is
never a security boundary.

## Decision

P0 establishes the security framework but not the complete Identity domain:

- A `SecurityFilterChain` with public infrastructure endpoints and an authenticated API boundary.
- A JWT decoding/validation boundary (`JwtDecoder` + a JWT properties model). Token *issuance*
  and the login flow belong to P1 Identity.
- A secure adaptive password encoder (BCrypt) exposed as a bean; no password is ever logged or
  returned through a DTO.
- Explicit 401 (unauthenticated) and 403 (forbidden) JSON responses using the standard error
  envelope.
- CORS limited to configured trusted origins — no wildcard in production.
- Stateless sessions; CSRF disabled for the JWT-bearer API with the rationale documented
  (bearer tokens are not ambient browser credentials).

OAuth, SSO, and 2FA are intentionally excluded for this version.

## CSRF rationale

The API authenticates with a bearer token supplied in the `Authorization` header by the client,
not with ambient cookies. A browser does not attach that header automatically to cross-site
requests, so classic CSRF does not apply to the API surface. This is why CSRF protection is
disabled here, and the decision is recorded rather than left as an unexplained framework default.

## Consequences

- P1 Identity implements login, token issuance, sessions, RBAC catalogs, and the system actor,
  reusing this foundation without rebuilding it.
- Protected routes reject unauthenticated callers with 401 and unauthorized callers with 403.
