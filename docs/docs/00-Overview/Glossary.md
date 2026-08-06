# Glossary

**Document Version:** 1.0

**Status:** Approved

---

# Purpose

This document defines the standard business and technical terminology used throughout the Configurable Examination Platform.

All documentation, APIs, database objects, source code, user interfaces, and discussions should use the terminology defined here to ensure consistency across engineering, product management, QA, support, and operations.

---

# General Principles

- Every business concept has one official name.
- Avoid synonyms unless explicitly documented.
- Use consistent terminology across documentation and source code.
- Database table names, API resources, UI labels, and documentation should align wherever practical.

---

# Business Terms

## Organization

A tenant or institution using the platform.

Examples include:

- University
- School
- Coaching Institute
- Certification Body
- Corporate Training Organization

---

## User

A person who can authenticate and interact with the platform.

Examples:

- Student
- Question Author
- Reviewer
- Administrator
- Evaluator

---

## Role

A collection of permissions assigned to one or more users.

Examples:

- Super Administrator
- Organization Administrator
- Academic Administrator
- Question Author
- Reviewer
- Student

---

## Permission

A single authorization that allows a user to perform a specific action.

Examples:

- Create Question
- Publish Assessment
- View Results
- Evaluate Answers

---

# Academic Terms

## Exam

The highest level of academic categorization.

Examples:

- UPSC
- JEE
- NEET
- GATE
- CAT

---

## Subject

A major academic discipline within an exam.

Example:

Mathematics

---

## Chapter

A logical subdivision of a subject.

---

## Topic

A specific concept within a chapter.

---

## Sub Topic

A finer classification under a topic.

---

## Difficulty Level

Represents the estimated complexity of a question.

Typical values:

- Easy
- Medium
- Hard

---

## Tag

A searchable label attached to business entities.

Examples:

- Algebra
- Organic Chemistry
- Previous Year
- Important

---

# Question Bank Terms

## Question

A reusable assessment item.

A question may contain:

- Statement
- Images
- Mathematical expressions
- Options
- Explanation
- Metadata

---

## Question Version

An immutable revision of a question.

Published versions cannot be modified.

---

## Question Type

Defines how a learner responds.

Examples:

- Multiple Choice
- Multiple Select
- True / False
- Integer
- Numerical
- Descriptive

---

## Question Author

A user responsible for creating questions.

---

## Reviewer

A user responsible for validating questions before publication.

---

# Blueprint Terms

## Blueprint

A reusable examination template that defines how an assessment should be assembled.

---

## Section

A logical grouping of questions inside a blueprint.

---

## Blueprint Version

An immutable published version of a blueprint.

---

# Assessment Terms

## Assessment

An examination delivered to candidates.

---

## Assessment Version

A published version of an assessment.

---

## Schedule

Defines when an assessment becomes available.

---

## Attempt

A student's participation in an assessment.

---

## Response

An answer submitted by a student.

---

# Evaluation Terms

## Answer Key

The official reference used during evaluation.

---

## Evaluation

The process of assigning marks to responses.

---

## Result

The final outcome of an evaluated assessment.

---

## Scorecard

A structured presentation of examination results.

---

# Analytics Terms

## Dashboard

A visual summary of key metrics.

---

## Metric

A measurable value used for reporting.

Examples:

- Average Score
- Pass Percentage
- Accuracy
- Completion Rate

---

# Administration Terms

## Business Rule

A configurable rule that influences platform behavior without requiring source code changes.

---

## Lookup

A configurable master data value.

Examples:

- Languages
- Categories
- Statuses

---

## Audit Log

A permanent record of significant user and system actions.

Audit records are immutable.

---

# Technical Terms

## Module

A logical business component responsible for a specific domain.

Examples:

- Identity
- Academic
- Question Bank
- Assessment

---

## API

An HTTP endpoint that exposes business functionality.

---

## DTO

Data Transfer Object used for communication between clients and services.

---

## Entity

A persistent business object stored in the database.

---

## Repository

The persistence layer responsible for database interaction.

---

## Service

The layer responsible for implementing business logic.

---

## Controller

The layer responsible for exposing REST APIs.

Controllers must remain thin and delegate business logic to services.

---

## Versioning

The process of preserving historical revisions of business entities.

Published entities are immutable.

---

# Abbreviations

| Abbreviation | Meaning |
|--------------|---------|
| API | Application Programming Interface |
| ADR | Architecture Decision Record |
| DTO | Data Transfer Object |
| ERD | Entity Relationship Diagram |
| JWT | JSON Web Token |
| RBAC | Role-Based Access Control |
| REST | Representational State Transfer |
| UUID | Universally Unique Identifier |

---

# References

- Vision
- Product Scope
- PRD
- Domain Model
- Technical Design
- API Documentation
- ADR Repository
