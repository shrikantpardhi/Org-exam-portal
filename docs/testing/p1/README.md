# P1 Acceptance Evidence

- **Phase:** P1 — Identity and Security
- **Date:** 2026-10-10
- **Frozen source:** `specs/10-P1-Identity-V1.5.md §224, §225, §226, §229`

Commands were run and their results recorded, not claimed (`§224`).

## 1. Verification command set (§224)

| Command | Result |
|---|---|
| `cd backend && ./mvnw clean verify` | PASS — 103 tests, 0 failures, 0 errors, BUILD SUCCESS |
| `cd frontend && npm ci` | PASS (requires `NODE_ENV` not set to `production`) |
| `npm run lint` | PASS — 0 errors, 0 warnings |
| `npx tsc --noEmit` | PASS |
| `npm test` | PASS — 35 tests, 11 files |
| `npm run build` | PASS — `/login`, `/forgot-password`, `/reset-password`, `/account` routes built |
| Migration validation | PASS — `FlywayMigrationIntegrationTest`, `IdentityMigrationIntegrationTest` |
| Security tests | PASS — see `docs/testing/security/p1/README.md` |
| End-to-end authentication | PASS — `IdentityAuthenticationIntegrationTest` (backend) + `auth-flow.test.tsx` (frontend) |

Backend test breakdown: 46 P0 tests retained + 57 P1 tests (migration 7, authentication 12,
authorization 5, users 11, roles 8, sessions 5, password reset 8, concurrency 2 — some classes
overlap categories).

## 2. Acceptance criteria (§225)

```text
[x] Identity migrations applied
[x] System actor seeded and login blocked
[x] Organization works
[x] User works
[x] Role works
[x] Permission works
[x] UserRole works
[x] RolePermission works
[x] LoginSession works
[x] Login works
[x] Logout works
[x] Refresh strategy implemented and documented (ADR-0011)
[x] Forgot-password works
[x] Reset-password works
[x] /me works
[x] /me/permissions works
[x] Users APIs work
[x] Roles APIs work
[x] Sessions APIs work
[x] Backend authorization works
[x] Privileged role change policy enforced
[x] Frontend authentication works
[x] End-to-end works
[x] No password leakage
[x] No token leakage
[x] No secret leakage
[~] CI passes — no CI workflow exists yet (P0-12/P1 CI is delivered by the CI/CD task); tests pass locally
[x] Security review passes
```

## 3. Security exit criteria (§226)

All ten exit conditions are proven by the tests in
`docs/testing/security/p1/README.md §2`.

## 4. Known limitations (recorded, §229)

The consolidated register is [`docs/known-gaps.md`](../../known-gaps.md). The P1-relevant items:

- **Persistent audit** — `audit_log` lands with Administration (frozen migration `V11`); P1 emits
  structured security events (ADR-0013).
- **Reset delivery** — no email channel in P1; a logging delivery boundary is used (ADR-0012).
- **Refresh** — session-bound; cannot revive an expired token because no long-lived credential
  column exists (ADR-0011).
- **Idle timeout** — not implemented; the frozen schema has no last-activity column (ADR-0011).
- **Token storage** — `sessionStorage`; httpOnly cookie is the documented hardening step (ADR-0010).
- **Docker/edge** — P0 container and edge verification is still outstanding (no Docker on the
  development host); unaffected by P1 code.
