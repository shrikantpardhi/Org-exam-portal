package com.company.exam.administration.service;

import com.company.exam.administration.api.LookupCatalog;
import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.repository.AuditLogRepository;
import com.company.exam.administration.repository.BusinessRuleRepository;
import com.company.exam.administration.repository.LookupRepository;
import com.company.exam.identity.api.AdminUserDirectory;
import com.company.exam.identity.api.AnalyticsIdentityCatalog;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin dashboard ({@code specs/20-P11-Administration-V1.5.md §21, §22}).
 *
 * <p>UI composition, not a database entity: every card is derived from the owning module and no
 * dashboard-specific transactional copy is created ({@code §21, §22}).
 */
@Service
public class AdminDashboardService {

    private final AdminUserDirectory userDirectory;
    private final AnalyticsIdentityCatalog identityCatalog;
    private final AuditLogRepository auditLogRepository;
    private final BusinessRuleRepository ruleRepository;
    private final LookupRepository lookupRepository;
    private final LookupCatalog lookupCatalog;

    public AdminDashboardService(AdminUserDirectory userDirectory, AnalyticsIdentityCatalog identityCatalog,
            AuditLogRepository auditLogRepository, BusinessRuleRepository ruleRepository,
            LookupRepository lookupRepository, LookupCatalog lookupCatalog) {
        this.userDirectory = userDirectory;
        this.identityCatalog = identityCatalog;
        this.auditLogRepository = auditLogRepository;
        this.ruleRepository = ruleRepository;
        this.lookupRepository = lookupRepository;
        this.lookupCatalog = lookupCatalog;
    }

    @Transactional(readOnly = true)
    public AdministrationDtos.AdminDashboardResponse dashboard() {
        Instant since = Instant.now().minus(Duration.ofDays(1));
        long activeUsers = userDirectory.countActiveUsers();
        long students = identityCatalog.activeStudentUserIds().size();
        long auditEvents = auditLogRepository.count((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("createdOn"), since));
        long rules = ruleRepository.count();
        long lookups = lookupRepository.count();
        // Demonstrates the consumer contracts the dashboard uses without a transactional copy.
        int activeLookupValues = lookups == 0 ? 0 : lookupCatalog.values("QUESTION_DIFFICULTY").size();
        List<AdministrationDtos.DashboardCardSource> sources = List.of(
                new AdministrationDtos.DashboardCardSource("activeUsers", "Identity"),
                new AdministrationDtos.DashboardCardSource("students", "Identity"),
                new AdministrationDtos.DashboardCardSource("auditEventsLast24h", "Administration (audit_log)"),
                new AdministrationDtos.DashboardCardSource("businessRules", "Administration"),
                new AdministrationDtos.DashboardCardSource("lookups", "Administration"));
        return new AdministrationDtos.AdminDashboardResponse(activeUsers, students, auditEvents, rules, lookups,
                activeLookupValues, Instant.now(), sources);
    }
}
