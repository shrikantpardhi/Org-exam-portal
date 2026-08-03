# Database Design v1.0

## 07. Evaluation Tables

## Purpose

The Evaluation tables store answer keys, evaluations, results, and scorecards.

## Tables

### answer_key

Stores the correct answer set for a specific assessment version.

### evaluation

Stores the evaluation run for a student attempt.

### result

Stores the official published result after evaluation.

### scorecard

Stores the generated scorecard artifact or reference.

## Key Relationships

- assessment_version → answer_key (1:N)
- student_attempt → evaluation (1:1)
- evaluation → result (1:1)
- result → scorecard (1:1)

## Design Rules

- Answer keys are versioned and immutable after publication
- Every attempt should map to one evaluation
- Results should remain stable after publication unless re-evaluation is explicitly initiated
- Scorecards are derived presentation artifacts

## Status

This section is part of the frozen Version 1.0 architecture baseline.
