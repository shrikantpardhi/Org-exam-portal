---
title: Architecture
order: 5
---

# Architecture

The platform is a modular monolith: one backend, one frontend, and a small set of infrastructure
services.

- **Backend** — a Java 21 / Spring Boot service that is authoritative for all business rules.
- **Frontend** — a Next.js / React application that renders every portal.
- **PostgreSQL** — the single source of truth for all data.
- **Redis** — cache only; never the source of truth.
- **OpenAPI** — the backend emits a machine-readable API document used to build the reference.

## Principles

- The backend is authoritative; the frontend never computes business outcomes.
- Every schema change is a Flyway migration; every contract change is an ADR.
- Authorization is decided from access grants, never from hard-coded role checks.
