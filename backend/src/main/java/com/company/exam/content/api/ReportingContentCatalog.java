package com.company.exam.content.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Content read contract consumed by Reporting
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §50, §51, §52}).
 *
 * <p>Reporting reads question inventory, version lifecycle state and tag linkage through this
 * contract rather than through Content repositories ({@code §10}). It deliberately exposes no
 * answer-key or correctness data ({@code §34}).
 *
 * <p>Every method is bounded by {@code limit} (§7.6) and read-only (§7.3).
 */
public interface ReportingContentCatalog {

    /** One logical question with its authoritative taxonomy (§50). */
    record QuestionRow(UUID questionId, UUID examId, UUID subjectId, UUID chapterId, UUID topicId,
            UUID subTopicId, String status) {
    }

    /** One question version's lifecycle state (§51). */
    record QuestionVersionRow(UUID questionVersionId, UUID questionId, Integer version, String reviewStatus,
            boolean published, String difficulty) {
    }

    /** One question-to-tag association (§52). */
    record QuestionTagRow(UUID questionId, UUID tagId) {
    }

    /** Questions, optionally narrowed to an exam and/or subject, bounded by {@code limit}. */
    List<QuestionRow> questions(UUID examId, UUID subjectId, int limit);

    /** The versions of the given logical questions. */
    List<QuestionVersionRow> versionsOf(List<UUID> questionIds);

    /** Every question-to-tag association, bounded by {@code limit}. */
    List<QuestionTagRow> questionTags(int limit);
}
