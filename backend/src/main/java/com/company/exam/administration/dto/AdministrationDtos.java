package com.company.exam.administration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Administration request and response payloads ({@code specs/20-P11-Administration-V1.5.md §8-§11}). */
public final class AdministrationDtos {

    private AdministrationDtos() {
    }

    // ------------------------------------------------------------------ organization settings

    public record OrganizationSettingResponse(UUID id, UUID organizationId, Map<String, Object> configuration,
            List<SettingKeyDescriptor> registry) {
    }

    public record SettingKeyDescriptor(String key, String type, Object defaultValue, boolean mutable,
            String securityClassification) {
    }

    public record UpdateOrganizationSettingRequest(Map<String, Object> configuration) {
    }

    // ------------------------------------------------------------------ business rules

    public record BusinessRuleResponse(UUID id, String code, Map<String, Object> configuration, String status) {
    }

    public record CreateBusinessRuleRequest(@NotBlank @Size(max = 100) String code,
            Map<String, Object> configuration) {
    }

    public record UpdateBusinessRuleRequest(Map<String, Object> configuration) {
    }

    // ------------------------------------------------------------------ lookups

    public record LookupResponse(UUID id, String category, String code, String name, Integer displayOrder,
            String status) {
    }

    public record CreateLookupRequest(@NotBlank @Size(max = 100) String category,
            @NotBlank @Size(max = 100) String code, @NotBlank @Size(max = 150) String name, Integer displayOrder) {
    }

    public record UpdateLookupRequest(@Size(max = 150) String name, Integer displayOrder) {
    }

    // ------------------------------------------------------------------ audit

    public record AuditLogResponse(UUID id, UUID userId, String entityName, UUID entityId, String action,
            String remarks, Instant createdOn) {
    }

    // ------------------------------------------------------------------ dashboard

    /** Dashboard cards derived from owning modules; never a transactional copy ({@code §21, §22}). */
    public record AdminDashboardResponse(long activeUsers, long students, long auditEventsLast24h,
            long businessRules, long lookups, int activeLookupValues, Instant generatedOn,
            List<DashboardCardSource> sources) {
    }

    public record DashboardCardSource(String card, String owner) {
    }

    // ------------------------------------------------------------------ organization configuration

    /**
     * The caller's organization identity plus its editable profile (V37, ADR-0051).
     *
     * <p>The profile fields are returned here rather than through a separate endpoint so the settings
     * screen makes one request, not two. Every field is nullable: an organization that has not filled in
     * its profile yet returns nulls, and the UI renders an empty state.
     */
    public record OrganizationConfigOrganization(String name, String code, String timezone,
            String description, String addressLine1, String addressLine2, String city, String stateRegion,
            String postalCode, String country, String contactEmail, String contactPhone, String websiteUrl,
            String logoUrl) {
    }

    /** {@code PUT /api/v1/admin/organization-config/profile} request (ADR-0051). */
    public record UpdateOrganizationProfileRequest(@Size(max = 150) String name,
            @Size(max = 4000) String description, @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2, @Size(max = 100) String city,
            @Size(max = 100) String stateRegion, @Size(max = 20) String postalCode,
            @Size(max = 100) String country, @Email @Size(max = 254) String contactEmail,
            @Size(max = 32) String contactPhone, @Size(max = 500) String websiteUrl,
            @Size(max = 500) String logoUrl) {
    }

    public record OrganizationConfigProviders(boolean emailConfigured, boolean paymentConfigured) {
    }

    public record OrganizationConfigResponse(OrganizationConfigOrganization organization,
            Map<String, Object> featureFlags, OrganizationConfigProviders providerStatus,
            Integer lastMigrationVersion, String appVersion) {
    }

    // ------------------------------------------------------------------ system health

    public record SystemHealthResponse(String overall, Instant checkedOn, List<HealthComponent> components) {
    }

    public record HealthComponent(String name, String status, Long latencyMs, String message,
            Map<String, String> detail) {
    }

    // ------------------------------------------------------------------ global search

    public record SearchResult(String resource, UUID id, String label, Map<String, String> fields) {
    }

    public record SearchResponse(String query, List<String> searchedResources, List<SearchResult> results,
            boolean truncated) {
    }

    // ------------------------------------------------------------------ support console

    public record SupportUserView(UUID userId, String firstName, String lastName, String email, String mobile,
            String status, boolean contactUnmasked) {
    }

    // ------------------------------------------------------------------ bulk and imports

    public record BulkOperationAccepted(UUID jobId, String status) {
    }

    public record BulkOperationRequest(List<UUID> ids) {
    }

    public record ImportRowError(int row, String field, String code, String message) {
    }

    public record ImportJobStatus(UUID jobId, String targetCode, String status, int totalRecords, int successCount,
            int failureCount, List<ImportRowError> errors) {
    }

    public record SupportedResource(String resource, List<String> operations, String permission) {
    }

    public record SupportedImportTarget(String targetCode, String source, List<String> requiredPermissions) {
    }
}
