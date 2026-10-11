package com.company.exam.common.web;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Query hygiene for collection endpoints, shared by all modules
 * ({@code specs/10-P1-Identity-V1.5.md §136-§138}, {@code specs/11-P2-Academic-V1.5.md §42-§44}).
 *
 * <p>Caps page size and refuses client-supplied sort fields that are not on an explicit allowlist,
 * so arbitrary database columns never reach query construction.
 */
public final class QuerySupport {

    /** Maximum page size, to prevent accidental huge responses. */
    public static final int MAX_PAGE_SIZE = 100;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private QuerySupport() {
    }

    public static Pageable sanitize(Pageable pageable, Set<String> allowedSortFields) {
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort();
        List<Sort.Order> orders = sort.stream()
                .filter(order -> allowedSortFields.contains(order.getProperty()))
                .toList();
        if (sort.isSorted() && orders.isEmpty()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("sort", "INVALID_SORT", "Unsupported sort field")));
        }
        return PageRequest.of(pageable.getPageNumber(), size, Sort.by(orders));
    }
}
