# Master PRD v1.0

## 01. Executive Summary

The Smart Competitive Examination Platform is a web-first, enterprise-grade examination system designed for a single organization deployment. It supports realistic computer-based test simulations, configurable assessments, reusable question banks, result evaluation, analytics, commerce, communication, and administration.

The platform is intentionally designed as a modular monolith with clear domain boundaries. It is optimized for maintainability, auditability, and versioned configuration rather than hardcoded behavior.

## Key Outcomes

- Deliver authentic CBT-style examination experiences
- Support reusable and versioned question content
- Enable configurable exam blueprints and assessment delivery rules
- Provide accurate evaluation, scorecards, rankings, and analytics
- Support commercial access through collections, plans, purchases, and access grants
- Maintain full auditability and immutable published records
- Keep the system simple for a single organization while remaining future-ready

## Product Positioning

This product is not just an online test app. It is a configurable examination platform for coaching institutes, universities, recruitment workflows, certification providers, and enterprise assessment use cases.

## Architecture Summary

- Backend: Spring Boot 4
- Frontend: Next.js + TypeScript
- Database: PostgreSQL
- Cache: Redis
- Authentication: JWT
- Authorization: RBAC
- Migrations: Flyway
- API Style: REST with OpenAPI
- Documentation: Markdown first

## Frozen Product Principles

- Configuration over code
- Version everything important
- Immutable published data
- Snapshot-based execution
- Audit everything
- Single source of truth
- Modular architecture
- Engine-oriented design

## Core Business Engines

- Identity / Access Control Engine
- Academic Engine
- Question Engine
- Blueprint Engine
- Assessment Engine
- Assessment Delivery Engine
- Evaluation Engine
- Analytics Engine
- Commerce Engine
- Notification Engine
- Configuration Engine
- Reporting Engine
- Audit Engine

## Status

This document is part of the frozen Version 1.0 documentation baseline.
