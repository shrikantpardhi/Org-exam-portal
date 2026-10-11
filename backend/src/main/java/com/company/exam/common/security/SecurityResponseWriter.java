package com.company.exam.common.security;

import com.company.exam.common.api.ApiErrorResponse;
import com.company.exam.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes a security failure as the standard error envelope.
 *
 * <p>Security failures occur before the dispatcher, so they cannot be rendered by the
 * {@code GlobalExceptionHandler}; this writer keeps the response contract identical.
 */
@Component
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(message, null, request.getRequestURI(),
                RequestContext.getRequestId());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
