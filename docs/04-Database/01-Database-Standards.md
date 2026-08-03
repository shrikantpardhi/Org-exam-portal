# Database Design v1.0

## 01. Database Standards

## Purpose

This document defines the standards used across all database tables in the platform.

## General Standards

- PostgreSQL is the primary database
- UUID is used as the primary key for business entities
- Singular snake_case table names are used
- Foreign key columns end with `_id`
- Audit columns are standardized across entities
- Soft delete is represented through status or archive state rather than hard deletion

## Base Fields

Common fields for most tables:

- id
- status
- created_by
- created_on
- updated_by
- updated_on

## Versioned Entity Fields

Versioned entities such as questions, blueprints, assessments, and answer keys also use:

- version
- is_published
- published_by
- published_on

## Constraints

- Primary keys must be unique and immutable
- Foreign key integrity must be preserved
- Business codes should be unique where appropriate
- Lookup and reference data should be stable and controlled

## Indexing Principles

- Index frequently searched columns
- Index foreign keys
- Add composite indexes for common access patterns
- Avoid unnecessary indexes that slow down writes

## JSONB Usage

Use JSONB only for configurable structures such as business rules or flexible runtime settings. Avoid using JSONB for core transactional records.

## Status

This is part of the frozen Version 1.0 architecture baseline.
