package com.company.exam.assessment.service;

import com.company.exam.assessment.api.AssessmentAnalyticsCatalog;
import com.company.exam.assessment.entity.Assessment;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.repository.AssessmentQuestionRepository;
import com.company.exam.assessment.repository.AssessmentRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.common.enums.Status;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link AssessmentAnalyticsCatalog} ({@code specs/17-P8-Analytics-V1.5.md §12, §42, §77}). */
@Service
public class AssessmentAnalyticsCatalogService implements AssessmentAnalyticsCatalog {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentVersionRepository versionRepository;
    private final AssessmentQuestionRepository questionRepository;

    public AssessmentAnalyticsCatalogService(AssessmentRepository assessmentRepository,
            AssessmentVersionRepository versionRepository, AssessmentQuestionRepository questionRepository) {
        this.assessmentRepository = assessmentRepository;
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> qualifyingAssessmentIds() {
        return assessmentRepository.findByStatus(Status.ACTIVE).stream().map(Assessment::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionRow> allVersions() {
        return versionRepository.findAll().stream().map(this::toRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionRow> versions(Collection<UUID> versionIds) {
        return versionIds.isEmpty() ? List.of()
                : versionRepository.findAllById(versionIds).stream().map(this::toRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionRow> questionsForVersions(Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        return questionRepository.findByAssessmentVersionIdIn(List.copyOf(versionIds)).stream()
                .map(question -> new QuestionRow(question.getAssessmentVersionId(), question.getQuestionVersionId(),
                        question.getSectionName(), question.getMarks()))
                .toList();
    }

    private VersionRow toRow(AssessmentVersion version) {
        return new VersionRow(version.getId(), version.getAssessmentId(),
                version.getEngineType() == null ? null : version.getEngineType().name());
    }
}
