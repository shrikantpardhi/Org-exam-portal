---
title: API reference
order: 3
---

# API reference

The platform exposes a versioned JSON API under /api/v1. The reference below is generated from the
backend's OpenAPI document at build time, so it always matches the running contracts.

## Conventions

- Every response is an envelope: { success, message, data, pagination }.
- Errors carry a stable code in the errors array, so clients can branch on it.
- Protected endpoints require a bearer token; authorization is enforced on the server for every
  request.
- All state is authoritative on the server; the browser holds no source of truth.

## Endpoint reference

The sections below are generated from the OpenAPI document. Use the filter to narrow the list by
path, method, or description, and expand an endpoint for its parameters and request body.
