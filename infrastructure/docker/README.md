# Shared Docker build assets

This directory holds Dockerfiles that are shared across the stack rather than
belonging to a single application module.

| File | Build context | Purpose |
|---|---|---|
| `pgbouncer.Dockerfile` | `./infrastructure/pgbouncer` | PgBouncer connection-management tier |

## Why the backend and frontend Dockerfiles are not here

`backend/Dockerfile` and `frontend/Dockerfile` intentionally live beside their
modules. A Docker build context cannot reach outside itself, and both images need
their module's sources:

- the backend image copies `pom.xml` + `src/` and runs the Maven build;
- the frontend image copies `package.json` + `package-lock.json`, runs `npm ci`,
  and then runs `next build`.

Moving them here would force the build context to the repository root, which
would ship the entire repository (including `node_modules`, `target`, and every
other module) into the daemon on each build. Keeping each Dockerfile next to its
module keeps the context small and the layer caching effective.

The PgBouncer image is different: it is a thin image over a stock package, and
its configuration and entrypoint live under `infrastructure/pgbouncer/`. The
Dockerfile is therefore shared here and referenced from `docker-compose.yml`:

```yaml
build:
  context: ./infrastructure/pgbouncer
  dockerfile: ../docker/pgbouncer.Dockerfile
```

## Credentials

No image contains a credential. Every secret is injected at start-up through the
environment, and the Compose file fails fast when one is missing
(`specs/09-P0-Foundation-V1.5.md` §67, §99). PgBouncer renders its configuration
and auth file from the environment in its entrypoint rather than baking them in.
