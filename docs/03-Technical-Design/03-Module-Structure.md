# Technical Design v1.0

## 03. Module Structure

## Purpose

This document defines the internal module organization of the backend codebase.

## Module Organization

The backend is organized into feature-based modules:

- identity
- academic
- content
- assessment
- delivery
- evaluation
- analytics
- commerce
- communication
- administration
- common
- config

## Standard Internal Structure per Module

```text
module/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
├── validation/
├── exception/
└── util/
```

## Common Module

The common module contains shared infrastructure components such as:

- Base entity
- Common enums
- Common response objects
- Shared exception types
- Utility methods
- Constants

## Config Module

The config module contains shared application configuration such as:

- Security configuration
- JWT configuration
- Swagger/OpenAPI configuration
- Redis configuration
- Database configuration
- Scheduler configuration

## Module Boundaries

- Each module owns its own entities and business rules.
- Cross-module access should occur through services, not direct repository sharing.
- Shared behavior belongs in the common module.

## Status

This is part of the frozen Version 1.0 architecture baseline.
