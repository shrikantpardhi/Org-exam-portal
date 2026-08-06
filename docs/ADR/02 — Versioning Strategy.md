# ADR 002 — Versioning Strategy

## Status

Accepted

## Context

Questions, blueprints, assessments, answer keys, and results need to remain historically correct after publication. Updating a published entity in place would break reproducibility and auditability.

## Decision

Use explicit versioning for core published business entities.

## Rationale

- Preserves historical accuracy
- Prevents published data from changing unexpectedly
- Makes assessments reproducible
- Supports audit and support workflows
- Allows safe draft-to-publish workflows

## Consequences

- Published records become immutable
- New changes must create a new version
- Queries must be version-aware
- API and database design must support version references

## Entities Covered

- Question
- Question Version
- Blueprint
- Blueprint Version
- Assessment
- Assessment Version
- Answer Key
- Result artifacts where relevant

## Alternatives Considered

- In-place edits
- Soft version fields without publish immutability
- Copy-on-write without explicit version tracking

## Why Rejected

These approaches would reduce traceability and create ambiguity in historical results.
