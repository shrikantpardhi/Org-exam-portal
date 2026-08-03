# Database Design v1.0

## 06. Delivery Tables

## Purpose

The Delivery tables store live assessment sessions, attempts, responses, and delivery policies.

## Tables

### assessment_session

Stores live runtime session details for a user taking an assessment.

### student_attempt

Stores the final attempt record for the assessment.

### student_response

Stores answers submitted against specific question versions.

### delivery_policy

Stores configurable delivery rules and runtime behavior.

## Key Relationships

- assessment_session → student_attempt (1:1)
- student_attempt → student_response (1:N)
- student_response → question_version (N:1)
- delivery_policy can be reused by multiple assessments

## Design Rules

- Session data is runtime-focused
- Attempt data is the permanent academic record
- Responses must be linked to published question versions
- Auto-save and synchronization are required for reliability

## Status

This section is part of the frozen Version 1.0 architecture baseline.
