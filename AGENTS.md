# AGENTS.md — Instruction File for AI Coding Agents

## Project

Smart Competitive Examination Platform, version 1.5.

## Before you write any code

1. Read `specs/README.md`.
2. Read `specs/V1.5-SCOPE-AND-FREEZE-AUTHORITY.md`.
3. Read the phase specification you have been assigned.
4. Read the upstream specifications the phase depends on, listed at the top of the phase file.

## Frozen rules

1. Do not invent database tables.
2. Do not invent API endpoints.
3. Do not modify frozen schema, API, or domain contracts.
4. Do not hard-code exam names into frontend or backend code.
5. Do not expose correct answers to students.
6. Do not generate snapshots synchronously from Start Exam.
7. PostgreSQL is the source of truth.
8. Redis is cache only.
9. Backend is authoritative.
10. Use Flyway for all schema changes.
11. Audit insertion is transactional with the business mutation.
12. AccessGrant is the authorization source of truth.
13. Published examination artifacts are immutable.
14. Content uses MARKDOWN_LATEX_V1.
15. Math delimiters are `\( ... \)` and `\[ ... \]` only.
16. Asset shortcodes are `[asset: <uuid>]`.
17. Reject raw HTML and executable MDX in content.
18. Stale-write protection uses HTTP ETag and If-Match.
19. Evaluation reads correctness only from answer_key_detail.
20. Stop and report when a frozen contract gap is found. Do not silently resolve it.

## Workflow

For each assigned task:

1. Read the phase specification section for the task.
2. Inspect the existing repository.
3. Implement the smallest correct change.
4. Write tests.
5. Run the tests and the build.
6. Review the diff.
7. Report files changed, tests added, commands run, and remaining issues.
8. Stop for review before moving to the next high-risk task.

## Reporting

After every task, report:

- Files changed
- Migration files added
- API endpoints added or modified
- Tests added
- Commands run
- Build result
- Unresolved issues
- Any architecture deviation

## Never

- Never push to main.
- Never commit secrets.
- Never skip tests to make CI green.
- Never delete a failing test.
- Never invent a workaround for a frozen contract gap.