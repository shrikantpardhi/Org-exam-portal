# Technical Design v1.0

## 04. Backend Standards

## Purpose

This document defines the backend implementation standards for the platform.

## Backend Stack

- Java 25 (or Java 21 if required)
- Spring Boot 4
- Spring Security
- Spring Data JPA
- Hibernate
- Flyway

## Code Organization

- Use feature-based package structure
- Keep controller, service, repository, entity, dto, mapper, validation, and exception layers separate
- Place shared utilities in the common module

## Service Design

- Services should contain business logic
- Controllers should only handle transport and orchestration
- Repositories should only handle persistence access
- DTOs should be used for API contracts

## Validation Standards

- Validate all incoming requests
- Use bean validation for simple constraints
- Use service-level validation for business rules
- Fail fast on invalid input

## Error Handling

- Use a global exception handler
- Return consistent error response formats
- Do not expose internal stack traces to users

## Testing Standards

- Unit tests for service logic
- Integration tests for repositories and workflows
- API tests for critical endpoints

## Design Principles

- Prefer composition over inheritance
- Keep classes focused on one responsibility
- Avoid shared mutable state
- Use constructor injection

## Status

This is part of the frozen Version 1.0 architecture baseline.
