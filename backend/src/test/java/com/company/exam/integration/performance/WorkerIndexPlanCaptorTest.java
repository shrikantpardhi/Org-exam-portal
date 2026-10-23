package com.company.exam.integration.performance;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Emits the real query plans behind ADR-0028 so the evidence file records what PostgreSQL actually
 * did rather than a hand-written approximation.
 *
 * <p>Prints only; asserts nothing. Kept separate from {@link WorkerIndexPlanIntegrationTest} so the
 * gate stays fast and the evidence can be regenerated on demand.
 *
 * <p>Three scenarios are captured for the worker query, because the honest answer depends on the
 * queue's shape and the first two attempts to measure it produced misleading results:
 *
 * <ol>
 *   <li><b>deep backlog, several types</b> — the situation the index exists for;</li>
 *   <li><b>shallow queue</b> — where the planner correctly prefers the existing
 *       {@code idx_background_job_status}, and the composite index buys nothing;</li>
 *   <li><b>uniformly interleaved</b> — a seed shape that <em>flatters</em> the composite index and
 *       must not be used as evidence.</li>
 * </ol>
 */
class WorkerIndexPlanCaptorTest extends AbstractIntegrationTest {

    private static final int HISTORY = 20_000;
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void capturePlans() {
        try {
            captureNotificationPlans();
            captureWorkerPlans();
        } finally {
            // These tables are shared with the rest of the suite, and ReportWorker claims any queued
            // REPORT_GENERATION row it finds. Leaving seeded rows behind fails unrelated tests.
            jdbcTemplate.update("delete from background_job");
            jdbcTemplate.update("delete from notification");
        }
    }

    private void captureNotificationPlans() {
        // A long-lived account: the overwhelming majority of its notifications are already READ, and
        // the UNREAD page wants the newest ones.
        jdbcTemplate.update("""
                insert into notification (id, user_id, title, message, notification_status, created_on)
                select gen_random_uuid(), ?, 'seeded ' || g, 'seed',
                       case when g % 20 = 0 then 'UNREAD' else 'READ' end,
                       now() - (g || ' seconds')::interval
                  from generate_series(1, ?) as g
                """, USER, HISTORY);
        jdbcTemplate.execute("vacuum (analyze) notification");

        String notifications = """
                select * from notification
                 where user_id = '00000000-0000-0000-0000-000000000001'
                   and notification_status = 'UNREAD'
                 order by created_on desc limit 20""";

        emit("NOTIFICATION LIST — WITH the composite index (V27 applied)", notifications);

        jdbcTemplate.execute("drop index idx_notification_user_status_created");
        jdbcTemplate.execute("vacuum (analyze) notification");
        emit("NOTIFICATION LIST — WITHOUT the composite index (pre-amendment baseline)", notifications);
        jdbcTemplate.execute(
                "create index idx_notification_user_status_created on notification (user_id, notification_status, created_on)");
    }

    private void captureWorkerPlans() {
        String worker = """
                select * from background_job
                 where job_type = 'EVALUATION' and status = 'QUEUED'
                 order by queued_on asc limit 10""";

        // ---- scenario 1: deep backlog across several types -------------------- the target case
        jdbcTemplate.update("delete from background_job");
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on, started_on, completed_on)
                select gen_random_uuid(), 'EVALUATION', 'COMPLETED',
                       now() - (g || ' minutes')::interval,
                       now() - (g || ' minutes')::interval, now()
                  from generate_series(1, ?) as g
                """, HISTORY);
        // Five other job types hold the bulk of a deep queued backlog, and they are strictly older
        // than EVALUATION's backlog: a real queue's outstanding rows are the newest, and everything
        // ahead of them in queued_on order belongs to someone else.
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on)
                select gen_random_uuid(),
                       (array['EMAIL_DELIVERY','REPORT_GENERATION','ANNOUNCEMENT_FANOUT',
                              'NOTIFICATION_REQUEST','ANALYTICS_REFRESH'])[1 + (g % 5)],
                       'QUEUED',
                       now() - ((10000 + g) || ' seconds')::interval
                  from generate_series(1, 25000) as g
                """);
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on)
                select gen_random_uuid(), 'EVALUATION', 'QUEUED',
                       now() - (g || ' seconds')::interval
                  from generate_series(1, 5000) as g
                """);
        jdbcTemplate.execute("vacuum (analyze) background_job");

        emit("WORKER CANDIDATE — deep multi-type backlog, WITH the composite index", worker);

        jdbcTemplate.execute("drop index idx_background_job_type_status_queued");
        jdbcTemplate.execute("vacuum (analyze) background_job");
        emit("WORKER CANDIDATE — deep multi-type backlog, WITHOUT the composite index", worker);
        jdbcTemplate.execute(
                "create index idx_background_job_type_status_queued on background_job (job_type, status, queued_on)");
        jdbcTemplate.execute("vacuum (analyze) background_job");

        // ---- scenario 2: shallow queue ------------------------------------- where the index is not needed
        jdbcTemplate.update("delete from background_job");
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on, started_on, completed_on)
                select gen_random_uuid(), 'EVALUATION', 'COMPLETED',
                       now() - (g || ' minutes')::interval,
                       now() - (g || ' minutes')::interval, now()
                  from generate_series(1, ?) as g
                """, HISTORY);
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on)
                select gen_random_uuid(), 'EVALUATION', 'QUEUED', now() - (g || ' seconds')::interval
                  from generate_series(1, 100) as g
                """);
        jdbcTemplate.execute("vacuum (analyze) background_job");
        emit("WORKER CANDIDATE — shallow queue (100 queued), WITH the composite index", worker);

        // ---- scenario 3: uniformly interleaved ----------------------------- NOT valid evidence
        jdbcTemplate.update("delete from background_job");
        jdbcTemplate.update("""
                insert into background_job (id, job_type, status, queued_on, started_on, completed_on)
                select gen_random_uuid(), 'EVALUATION',
                       case when g % 20 = 0 then 'QUEUED' else 'COMPLETED' end,
                       now() - (g || ' seconds')::interval, null, now()
                  from generate_series(1, ?) as g
                """, HISTORY);
        jdbcTemplate.execute("vacuum (analyze) background_job");
        emit("WORKER CANDIDATE — uniformly interleaved seed (NOT evidence; flatters the index)", worker);

        jdbcTemplate.update("delete from background_job");
    }

    private void emit(String title, String sql) {
        System.out.println("\n===== " + title + " =====");
        List<String> lines = jdbcTemplate.queryForList("explain (analyze, buffers) " + sql, String.class);
        lines.forEach(System.out::println);
        System.out.println("===== end =====");
    }
}
