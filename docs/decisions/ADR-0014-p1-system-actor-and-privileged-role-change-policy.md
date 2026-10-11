# ADR-0014: P1 system actor identity and privileged role change policy

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P1 — Identity and Security
- **Frozen source:** `specs/10-P1-Identity-V1.5.md §15, §63, §64, §65, §66`

## Context

Background and scheduled operations need an attributable actor, and privileged changes (especially
the ADMIN role) need guardrails so the platform cannot be locked out or escalated silently.

## Decision

**System actor.** A well-known user is seeded by Flyway with a fixed id and email
(`00000000-0000-0000-0000-000000000001` / `system@platform.internal`). Login is unconditionally
rejected for it — checked by id **and** email — with the same generic 401 as any other failure, so
its existence is not revealed. Audit attribution and background operations use this identity. The
seeded credential is a non-usable placeholder.

**Privileged role change policy** (`§63`):

| Rule | Enforcement |
|---|---|
| A user cannot assign or revoke their own roles | 403 `SELF_ROLE_MODIFICATION` |
| A user cannot modify the permissions of their own role | 403 `SELF_PERMISSION_MODIFICATION` |
| ADMIN assignment or removal requires `ADMIN_ROLE_ASSIGN` | backend permission check |
| Removing the last active ADMIN (including by archive) is blocked | 409 `LAST_ADMIN` |
| Role and permission changes are audited | structured `SECURITY` event (see ADR-0013) |
| Two-person approval | not required |

The last-admin count is `count(*) from user_role ur join role r on r.id = ur.role_id where
r.code = 'ADMIN' and ur.status = 'ACTIVE'`, evaluated inside the change transaction.

**Seeding** (`§64, §65, §66`): six roles (ADMIN, REVIEWER, STAFF, STUDENT, SUPPORT, FINANCE), the
twelve P1 identity permissions, and explicit ADMIN grants for the identity management permissions.
`ADMIN_ROLE_ASSIGN` is seeded but deliberately **not** granted to any role — it must be assigned
explicitly, which prevents an automatic self-escalation path.

## Consequences

- Login for the system actor is impossible; background attribution is deterministic.
- A deployment can never lose its last administrator through the API.
- Admin delegation requires an explicit grant of `ADMIN_ROLE_ASSIGN`, which is an intentional
  operational step.
