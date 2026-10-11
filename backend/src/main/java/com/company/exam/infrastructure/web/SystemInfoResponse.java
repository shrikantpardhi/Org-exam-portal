package com.company.exam.infrastructure.web;

import java.time.Instant;

/**
 * Non-business application information returned by the P0 infrastructure endpoint.
 *
 * @param application  the application name
 * @param version      the build version
 * @param environment  the active environment (local, test, staging, production)
 * @param timestamp    the server time in UTC
 * @param requestId    the request correlation identifier
 */
public record SystemInfoResponse(
        String application,
        String version,
        String environment,
        Instant timestamp,
        String requestId) {
}
