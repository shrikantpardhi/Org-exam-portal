# ADR-0002: Backend, frontend, and infrastructure technology stack

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/06-Architecture-Freeze-V1.5.md §9, §20, §126`,
  `specs/03-Technical-Design-Document-V1.5.md §6`

## Decision

| Layer | Choice |
|---|---|
| Backend language | Java 25 preferred; **Java 21** used (approved enterprise fallback) |
| Backend framework | Spring Boot 4 (`4.0.x`) |
| Security | Spring Security |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Cache | Redis |
| Connection pooling | PgBouncer |
| Migrations | Flyway |
| API docs | OpenAPI (springdoc-openapi 3.x) |
| Frontend | Next.js + TypeScript |
| Styling / UI | Tailwind CSS + shadcn/ui |
| Server state | TanStack Query |
| Forms / validation | React Hook Form + Zod |
| HTTP client | Axios |
| Content rendering | Unified/Remark + KaTeX SSR + Rehype sanitization |
| Containers | Docker / Docker Compose |
| Edge | Nginx |
| CI/CD | GitHub Actions |
| Observability | Prometheus + Grafana + Loki |

## Version pinning

- Spring Boot `4.0.8` (the 4.0 line; a frozen-compatible stable patch).
- springdoc-openapi `3.1.1` (Spring Boot 4 line).
- Maven via the committed wrapper; JDK 21.
- Node.js 22 via `frontend/.nvmrc`; dependencies pinned by `package-lock.json`.

## Notes

- Backend and frontend build independently.
- A major technology substitution would require a formal architecture review and an
  architecture-version change.
