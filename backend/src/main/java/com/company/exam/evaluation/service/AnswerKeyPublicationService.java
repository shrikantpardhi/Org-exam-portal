package com.company.exam.evaluation.service;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.AnswerKeyDetail;
import com.company.exam.evaluation.exception.AnswerKeyIncompleteException;
import com.company.exam.evaluation.mapper.EvaluationMapper;
import com.company.exam.evaluation.repository.AnswerKeyDetailRepository;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answer key publication ({@code specs/15-P6-Evaluation-V1.5.md §28, §29, §52, §120}).
 *
 * <p>Publication verifies the source assessment is published and that the key is complete: exactly
 * one detail row per question of the bound assessment version. Once published the key is immutable.
 */
@Service
public class AnswerKeyPublicationService {

    private final AnswerKeyRepository answerKeyRepository;
    private final AnswerKeyDetailRepository detailRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final EvaluationMapper mapper;
    private final SecurityEventLogger audit;

    public AnswerKeyPublicationService(AnswerKeyRepository answerKeyRepository,
            AnswerKeyDetailRepository detailRepository, AssessmentDefinitionCatalog assessmentCatalog,
            EvaluationMapper mapper, SecurityEventLogger audit) {
        this.answerKeyRepository = answerKeyRepository;
        this.detailRepository = detailRepository;
        this.assessmentCatalog = assessmentCatalog;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public EvaluationDtos.AnswerKeyResponse publish(UUID answerKeyId, UUID actorId) {
        AnswerKey key = answerKeyRepository.findById(answerKeyId)
                .orElseThrow(() -> NotFoundException.of("Answer key", answerKeyId));
        if (key.isPublished()) {
            return mapper.toResponse(key);
        }

        AssessmentDefinitionCatalog.AssessmentScoringView version =
                assessmentCatalog.requireScoringContext(key.getAssessmentVersionId());
        if (!version.published()) {
            throw new BusinessException("Assessment version is not published",
                    List.of(ApiError.of("assessmentVersionId", "ASSESSMENT_VERSION_NOT_PUBLISHED",
                            "Only a published assessment version can have a published answer key")));
        }

        Set<UUID> expected = assessmentCatalog.questions(key.getAssessmentVersionId()).stream()
                .map(AssessmentDefinitionCatalog.AssessmentQuestionView::questionVersionId)
                .collect(Collectors.toSet());
        if (expected.isEmpty()) {
            throw new AnswerKeyIncompleteException("The bound assessment version has no questions");
        }
        Set<UUID> covered = detailRepository.findByAnswerKeyId(answerKeyId).stream()
                .map(AnswerKeyDetail::getQuestionVersionId)
                .collect(Collectors.toSet());
        Set<UUID> missing = expected.stream().filter(questionVersionId -> !covered.contains(questionVersionId))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new AnswerKeyIncompleteException(
                    "The answer key is missing a detail for " + missing.size() + " question(s)");
        }

        key.setPublished(true);
        key.setPublishedBy(actorId);
        key.setPublishedOn(Instant.now());
        AnswerKey saved = answerKeyRepository.save(key);
        audit.audit(actorId, "PUBLISH", "AnswerKey", saved.getId(),
                "Published answer key version " + saved.getVersion());
        return mapper.toResponse(saved);
    }
}
