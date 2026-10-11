package com.company.exam.content.rendering;

import java.util.List;
import java.util.UUID;

/**
 * The result of rendering canonical content ({@code specs/12-P3-Content-V1.5.md §126, §131}).
 *
 * @param html              sanitized HTML with math placeholders and resolved asset references
 * @param contentFormat     always {@code MARKDOWN_LATEX_V1}
 * @param rendererVersion   stable renderer version for cache identity and reproducibility
 * @param referencedAssetIds asset ids referenced by the content
 */
public record RenderedContent(String html, String contentFormat, String rendererVersion,
        List<UUID> referencedAssetIds) {
}
