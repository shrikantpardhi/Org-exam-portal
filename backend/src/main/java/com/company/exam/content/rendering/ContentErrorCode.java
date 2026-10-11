package com.company.exam.content.rendering;

/**
 * Structured content validation error codes
 * ({@code specs/12-P3-Content-V1.5.md §59}).
 *
 * <p>These are resource and content errors returned by the backend, not frontend-only strings.
 */
public enum ContentErrorCode {

    INVALID_CONTENT_FORMAT,
    RAW_HTML_NOT_ALLOWED,
    INVALID_MATH_DELIMITER,
    UNSUPPORTED_MATH_DELIMITER,
    INVALID_ASSET_SHORTCODE,
    ASSET_NOT_FOUND,
    ASSET_VERSION_MISMATCH,
    CONTENT_TOO_LARGE,
    INVALID_MARKDOWN
}
