# API Contract v1.0

## 08. Analytics APIs

## Purpose

The Analytics APIs expose aggregated insights for students, assessments, questions, topics, and the organization.

## Core Endpoints

- `GET /api/v1/students/{id}/analytics`
- `GET /api/v1/assessments/{id}/analytics`
- `GET /api/v1/questions/{id}/analytics`
- `GET /api/v1/topics/{id}/analytics`
- `GET /api/v1/organizations/{id}/analytics`
- `GET /api/v1/analytics/dashboard`

## Responsibilities

- Provide student performance insights
- Provide assessment and content quality insights
- Support organization-level dashboards
- Expose derived analytics data to the frontend

## Business Rules

- Analytics are read-only from the API consumer perspective
- Analytics should reflect derived data from source transactions
- The API should not recalculate metrics synchronously unless explicitly designed for that workflow

## Status

This is part of the frozen Version 1.0 architecture baseline.
