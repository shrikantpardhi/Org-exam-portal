# Schema Documentation

The authoritative schema is the frozen
[`specs/04-Database-Schema-V1.5.md`](../../specs/04-Database-Schema-V1.5.md). This directory
documents conventions and operational notes only; the executable schema lives in
`backend/src/main/resources/db/migration/`.

## Naming conventions

| Object | Convention | Example |
|---|---|---|
| Table | singular `snake_case` | `student_attempt` |
| Column | `snake_case` | `updated_on` |
| Primary key | `pk_<table>` on `id UUID` | `pk_student_attempt` |
| Foreign key | `fk_<table>_<reference>` | `fk_user_role_user_id` |
| Unique | `uk_<table>_<column...>` | `uk_user_email` |
| Index | `idx_<table>_<column...>` | `idx_student_attempt_user_id` |

## Common conventions

- UUID primary keys.
- Audit fields on major business tables: `id, status, created_by, created_on, updated_by, updated_on`.
- Standard lifecycle status: `ACTIVE, INACTIVE, ARCHIVED`.
- Domain status enums per `specs/04-Database-Schema-V1.5.md §9`.
- `TIMESTAMPTZ` for instants; UTC.
- `NUMERIC` for marks and money; never floating point.
- JSONB only for genuinely configurable structures.
- Soft delete via `status`; hard delete only where explicitly safe.

## Migration layout

```text
backend/src/main/resources/db/migration/
  V1__create_base_schema.sql     # P0: base schema/extensions only
  V2__...                        # P1 identity
  V3__...                        # P2 academic
  ...
```

Business tables are added by their corresponding phases. P0 creates no business tables.
Never edit an applied migration; add a new one.
