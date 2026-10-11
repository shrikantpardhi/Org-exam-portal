package com.company.exam.content.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The explicit Content application contract consumed by Assessment
 * ({@code specs/13-P4-Assessment-V1.5.md §81, §82}).
 *
 * <p>Assessment verifies and selects published question versions through this contract rather than
 * reaching into Content repositories, preserving the modular-monolith boundary.
 */
public interface ContentCatalog {

    /** A summary of an exact published question version. */
    record PublishedQuestionVersion(UUID questionVersionId, UUID questionId, UUID languageId, String difficulty,
            BigDecimal marks, BigDecimal negativeMarks, String templateCode, boolean published) {
    }

    /** Selection criteria for rule-based authoring ({@code §45}). */
    record SelectionCriteria(String templateCode, UUID examId, UUID subjectId, UUID chapterId, UUID topicId,
            List<String> difficulties, List<UUID> tagIds, UUID languageId, Boolean publishedOnly,
            List<UUID> excludeQuestionIds, List<UUID> excludeQuestionVersionIds) {
    }

    /**
     * Returns the exact published question version, or throws a controlled error if it does not
     * exist or is not published.
     */
    PublishedQuestionVersion requirePublishedQuestionVersion(UUID questionVersionId);

    /** Returns the eligible published question versions matching the criteria. */
    List<PublishedQuestionVersion> findEligible(SelectionCriteria criteria);

    /** Canonical content plus its server-rendered HTML for preview ({@code 13-P4 §57}). */
    record PreviewContent(String contentFormat, String content, String renderedContent) {
    }

    PreviewContent previewContent(UUID questionVersionId);

    /** One authorized option in the runtime snapshot, deliberately without {@code isCorrect}. */
    record RuntimeOption(UUID optionId, String optionKey, Integer displayOrder, String content,
            String renderedContent) {
    }

    /**
     * Student-safe runtime question content ({@code specs/14-P5-Delivery-V1.5.md §17}).
     *
     * <p>Uses MARKDOWN_LATEX_V1 and never exposes {@code isCorrect}, answer keys, review metadata, or
     * raw asset paths.
     */
    record RuntimeQuestion(UUID questionVersionId, String contentFormat, boolean requiresLatexParsing,
            String content, String renderedContent, List<RuntimeOption> options) {
    }

    /** Returns the student-safe runtime content of an exact question version. */
    RuntimeQuestion runtimeQuestion(UUID questionVersionId);
}
