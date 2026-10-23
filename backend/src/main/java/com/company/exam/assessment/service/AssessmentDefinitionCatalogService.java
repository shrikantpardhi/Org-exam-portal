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
        return toVersionView(version);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleBinding requireScheduleBinding(UUID scheduleId) {
        List<Object[]> rows = scheduleRepository.findScheduleWithVersion(scheduleId);
        if (rows.isEmpty()) {
            throw NotFoundException.of("Assessment schedule", scheduleId);
        }
        Object[] row = rows.get(0);
        AssessmentSchedule schedule = (AssessmentSchedule) row[0];
        AssessmentVersion version = (AssessmentVersion) row[1];
        if (!version.isPublished()) {
            throw new BusinessException("Assessment version is not published",
                    List.of(ApiError.of("assessmentVersionId", "ASSESSMENT_VERSION_NOT_PUBLISHED",
                            "Only a published assessment version can be delivered")));
        }
        return new ScheduleBinding(toScheduleView(schedule), toVersionView(version));
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleView requireSchedule(UUID scheduleId) {
        AssessmentSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> NotFoundException.of("Assessment schedule", scheduleId));
        return toScheduleView(schedule);
    }

    private ScheduleView toScheduleView(AssessmentSchedule schedule) {
        return new ScheduleView(schedule.getId(), schedule.getAssessmentVersionId(), schedule.getMode(),
                schedule.getStartTime(), schedule.getEndTime(),
                schedule.getStatus() == null ? null : schedule.getStatus().name());
    }

    private PublishedAssessmentVersion toVersionView(AssessmentVersion version) {
        return new PublishedAssessmentVersion(version.getId(), version.getAssessmentId(),
                version.getBlueprintVersionId(), version.getDurationMinutes(),
                version.getEngineType() == null ? null : version.getEngineType().name(),
                version.getEngineConfigurationVersion(), version.getDeliveryPolicyId(), version.isPublished());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentQuestionView> questions(UUID assessmentVersionId) {
        return questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(assessmentVersionId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentScoringView requireScoringContext(UUID assessmentVersionId) {
        AssessmentVersion version = versionRepository.findById(assessmentVersionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", assessmentVersionId));
        return new AssessmentScoringView(version.getId(), version.getAssessmentId(), version.isPublished(),
                version.getTotalQuestions(), version.getTotalMarks());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> assessmentVersionIds(UUID assessmentId) {
        return versionRepository.findByAssessmentIdOrderByVersionAsc(assessmentId).stream()
                .map(AssessmentVersion::getId)
                .toList();
    }

    private AssessmentQuestionView toView(AssessmentQuestion question) {
        return new AssessmentQuestionView(question.getQuestionVersionId(), question.getDisplayOrder(),
                question.getSectionName(), question.getMarks(), question.getNegativeMarks());
    }
}
