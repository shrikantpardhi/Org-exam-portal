package com.company.exam.common.job;

/**
 * Shared background job type identifiers on the frozen {@code background_job} table
 * ({@code specs/04-Database-Schema-V1.5.md §35.1}).
 *
 * <p>Producers and consumers in different modules agree on these values without depending on each
 * other; the durable job row is the shared infrastructure contract.
 */
public final class JobTypes {

    /** P5 snapshot preparation for an assessment schedule; reference type {@code ASSESSMENT_SCHEDULE}. */
    public static final String ATTEMPT_PREPARATION = "ATTEMPT_PREPARATION";

    /** P5 evaluation handoff after submission; reference type {@code STUDENT_ATTEMPT}. */
    public static final String EVALUATION = "EVALUATION";

    /** P8 incremental analytics refresh; reference type {@code STUDENT_ATTEMPT}. */
    public static final String ANALYTICS_REFRESH = "ANALYTICS_REFRESH";

    /** P8 full analytics rebuild. */
    public static final String ANALYTICS_REBUILD = "ANALYTICS_REBUILD";

    /** P10 durable notification request produced from a business event after commit. */
    public static final String NOTIFICATION_REQUEST = "NOTIFICATION_REQUEST";

    /** P10 asynchronous email delivery; retried with bounded backoff. */
    public static final String EMAIL_DELIVERY = "EMAIL_DELIVERY";

    /** P10 asynchronous announcement fan-out. */
    public static final String ANNOUNCEMENT_FANOUT = "ANNOUNCEMENT_FANOUT";

    /**
     * P13 report generation. Report history is served from these rows, so no reporting table is
     * created ({@code specs/22-P13-Reporting-V1.5.md §25, §30}).
     */
    public static final String REPORT_GENERATION = "REPORT_GENERATION";

    private JobTypes() {
    }

    /** Every registered job type, for metrics and reporting over the fixed catalog. */
    public static java.util.List<String> all() {
        return java.util.List.of(ATTEMPT_PREPARATION, EVALUATION, ANALYTICS_REFRESH, ANALYTICS_REBUILD,
                NOTIFICATION_REQUEST, EMAIL_DELIVERY, ANNOUNCEMENT_FANOUT, REPORT_GENERATION);
    }
}
