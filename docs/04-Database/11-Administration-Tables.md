# Database Design v1.0

## 11. Administration Tables

## Purpose

The Administration tables store organization settings, business rules, lookup values, and audit logs.

## Tables

### organization_setting

Stores organization-level configuration values.

### business_rule

Stores configurable platform and business rules.

### lookup

Stores reusable lookup master values such as statuses, difficulty levels, and categories.

### audit_log

Stores immutable audit entries for sensitive actions and key platform events.

## Key Relationships

- organization → organization_setting (1:1)
- user → audit_log (1:N)
- business_rule and lookup are shared administrative masters

## Design Rules

- Configuration should be centrally manageable
- Lookup values should be reusable across the platform
- Audit logs should be immutable and query-friendly
- Settings should use JSONB where flexibility is required

## Status

This section is part of the frozen Version 1.0 architecture baseline.
