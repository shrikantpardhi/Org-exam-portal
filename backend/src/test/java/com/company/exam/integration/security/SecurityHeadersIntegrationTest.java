package com.company.exam.integration.security;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GAP-137: the security headers are present on responses.
 *
 * <p>Asserted on an actually-served response rather than on the configuration object, because the
 * gap was not "no configuration exists" — it was "no header reaches the client". A test that
 * inspected the {@code HeadersConfigurer} would pass while every response shipped bare, which is
 * precisely how the gap survived to this phase.
 *
 * <p>{@code specs/21-P12-Hardening-V1.5.md §26} requires a Content-Security-Policy, a
 * Strict-Transport-Security, and a Referrer-Policy. HSTS is deliberately conditional on the request
 * having arrived over TLS (§27): the header is meaningless over plain HTTP, and the edge serves
 * plain HTTP in local development. Both branches are therefore covered — the unconditional headers
 * on a plain request, and HSTS on a request the edge reports as forwarded over HTTPS.
 *
 * <p>The assertions deliberately do not require a 2xx status. The headers must be written on error
 * responses too (that is the {@code always} requirement), and in this profile the aggregate health
 * endpoint legitimately answers 503 because Redis is intentionally unavailable.
 */
@AutoConfigureMockMvc
class SecurityHeadersIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contentSecurityPolicyIsPresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void referrerPolicyIsPresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void framingAndSniffingAreDenied() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void thePolicyForbidsFramingAndBaseUriOverrides() throws Exception {
        // The specific directives matter more than the header's presence: frame-ancestors and
        // base-uri are the two that stop clickjacking and relative-URL hijacking respectively.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy",
                        allOf(containsString("frame-ancestors 'none'"), containsString("base-uri"))));
    }

    @Test
    void transportSecurityIsOmittedOverPlainHttp() throws Exception {
        // Local development talks to the backend over plain HTTP; emitting HSTS there would be
        // inert at best and misleading at worst.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void transportSecurityIsPresentWhenTheEdgeReportsHttps() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Forwarded-Proto", "https"))
                .andExpect(header().string("Strict-Transport-Security",
                        allOf(containsString("max-age=31536000"), containsString("includeSubDomains"))));
    }

    @Test
    void headersArePresentOnRejectedRequestsToo() throws Exception {
        // A 401 that omits the policy would let an unauthenticated caller frame or sniff an error
        // page. `always` semantics are the requirement, not `on-success`.
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
