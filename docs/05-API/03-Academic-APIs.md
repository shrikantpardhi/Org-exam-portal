# API Contract v1.0

## 03. Academic APIs

## Purpose

The Academic APIs manage exam taxonomy masters used across questions and assessments.

## Core Endpoints

- `GET /api/v1/exams`
- `GET /api/v1/exams/{id}`
- `POST /api/v1/exams`
- `PUT /api/v1/exams/{id}`
- `GET /api/v1/exams/{id}/subjects`
- `POST /api/v1/exams/{id}/subjects`
- `GET /api/v1/subjects/{id}`
- `PUT /api/v1/subjects/{id}`
- `GET /api/v1/subjects/{id}/chapters`
- `GET /api/v1/chapters/{id}/topics`
- `GET /api/v1/topics/{id}/sub-topics`
- `GET /api/v1/languages`
- `GET /api/v1/tags`

## Responsibilities

- Maintain the exam hierarchy
- Support language and tag masters
- Serve the academic classification layer for content and analytics

## Business Rules

- The hierarchy is fixed and non-circular
- Child records cannot exist without parent records
- Tags and languages are reusable masters
- Changes to masters should be audited when applicable

## Status

This is part of the frozen Version 1.0 architecture baseline.
