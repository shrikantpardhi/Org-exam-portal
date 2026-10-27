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

    /**
     * {@code POST /api/v1/platform/organizations} request (§72.2, ADR-0056).
     *
     * <p>Collects the whole organization profile at creation: an operator registering an institute
     * already knows its address and contact point, and making them create the organization first and
     * then re-open it to fill in ten fields is busywork that leaves half-populated rows behind.
     *
     * <p>Only {@code code} and {@code name} are required. Every profile column is NULLable in the
     * schema ({@code V37}, ADR-0051) because the {@code PLATFORM} and {@code DEFAULT} organizations
     * predate the profile and had to stay valid without a backfill; requiring fields that the
     * database and every existing row treat as optional would contradict that decision and would
     * reject an organization that legitimately has no public address. An organization can be
     * corrected later through {@code PATCH /organizations/{id}} (§72.17).
     *
     * <p>The {@code @Size} bounds mirror {@code V37} exactly, so a value that passes validation can
     * never be truncated or rejected by PostgreSQL.
     */
    public record CreateOrganizationRequest(
            @NotBlank(message = "code is required") @Size(max = 64) String code,
            @NotBlank(message = "name is required") @Size(max = 200) String name,
            @Size(max = 500) String description,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String stateRegion,
            @Size(max = 20) String postalCode,
            @Size(max = 100) String country,
            @Email(message = "contact email must be a valid address") @Size(max = 254) String contactEmail,
            @Size(max = 32) String contactPhone,
            @Size(max = 500) String websiteUrl,
            @Size(max = 500) String logoUrl) {
    }

    /** Business organization summary in list/create responses (§72.2, §72.3). */
    public record OrganizationSummary(UUID id, String code, String name, Status status) {
    }

    /** Business organization detail (§72.4). */
    public record OrganizationDetail(UUID id, String code, String name, String description, Status status,
            long adminCount, long userCount, String addressLine1, String addressLine2, String city,
            String stateRegion, String postalCode, String country, String contactEmail, String contactPhone,
            String websiteUrl, String logoUrl,
            /* ADR-0052: which administrator may edit roles in this organization; null means none. */
            UUID primaryAdminUserId, String primaryAdminEmail) {
    }

    /**
     * {@code POST /api/v1/platform/organizations/{id}/admins} request (§72.5, ADR-0054).
     *
     * <p>{@code makePrimary} lets the platform owner designate the new account as the organization's
     * primary administrator in the same call. When false the existing primary is kept, and the new
     * account is designated only if the organization has none — the escape from ADR-0052's
     * fail-closed state. {@code Boolean} rather than {@code boolean} so an absent field reads as
     * "not asked for", keeping the wire contract tolerant of older clients.
     */
    public record CreateAdminRequest(
            @NotBlank(message = "firstName is required") @Size(max = 100) String firstName,
            @NotBlank(message = "lastName is required") @Size(max = 100) String lastName,
            @NotBlank(message = "email is required") @Email(message = "email must be a valid address")
            @Size(max = 255) String email,
            @Size(max = 20) String mobile,
            Boolean makePrimary) {
    }

    /**
     * Created administrator summary (§72.5, ADR-0054).
     *
     * <p>{@code isPrimary} reports what the server actually did, so the client can confirm the
     * designation without a second round trip and without guessing from the request it sent.
     */
    public record AdminSummary(UUID userId, String email, Instant invitationSentAt, boolean isPrimary) {
    }

    /**
     * {@code PATCH /api/v1/platform/organizations/{id}} request (ADR-0056, §72.17).
     *
     * <p>Every field is optional and, like {@code CreateOrganizationRequest}, bounded to the {@code V37}
     * column widths. Absent and empty are treated the same way: the profile field is cleared. The
     * request cannot change {@code code} (a rename would break every reference an operator holds),
     * {@code status} (§72.16 owns that lever) or the primary administrator (§72.14).
     */
    public record UpdateOrganizationRequest(
            @Size(max = 200) String name,
            @Size(max = 500) String description,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String stateRegion,
            @Size(max = 20) String postalCode,
            @Size(max = 100) String country,
            @Email(message = "contact email must be a valid address") @Size(max = 254) String contactEmail,
            @Size(max = 32) String contactPhone,
            @Size(max = 500) String websiteUrl,
            @Size(max = 500) String logoUrl) {
    }

    /**
     * {@code PATCH /api/v1/platform/organizations/{id}/status} request (ADR-0055, §72.16).
     *
     * <p>Only {@code ACTIVE} and {@code INACTIVE} are accepted. {@code ARCHIVED} is deliberately not
     * offered: the login gate in {@code AuthenticationService} treats any non-{@code ACTIVE} status the
     * same way, so a third state would add vocabulary without adding behaviour — and a status the
     * product cannot explain is worse than two it can (ADR-0055).
     */
    public record ChangeOrganizationStatusRequest(
            @NotNull(message = "status is required") Status status) {
    }

    /**
     * {@code PATCH /api/v1/platform/organizations/{id}/primary-admin} request (ADR-0054, §72.14).
     *
     * <p>Names the administrator to designate. The account must belong to the organization and must
     * not be archived — an archived account cannot be the authority for who may edit roles.
     */
    public record SetPrimaryAdminRequest(@NotNull(message = "userId is required") UUID userId) {
    }

    /**
     * One administrator of an organization (ADR-0053, {@code GET .../admins}).
     *
     * <p>{@code isPrimary} is carried explicitly rather than left for the client to infer by comparing
     * ids against {@code primaryAdminUserId}: the list is the screen a human uses to decide who may
     * edit roles, so the answer belongs in the row.
     *
     * <p>{@code roles} holds role codes the administrator also carries (typically STUDENT), never
     * ADMIN itself — the administrator capability is the reason the row exists.
     */
    public record AdminListEntry(UUID userId, String firstName, String lastName, String email,
            Status status, boolean isPrimary, Instant createdAt, List<String> roles) {
    }

    /**
     * {@code PATCH .../admins/{userId}/roles} request (ADR-0053).
     *
     * <p>Replaces the non-administrator role set. Sending an empty list removes every extra role and
     * leaves the account an administrator, which is valid: ADMIN alone is a complete assignment.
     */
    public record UpdateAdminRolesRequest(
            @NotNull(message = "roles is required") List<@NotBlank String> roles) {
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
