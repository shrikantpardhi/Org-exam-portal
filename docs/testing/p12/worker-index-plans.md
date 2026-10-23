# Query-plan evidence — ADR-0028 composite indexes (GAP-139)

- **Date:** 2026-10-23
- **Database:** embedded PostgreSQL (ADR-0009), the same engine the integration suite runs against
- **Migration:** `V27__add_worker_and_notification_indexes.sql`
- **Reproduce:** `cd backend && ./mvnw -o surefire:test -Dtest=WorkerIndexPlanCaptorTest`
  (prints the plans; asserts nothing). The gate that enforces them is
  `WorkerIndexPlanIntegrationTest`.

Captured with `EXPLAIN (ANALYZE, BUFFERS)`. Timings are single-run on one Windows development host
and are **not** a capacity claim (P7 §12, P12 §56).

---

## 1. Notification list — clear win

Query (`NotificationRepository.findByUserIdAndNotificationStatusOrderByCreatedOnDesc`):

```sql
SELECT * FROM notification
 WHERE user_id = '…' AND notification_status = 'UNREAD'
 ORDER BY created_on DESC LIMIT 20;
```

Dataset: 20,000 notifications for one long-lived account, 5% UNREAD — mostly-READ history, which is
the shape a real student's notification centre reaches.

**With `idx_notification_user_status_created`:**

```text
Limit  (cost=0.41..9.84 rows=20) (actual time=0.036..0.054 rows=20 loops=1)
  Buffers: shared hit=8
  ->  Index Scan Backward using idx_notification_user_status_created on notification
        Index Cond: ((user_id = '…'::uuid) AND ((notification_status)::text = 'UNREAD'::text))
Execution Time: 0.088 ms
```

**Without it (pre-amendment baseline):**

```text
Limit  (cost=228.00..228.05 rows=20) (actual time=0.950..0.958 rows=20 loops=1)
  Buffers: shared hit=230
  ->  Sort  (Sort Key: created_on DESC)
        Sort Method: top-N heapsort  Memory: 27kB
        ->  Index Scan using idx_notification_notification_status on notification
              Index Cond: ((notification_status)::text = 'UNREAD'::text)
              Filter: (user_id = '…'::uuid)
Execution Time: 0.994 ms
```

| | With | Without |
|---|---|---|
| Plan | backward index scan, no sort | index scan + filter + top-N heapsort |
| Buffers | 8 | 230 |
| Execution | 0.088 ms | 0.994 ms |

**~11× faster, 29× fewer buffers**, and the sort step disappears entirely. The baseline is the
pathology ADR-0028 describes: `idx_notification_notification_status` selects all 1,000 UNREAD rows
across *every* user, then filters to one user and sorts. Its cost scales with total unread volume
platform-wide, not with one student's page.

---

## 2. Worker candidate selection — a win only under a deep multi-type backlog

Query (`BackgroundJobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc`):

```sql
SELECT * FROM background_job
 WHERE job_type = 'EVALUATION' AND status = 'QUEUED'
 ORDER BY queued_on ASC LIMIT 10;
```

### Honest framing first

This index is **conditional**, unlike the notification one. Two attempts to measure it are recorded
below because the first two produced answers that were wrong in opposite directions, and the
difference matters more than the headline number.

**Attempt 1 — `QUEUED` interleaved uniformly through history.**
A `QUEUED` row every 20th row, ascending by `queued_on`. Plan: composite index, 0.095 ms — versus
0.099 ms without it. Essentially **no improvement**: an ascending scan of `idx_background_job_queued_on`
meets a matching row after ~20 rows, so the filter never does real work. The composite index looked
chosen, but the baseline was already fast. This seed is meaningless.

**Attempt 2 — deep history, small recent backlog (100 QUEUED).**
The composite index was **not chosen at all**:

```text
Limit  (cost=16.45..16.47 rows=10) (actual time=0.178..0.182 rows=10 loops=1)
  Buffers: shared hit=4
  ->  Sort  (Sort Key: queued_on)
        Sort Method: top-N heapsort  Memory: 27kB
        ->  Index Scan using idx_background_job_status on background_job
              Index Cond: ((status)::text = 'QUEUED'::text)
              Filter: ((job_type)::text = 'EVALUATION'::text)
Execution Time: 0.213 ms
```

PostgreSQL correctly prefers `idx_background_job_status`: with only 100 queued rows the whole queue
is cheaper to read and sort than to navigate a deeper index. **The two worker-plan assertions in the
gate fail on this shape** (the composite index is not used, and a `Sort Key: queued_on` reappears),
while the existence and notification assertions still pass — which is exactly why the gate now seeds
the shape below. A shallow queue is a real state, and in it the composite index buys nothing, only
write cost.

### The case the index is for: deep backlog across several job types

Dataset: 20,000 COMPLETED history rows plus a 30,000-row queued backlog spread over six job types,
of which `EVALUATION` holds 5,000. This is a backlog that has built up — several workers behind, a
fan-out burst, or a provider outage.

**With `idx_background_job_type_status_queued`:**

```text
Limit  (cost=0.41..2.60 rows=10) (actual time=0.054..0.059 rows=10 loops=1)
  Buffers: shared hit=4
  ->  Index Scan using idx_background_job_type_status_queued on background_job
        Index Cond: (((job_type)::text = 'EVALUATION'::text) AND ((status)::text = 'QUEUED'::text))
Execution Time: 0.084 ms
```

**Without it:**

```text
Limit  (cost=0.29..2.76 rows=10) (actual time=21.280..21.289 rows=10 loops=1)
  Buffers: shared hit=1576
  ->  Index Scan using idx_background_job_queued_on on background_job
        Filter: (((job_type)::text = 'EVALUATION'::text) AND ((status)::text = 'QUEUED'::text))
        Rows Removed by Filter: 44917
Execution Time: 21.337 ms
```

| | With | Without |
|---|---|---|
| Plan | index scan, both predicates in the `Index Cond` | index scan + filter over ~45,000 rows |
| Rows discarded by filter | 0 | 44,917 |
| Buffers | 4 | 1,576 |
| Execution | 0.084 ms | 21.337 ms |

**Two orders of magnitude: 254× faster on this run, 394× fewer buffers.** Absolute timings vary
between runs with cache state — an earlier capture of this same shape measured 0.094 ms against
11.514 ms, i.e. ~122× — so the range is ~120–250× rather than a single figure. The parts that do
not vary are the plan shape (two-predicate `Index Cond`, no `Sort`), the buffer ratio (single-digit
versus four figures) and the number of rows discarded by the filter, which grows with the backlog.

Every worker polls on a timer, so the baseline cost is multiplied by the worker count and paid every
interval: at the configured 30 s interval across six workers, the undiscarded baseline would burn
seconds per cycle doing nothing but rejecting non-matching rows once a backlog exists. That is the
cost the amendment removes.

**The decisive column is `queued_on`, not the second key.** Without the trailing `queued_on` the
plan would carry `Sort Key: queued_on`; the captured plan has no sort node, confirming the
`ORDER BY` is satisfied by the index order itself.

---

## 3. Summary, stated without overclaiming

| Query | Backlog state | Index used | Effect |
|---|---|---|---|
| Notification list | long-lived account, mostly READ | `idx_notification_user_status_created` | **~11× faster; sort eliminated** — unconditional |
| Worker candidates | deep multi-type backlog | `idx_background_job_type_status_queued` | **~120–250× faster** |
| Worker candidates | shallow queue (~100) | `idx_background_job_status` (planner's choice) | no benefit; write cost only |
| Worker candidates | uniformly interleaved seed | composite index | no benefit vs already-fast baseline (invalid evidence) |

So: the notification index is a clear, unconditional improvement. The worker index is a **backlog
insurance policy** — it costs one B-tree on a low-frequency write path and pays for itself the first
time the queue is deep, which is precisely when the platform is under stress and when a slow poll
compounds the backlog. The ADR states this rather than claiming a blanket speed-up.

Note also that the claim update itself (`UPDATE … WHERE id = :id AND status = 'QUEUED'`) uses
`pk_background_job` and is unaffected by either index; what improves is the candidate selection that
precedes it.

---

## 4. Rollback

Verified as valid SQL in `WorkerIndexPlanIntegrationTest#theDowngradeRestoresTheFrozenIndexList`,
which drops the indexes, asserts that all seven frozen single-column indexes remain, and re-creates
both indexes in a `finally` block:

```sql
DROP INDEX IF EXISTS idx_background_job_type_status_queued;
DROP INDEX IF EXISTS idx_notification_user_status_created;
```

After the drop, both tables match `specs/04-Database-Schema-V1.5.md` §33.1 and §35.1 exactly, and the
re-create restores the amended state.
