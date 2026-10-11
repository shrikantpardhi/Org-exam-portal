package com.company.exam.identity.dto;

import com.company.exam.common.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User API DTOs ({@code specs/10-P1-Identity-V1.5.md §53, §112, §113}).
 *
 * <p>{@code UserSummary} is the list projection; {@code UserDetail} adds safe profile context.
 * Neither exposes the password hash or audit internals.
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record UserSummary(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String mobile,
            Status status,
            List<String> roles) {
    }

    public record UserDetail(
            UUID id,
            UUID organizationId,
            String firstName,
            String lastName,
            String email,
            String mobile,
            Status status,
            List<String> roles,
            Instant createdOn,
            Instant updatedOn) {
    }

    /**
     * {@code POST /api/v1/users} request body.
     *
     * <p>The frozen API contract does not accept a password here; the onboarding policy assigns a
     * non-usable credential and the account is activated through password reset
     * ({@code specs/10-P1-Identity-V1.5.md §56, §58}).
     */
    public record CreateUserRequest(
            @NotBlank(message = "firstName is required") @Size(max = 100) String firstName,
            @NotBlank(message = "lastName is required") @Size(max = 100) String lastName,
            @NotBlank(message = "email is required") @Email(message = "email must be a valid address")
            @Size(max = 255) String email,
            @Size(max = 20) String mobile,
            @NotNull(message = "roles is required") List<UUID> roles) {
    }

    /** {@code PUT /api/v1/users/{id}} request body. Only permitted profile fields may change. */
    public record UpdateUserRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 20) String mobile,
            List<UUID> roles) {
    }
}
