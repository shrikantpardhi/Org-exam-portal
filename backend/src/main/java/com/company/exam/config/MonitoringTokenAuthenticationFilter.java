package com.company.exam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the monitoring scrape job on the actuator surface (closes GAP-138).
 *
 * <p>Prometheus cannot perform an interactive login; it sends a static credential. This filter
 * recognises that credential on {@code /actuator/**} and grants exactly one authority —
 * {@link SecurityExposurePolicy#MONITORING_SCOPE} — so the monitoring caller can read metrics and
 * nothing else. It is not a general-purpose back door: the token is only inspected for actuator
 * paths, and outside those it is left alone, so presenting it on {@code /api/v1/**} authenticates
 * nothing.
 *
 * <p>The filter must run <em>before</em> {@code IdentityJwtAuthenticationFilter}. That filter only
 * acts when no authentication is present yet, so establishing the monitoring scope first means a
 * monitoring token is never handed to the JWT parser (which would reject it and clear the context).
 *
 * <p>Comparison is delegated to {@link SecurityExposurePolicy#matchesMonitoringToken}, which is
 * constant-time and fails closed when no token is configured.
 */
public class MonitoringTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACTUATOR_PATH_PREFIX = "/actuator/";

    private final SecurityExposurePolicy exposurePolicy;

    public MonitoringTokenAuthenticationFilter(SecurityExposurePolicy exposurePolicy) {
        this.exposurePolicy = exposurePolicy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(ACTUATOR_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String presented = extractBearerToken(request);
        if (presented != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && exposurePolicy.matchesMonitoringToken(presented)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "monitoring", null, List.of(new SimpleGrantedAuthority(SecurityExposurePolicy.MONITORING_SCOPE)));
            authentication.setDetails(request.getRequestURI());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
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
