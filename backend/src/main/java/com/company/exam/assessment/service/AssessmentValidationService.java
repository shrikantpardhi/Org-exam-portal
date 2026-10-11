package com.company.exam.assessment.service;

import com.company.exam.assessment.blueprint.BlueprintConfiguration;
import com.company.exam.assessment.blueprint.BlueprintConfigurationParser;
import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentQuestion;
import com.company.exam.assessment.entity.AssessmentSelectionRule;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.entity.BlueprintVersion;
import com.company.exam.assessment.repository.AssessmentQuestionRepository;
import com.company.exam.assessment.repository.AssessmentSelectionRuleRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.assessment.repository.BlueprintVersionRepository;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.api.ContentCatalog;
import com.company.exam.delivery.api.DeliveryPolicyCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessment validation ({@code specs/13-P4-Assessment-V1.5.md §37-§41, §84, §190}).
 *
 * <p>Read-only: it does not mutate persistence. Every check is reported as a structured issue so the
 * caller can present them together.
 */
@Service
public class AssessmentValidationService {

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentSelectionRuleRepository ruleRepository;
    private final BlueprintVersionRepository blueprintVersionRepository;
    private final BlueprintConfigurationParser configurationParser;
    private final ContentCatalog contentCatalog;
    private final DeliveryPolicyCatalog deliveryPolicyCatalog;

    public AssessmentValidationService(AssessmentVersionRepository versionRepository,
            AssessmentQuestionRepository questionRepository, AssessmentSelectionRuleRepository ruleRepository,
            BlueprintVersionRepository blueprintVersionRepository, BlueprintConfigurationParser configurationParser,
            ContentCatalog contentCatalog, DeliveryPolicyCatalog deliveryPolicyCatalog) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
        this.ruleRepository = ruleRepository;
        this.blueprintVersionRepository = blueprintVersionRepository;
        this.configurationParser = configurationParser;
        this.contentCatalog = contentCatalog;
        this.deliveryPolicyCatalog = deliveryPolicyCatalog;
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.ValidationResult validate(UUID versionId) {
        AssessmentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
        List<AssessmentDtos.ValidationIssue> errors = new ArrayList<>();

        BlueprintVersion blueprintVersion = blueprintVersionRepository.findById(version.getBlueprintVersionId())
                .orElse(null);
        BlueprintConfiguration configuration = null;
        if (blueprintVersion == null) {
            errors.add(issue("BLUEPRINT_VERSION_NOT_FOUND", "The referenced blueprint version does not exist"));
        } else {
            try {
                configuration = configurationParser.parse(blueprintVersion.getConfiguration());
                configurationParser.validate(configuration);
            } catch (RuntimeException invalid) {
                errors.add(issue("BLUEPRINT_INVALID", "The blueprint configuration is invalid"));
            }
        }

        List<AssessmentQuestion> questions = questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId);

        // Engine and delivery policy.
        if (version.getEngineType() == null) {
            errors.add(issue("ENGINE_CONFIGURATION_INVALID", "An engine type is required for publication"));
        }
        if (version.getDeliveryPolicyId() == null) {
            errors.add(issue("DELIVERY_POLICY_NOT_FOUND", "A delivery policy is required for publication"));
        } else if (!deliveryPolicyCatalog.exists(version.getDeliveryPolicyId())) {
            errors.add(issue("DELIVERY_POLICY_NOT_FOUND", "The delivery policy does not exist"));
        }
        if (version.getDurationMinutes() == null || version.getDurationMinutes() <= 0) {
            errors.add(issue("DURATION_INVALID", "A positive duration is required for publication"));
        }

        // Every question must be an eligible published version.
        for (AssessmentQuestion question : questions) {
            try {
                contentCatalog.requirePublishedQuestionVersion(question.getQuestionVersionId());
            } catch (RuntimeException notPublished) {
                errors.add(issue("QUESTION_VERSION_NOT_PUBLISHED",
                        "Question version " + question.getQuestionVersionId() + " is not published"));
            }
        }

        // Counts and sections.
        if (configuration != null && configuration.sections() != null) {
            int expected = configuration.sections().stream()
                    .mapToInt(section -> section.questionCount() == null ? 0 : section.questionCount()).sum();
            if (questions.size() != expected) {
                errors.add(issue("QUESTION_COUNT_MISMATCH",
                        "Assessment requires " + expected + " questions but " + questions.size() + " are configured"));
            }
            for (BlueprintConfiguration.Section section : configuration.sections()) {
                long actual = questions.stream().filter(q -> section.name().equals(q.getSectionName())).count();
                int configured = section.questionCount() == null ? 0 : section.questionCount();
                if (actual != configured) {
                    errors.add(issue("SECTION_COUNT_MISMATCH",
                            "Section " + section.name() + " requires " + configured + " but has " + actual));
                }
            }
        }

        // Marks.
        BigDecimal sum = BigDecimal.ZERO;
        for (AssessmentQuestion question : questions) {
            if (question.getMarks() == null || question.getMarks().signum() < 0) {
                errors.add(issue("TOTAL_MARKS_MISMATCH", "A question has invalid marks"));
            } else {
                sum = sum.add(question.getMarks());
            }
        }
        if (version.getTotalMarks() != null && version.getTotalMarks().compareTo(sum) != 0) {
            errors.add(issue("TOTAL_MARKS_MISMATCH",
                    "Configured total marks " + version.getTotalMarks() + " do not match " + sum));
        }

        // Unmaterialized selection rules are an incomplete draft.
        for (AssessmentSelectionRule rule : ruleRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId)) {
            if (rule.getMaterializedOn() == null) {
                errors.add(issue("QUESTION_COUNT_MISMATCH",
                        "Selection rule for section " + rule.getSectionName() + " has not been materialized"));
            }
        }

        return new AssessmentDtos.ValidationResult(errors.isEmpty(), errors);
    }

    private AssessmentDtos.ValidationIssue issue(String code, String message) {
        return new AssessmentDtos.ValidationIssue(code, message);
    }
}
