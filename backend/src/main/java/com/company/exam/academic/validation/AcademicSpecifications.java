package com.company.exam.academic.validation;

import com.company.exam.common.enums.Status;
import jakarta.persistence.criteria.Predicate;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable, parameterized specifications for Academic list queries
 * ({@code specs/11-P2-Academic-V1.5.md §43, §44}).
 *
 * <p>All values are bound as parameters; no SQL is built by string concatenation, and sort fields
 * are allow-listed by the controller before they reach here.
 */
public final class AcademicSpecifications {

    private AcademicSpecifications() {
    }

    public static <T> Specification<T> status(Status status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static <T> Specification<T> search(String term, String... fields) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
            Predicate[] predicates = Arrays.stream(fields)
                    .map(field -> cb.like(cb.lower(root.get(field)), pattern))
                    .toArray(Predicate[]::new);
            return cb.or(predicates);
        };
    }

    public static <T> Specification<T> parentEquals(String field, UUID parentId) {
        return (root, query, cb) -> cb.equal(root.get(field), parentId);
    }
}
