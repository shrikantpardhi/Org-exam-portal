package com.company.exam.assessment.blueprint;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses and validates Blueprint JSONB configuration into the typed model
 * ({@code specs/13-P4-Assessment-V1.5.md §11, §85}).
 *
 * <p>The parsing pipeline is JSONB -&gt; typed deserialization -&gt; field validation -&gt; cross-field
 * validation. The platform ObjectMapper rejects unknown fields, so uncontrolled or executable
 * configuration never reaches business logic.
 */
@Component
public class BlueprintConfigurationParser {

    private final ObjectMapper objectMapper;

    public BlueprintConfigurationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Parses configuration, failing with a controlled error on malformed or unknown content. */
    public BlueprintConfiguration parse(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(configuration, BlueprintConfiguration.class);
        } catch (Exception invalid) {
            throw new ValidationException("Blueprint configuration is invalid",
                    List.of(ApiError.of("configuration", "BLUEPRINT_INVALID",
                            "Configuration is not valid, or contains unsupported or executable fields")));
        }
    }

    /**
     * Validates a parsed configuration for publication ({@code §12-§18, §85}).
     *
     * <p>Structural rules: at least one section; each section has a non-blank name, a unique
     * ascending display order, a positive question count, non-negative marks, and non-negative
     * duration.
     */
    public void validate(BlueprintConfiguration configuration) {
        List<ApiError> errors = new ArrayList<>();
        if (configuration == null || configuration.sections() == null || configuration.sections().isEmpty()) {
            errors.add(ApiError.of("configuration", "BLUEPRINT_INVALID", "At least one section is required"));
            throw new ValidationException("Blueprint configuration is invalid", errors);
        }
        Set<Integer> orders = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (BlueprintConfiguration.Section section : configuration.sections()) {
            if (section.name() == null || section.name().isBlank()) {
                errors.add(ApiError.of("sections", "SECTION_NAME_INVALID", "Section name is required"));
            } else if (!names.add(section.name())) {
                errors.add(ApiError.of("sections", "SECTION_NAME_DUPLICATE", "Section names must be unique"));
            }
            if (section.displayOrder() == null || section.displayOrder() <= 0) {
                errors.add(ApiError.of("sections", "SECTION_ORDER_INVALID", "Section displayOrder must be positive"));
            } else if (!orders.add(section.displayOrder())) {
                errors.add(ApiError.of("sections", "SECTION_ORDER_DUPLICATE", "Section displayOrder must be unique"));
            }
            if (section.questionCount() == null || section.questionCount() <= 0) {
                errors.add(ApiError.of("sections", "SECTION_COUNT_INVALID", "Section questionCount must be positive"));
            }
            if (section.marks() != null && section.marks().signum() < 0) {
                errors.add(ApiError.of("sections", "SECTION_MARKS_INVALID", "Section marks must not be negative"));
            }
            if (section.durationMinutes() != null && section.durationMinutes() < 0) {
                errors.add(ApiError.of("sections", "SECTION_DURATION_INVALID",
                        "Section durationMinutes must not be negative"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Blueprint configuration is invalid", errors);
        }
    }
}
