package com.company.exam.content.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Content application contract consumed by Analytics
 * ({@code specs/17-P8-Analytics-V1.5.md §12, §30, §37, §74}).
 *
 * <p>Analytics aggregates versioned question activity back to the parent Question and maps it to the
 * authoritative taxonomy from the Question table. It never guesses taxonomy from names ({@code §74})
 * and never reads authoring-only correctness.
 */
public interface QuestionTaxonomyCatalog {

    /** The authoritative taxonomy of a logical question. */
    record TaxonomyView(UUID questionId, UUID examId, UUID subjectId, UUID chapterId, UUID topicId,
            UUID subTopicId) {
    }

    /** Maps each question version to its parent Question ({@code §30}). */
    Map<UUID, UUID> questionIdByVersionId(Collection<UUID> questionVersionIds);

    /** The question versions of the given logical questions. */
    Map<UUID, List<UUID>> versionIdsByQuestionId(Collection<UUID> questionIds);

    /** The taxonomy of the given logical questions. */
    List<TaxonomyView> taxonomy(Collection<UUID> questionIds);
}
