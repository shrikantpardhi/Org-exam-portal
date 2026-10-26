package com.company.exam.identity.security;

import com.company.exam.common.security.JwtTokenValidator;
import com.company.exam.identity.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from a bearer JWT and populates the security context
 * ({@code specs/10-P1-Identity-V1.5.md §87, §88}).
 *
 * <p>Only the {@code Authorization: Bearer <JWT>} scheme is accepted. A token whose {@code sid}
 * claim refers to a session that is no longer ACTIVE is treated as anonymous, so logout, reset,
 * archive, and timeout all take effect immediately ({@code §43, §76}).
 *
 * <p>Tokens without a {@code sid} claim (for example, the P0 boundary smoke tokens) authenticate
 * the subject identity but carry no session; they cannot reach any permission-protected endpoint
 * because authorities are resolved per request by the authorization service.
 *
 * <p>Authorities are intentionally not populated here: authorization is a separate concern
 * ({@code §87}).
 */
public class IdentityJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenValidator tokenValidator;
    private final SessionService sessionService;

    public IdentityJwtAuthenticationFilter(JwtTokenValidator tokenValidator, SessionService sessionService) {
        this.tokenValidator = tokenValidator;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenValidator.validate(token).getPayload();
                authenticate(claims, request);
            } catch (RuntimeException invalidToken) {
                // Leave the request anonymous; protected routes return 401 via the entry point.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            return;
        }
        UUID userId = UUID.fromString(subject);
        String sessionReference = claims.get(JwtTokenIssuer.SESSION_CLAIM, String.class);
        if (sessionReference == null || sessionReference.isBlank()) {
            setAuthentication(userId, null, false, request);
            return;
        }
        UUID sessionId = UUID.fromString(sessionReference);
        Optional<Boolean> mustChangePassword = sessionService.mustChangePasswordForActiveSession(sessionId, userId);
        if (mustChangePassword.isPresent()) {
            setAuthentication(userId, sessionId, mustChangePassword.get(), request);
        }
    }

    private void setAuthentication(UUID userId, UUID sessionId, boolean mustChangePassword,
            HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(
                new IdentityPrincipal(userId, sessionId, mustChangePassword), null, List.of());
        authentication.setDetails(request.getRequestURI());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
