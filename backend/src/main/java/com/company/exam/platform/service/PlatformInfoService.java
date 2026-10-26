package com.company.exam.platform.service;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.SystemHealthService;
import com.company.exam.common.enums.Status;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.platform.dto.PlatformDtos;
import java.lang.management.ManagementFactory;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SUPER_ADMIN platform status and system information ({@code specs/05 §72.6, §72.7}; ADR-0033).
 *
 * <p>Status aggregates counts and a coarse health signal; system information reports version,
 * build, migration, uptime, and infrastructure dependency health. Never returns credentials,
 * connection strings, environment dumps, or a container inventory (§72.7, C6).
 */
@Service
public class PlatformInfoService {

    private final OrganizationRepository organizationRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SystemHealthService systemHealthService;
    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Flyway flyway;

    public PlatformInfoService(OrganizationRepository organizationRepository,
            UserRoleRepository userRoleRepository, UserRepository userRepository, RoleRepository roleRepository,
            SystemHealthService systemHealthService, Environment environment,
            ObjectProvider<BuildProperties> buildProperties, Flyway flyway) {
        this.organizationRepository = organizationRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.systemHealthService = systemHealthService;
        this.environment = environment;
        this.buildProperties = buildProperties;
        this.flyway = flyway;
    }

    @Transactional(readOnly = true)
    public PlatformDtos.PlatformStatusResponse status() {
        long organizationCount = organizationRepository.countByStatusAndCodeNot(Status.ACTIVE,
                PlatformOrganizationService.PLATFORM_CODE);
        long adminCount = userRoleRepository.countActiveAssignmentsByRoleCode(
                PlatformOrganizationService.ADMIN_ROLE_CODE);
        long userCount = userRepository.countByStatusAndIdNot(Status.ACTIVE, IdentityConstants.SYSTEM_ACTOR_ID);
        String seedStatus = seedComplete() ? "COMPLETE" : "INCOMPLETE";
        String systemHealth = toUpDown(systemHealthService.check().overall());
        return new PlatformDtos.PlatformStatusResponse(organizationCount, adminCount, userCount, seedStatus,
                systemHealth);
    }

    @Transactional(readOnly = true)
    public PlatformDtos.PlatformSystemResponse system() {
        AdministrationDtos.SystemHealthResponse health = systemHealthService.check();
        List<PlatformDtos.DependencyStatus> dependencies = health.components().stream()
                .filter(component -> "POSTGRESQL".equals(component.name()) || "REDIS".equals(component.name()))
                .map(component -> new PlatformDtos.DependencyStatus(component.name().toLowerCase(),
                        toUpDown(component.status())))
                .toList();

        String appVersion = environment.getProperty("app.info.version");
        String frontendVersion = environment.getProperty("app.info.frontend-version");
        String gitCommit = null;
        String buildDate = null;
        BuildProperties build = buildProperties.getIfAvailable();
        if (build != null) {
            gitCommit = build.get("git.commit.id");
            buildDate = build.getTime() == null ? null : build.getTime().toString();
        }

        MigrationInfo current = flyway.info().current();
        Integer migrationVersion = null;
        String lastMigration = null;
        java.time.Instant appliedOn = null;
        if (current != null && current.getVersion() != null) {
            migrationVersion = parseVersion(current.getVersion().getVersion());
            String script = current.getScript();
            lastMigration = script == null ? null : script.substring(script.lastIndexOf('/') + 1);
            appliedOn = current.getInstalledOn() == null ? null : current.getInstalledOn().toInstant();
        }

        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new PlatformDtos.PlatformSystemResponse(appVersion, frontendVersion, gitCommit, buildDate,
                migrationVersion, lastMigration, appliedOn, uptimeSeconds, dependencies);
    }

    private Integer parseVersion(String version) {
        try {
            return version == null ? null : Integer.valueOf(version);
        } catch (NumberFormatException nonNumeric) {
            return null;
        }
    }

    private boolean seedComplete() {
        return organizationRepository.findByCode(PlatformOrganizationService.PLATFORM_CODE).isPresent()
                && roleRepository.findByCodeAndOrganizationIdIsNull("SUPER_ADMIN").isPresent()
                && roleRepository.findByCodeAndOrganizationIdIsNull(PlatformOrganizationService.ADMIN_ROLE_CODE).isPresent();
    }

    private String toUpDown(String healthStatus) {
        if (healthStatus == null) {
            return "DOWN";
        }
        return switch (healthStatus) {
            case "HEALTHY" -> "UP";
            case "UNAVAILABLE" -> "DOWN";
            default -> "DEGRADED";
        };
    }
}
