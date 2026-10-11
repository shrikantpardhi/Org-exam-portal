package com.company.exam.assessment.service;

import com.company.exam.assessment.blueprint.BlueprintConfiguration;
import com.company.exam.assessment.blueprint.BlueprintConfigurationParser;
import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentSelectionRule;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.entity.BlueprintVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentSelectionRuleRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.assessment.repository.BlueprintVersionRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Assessment selection rule CRUD ({@code specs/13-P4-Assessment-V1.5.md §45, §63, §188}). */
@Service
public class AssessmentSelectionRuleService {

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentSelectionRuleRepository ruleRepository;
    private final BlueprintVersionRepository blueprintVersionRepository;
    private final BlueprintConfigurationParser configurationParser;
    private final AssessmentMapper mapper;
    private final SecurityEventLogger audit;

    public AssessmentSelectionRuleService(AssessmentVersionRepository versionRepository,
            AssessmentSelectionRuleRepository ruleRepository, BlueprintVersionRepository blueprintVersionRepository,
            BlueprintConfigurationParser configurationParser, AssessmentMapper mapper, SecurityEventLogger audit) {
        this.versionRepository = versionRepository;
        this.ruleRepository = ruleRepository;
        this.blueprintVersionRepository = blueprintVersionRepository;
        this.configurationParser = configurationParser;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssessmentDtos.SelectionRuleResponse> list(UUID versionId) {
        requireVersion(versionId);
        return ruleRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public AssessmentDtos.SelectionRuleResponse create(UUID versionId, AssessmentDtos.SelectionRuleCreateRequest request,
            UUID actorId) {
        AssessmentVersion version = requireMutable(versionId);
        validateSection(version, request.sectionName());
        validateRuleConfiguration(request.ruleConfiguration());
        if (ruleRepository.existsByAssessmentVersionIdAndSectionName(versionId, request.sectionName())) {
            throw new ConflictException("A selection rule already exists for this section");
        }
        AssessmentSelectionRule rule = new AssessmentSelectionRule();
        rule.setAssessmentVersionId(versionId);
        rule.setSectionName(request.sectionName());
        rule.setTargetCount(request.targetCount());
        rule.setDisplayOrder(request.displayOrder());
        rule.setRuleConfiguration(request.ruleConfiguration().toString());
        rule.setStatus(Status.ACTIVE);
        AssessmentSelectionRule saved = save(rule);
        audit.audit(actorId, "CREATE", "AssessmentSelectionRule", saved.getId(), "Created selection rule");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AssessmentDtos.SelectionRuleResponse update(UUID versionId, UUID ruleId,
            AssessmentDtos.SelectionRuleUpdateRequest request, UUID actorId) {
        requireMutable(versionId);
        AssessmentSelectionRule rule = ruleRepository.findByIdAndAssessmentVersionId(ruleId, versionId)
                .orElseThrow(() -> NotFoundException.of("Selection rule", ruleId));
        if (rule.getMaterializedOn() != null) {
            throw new ConflictException("A materialized selection rule cannot be changed");
        }
        if (request.targetCount() != null) {
            rule.setTargetCount(request.targetCount());
        }
        if (request.displayOrder() != null) {
            rule.setDisplayOrder(request.displayOrder());
        }
        if (request.ruleConfiguration() != null) {
            validateRuleConfiguration(request.ruleConfiguration());
            rule.setRuleConfiguration(request.ruleConfiguration().toString());
        }
        AssessmentSelectionRule saved = ruleRepository.save(rule);
        audit.audit(actorId, "UPDATE", "AssessmentSelectionRule", saved.getId(), "Updated selection rule");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID versionId, UUID ruleId, UUID actorId) {
        requireMutable(versionId);
        AssessmentSelectionRule rule = ruleRepository.findByIdAndAssessmentVersionId(ruleId, versionId)
                .orElseThrow(() -> NotFoundException.of("Selection rule", ruleId));
        ruleRepository.delete(rule);
        audit.audit(actorId, "DELETE", "AssessmentSelectionRule", ruleId, "Deleted selection rule");
    }

    /** A rule must not accidentally target the entire question bank ({@code §45}). */
    private void validateRuleConfiguration(JsonNode configuration) {
        boolean scoped = configuration.hasNonNull("examId") || configuration.hasNonNull("subjectId")
                || configuration.hasNonNull("topicId")
                || (configuration.has("tagIds") && configuration.get("tagIds").size() > 0);
        if (!scoped) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("ruleConfiguration", "RULE_NOT_SCOPED",
                            "At least one of examId, subjectId, topicId, or tagIds is required")));
        }
    }

    private void validateSection(AssessmentVersion version, String sectionName) {
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

    private AssessmentVersion requireVersion(UUID versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
    }

    private AssessmentVersion requireMutable(UUID versionId) {
        AssessmentVersion version = requireVersion(versionId);
        if (version.isPublished()) {
            throw new InvalidStateException("Published assessment versions are immutable");
        }
        return version;
    }

    private AssessmentSelectionRule save(AssessmentSelectionRule rule) {
        try {
            return ruleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A selection rule already exists for this section");
        }
    }
}
