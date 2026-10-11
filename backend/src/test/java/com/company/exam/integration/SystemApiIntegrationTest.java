package com.company.exam.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.cache.CacheService;
import com.company.exam.common.web.RequestContext;
import com.company.exam.support.AbstractIntegrationTest;
import com.company.exam.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P0 API and security smoke gate ({@code specs/09-P0-Foundation-V1.5.md §76, §125}):
 * the API starts, the standard envelope is valid, the request id is present, protected routes
 * reject unauthenticated callers, forbidden access returns 403, and health distinguishes the
 * database from Redis.
 */
@AutoConfigureMockMvc
class SystemApiIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheService cacheService;

    @Test
    void systemInfoReturnsTheStandardEnvelopeAndEchoesTheRequestId() throws Exception {
        String token = TestTokens.forSubject(UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/system/info")
                        .header(AUTH_HEADER, "Bearer " + token)
                        .header(RequestContext.HEADER, "test-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestContext.HEADER, "test-request-1"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Request processed successfully"))
                .andExpect(jsonPath("$.data.application").value("exam-platform-backend"))
                .andExpect(jsonPath("$.data.requestId").value("test-request-1"))
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void generatedRequestIdIsReturnedWhenTheCallerSuppliesNone() throws Exception {
        String token = TestTokens.forSubject(UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/system/info").header(AUTH_HEADER, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestContext.HEADER))
                .andExpect(jsonPath("$.data.requestId").isNotEmpty());
    }

    @Test
    void protectedEndpointRejectsAnonymousCallerWith401() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void invalidTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/system/info").header(AUTH_HEADER, "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void authenticatedCallerWithoutTheRequiredAuthorityReceives403() throws Exception {
        String token = TestTokens.forSubject(UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/test-support/admin-only").header(AUTH_HEADER, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void unknownApiRouteReturnsAControlledNotFoundEnvelope() throws Exception {
        String token = TestTokens.forSubject(UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/does-not-exist").header(AUTH_HEADER, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void healthDistinguishesDatabaseFromRedis() throws Exception {
        // Redis points at an unused port in the test profile; PostgreSQL is available.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("DOWN"));
    }

    @Test
    void readinessIsUpBecauseTheAuthoritativeDatastoreIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void cacheFailureDoesNotBecomeDataLoss() {
        cacheService.put("p0:probe", new CacheProbe("value"));

        // Redis is unavailable, so the read degrades to a miss instead of failing.
        assertThat(cacheService.get("p0:probe", CacheProbe.class)).isEmpty();
    }

    record CacheProbe(String value) {
    }
}
