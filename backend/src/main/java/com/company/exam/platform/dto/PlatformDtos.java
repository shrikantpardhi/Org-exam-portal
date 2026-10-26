package com.company.exam.platform.dto;

import com.company.exam.common.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SUPER_ADMIN platform DTOs ({@code specs/05-API-Contract-V1.5.md §72}).
 *
 * <p>Entities are never exposed ({@code specs/06 §44}); these are the wire shapes for the nine
 * SUPER_ADMIN/authenticated endpoints. Provider responses never carry secrets (§72.8).
 */
public final class PlatformDtos {

    private PlatformDtos() {
    }

    /**
     * One row of the cross-organization user list (§72.11, ADR-0042).
     *
     * <p>{@code organizationId}/{@code organizationCode} are present so a platform operator can tell
     * two identically named users apart; {@code roles} carries codes, never permission lists.
     */
    public record PlatformUserSummary(UUID id, String firstName, String lastName, String email,
            Status status, UUID organizationId, String organizationCode, List<String> roles) {
    }

    /** {@code POST /api/v1/platform/organizations} request (§72.2). */
    public record CreateOrganizationRequest(
            @NotBlank(message = "code is required") @Size(max = 64) String code,
            @NotBlank(message = "name is required") @Size(max = 200) String name,
            @Size(max = 500) String description) {
    }

    /** Business organization summary in list/create responses (§72.2, §72.3). */
    public record OrganizationSummary(UUID id, String code, String name, Status status) {
    }

    /** Business organization detail (§72.4). */
    public record OrganizationDetail(UUID id, String code, String name, String description, Status status,
            long adminCount, long userCount, String addressLine1, String addressLine2, String city,
            String stateRegion, String postalCode, String country, String contactEmail, String contactPhone,
            String websiteUrl, String logoUrl) {
    }

    /** {@code POST /api/v1/platform/organizations/{id}/admins} request (§72.5). */
    public record CreateAdminRequest(
            @NotBlank(message = "firstName is required") @Size(max = 100) String firstName,
            @NotBlank(message = "lastName is required") @Size(max = 100) String lastName,
            @NotBlank(message = "email is required") @Email(message = "email must be a valid address")
            @Size(max = 255) String email,
            @Size(max = 20) String mobile) {
    }

    /** Created administrator summary (§72.5). */
    public record AdminSummary(UUID userId, String email, Instant invitationSentAt) {
    }

    /**
     * {@code POST /api/v1/platform/organizations/{orgId}/admins/{userId}/reset-password} and
     * {@code .../unlock-sessions} response (ADR-0042, §72.12-§72.13).
     *
     * <p>Never carries a password or a reset token: the token is delivered out of band
     * ({@code §24}). {@code revokedSessions} is the count of sessions this call ended.
     */
    public record AdminRecoveryResponse(UUID userId, String email, Action action, int revokedSessions) {

        /** Which recovery action was performed. */
        public enum Action {
            RESET_PASSWORD,
            UNLOCK_SESSIONS
        }
    }

    /** {@code GET /api/v1/platform/status} (§72.6). */
    public record PlatformStatusResponse(long organizationCount, long adminCount, long userCount,
            String seedStatus, String systemHealth) {
    }

    /** {@code GET /api/v1/platform/system} (§72.7). */
    public record PlatformSystemResponse(String appVersion, String frontendVersion, String gitCommit,
            String buildDate, Integer migrationVersion, String lastMigration, Instant appliedOn,
            long uptimeSeconds, List<DependencyStatus> dependencies) {
    }

    /** One infrastructure dependency health entry (§72.7). */
    public record DependencyStatus(String name, String status) {
    }

    /** One provider's editable, non-secret fields (§72.9). */
    public record ProviderFields(String providerName, String fromAddress) {
    }

    /** {@code PUT /api/v1/platform/providers} request (§72.9). */
    public record ProviderConfigUpdate(ProviderFields email, ProviderFields payment) {
    }

    /** A masked email-provider view; never contains keys or secrets (§72.8). */
    public record EmailProviderView(boolean configured, String providerName, String fromAddress,
            Instant updatedAt) {
    }

    /** A masked payment-provider view; never contains keys or secrets (§72.8). */
    public record PaymentProviderView(boolean configured, String providerName, boolean webhookSecretSet,
            Instant updatedAt) {
    }

    /** {@code GET /api/v1/platform/providers} and PUT response (§72.8). */
    public record ProvidersResponse(EmailProviderView email, PaymentProviderView payment) {
    }
}
