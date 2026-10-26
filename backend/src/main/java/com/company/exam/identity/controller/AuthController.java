package com.company.exam.identity.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.dto.AuthDtos;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthenticationService;
import com.company.exam.identity.service.PasswordChangeService;
import com.company.exam.identity.service.PasswordResetService;
import com.company.exam.identity.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication API ({@code specs/10-P1-Identity-V1.5.md §16-§31};
 * {@code specs/05-API-Contract-V1.5.md §21}).
 *
 * <p>Controllers stay thin: validation and HTTP mapping here, workflows in the services.
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Login, logout, refresh, and password reset")
public class AuthController {

    private static final int MAX_DEVICE_INFO_LENGTH = 1000;

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final PasswordChangeService passwordChangeService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthenticationService authenticationService, PasswordResetService passwordResetService,
            PasswordChangeService passwordChangeService, ClientIpResolver clientIpResolver) {
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
        this.passwordChangeService = passwordChangeService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Log in", description = "Authenticates a user and returns an access token.")
    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthDtos.LoginResponse response = authenticationService.login(request,
                clientIpResolver.resolve(httpRequest), deviceInfo(httpRequest));
        return ApiResponse.ok("Login successful", response);
    }

    @Operation(summary = "Log out", description = "Ends the caller's login session. Idempotent.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal IdentityPrincipal principal) {
        authenticationService.logout(principal);
        return ApiResponse.ok("Logout successful", null);
    }

    @Operation(summary = "Refresh access token",
            description = "Reissues an access token for the caller's active session.")
    @PostMapping("/refresh-token")
    public ApiResponse<AuthDtos.LoginResponse> refresh(@AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Token refreshed", authenticationService.refresh(principal));
    }

    @Operation(summary = "Request a password reset",
            description = "Always returns the same response; never reveals whether the account exists.")
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ApiResponse.ok("If the account exists, password reset instructions have been sent", null);
    }

    @Operation(summary = "Change the caller's password",
            description = "Verifies the current password and replaces it. Clears must_change_password.")
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        passwordChangeService.changePassword(principal.userId(), principal.sessionId(), request);
        return ApiResponse.ok("Password changed", null);
    }

    @Operation(summary = "Reset a password", description = "Completes a reset with a single-use token.")
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ApiResponse.ok("Password has been reset", null);
    }

    private String deviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_DEVICE_INFO_LENGTH ? userAgent : userAgent.substring(0, MAX_DEVICE_INFO_LENGTH);
    }
}
