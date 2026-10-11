# ADR-0006: PgBouncer manages production connections

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/06-Architecture-Freeze-V1.5.md §18`,
  `specs/09-P0-Foundation-V1.5.md §26–§28, §141`

## Decision

In production and staged environments the request path is:

```text
Spring Boot -> PgBouncer -> PostgreSQL
```

PgBouncer is a connection-management component. It is **not** a substitute for application-side
connection pool configuration, and it does not hold business state.

Two connection pools exist: the application pool and the PgBouncer pool. Both are configured
and both matter to capacity.

## Consequences

- Local development supports both a direct profile (`local`) and a pooled profile
  (`staging`/`production`).
- **Flyway migrations connect directly to PostgreSQL**, not through PgBouncer, so migration
  tooling does not depend on the traffic pooler.
- Pool sizes are engineering tuning parameters supported by load testing; they are not
  hardcoded as though the concurrent-user target had already been validated.
- Transaction pooling mode is used; the application does not rely on session-scoped PostgreSQL
  state.
