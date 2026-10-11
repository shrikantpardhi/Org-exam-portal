# ADR-0001: Modular monolith architecture

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/06-Architecture-Freeze-V1.5.md §5, §6, §7`,
  `specs/03-Technical-Design-Document-V1.5.md §4`

## Context

The platform must be maintainable at the current scale while remaining extractable into
services later. Microservices would introduce service discovery, distributed tracing,
inter-service authentication, distributed transactions, and message brokers — complexity not
justified for the initial single-organization product.

## Decision

One deployable Spring Boot application with eleven explicit business modules (Identity,
Academic, Content, Assessment, Delivery, Evaluation, Analytics, Commerce, Communication,
Administration, Infrastructure). Module boundaries are enforced through explicit service/
application contracts. No module accesses another module's repositories or JPA entities.

## Consequences

- Simple deployment, local development, transactions, and debugging.
- Boundaries must be actively enforced in code review (dependency-direction gate).
- Future extraction is possible but not premature.
