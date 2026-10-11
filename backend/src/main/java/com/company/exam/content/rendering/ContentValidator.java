package com.company.exam.content.rendering;

import com.company.exam.common.api.ApiError;
import com.company.exam.content.config.ContentProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates canonical {@code MARKDOWN_LATEX_V1} content
 * ({@code specs/12-P3-Content-V1.5.md §12-§15, §58, §59}).
 *
 * <p>Enforces the security contract: no raw HTML, no executable MDX, only the approved math
 * delimiters, bounded size, and well-formed asset shortcodes. Currency such as {@code $500} is
 * ordinary text; {@code $...$} is never treated as math.
 */
@Component
public class ContentValidator {

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--");
    private static final Pattern HTML_TAG =
            Pattern.compile("<\\s*/?\\s*[a-zA-Z][a-zA-Z0-9-]*(\\s[^<>]*)?/?\\s*>");
    private static final Pattern MDX_STATEMENT = Pattern.compile("(?m)^\\s*(import|export)\\s");
    private static final Pattern DOUBLE_DOLLAR = Pattern.compile("\\$\\$");
    private static final Pattern ASSET_SHORTCODE = Pattern.compile("\\[asset:\\s*([^\\]]*)\\]");
    private static final Pattern ASSET_OPENER = Pattern.compile("\\[asset:");

    private final ContentProperties properties;

    public ContentValidator(ContentProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates content and returns the referenced asset ids.
     *
     * <p>Blank content is accepted here (drafts may be incomplete); presence is enforced by the
     * submission and publication gates.
     */
    public List<UUID> validate(String content, String field) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<ApiError> errors = new ArrayList<>();

        if (content.length() > properties.getMaxContentLength()) {
            errors.add(ApiError.of(field, ContentErrorCode.CONTENT_TOO_LARGE.name(),
                    "Content exceeds the maximum length of " + properties.getMaxContentLength() + " characters"));
        }
        if (HTML_COMMENT.matcher(content).find() || HTML_TAG.matcher(content).find()) {
            errors.add(ApiError.of(field, ContentErrorCode.RAW_HTML_NOT_ALLOWED.name(),
                    "Raw HTML is not allowed in canonical content"));
        }
        if (MDX_STATEMENT.matcher(content).find()) {
            errors.add(ApiError.of(field, ContentErrorCode.INVALID_MARKDOWN.name(),
                    "Executable MDX is not allowed in canonical content"));
        }
        if (DOUBLE_DOLLAR.matcher(content).find()) {
            errors.add(ApiError.of(field, ContentErrorCode.UNSUPPORTED_MATH_DELIMITER.name(),
                    "$$...$$ is not a supported math delimiter; use \\[ ... \\]"));
        }
        if (count(content, "\\(") != count(content, "\\)")
                || count(content, "\\[") != count(content, "\\]")) {
            errors.add(ApiError.of(field, ContentErrorCode.INVALID_MATH_DELIMITER.name(),
                    "Unbalanced math delimiters: use \\( ... \\) or \\[ ... \\]"));
        }

        List<UUID> assetIds = new ArrayList<>();
        Matcher shortcodes = ASSET_SHORTCODE.matcher(content);
        int shortcodeCount = 0;
        while (shortcodes.find()) {
            shortcodeCount++;
            String raw = shortcodes.group(1).trim();
            try {
                assetIds.add(UUID.fromString(raw));
            } catch (IllegalArgumentException notAUuid) {
                errors.add(ApiError.of(field, ContentErrorCode.INVALID_ASSET_SHORTCODE.name(),
                        "Asset shortcode must contain a valid UUID: [asset: <uuid>]"));
            }
        }
        if (count(content, "[asset:") > shortcodeCount) {
            errors.add(ApiError.of(field, ContentErrorCode.INVALID_ASSET_SHORTCODE.name(),
                    "Malformed asset shortcode: use [asset: <uuid>]"));
        }

        if (!errors.isEmpty()) {
            throw new ContentValidationException(errors);
        }
        return assetIds;
    }

    private int count(String haystack, String needle) {
        int total = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            total++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return total;
    }
}
