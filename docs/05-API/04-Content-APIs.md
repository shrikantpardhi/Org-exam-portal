# API Contract v1.0

## 04. Content APIs

## Purpose

The Content APIs manage question templates, questions, versions, assets, explanations, hints, previous exam history, and tags.

## Core Endpoints

- `GET /api/v1/question-templates`
- `POST /api/v1/question-templates`
- `GET /api/v1/questions`
- `GET /api/v1/questions/{id}`
- `POST /api/v1/questions`
- `PUT /api/v1/questions/{id}`
- `GET /api/v1/questions/{id}/versions`
- `POST /api/v1/questions/{id}/versions`
- `GET /api/v1/question-versions/{id}`
- `POST /api/v1/question-versions/{id}/assets`
- `GET /api/v1/question-versions/{id}/explanation`
- `GET /api/v1/question-versions/{id}/hint`
- `GET /api/v1/questions/{id}/previous-exam-history`
- `POST /api/v1/questions/{id}/tags`

## Responsibilities

- Maintain the reusable question bank
- Preserve version history
- Support assets and learning aids
- Support historical references and tagging

## Business Rules

- Published question versions are immutable
- Assets belong to a specific version
- Explanations and hints should map to versioned content
- Question templates define behaviour rather than storing content

## Status

This is part of the frozen Version 1.0 architecture baseline.
