package com.company.exam.evaluation.service;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.mapper.EvaluationMapper;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answer key lifecycle ({@code specs/15-P6-Evaluation-V1.5.md §25-§28, §52, §88}).
 *
 * <p>A new key is always a new version: version numbering is monotonic per assessment version, and a
 * published key is never edited ({@code §27, §52}).
 */
@Service
public class AnswerKeyService {

    private final AnswerKeyRepository answerKeyRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final EvaluationMapper mapper;
    private final SecurityEventLogger audit;

    public AnswerKeyService(AnswerKeyRepository answerKeyRepository, AssessmentDefinitionCatalog assessmentCatalog,
            EvaluationMapper mapper, SecurityEventLogger audit) {
        this.answerKeyRepository = answerKeyRepository;
        this.assessmentCatalog = assessmentCatalog;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EvaluationDtos.AnswerKeyResponse> list(UUID assessmentVersionId) {
        List<AnswerKey> keys = assessmentVersionId == null
                ? answerKeyRepository.findAll()
                : answerKeyRepository.findByAssessmentVersionIdOrderByVersionDesc(assessmentVersionId);
        return keys.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EvaluationDtos.AnswerKeyResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public EvaluationDtos.AnswerKeyResponse create(EvaluationDtos.AnswerKeyCreateRequest request, UUID actorId) {
        AssessmentDefinitionCatalog.AssessmentScoringView version =
                assessmentCatalog.requireScoringContext(request.assessmentVersionId());
        if (!version.published()) {
            throw new BusinessException("Assessment version is not published",
                    List.of(ApiError.of("assessmentVersionId", "ASSESSMENT_VERSION_NOT_PUBLISHED",
                            "An answer key can only be created for a published assessment version")));
        }
        AnswerKey key = new AnswerKey();
        key.setAssessmentVersionId(request.assessmentVersionId());
        key.setVersion(answerKeyRepository.findMaxVersion(request.assessmentVersionId()) + 1);
        key.setPublished(false);
        try {
            AnswerKey saved = answerKeyRepository.saveAndFlush(key);
            audit.audit(actorId, "CREATE", "AnswerKey", saved.getId(), "Created answer key version "
                    + saved.getVersion());
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("An answer key with that version already exists");
        }
    }

    /** Loads an answer key or throws a controlled not-found error. */
    @Transactional(readOnly = true)
    public AnswerKey require(UUID id) {
        return answerKeyRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Answer key", id));
    }
}
