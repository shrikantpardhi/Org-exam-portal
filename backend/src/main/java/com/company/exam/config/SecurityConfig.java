package com.company.exam.config;

import com.company.exam.common.security.JwtTokenValidator;
import com.company.exam.common.security.RestAccessDeniedHandler;
import com.company.exam.common.security.RestAuthenticationEntryPoint;
import com.company.exam.identity.security.IdentityJwtAuthenticationFilter;
import com.company.exam.identity.service.SessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
 * <p>See {@code specs/09-P0-Foundation-V1.5.md §31, §34, §35, §36, §125}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Public infrastructure endpoints and the public authentication endpoints. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
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
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        // Deny by default: anything not explicitly public requires authentication.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new IdentityJwtAuthenticationFilter(jwtTokenValidator, sessionService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
