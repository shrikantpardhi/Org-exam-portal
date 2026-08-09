# Password Policy

**Document Version:** 1.0  
**Status:** Approved  
**Parent Document:** Security Architecture

---

# 1. Purpose

This document defines password security requirements for the Configurable Examination Platform.

The objective is to protect user accounts from:

- Credential guessing
- Brute-force attacks
- Credential stuffing
- Password database compromise
- Password reuse attacks
- Weak password selection
- Password-reset abuse
- Administrative credential misuse

This policy applies to every account authenticated using a platform-managed password.

---

# 2. Scope

This policy covers:

- Password creation
- Password validation
- Password hashing
- Password storage
- Password changes
- Password reset
- Temporary passwords
- Compromised passwords
- Administrative password operations
- Authentication throttling
- Logging
- Auditing
- Testing

External identity providers and future SSO integrations may enforce their own authentication policies.

---

# 3. Core Principles

Password security follows these principles:

1. Passwords are secrets.
2. Plain-text passwords must never be stored.
3. Passwords must never be recoverable from the database.
4. Password complexity should prioritize length and resistance to guessing.
5. Password-reset operations are security-sensitive.
6. Passwords must never appear in logs.
7. Password validation must occur on the server.
8. Authentication systems must protect against automated guessing.

---

# 4. Password Creation

Users must create passwords that satisfy the configured password policy.

The baseline Version 1 policy should require:

```text
Minimum length: 12 characters
Maximum supported length: at least 64 characters
```

The application should support long passphrases.

Examples of acceptable password styles include:

```text
correct-horse-river-coffee-27
```

rather than forcing users to create short, difficult-to-remember strings.

---

# 5. Password Complexity

The platform should not rely exclusively on rules such as:

```text
Must contain:
- uppercase
- lowercase
- number
- symbol
```

Such requirements can produce predictable passwords.

The preferred strategy combines:

```text
Sufficient Length
+
Compromised Password Detection
+
Rate Limiting
+
Secure Password Hashing
+
Multi-Factor Authentication in future
```

If organization-specific complexity requirements are required, they should be configurable.

---

# 6. Maximum Password Length

The application must support sufficiently long passwords.

The system must not silently truncate passwords.

For example:

```text
User submits:
MyVeryLongPasswordExample123456789

Stored comparison:
MyVeryLongPasswordExample
```

is prohibited.

The complete accepted password must participate in password hashing.

---

# 7. Unicode and Whitespace

Password handling must be consistent across:

- Frontend
- Backend
- Authentication service
- Password hashing

The application must not unexpectedly alter user passwords through:

- Automatic trimming
- Case conversion
- Character removal
- Unintended normalization

If normalization is performed, it must be explicitly defined and consistently implemented.

---

# 8. Password Hashing

Passwords must be stored using a password hashing algorithm designed specifically for password storage.

Approved approaches should use an adaptive password hashing function supported by the application's security framework.

Examples include:

- Argon2id
- bcrypt
- PBKDF2 where appropriately configured

The final algorithm and parameters must be centrally configured.

---

# 9. Recommended Hashing Strategy

For a new platform deployment, **Argon2id** should be considered the preferred option where operationally supported.

The selected parameters must balance:

- Security
- Authentication latency
- Server capacity
- Memory consumption

Parameters must be benchmarked on production-equivalent infrastructure.

The hashing strategy must be upgradeable without requiring users to immediately reset their passwords.

---

# 10. Password Storage

The database stores:

```text
password_hash
```

It must never store:

```text
password
plaintext_password
encrypted_password
recoverable_password
```

Password encryption is not an acceptable replacement for password hashing.

---

# 11. Salt

The selected password hashing implementation must use an appropriate unique salt.

Developers must not implement custom salt generation or password cryptography unless explicitly required by the approved security architecture.

Use established security libraries.

---

# 12. Pepper

A server-side pepper may be considered as an additional defense.

If used:

- It must not be stored in the user database.
- It must be stored in secure secret management.
- Rotation must have a documented strategy.
- Failure or loss must have an operational recovery plan.

Pepper implementation is optional and should not introduce operational risk without a clear security requirement.

---

# 13. Password Hash Upgrades

Password hashing parameters may evolve.

Example:

```text
User has old password hash
        |
        v
Successful Login
        |
        v
System detects old parameters
        |
        v
Password rehashed using current parameters
        |
        v
New hash stored
```

This allows gradual security upgrades without forcing all users to reset their passwords simultaneously.

---

# 14. Prohibited Password Storage

Passwords must never be stored in:

- Logs
- Audit logs
- Analytics
- Cache
- Browser local storage
- Error messages
- Exception traces
- Monitoring systems
- Message queues
- Support tickets
- Email
- API response payloads

---

# 15. Password Transmission

Passwords must only be transmitted over encrypted connections.

Production authentication endpoints must use HTTPS.

Plain HTTP authentication is prohibited.

---

# 16. Password Validation

Password policy must be validated on the backend.

Frontend validation may improve user experience but cannot be the security enforcement layer.

Correct flow:

```text
Frontend Validation
        |
        v
Backend Validation
        |
        v
Password Hashing
        |
        v
Persistence
```

---

# 17. Compromised Password Detection

The platform should prevent use of passwords known to be widely compromised where technically and operationally practical.

Examples include passwords such as:

```text
password
password123
123456789
qwerty123
```

The implementation should use an approved compromised-password strategy rather than maintaining a small handcrafted list.

---

# 18. Username Similarity

The platform may reject passwords that are substantially identical to easily discoverable account identifiers.

Examples:

```text
username: shrikant
password: shrikant
```

or trivial variations where policy requires stronger protection.

This should not replace proper password-strength controls.

---

# 19. Password Change

Authenticated users may change their passwords.

The flow should be:

```text
Authenticated User
        |
        v
Current Password Verification
        |
        v
New Password Validation
        |
        v
Password Hash
        |
        v
Database Update
        |
        v
Session Revocation
        |
        v
Audit Event
```

Sensitive account-management flows may require recent authentication.

---

# 20. Current Password Verification

Normal password changes should require verification of the current password.

Administrative reset flows are separate and must not pretend to be normal user password changes.

---

# 21. Session Handling After Password Change

After a password change, existing persistent authentication sessions should be invalidated according to the security policy.

Recommended behavior:

```text
Password Changed
      |
      +--> Revoke Refresh Tokens
      |
      +--> Invalidate Other Sessions
      |
      +--> Require Reauthentication
```

Whether the current session remains active should be explicitly defined.

A security-focused default is to require fresh authentication.

---

# 22. Password Reset

Users who cannot authenticate require a secure password-reset process.

The basic flow is:

```text
Forgot Password
      |
      v
Submit Account Identifier
      |
      v
Generic Response
      |
      v
Generate Reset Token
      |
      v
Deliver Reset Link
      |
      v
Verify Token
      |
      v
Set New Password
      |
      v
Invalidate Token
      |
      v
Revoke Existing Sessions
```

---

# 23. Account Enumeration Prevention

Password-reset endpoints must not reveal whether an account exists.

Incorrect:

```text
No account exists with this email.
```

Preferred:

```text
If an eligible account exists, password reset instructions will be sent.
```

The same principle applies to login and account recovery APIs where appropriate.

---

# 24. Password Reset Token

Reset tokens must be:

- Cryptographically strong
- Random
- Single-use
- Time-limited
- Associated with the intended account
- Invalidated after successful use

Reset tokens must not be predictable.

---

# 25. Reset Token Storage

Reset tokens should not be stored in recoverable plain-text form when avoidable.

A secure pattern is:

```text
Generated Reset Token
        |
        +--> Raw token sent to user
        |
        +--> Secure hash stored server-side
```

When the user submits the token, the server validates it against the stored representation.

---

# 26. Reset Token Expiration

Password-reset tokens must expire after a short period.

The exact duration should be configurable.

An initial policy may use a validity period such as:

```text
15–30 minutes
```

The final production value must be approved through security configuration.

---

# 27. Reset Token Reuse

After successful password reset:

```text
Reset Token
    |
    v
USED / INVALIDATED
```

The same token must never reset the password twice.

---

# 28. Password Reset and Sessions

A successful password reset should revoke existing refresh-token sessions.

This prevents an attacker with an existing persistent session from remaining authenticated after account recovery.

---

# 29. Administrative Password Reset

Administrators may require the ability to initiate account recovery.

Administrators must not:

- View existing passwords.
- Retrieve existing passwords.
- Set predictable permanent passwords.
- Receive users' passwords through APIs.

Preferred administrative actions include:

```text
Initiate Password Reset
```

or:

```text
Force Password Change
```

Administrative password actions must be audited.

---

# 30. Temporary Passwords

If temporary passwords are supported, they must:

- Be randomly generated.
- Have limited validity.
- Require change at first successful login.
- Never become permanent automatically.
- Be delivered through an approved secure mechanism.

Password-reset links are generally preferred over temporary passwords.

---

# 31. Forced Password Change

The user account may contain a state such as:

```text
passwordChangeRequired = true
```

When active:

```text
Login
  |
  v
Limited Authentication State
  |
  v
Password Change
  |
  v
Normal Application Access
```

The user must not receive unrestricted application access before completing the required password change.

---

# 32. Password Expiration

Version 1 should **not automatically force periodic password changes solely because a fixed number of days has elapsed**, unless required by a specific organizational or regulatory policy.

Forced periodic changes often lead users toward predictable password patterns.

Passwords should instead be changed when:

- Compromise is suspected.
- The user requests a change.
- Account recovery occurs.
- An administrator requires reset for a valid security reason.
- Applicable policy explicitly requires expiration.

---

# 33. Password History

If an organization requires password history, the system may prevent immediate reuse of recent passwords.

If implemented, historical passwords must be stored only as secure hashes.

Plain-text password history is prohibited.

---

# 34. Authentication Failure Protection

Password authentication must include controls against automated guessing.

Controls should include:

- Rate limiting
- Progressive throttling
- Temporary lockouts where appropriate
- Monitoring
- Security event logging

Controls may operate using combinations of:

- Account
- IP address
- Session
- Device
- Request patterns

---

# 35. Account Lockout

Repeated failed authentication attempts may trigger temporary protection.

Example:

```text
Failed Attempts
      |
      v
Threshold Reached
      |
      v
Temporary Protection
      |
      v
Cooldown / Recovery
```

Exact thresholds must be configurable and should not be hardcoded.

Care must be taken to prevent attackers from intentionally locking large numbers of legitimate accounts.

---

# 36. Credential Stuffing

The platform must account for credential-stuffing attacks where attackers use passwords compromised on unrelated services.

Defenses include:

- Rate limiting
- Compromised-password detection
- Authentication monitoring
- Suspicious login detection
- MFA in future releases

---

# 37. Multi-Factor Authentication

MFA is not mandatory for the initial baseline unless required by the PRD.

However, the authentication architecture should allow future support for:

- TOTP authenticator applications
- WebAuthn/passkeys
- Enterprise identity providers
- Recovery codes

Administrative accounts should be prioritized if MFA is introduced.

---

# 38. Examination-Specific Considerations

Candidate passwords must not be treated as the sole security mechanism protecting examination integrity.

Assessment access must additionally validate:

- Candidate identity
- Assessment assignment
- Examination window
- Attempt eligibility
- Attempt status

Authentication and assessment authorization are separate controls.

---

# 39. API Requirements

Password-related endpoints must:

- Use HTTPS.
- Apply rate limiting.
- Validate payload size.
- Validate password policy.
- Prevent account enumeration.
- Return standardized errors.
- Generate appropriate security events.

Examples include:

```text
POST /auth/login
POST /auth/forgot-password
POST /auth/reset-password
POST /auth/change-password
```

Exact routes are governed by the API specification.

---

# 40. Error Handling

Password errors should provide useful information without exposing sensitive details.

During password creation, policy violations may be communicated clearly.

Example:

```json
{
  "code": "PASSWORD_POLICY_VIOLATION",
  "message": "Password does not meet the required security policy."
}
```

Login failures should remain generic.

Example:

```json
{
  "code": "AUTHENTICATION_FAILED",
  "message": "Invalid credentials."
}
```

---

# 41. Logging

Password-related logs may record:

- Password reset requested
- Password reset completed
- Password changed
- Authentication failure
- Account protection triggered

Logs must never contain:

```text
password
oldPassword
newPassword
temporaryPassword
resetToken
```

---

# 42. Audit Events

The following events should be auditable:

```text
PASSWORD_CHANGED
PASSWORD_RESET_REQUESTED
PASSWORD_RESET_COMPLETED
PASSWORD_RESET_FAILED
PASSWORD_CHANGE_FORCED
ACCOUNT_LOCKED
ACCOUNT_UNLOCKED
```

Audit events must contain contextual information without credential material.

---

# 43. Database Requirements

User credential storage should maintain only the information required for authentication.

Example conceptual structure:

```text
UserCredential

user_id
password_hash
password_changed_at
password_change_required
failed_login_count
locked_until
created_at
updated_at
```

Actual schema definitions belong in `04-Database`.

---

# 44. Secrets and Configuration

Password policy configuration should be externalized where appropriate.

Examples:

```text
minimum length
reset token lifetime
authentication throttle
lockout threshold
lockout duration
```

Security-sensitive defaults must be provided.

Organizations must not be allowed to configure values below platform-defined minimum security boundaries.

---

# 45. Frontend Requirements

The frontend should:

- Provide password visibility toggle where appropriate.
- Display password requirements before submission.
- Avoid unnecessary password retention in component state.
- Clear password fields after failure where appropriate.
- Never persist passwords.
- Never send passwords to analytics.
- Never expose passwords through URLs.

Password reset tokens must not be unnecessarily retained after completion.

---

# 46. Backend Requirements

The backend must:

- Enforce password policy.
- Hash passwords securely.
- Never persist plain-text passwords.
- Rate-limit sensitive endpoints.
- Prevent enumeration.
- Validate reset tokens securely.
- Revoke sessions after recovery.
- Audit security-sensitive operations.

---

# 47. Testing Requirements

## Password Creation

Test:

- Minimum accepted length
- Below-minimum rejection
- Long passwords
- Unicode passwords
- Whitespace behavior
- Compromised-password rejection
- No silent truncation

## Password Hashing

Test:

- Password stored as hash
- Different salts produce different hashes
- Correct password verifies
- Incorrect password fails
- Hash upgrade works

## Password Change

Test:

- Correct current password
- Incorrect current password
- New policy validation
- Session invalidation
- Audit event generation

## Password Reset

Test:

- Valid token
- Invalid token
- Expired token
- Reused token
- Account enumeration resistance
- Session revocation
- Concurrent reset requests

## Attack Scenarios

Test:

- Brute force
- Credential stuffing
- Excessive reset requests
- Reset-token replay
- Reset-token guessing
- Log leakage
- Oversized password payloads

---

# 48. Acceptance Criteria

Password security is considered complete when:

- Plain-text passwords are never persisted.
- Approved adaptive password hashing is implemented.
- Password policy is enforced server-side.
- Long passwords are supported without silent truncation.
- Authentication endpoints are rate-limited.
- Password reset does not expose account existence.
- Reset tokens are strong, expiring, and single-use.
- Password changes and resets revoke appropriate sessions.
- Administrators cannot retrieve passwords.
- Passwords and reset tokens never appear in logs.
- Security-sensitive password events are audited.
- Automated security tests pass.

---

# 49. Related Documents

- `Security Architecture`
- `Authentication.md`
- `Authorization-RBAC.md`
- `JWT-Strategy.md`
- `Audit-Logging.md`
- `Data-Protection.md`
- `OWASP-Checklist.md`
- `04-Database`
- `05-API`
- `06-Implementation`