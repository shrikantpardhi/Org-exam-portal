package com.company.exam.content.validation;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Content text normalization ({@code specs/12-P3-Content-V1.5.md §16, §28}).
 *
 * <p>Codes and option keys are trimmed and upper-cased; names are trimmed and length-checked;
 * optional text becomes null when blank.
 */
@Component
public class ContentTextNormalizer {

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

    public String normalizeOptionKey(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw validation("optionKey", "INVALID_OPTION_KEY", "optionKey is required");
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (key.length() > maxLength) {
            throw validation("optionKey", "INVALID_OPTION_KEY",
                    "optionKey must be at most " + maxLength + " characters");
        }
        return key;
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
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    public String normalizeConfiguration(String raw) {
        return normalizeOptional(raw);
    }

    private ValidationException validation(String field, String code, String message) {
        return new ValidationException("Validation failed", List.of(ApiError.of(field, code, message)));
    }
}
