package com.company.exam.identity.security;

import com.company.exam.common.security.SecurityResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Server-side enforcement of the forced first-login password change (ADR-0034).
 *
 * <p>A {@code must_change_password} account may only reach the change-password, logout, and
 * current-user endpoints until the flag is cleared. Every other {@code /api/v1} resource is
 * refused with 403 so the flag is never merely a client-side courtesy. The flag is read from the
 * user row by the authentication filter as part of its single session lookup (PostgreSQL is the
 * source of truth), never from the token, so a stale token cannot bypass the gate.
 *
 * <p>Runs after {@link IdentityJwtAuthenticationFilter}, which populates the {@link IdentityPrincipal}
 * (including its {@code mustChangePassword} flag) from a single session read; on public or
 * unauthenticated routes there is no principal and this filter does nothing.
 *
 * <p>Not a Spring bean: it is constructed in {@code SecurityConfig} and added to the security
 * filter chain explicitly, so it is never double-registered as a servlet filter.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    /** Endpoints a must-change account may still reach. Exact paths only. */
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/me",
            "/api/v1/me/permissions");

    private static final String MESSAGE = "Password change required";

    private final SecurityResponseWriter responseWriter;

    public MustChangePasswordFilter(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (mustChangePassword(authentication) && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            responseWriter.write(request, response, HttpStatus.FORBIDDEN, MESSAGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean mustChangePassword(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return false;
        }
        return principal.mustChangePassword();
    }
}
