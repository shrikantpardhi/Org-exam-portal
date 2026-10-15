package com.company.exam.administration.service;

import com.company.exam.administration.api.ReportingAdministrationCatalog;
import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.entity.AuditLog;
import com.company.exam.administration.repository.AuditLogRepository;
import com.company.exam.identity.api.AdminUserDirectory;
import com.company.exam.identity.api.AnalyticsIdentityCatalog;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ReportingAdministrationCatalog}
 * ({@code specs/22-P13-Reporting-V1.5.md §64-§68, §70, §71}).
 *
 * <p>Read-only. The audit query is a bounded, newest-first page so an operations report never scans
 * the whole trail, and the KPI list always carries an explicit definition per metric.
 */
@Service
public class ReportingAdministrationCatalogService implements ReportingAdministrationCatalog {

    private final AuditLogRepository auditLogRepository;
    private final SystemHealthService systemHealthService;
    private final AdminUserDirectory userDirectory;
    private final AnalyticsIdentityCatalog identityCatalog;

    public ReportingAdministrationCatalogService(AuditLogRepository auditLogRepository,
            SystemHealthService systemHealthService, AdminUserDirectory userDirectory,
            AnalyticsIdentityCatalog identityCatalog) {
        this.auditLogRepository = auditLogRepository;
        this.systemHealthService = systemHealthService;
        this.userDirectory = userDirectory;
        this.identityCatalog = identityCatalog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRow> auditRows(String entityName, String action, Instant from, Instant to, int limit) {
        return auditLogRepository
                .findAll((root, query, builder) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (entityName != null && !entityName.isBlank()) {
                        predicates.add(builder.equal(root.get("entityName"), entityName));
                    }
                    if (action != null && !action.isBlank()) {
                        predicates.add(builder.equal(root.get("action"), action));
                    }
                    if (from != null) {
                        predicates.add(builder.greaterThanOrEqualTo(root.get("createdOn"), from));
                    }
                    if (to != null) {
                        predicates.add(builder.lessThanOrEqualTo(root.get("createdOn"), to));
                    }
                    return builder.and(predicates.toArray(Predicate[]::new));
                }, PageRequest.of(0, Math.max(1, Math.min(limit, 5000)), Sort.by(Sort.Direction.DESC, "createdOn")))
                .map(entry -> new AuditRow(entry.getId(), entry.getUserId(), entry.getEntityName(),
                        entry.getAction(), entry.getRemarks(), entry.getCreatedOn()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealthRow> healthSnapshot() {
        AdministrationDtos.SystemHealthResponse health = systemHealthService.check();
        return health.components().stream()
                .map(component -> new HealthRow(component.name(), component.status(), component.latencyMs(),
                        component.message()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KpiRow> organizationKpis() {
        long activeUsers = userDirectory.countActiveUsers();
        long students = identityCatalog.activeStudentUserIds().size();
        long organizations = identityCatalog.organizationIds().size();
        long auditEvents = auditLogRepository.count();
        // Every KPI declares its definition and its source (§70, §71).
        return List.of(
                new KpiRow("activeUsers", activeUsers,
                        "Count of users whose Identity lifecycle state is ACTIVE, source: Identity user table"),
                new KpiRow("students", students,
                        "Count of ACTIVE users holding the STUDENT role, source: Identity role assignment"),
                new KpiRow("organizations", organizations,
                        "Count of ACTIVE organizations in the deployment, source: Identity organization table"),
                new KpiRow("auditEvents", auditEvents,
                        "Count of immutable audit_log rows, source: Administration audit trail"));
    }
}
