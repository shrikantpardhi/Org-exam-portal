# Database Design v1.0

## 08. Analytics Tables

## Purpose

The Analytics tables store aggregated insights derived from transactional assessment data.

## Tables

### student_analytics

Stores student performance analytics.

### assessment_analytics

Stores assessment-level analytics.

### question_analytics

Stores question-level performance data.

### topic_analytics

Stores topic-level performance data.

### organization_analytics

Stores organization-wide summary analytics.

## Key Relationships

- user → student_analytics (1:1)
- assessment → assessment_analytics (1:1)
- question → question_analytics (1:1)
- topic → topic_analytics (1:1)
- organization → organization_analytics (1:1)

## Design Rules

- Analytics are derived data
- Analytics should be recalculable from source records
- Analytics must not alter transactional data
- Background jobs may refresh these records

## Status

This section is part of the frozen Version 1.0 architecture baseline.
