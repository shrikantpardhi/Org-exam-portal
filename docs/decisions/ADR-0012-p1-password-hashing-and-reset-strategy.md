# ADR-0012: P1 password hashing and password reset strategy

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P1 — Identity and Security
- **Frozen source:** `specs/10-P1-Identity-V1.5.md §21–§28, §58, §130, §167`

## Context

Passwords must be stored only as secure hashes, and P1 must provide a password reset flow. The
schema has no reset-token table, and `§24` forbids introducing one without formal review. Reset
tokens must still be confidential, expiring, single-use, and never logged.

## Decision

**Hashing.** Passwords use the Spring Security delegating password encoder
(`PasswordEncoderFactories.createDelegatingPasswordEncoder()`, BCrypt by default). The algorithm
prefix is stored in the hash so the algorithm can evolve without breaking credentials. Plaintext
is never persisted, compared directly, or logged.

**Password policy.** Minimum length 12, maximum length 128 (a storage-bound technical limit, not an
arbitrary strength rule), a small denylist of obvious passwords, and rejection of the account's own
email as the password. Long secure passwords are not rejected for arbitrary reasons.

**Onboarding.** The frozen `POST /api/v1/users` body has no password field, so a created account
starts with a random, non-usable credential and is activated through the reset flow. The API never
accepts or returns a password (`§56, §58`).

**Reset token — no new table.** A reset token is a short-lived (30 min) signed JWT whose `sub` is
the user and whose `pwd` claim is a SHA-256 fingerprint of the account's **current** password hash.
Single use is achieved by binding: once the password changes, the fingerprint no longer matches and
the token can never be replayed (`§27, §130`). Reset loads the user under a pessimistic write lock
so two concurrent resets cannot both succeed.

**Session invalidation.** A successful reset marks all of the user's `ACTIVE` login sessions
`EXPIRED` using the existing session model (`§28`).

**Delivery.** `PasswordResetDelivery` is a boundary with a logging default that never emits the
token; the email/notification platform is not built in P1 (`§184`). Tests obtain a token through
the token service directly, which validates the backend reset contract without a mail channel.

**Anti-enumeration.** `POST /api/v1/auth/forgot-password` always returns the same response. Login
failures for unknown accounts, wrong passwords, inactive users, inactive organizations, and the
system actor all return the same generic 401 (`§19, §175`).

## Consequences

- No reset-token table or column is added; the frozen schema is preserved.
- Reset tokens are self-expiring and single-use without server-side token storage.
- Token rotation/invalidations rely on the password hash changing; a reset that fails before
  committing leaves the account unchanged.
