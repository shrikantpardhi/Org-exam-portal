# Domain Model & ERD v1.0

## 06. Delivery Domain

## Purpose

The Delivery Domain manages live assessment execution, assessment sessions, attempts, responses, and delivery policies.

## Core Entities

- AssessmentSession
- StudentAttempt
- StudentResponse
- DeliveryPolicy

## Entity Relationships

- One AssessmentSession belongs to one User
- One AssessmentSession belongs to one AssessmentSchedule
- One AssessmentSession produces one StudentAttempt
- One StudentAttempt has many StudentResponses
- One StudentResponse belongs to one QuestionVersion
- One DeliveryPolicy can be reused by many assessments

## Responsibilities

- Manage the live exam state
- Preserve student responses reliably
- Track timers, session state, and recovery
- Enforce delivery rules consistently

## Business Rules

- Session state is transient and runtime-focused
- Attempt is the permanent academic record
- Responses are linked to published question versions
- Auto-save and synchronization are mandatory for reliability

## Status

Part of the frozen Version 1.0 architecture baseline.
