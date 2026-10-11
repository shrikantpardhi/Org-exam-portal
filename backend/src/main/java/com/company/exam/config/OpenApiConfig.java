package com.company.exam.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document configuration.
 *
 * <p>Documents the API title, version, description, the bearer authentication scheme, and the
 * {@code X-Request-Id} correlation header. Every later business endpoint is added to this same
 * document ({@code specs/09-P0-Foundation-V1.5.md §38, §39}).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI examPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart Competitive Examination Platform API")
                        .version("v1")
                        .description("""
                                REST API for the Smart Competitive Examination Platform.

                                Base path: `/api/v1`. All business endpoints are JWT-protected unless \
                                explicitly public. Requests may carry an `X-Request-Id` correlation \
                                header; if absent, the server generates one and returns it on the response.

                                Errors use a single standard envelope with a stable `code` per error entry.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token supplied as `Authorization: Bearer <token>`.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
