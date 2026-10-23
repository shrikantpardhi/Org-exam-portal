# ADR-0028 — Composite indexes for worker candidate selection and the notification list

- **Status:** Accepted
- **Phase:** P12 — Production hardening (index amendment raised by the P12-04 database review)
- **Spec:** `specs/04-Database-Schema-V1.5.md` §33.1 (`notification`), §35.1 (`background_job`);
  `specs/06-Architecture-Freeze-V1.5.md` §19 (every schema change is a versioned migration)
- **Closes:** GAP-139
- **Migration:** `V27__add_worker_and_notification_indexes.sql`

## Context

The frozen schema declares exactly two indexes on `notification` and exactly five on
`background_job`, all single-column:

```text
notification:    idx_notification_user_id
                 idx_notification_notification_status

background_job:  idx_background_job_job_type
                 idx_background_job_initiated_by
                 idx_background_job_status
                 idx_background_job_queued_on
                 idx_background_job_reference
```

Two of the platform's highest-frequency read paths filter on **two** columns and order by a
**third**. A single-column index can serve only the first predicate; PostgreSQL must then fetch
every matching row, filter the rest heap-side, and sort the survivors.

**The worker candidate selection.** Every worker polls the same repository method
(`BackgroundJobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc`, called by
`EvaluationWorker`, `AnalyticsWorker`, `ReportWorker`, `EmailDeliveryWorker`,
`NotificationRequestWorker`, and `AnnouncementFanoutWorker`):

```sql
SELECT ...
  FROM background_job
 WHERE job_type = ? AND status = ?
 ORDER BY queued_on ASC
 LIMIT ?;
```

`idx_background_job_job_type` narrows to one job type. That type accumulates `COMPLETED` and
`FAILED` rows forever, so the index yields a set dominated by terminal rows that the heap filter
must discard, and the surviving handful is then sorted. The cost of a poll therefore grows with the
total number of jobs ever run for that type, not with the number of jobs currently queued — which is
the wrong shape for a query that runs on a fixed interval per worker.

**The notification list.** A student's notification centre
(`NotificationRepository.findByUserIdAndNotificationStatusOrderByCreatedOnDesc`):

```sql
SELECT ...
  FROM notification
 WHERE user_id = ? AND notification_status = ?
 ORDER BY created_on DESC
 LIMIT ?;
```

`idx_notification_user_id` narrows to one user, but a long-lived account accumulates mostly-`READ`
rows, so the `UNREAD` page pays a heap filter over the account's entire history and then a sort.
`markAllRead` and `countByUserIdAndNotificationStatus` use the same two-column predicate and benefit
from the same index.

**The claim itself is not the index's job.** GAP-139's original wording said "the worker claim … is
served by single-column indexes". That is inaccurate and worth correcting in the record: the claim
is a primary-key update,

```sql
UPDATE background_job
   SET status = 'RUNNING', started_on = now(), attempt_count = … 
 WHERE id = :id AND status = 'QUEUED';
```

which uses `pk_background_job` and already touches exactly one row. The composite index improves the
*candidate selection* that precedes the claim, i.e. which ids are handed to it — not the update.

## Decision

Add two composite indexes as an amendment to the frozen index lists:

```sql
CREATE INDEX idx_background_job_type_status_queued
    ON background_job (job_type, status, queued_on);

CREATE INDEX idx_notification_user_status_created
    ON notification (user_id, notification_status, created_on);
```

**Column order.** Equality predicates first, then the ordering column — `(job_type, status,
queued_on)` and `(user_id, notification_status, created_on)`. Both leading pairs are compared with
`=`, so they form an exact index prefix for the filter; the trailing column then arrives pre-sorted
within that prefix, which removes the sort step entirely.

**Direction.** `created_on` is declared `ASC` even though the notification query orders `DESC`.
PostgreSQL scans a B-tree backwards at no cost, so one index serves both directions; declaring
`DESC` would only constrain the planner without adding a capability. The worker query's
`ORDER BY queued_on ASC` matches the declared direction directly.

**Nothing is removed.** The five and two original indexes stay. They still serve predicates the
composites cannot: `countByStatus` and the admin health view (`idx_background_job_status`),
`findByJobTypeOrderByQueuedOnDesc` and report history (`idx_background_job_job_type`), the
idempotency lookups on `(reference_type, reference_id)`, the unfiltered notification page
(`idx_notification_user_id` with no status predicate), and the FK checks behind
`fk_notification_user` (`idx_notification_user_id`). Dropping any of them to "make room" would trade
one plan for another.

## Write overhead

Every `INSERT`, `UPDATE` of an indexed column, and `DELETE` on these two tables now maintains one
additional B-tree. Concretely:

- **`background_job`** — one row per asynchronous unit of work (evaluation handoff, report
  generation, email delivery, announcement fan-out, notification requests). The row is inserted
  once, updated a small bounded number of times (claim, then completion or failure, plus at most a
  few retries), and never deleted. Write volume is proportional to **jobs submitted**, which is
  low-frequency and operator- or workflow-driven, not request-driven.
- **`notification`** — one row per notification, written in batches
  (`NotificationServiceImpl` uses `saveAll`). Also low-frequency: a notification is created when
  something noteworthy happens to a user, not per page view. The one high-frequency write is
  `markAllRead`, the bulk `UPDATE … WHERE user_id = ? AND notification_status = ?`, which touches
  the same column pair as the index and therefore rewrites those index entries. That statement is
  already proportional to the caller's unread count and is issued deliberately, not per request.

Both tables are append-dominant with narrow, bounded update traffic, so the added cost is a small
constant per write against a query-side benefit that grows with table history. The trade is
favourable because the queries it improves run on a fixed timer per worker regardless of load, while
the writes scale with business events.

The indexes are also materially narrower than the rows they index, so they stay cache-resident far
better than the heap.

**The worker index is insurance, not a routine speed-up.** Measurement (see
`docs/testing/p12/worker-index-plans.md`) showed the worker query already runs in ~0.1 ms against a
shallow queue, because `idx_background_job_queued_on` finds a match almost immediately. The composite
index only wins when the queue is deep and shared between job types, and then it wins by two orders
of magnitude. That is the situation worth insuring against: a backlog is exactly when a slow poll
compounds itself, and it is the state in which the platform is least able to absorb the cost. The
notification index, by contrast, is a straightforward win at any realistic account age.

## Rollback

The change is fully reversible and loses no data: it adds two indexes and touches no column,
constraint, or row.

```sql
DROP INDEX IF EXISTS idx_background_job_type_status_queued;
DROP INDEX IF EXISTS idx_notification_user_status_created;
```

Undoing it through Flyway requires a forward migration (the project does not use Flyway's undo
mode); a `V28__drop_worker_and_notification_indexes.sql` containing the two statements above is the
supported path, and it returns both tables to exactly the frozen index lists of §33.1 and §35.1.
Because dropping an index is instantaneous in metadata terms and reclaims its storage immediately,
rollback after a measured regression is a low-risk operation. `DROP INDEX` takes an
`ACCESS EXCLUSIVE` lock on the table briefly; on a large production table the same
`CONCURRENTLY` caveat described below applies.

## Production build note

`V27` creates both indexes **without** `CONCURRENTLY`, because Flyway executes each migration inside
a transaction and `CREATE INDEX CONCURRENTLY` cannot run inside one. That is correct at this stage:
both tables are small, and the brief write lock is immaterial. When these indexes are introduced to
a production database that already holds substantial history, they should instead be built
out-of-band with `CONCURRENTLY` and recorded in Flyway as an already-applied migration
(`baseline`/`skip`), so that the write blocking of a normal `CREATE INDEX` is avoided. This is a
deployment-procedure choice, not a change to the schema the migration produces.

**This is now measured, not assumed (2026-10-23).** The GAP-133 rehearsal inflated the two tables to
4.4M (`background_job`, 420 MB heap) and 5M (`notification`, 802 MB heap) rows and compared the two
build methods with a concurrent writer:

| Build method | Build duration | Concurrent writer blocked |
|---|---|---|
| `CREATE INDEX` (what V27 does) | ~10.2 s | **9,749 ms** |
| `CREATE INDEX CONCURRENTLY` | ~12.6 s | **104 ms** |

So deploying V27 to a table of this size through a plain `flyway migrate` stops writes to
`background_job` for roughly ten seconds, and to `notification` for a similar period. The
`CONCURRENTLY` build costs about 2 s more in wall time and blocks for about a tenth of a second. That
ratio is the whole argument for the out-of-band procedure on a populated production database: the
plain form is fine for an empty or small table, and the migration is safe to ship as written, but the
production runbook must **not** simply `migrate` it at volume. Evidence:
`docs/testing/p12/release-candidate.md` §3.

## Consequences

- The notification list becomes an index-ordered backward scan: **~11× faster with the sort step
  eliminated**, unconditionally, because the predicate and ordering match the index exactly.
- Worker candidate selection becomes a two-predicate index scan instead of filter-then-sort:
  **two orders of magnitude faster when the queue is deep** — measured 0.084 ms against 21.337 ms
  on the recorded run (~254×), and 0.094 ms against 11.514 ms on an earlier one (~122×), with
  1,576 → 4 buffers and ~45,000 rows discarded → 0. The ratio varies with cache state; the plan
  shape, the buffer ratio and the eliminated filter do not. This benefit is **conditional on a backlog**, and the ADR records that honestly
  rather than claiming a blanket speed-up: with a shallow queue PostgreSQL correctly prefers
  `idx_background_job_status` and the composite index buys nothing. See
  `docs/testing/p12/worker-index-plans.md` for the measured plans, including the two seed shapes
  that produced misleading answers.
- Two additional B-trees to maintain on low-frequency write paths.
- The frozen index lists in `specs/04-Database-Schema-V1.5.md` §33.1 and §35.1 are superseded by
  this ADR; a future reader comparing the spec to the migrations will find two extra indexes here
  and should treat this record as the authority for them.
- `docs/known-gaps.md` GAP-139 closes; its original "worker claim" phrasing is corrected in that row.

## Verification

`backend/src/test/java/com/company/exam/integration/performance/WorkerIndexPlanIntegrationTest.java`
proves three things against a real PostgreSQL instance (embedded, per ADR-0009):

1. both indexes exist with exactly the declared column order, and all seven frozen single-column
   indexes are retained;
2. with a realistic dataset — deep history plus a multi-type queued backlog, followed by
   `VACUUM (ANALYZE)` — `EXPLAIN (ANALYZE, BUFFERS)` for both target queries chooses the new index,
   reports no sequential scan, and shows no residual `Sort` node;
3. both `DROP INDEX` statements are valid and leave exactly the frozen index list behind. The test
   drops the indexes, asserts the frozen seven remain, and re-creates them in a `finally` block, so a
   failure still restores the schema the migration produced. It is deliberately not `@Transactional`:
   the class seeds with `VACUUM`, which PostgreSQL refuses to run inside a transaction.

The plans captured from that measurement are recorded in `docs/testing/p12/worker-index-plans.md`.
`WorkerIndexPlanCaptorTest` regenerates them on demand.
