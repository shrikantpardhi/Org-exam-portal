# Domain Model & ERD v1.0

## 01. Domain Overview and Design Principles

## Purpose

The domain model defines the core business domains, ownership boundaries, and entity relationships for the Smart Competitive Examination Platform.

## Design Principles

- Each entity belongs to one clear domain.
- Master data is separated from transactional data.
- Published records are immutable.
- Versioned entities preserve historical correctness.
- UUID is used as the primary key for all entities.
- Junction tables are used for many-to-many relationships.
- Business configuration is stored close to the owning domain.

## Core Domains

- Identity
- Academic
- Content
- Assessment
- Delivery
- Evaluation
- Analytics
- Commerce
- Communication
- Administration

## Base Entity

Every business entity uses a shared base structure:

- id
- status
- created_by
- created_on
- updated_by
- updated_on

## Versioned Entity Pattern

Some entities support versioning to preserve published history:

- Question
- Blueprint
- Assessment
- Answer Key

Versioned entities additionally store:

- version
- is_published
- published_by
- published_on

## Status

This chapter is part of the frozen Version 1.0 architecture baseline.
