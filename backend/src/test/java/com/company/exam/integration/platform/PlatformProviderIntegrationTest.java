package com.company.exam.integration.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * SUPER_ADMIN provider configuration ({@code specs/05 §72.8, §72.9}).
 *
 * <p>Responses are masked: no secret is ever returned, and the payment view never reports a stored
 * secret. Configuration is persisted in {@code organization_setting.configuration} for PLATFORM.
 */
class PlatformProviderIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Super-Strong-Pass-1";

    @Test
    void getNeverLeaksSecrets() throws Exception {
        User sa = createUser(syntheticEmail("sa-prov"), PASSWORD, "SUPER_ADMIN");
        mockMvc.perform(get("/api/v1/platform/providers")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.webhookSecretSet").value(false))
                .andExpect(jsonPath("$.data.email.secret").doesNotExist())
                .andExpect(jsonPath("$.data.email.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.payment.secret").doesNotExist())
                .andExpect(jsonPath("$.data.payment.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.payment.webhookSecret").doesNotExist());
    }

    @Test
    void updatePersistsAndGetReflectsMaskedValues() throws Exception {
        User sa = createUser(syntheticEmail("sa-prov-put"), PASSWORD, "SUPER_ADMIN");
        String token = bearer(tokenFor(sa));

        mockMvc.perform(put("/api/v1/platform/providers")
                        .header(AUTHORIZATION, token)
                        .contentType(JSON)
                        .content("{\"email\":{\"providerName\":\"SMTP\",\"fromAddress\":\"noreply@acme.test\"},"
                                + "\"payment\":{\"providerName\":\"RAZORPAY\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email.providerName").value("SMTP"))
                .andExpect(jsonPath("$.data.email.fromAddress").value("noreply@acme.test"))
                .andExpect(jsonPath("$.data.email.configured").value(true))
                .andExpect(jsonPath("$.data.payment.providerName").value("RAZORPAY"))
                .andExpect(jsonPath("$.data.payment.webhookSecretSet").value(false));

        mockMvc.perform(get("/api/v1/platform/providers").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email.configured").value(true))
                .andExpect(jsonPath("$.data.email.providerName").value("SMTP"))
                .andExpect(jsonPath("$.data.payment.providerName").value("RAZORPAY"))
                .andExpect(jsonPath("$.data.payment.webhookSecretSet").value(false));
    }

    @Test
    void providersRequireSuperAdminPermission() throws Exception {
        User student = createUser(syntheticEmail("student-prov"), PASSWORD, "STUDENT");
        mockMvc.perform(get("/api/v1/platform/providers")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden());
    }
}
