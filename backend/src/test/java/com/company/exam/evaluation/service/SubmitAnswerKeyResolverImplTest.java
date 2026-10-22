package com.company.exam.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Resolves the submit-time answer key per {@code specs/15-P6-Evaluation-V1.5.md §25, §26}. */
@ExtendWith(MockitoExtension.class)
class SubmitAnswerKeyResolverImplTest {

    @Mock
    private AnswerKeyRepository answerKeyRepository;

    private SubmitAnswerKeyResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new SubmitAnswerKeyResolverImpl(answerKeyRepository);
    }

    @Test
    void returnsTheSinglePublishedKey() {
        UUID version = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        when(answerKeyRepository.findByAssessmentVersionIdOrderByVersionDesc(version))
                .thenReturn(List.of(published(version, 1, keyId)));

        assertThat(resolver.resolveForSubmit(version)).contains(keyId);
    }

    @Test
    void returnsTheHighestPublishedVersionWhenSeveralExist() {
        UUID version = UUID.randomUUID();
        UUID highestId = UUID.randomUUID();
        UUID v2Id = UUID.randomUUID();
        UUID v1Id = UUID.randomUUID();
        // Repository orders by version desc, so the highest version (3) is first.
        when(answerKeyRepository.findByAssessmentVersionIdOrderByVersionDesc(version))
                .thenReturn(List.of(published(version, 3, highestId),
                        published(version, 2, v2Id),
                        published(version, 1, v1Id)));

        assertThat(resolver.resolveForSubmit(version)).contains(highestId);
    }

    @Test
    void ignoresUnpublishedKeys() {
        UUID version = UUID.randomUUID();
        when(answerKeyRepository.findByAssessmentVersionIdOrderByVersionDesc(version))
                .thenReturn(List.of(draft(version, 5, UUID.randomUUID())));

        assertThat(resolver.resolveForSubmit(version)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoPublishedKeyExists() {
        UUID version = UUID.randomUUID();
        when(answerKeyRepository.findByAssessmentVersionIdOrderByVersionDesc(version)).thenReturn(List.of());

        assertThat(resolver.resolveForSubmit(version)).isEmpty();
    }

    private static AnswerKey published(UUID version, int versionNumber, UUID id) {
        return key(version, versionNumber, id, true);
    }

    private static AnswerKey draft(UUID version, int versionNumber, UUID id) {
        return key(version, versionNumber, id, false);
    }

    private static AnswerKey key(UUID version, int versionNumber, UUID id, boolean published) {
        AnswerKey key = new AnswerKey();
        key.setAssessmentVersionId(version);
        key.setVersion(versionNumber);
        key.setPublished(published);
        key.setId(id);
        return key;
    }
}
