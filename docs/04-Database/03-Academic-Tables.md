# Database Design v1.0

## 03. Academic Tables

## Purpose

The Academic tables store the exam taxonomy used to organize questions and assessments.

## Tables

### exam

Stores competitive exams such as SSC, Banking, NEET, JEE, and UPSC.

### subject

Stores subjects under a specific exam.

### chapter

Stores chapters under a subject.

### topic

Stores topics under a chapter.

### sub_topic

Stores sub-topics under a topic.

### language

Stores supported languages.

### tag

Stores reusable tags for classification and filtering.

## Key Relationships

- exam → subject (1:N)
- subject → chapter (1:N)
- chapter → topic (1:N)
- topic → sub_topic (1:N)
- language and tag are reusable masters

## Design Rules

- Hierarchy is fixed and non-circular
- Child records cannot exist without their parent
- Tags and languages are shared masters used across modules

## Status

This section is part of the frozen Version 1.0 architecture baseline.
