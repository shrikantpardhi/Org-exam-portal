# Security Policy

## Scope

This policy covers the Smart Competitive Examination Platform repository and its deployed
environment. The platform handles examination content, student responses, official results,
and payment-adjacent access authorization, so security and data integrity are primary
architectural concerns.

## Reporting a vulnerability

Do **not** open a public issue for a security vulnerability.

Report privately to the security contact configured for this repository (repository Security
advisories, or the address recorded in the organization's internal security runbook). Include:

- a description of the issue and its impact,
- reproduction steps or a proof of concept,
- affected component and version/commit,
- any suggested remediation.

You will receive an acknowledgement, and the report will be triaged against the severity
model below.

## Severity model

| Severity | Examples |
|---|---|
| Critical | Exposure of correct answers/answer keys to students, authentication bypass, scoring tampering, payment callback forgery, unauthorized assessment access |
| High | Privilege escalation, IDOR on student data, secret leakage, SQL injection |
| Medium | Sensitive-data exposure in logs, missing rate limiting on sensitive endpoints, CSRF on credentialed flows |
| Low | Hardening gaps, informational leakage |

## Security baseline

The frozen architecture requires (see `specs/06-Architecture-Freeze-V1.5.md §116`,
`specs/03-Technical-Design-Document-V1.5.md §28`):

- HTTPS in production.
- JWT authentication and backend-authoritative RBAC.
- Secure adaptive password hashing; passwords are never stored in plaintext or logged.
- Input validation at API, business, and database boundaries.
- Backend authorization is authoritative; the frontend is never a security boundary.
- Audit insertion is transactional with the business mutation.
- AccessGrant is the authorization source of truth; payment success alone never grants access.
- Published examination artifacts are immutable.
- Correct answers and answer keys are never exposed to students during active delivery.

## Excluded security features

OAuth, SSO, 2FA, biometric verification, and advanced/AI proctoring are intentionally out of
scope for this version (`specs/06-Architecture-Freeze-V1.5.md §84, §113`).

## Secrets

- Secrets are never committed to Git.
- `.env` files are ignored; only `.env.example` with placeholders is committed.
- Production secrets come from an environment-specific secret manager.
- CI runs a secret scan; a committed secret is an immediate failure.

## Logging

Logs are structured and never contain passwords, JWTs, payment secrets, raw authorization
headers, answer keys, or unnecessary student answers. A correlation ID (`X-Request-Id`) is
propagated for traceability.

## Supported versions

Only the current implementation line on `main` receives fixes. Release versioning follows the
frozen roadmap (`v1.5.x`).
