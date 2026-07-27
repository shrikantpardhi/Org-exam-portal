# Master PRD v1.0

## 03. Scope and Assumptions

## Product Scope

The platform is designed for a single organization deployment and supports the full lifecycle of examination content and delivery.

### In Scope

- Identity and access management
- Academic taxonomy management
- Question templates and question bank
- Assessment blueprints and assessment creation
- Assessment delivery and runtime session management
- Evaluation, scorecards, rankings, and results
- Analytics for students, assessments, questions, topics, and organization health
- Commerce through access packages and subscription plans
- Notifications, email templates, and announcements
- Administration, configuration, audit, and operational support
- Reports for operational and executive use

### Out of Scope for Version 1

- Multi-organization tenancy
- External marketplace support
- Advanced proctoring
- SSO and enterprise identity federation
- Full AI automation in core workflows
- Complex BI warehouse integrations

## Assumptions

- The platform is deployed for one organization at a time.
- The organization may manage multiple roles but only within one deployment boundary.
- Assessment patterns may differ by exam, but the engine remains reusable.
- Published blueprints, assessments, answer keys, and results are immutable.
- Markdown documentation is the source of truth for requirements and design.

## Constraints

- Version 1 must remain maintainable by a small team.
- The architecture should favor configurability over custom code.
- The first release should not require microservices.
- All critical records must be audit-friendly and reproducible.

## Status

Frozen architecture baseline for Version 1.0.
