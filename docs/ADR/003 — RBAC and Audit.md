# ADR 003 — RBAC and Audit

## Status

Accepted

## Context

The platform must support multiple roles such as admin, faculty, reviewer, support, and student while keeping security controls clear and auditable.

## Decision

Use role-based access control with centralized permission evaluation and immutable audit logs.

## Rationale

- Simple to understand and manage
- Works well for an internal business platform
- Permissions can be granted through roles
- Audit logs provide traceability for sensitive actions
- Supports operational and compliance needs

## Consequences

- Users may have multiple roles
- Permission checks must be enforced consistently
- Sensitive operations must be written to the audit log
- Security logic must remain centralized rather than duplicated in UI code

## Alternatives Considered

- Attribute-based access control
- Hardcoded role checks
- Per-screen custom permissions only

## Why Rejected

These options are either too complex for Version 1 or too brittle for long-term maintenance.

## Notes

RBAC plus audit logging is the standard security baseline for the platform.
