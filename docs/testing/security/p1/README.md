# P1 Security Review and Regression Corpus

- **Phase:** P1 — Identity and Security
- **Date:** 2026-10-10
- **Frozen source:** `specs/10-P1-Identity-V1.5.md §168–§175, §186, §199`

This is the read-only second-pass security review (`P1-22`) plus the security regression corpus
(`§186`). The reviewer inspected the implementation; no architecture was changed during review.

## 1. Review scope and result

| Area inspected | Finding | Evidence |
|---|---|---|
| Authentication filter (`IdentityJwtAuthenticationFilter`) | Accepts only `Bearer`; requires an ACTIVE session when `sid` is present; invalid tokens leave the request anonymous | `IdentityAuthenticationIntegrationTest`, `IdentitySessionApiIntegrationTest` |
| JWT validation (`JwtTokenValidator`, `JwtTokenIssuer`) | Signature + issuer verified; HS256 secret ≥ 32 bytes; minimal claims; no permissions in the token | ADR-0010, `JwtTokenValidatorTest` |
| Authorization (`AuthorizationService`, `@PreAuthorize`) | Backend-authoritative; five-way ACTIVE conjunction; DB fallback on cache outage | `IdentityAuthorizationIntegrationTest` |
| Session state (`SessionService`) | Terminal transitions; logout idempotent; timeout = absolute lifetime; no schema additions | `IdentitySessionApiIntegrationTest`, ADR-0011 |
| Password reset (`PasswordResetService`, `PasswordResetTokenService`) | Single-use via password-hash binding; 30-minute expiry; pessimistic lock; no token logging | `IdentityPasswordResetIntegrationTest`, `IdentityConcurrencyIntegrationTest` |
| User archive | Sets ARCHIVED, expires sessions, enforces last-admin | `IdentityUserApiIntegrationTest`, `IdentityRoleApiIntegrationTest` |
| Role-permission replacement | Single transaction; no partial state; self-modification blocked | `IdentityRoleApiIntegrationTest` |
| Frontend token handling | One store (`sessionStorage`); 401 clears state; no blind retry; generic errors | `authenticated-guard.test.tsx`, `auth-flow.test.tsx`, `api-client.test.ts`, ADR-0010 |
| Enumeration resistance | forgot-password and login return uniform responses | `IdentityAuthenticationIntegrationTest`, `IdentityPasswordResetIntegrationTest` |

**Result:** no unresolved security defects. Two deferred items are recorded in §4.

## 2. Exit criteria proven

```text
Unauthenticated request -> 401                      (IdentityAuthorizationIntegrationTest)
Authenticated without permission -> 403             (IdentityAuthorizationIntegrationTest)
Valid user with permission -> success               (IdentityAuthorizationIntegrationTest)
Inactive user -> rejected                           (IdentityAuthenticationIntegrationTest)
Inactive role / relationship / permission -> denied (IdentityAuthorizationIntegrationTest)
Password reset -> secure, single-use                (IdentityPasswordResetIntegrationTest, Concurrency)
Logout -> terminal session state                    (IdentityAuthenticationIntegrationTest)
Authorization cache failure -> database fallback    (AuthorizationService; Redis unreachable in tests)
System actor login -> rejected                      (IdentityAuthenticationIntegrationTest)
Privileged role change -> enforced                  (IdentityRoleApiIntegrationTest)
```

## 3. Regression corpus (scenarios to re-run on every change)

| ID | Scenario | Test |
|---|---|---|
| SEC-A1 | Login with valid credentials | `IdentityAuthenticationIntegrationTest.loginSucceedsForAnActiveUserWithCorrectCredentials` |
| SEC-A2 | Login with wrong password / unknown account / system actor | `IdentityAuthenticationIntegrationTest` |
| SEC-A3 | Logout invalidates the token immediately | `IdentityAuthenticationIntegrationTest.logoutEndsTheSessionAndInvalidatesTheToken` |
| SEC-Z1 | 401 anonymous, 403 without permission, 200 with permission | `IdentityAuthorizationIntegrationTest` |
| SEC-Z2 | Inactive user/role/permission/relationship denies | `IdentityAuthorizationIntegrationTest.inactiveRelationshipsAndStatusesDoNotGrantAPermission` |
| SEC-S1 | Session scoping (own sessions only; SESSION_VIEW broadens) | `IdentitySessionApiIntegrationTest` |
| SEC-S2 | Expired session rejected | `IdentitySessionApiIntegrationTest.anExpiredSessionIsNoLongerAccepted` |
| SEC-P1 | Reset single-use under concurrency | `IdentityConcurrencyIntegrationTest` |
| SEC-P2 | Duplicate user prevented under concurrency | `IdentityConcurrencyIntegrationTest` |
| SEC-R1 | Reset invalidates existing sessions | `IdentityPasswordResetIntegrationTest.resetInvalidatesExistingSessions` |
| SEC-C1 | Self role / role-permission modification blocked | `IdentityUserApiIntegrationTest`, `IdentityRoleApiIntegrationTest` |
| SEC-C2 | Last active admin cannot be removed | `IdentityRoleApiIntegrationTest.removingTheLastActiveAdminIsBlocked` |

## 4. Deferred items (recorded, not silently accepted)

The consolidated, cross-phase register is [`docs/known-gaps.md`](../../known-gaps.md). The
security-relevant deferred items are:

1. **Persistent audit table** — `audit_log` is delivered by the Administration phase (frozen
   migration `V11`). P1 emits structured `SECURITY` events; it does not invent a second audit table
   (ADR-0013).
2. **Real reset delivery and httpOnly token transport** — email/notification delivery is not built
   in P1 (`§184`); token storage hardening to an httpOnly cookie requires an API transport change
   (ADR-0010).

## 5. Explicit non-noncompliance

No test was skipped, deleted, or weakened. No JWT validation is bypassed. No authorization decision
is made in the frontend. No reset token is logged. No plaintext password appears in fixtures or logs.
