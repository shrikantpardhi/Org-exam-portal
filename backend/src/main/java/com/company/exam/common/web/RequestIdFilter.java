package com.company.exam.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the {@code X-Request-Id} correlation identifier for every request.
 *
 * <p>Rules ({@code specs/09-P0-Foundation-V1.5.md §16}):
 * <ul>
 *   <li>if the caller supplies a valid identifier, it is propagated;</li>
 *   <li>otherwise a new identifier is generated;</li>
 *   <li>the identifier is returned in the response and appears in structured logs;</li>
 *   <li>arbitrary client input never becomes a trusted security identity — the value is
 *       strictly validated and length-bounded before use.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Allows correlation identifiers but rejects anything that could be injected into logs. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(RequestContext.HEADER));
        try {
            RequestContext.setRequestId(requestId);
            response.setHeader(RequestContext.HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
