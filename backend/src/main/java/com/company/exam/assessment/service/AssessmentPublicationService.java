package com.company.exam.assessment.service;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentQuestion;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentQuestionRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessment publication ({@code specs/13-P4-Assessment-V1.5.md §53, §75, §86, §192}).
 *
 * <p>Publication is an explicit, validating business action. It is blocked while the assessment is
 * incomplete, and it is atomic: totals are recomputed from the materialized questions and the
 * version becomes immutable. Engine type and delivery policy are required and fixed at publication.
 */
@Service
public class AssessmentPublicationService {

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentValidationService validationService;
    private final AssessmentMapper mapper;
    private final SecurityEventLogger audit;

    public AssessmentPublicationService(AssessmentVersionRepository versionRepository,
            AssessmentQuestionRepository questionRepository, AssessmentValidationService validationService,
            AssessmentMapper mapper, SecurityEventLogger audit) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
        this.validationService = validationService;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public AssessmentDtos.AssessmentVersionResponse publish(UUID versionId, UUID actorId) {
        AssessmentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
        if (version.isPublished()) {
            throw new ConflictException("This assessment version is already published");
        }

        AssessmentDtos.ValidationResult result = validationService.validate(versionId);
        if (!result.valid()) {
            throw new BusinessException("Assessment is not ready for publication", result.errors().stream()
                    .map(issue -> ApiError.of(null, issue.code(), issue.message()))
                    .toList());
        }

        List<AssessmentQuestion> questions = questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId);
        BigDecimal totalMarks = questions.stream().map(AssessmentQuestion::getMarks)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        version.setTotalQuestions(questions.size());
        version.setTotalMarks(totalMarks);
        version.setPublished(true);
        version.setPublishedBy(actorId);
        version.setPublishedOn(Instant.now());
        AssessmentVersion saved = versionRepository.save(version);
        audit.audit(actorId, "PUBLISH", "AssessmentVersion", saved.getId(), "Published assessment version");
        return mapper.toResponse(saved);
    }
}
