# Project Bootstrap

## Project
Org Exam Portal

## Purpose
A web-first, configurable competitive examination platform for a single organization deployment. The platform supports realistic CBT simulations, assessment delivery, configurable scoring, analytics, commerce, and administration.

## Frozen Architecture Summary
- Single organization per deployment
- Modular monolith architecture
- Backend: Spring Boot 4
- Java: 25 (Java 21 acceptable if required)
- Frontend: Next.js + TypeScript
- Database: PostgreSQL
- Cache: Redis
- Migrations: Flyway
- Auth: JWT + RBAC
- API style: REST + OpenAPI
- File storage: local in development, S3-compatible in production

## Core Engines
- Identity / Access Control Engine
- Academic Engine
- Question Engine
- Blueprint Engine
- Assessment Engine
- Assessment Delivery Engine
- Evaluation Engine
- Analytics Engine
- Commerce Engine
- Notification Engine
- Configuration Engine
- Reporting Engine
- Audit Engine

## Product Principles
- Configuration over code
- Version everything important
- Immutable published data
- Snapshot-based execution
- Audit everything
- Single source of truth
- Modular architecture
- Engine-oriented design

## Documentation Rules
- Markdown is the source of truth
- PDFs are derived artifacts only
- Every important decision should have an ADR if needed
- All frozen documents are versioned
- Future changes should be introduced through versioning rather than mutation

## Repository Structure
```text
docs/
├── PROJECT_BOOTSTRAP.md
├── ARCHITECTURE_FREEZE.md
├── README.md
├── 01-PRD/
├── 02-Domain-Model/
├── 03-Technical-Design/
├── 04-Database/
├── 05-API/
├── 06-Implementation/
├── 07-Deployment/
└── ADR/
```

## Status
Frozen baseline for Version 1.0 documentation and implementation.
