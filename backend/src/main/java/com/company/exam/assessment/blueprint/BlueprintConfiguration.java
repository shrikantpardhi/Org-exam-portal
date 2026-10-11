package com.company.exam.assessment.blueprint;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The typed, declarative Blueprint configuration ({@code specs/13-P4-Assessment-V1.5.md §9, §10, §12}).
 *
 * <p>Configuration is data, never executable code. Because the platform ObjectMapper rejects
 * unknown properties, an attempt to smuggle {@code javascript}/{@code renderScript}/{@code component}
 * is refused at parse time. No database table is created per configuration section.
 */
public record BlueprintConfiguration(
        List<Section> sections,
        Navigation navigation,
        Timer timer,
        Submission submission,
        Validation validation,
        Presentation presentation,
        Calculator calculator,
        Language language) {

    public record Section(String name, Integer displayOrder, Integer questionCount, BigDecimal marks,
            Integer durationMinutes) {
    }

    public record Navigation(Boolean allowSectionSwitch, String mode) {
    }

    public record Timer(String mode, Boolean serverAuthoritative, Boolean autoSubmitOnExpiry) {
    }

    public record Submission(Boolean allowManualSubmit, Boolean autoSubmitOnExpiry, Boolean requireConfirmation) {
    }

    public record Validation(Boolean responseRequiredBeforeNext, Boolean sectionCompletionRequired) {
    }

    public record Presentation(String palettePosition, Boolean showProgress) {
    }

    public record Calculator(Boolean enabled) {
    }

    public record Language(List<UUID> languageIds, UUID defaultLanguageId) {
    }
}
