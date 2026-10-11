package com.company.exam.assessment.service;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.assessment.entity.AssessmentQuestion;
import com.company.exam.assessment.entity.AssessmentSchedule;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.repository.AssessmentQuestionRepository;
import com.company.exam.assessment.repository.AssessmentScheduleRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AssessmentDefinitionCatalog} — the published P4 handoff for Delivery
 * ({@code specs/14-P5-Delivery-V1.5.md §16}).
 */
@Service
public class AssessmentDefinitionCatalogService implements AssessmentDefinitionCatalog {

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentScheduleRepository scheduleRepository;
    private final AssessmentQuestionRepository questionRepository;

    public AssessmentDefinitionCatalogService(AssessmentVersionRepository versionRepository,
            AssessmentScheduleRepository scheduleRepository, AssessmentQuestionRepository questionRepository) {
        this.versionRepository = versionRepository;
        this.scheduleRepository = scheduleRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedAssessmentVersion requirePublishedVersion(UUID assessmentVersionId) {
        AssessmentVersion version = versionRepository.findById(assessmentVersionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", assessmentVersionId));
        if (!version.isPublished()) {
            throw new BusinessException("Assessment version is not published",
                    List.of(ApiError.of("assessmentVersionId", "ASSESSMENT_VERSION_NOT_PUBLISHED",
                            "Only a published assessment version can be delivered")));
        }
        return new PublishedAssessmentVersion(version.getId(), version.getAssessmentId(),
                version.getBlueprintVersionId(), version.getDurationMinutes(),
                version.getEngineType() == null ? null : version.getEngineType().name(),
                version.getEngineConfigurationVersion(), version.getDeliveryPolicyId(), version.isPublished());
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleView requireSchedule(UUID scheduleId) {
        AssessmentSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> NotFoundException.of("Assessment schedule", scheduleId));
        return new ScheduleView(schedule.getId(), schedule.getAssessmentVersionId(), schedule.getMode(),
                schedule.getStartTime(), schedule.getEndTime(),
                schedule.getStatus() == null ? null : schedule.getStatus().name());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentQuestionView> questions(UUID assessmentVersionId) {
        return questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(assessmentVersionId).stream()
                .map(this::toView)
                .toList();
    }

    private AssessmentQuestionView toView(AssessmentQuestion question) {
        return new AssessmentQuestionView(question.getQuestionVersionId(), question.getDisplayOrder(),
                question.getSectionName(), question.getMarks(), question.getNegativeMarks());
    }
}
