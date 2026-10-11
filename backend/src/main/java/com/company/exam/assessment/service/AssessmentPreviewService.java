package com.company.exam.assessment.service;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentQuestion;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.repository.AssessmentQuestionRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.api.ContentCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessment preview ({@code specs/13-P4-Assessment-V1.5.md §55-§57, §191}).
 *
 * <p>Read-only: preview creates no runtime rows. Rendering uses the P3 canonical contract — there is
 * no separate permissive preview renderer.
 */
@Service
public class AssessmentPreviewService {

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final ContentCatalog contentCatalog;

    public AssessmentPreviewService(AssessmentVersionRepository versionRepository,
            AssessmentQuestionRepository questionRepository, ContentCatalog contentCatalog) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
        this.contentCatalog = contentCatalog;
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.PreviewResponse preview(UUID versionId) {
        AssessmentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
        List<AssessmentQuestion> questions = questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId);

        Map<String, List<AssessmentDtos.PreviewQuestion>> bySection = new LinkedHashMap<>();
        for (AssessmentQuestion question : questions) {
            ContentCatalog.PreviewContent content = contentCatalog.previewContent(question.getQuestionVersionId());
            String section = question.getSectionName() == null ? "" : question.getSectionName();
            bySection.computeIfAbsent(section, key -> new ArrayList<>())
                    .add(new AssessmentDtos.PreviewQuestion(question.getId(), question.getQuestionVersionId(),
                            question.getDisplayOrder(), question.getMarks(), question.getNegativeMarks(),
                            content.contentFormat(), content.content(), content.renderedContent()));
        }
        List<AssessmentDtos.PreviewSection> sections = bySection.entrySet().stream()
                .map(entry -> new AssessmentDtos.PreviewSection(entry.getKey(), entry.getValue()))
                .toList();
        return new AssessmentDtos.PreviewResponse(version.getId(), version.getTotalQuestions(), version.getTotalMarks(),
                version.getDurationMinutes(), version.getEngineType(), sections);
    }
}
