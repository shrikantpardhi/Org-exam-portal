# Technical Design v1.0

## 01. Architecture Overview

## Purpose

The platform uses a modular monolith architecture with clear domain boundaries, shared infrastructure components, and a REST-based frontend/backend separation.

## High-Level Stack

- Backend: Spring Boot 4
- Language: Java 25 (Java 21 acceptable if required)
- Frontend: Next.js + TypeScript
- Database: PostgreSQL
- Cache: Redis
- Migrations: Flyway
- Auth: JWT
- API: REST + OpenAPI
- File Storage: Local in development, S3-compatible in production

## Architecture Goals

- Keep Version 1 maintainable by a small team
- Preserve clean domain boundaries
- Avoid premature microservices
- Enable future scaling through modular design
- Make the system easy to observe and audit

## Core Architectural Principles

- Configuration over code
- Immutable published records
- Versioned entities for important business objects
- Snapshot-based execution for assessments and results
- Audit logs for important actions

## Major Runtime Areas

- Identity and access control
- Academic content
- Assessment assembly and scheduling
- Live delivery and response capture
- Evaluation and result generation
- Analytics and reporting
- Commerce and entitlement management
- Notifications and administration

## Status

This is part of the frozen Version 1.0 architecture baseline.
