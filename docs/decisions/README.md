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
| [0019](ADR-0019-p6-evaluation-decisions.md) | P6 Evaluation: scoring, versioning, and publication decisions | Accepted |
| [0020](ADR-0020-p8-analytics-decisions.md) | P8 Analytics: derivation, bounds, and refresh decisions | Accepted |
| [0021](ADR-0021-p9-commerce-decisions.md) | P9 Commerce: pricing, payment, and entitlement decisions | Accepted |
| [0022](ADR-0022-p10-communication-decisions.md) | P10 Communication: event-driven notification and delivery decisions | Accepted |
| [0023](ADR-0023-p11-administration-decisions.md) | P11 Administration: control-plane decisions | Accepted |
| [0024](ADR-0024-p13-reporting-decisions.md) | P13 Reporting: read-only composition without new tables | Accepted |
| [0025](ADR-0025-answer-key-multiple-correct.md) | Answer key: multiple correct options for MULTIPLE_CHOICE | Accepted |
| [0026](ADR-0026-free-assessment-rule.md) | The free-assessment rule, and paid-content enforcement at Start Exam | Accepted |
| [0027](ADR-0027-assessment-exam-link.md) | Assessment exam link | Accepted |
| [0028](ADR-0028-worker-indexes.md) | Composite indexes for worker candidate selection and the notification list | Accepted |
| [0029](ADR-0029-commerce-report-catalog-amendment.md) | Commerce report catalog amendment: subscriptions, coupons, refunds | Accepted |
| [0030](ADR-0030-student-reporting-permission.md) | Student reporting is permission-based, not role-based | Accepted |
| [0031](ADR-0031-content-created-date-and-asset-preview.md) | Version created date and asset preview are by-design absent | Accepted |
