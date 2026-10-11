package com.company.exam.infrastructure.web;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.web.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Infrastructure-only endpoint that proves the API, response envelope, and request correlation
 * are wired correctly.
 *
 * <p>This is deliberately not a business endpoint ({@code specs/09-P0-Foundation-V1.5.md §76,
 * §128}). It returns only non-sensitive application information and is authenticated like any
 * other {@code /api/v1} resource.
 */
@RestController
@RequestMapping(path = "/api/v1/system", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "System", description = "Non-business infrastructure information")
public class SystemInfoController {

    private final Environment environment;

    public SystemInfoController(Environment environment) {
        this.environment = environment;
    }

    @Operation(
            summary = "Application information",
            description = "Returns application name, version, environment and server time. "
                    + "Used to verify the API foundation and the standard response envelope.")
    @Parameter(name = "X-Request-Id", in = ParameterIn.HEADER, required = false,
            description = "Optional request correlation identifier; returned on the response.")
    @GetMapping("/info")
    public ApiResponse<SystemInfoResponse> info() {
        SystemInfoResponse info = new SystemInfoResponse(
                environment.getProperty("spring.application.name", "exam-platform-backend"),
                environment.getProperty("app.info.version", "unknown"),
                environment.getProperty("app.environment", "unknown"),
                Instant.now(),
                RequestContext.getRequestId());
        return ApiResponse.ok(info);
    }
}
