package com.company.exam.assessment.service;

import com.company.exam.assessment.blueprint.BlueprintConfiguration;
import com.company.exam.assessment.blueprint.BlueprintConfigurationParser;
import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentQuestion;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.entity.BlueprintVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentQuestionRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.assessment.repository.BlueprintVersionRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.delivery.api.DeliveryPolicyCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assessment question workflows ({@code specs/13-P4-Assessment-V1.5.md §58-§62, §184, §185}). */
@Service
public class AssessmentQuestionService {

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final BlueprintVersionRepository blueprintVersionRepository;
    private final BlueprintConfigurationParser configurationParser;
    private final AssessmentMapper mapper;
    private final SecurityEventLogger audit;

    public AssessmentQuestionService(AssessmentVersionRepository versionRepository,
            AssessmentQuestionRepository questionRepository, BlueprintVersionRepository blueprintVersionRepository,
            BlueprintConfigurationParser configurationParser, AssessmentMapper mapper, SecurityEventLogger audit) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
        this.blueprintVersionRepository = blueprintVersionRepository;
        this.configurationParser = configurationParser;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssessmentDtos.AssessmentQuestionResponse> list(UUID versionId) {
        requireVersion(versionId);
        return questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public AssessmentDtos.AssessmentQuestionResponse add(UUID versionId, AssessmentDtos.AddAssessmentQuestionRequest request,
            UUID actorId) {
        AssessmentVersion version = requireMutable(versionId);
        validateSection(version, request.sectionName());
        if (questionRepository.existsByAssessmentVersionIdAndQuestionVersionId(versionId, request.questionVersionId())) {
            throw new ConflictException("This question version is already part of the assessment version");
        }
        AssessmentQuestion question = new AssessmentQuestion();
        question.setAssessmentVersionId(versionId);
        question.setQuestionVersionId(request.questionVersionId());
        question.setDisplayOrder(request.displayOrder());
        question.setSectionName(request.sectionName());
        question.setMarks(request.marks());
        question.setNegativeMarks(request.negativeMarks());
        question.setStatus(Status.ACTIVE);
        AssessmentQuestion saved = save(question);
        audit.audit(actorId, "CREATE", "AssessmentQuestion", saved.getId(), "Added question to assessment version");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AssessmentDtos.AssessmentQuestionResponse update(UUID versionId, UUID assessmentQuestionId,
            AssessmentDtos.UpdateAssessmentQuestionRequest request, UUID actorId) {
        AssessmentVersion version = requireMutable(versionId);
        AssessmentQuestion question = questionRepository.findByIdAndAssessmentVersionId(assessmentQuestionId, versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment question", assessmentQuestionId));
        if (request.sectionName() != null) {
            validateSection(version, request.sectionName());
            question.setSectionName(request.sectionName());
        }
        if (request.displayOrder() != null) {
            question.setDisplayOrder(request.displayOrder());
        }
        if (request.marks() != null) {
            question.setMarks(request.marks());
        }
        if (request.negativeMarks() != null) {
            question.setNegativeMarks(request.negativeMarks());
        }
        AssessmentQuestion saved = questionRepository.save(question);
        audit.audit(actorId, "UPDATE", "AssessmentQuestion", saved.getId(), "Updated assessment question");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void remove(UUID versionId, UUID assessmentQuestionId, UUID actorId) {
        requireMutable(versionId);
        AssessmentQuestion question = questionRepository.findByIdAndAssessmentVersionId(assessmentQuestionId, versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment question", assessmentQuestionId));
        questionRepository.delete(question);
        audit.audit(actorId, "DELETE", "AssessmentQuestion", assessmentQuestionId, "Removed assessment question");
    }

    @Transactional
    public void reorder(UUID versionId, AssessmentDtos.ReorderRequest request, UUID actorId) {
        requireMutable(versionId);
        List<AssessmentQuestion> existing = questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId);
        Set<UUID> existingIds = new HashSet<>(existing.stream().map(AssessmentQuestion::getId).toList());
        Set<UUID> seenIds = new HashSet<>();
        Set<Integer> seenOrders = new HashSet<>();
        for (AssessmentDtos.QuestionOrder order : request.questionOrders()) {
            if (!existingIds.contains(order.assessmentQuestionId())) {
                throw new ValidationException("Validation failed",
                        List.of(ApiError.of("questionOrders", "QUESTION_NOT_IN_VERSION",
                                "A question id does not belong to this assessment version")));
            }
            if (!seenIds.add(order.assessmentQuestionId())) {
                throw new ValidationException("Validation failed",
                        List.of(ApiError.of("questionOrders", "DUPLICATE_QUESTION_ID", "Duplicate question id")));
            }
            if (!seenOrders.add(order.displayOrder())) {
                throw new ValidationException("Validation failed",
                        List.of(ApiError.of("questionOrders", "DUPLICATE_DISPLAY_ORDER", "Duplicate display order")));
            }
        }
        for (AssessmentQuestion question : existing) {
            request.questionOrders().stream()
                    .filter(order -> order.assessmentQuestionId().equals(question.getId()))
                    .findFirst()
                    .ifPresent(order -> question.setDisplayOrder(order.displayOrder()));
        }
        questionRepository.saveAll(existing);
        audit.audit(actorId, "REORDER", "AssessmentQuestion", versionId, "Reordered assessment questions");
    }

    AssessmentVersion requireMutable(UUID versionId) {
        AssessmentVersion version = requireVersion(versionId);
        if (version.isPublished()) {
            throw new InvalidStateException("Published assessment versions are immutable");
        }
        return version;
    }

    AssessmentVersion requireVersion(UUID versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
    }

    void validateSection(AssessmentVersion version, String sectionName) {
        BlueprintVersion blueprintVersion = blueprintVersionRepository.findById(version.getBlueprintVersionId())
                .orElseThrow(() -> NotFoundException.of("Blueprint version", version.getBlueprintVersionId()));
        BlueprintConfiguration configuration = configurationParser.parse(blueprintVersion.getConfiguration());
        configurationParser.validate(configuration);
        boolean known = configuration.sections().stream().anyMatch(section -> section.name().equals(sectionName));
        if (!known) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("sectionName", "QUESTION_SECTION_INVALID",
                            "Section is not present in the blueprint configuration")));
        }
    }

    private AssessmentQuestion save(AssessmentQuestion question) {
        try {
            return questionRepository.saveAndFlush(question);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("This question version is already part of the assessment version");
        }
    }
}
