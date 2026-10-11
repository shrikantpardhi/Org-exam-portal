# ADR-0011: P1 refresh-token and session revocation strategy

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P1 — Identity and Security
- **Frozen source:** `specs/10-P1-Identity-V1.5.md §11–§14, §28, §29, §31, §41, §42, §43, §76`

## Context

The API contract exposes `POST /api/v1/auth/refresh-token`, but leaves the refresh credential
open. The frozen `login_session` table has **no** `refresh_token`, `token_hash`, `expires_on`, or
last-activity column, and `§42` explicitly forbids adding those columns without a formal schema
change. P1 must therefore implement refresh and revocation using the tables that exist.

## Decision

**Refresh.** Refresh is bound to the current active login session, not to a separate refresh
credential. `POST /api/v1/auth/refresh-token` is authenticated with the access token: it validates
that the referenced session is still `ACTIVE` and the user is still `ACTIVE`, then issues a new
access token for the **same** session. Limitation: because no long-lived credential is stored, a
refresh cannot revive an already-expired access token — the session lifetime is the real gate.
This is an accepted consequence of not adding schema, not an oversight.

**Session enforcement.** Every authenticated request resolves the `sid` claim to its
`login_session` row and requires `session_status = ACTIVE`. A token whose session is
`LOGGED_OUT` or `EXPIRED` is treated as anonymous, so logout, password reset, archive, and timeout
take effect immediately rather than at token expiry.

**Timeout.** The session has an absolute lifetime (`app.security.session.absolute-lifetime`,
default `PT12H`) measured from `login_time`, swept by a scheduled job that marks overdue `ACTIVE`
sessions `EXPIRED`. There is **no idle timeout**, because the frozen schema has nowhere to store
last activity.

**Logout.** `POST /api/v1/auth/logout` moves the session `ACTIVE -> LOGGED_OUT` and records
`logout_time`. Transitions are terminal and idempotent; a session is never resurrected.

**Session-less tokens.** A token without a `sid` claim (only producible by a holder of the signing
secret; the P0 boundary smoke tokens are the example) authenticates identity but carries no
session. It reaches no permission-protected endpoint because authorities are resolved per request
from the database. This preserves the P0 security tests without creating a production bypass.

## Consequences

- No new table or column is introduced; the frozen schema is preserved.
- Revocation is immediate and database-authoritative.
- Redis is not used as a session source of truth, consistent with ADR-0005 and `§148`.
- A future true refresh-token design would require a schema change and a new ADR.
