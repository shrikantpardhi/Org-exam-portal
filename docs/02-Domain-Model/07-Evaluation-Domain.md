# Domain Model & ERD v1.0

## 07. Evaluation Domain

## Purpose

The Evaluation Domain manages answer keys, evaluation runs, results, scorecards, rankings, and publication of official outcomes.

## Core Entities

- AnswerKey
- Evaluation
- Result
- Scorecard

## Entity Relationships

- One AssessmentVersion has many AnswerKeys
- One StudentAttempt has one Evaluation
- One Evaluation produces one Result
- One Result produces one Scorecard

## Responsibilities

- Apply the correct answer key
- Calculate marks and negative marking
- Generate ranks and percentiles
- Produce official results and scorecards

## Business Rules

- Answer keys are versioned
- Results are produced from a specific evaluation context
- Published results are immutable unless re-evaluation is explicitly initiated
- Scorecards are derived artifacts

## Status

Part of the frozen Version 1.0 architecture baseline.
