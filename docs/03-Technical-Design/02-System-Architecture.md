# Technical Design v1.0

## 02. System Architecture

## Purpose

This document describes the overall system architecture, runtime flow, and major platform layers.

## Architecture Style

The platform uses a Modular Monolith architecture for Version 1.

## Why Modular Monolith

- Easier to develop and deploy
- Lower operational overhead
- Better for a single organization deployment
- Clear boundaries allow future service extraction if needed
- Faster iteration for the initial release

## Major Layers

### 1. Presentation Layer

- Next.js frontend
- Role-based navigation
- Responsive UI
- API-driven screens

### 2. Application Layer

- Controllers
- Use case orchestration
- Validation
- Security checks
- Transaction boundaries

### 3. Domain Layer

- Business entities
- Domain services
- Validation rules
- Versioning logic
- Workflow rules

### 4. Infrastructure Layer

- PostgreSQL persistence
- Redis caching
- File storage
- Email and notification providers
- Logging and monitoring

## Runtime Flow

```text
User → Next.js UI → REST API → Application Service → Domain Logic → Repository → PostgreSQL/Redis/File Storage
```

## Major Communication Patterns

- REST APIs for frontend/backend communication
- Internal service calls within the monolith
- Background jobs for long-running work
- Event-based internal notifications for decoupled actions

## Cross-Cutting Concerns

- Authentication and authorization
- Audit logging
- Error handling
- Input validation
- Observability
- Versioned configuration

## Status

This is part of the frozen Version 1.0 architecture baseline.
