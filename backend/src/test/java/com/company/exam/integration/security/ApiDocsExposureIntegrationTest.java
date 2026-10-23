package com.company.exam.integration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * GAP-136: outside production the API documentation stays usable.
 *
 * <p>This is the other half of the gate. The production test proves the docs are refused; this one
 * proves the refusal is not achieved by breaking the feature everywhere, which would "fix" the gap
 * by removing the developer affordance the constraint requires us to preserve
 * ({@code specs/21-P12-Hardening-V1.5.md §26}).
 */
@AutoConfigureMockMvc
class ApiDocsExposureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentIsServedOutsideProduction() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"openapi\"");
        // The document must actually describe this API, not be an empty stub: a gate that
        // silently returns an empty schema would pass a status-only assertion.
        assertThat(body).contains("/api/v1/auth/login");
    }

    @Test
    void swaggerUiIsReachableOutsideProduction() throws Exception {
        // springdoc redirects the configured UI path to the bundled index page.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void swaggerUiAssetsAreServedOutsideProduction() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
