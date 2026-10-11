# ADR-0005: Redis is cache only

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/06-Architecture-Freeze-V1.5.md §16, §17`,
  `specs/03-Technical-Design-Document-V1.5.md §51–§56`

## Decision

Redis is a supporting cache and lightweight coordination store only. It is never the
authoritative source for student responses, attempts, payments, results, answer keys, access
grants, audit records, background job status, or official examination state.

Approved cache categories: organization settings, business rules, lookup values, roles and
permissions, exam master configuration, and short-lived application cache.

## Failure behaviour

If Redis is unavailable, the application logs a controlled warning and reads the authoritative
datastore (PostgreSQL). Cache failure never becomes data loss. The application does not crash
because optional caching is unavailable.

## Implementation

A `CacheService` abstraction wraps the Redis client. Every read is a cache-aside operation with
a checked fallback: a cache miss or a cache error both fall through to the caller's loader. The
abstraction is deliberately independent of Redis API details so that the failure path is
testable without a running server.

## Consequences

- No transactional data is stored exclusively in Redis.
- Redis may be omitted in a minimal local or test environment without breaking cache-optional
  functionality.
