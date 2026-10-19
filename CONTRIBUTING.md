# Contributing

This repository implements the Smart Competitive Examination Platform, version 1.5.
All work follows the frozen specification package in [`specs/`](specs/).

## Before you start

1. Read [`AGENTS.md`](AGENTS.md).
2. Read [`specs/README.md`](specs/README.md) and
   [`specs/V1.5-SCOPE-AND-FREEZE-AUTHORITY.md`](specs/V1.5-SCOPE-AND-FREEZE-AUTHORITY.md).
3. Read the phase specification for the area you are changing.
4. Read the upstream specifications the phase depends on.

## Branching

| Change type | Branch pattern |
|---|---|
| Feature | `feature/<short-name>` |
| Bug fix | `fix/<short-name>` |
| Documentation | `docs/<short-name>` |
| Infrastructure | `infra/<short-name>` |

`main` is protected: pull request required, required checks must pass, and at least one review
approval before merge (P0-Foundation §8; P12 §134). Never push directly to `main`. Working
branches may be committed to directly; repository protection is never bypassed.

### Required status checks

Configure these five workflows as **required checks** on `main` (branch protection
`required_status_checks`):

1. `backend-ci` — Backend (compile, unit, integration, migration)
2. `frontend-ci` — Frontend (lock sync, lint, types, tests, build)
3. `secret-scan` — Secret scan (gitleaks)
4. `dependency-check` → job `backend-dependency-check` (OWASP Dependency Check)
5. `dependency-check` → job `frontend-dependency-check` (npm audit, high or critical)

```bash
gh api -X PUT repos/{owner}/{repo}/branches/main/protection \
  -f "required_status_checks[strict]=true" \
  -f "required_status_checks[checks][][context]=backend-ci" \
  -f "required_status_checks[checks][][context]=frontend-ci" \
  -f "required_status_checks[checks][][context]=secret-scan" \
  -f "required_status_checks[checks][][context]=dependency-check / backend-dependency-check" \
  -f "required_status_checks[checks][][context]=dependency-check / frontend-dependency-check" \
  -F "enforce_admins=true" \
  -F "required_pull_request_reviews[required_approving_review_count]=1" \
  -F "required_pull_request_reviews[dismiss_stale_reviews]=true" \
  -F "restrictions=null"
```

Note the two `dependency-check` contexts: a status-check name is
`<workflow> / <job name>`, so a workflow with two jobs yields two distinct checks, and a renamed
workflow must have its required-check context renamed with it or a pull request hangs on a check
that never starts.

## Workflow

1. Create a branch from `main`.
2. Make the smallest correct change in the frozen architecture.
3. Add or update tests.
4. Run the local checks:
   - Backend: `cd backend && ./mvnw clean verify`
   - Frontend: `cd frontend && npm run lint && npm run type-check && npm test && npm run build`
   - Infrastructure: `docker compose config`
5. Review your diff: `git status`, `git diff --stat`, `git diff`.
6. Open a pull request using the template and describe database, API, test, and security impact.
7. Ensure CI is green. Do not weaken, skip, or delete tests to make CI pass.

## Definition of done

A change is done only when all applicable items hold (`specs/07-Development-Roadmap-V1.5.md §33`):

- Requirement implemented; business rules and edge cases handled; invalid states rejected.
- Backend: entity, repository, service, DTO, mapper, validation, controller, exception handling.
- Database: Flyway migration, constraints, indexes, foreign keys, seed data if required.
- Frontend: screen, loading/empty/error states, validation, responsive behaviour, authorization.
- API: OpenAPI documentation with request/response and error examples.
- Testing: unit, integration, API, and end-to-end for critical workflows.
- Security: authorization verified, sensitive fields protected, input validated, audit considered.
- Operations: logs, monitoring, and background-job retry behaviour considered.
- Review: code reviewed, CI successful, migration reviewed.

## Database changes

All schema changes use **Flyway**. Never edit an applied migration. Never change the schema
manually. Add a new versioned migration and complete the migration review checklist
(`specs/09-P0-Foundation-V1.5.md §135`).

## Code style

- Follow the canonical formatting in [`.editorconfig`](.editorconfig).
- Java: constructor injection, DTO-based APIs, no JPA entities through controllers, service-layer
  business logic, no repository access from controllers.
- TypeScript: strict mode; no loosened strictness to make a build pass.

## Architecture changes

Before changing database schema, API contract, domain model, technology stack, security
architecture, exam-engine contract, or CDUI contract, stop and raise an Architecture Decision
Record under [`docs/decisions/`](docs/decisions/). No silent architecture changes.

## Secrets

Never commit secrets. Use environment configuration and a secret manager. Run the secret scan
in CI; a committed secret is an immediate failure.
