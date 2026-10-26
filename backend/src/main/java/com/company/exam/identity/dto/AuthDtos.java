package com.company.exam.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Authentication API DTOs ({@code specs/10-P1-Identity-V1.5.md §17, §18, §25, §26, §112}).
 *
 * <p>These shapes follow the frozen API contract exactly. No JPA entity is exposed.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * {@code POST /api/v1/auth/login} request body.
     *
     * <p>{@code portal} is optional ({@code SYSTEM_ADMIN} | {@code ORGANIZATION_ADMIN} |
     * {@code STUDENT}); when present the backend validates the account's roles against it
     * (ADR-0035). Absent keeps the pre-portal behaviour.
     */
    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password,
            String portal) {
    }

    /**
     * Safe user projection embedded in the login response.
     *
     * <p>{@code mustChangePassword} lets the client route straight to the forced password change
     * (ADR-0034); the server filter still enforces the gate regardless of what the client shows.
     */
    public record LoginUser(UUID id, String firstName, String lastName, List<String> roles,
            boolean mustChangePassword) {
    }

    /** {@code POST /api/v1/auth/login} response body. Never contains password or token internals. */
    public record LoginResponse(String accessToken, String tokenType, long expiresIn, LoginUser user) {
    }

    /** {@code POST /api/v1/auth/forgot-password} request body. */
    public record ForgotPasswordRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            String email) {
    }

    /** {@code POST /api/v1/auth/reset-password} request body. */
    public record ResetPasswordRequest(
            @NotBlank(message = "token is required") String token,
            @NotBlank(message = "newPassword is required") String newPassword) {
    }

    /** {@code POST /api/v1/auth/change-password} request body ({@code specs/05 §72.1}). */
    public record ChangePasswordRequest(
            @NotBlank(message = "currentPassword is required") String currentPassword,
            @NotBlank(message = "newPassword is required") String newPassword) {
    }
}
