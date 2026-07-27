# Domain Model & ERD v1.0

## 04. Content Domain

## Purpose

The Content Domain manages the reusable question bank, versioning, templates, assets, explanations, hints, and previous exam references.

## Core Entities

- Question
- QuestionVersion
- QuestionTemplate
- Asset
- Explanation
- Hint
- PreviousExamHistory
- QuestionTag

## Entity Relationships

- One Question has many QuestionVersions
- One Question belongs to one QuestionTemplate
- One QuestionVersion has many Assets
- One QuestionVersion has one Explanation
- One QuestionVersion has one Hint
- One Question has many PreviousExamHistory records
- Questions and Tags are linked through QuestionTag

## Responsibilities

- Maintain the central question bank
- Preserve historical versions
- Support rich media and explanations
- Enable tagging and historical search

## Business Rules

- Published question versions are immutable
- Assets are linked to a specific question version
- Templates define behavior, not content
- Previous exam history may contain multiple rows per question

## Status

Part of the frozen Version 1.0 architecture baseline.
