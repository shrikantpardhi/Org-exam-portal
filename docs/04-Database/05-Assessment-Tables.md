# Database Design v1.0

## 05. Assessment Tables

## Purpose

The Assessment tables store blueprints, assessments, assessment versions, question mappings, schedules, and collections.

## Tables

### blueprint

Stores the master blueprint record.

### blueprint_version

Stores versioned blueprint configuration.

### assessment

Stores the master assessment record.

### assessment_version

Stores versioned assessments and published exam content.

### assessment_question

Maps questions to assessment versions.

### assessment_schedule

Stores availability windows and schedule information.

### assessment_collection

Stores reusable collections of assessments.

### collection_assessment

Maps assessments to collections.

## Key Relationships

- blueprint → blueprint_version (1:N)
- assessment → assessment_version (1:N)
- blueprint_version → assessment_version (1:N)
- assessment_version → assessment_question (1:N)
- assessment_version → assessment_schedule (1:N)
- assessment_collection ↔ assessment (M:N via collection_assessment)

## Design Rules

- Published blueprints and assessments are immutable
- Assessment questions reference published question versions
- Scheduling must be independent from the content definition
- Collections are reusable commercial and operational groupings

## Status

This section is part of the frozen Version 1.0 architecture baseline.
