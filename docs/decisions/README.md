# Architecture Decision Records

These records capture *implementation* decisions that sit inside the frozen architecture.
They reference the frozen source decisions rather than duplicating them. A decision that would
change a frozen contract is out of scope here and must go through the architecture-change
process (`specs/09-P0-Foundation-V1.5.md §86`).

| ADR | Title | Status |
|---|---|---|
| [0001](ADR-0001-modular-monolith.md) | Modular monolith architecture | Accepted |
| [0002](ADR-0002-technology-stack.md) | Backend, frontend, and infrastructure technology stack | Accepted |
| [0003](ADR-0003-jwt-rbac-security-foundation.md) | JWT and RBAC security foundation | Accepted |
| [0004](ADR-0004-postgresql-source-of-truth.md) | PostgreSQL as the transactional source of truth | Accepted |
| [0005](ADR-0005-redis-cache-only.md) | Redis is cache only | Accepted |
| [0006](ADR-0006-pgbouncer-connection-management.md) | PgBouncer manages production connections | Accepted |
| [0007](ADR-0007-configuration-driven-ui-boundary.md) | Configuration-driven UI (CDUI) boundary | Accepted |
| [0008](ADR-0008-markdown-latex-content-boundary.md) | Markdown-LaTeX content boundary | Accepted |
| [0009](ADR-0009-integration-test-datastore.md) | Integration-test datastore strategy | Accepted |
| [0010](ADR-0010-p1-authentication-transport-and-token-policy.md) | P1 authentication transport, token storage, and JWT claim policy | Accepted |
| [0011](ADR-0011-p1-refresh-and-session-revocation-strategy.md) | P1 refresh-token and session revocation strategy | Accepted |
| [0012](ADR-0012-p1-password-hashing-and-reset-strategy.md) | P1 password hashing and password reset strategy | Accepted |
| [0013](ADR-0013-p1-authorization-resolution-caching-and-audit-boundary.md) | P1 authorization resolution, permission caching, and security audit boundary | Accepted |
| [0014](ADR-0014-p1-system-actor-and-privileged-role-change-policy.md) | P1 system actor identity and privileged role change policy | Accepted |
| [0015](ADR-0015-p2-academic-structure-decisions.md) | P2 Academic structure decisions | Accepted |
| [0016](ADR-0016-p3-content-rendering-boundary-and-decisions.md) | P3 content rendering boundary and decisions | Accepted |
| [0017](ADR-0017-p4-blueprint-and-assessment-decisions.md) | P4 blueprint and assessment decisions | Accepted |
| [0018](ADR-0018-p5-delivery-runtime-decisions.md) | P5 delivery runtime decisions | Accepted |
