package com.company.exam.evaluation.service;

import com.company.exam.delivery.api.SubmitAnswerKeyResolver;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluation-owned implementation of the submit-time answer-key contract
 * ({@code specs/15-P6-Evaluation-V1.5.md §25, §26}).
 *
 * <p>The resolution reads only published keys bound to the exact {@code AssessmentVersion} and
 * selects the highest version. It never invents a key and never defaults implicitly: when no key is
 * published it returns empty so the Delivery handoff skips the evaluation job and records an
 * operational warning instead.
 */
@Service
@Transactional(readOnly = true)
public class SubmitAnswerKeyResolverImpl implements SubmitAnswerKeyResolver {

    private final AnswerKeyRepository answerKeyRepository;

    public SubmitAnswerKeyResolverImpl(AnswerKeyRepository answerKeyRepository) {
        this.answerKeyRepository = answerKeyRepository;
    }

    @Override
    public Optional<UUID> resolveForSubmit(UUID assessmentVersionId) {
        return answerKeyRepository.findByAssessmentVersionIdOrderByVersionDesc(assessmentVersionId).stream()
                .filter(AnswerKey::isPublished)
                .map(AnswerKey::getId)
                .findFirst();
    }
}
