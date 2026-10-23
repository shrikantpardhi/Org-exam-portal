package com.company.exam.config;

import com.company.exam.common.security.JwtTokenValidator;
import com.company.exam.common.security.RestAccessDeniedHandler;
import com.company.exam.common.security.RestAuthenticationEntryPoint;
import com.company.exam.identity.security.IdentityJwtAuthenticationFilter;
import com.company.exam.identity.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The Spring Security foundation.
 *
 * <p>P0 configures the authentication pipeline, a JWT validation boundary, JSON 401/403
 * handling, and password hashing. It deliberately does not implement the Identity domain
 * (login, token issuance, RBAC catalogs) — that is P1.
 *
 * <p>CSRF is disabled for this bearer-token REST API; the rationale is recorded in
 * {@code docs/decisions/ADR-0003-jwt-rbac-security-foundation.md}.
 *
 * <p><b>P12 hardening (GAP-136, GAP-137, GAP-138).</b> Three properties of this chain changed:
 *
 * <ul>
 *   <li>the API documentation is no longer unconditionally public — it is gated on
 *       {@link SecurityExposurePolicy#apiDocsReachable()}, which refuses production regardless of
 *       the enabling property;</li>
 *   <li>the actuator tree beyond health and info is no longer anonymous — it requires either the
 *       monitoring scope or the {@code SYSTEM_HEALTH_VIEW} permission, and the
 *       {@link MonitoringTokenAuthenticationFilter} supplies the former to the scrape job;</li>
 *   <li>explicit {@code Content-Security-Policy}, {@code Strict-Transport-Security}, and
 *       {@code Referrer-Policy} headers are written on every response, with the CSP value chosen
 *       by the same policy object so it cannot disagree with the environment it describes.</li>
 * </ul>
 *
 * <p>The public authentication and payment-callback endpoints are untouched: this change narrows
 * the infrastructure surface only, never the business API.
 *
 * <p>See {@code specs/09-P0-Foundation-V1.5.md §31, §34, §35, §36, §125} and
 * {@code specs/21-P12-Hardening-V1.5.md §26, §27, §34}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Health and info stay public.
     *
     * <p>Container healthchecks, the compose {@code depends_on} conditions, and the edge's
     * {@code /health} probes all depend on these being reachable without a credential.
     * {@code show-details: never} is what keeps them safe to leave open.
     */
    private static final String[] PUBLIC_ACTUATOR_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    /** The OpenAPI document and the Swagger UI assets that reference it (GAP-136). */
    private static final String[] API_DOCUMENTATION_ENDPOINTS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    /** Public business endpoints: authentication and the provider-signed payment callback. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/error",
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            // Provider-facing payment callback: authenticated by the provider signature, never a JWT
            // (specs/18-P9-Commerce-V1.5.md §69).
            "/api/v1/payments/callback"
    };

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JwtTokenValidator jwtTokenValidator,
            SessionService sessionService,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource,
            SecurityExposurePolicy exposurePolicy) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // GAP-137. The policy value comes from the policy object so the header and the
                        // environment gate can never disagree; production gets the maximally
                        // restrictive policy and non-production gets the one Swagger UI needs.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(exposurePolicy.contentSecurityPolicy()))
                        // HSTS is written whenever the request actually arrived over TLS. Behind the
                        // Nginx edge the backend itself sees plain HTTP, so the forwarded proto is
                        // part of the decision; over plain HTTP the header is meaningless and is
                        // omitted so local development is unaffected (specs/21-P12 §27).
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)
                                .requestMatcher(IS_HTTPS))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // X-Content-Type-Options: nosniff and X-Frame-Options: DENY are Spring
                        // Security defaults; frameOptions is restated below because DENY is a
                        // deliberate choice here, not an inherited one.
                        .frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Health and info stay open: removing them would blind the deployment.
                        .requestMatchers(PUBLIC_ACTUATOR_ENDPOINTS).permitAll()
                        // Metrics and the rest of the actuator tree require the monitoring scope or
                        // the SYSTEM_HEALTH_VIEW permission (GAP-138). Declared before the business
                        // rules so the intent is readable next to the public list above.
                        .requestMatchers("/actuator/**").access((authentication, context) ->
                                new AuthorizationDecision(
                                        exposurePolicy.mayReadActuator(authentication.get())))
                        // Documentation is a development aid, not a production affordance (GAP-136).
                        .requestMatchers(API_DOCUMENTATION_ENDPOINTS).access((authentication, context) ->
                                new AuthorizationDecision(exposurePolicy.apiDocsReachable()))
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        // Deny by default: anything not explicitly public requires authentication.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // The monitoring filter must precede the JWT filter: the JWT filter only acts when no
                // authentication exists yet, so establishing the monitoring scope first keeps a
                // monitoring token out of the JWT parser (GAP-138). The order is anchored explicitly
                // rather than left to registration sequence.
                .addFilterBefore(new MonitoringTokenAuthenticationFilter(exposurePolicy),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new IdentityJwtAuthenticationFilter(jwtTokenValidator, sessionService),
                        MonitoringTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Matches requests that arrived over TLS, including those terminated at the trusted edge.
     *
     * <p>A plain HTTP deployment therefore never emits {@code Strict-Transport-Security} — which is
     * what keeps local development on {@code http://localhost} working — while a production
     * deployment behind the TLS edge does (specs/21-P12 §27).
     */
    private static final RequestMatcher IS_HTTPS = request -> isSecure(request);

    private static boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto == null) {
            return false;
        }
        for (String proto : List.of(forwardedProto.split(","))) {
            if ("https".equalsIgnoreCase(proto.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adaptive password hashing. The delegating encoder writes the algorithm prefix into the
     * hash so that the algorithm can evolve without breaking stored credentials.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
