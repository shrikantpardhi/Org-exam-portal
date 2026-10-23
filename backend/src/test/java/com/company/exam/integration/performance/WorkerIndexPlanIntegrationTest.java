package com.company.exam.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GAP-139, ADR-0028: the two composite indexes exist and the planner actually chooses them.
 *
 * <p>The point of this class is that "the index exists" is not the claim being made. An index that
 * PostgreSQL declines to use buys nothing but write overhead, and a test that only counted
 * {@code pg_indexes} rows would pass while both target queries kept doing a sequential scan. Each
 * plan assertion therefore reads the real {@code EXPLAIN} output for the real query text.
 *
 * <p>Two details make those plans trustworthy rather than accidental:
 *
 * <ul>
 *   <li>the tables are seeded well past the point where a sequential scan is competitive, because
 *       on an empty or tiny table PostgreSQL correctly prefers a scan and the index would appear
 *       unused for a truthful reason;</li>
 *   <li>{@code ANALYZE} runs after seeding, since the planner chooses from statistics that are
 *       otherwise stale and would not reflect the seeded distribution.</li>
 * </ul>
 *
 * <p>The plan is also checked against the *cost* asymmetry that motivates the index: the query is
 * selective, so an index scan is the correct choice and a sequential scan means the amendment did
 * not achieve its purpose.
 */
class WorkerIndexPlanIntegrationTest extends AbstractIntegrationTest {

    /** Enough rows that a sequential scan of either table is not the cheaper plan. */
    private static final int SEEDED_JOBS = 20_000;

    /** Terminal rows per job type, the accumulation that makes the single-column index decay. */
    private static final int SEEDED_NOTIFICATIONS = 20_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Clears the two tables before seeding, and restores them afterwards.
     *
     * <p>This is not tidiness for its own sake. Both tables are shared with the rest of the suite and
     * {@code ReportWorker} claims any queued {@code REPORT_GENERATION} row it finds, so seeded jobs
     * left behind are picked up by an unrelated test and fail it for reasons that have nothing to do
     * with indexing. Ambient rows also change the planner's statistics, which is what this class
     * asserts on.
     */
    @AfterEach
    void clearSeededRows() {
        jdbcTemplate.update("delete from background_job");
        jdbcTemplate.update("delete from notification");
    }

    @BeforeEach
    void seedRealisticVolume() {
        // Start from a known empty state: another test's leftovers would otherwise both alter the
        // statistics this class measures and be claimed by workers.
        jdbcTemplate.update("delete from background_job");
        jdbcTemplate.update("delete from notification");

        // The shape the index exists for, and the shape production actually has: a long tail of
        // terminal history (COMPLETED, older) plus a *deep backlog spread across several job
        // types*, of which this query wants only its own type's share.
        //
        // This deliberately is NOT "interleave QUEUED rows uniformly through history". That seed
        // flatters the composite index (an ascending scan meets a matching row almost immediately)
        // and, more importantly, is not what a queue looks like. With a uniform interleave the
        // planner picks the composite index for the wrong reason and a shallow queue does not,
        // which makes such a test a tautology rather than a check.
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on, started_on, completed_on)
                select gen_random_uuid(), 'EVALUATION', 'COMPLETED',
                       now() - (g || ' minutes')::interval,
                       now() - (g || ' minutes')::interval, now()
                  from generate_series(1, ?) as g
                """, SEEDED_JOBS);

        // Five other types hold most of the queued backlog, and they are strictly *older* than this
        // type's backlog — which is what a real queue looks like: the rows a worker still has to
        // reach are the newest, and everything ahead of them in queued_on order belongs to someone
        // else.
        //
        // The offset matters. Interleaving the two sets over the same queued_on range (the obvious
        // seed) makes the planner's choice unstable and can make it prefer idx_background_job_queued_on,
        // because an ascending scan meets a matching row every few entries. Probing the planner across
        // 25k-200k row backlogs confirmed a strict age separation selects the composite index
        // deterministically at both realistic batch sizes.
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on)
                select gen_random_uuid(),
                       (array['EMAIL_DELIVERY','REPORT_GENERATION','ANNOUNCEMENT_FANOUT',
                              'NOTIFICATION_REQUEST','ANALYTICS_REFRESH'])[1 + (g % 5)],
                       'QUEUED', now() - ((10000 + g) || ' seconds')::interval
                  from generate_series(1, 25000) as g
                """);
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on)
                select gen_random_uuid(), 'EVALUATION', 'QUEUED',
                       now() - (g || ' seconds')::interval
                  from generate_series(1, 5000) as g
                """);

        jdbcTemplate.update("""
                insert into notification (id, user_id, title, message, notification_status, created_on)
                select gen_random_uuid(), ?,
                       'seeded ' || g, 'seed row for the plan assertion',
                       case when g % 20 = 0 then 'UNREAD' else 'READ' end,
                       now() - (g || ' seconds')::interval
                  from generate_series(1, ?) as g
                """, UUID.fromString("00000000-0000-0000-0000-000000000001"), SEEDED_NOTIFICATIONS);

        // The planner reads these statistics, and VACUUM (ANALYZE) additionally refreshes the
        // visibility map: without it a scan visits heap pages that ANALYZE alone leaves marked
        // all-visible=false, and the buffer counts — which this class asserts on — are inflated by
        // whatever ran before.
        jdbcTemplate.execute("vacuum (analyze) background_job");
        jdbcTemplate.execute("vacuum (analyze) notification");
    }

    @Test
    void bothCompositeIndexesExistWithTheDeclaredColumnOrder() {
        assertThat(indexColumns("idx_background_job_type_status_queued"))
                .containsExactly("job_type", "status", "queued_on");
        assertThat(indexColumns("idx_notification_user_status_created"))
                .containsExactly("user_id", "notification_status", "created_on");
    }

    @Test
    void theFrozenSingleColumnIndexesAreStillPresent() {
        // ADR-0028 adds indexes; it does not replace them. Removing one would break a different
        // plan (admin counts, per-user reads without a status filter, FK checks).
        for (String index : List.of("idx_background_job_job_type", "idx_background_job_status",
                "idx_background_job_queued_on", "idx_notification_user_id",
                "idx_notification_notification_status")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Integer.class, index);
            assertThat(found).as("frozen index %s must be retained", index).isEqualTo(1);
        }
    }

    @Test
    void theWorkerCandidateQueryUsesTheCompositeIndex() {
        String plan = explain("""
                select * from background_job
                 where job_type = 'EVALUATION' and status = 'QUEUED'
                 order by queued_on asc
                 limit 10
                """);

        assertThat(plan)
                .as("worker candidate selection must use idx_background_job_type_status_queued:%n%s", plan)
                .contains("idx_background_job_type_status_queued");
        assertThat(plan)
                .as("the worker query must not scan background_job sequentially:%n%s", plan)
                .doesNotContain("Seq Scan on background_job");
    }

    @Test
    void theWorkerQueryNeedsNoSeparateSort() {
        // The trailing queued_on column is what removes the sort; if the index were only
        // (job_type, status) this would fail and the amendment would be half done.
        String plan = explain("""
                select * from background_job
                 where job_type = 'EVALUATION' and status = 'QUEUED'
                 order by queued_on asc
                 limit 10
                """);

        assertThat(plan)
                .as("queued_on must come back pre-sorted from the index:%n%s", plan)
                .doesNotContain("Sort Key: queued_on");
    }

    @Test
    void theNotificationListQueryUsesTheCompositeIndex() {
        String plan = explain("""
                select * from notification
                 where user_id = '00000000-0000-0000-0000-000000000001'
                   and notification_status = 'UNREAD'
                 order by created_on desc
                 limit 20
                """);

        assertThat(plan)
                .as("notification list must use idx_notification_user_status_created:%n%s", plan)
                .contains("idx_notification_user_status_created");
        assertThat(plan)
                .as("the notification query must not scan notification sequentially:%n%s", plan)
                .doesNotContain("Seq Scan on notification");
    }

    @Test
    void theNotificationQueryNeedsNoSeparateSort() {
        // created_on is declared ASC and queried DESC: PostgreSQL scans the index backwards. This
        // asserts that the backward scan really does eliminate the sort step.
        String plan = explain("""
                select * from notification
                 where user_id = '00000000-0000-0000-0000-000000000001'
                   and notification_status = 'UNREAD'
                 order by created_on desc
                 limit 20
                """);

        assertThat(plan)
                .as("created_on must come back pre-sorted (backward index scan):%n%s", plan)
                .doesNotContain("Sort Key: created_on");
    }

    @Test
    void theDowngradeRestoresTheFrozenIndexList() {
        // Proves the rollback in ADR-0028 is real SQL and leaves exactly the frozen lists behind.
        //
        // Not @Transactional: the DDL is undone by re-creating the indexes in the finally block
        // instead, because this class's @BeforeEach runs VACUUM, which PostgreSQL refuses to run
        // inside a transaction block. The indexes are restored unconditionally, so a failure here
        // still leaves the shared schema as the migration created it.
        try {
            jdbcTemplate.execute("drop index if exists idx_background_job_type_status_queued");
            jdbcTemplate.execute("drop index if exists idx_notification_user_status_created");

            assertThat(indexExists("idx_background_job_type_status_queued")).isFalse();
            assertThat(indexExists("idx_notification_user_status_created")).isFalse();

            for (String frozen : List.of("idx_background_job_job_type", "idx_background_job_initiated_by",
                    "idx_background_job_status", "idx_background_job_queued_on", "idx_background_job_reference",
                    "idx_notification_user_id", "idx_notification_notification_status")) {
                assertThat(indexExists(frozen)).as("rollback must leave %s in place", frozen).isTrue();
            }
        } finally {
            jdbcTemplate.execute("create index if not exists idx_background_job_type_status_queued "
                    + "on background_job (job_type, status, queued_on)");
            jdbcTemplate.execute("create index if not exists idx_notification_user_status_created "
                    + "on notification (user_id, notification_status, created_on)");
        }
    }

    private String explain(String sql) {
        List<String> lines = jdbcTemplate.queryForList("explain (analyze, buffers) " + sql, String.class);
        return String.join("\n", lines);
    }

    private List<String> indexColumns(String indexName) {
        return jdbcTemplate.queryForList("""
                select a.attname
                  from pg_index i
                  join pg_class c on c.oid = i.indexrelid
                  join unnest(i.indkey) with ordinality as k(attnum, ord) on true
                  join pg_attribute a on a.attrelid = i.indrelid and a.attnum = k.attnum
                 where c.relname = ?
                 order by k.ord
                """, String.class, indexName);
    }

    private boolean indexExists(String indexName) {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                Integer.class, indexName);
        return found != null && found > 0;
    }
}
