package com.company.exam.identity.validation;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Password policy ({@code specs/10-P1-Identity-V1.5.md §23}).
 *
 * <p>The frozen design requires a policy but does not freeze exact strength rules. P1 defines:
 * minimum length 12, maximum length 128 (a storage-bound technical limit, not an arbitrary
 * strength rule), a small denylist of obvious passwords, and rejection of the user's own email.
 * Long secure passwords that satisfy the minimum are never rejected for arbitrary reasons.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> OBVIOUS_PASSWORDS = Set.of(
            "password", "password123", "password1234", "123456789012", "qwertyuiop12",
            "letmein12345", "administrator", "changeme1234", "welcome12345");

    /**
     * Validates a raw password against the policy.
     *
     * @param rawPassword the candidate password
     * @param email       the owning account email, rejected if used as the password (may be null)
     * @throws ValidationException with a field-level {@code INVALID_PASSWORD} error when invalid
     */
    public void validate(String rawPassword, String email) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw invalid("Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        String lower = rawPassword.toLowerCase(Locale.ROOT);
        if (OBVIOUS_PASSWORDS.contains(lower)) {
            throw invalid("Password is too easily guessed");
        }
        if (email != null && lower.equals(email.toLowerCase(Locale.ROOT))) {
            throw invalid("Password must not be the account email address");
        }
    }

    private ValidationException invalid(String message) {
        return new ValidationException("Validation failed",
                List.of(ApiError.of("newPassword", "INVALID_PASSWORD", message)));
    }
}
