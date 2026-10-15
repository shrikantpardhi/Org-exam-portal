package com.company.exam.administration.dto;

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
