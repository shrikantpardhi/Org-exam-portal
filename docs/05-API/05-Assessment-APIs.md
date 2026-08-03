# API Contract v1.0

## 05. Assessment APIs

## Purpose

The Assessment APIs manage blueprints, assessments, assessment versions, schedules, and collections.

## Core Endpoints

- `GET /api/v1/blueprints`
- `POST /api/v1/blueprints`
- `GET /api/v1/blueprints/{id}`
- `PUT /api/v1/blueprints/{id}`
- `GET /api/v1/blueprints/{id}/versions`
- `POST /api/v1/blueprints/{id}/versions`
- `GET /api/v1/assessments`
- `POST /api/v1/assessments`
- `GET /api/v1/assessments/{id}`
- `PUT /api/v1/assessments/{id}`
- `GET /api/v1/assessments/{id}/versions`
- `POST /api/v1/assessments/{id}/versions`
- `GET /api/v1/assessment-versions/{id}/questions`
- `POST /api/v1/assessment-versions/{id}/questions`
- `GET /api/v1/assessment-versions/{id}/schedules`
- `POST /api/v1/assessment-versions/{id}/schedules`
- `GET /api/v1/assessment-collections`
- `POST /api/v1/assessment-collections`
- `GET /api/v1/assessment-collections/{id}`

## Responsibilities

- Define exam blueprints
- Assemble assessments from published questions
- Manage versioning and scheduling
- Support collections for commerce and operations

## Business Rules

- Published blueprints and assessments are immutable
- Assessment versions reference published question versions only
- Scheduling should not modify published content
- Collections are reusable groupings

## Status

This is part of the frozen Version 1.0 architecture baseline.
