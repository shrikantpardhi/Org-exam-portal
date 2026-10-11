package com.company.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Smart Competitive Examination Platform backend.
 *
 * <p>The backend is a modular monolith: one deployable Spring Boot application with explicit
 * business-module boundaries (Identity, Academic, Content, Assessment, Delivery, Evaluation,
 * Analytics, Commerce, Communication, Administration, Infrastructure).
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md} and {@code specs/09-P0-Foundation-V1.5.md}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ExamPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamPlatformApplication.class, args);
    }
}
