# ADR-0009: Integration-test datastore strategy

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/09-P0-Foundation-V1.5.md §71–§75, §109`

## Context

The specification prefers disposable PostgreSQL/Redis containers for integration testing
(Testcontainers) but explicitly allows an alternative when Testcontainers is not practical:
*"If the project chooses not to use Testcontainers, the implementation documents the
alternative."*

The P0 development host has no Docker daemon, so Testcontainers cannot run there. Integration
tests must nonetheless run deterministically in CI and locally, and must exercise real
PostgreSQL behaviour (JSONB, partial indexes, `TIMESTAMPTZ`) rather than an H2 approximation.

## Decision

- **PostgreSQL integration tests** use an embedded, disposable PostgreSQL server
  (`io.zonky.test:embedded-postgres`) started from the test JVM. It runs a real PostgreSQL
  binary, so PostgreSQL-specific migration behaviour is genuinely exercised.
- The **runtime path remains PostgreSQL**; the embedded server is a *test* dependency only and
  never a production datastore.
- **Redis** behaviour is verified without requiring a server: the cache-aside fallback is unit
  tested against a failing client, and the deterministic "Redis unavailable → read
  authoritative source" path is asserted. A Redis integration test runs only when a test Redis
  is reachable.

## Consequences

- Integration tests are deterministic and require no pre-running database or Docker.
- Unit tests introduce no external services and do not make CI nondeterministic.
- If the team later adopts Testcontainers, the embedded strategy can be swapped behind the same
  Spring test profile without changing test intent.
