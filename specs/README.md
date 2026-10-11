# Smart Competitive Examination Platform

**Version:** 1.5
**Status:** FROZEN baseline — implementation-ready
**Date:** 7 October 2026

---

## What this is

The Smart Competitive Examination Platform is a configurable examination engine with a complete web-based learning, assessment, commerce, analytics, and administration platform around it.

This package is the frozen implementation baseline. Every document is self-contained. There are no appended amendments, no pointer files, and no open schema or API gaps.

---

## Scope

**One organization per deployment.**

The platform runs for a single active organization at a time. All domain models, schemas, APIs, and authorization contracts assume this. Code must nevertheless pass organization context explicitly through service contracts so that a future multi-tenant expansion is not blocked by structural choices made today.

Multi-tenancy is not implemented in this version.

---

## Architecture at a glance

- Modular monolith
- Java 25 + Spring Boot 4
- PostgreSQL (transactional source of truth)
- Redis (cache only)
- PgBouncer (production connection pooling)
- Flyway (schema migrations)
- Next.js + TypeScript frontend
- KaTeX server-side rendering for mathematical content
- Docker + Nginx edge
- Prometheus + Grafana + Loki observability

---

## Core principles

1. **Configuration drives examination behavior.** No exam-specific code scattered through the runtime.
2. **The backend owns business truth.** The frontend delivers experience.
3. **Published examination artifacts are immutable.** Corrections create new versions.
4. **The database protects integrity.** Constraints are the final boundary.
5. **Every published examination is reproducible.** A result can be explained years later.

---

## Package contents

| File | Purpose |
|---|---|
| `README.md` | This file |
| `V1.5-SCOPE-AND-FREEZE-AUTHORITY.md` | Scope and authority rules |
| `01-Master-PRD-V1.5.md` | Product requirements |
| `02-Domain-Model-V1.5.md` | Domain model and ERD |
| `03-Technical-Design-Document-V1.5.md` | Technical architecture |
| `04-Database-Schema-V1.5.md` | Complete database schema |
| `05-API-Contract-V1.5.md` | REST API contract |
| `06-Architecture-Freeze-V1.5.md` | Architecture freeze |
| `07-Development-Roadmap-V1.5.md` | Implementation roadmap |
| `08-Exam-Engine-and-CDUI-Contract-V1.5.md` | Exam engine and configuration-driven UI |
| `09-P0-Foundation-V1.5.md` | Foundation phase |
| `10-P1-Identity-V1.5.md` | Identity and security |
| `11-P2-Academic-V1.5.md` | Academic structure |
| `12-P3-Content-V1.5.md` | Content and question bank |
| `13-P4-Assessment-V1.5.md` | Assessment management |
| `14-P5-Delivery-V1.5.md` | Delivery and runtime |
| `15-P6-Evaluation-V1.5.md` | Evaluation and results |
| `16-P7-Pilot-V1.5.md` | Scale validation and pilot |
| `17-P8-Analytics-V1.5.md` | Full analytics |
| `18-P9-Commerce-V1.5.md` | Commerce and access |
| `19-P10-Communication-V1.5.md` | Communication |
| `20-P11-Administration-V1.5.md` | Full administration |
| `21-P12-Hardening-V1.5.md` | Production hardening |
| `22-P13-Reporting-V1.5.md` | Broader reporting |
| `V1.5-IMPLEMENTATION-STATUS.md` | Phase status matrix |
| `V2-MULTI-TENANT-BOUNDARY.md` | Future multi-tenant boundary |

---

## Database at a glance

63 tables across eleven modules.

**Identity:** organization, user, role, permission, user_role, role_permission, login_session

**Academic:** exam, subject, chapter, topic, sub_topic, language, tag

**Content:** question, question_version, question_template, question_option, asset, explanation, hint, previous_exam_history, question_tag

**Assessment:** blueprint, blueprint_version, assessment, assessment_version, assessment_question, assessment_schedule, assessment_collection, collection_assessment, assessment_selection_rule

**Delivery:** assessment_session, student_attempt, attempt_question, attempt_question_option, student_response, delivery_policy

**Evaluation:** answer_key, answer_key_detail, evaluation, result, scorecard

**Analytics:** student_analytics, assessment_analytics, question_analytics, topic_analytics, organization_analytics

**Commerce:** access_package, access_package_collection, subscription_plan, purchase, payment_record, access_grant, coupon

**Communication:** notification, email_template, announcement

**Administration:** organization_setting, business_rule, lookup, audit_log

**Infrastructure:** background_job

---

## Content representation

All renderable examination content uses a single contract: `MARKDOWN_LATEX_V1`.

- **Inline math:** `\( ... \)`
- **Block math:** `\[ ... \]`
- **Currency and dollar signs:** remain ordinary text (never parsed as math)
- **Raw HTML:** rejected at authoring
- **Executable MDX:** prohibited
- **Assets:** `[asset: <uuid>]` shortcodes, resolved server-side with authorization
- **Renderer:** KaTeX server-side rendering

---

## Deployment model

- **Development:** Docker Compose with local PostgreSQL and Redis
- **Production:** Nginx edge, Next.js frontend, Spring Boot modular monolith, PgBouncer, PostgreSQL, Redis, object storage, observability stack

---

## What is not in scope

The following are business workstreams outside the engineering package:

- Compliance frameworks (data protection, payment compliance, tax handling, minors' consent)
- Native mobile applications and mobile-first exam UI
- SMS and WhatsApp notification channels
- Pricing strategy and unit economics
- Go-to-market motion
- Student support operations (help center, chat, ticketing)
- Exam-day student-facing failure compensation policy
- Numerical recovery objectives (RPO / RTO)
- AI-driven capabilities (question generation, recommendations, natural language search)

The engineering baseline is complete and ready for implementation. These business workstreams must proceed in parallel with engineering.

---

## Rule

No document in this package introduces a database table, API endpoint, or authorization mechanism that is not listed in `04-Database-Schema-V1.5.md` or `05-API-Contract-V1.5.md`. If an implementation requires a structure that is not present, stop and raise a versioned change — do not invent the structure locally.