# P8 — Full Analytics & Insights: Testing Notes

Phase specification: `specs/17-P8-Analytics-V1.5.md`.

## Scope verified

- Frozen analytics tables and one-current-row-per-entity upsert semantics.
- Student / assessment / question / topic / organization calculations, with the frozen
  difficulty-index formula `1 - (correct / total)`.
- Section dynamic read model (no section table).
- Dashboard read APIs (the nine frozen GET endpoints).
- Incremental refresh and full rebuild, driven by `background_job`.
- Data-quality checks and drift detection.
- Cache strategy with PostgreSQL fallback and invalidation on refresh.

## Unit tests

| Test | What it proves |
|---|---|
| `analytics/service/AnalyticsCalculatorTest` | attempt/score/accuracy/average-time aggregation; accuracy excludes unanswered/bonus/dropped; missing population yields `null` not zero; frozen difficulty formula; assessment min/max/average; topic accuracy and average awarded marks |

Run: `cd backend && ./mvnw test -Dtest=AnalyticsCalculatorTest`.

## Integration tests

Base: `integration/analytics/AnalyticsIntegrationTest` (extends the P6 evaluation harness, so
attempts are created, answered, submitted, evaluated, and published through the real endpoints).

| Test | What it proves |
|---|---|
| `AnalyticsMigrationIntegrationTest` | five tables, unique/FK constraints, the student index, no time-series/fact tables, permission seed |
| `AnalyticsIntegrationTest.rebuildIsDeterministicAndDoesNotModifyTransactionalTables` | two full rebuilds produce identical aggregate rows; row counts of every transactional table (user, assessment, attempt, snapshot, response, evaluation, result, answer key) are unchanged by analytics |
| `AnalyticsIntegrationTest.difficultyIndexUsesTheFrozenFormulaEndToEnd` | one correct + one incorrect attempt → `difficulty_index = 0.5000` |
| `AnalyticsIntegrationTest.analyticsReadsFallBackToPostgreSqlWhenTheCacheIsUnavailable` | with Redis unreachable (test profile), reads still return correct values (cache is never authoritative) |
| `AnalyticsIntegrationTest.studentsCannotReadStaffAnalyticsAndSeeOnlyTheirOwn` | student reads own dashboard; staff analytics endpoints return 403 to a student and 200 to staff |

Run: `cd backend && ./mvnw test -Dtest="*Analytics*"`.

## Manual/curl smoke

```bash
# Rebuild is an operational function (no public write endpoint, §58). In a running deployment,
# queue a rebuild job (operator) or use the worker; then read:
curl -H "Authorization: Bearer <student>" $BASE/api/v1/analytics/student
curl -H "Authorization: Bearer <staff>"   $BASE/api/v1/analytics/assessments/<assessmentId>
curl -H "Authorization: Bearer <staff>"   $BASE/api/v1/analytics/assessments/<assessmentId>/sections
curl -H "Authorization: Bearer <staff>"   $BASE/api/v1/analytics/assessments/<assessmentId>/questions
curl -H "Authorization: Bearer <staff>"   $BASE/api/v1/analytics/questions/<questionId>
curl -H "Authorization: Bearer <staff>"   $BASE/api/v1/analytics/topics/<topicId>
curl -H "Authorization: Bearer <staff>"   $BASE/api/v1/analytics/organization
```

## Not exercised / deferred

- Analytics dashboard **frontend** (P8-16..20) — backend read APIs only (GAP-092).
- Rebuild/refresh load on staged data (P8-25/26) — no staging environment (GAP-094).
- Trend/daily/weekly buckets — derived from transactional timestamps; no trend tables (GAP-093).
