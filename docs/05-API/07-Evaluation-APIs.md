# API Contract v1.0

## 07. Evaluation APIs

## Purpose

The Evaluation APIs manage answer keys, evaluation runs, results, scorecards, and result publication.

## Core Endpoints

- `GET /api/v1/answer-keys`
- `POST /api/v1/assessment-versions/{id}/answer-keys`
- `GET /api/v1/answer-keys/{id}`
- `POST /api/v1/evaluations`
- `GET /api/v1/evaluations/{id}`
- `POST /api/v1/evaluations/{id}/re-evaluate`
- `GET /api/v1/results/{id}`
- `POST /api/v1/results/{id}/publish`
- `GET /api/v1/scorecards/{id}`

## Responsibilities

- Evaluate submitted attempts against answer keys
- Generate results, ranks, percentiles, and scorecards
- Support controlled re-evaluation and publication workflows

## Business Rules

- Answer keys are versioned
- Evaluations should reference a known attempt and answer key
- Published results are stable unless explicitly regenerated
- Scorecards are derived from results

## Status

This is part of the frozen Version 1.0 architecture baseline.
