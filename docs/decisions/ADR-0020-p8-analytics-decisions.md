# ADR-0020 — P8 Analytics: derivation, bounds, and refresh decisions

- **Status:** Accepted
- **Phase:** P8 — Full Analytics & Insights
- **Spec:** `specs/17-P8-Analytics-V1.5.md`
- **Preserves:** the frozen analytics schema (five tables), the frozen read endpoints, and the
  transactional source of truth.

## Context

P8 turns transactional examination history into derived aggregates. The frozen schema already fixes
five current-aggregate tables, the difficulty-index formula, and the read endpoints. This ADR records
the implementation decisions the specification left open. No table, column, or endpoint is added.

## Decisions

1. **Source of truth.** Analytics is derived and recalculable; PostgreSQL transactional data remains
   authoritative (`AGENTS.md` rule 7, `§3, §10`). The module never writes to a transactional table.

2. **Grain.** One current aggregate row per entity: student (user), assessment, question (parent
   Question), topic, organization. Aggregates are never interpreted as time-series data (`§8, §9`).

3. **Inclusion rule (centralized).** An attempt qualifies when it is `SUBMITTED`/`EVALUATED` **and**
   has a current Result on record (`§15, §16`). `NOT_STARTED`, `IN_PROGRESS`, and submitted-but-not-yet
   evaluated attempts are excluded. The rule lives in `AnalyticsRefreshService`, not in dashboards.

4. **Accuracy.** `correct / (correct + incorrect) * 100`, bounded to [0, 100]; unanswered, dropped,
   bonus, invalid, and unevaluated outcomes are excluded from the denominator (`§17, §35, §115`).

5. **Difficulty index.** Exactly `1 - (correct_attempts / total_attempts)` with
   `total = correct + incorrect`, bounded to [0, 1], `NUMERIC(8,4)` (`§35`). It is not redefined.

6. **Missing data.** An empty population yields `null`, never a misleading zero (`§90`). Derived
   percentages (correct/incorrect/skipped, `§28, §33`) are computed at read time rather than stored.

7. **Per-question correctness.** Per-question outcomes are produced by the **existing** P6 evaluator
   over the authoritative answer key (`EvaluationEngine`), exposed through
   `evaluation/api/AnalyticsOutcomeCatalog`. Analytics is not a second scoring engine (`§1966`). P6
   does not persist per-question outcomes (GAP-079), so they are derived deterministically on each
   refresh and are never treated as authoritative storage.

8. **Section read model.** Section analytics is a dynamic aggregation over
   `AttemptQuestion.section_name`, persisted question time, and evaluation outcomes; no section table
   is introduced (`§45-§48`). Section names are scoped to the assessment version.

9. **Module boundary.** Analytics reads other modules only through narrow application contracts
   (`AnalyticsIdentityCatalog`, `AttemptAnalyticsCatalog`, `AssessmentAnalyticsCatalog`,
   `QuestionTaxonomyCatalog`, `AnalyticsOutcomeCatalog`). It never guesses taxonomy (`§74`).

10. **Refresh and rebuild.** `AnalyticsRefreshService` provides a bounded incremental refresh (scoped
    to the impacted attempt's user and assessment version) and a full rebuild for all grains. Every
    metric is recomputed and upserted via `UNIQUE(entity_id)`; no counter is incremented blindly, so
    refresh is idempotent and a formula change is recoverable by a rebuild (`§92, §95-§97`).

11. **Job plumbing.** Refresh/rebuild run on the shared `background_job` table (`JobTypes`), consumed
    by `AnalyticsWorker`. Result publication emits an `ANALYTICS_REFRESH` signal through the same
    shared infrastructure, so Evaluation never depends on Analytics (`§64, §106`).

12. **Caching.** Only the five single-aggregate reads are cached (scope-bearing keys, short TTL);
    derived lists are computed per request. The cache is never authoritative, so a Redis outage
    degrades to a PostgreSQL read (`§62, §63`). The refresh path evicts the affected keys.

13. **Data quality.** `AnalyticsDataQualityService` runs read-only drift checks (negative/impossible
    counts, accuracy/time/difficulty bounds, score-range consistency) and emits metrics (`§113-§117`).

## Consequences

- Rebuild determinism, transactional isolation, and cache fallback to PostgreSQL are verified by
  `AnalyticsIntegrationTest`; the difficulty formula and bounds by `AnalyticsCalculatorTest`.
- Question/topic correctness depends on re-running the P6 evaluator per attempt (GAP-090) and the
  incremental question/topic scope is the affected assessment versions (GAP-091).
- The analytics frontend dashboards (P8-16..20) are not part of this backend slice (GAP-092).
- Full-rebuild performance evidence on staged data is outstanding (GAP-094).

## Contract gaps discovered

Recorded in `docs/known-gaps.md` (GAP-089 … GAP-096). None was silently resolved; none changes a
frozen table, column, or endpoint.
