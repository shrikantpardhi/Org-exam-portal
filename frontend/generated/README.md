# Generated API reference

This directory holds the retained OpenAPI document used to build the public /docs/api reference
(ADR-0036, GAP-170).

- openapi.json — the backend's OpenAPI 3.1 document, captured from GET /v3/api-docs.

## Regenerate after a backend contract change

1. Build the backend, which retains the document via OpenApiDocumentationGenerationTest:

   ./mvnw -q clean verify   (writes backend/target/generated/openapi.json)

2. Copy it here:

   Copy backend/target/generated/openapi.json to frontend/generated/openapi.json

The frontend build prefers backend/target/generated/openapi.json when it is present (so the verify
pipeline stays fresh) and falls back to this checked-in snapshot for a standalone frontend build.
