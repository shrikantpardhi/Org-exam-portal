package com.company.exam.assessment.validation;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Blueprint/Assessment code and name normalization. */
@Component
public class AssessmentTextNormalizer {

    public String normalizeCode(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw validation("code", "INVALID_CODE", "Code is required");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.length() > maxLength) {
            throw validation("code", "INVALID_CODE", "Code must be at most " + maxLength + " characters");
        }
        return code;
    }

    public String normalizeName(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw validation("name", "INVALID_NAME", "Name is required");
        }
        String name = raw.trim();
        if (name.length() > maxLength) {
            throw validation("name", "INVALID_NAME", "Name must be at most " + maxLength + " characters");
        }
        return name;
    }

    public String normalizeOptional(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private ValidationException validation(String field, String code, String message) {
        return new ValidationException("Validation failed", List.of(ApiError.of(field, code, message)));
    }
}
