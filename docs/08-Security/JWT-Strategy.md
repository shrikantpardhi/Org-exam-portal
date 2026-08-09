# JWT Strategy

**Document Version:** 1.0  
**Status:** Approved  
**Parent Document:** Security Architecture

---

# 1. Purpose

This document defines the JSON Web Token (JWT) strategy for the Configurable Examination Platform.

It establishes how access tokens and refresh tokens are created, validated, transported, expired, rotated, revoked, and audited.

The objective is to provide secure, predictable, and maintainable token-based authentication for the platform's web and API clients.

---

# 2. Scope

This document covers:

- JWT architecture
- Access tokens
- Refresh tokens
- Token claims
- Signing
- Token validation
- Expiration
- Refresh lifecycle
- Rotation
- Revocation
- Logout
- Token storage
- Key management
- Security controls
- Failure handling
- Testing

This document does not define application-level permissions. Those are defined in `Authorization-RBAC.md`.

---

# 3. JWT Architecture

Version 1 uses a short-lived access token combined with a longer-lived refresh token.

```text
                    +----------------------+
                    | Authentication API   |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    | Authentication       |
                    | Service              |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    | Token Service        |
                    +----------+-----------+
                         /             \
                        /               \
                       v                 v
              Access Token       Refresh Token
                short-lived        long-lived
                       \               /
                        \             /
                         v           v
                            Client
```

The access token is used for normal API requests.

The refresh token is used only to obtain a new access token.

---

# 4. Access Token

The access token represents a short-lived authenticated session credential.

It is presented to protected APIs using:

```http
Authorization: Bearer <access-token>
```

The server validates the token before processing the protected request.

---

# 5. Access Token Characteristics

Access tokens should be:

- Short-lived
- Cryptographically signed
- Non-sensitive in content
- Scoped to the platform
- Validated on every protected request
- Rejected after expiration

The exact expiration period must be configurable through deployment configuration rather than hardcoded in application logic.

A typical initial configuration may use approximately:

```text
Access Token: 10–15 minutes
```

The final production value should be determined through security and operational testing.

---

# 6. Refresh Token

The refresh token allows the client to obtain a new access token without repeating the primary authentication process.

Refresh tokens should:

- Have a significantly longer lifetime.
- Be individually identifiable.
- Be revocable.
- Be protected from client-side exposure.
- Be rotated where practical.
- Never appear in application logs.

---

# 7. Refresh Token Storage

Refresh-token handling must minimize the impact of token theft.

For browser clients, the preferred strategy is to store refresh tokens using a mechanism resistant to JavaScript-based token theft, such as a secure, appropriately configured HTTP-only cookie, subject to the final frontend architecture.

Security attributes should include where applicable:

```text
HttpOnly
Secure
SameSite
```

The exact `SameSite` policy must account for the platform's deployment topology.

Access tokens should not be persisted in browser local storage by default.

---

# 8. JWT Claims

Access tokens should contain only information required by the application.

Recommended claims include:

| Claim | Purpose |
|---|---|
| `sub` | Authenticated user identifier |
| `jti` | Unique token identifier |
| `iss` | Token issuer |
| `aud` | Intended audience |
| `iat` | Issued-at timestamp |
| `exp` | Expiration timestamp |
| `nbf` | Earliest valid timestamp |
| Organization context | Where required |
| Authentication context | Where required |

Roles or permissions may be included where justified by the architecture, but the token must not become an unnecessarily large authorization database.

---

# 9. Sensitive Data Restrictions

JWT payloads must not contain:

- Passwords
- Password hashes
- Refresh tokens
- Secret keys
- Private keys
- Payment information
- Examination answers
- Candidate responses
- Sensitive personal information that is not required for authorization

JWTs are signed, not inherently encrypted.

Therefore, token payload contents should be considered readable by the token holder.

---

# 10. Token Signing

Tokens must be cryptographically signed.

The signing algorithm must be explicitly configured and must not be selected dynamically from untrusted token headers.

The platform must:

- Reject unsigned tokens.
- Reject unsupported algorithms.
- Validate the expected signing algorithm.
- Protect signing keys.
- Support controlled key rotation.

The production signing key must never be committed to source control.

---

# 11. Signing Key Management

Signing keys must be provided through secure deployment configuration or a dedicated secret-management mechanism.

Keys must not be:

- Hardcoded in source code.
- Stored in Git.
- Included in container images.
- Printed in logs.
- Returned through APIs.

Production keys must be separated from development/test keys.

---

# 12. Key Rotation

The platform should support controlled signing-key rotation.

A rotation strategy should allow:

```text
Old Key
   |
   | Existing tokens remain valid
   |
   v
New Key
   |
   | New tokens signed with new key
   |
   v
Old Key Retirement
```

During a controlled transition, the validator may need to trust both the current and previous signing key until tokens signed with the previous key expire.

Key rotation procedures must be documented operationally.

---

# 13. Token Validation

Every protected API request must validate the access token.

Validation includes:

1. Token presence
2. Token structure
3. Signature
4. Algorithm
5. Issuer
6. Audience where configured
7. Expiration
8. Not-before timestamp
9. Required claims
10. Token type/context where applicable

Only after successful validation should the request proceed to authorization.

---

# 14. Authentication vs Authorization

JWT validation establishes an authenticated security context.

It does not by itself establish that the user may perform the requested operation.

The request flow is:

```text
HTTP Request
     |
     v
Extract Token
     |
     v
Validate JWT
     |
     v
Authenticated User
     |
     v
Authorization
     |
     v
Organization Scope
     |
     v
Resource / Business Rules
     |
     v
Operation
```

---

# 15. Expiration

Access tokens must contain an expiration timestamp.

Expired tokens must be rejected.

Clients should not attempt to extend an expired access token locally.

Instead:

```text
Expired Access Token
        |
        v
Refresh Token
        |
        v
Refresh Validation
        |
        v
New Access Token
```

If refresh fails, the client must require the user to authenticate again.

---

# 16. Clock Skew

Distributed systems may have small differences between system clocks.

The JWT validator may allow a small, explicitly configured clock-skew tolerance where required.

The tolerance must be:

- Small
- Documented
- Consistent across environments

Large clock-skew windows must not be used as a substitute for correct infrastructure time synchronization.

---

# 17. Refresh Flow

The refresh flow is:

```text
Client
  |
  | Refresh Request
  v
Authentication API
  |
  v
Refresh Token Validation
  |
  +---- Invalid ---> 401
  |
  v
Refresh Token State
  |
  +---- Revoked ---> 401
  |
  v
User Account State
  |
  +---- Inactive ---> 401
  |
  v
Token Rotation
  |
  v
New Access Token
  |
  v
Client
```

---

# 18. Refresh Token Rotation

Where refresh-token persistence is implemented, each successful refresh should rotate the refresh token.

Example:

```text
Refresh Token A
       |
       v
Refresh Request
       |
       v
Token A invalidated
       |
       v
Refresh Token B issued
```

This reduces the usefulness of a stolen refresh token after legitimate rotation.

---

# 19. Refresh Token Replay Detection

The system should detect reuse of an already-rotated refresh token.

Example:

```text
Token A
  |
  +--> Legitimate refresh
  |
  +--> Token B issued
  |
  X Token A reused
```

If replay is detected, the platform should invalidate the associated refresh-token family/session according to the configured security policy.

Such events should be treated as security events.

---

# 20. Refresh Token Revocation

Refresh tokens must be revocable.

Revocation should occur when appropriate, including:

- Logout
- Password reset
- Account suspension
- Account lock
- Security incident
- Administrative session termination
- Detected refresh-token replay

---

# 21. Logout

Logout should invalidate the refresh-token/session state.

For browser clients, the authentication cookie should also be removed or expired.

A previously issued access token may remain valid until its expiration if the platform uses purely stateless access tokens.

Therefore, short access-token lifetime remains important.

---

# 22. Password Change

A successful password change should invalidate existing refresh-token sessions according to the security policy.

This prevents an attacker who has obtained a persistent session from retaining access after the legitimate user changes their password.

---

# 23. Account Suspension

When an account is suspended:

- New authentication must fail.
- Refresh operations must fail.
- Existing refresh sessions should be revoked.
- Access-token behavior must follow the configured token revocation strategy.

For highly sensitive administrative operations, additional server-side account-state checks may be required even when a JWT is otherwise valid.

---

# 24. Token Revocation Strategy

Version 1 should avoid introducing a global access-token blacklist unless there is a demonstrated requirement.

The preferred baseline is:

```text
Short-lived Access Token
+
Revocable Refresh Token
+
Account State Validation
+
Security Event Monitoring
```

A centralized access-token deny-list may be introduced later if operational or security requirements justify the additional complexity.

---

# 25. Organization Context

If organization information is included in a JWT, it must not be treated as unconditional authorization.

For example:

```json
{
  "sub": "user-id",
  "organizationId": "org-id"
}
```

The server must still validate that:

- The user belongs to the organization.
- The organization is active.
- The requested resource belongs to the organization.
- The user's role permits the requested operation.

JWT claims are security inputs, not a replacement for business authorization.

---

# 26. Concurrent Sessions

The platform should define how multiple sessions are handled.

Possible policies include:

- Unlimited sessions
- Maximum active sessions
- Per-device sessions
- Administrative session termination
- Logout from all devices

Version 1 should support the ability to invalidate refresh-token sessions even if unrestricted concurrent sessions are initially permitted.

---

# 27. Token Theft Response

If token compromise is suspected:

1. Revoke affected refresh session.
2. Invalidate the refresh-token family where appropriate.
3. Require re-authentication.
4. Record the security event.
5. Investigate related activity.
6. Rotate credentials or signing keys when the compromise scope requires it.

Signing-key rotation is a high-impact operation and should not be performed for every individual session compromise.

---

# 28. JWT Error Handling

The platform should distinguish internally between:

- Missing token
- Malformed token
- Invalid signature
- Unsupported algorithm
- Expired token
- Invalid issuer
- Invalid audience
- Invalid claims
- Revoked session

External API responses should avoid exposing cryptographic or internal validation details.

---

# 29. HTTP Security Behavior

Typical behavior:

### Missing authentication

```http
401 Unauthorized
```

### Invalid or expired token

```http
401 Unauthorized
```

### Valid token but insufficient permission

```http
403 Forbidden
```

The API must not return `403` simply because a token is malformed or missing.

---

# 30. Caching Restrictions

Authenticated responses must be designed carefully to prevent sensitive data from being cached or shared incorrectly.

Particular care is required for:

- Candidate responses
- Results
- Evaluation data
- Examination content
- Administrative information

Browser and intermediary caching policies must be appropriate to the sensitivity of the resource.

---

# 31. Logging Restrictions

Never log:

- Full access tokens
- Full refresh tokens
- Authorization headers
- Signing keys
- Token secrets

If token identification is required for troubleshooting, log only a safe identifier such as a token `jti` or a securely derived fingerprint where appropriate.

---

# 32. API Gateway / Reverse Proxy Considerations

If a reverse proxy or API gateway is introduced, it must not weaken token security.

The platform must ensure:

- Authorization headers are transmitted securely.
- Proxy configuration does not expose tokens.
- TLS termination is controlled.
- Trusted proxy headers are validated.
- Internal services do not blindly trust externally supplied identity headers.

---

# 33. Frontend Responsibilities

The frontend must:

- Protect authentication state.
- Avoid exposing refresh tokens to JavaScript where possible.
- Attach access tokens to API requests according to the approved strategy.
- Handle access-token expiration.
- Attempt refresh when appropriate.
- Clear authentication state after refresh failure.
- Redirect users to login when authentication is no longer valid.

The frontend must never determine authorization solely from decoded JWT claims.

---

# 34. Backend Responsibilities

The backend must:

- Validate every access token.
- Establish the authenticated security context.
- Enforce authorization.
- Validate organization scope.
- Validate resource access.
- Enforce business rules.
- Handle token failures consistently.
- Protect signing keys.

---

# 35. Testing Requirements

## Access Token

Test:

- Valid token
- Expired token
- Malformed token
- Invalid signature
- Wrong issuer
- Wrong audience
- Unsupported algorithm
- Missing required claims

## Refresh Token

Test:

- Valid refresh
- Expired refresh
- Revoked refresh
- Rotated refresh
- Replay of old refresh token
- Account suspension
- Password change
- Logout

## Security

Test:

- Token theft response
- Token leakage prevention
- Algorithm confusion attempts
- Token substitution
- Cross-organization access
- Privilege escalation

---

# 36. Acceptance Criteria

The JWT implementation is considered complete when:

- Access tokens are cryptographically signed.
- Signing algorithms are explicitly controlled.
- Access tokens expire.
- Refresh tokens have independent lifecycle management.
- Refresh tokens can be revoked.
- Refresh rotation is implemented where configured.
- Refresh-token replay can be detected.
- Logout invalidates the appropriate session.
- Password changes invalidate persistent sessions.
- Suspended users cannot refresh sessions.
- Tokens do not contain unnecessary sensitive information.
- Secrets and signing keys are never committed or logged.
- Protected APIs reject invalid tokens.
- Authorization occurs after successful authentication.
- Automated JWT security tests pass.

---

# 37. Related Documents

- `Security Architecture`
- `Authentication.md`
- `Authorization-RBAC.md`
- `Password-Policy.md`
- `Audit-Logging.md`
- `Data-Protection.md`
- `OWASP-Checklist.md`
- `05-API`
- `06-Implementation`
- `07-Deployment`