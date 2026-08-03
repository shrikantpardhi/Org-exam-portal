# Database Design v1.0

## 04. Content Tables

## Purpose

The Content tables store questions, versions, templates, assets, explanations, hints, and previous exam references.

## Tables

### question

Stores the master question record.

### question_version

Stores immutable versions of a question.

### question_template

Stores question behavior templates.

### asset

Stores media assets linked to question versions.

### explanation

Stores explanations for question versions.

### hint

Stores optional hints for question versions.

### previous_exam_history

Stores historical exam references for questions.

### question_tag

Maps questions to tags.

## Key Relationships

- question → question_version (1:N)
- question_version → asset (1:N)
- question_version → explanation (1:1)
- question_version → hint (1:1 optional)
- question → previous_exam_history (1:N)
- question ↔ tag (M:N via question_tag)

## Design Rules

- Questions are versioned and published immutably
- Content assets should be linked to a specific version
- Templates define behavior only
- Previous exam history may contain multiple records per question

## Status

This section is part of the frozen Version 1.0 architecture baseline.
