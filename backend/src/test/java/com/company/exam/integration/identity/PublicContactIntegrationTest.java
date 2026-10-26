package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.publicapi.ContactRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Public landing-page contact form (ADR-0037): unauthenticated, validated, rate-limited per source
 * IP, and recorded as a {@code CONTACT_REQUEST} background job (no user, no business data).
 */
class PublicContactIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String VALID_BODY = "{\"name\":\"Jane Doe\",\"email\":\"jane@example.com\","
            + "\"message\":\"Hello there\",\"organization\":\"Acme\"}";

    @Autowired
    private BackgroundJobRepository backgroundJobRepository;

    @Autowired
    private ContactRateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.reset();
    }

    @Test
    void validContactIsAcceptedAndRecorded() throws Exception {
        mockMvc.perform(post("/api/v1/public/contact").contentType(JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(backgroundJobRepository.findAll())
                .anySatisfy(job -> {
                    assertThat(job.getJobType()).isEqualTo("CONTACT_REQUEST");
                    assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
                });
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/public/contact").contentType(JSON)
                        .content("{\"name\":\"Jane\",\"email\":\"not-an-email\",\"message\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rateLimitIsEnforcedPerSourceIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/public/contact").contentType(JSON).content(VALID_BODY))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/public/contact").contentType(JSON).content(VALID_BODY))
                .andExpect(status().isTooManyRequests());
    }
}
