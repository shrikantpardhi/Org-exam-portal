# Architecture Overview

This is a navigation summary. The authoritative architecture is the frozen specification
package in [`../../specs/`](../../specs/); where this summary and a frozen document disagree,
the frozen document governs.

## Style

**Modular monolith.** One deployable Spring Boot application with strong internal module
boundaries. No microservices in this version. Modules own their own logic, repositories, and
persistence and communicate through explicit service/application contracts — never by reaching
into another module's repositories or JPA entities.

## Runtime shape

```text
Browser / Desktop / Tablet
        |
      HTTPS
        |
     Nginx (edge: TLS, routing, request limits)
        |
   +----+---------------------+
   |                          |
Next.js                 Spring Boot 4
(frontend)              (modular monolith)
                              |
                +-------------+-------------+
                |             |             |
            PgBouncer      Redis        Object storage
                |          (cache)
                v
           PostgreSQL
        (source of truth)
```

## The eleven business modules

Identity, Academic, Content, Assessment, Delivery, Evaluation, Analytics, Commerce,
Communication, Administration, Infrastructure. They are architectural modules, not services.

## Layering inside a module

```text
controller -> service/application -> repository -> database
```

Cross-module access goes through a module's public service contract, not its repository.

## Non-negotiable invariants

1. PostgreSQL is the transactional source of truth.
2. Redis is cache only.
3. The backend is authoritative for authorization, timing, and scoring.
4. Published examination artifacts (question/blueprint/assessment versions, answer keys) are
   immutable; corrections create new versions.
5. AccessGrant is the authorization source of truth for access.
6. Flyway owns all schema change.
7. Start Exam performs no synchronous snapshot generation.
8. Audit insertion is transactional with the business mutation.
9. Content uses `MARKDOWN_LATEX_V1` with `\( ... \)`, `\[ ... \]`, and `[asset: <uuid>]`; raw
   HTML and executable MDX are rejected.

Full lists: `specs/06-Architecture-Freeze-V1.5.md §116, §125`,
`specs/09-P0-Foundation-V1.5.md §153`.

## What P0 establishes

Repository structure, backend and frontend bootstraps, PostgreSQL/Redis/PgBouncer/Flyway,
Docker Compose, Nginx edge, common API infrastructure, security foundation, observability
foundation, and CI. It implements **no business features**.

See [`../decisions/`](../decisions/) for the decision records behind these choices.
