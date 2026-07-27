# Domain Model & ERD v1.0

## 05. Assessment Domain

## Purpose

The Assessment Domain manages blueprint-driven assessment creation, versioning, scheduling, collections, and publication.

## Core Entities

- Blueprint
- BlueprintVersion
- Assessment
- AssessmentVersion
- AssessmentQuestion
- AssessmentSchedule
- AssessmentCollection
- CollectionAssessment

## Entity Relationships

- One Blueprint has many BlueprintVersions
- One Assessment has many AssessmentVersions
- One AssessmentVersion references one BlueprintVersion
- One AssessmentVersion has many AssessmentQuestions
- One AssessmentVersion has many AssessmentSchedules
- One AssessmentCollection has many Assessments through CollectionAssessment

## Responsibilities

- Define assessment patterns through blueprints
- Assemble assessments from published questions
- Preserve published versions immutably
- Support scheduling and collection grouping

## Business Rules

- Every assessment version uses a published blueprint version
- Assessment questions reference published question versions
- Published assessment versions are immutable
- Collections are reusable and may contain multiple assessments

## Status

Part of the frozen Version 1.0 architecture baseline.
