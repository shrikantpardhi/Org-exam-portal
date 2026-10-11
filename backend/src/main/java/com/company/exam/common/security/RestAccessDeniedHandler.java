package com.company.exam.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns 403 with the standard error envelope when an authenticated caller is not permitted.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = "Access denied";

    private final SecurityResponseWriter responseWriter;

    public RestAccessDeniedHandler(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        responseWriter.write(request, response, HttpStatus.FORBIDDEN, MESSAGE);
    }
}
