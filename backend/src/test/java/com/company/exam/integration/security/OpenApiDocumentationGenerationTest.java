package com.company.exam.integration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * GAP-170 / GAP-028: retain the OpenAPI document as a build artifact so the public /docs/api
 * reference can be generated from it at build time instead of drifting as static prose
 * ({@code specs/21-P12-Hardening-V1.5.md §130}, {@code docs/frontend/docs-site.md} §35).
 *
 * <p>The document is written to {@code target/generated/openapi.json} during {@code verify}. The
 * frontend build consumes the checked-in copy at {@code frontend/generated/openapi.json}, which is
 * refreshed by copying this artifact after a backend build.
 */
@AutoConfigureMockMvc
class OpenApiDocumentationGenerationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentIsRetainedAsABuildArtifact() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Not an empty stub: the document must describe the real surface.
        assertThat(body).contains("openapi");
        assertThat(body).contains("/api/v1/auth/login");

        Path target = Path.of("target", "generated", "openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, body);

        assertThat(target).exists();
    }
}
