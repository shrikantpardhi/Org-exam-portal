package com.company.exam.reporting.catalog;

import java.util.List;
import java.util.Optional;

/**
 * The report catalog registry ({@code specs/22-P13-Reporting-V1.5.md §11, §12, §107}).
 *
 * <p>Each entry defines reportType, category, display name, description, supported formats,
 * permission, filter schema, and execution mode. The registry is an application-level catalog — it is
 * <strong>not</strong> a database entity and requires no new table (§8, §12).
 *
 * <p>A report type maps to a controlled query implementation. There is no endpoint that accepts
 * caller-supplied SQL or a caller-supplied table name (§106).
 *
 * <p>A catalog entry marked {@code enabled=false} is a reserved identifier whose projection provider
 * is not implemented yet; requesting it fails with a controlled error rather than silently
 * returning an empty artifact.
 */
public enum ReportType {

    // ---------------------------------------------------------------- Student
    STUDENT_PROFILE("STUDENT_PROFILE", ReportCategory.STUDENT, "Student Profile",
            "Identity, status and enrolment summary for a student.", "REPORT_STUDENT_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false,
            List.of("studentId")),
    STUDENT_ATTEMPTS("STUDENT_ATTEMPTS", ReportCategory.STUDENT, "Student Attempts",
            "Every attempt belonging to a student with its state and timing.", "REPORT_STUDENT_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), false,
            List.of("studentId", "from", "to")),
    STUDENT_PERFORMANCE("STUDENT_PERFORMANCE", ReportCategory.STUDENT, "Student Performance",
            "Published results with marks, rank and percentile for a student.", "REPORT_STUDENT_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), false,
            List.of("studentId", "from", "to")),
    STUDENT_TOPIC_PERFORMANCE("STUDENT_TOPIC_PERFORMANCE", ReportCategory.STUDENT,
            "Student Topic Performance", "Accuracy per topic for a student.", "REPORT_STUDENT_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false, List.of("studentId")),

    // ---------------------------------------------------------------- Assessment
    ASSESSMENT_SUMMARY("ASSESSMENT_SUMMARY", ReportCategory.ASSESSMENT, "Assessment Summary",
            "Published assessments with their question count and configuration.", "REPORT_ASSESSMENT_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), false, List.of()),
    ASSESSMENT_ATTEMPTS("ASSESSMENT_ATTEMPTS", ReportCategory.ASSESSMENT, "Assessment Attempts",
            "Attempts recorded against assessment versions in a period.", "REPORT_ASSESSMENT_READ",
            ReportExecutionMode.ASYNC_PREFERRED, List.of(ReportFormat.CSV, ReportFormat.PDF), false,
            List.of("from", "to")),
    ASSESSMENT_PERFORMANCE("ASSESSMENT_PERFORMANCE", ReportCategory.ASSESSMENT, "Assessment Performance",
            "Marks distribution and pass rate per assessment.", "REPORT_ASSESSMENT_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false, List.of("from", "to")),
    ASSESSMENT_QUESTION_ANALYSIS("ASSESSMENT_QUESTION_ANALYSIS", ReportCategory.ASSESSMENT,
            "Assessment Question Analysis", "Per-question difficulty and discrimination.",
            "REPORT_ASSESSMENT_READ", ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false,
            List.of("assessmentId")),
    ASSESSMENT_SCHEDULE_ACTIVITY("ASSESSMENT_SCHEDULE_ACTIVITY", ReportCategory.ASSESSMENT,
            "Assessment Schedule Activity", "Scheduled windows and their start activity.",
            "REPORT_ASSESSMENT_READ", ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false,
            List.of("from", "to")),

    // ---------------------------------------------------------------- Content
    CONTENT_QUESTION_INVENTORY("CONTENT_QUESTION_INVENTORY", ReportCategory.CONTENT,
            "Question Inventory", "Questions with their blueprint placement and version state.",
            "REPORT_CONTENT_READ", ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF),
            false, List.of("examId", "subjectId")),
    CONTENT_VERSION_STATUS("CONTENT_VERSION_STATUS", ReportCategory.CONTENT, "Version Status",
            "Question versions by review status.", "REPORT_CONTENT_READ", ReportExecutionMode.SYNC,
            List.of(ReportFormat.CSV), false, List.of()),
    CONTENT_TAG_COVERAGE("CONTENT_TAG_COVERAGE", ReportCategory.CONTENT, "Tag Coverage",
            "Question counts per tag, revealing under-covered topics.", "REPORT_CONTENT_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), false, List.of()),

    // ---------------------------------------------------------------- Commerce
    COMMERCE_PURCHASES("COMMERCE_PURCHASES", ReportCategory.COMMERCE, "Purchases",
            "Purchases with amount and payment state.", "REPORT_COMMERCE_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), false,
            List.of("from", "to")),
    COMMERCE_PAYMENTS("COMMERCE_PAYMENTS", ReportCategory.COMMERCE, "Payments",
            "Payment records and their normalized status.", "REPORT_COMMERCE_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false, List.of("from", "to")),
    COMMERCE_ACCESS_GRANTS("COMMERCE_ACCESS_GRANTS", ReportCategory.COMMERCE, "Access Grants",
            "Access grants with their validity window and state.", "REPORT_COMMERCE_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false, List.of("from", "to")),
    COMMERCE_REVENUE_SUMMARY("COMMERCE_REVENUE_SUMMARY", ReportCategory.COMMERCE, "Revenue Summary",
            "Collected revenue grouped by day for a period.", "REPORT_COMMERCE_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), false,
            List.of("from", "to")),

    // ---------------------------------------------------------------- Operations
    OPERATIONS_USER_ACTIVITY("OPERATIONS_USER_ACTIVITY", ReportCategory.OPERATIONS, "User Activity",
            "Users with their lifecycle state and contact scope.", "REPORT_OPERATIONS_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), true,
            List.of("query")),
    OPERATIONS_AUDIT_ACTIVITY("OPERATIONS_AUDIT_ACTIVITY", ReportCategory.OPERATIONS,
            "Audit Activity", "Audit trail entries for a period.", "REPORT_OPERATIONS_READ",
            ReportExecutionMode.ASYNC_PREFERRED, List.of(ReportFormat.CSV, ReportFormat.PDF), true,
            List.of("entityName", "action", "from", "to")),
    OPERATIONS_COMMUNICATION_STATUS("OPERATIONS_COMMUNICATION_STATUS", ReportCategory.OPERATIONS,
            "Communication Status", "Notification and delivery backlog by state.",
            "REPORT_OPERATIONS_READ", ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false,
            List.of("from", "to")),
    OPERATIONS_SYSTEM_HEALTH("OPERATIONS_SYSTEM_HEALTH", ReportCategory.OPERATIONS, "System Health",
            "Component status snapshot captured at generation time.", "REPORT_OPERATIONS_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), true, List.of()),
    OPERATIONS_REPORT_JOB_ACTIVITY("OPERATIONS_REPORT_JOB_ACTIVITY", ReportCategory.OPERATIONS,
            "Report Job Activity", "Report generation jobs and their outcomes.",
            "REPORT_OPERATIONS_READ", ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false,
            List.of("from", "to")),

    // ---------------------------------------------------------------- Executive
    EXECUTIVE_KPI_SUMMARY("EXECUTIVE_KPI_SUMMARY", ReportCategory.EXECUTIVE, "Executive KPI Summary",
            "Organization-level KPIs with their explicit definitions.", "REPORT_EXECUTIVE_READ",
            ReportExecutionMode.SYNC, List.of(ReportFormat.CSV, ReportFormat.PDF), true, List.of()),
    EXECUTIVE_ORGANIZATION_PERFORMANCE("EXECUTIVE_ORGANIZATION_PERFORMANCE", ReportCategory.EXECUTIVE,
            "Organization Performance", "Derived organization aggregates from Analytics.",
            "REPORT_EXECUTIVE_READ", ReportExecutionMode.SYNC, List.of(ReportFormat.CSV), false,
            List.of("from", "to"));

    private final String reportType;
    private final ReportCategory category;
    private final String displayName;
    private final String description;
    private final String permission;
    private final ReportExecutionMode executionMode;
    private final List<ReportFormat> supportedFormats;
    private final boolean enabled;
    private final List<String> filterSchema;

    ReportType(String reportType, ReportCategory category, String displayName, String description,
            String permission, ReportExecutionMode executionMode, List<ReportFormat> supportedFormats,
            boolean enabled, List<String> filterSchema) {
        this.reportType = reportType;
        this.category = category;
        this.displayName = displayName;
        this.description = description;
        this.permission = permission;
        this.executionMode = executionMode;
        this.supportedFormats = List.copyOf(supportedFormats);
        this.enabled = enabled;
        this.filterSchema = List.copyOf(filterSchema);
    }

    public String reportType() {
        return reportType;
    }

    public ReportCategory category() {
        return category;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return permission;
    }

    public ReportExecutionMode executionMode() {
        return executionMode;
    }

    public List<ReportFormat> supportedFormats() {
        return supportedFormats;
    }

    /** Whether this identifier is backed by an implemented projection provider (§107). */
    public boolean enabled() {
        return enabled;
    }

    /** The recognized filter keys; any other key is rejected before execution (§15, §105). */
    public List<String> filterSchema() {
        return filterSchema;
    }

    public static Optional<ReportType> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (ReportType type : values()) {
            if (type.reportType.equalsIgnoreCase(code.trim())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
