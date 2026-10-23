package com.company.exam.integration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GAP-136: production rejects the API documentation endpoints.
 *
 * <p>The two properties of this test are the whole point.
 *
 * <p><b>It runs with the production profile active.</b> That is what actually ships. Asserting on
 * the {@code app.environment} property alone would test a string, not a deployment.
 *
 * <p><b>It declares {@code app.environment=staging} on purpose.</b> This is the exact trap that
 * shipped a defect once already (GAP-085): {@code docker-compose.yml} sets an {@code
 * APP_ENVIRONMENT} variable that outranks the value in {@code application-prod.yml}, so a process
 * started with the production profile inside such a container reports itself as staging. A gate
 * that trusted only the declared environment would publish the entire API surface in production
 * while every configuration file looked correct. The gate must therefore refuse on <em>either</em>
 * signal, and this test locks that in.
 */
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "prod"})
@TestPropertySource(properties = "app.environment=staging")
class ApiDocsProductionGateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void thePremiseHoldsTheEnvironmentSaysStagingWhileTheProdProfileIsActive() {
        // Guards the test's own value: if a future change made app.environment read "production"
        // here, the assertions below could pass for the wrong reason and the trap would be untested.
        assertThat(environment.getProperty("app.environment")).isEqualTo("staging");
        assertThat(List.of(environment.getActiveProfiles())).contains("prod");
    }

    @Test
    void openApiDocumentIsNotReachable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentSubPathsAreNotReachable() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUiIsNotReachable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUiAssetsAreNotReachable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }
}
