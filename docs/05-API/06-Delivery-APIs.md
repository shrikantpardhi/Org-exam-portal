# API Contract v1.0

## 06. Delivery APIs

## Purpose

The Delivery APIs manage live assessment sessions, attempts, responses, timers, and submission workflows.

## Core Endpoints

- `POST /api/v1/assessment-sessions`
- `GET /api/v1/assessment-sessions/{id}`
- `POST /api/v1/assessment-sessions/{id}/start`
- `POST /api/v1/assessment-sessions/{id}/autosave`
- `POST /api/v1/assessment-sessions/{id}/submit`
- `GET /api/v1/student-attempts/{id}`
- `GET /api/v1/student-attempts/{id}/responses`
- `POST /api/v1/student-attempts/{id}/responses`
- `PUT /api/v1/student-attempts/{id}/responses/{responseId}`
- `GET /api/v1/delivery-policies`
- `POST /api/v1/delivery-policies`

## Responsibilities

- Manage runtime assessment sessions
- Persist student responses safely
- Support auto-save and synchronization
- Enforce delivery policy rules

## Business Rules

- A session should produce a single attempt
- Responses must be tied to published question versions
- Submitted attempts should be protected from unauthorized mutation
- Delivery policy should control runtime behavior consistently

## Status

This is part of the frozen Version 1.0 architecture baseline.
