# 07-Deployment/04-Migrations-and-Rollbacks.md

# Deployment Guide v1.0

## 04. Migrations and Rollbacks

## Purpose

This document defines how database migrations and rollback handling should be managed during deployment.

## Migration Strategy

- All schema changes must be versioned.
- Flyway is the migration tool for Version 1.
- Migrations must be deterministic and repeatable.
- Migrations should be reviewed before release.
- Manual database edits are not allowed in normal release flow.

## Migration Rules

- Each migration must have a unique version.
- Migrations must be backward-compatible whenever possible.
- Destructive changes should be handled carefully and only when approved.
- New columns should normally be added before old columns are retired.
- Existing production data must be considered in every schema change.

## Rollback Principles

- Every release should have a rollback plan.
- Rollback should restore the application to the previous known-good version.
- Rollback should not depend on unreproducible manual changes.
- Data migrations must be designed with rollback safety in mind.

## Recommended Practices

- Prefer additive migrations.
- Use separate migrations for schema and data where needed.
- Validate migrations in lower environments before production.
- Keep migration scripts reviewed and version-controlled.

## Status

This is part of the frozen Version 1.0 architecture baseline.
