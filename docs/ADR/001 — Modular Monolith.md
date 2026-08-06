# ADR 001 — Modular Monolith

## Status

Accepted

## Context

The platform is intended for a single organization in Version 1. Version 1 needs to be maintainable, fast to develop, and easy to deploy without introducing unnecessary distributed system complexity.

## Decision

Use a modular monolith architecture.

## Rationale

- Easier to build and maintain for a small team
- Lower operational overhead
- Easier testing and deployment
- Clear domain boundaries can still be enforced internally
- Future microservice extraction remains possible if needed

## Consequences

- Shared application runtime
- Strong module discipline is required
- Internal boundaries must be respected in code
- Scaling is handled at the application level first
- Future service extraction may require refactoring if boundaries are not maintained well

## Alternatives Considered

- Microservices
- Traditional layered monolith
- Event-driven distributed architecture

## Why Rejected

These alternatives add complexity that is not necessary for Version 1.

## Notes

The modular monolith is the recommended baseline for the current product stage.
