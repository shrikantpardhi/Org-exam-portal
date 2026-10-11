package com.company.exam.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A page of mapped response items plus its pagination metadata.
 *
 * <p>Keeps the mapping from a persistence {@link Page} to API DTOs in one place so that
 * controllers never expose entities.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §44} (DTO boundary rule).
 *
 * @param content    the mapped items for the requested page
 * @param pagination pagination metadata
 */
public record PageResponse<T>(List<T> content, Pagination pagination) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), Pagination.from(page));
    }
}
