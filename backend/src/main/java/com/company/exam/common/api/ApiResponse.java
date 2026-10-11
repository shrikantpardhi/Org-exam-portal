package com.company.exam.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Standard success envelope returned by the API.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Request processed successfully",
 *   "data": {}
 * }
 * </pre>
 *
 * <p>Collection responses additionally carry a {@code pagination} object. The field is omitted
 * when {@code null}, so single-resource responses keep the documented shape.
 *
 * <p>See {@code specs/03-Technical-Design-Document-V1.5.md §19} and
 * {@code specs/09-P0-Foundation-V1.5.md §13}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "message", "data", "pagination"})
public record ApiResponse<T>(boolean success, String message, T data, Pagination pagination) {

    public static final String DEFAULT_MESSAGE = "Request processed successfully";
    public static final String DEFAULT_COLLECTION_MESSAGE = "Records retrieved successfully";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, DEFAULT_MESSAGE, data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<List<T>> paged(PageResponse<T> page) {
        return paged(DEFAULT_COLLECTION_MESSAGE, page);
    }

    public static <T> ApiResponse<List<T>> paged(String message, PageResponse<T> page) {
        return new ApiResponse<>(true, message, page.content(), page.pagination());
    }
}
