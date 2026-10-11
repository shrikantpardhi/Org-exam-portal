# ADR-0004: PostgreSQL as the transactional source of truth

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/06-Architecture-Freeze-V1.5.md §14, §15`,
  `specs/04-Database-Schema-V1.5.md §2, §73`

## Decision

PostgreSQL is the authoritative transactional datastore for identity, academic masters,
content, assessments, sessions, attempts, responses, evaluations, results, commerce,
notifications, audit, and infrastructure job records. Schema change is owned exclusively by
Flyway; production schema is never modified manually.

## Conventions

- UUID primary keys; singular `snake_case` tables.
- Common audit fields (`id, status, created_by, created_on, updated_by, updated_on`) on major
  business tables.
- `TIMESTAMPTZ` for real instants; UTC timezone.
- `NUMERIC` (never floating point) for marks and money.
- JSONB only for genuinely configurable structures.
- Constraint naming: `pk_`, `fk_`, `uk_`, `idx_`.

## P0 scope

P0 proves the migration mechanism only. It creates a base-schema migration (extensions) and no
business tables. Business tables are added by their corresponding phases (P1 identity, P2
academic, P3 content, P4 assessment, P5 delivery, P6 evaluation, P8 analytics, P9 commerce,
P10 communication, P11 administration, P13 reporting-related).

## Consequences

- Analytics and cache never become authoritative.
- Migrations are reviewed with the migration checklist before merge.
