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

    /** {@code POST /api/v1/auth/login} request body. */
    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) {
    }

    /** Safe user projection embedded in the login response. */
    public record LoginUser(UUID id, String firstName, String lastName, List<String> roles) {
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
}
