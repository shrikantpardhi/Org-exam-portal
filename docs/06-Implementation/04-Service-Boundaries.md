# Implementation Guide v1.0

## 04. Service Boundaries

## Purpose

This document defines the service boundaries and interaction rules inside the modular monolith.

## Boundary Principles

- Each module owns its own business rules and entities
- Modules should communicate through services or application facades
- Repositories must remain internal to the owning module
- Shared utilities belong in the common module

## Major Boundaries

### Identity Boundary
Authentication, authorization, users, roles, permissions, sessions, and organization-level access.

### Academic Boundary
Exam taxonomy, subjects, chapters, topics, sub-topics, languages, and tags.

### Content Boundary
Questions, templates, versions, assets, explanations, hints, and historical references.

### Assessment Boundary
Blueprints, assessments, versions, schedules, and collections.

### Delivery Boundary
Live sessions, attempts, responses, and delivery policy.

### Evaluation Boundary
Answer keys, evaluation runs, results, and scorecards.

### Analytics Boundary
Derived metrics and dashboards.

### Commerce Boundary
Packages, plans, purchases, payments, grants, and coupons.

### Communication Boundary
Notifications, email templates, and announcements.

### Administration Boundary
Organization settings, business rules, lookup values, and audit logs.

## Status

This is part of the frozen Version 1.0 architecture baseline.
