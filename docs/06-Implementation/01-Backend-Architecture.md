# Implementation Guide v1.0

## 01. Backend Architecture

## Purpose

This document describes how the backend should be implemented using Spring Boot 4 and Java.

## Backend Stack

- Java 25 (or Java 21 if required)
- Spring Boot 4
- Spring Security
- Spring Data JPA
- Hibernate
- Flyway
- PostgreSQL
- Redis

## Architecture Style

The backend uses a modular monolith with feature-based modules.

## Module Responsibilities

### common
Shared utilities, enums, base entity, response wrappers, and exceptions.

### config
Application configuration for security, persistence, cache, scheduler, and OpenAPI.

### identity
Authentication, authorization, users, roles, sessions, and organization access.

### academic
Exam hierarchy, subjects, chapters, topics, languages, and tags.

### content
Questions, templates, versions, assets, explanations, hints, and previous exam history.

### assessment
Blueprints, assessments, versions, schedules, and collections.

### delivery
Live assessment sessions, attempts, responses, and delivery policy.

### evaluation
Answer keys, evaluations, results, and scorecards.

### analytics
Aggregated analytics for students, assessments, content, topics, and the organization.

### commerce
Access packages, plans, purchases, payment records, access grants, and coupons.

### communication
Notifications, email templates, and announcements.

### administration
Organization settings, business rules, lookups, and audit logs.

## Service Layer Rules

- Controllers should be thin
- Services should contain business logic
- Repositories should only handle persistence
- DTOs should be used for API boundaries
- Business workflows should remain inside the owning module

## Cross-Cutting Concerns

- Validation
- Error handling
- Logging
- Security
- Audit
- Background jobs
- Caching

## Status

This is part of the frozen Version 1.0 architecture baseline.
