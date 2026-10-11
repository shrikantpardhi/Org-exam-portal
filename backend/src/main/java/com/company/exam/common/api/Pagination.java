package com.company.exam.common.api;

import org.springframework.data.domain.Page;

/**
 * Standard pagination metadata for collection responses.
 *
 * <p>Pages are zero-based, matching Spring Data's {@link Page} convention.
 *
 * <p>See {@code specs/03-Technical-Design-Document-V1.5.md §19}.
 */
public record Pagination(int page, int size, long totalElements, int totalPages) {

    public static Pagination from(Page<?> page) {
        return new Pagination(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
