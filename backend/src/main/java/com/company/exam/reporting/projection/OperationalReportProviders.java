package com.company.exam.reporting.projection;

import static com.company.exam.reporting.projection.Projections.MAX_ROWS;
import static com.company.exam.reporting.projection.Projections.appliedFilters;
import static com.company.exam.reporting.projection.Projections.columns;
import static com.company.exam.reporting.projection.Projections.period;
import static com.company.exam.reporting.projection.Projections.row;
import static com.company.exam.reporting.projection.Projections.text;

import com.company.exam.analytics.api.ReportingAnalyticsCatalog;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.JobTypes;
import com.company.exam.identity.api.AnalyticsIdentityCatalog;
import com.company.exam.reporting.catalog.ReportType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Communication, report-job and organization-performance projections
 * ({@code specs/22-P13-Reporting-V1.5.md §48, §53, §63-§66}).
 *
 * <p>These read operational records — durable background jobs and the frozen
 * {@code organization_analytics} aggregate — through their owning contracts. No message body,
 * recipient address or error message is projected ({@code §82}).
 */
public final class OperationalReportProviders {

    private OperationalReportProviders() {
    }

    /**
     * OPERATIONS_COMMUNICATION_STATUS — notification and delivery backlog by state (§48, §63).
     *
     * <p>Communication's own contract reads per user only, so an organization-wide message list does
     * not exist. The delivery backlog, however, is already durable in the frozen {@code background_job}
     * table: every notification request, email delivery and announcement fan-out is a job row. The
     * report aggregates that authoritative backlog by job type and state, and never reads a mailbox or
     * a message body ({@code §63, §82}).
     */
    @Component
    public static class CommunicationStatusProvider implements ReportDataProvider {

        /** The Communication job types, from the shared catalog rather than re-declared. */
        private static final List<String> JOB_TYPES = List.of(JobTypes.NOTIFICATION_REQUEST,
                JobTypes.EMAIL_DELIVERY, JobTypes.ANNOUNCEMENT_FANOUT);

        private final BackgroundJobRepository jobRepository;

        public CommunicationStatusProvider(BackgroundJobRepository jobRepository) {
            this.jobRepository = jobRepository;
        }

        @Override
        public ReportType reportType() {
            return ReportType.OPERATIONS_COMMUNICATION_STATUS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            Instant from = criteria.instant("from");
            Instant to = criteria.instant("to");
            // The backlog is a count per (job type, state), not a message list.
            Map<BacklogKey, long[]> backlog = new LinkedHashMap<>();
            for (String jobType : JOB_TYPES) {
                for (BackgroundJob job : jobRepository.findByJobTypeOrderByQueuedOnDesc(jobType,
                        PageRequest.of(0, MAX_ROWS))) {
                    Instant queuedOn = job.getQueuedOn();
                    if (from != null && (queuedOn == null || queuedOn.isBefore(from))) {
                        continue;
                    }
                    if (to != null && (queuedOn == null || queuedOn.isAfter(to))) {
                        continue;
                    }
                    String status = job.getStatus() == null ? "UNKNOWN" : job.getStatus().name();
                    backlog.computeIfAbsent(new BacklogKey(jobType, status), ignored -> new long[1])[0]++;
                }
            }
            if (backlog.isEmpty()) {
                return Projections.empty(ReportType.OPERATIONS_COMMUNICATION_STATUS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            List<ReportProjection.SummaryMetric> metrics = new ArrayList<>();
            for (Map.Entry<BacklogKey, long[]> entry : backlog.entrySet()) {
                BacklogKey key = entry.getKey();
                rows.add(row("jobType", key.jobType(), "state", key.state(),
                        "notifications", entry.getValue()[0]));
                metrics.add(new ReportProjection.SummaryMetric(key.jobType() + " · " + key.state(),
                        Long.toString(entry.getValue()[0]),
                        "Communication jobs of type " + key.jobType() + " in state " + key.state()));
            }
            return new ReportProjection(ReportType.OPERATIONS_COMMUNICATION_STATUS.displayName(),
                    period(criteria.filters()), appliedFilters(criteria.filters()), criteria.generatedAt(),
                    criteria.callerId(),
                    columns("jobType", "Job type", "state", "State", "notifications", "Count"),
                    rows, metrics,
                    "Delivery backlog aggregated from the frozen background_job table; no message body or "
                            + "recipient address is read (§63, §82).");
        }

        /** A (job type, state) backlog bucket; a record keeps the grouping readable and safe. */
        private record BacklogKey(String jobType, String state) {
        }
    }

    /** OPERATIONS_REPORT_JOB_ACTIVITY — report generation jobs and their outcomes (§53). */
    @Component
    public static class ReportJobActivityProvider implements ReportDataProvider {

        private final BackgroundJobRepository jobRepository;

        public ReportJobActivityProvider(BackgroundJobRepository jobRepository) {
            this.jobRepository = jobRepository;
        }

        @Override
        public ReportType reportType() {
            return ReportType.OPERATIONS_REPORT_JOB_ACTIVITY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<BackgroundJob> jobs = jobRepository.findByJobTypeOrderByQueuedOnDesc(JobTypes.REPORT_GENERATION,
                    PageRequest.of(0, MAX_ROWS));
            Instant from = criteria.instant("from");
            Instant to = criteria.instant("to");
            List<Map<String, String>> rows = new ArrayList<>();
            Map<String, Long> byStatus = new LinkedHashMap<>();
            for (BackgroundJob job : jobs) {
                Instant queuedOn = job.getQueuedOn();
                if (from != null && (queuedOn == null || queuedOn.isBefore(from))) {
                    continue;
                }
                if (to != null && (queuedOn == null || queuedOn.isAfter(to))) {
                    continue;
                }
                String status = job.getStatus() == null ? "UNKNOWN" : job.getStatus().name();
                byStatus.merge(status, 1L, Long::sum);
                rows.add(row(
                        "jobId", text(job.getId()),
                        "status", status,
                        "queuedOn", text(queuedOn),
                        "startedOn", text(job.getStartedOn()),
                        "completedOn", text(job.getCompletedOn()),
                        "attemptCount", job.getAttemptCount(),
                        // The failure code is stable operational metadata; the message may echo
                        // internals, so it is deliberately not projected (§82).
                        "errorCode", job.getErrorCode()));
            }
            List<ReportProjection.SummaryMetric> metrics = new ArrayList<>();
            for (Map.Entry<String, Long> entry : byStatus.entrySet()) {
                metrics.add(new ReportProjection.SummaryMetric("Jobs · " + entry.getKey(),
                        Long.toString(entry.getValue()),
                        "Report generation jobs whose state is " + entry.getKey()));
            }
            return new ReportProjection(ReportType.OPERATIONS_REPORT_JOB_ACTIVITY.displayName(),
                    period(criteria.filters()), appliedFilters(criteria.filters()), criteria.generatedAt(),
                    criteria.callerId(),
                    columns("jobId", "Job id", "status", "State", "queuedOn", "Queued", "startedOn", "Started",
                            "completedOn", "Completed", "attemptCount", "Attempts", "errorCode", "Error code"),
                    rows, metrics,
                    "Report-generation job lifecycle. Error messages are omitted to avoid leaking internals; "
                            + "only the stable error code is shown (§82).");
        }
    }

    /** EXECUTIVE_ORGANIZATION_PERFORMANCE — organization aggregates from Analytics (§64, §73). */
    @Component
    public static class OrganizationPerformanceProvider implements ReportDataProvider {

        private final ReportingAnalyticsCatalog analyticsCatalog;
        private final AnalyticsIdentityCatalog identityCatalog;

        public OrganizationPerformanceProvider(ReportingAnalyticsCatalog analyticsCatalog,
                AnalyticsIdentityCatalog identityCatalog) {
            this.analyticsCatalog = analyticsCatalog;
            this.identityCatalog = identityCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.EXECUTIVE_ORGANIZATION_PERFORMANCE;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            // §73: the deployment's own organization scope bounds the read; organizations are never
            // enumerated from an unbounded table.
            List<java.util.UUID> organizationIds = identityCatalog.organizationIds();
            List<ReportingAnalyticsCatalog.OrganizationAnalyticsRow> organizations =
                    analyticsCatalog.organizations(organizationIds);
            if (organizations.isEmpty()) {
                return Projections.empty(ReportType.EXECUTIVE_ORGANIZATION_PERFORMANCE.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            List<ReportProjection.SummaryMetric> metrics = new ArrayList<>();
            for (ReportingAnalyticsCatalog.OrganizationAnalyticsRow organization : organizations) {
                String scope = text(organization.organizationId());
                rows.add(row(
                        "organizationId", text(organization.organizationId()),
                        "totalStudents", organization.totalStudents(),
                        "totalAssessments", organization.totalAssessments(),
                        "totalAttempts", organization.totalAttempts(),
                        "activeUsers", organization.activeUsers(),
                        "lastCalculatedOn", text(organization.lastCalculatedOn())));
                // §64 requires every executive measure to carry its explicit definition.
                metrics.add(new ReportProjection.SummaryMetric("Students · " + scope,
                        String.valueOf(organization.totalStudents()), "organization_analytics.total_students"));
                metrics.add(new ReportProjection.SummaryMetric("Active users · " + scope,
                        String.valueOf(organization.activeUsers()), "organization_analytics.active_users"));
            }
            return new ReportProjection(ReportType.EXECUTIVE_ORGANIZATION_PERFORMANCE.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("organizationId", "Organization", "totalStudents", "Students",
                            "totalAssessments", "Assessments", "totalAttempts", "Attempts",
                            "activeUsers", "Active users", "lastCalculatedOn", "Last calculated"),
                    rows, metrics,
                    "Derived from the frozen organization_analytics aggregate for the deployment's own "
                            + "organization scope (§64, §73, §91).");
        }
    }
}
