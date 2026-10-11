package com.company.exam.identity.validation;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Identity input normalization ({@code specs/10-P1-Identity-V1.5.md §117, §118}).
 *
 * <p>Documented behaviour:
 * <ul>
 *   <li><b>Email</b> is trimmed and lower-cased before storage and lookup, so
 *       {@code John@example.com} and {@code john@example.com} are the same identity.</li>
 *   <li><b>Mobile</b> is trimmed, common separators (spaces, hyphens, parentheses) are removed,
 *       and a single leading {@code +} is preserved. The result must be 7-15 digits with an
 *       optional leading {@code +}. Multiple country formats are not silently accepted.</li>
 *   <li><b>Role and permission codes</b> are trimmed and upper-cased, matching the project's
 *       code format.</li>
 * </ul>
 */
@Component
public class IdentityNormalizer {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern MOBILE = Pattern.compile("^\\+?\\d{7,15}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s()\\-]");

    public String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("email", "INVALID_EMAIL", "Email address is invalid")));
        }
        return normalized;
    }

    public String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String normalized = SEPARATORS.matcher(mobile.trim()).replaceAll("");
        if (!MOBILE.matcher(normalized).matches()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("mobile", "INVALID_MOBILE", "Mobile number is invalid")));
        }
        return normalized;
    }

    public String normalizeRoleCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizePermissionCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
