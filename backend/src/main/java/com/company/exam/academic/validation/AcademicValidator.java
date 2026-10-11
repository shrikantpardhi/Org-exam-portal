package com.company.exam.academic.validation;

import com.company.exam.academic.exception.AcademicDescendantsExistException;
import com.company.exam.academic.exception.AcademicParentNotEligibleException;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Centralized Academic business validation ({@code specs/11-P2-Academic-V1.5.md §12, §13, §14, §16}).
 *
 * <p>Holds the rules that must not be duplicated across controllers and repositories: code and name
 * normalization, parent eligibility, descendant safety on archive, and lifecycle transitions.
 */
@Component
public class AcademicValidator {

    /**
     * Normalizes a business code: trimmed and upper-cased. The schema document leaves the exact
     * convention open; this consistent normalization is documented and covered by validation tests
     * ({@code §16}). Database lengths are unchanged.
     */
    public String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validation("code", "INVALID_CODE", "Code is required");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeName(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw validation("name", "INVALID_NAME", "Name is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw validation("name", "INVALID_NAME", "Name must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    public String normalizeCode(String raw, int maxLength) {
        String code = normalizeCode(raw);
        if (code.length() > maxLength) {
            throw validation("code", "INVALID_CODE", "Code must be at most " + maxLength + " characters");
        }
        return code;
    }

    /** Descriptions are optional TEXT ({@code §18}); blank becomes null. */
    public String normalizeDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    /**
     * A child may only be created under an ACTIVE parent ({@code §13}).
     * INACTIVE and ARCHIVED parents are rejected.
     */
    public void requireParentEligible(Status parentStatus, String parentLabel) {
        if (parentStatus == Status.ARCHIVED) {
            throw new AcademicParentNotEligibleException("Cannot create a child under an archived " + parentLabel);
        }
        if (parentStatus == Status.INACTIVE) {
            throw new AcademicParentNotEligibleException("Cannot create a child under an inactive " + parentLabel);
        }
    }

    /** Archiving is blocked while non-archived descendants exist; P2 does not cascade silently. */
    public void requireNoActiveDescendants(long nonArchivedDescendants, String label) {
        if (nonArchivedDescendants > 0) {
            throw new AcademicDescendantsExistException(
                    "Cannot archive " + label + " while it has " + nonArchivedDescendants
                            + " non-archived descendant(s)");
        }
    }

    /**
     * Update endpoints may deactivate or reactivate ({@code ACTIVE}/{@code INACTIVE}) but not
     * archive: archiving must go through the archive operation so the descendant guard applies.
     */
    public Status validateLifecycleStatus(Status requested) {
        if (requested == null) {
            return null;
        }
        if (requested == Status.ARCHIVED) {
            throw new InvalidStateException("Use the archive operation to archive this record");
        }
        return requested;
    }

    private ValidationException validation(String field, String code, String message) {
        return new ValidationException("Validation failed", List.of(ApiError.of(field, code, message)));
    }
}
